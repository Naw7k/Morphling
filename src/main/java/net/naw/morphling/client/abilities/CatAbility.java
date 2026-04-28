package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.feline.Cat;
import net.naw.morphling.client.core.MorphState;

public class CatAbility {

    private static long lastActionTime = 0L;
    private static final long ACTION_COOLDOWN_MS = 300;

    public enum Pose { STAND, SIT, LYING, RELAXED }

    private static Pose currentPose = Pose.STAND;

    public static void toggleSit(Minecraft client) {
        if (!checkReady(client)) return;
        currentPose = (currentPose == Pose.SIT) ? Pose.STAND : Pose.SIT;
        applyPose();
    }

    public static void toggleLying(Minecraft client) {
        if (!checkReady(client)) return;
        currentPose = (currentPose == Pose.LYING) ? Pose.STAND : Pose.LYING;
        applyPose();
    }

    public static void toggleRelaxed(Minecraft client) {
        if (!checkReady(client)) return;
        currentPose = (currentPose == Pose.RELAXED) ? Pose.STAND : Pose.RELAXED;
        applyPose();
    }

    public static void playHiss(Minecraft client) {
        if (!checkReady(client)) return;
        if (client.level != null && client.player != null) {
            client.level.playLocalSound(
                    client.player.getX(), client.player.getY(), client.player.getZ(),
                    net.minecraft.sounds.SoundEvents.CAT_HISS_BABY.value(), SoundSource.PLAYERS,
                    1.0F, 1.0F, false
            );
        }
    }

    public static void playPurr(Minecraft client) {
        if (!checkReady(client)) return;
        if (client.level != null && client.player != null) {
            client.level.playLocalSound(
                    client.player.getX(), client.player.getY(), client.player.getZ(),
                    net.minecraft.sounds.SoundEvents.CAT_PURR_BABY.value(), SoundSource.PLAYERS,
                    1.0F, 1.0F, false
            );
        }
    }

    private static boolean checkReady(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.CAT) return false;
        if (client.player == null) return false;
        long now = System.currentTimeMillis();
        if (now - lastActionTime < ACTION_COOLDOWN_MS) return false;
        lastActionTime = now;
        return true;
    }

    private static void applyPose() {
        if (!(MorphState.getCachedEntity() instanceof Cat cat)) return;
        cat.setInSittingPose(false);
        cat.setLying(false);
        ((net.naw.morphling.mixin.accessors.CatRelaxAccessor)(Object) cat).morphling$setRelaxStateOne(false);

        switch (currentPose) {
            case SIT -> cat.setInSittingPose(true);
            case LYING -> cat.setLying(true);
            case RELAXED -> ((net.naw.morphling.mixin.accessors.CatRelaxAccessor)(Object) cat).morphling$setRelaxStateOne(true);
            case STAND -> { }
        }
    }

    public static void tick(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.CAT) {
            currentPose = Pose.STAND;
            return;
        }
        if (client.player == null) return;
        if (!(MorphState.getCachedEntity() instanceof Cat cat)) return;

        // Cancel poses if player is moving
        if (currentPose != Pose.STAND) {
            double speedSqr = client.player.getDeltaMovement().horizontalDistanceSqr();
            if (speedSqr > 0.001) {
                currentPose = Pose.STAND;
                applyPose();
                return;
            }
        }

        // Only call the lie-down animation update — NOT full tick (no AI = no drift)
        try {
            ((net.naw.morphling.mixin.accessors.CatTickAccessor)(Object) cat).morphling$handleLieDown();
        } catch (Exception ignored) {}
    }
}