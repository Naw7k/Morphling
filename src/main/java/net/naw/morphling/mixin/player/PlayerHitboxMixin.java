package net.naw.morphling.mixin.player;

import net.minecraft.client.Minecraft;
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
        if (!MorphState.isMorphed()) return;

        Entity self = (Entity)(Object)this;
        if (!(self instanceof Player)) return;

        // Match by UUID so both client-side and server-side copies of the player are affected (singleplayer)
        if (Minecraft.getInstance().player == null) return;
        if (!self.getUUID().equals(Minecraft.getInstance().player.getUUID())) return;

        Entity morphEntity = MorphState.getCachedEntity();
        if (morphEntity == null) return;

        cir.setReturnValue(morphEntity.getDimensions(pose));
    }
}