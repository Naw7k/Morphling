package net.naw.morphling.mixin.skeleton;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup;
import net.naw.morphling.client.abilities.SkeletonAbility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
public abstract class SkeletonArrowPickupMixin {

    @Inject(method = "setOwner", at = @At("TAIL"))
    private void morphling$disablePickup(net.minecraft.world.entity.Entity owner, CallbackInfo ci) {
        if (!SkeletonAbility.isBowEquipped()) return;
        if (owner == null) return;
        // Only apply to OUR player
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        if (!owner.getUUID().equals(mc.player.getUUID())) return;

        AbstractArrow self = (AbstractArrow)(Object)this;
        self.pickup = Pickup.DISALLOWED;
    }
}