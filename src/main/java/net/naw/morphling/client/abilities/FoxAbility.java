package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.phys.Vec3;
import net.naw.morphling.client.core.MorphState;

@SuppressWarnings({"unused", "BooleanMethodIsAlwaysInverted"})
public class FoxAbility {

    private static final long ACTION_COOLDOWN_MS = 300;
    private static final long POUNCE_COOLDOWN_MS = 1500;
    private static long lastActionTime = 0L;
    private static long lastPounceTime = 0L;

    // Pose states
    private static boolean sitting = false;
    private static boolean sleeping = false;
    private static boolean crouching = false;
    private static boolean interested = false; // head tilt
    private static boolean pouncing = false;

    // Internal animation floats (mirrors vanilla Fox)
    private static float crouchAmount = 0.0F;
    private static float interestedAngle = 0.0F;

    /** R — toggle sit */
    public static void toggleSit(Minecraft client) {
        if (!checkReady(client)) return;
        if (sleeping) return; // can't sit while sleeping
        sitting = !sitting;
        applyPose();
        MorphState.sendAbilityState("fox_sitting", String.valueOf(sitting));
    }

    /** Shift+R — toggle sleep */
    public static void toggleSleep(Minecraft client) {
        if (!checkReady(client)) return;
        sleeping = !sleeping;
        if (sleeping) {
            sitting = false;
            crouching = false;
            interested = false;
        }
        applyPose();
        MorphState.sendAbilityState("fox_sleeping", String.valueOf(sleeping));
        if (sleeping && client.level != null && client.player != null) {
            client.level.playLocalSound(
                    client.player.getX(), client.player.getY(), client.player.getZ(),
                    SoundEvents.FOX_SLEEP, SoundSource.PLAYERS,
                    0.6F, 1.0F, false
            );
            MorphState.broadcastSound(SoundEvents.FOX_SLEEP, 0.6F, 1.0F);
        }
    }

    /** Ctrl+R — toggle crouch/stalk */
    public static void toggleCrouch(Minecraft client) {
        if (!checkReady(client)) return;
        if (sleeping) return;
        crouching = !crouching;
        if (crouching) {
            sitting = false;
            interested = true;
        } else {
            interested = false;
        }
        applyPose();
        MorphState.sendAbilityState("fox_crouching", String.valueOf(crouching));
        MorphState.sendAbilityState("fox_interested", String.valueOf(interested));
    }

    /** F — pounce leap forward */
    public static void triggerPounce(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.FOX) return;
        if (client.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastPounceTime < POUNCE_COOLDOWN_MS) return;
        lastPounceTime = now;

        Vec3 look = client.player.getLookAngle();
        Vec3 current = client.player.getDeltaMovement();
        client.player.setDeltaMovement(
                current.x + look.x * 0.8,
                0.9,
                current.z + look.z * 0.8
        );

        pouncing = true;
        applyPose();
        MorphState.sendAbilityState("fox_pouncing", "true");

        if (client.level != null) {
            client.level.playLocalSound(
                    client.player.getX(), client.player.getY(), client.player.getZ(),
                    SoundEvents.FOX_AGGRO, SoundSource.PLAYERS,
                    0.5F, 1.2F, false
            );
            MorphState.broadcastSound(SoundEvents.FOX_AGGRO, 0.5F, 1.2F);
        }
    }

    /** B — ambient sound */
    public static void playAmbient(Minecraft client) {
        if (client.level == null || client.player == null) return;
        client.level.playLocalSound(
                client.player.getX(), client.player.getY(), client.player.getZ(),
                SoundEvents.FOX_AMBIENT, SoundSource.PLAYERS,
                1.0F, 1.0F, false
        );
        MorphState.broadcastSound(SoundEvents.FOX_AMBIENT, 1.0F, 1.0F);
    }

    /** Shift+B — screech */
    public static void playScreech(Minecraft client) {
        if (client.level == null || client.player == null) return;
        client.level.playLocalSound(
                client.player.getX(), client.player.getY(), client.player.getZ(),
                SoundEvents.FOX_SCREECH, SoundSource.PLAYERS,
                1.0F, 1.0F, false
        );
        MorphState.broadcastSound(SoundEvents.FOX_SCREECH, 1.0F, 1.0F);
    }

    /** Ctrl+B — sniff */
    public static void playSniff(Minecraft client) {
        if (client.level == null || client.player == null) return;
        client.level.playLocalSound(
                client.player.getX(), client.player.getY(), client.player.getZ(),
                SoundEvents.FOX_SNIFF, SoundSource.PLAYERS,
                1.0F, 1.0F, false
        );
        MorphState.broadcastSound(SoundEvents.FOX_SNIFF, 1.0F, 1.0F);
    }

    private static void applyPose() {
        if (!(MorphState.getCachedEntity() instanceof Fox fox)) return;
        // Clear all first
        ((net.naw.morphling.mixin.accessors.FoxVariantAccessor) fox).morphling$setSitting(false);
        ((net.naw.morphling.mixin.accessors.FoxVariantAccessor) fox).morphling$setSleeping(false);
        fox.setIsCrouching(false);
        fox.setIsInterested(false);
        fox.setIsPouncing(false);

        // Apply current states
        if (sitting)    ((net.naw.morphling.mixin.accessors.FoxVariantAccessor) fox).morphling$setSitting(true);
        if (sleeping)   ((net.naw.morphling.mixin.accessors.FoxVariantAccessor) fox).morphling$setSleeping(true);
        if (crouching)  fox.setIsCrouching(true);
        if (interested) fox.setIsInterested(true);
        if (pouncing)   fox.setIsPouncing(true);
    }

    public static void tick(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.FOX) {
            // Reset all state when not a fox
            sitting = false;
            sleeping = false;
            crouching = false;
            interested = false;
            pouncing = false;
            crouchAmount = 0.0F;
            interestedAngle = 0.0F;
            return;
        }

        if (client.player == null) return;
        if (!(MorphState.getCachedEntity() instanceof Fox fox)) return;

        // Cancel sit/sleep/crouch if player moves
        if (sitting || sleeping || crouching) {
            double speedSqr = client.player.getDeltaMovement().horizontalDistanceSqr();
            if (speedSqr > 0.001) {
                sitting = false;
                sleeping = false;
                crouching = false;
                interested = false;
                applyPose();
                MorphState.sendAbilityState("fox_sitting", "false");
                MorphState.sendAbilityState("fox_sleeping", "false");
                MorphState.sendAbilityState("fox_crouching", "false");
                MorphState.sendAbilityState("fox_interested", "false");
            }
        }

        // Clear pouncing once player lands
        if (pouncing && client.player.onGround()) {
            client.player.resetFallDistance();
            pouncing = false;
            fox.setIsPouncing(false);
            MorphState.sendAbilityState("fox_pouncing", "false");
        }

        // Drive crouch animation float
        if (crouching) {
            crouchAmount += 0.2F;
            if (crouchAmount > 5.0F) crouchAmount = 5.0F;
        } else {
            crouchAmount = 0.0F;
        }

        // Drive interested (head tilt) animation float
        if (interested) {
            interestedAngle += (1.0F - interestedAngle) * 0.4F;
        } else {
            interestedAngle += (0.0F - interestedAngle) * 0.4F;
        }

        // Play FOX_SLEEP snore sound periodically while sleeping
        if (sleeping && client.player.tickCount % 80 == 0) {
            if (client.level != null) {
                client.level.playLocalSound(
                        client.player.getX(), client.player.getY(), client.player.getZ(),
                        SoundEvents.FOX_SLEEP, SoundSource.PLAYERS,
                        0.1F, 1.0F, false
                );
                MorphState.broadcastSound(SoundEvents.FOX_SLEEP, 0.6F, 1.0F);
            }
        }

        // Play FOX_EAT sound periodically while eating — mirrors vanilla fox behavior
        if (client.player.isUsingItem() && client.player.tickCount % 8 == 0) {
            if (client.level != null) {
                client.level.playLocalSound(
                        client.player.getX(), client.player.getY(), client.player.getZ(),
                        SoundEvents.FOX_EAT, SoundSource.PLAYERS,
                        0.5F, 1.0F, false
                );
                MorphState.broadcastSound(SoundEvents.FOX_EAT, 0.5F, 1.0F);
            }
        }

        // Sync cached fox position
        fox.tickCount = client.player.tickCount;
        fox.setPos(client.player.getX(), client.player.getY(), client.player.getZ());
    }

    // Getters for remote state
    public static boolean isSitting()    { return sitting; }
    public static boolean isSleeping()   { return sleeping; }
    public static boolean isCrouching()  { return crouching; }
    public static boolean isInterested() { return interested; }
    public static boolean isPouncing()   { return pouncing; }

    private static boolean checkReady(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.FOX) return false;
        if (client.player == null) return false;
        long now = System.currentTimeMillis();
        if (now - lastActionTime < ACTION_COOLDOWN_MS) return false;
        lastActionTime = now;
        return true;
    }
}