package net.naw.morphling.mixin.player;

import net.minecraft.world.food.FoodData;
import net.naw.morphling.network.MorphlingNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodData.class)
public class HungerFreezeMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void morphling$freezeHunger(net.minecraft.server.level.ServerPlayer player, CallbackInfo ci) {
        String morphTypeId = MorphlingNetworking.playerMorphMap.get(player.getUUID());
        if (morphTypeId != null && morphTypeId.contains("iron_golem")) {
            ci.cancel();
        }
    }
}