package net.naw.morphling.mixin.bee;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.BeeRenderer;
import net.minecraft.client.renderer.entity.state.BeeRenderState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.bee.Bee;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.client.core.RemoteMorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

@Mixin(BeeRenderer.class)
public class BeeWingMixin {

    @Inject(method = "extractRenderState*", at = @At("TAIL"))
    private void morphling$stopWingsOnGround(Bee entity, BeeRenderState state, float partialTicks, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Local player
        if (entity == MorphState.getCachedEntity() && MorphState.getCurrentMorph() == EntityType.BEE) {
            if (mc.player.onGround()) state.isOnGround = true;
            state.isAngry = net.naw.morphling.client.abilities.BeeAbility.isAngry();
            state.hasNectar = net.naw.morphling.client.abilities.BeeAbility.hasNectar();
            state.rollAmount = net.naw.morphling.client.abilities.BeeAbility.getRollAmount();
            return;
        }

        // Remote players
        for (Map.Entry<UUID, RemoteMorphState.PlayerMorphData> entry : RemoteMorphState.getAllStates().entrySet()) {
            RemoteMorphState.PlayerMorphData data = entry.getValue();
            if (data.morphType == EntityType.BEE && data.cachedEntity == entity) {
                state.isAngry = data.beeAngry;
                state.hasNectar = data.beeNectar;
                state.rollAmount = data.beeRollAmount;
                // Wings stop when remote player is on ground
                if (mc.level != null) {
                    for (net.minecraft.world.entity.player.Player p : mc.level.players()) {
                        if (p.getUUID().equals(entry.getKey()) && p.onGround()) {
                            state.isOnGround = true;
                            break;
                        }
                    }
                }
                break;
            }
        }
    }
}