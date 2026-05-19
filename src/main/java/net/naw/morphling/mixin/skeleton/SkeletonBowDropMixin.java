package net.naw.morphling.mixin.skeleton;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.naw.morphling.client.abilities.SkeletonAbility;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class SkeletonBowDropMixin {

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void morphling$preventSkeletonBowDrop(ItemStack itemStack, boolean thrownFromHand, CallbackInfoReturnable<@Nullable ItemEntity> cir) {
        if (!SkeletonAbility.isBowEquipped()) return;
        if (itemStack.getItem() != Items.BOW) return;
        Component name = itemStack.get(DataComponents.CUSTOM_NAME);
        if (name != null && name.getString().equals("Skeleton Bow")) {
            cir.setReturnValue(null);
        }
    }
}