package net.naw.morphling.mixin.mob_ai;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumSet;
import java.util.List;

/**
 * Makes baby chickens follow a player who is morphed as a chicken,
 * mimicking the vanilla "baby follows parent" behavior.
 */
@Mixin(Chicken.class)
public abstract class ChickenFollowMorphMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void morphling$addFollowMorphedPlayerGoal(CallbackInfo ci) {
        Chicken chicken = (Chicken)(Object) this;
        ((net.naw.morphling.mixin.accessors.MobGoalSelectorAccessor)(Object) chicken)
                .morphling$getGoalSelector()
                .addGoal(4, new FollowMorphedPlayerGoal(chicken));
    }

    /**
     * Custom goal — only active when this chicken is a baby and there's a
     * nearby player morphed as a chicken. Walks toward that player.
     */
    private static class FollowMorphedPlayerGoal extends Goal {
        private final Chicken chicken;
        private Player target;
        private int retargetCooldown = 0;

        FollowMorphedPlayerGoal(Chicken chicken) {
            this.chicken = chicken;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!chicken.isBaby()) return false;
            if (retargetCooldown > 0) {
                retargetCooldown--;
                return false;
            }
            retargetCooldown = 10;

            // Find a player morphed as chicken within 16 blocks
            List<Player> nearby = chicken.level().getEntitiesOfClass(
                    Player.class,
                    chicken.getBoundingBox().inflate(16.0D),
                    p -> {
                        if (p.isSpectator() || !p.isAlive()) return false;
                        // Check if this player is morphed as a chicken
                        // (only the local client tracks MorphState — but morph is broadcast via player size, so this works in singleplayer)
                        return MorphState.isMorphed()
                                && MorphState.getCurrentMorph() == EntityType.CHICKEN;
                    }
            );
            if (nearby.isEmpty()) return false;

            Player closest = null;
            double closestDist = Double.MAX_VALUE;
            for (Player p : nearby) {
                double d = chicken.distanceToSqr(p);
                if (d < closestDist) {
                    closestDist = d;
                    closest = p;
                }
            }
            if (closest == null) return false;
            // Don't move if we're already close
            if (closestDist < 9.0D) return false;
            this.target = closest;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            if (target == null) return false;
            if (!target.isAlive()) return false;
            if (!chicken.isBaby()) return false;
            // Stop when close enough
            return chicken.distanceToSqr(target) > 9.0D;
        }

        @Override
        public void tick() {
            if (target == null) return;
            chicken.getLookControl().setLookAt(target, 10.0F, chicken.getMaxHeadXRot());
            chicken.getNavigation().moveTo(target, 1.1D);
        }

        @Override
        public void stop() {
            this.target = null;
            chicken.getNavigation().stop();
        }
    }
}