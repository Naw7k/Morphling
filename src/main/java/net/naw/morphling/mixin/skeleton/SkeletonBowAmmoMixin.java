package net.naw.morphling.mixin.skeleton;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.naw.morphling.client.abilities.SkeletonAbility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class SkeletonBowAmmoMixin {

    @Inject(method = "getProjectile", at = @At("HEAD"), cancellable = true)
    private void morphling$infiniteBowArrows(ItemStack heldWeapon, CallbackInfoReturnable<ItemStack> cir) {
        if (heldWeapon.getItem() instanceof BowItem && SkeletonAbility.isBowEquipped()) {
            cir.setReturnValue(new ItemStack(Items.ARROW));
        }
    }
}