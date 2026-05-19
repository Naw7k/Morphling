package net.naw.morphling.mixin.skeleton;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class SkeletonBowAmmoMixin {

    @Inject(method = "getProjectile", at = @At("HEAD"), cancellable = true)
    private void morphling$infiniteBowArrows(ItemStack heldWeapon, CallbackInfoReturnable<ItemStack> cir) {
        if (!(heldWeapon.getItem() instanceof BowItem)) return;

        // Check custom name for Skeleton Bow
        net.minecraft.network.chat.Component name = heldWeapon.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (name != null && name.getString().equals("Skeleton Bow")) {
            cir.setReturnValue(new ItemStack(Items.ARROW));
        }
    }
}