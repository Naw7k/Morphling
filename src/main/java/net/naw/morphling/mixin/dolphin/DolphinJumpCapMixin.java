package net.naw.morphling.mixin.dolphin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class DolphinJumpCapMixin {

    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void morphling$capDolphinJump(CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!(self instanceof Player)) return;
        if (!MorphState.isMorphed()) return;
        var morph = MorphState.getCachedEntity();
        if (morph == null || morph.getType() != EntityType.DOLPHIN) return;
        if (self.isInWater()) return;

        Vec3 dm = self.getDeltaMovement();
        double maxSpeed = 0.05;
        double horizSpeed = Math.sqrt(dm.x * dm.x + dm.z * dm.z);
        if (horizSpeed > maxSpeed) {
            double scale = maxSpeed / horizSpeed;
            self.setDeltaMovement(dm.x * scale, dm.y, dm.z * scale);
        }
    }
}