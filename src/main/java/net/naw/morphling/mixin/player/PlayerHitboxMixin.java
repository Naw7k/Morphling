package net.naw.morphling.mixin.player;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PlayerHitboxMixin {

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void morphling$overrideDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        Entity self = (Entity)(Object)this;
        if (!(self instanceof Player)) return;

        boolean isClientThread = Thread.currentThread().getName().equals("Render thread");

        // Client-side path
        if (isClientThread) {
            // Local player
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && self.getUUID().equals(mc.player.getUUID())) {
                if (!MorphState.isMorphed()) return;
                Entity morphEntity = MorphState.getCachedEntity();
                if (morphEntity == null) return;
                cir.setReturnValue(morphEntity.getDimensions(pose));
                return;
            }

            // Remote players
            net.naw.morphling.client.core.RemoteMorphState.PlayerMorphData data = net.naw.morphling.client.core.RemoteMorphState.get(self.getUUID());
            if (data != null && data.cachedEntity != null) {
                cir.setReturnValue(data.cachedEntity.getDimensions(pose));
            }
            return;
        }

        // Server-side path — use playerMorphMap
        String morphTypeId = net.naw.morphling.network.MorphlingNetworking.playerMorphMap.get(self.getUUID());
        if (morphTypeId == null || morphTypeId.isEmpty()) return;
        try {
            net.minecraft.world.entity.EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getValue(net.minecraft.resources.Identifier.parse(morphTypeId));
            if (type == null) return;
            var morphEntity = type.create(self.level(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
            if (morphEntity == null) return;
            cir.setReturnValue(morphEntity.getDimensions(pose));
        } catch (Exception ignored) {}
    }
}