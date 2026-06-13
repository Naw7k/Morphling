package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.naw.morphling.client.core.MorphState;
import net.minecraft.world.entity.Entity;

public class VillagerAbility {

    private static final long ACTION_COOLDOWN_MS = 300;
    private static long lastActionTime = 0L;
    private static float sleepLockedYRot = 0f;
    private static int unhappyResetTimer = 0;

    // Sleep state
    private static boolean sleeping = false;

    /** R — trigger unhappy animation (head shake + no sound) */
    public static void triggerUnhappy(Minecraft client) {
        if (!checkReady(client)) return;
        if (!(MorphState.getCachedEntity() instanceof Villager villager)) return;

        unhappyResetTimer = 45;

        villager.setUnhappyCounter(40);
        MorphState.sendAbilityState("villager_unhappy", "true");

        if (client.level != null && client.player != null) {
            client.level.playLocalSound(
                    client.player.getX(), client.player.getY(), client.player.getZ(),
                    SoundEvents.VILLAGER_NO, SoundSource.PLAYERS,
                    1.0F, 1.0F, false
            );
        }
        MorphState.broadcastSound(SoundEvents.VILLAGER_NO, 1.0F, 1.0F);
    }

    /** Shift+R — toggle sleep pose */
    public static void toggleSleep(Minecraft client) {
        if (!checkReady(client)) return;
        if (!(MorphState.getCachedEntity() instanceof Villager villager)) return;

        sleeping = !sleeping;
        if (sleeping) {
            if (client.player != null) {
                sleepLockedYRot = client.player.getYRot();
            }
            villager.setPose(net.minecraft.world.entity.Pose.SLEEPING);
        } else {
            villager.setPose(net.minecraft.world.entity.Pose.STANDING);
        }
        MorphState.sendAbilityState("villager_sleeping", String.valueOf(sleeping));
    }

    /** Ctrl+R — play profession-specific work sound */
    public static void playWorkSound(Minecraft client) {
        if (!checkReady(client)) return;
        if (!(MorphState.getCachedEntity() instanceof Villager villager)) return;
        if (client.level == null || client.player == null) return;

        var workSound = villager.getVillagerData().profession().value().workSound();
        if (workSound != null) {
            client.level.playLocalSound(
                    client.player.getX(), client.player.getY(), client.player.getZ(),
                    workSound, SoundSource.PLAYERS,
                    1.0F, 1.0F, false
            );
            MorphState.broadcastSound(workSound, 1.0F, 1.0F);
        }
    }

    /** B — play ambient hum */
    public static void playAmbient(Minecraft client) {
        if (!checkReady(client)) return;
        if (client.level == null || client.player == null) return;

        client.level.playLocalSound(
                client.player.getX(), client.player.getY(), client.player.getZ(),
                SoundEvents.VILLAGER_AMBIENT, SoundSource.PLAYERS,
                1.0F, 1.0F, false
        );
        MorphState.broadcastSound(SoundEvents.VILLAGER_AMBIENT, 1.0F, 1.0F);
    }

    /** Shift+B — play yes sound */
    public static void playYes(Minecraft client) {
        if (!checkReady(client)) return;
        if (client.level == null || client.player == null) return;

        client.level.playLocalSound(
                client.player.getX(), client.player.getY(), client.player.getZ(),
                SoundEvents.VILLAGER_YES, SoundSource.PLAYERS,
                1.0F, 1.0F, false
        );
        MorphState.broadcastSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
    }

    /** Ctrl+B — play celebrate sound */
    public static void playCelebrate(Minecraft client) {
        if (!checkReady(client)) return;
        if (client.level == null || client.player == null) return;

        client.level.playLocalSound(
                client.player.getX(), client.player.getY(), client.player.getZ(),
                SoundEvents.VILLAGER_CELEBRATE, SoundSource.PLAYERS,
                1.0F, 1.0F, false
        );
        MorphState.broadcastSound(SoundEvents.VILLAGER_CELEBRATE, 1.0F, 1.0F);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean checkReady(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.VILLAGER) return false;
        if (client.player == null) return false;
        long now = System.currentTimeMillis();
        if (now - lastActionTime < ACTION_COOLDOWN_MS) return false;
        lastActionTime = now;
        return true;
    }

    public static void tick(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.VILLAGER) {
            if (sleeping) {
                sleeping = false;
                Entity cached = MorphState.getCachedEntity();
                if (cached instanceof Villager v) v.setPose(net.minecraft.world.entity.Pose.STANDING);
            }
            return;
        }

        if (client.player == null) return;
        if (!(MorphState.getCachedEntity() instanceof Villager villager)) return;

        // Tick cached entity so unhappy counter counts down
        villager.tickCount = client.player.tickCount;
        villager.setPos(client.player.getX(), client.player.getY(), client.player.getZ());
        if (!sleeping) {
            try {
                villager.tick();
                villager.setPos(client.player.getX(), client.player.getY(), client.player.getZ());
                villager.setDeltaMovement(0, 0, 0);
            } catch (Exception ignored) {}
        }

        // Force sleep pose AFTER tick so vanilla doesn't override it
        if (sleeping) {
            villager.setPose(net.minecraft.world.entity.Pose.SLEEPING);
            client.player.setPose(net.minecraft.world.entity.Pose.SLEEPING);
            client.player.yBodyRot = sleepLockedYRot;
            client.player.yBodyRotO = sleepLockedYRot;
            client.player.yHeadRot = sleepLockedYRot;
            // Still tick unhappy counter down while sleeping
            if (villager.getUnhappyCounter() > 0) {
                villager.setUnhappyCounter(villager.getUnhappyCounter() - 1);
                if (villager.getUnhappyCounter() == 0) {
                    MorphState.sendAbilityState("villager_unhappy", "false");
                }
            }
        }

        // Cancel sleep if player moves
        if (sleeping) {
            double speedSqr = client.player.getDeltaMovement().horizontalDistanceSqr();
            if (speedSqr > 0.001) {
                sleeping = false;
                villager.setPose(net.minecraft.world.entity.Pose.STANDING);
                client.player.setPose(net.minecraft.world.entity.Pose.STANDING);
                MorphState.sendAbilityState("villager_sleeping", "false");
            }
        }

        if (unhappyResetTimer > 0) {
            unhappyResetTimer--;
            if (unhappyResetTimer == 0) {
                MorphState.sendAbilityState("villager_unhappy", "false");
            }
        }
    }

    public static boolean isSleeping() { return sleeping; }
}