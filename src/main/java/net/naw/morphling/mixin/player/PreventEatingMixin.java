package net.naw.morphling.mixin.player;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.naw.morphling.network.MorphlingNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.world.entity.LivingEntity.class)
public class PreventEatingMixin {

    @Inject(method = "startUsingItem", at = @At("HEAD"), cancellable = true)
    private void morphling$preventEat(net.minecraft.world.InteractionHand hand, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!(self instanceof ServerPlayer player)) return;
        String morphTypeId = MorphlingNetworking.playerMorphMap.get(player.getUUID());
        if (morphTypeId == null || !morphTypeId.contains("iron_golem")) return;
        net.minecraft.world.item.ItemStack item = player.getItemInHand(hand);
        if (item.has(DataComponents.FOOD)) {
            ci.cancel();
        }
    }
}
