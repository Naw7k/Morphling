package net.naw.morphling.mixin.skeleton;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup;
import net.minecraft.world.item.ItemStack;
import net.naw.morphling.client.abilities.SkeletonAbility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
public abstract class SkeletonArrowPickupMixin {

    @Inject(method = "setOwner", at = @At("TAIL"))
    private void morphling$disablePickup(net.minecraft.world.entity.Entity owner, CallbackInfo ci) {
        if (owner == null) return;
        if (!(owner instanceof Player player)) return;

        // Check if owner is holding Skeleton Bow
        ItemStack held = player.getMainHandItem();
        net.minecraft.network.chat.Component name = held.getItem() instanceof net.minecraft.world.item.BowItem
                ? held.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME) : null;
        if (name != null && name.getString().equals("Skeleton Bow")) {
            AbstractArrow self = (AbstractArrow)(Object)this;
            self.pickup = Pickup.DISALLOWED;
            return;
        }

        // Client-side fallback
        if (Thread.currentThread().getName().equals("Render thread")) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;
            if (!owner.getUUID().equals(mc.player.getUUID())) return;
            if (!SkeletonAbility.isBowEquipped()) return;
            AbstractArrow self = (AbstractArrow)(Object)this;
            self.pickup = Pickup.DISALLOWED;
        }
    }
}