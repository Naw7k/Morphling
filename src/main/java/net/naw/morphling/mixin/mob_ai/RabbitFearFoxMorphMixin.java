package net.naw.morphling.mixin.mob_ai;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
import net.naw.morphling.client.abilities.FoxAbility;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Rabbit.class)
public abstract class RabbitFearFoxMorphMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void morphling$addFearFoxGoal(CallbackInfo ci) {
        Rabbit rabbit = (Rabbit)(Object) this;
        ((net.naw.morphling.mixin.accessors.MobGoalSelectorAccessor) rabbit).morphling$getGoalSelector().addGoal(2, new AvoidEntityGoal<>(
                rabbit,
                Player.class,

                (_) -> MorphState.getCurrentMorph() == EntityType.FOX
                        && !FoxAbility.isCrouching()
                        && !(Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCrouching()),
                8.0F, 1.4, 1.6,
                (_) -> true
        ));
    }
}