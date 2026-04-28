package net.naw.morphling.mixin.fear;

import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.client.config.MorphFearConfig;
import net.naw.morphling.client.core.MorphState;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Creeper.class)
public abstract class CreeperAvoidMorphMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void morphling$addAvoidMorphGoal(CallbackInfo ci) {
        Creeper creeper = (Creeper)(Object) this;
        ((net.naw.morphling.mixin.accessors.MobGoalSelectorAccessor)(Object) creeper).morphling$getGoalSelector().addGoal(3, new AvoidEntityGoal<>(
                creeper,
                Player.class,
                (livingEntity) -> {
                    // Check if the nearby player is morphed as a mob that scares creepers
                    if (MorphState.getCurrentMorph() == null) return false;
                    return MorphFearConfig.shouldFlee(EntityType.CREEPER, MorphState.getCurrentMorph());
                },
                6.0F,
                1.0,
                1.2,
                (entity) -> true
        ));
    }
}