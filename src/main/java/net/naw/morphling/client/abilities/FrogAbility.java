package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.client.core.RemoteMorphState;

/**
 * Frog morph ability.

 * R — croak (plays FROG_AMBIENT sound, triggers croakAnimationState for 3s)
 * F — big leap (launches player upward + forward, triggers jumpAnimationState)
 * B — tongue grab (raycasts in look direction up to 5 blocks, pulls then damages target)

 * Tongue uses a two-packet system matching vanilla ShootTongue timing:
 *   frog_tongue_pull  — sent immediately, server applies pull velocity
 *   frog_tongue_eat   — sent 6 ticks later, server deals damage + eats

 * Animations are driven from FrogAbility.tickAnimators() at END_CLIENT_TICK.
 * swimIdleAnimationState triggers automatically when in water and not moving.
 */
@SuppressWarnings("unused")
public class FrogAbility {

    // ── Croak ─────────────────────────────────────────────────────────────────
    private static final long CROAK_COOLDOWN_MS = 3000;
    private static long lastCroakTime = 0L;
    private static int croakTimer = 0;         // ticks remaining in croak animation
    private static final int CROAK_TICKS = 60; // 3 seconds = 3s animation length

    // ── Leap ──────────────────────────────────────────────────────────────────
    private static final long LEAP_COOLDOWN_MS = 1500;
    private static long lastLeapTime = 0L;
    private static int leapTimer = 0;          // ticks remaining in jump animation
    private static final int LEAP_TICKS = 10;  // 0.5s animation

    // ── Tongue ────────────────────────────────────────────────────────────────
    private static final long TONGUE_COOLDOWN_MS = 1000;
    private static long lastTongueTime = 0L;
    private static int tongueTimer = 0;        // ticks remaining in tongue animation
    private static final int TONGUE_TICKS = 10; // 0.5s animation
    private static final float TONGUE_RANGE = 5.0F; // max tongue reach in blocks
    private static final float AIM_TOLERANCE = 0.3F; // how wide the aim box is around the look ray

    // Two-packet tongue timing (matches vanilla ShootTongue CATCH_ANIMATION = 6 ticks)
    private static int tongueEatDelay = 0;      // countdown to send frog_tongue_eat
    private static java.util.UUID tongueTarget = null; // held between pull and eat packets

    // ── R — Croak ─────────────────────────────────────────────────────────────
    public static void triggerCroak(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.FROG) return;
        if (client.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastCroakTime < CROAK_COOLDOWN_MS) return;
        lastCroakTime = now;

        croakTimer = CROAK_TICKS;

        if (client.level != null) {
            client.level.playLocalSound(
                    client.player.getX(), client.player.getY(), client.player.getZ(),
                    SoundEvents.FROG_AMBIENT, SoundSource.PLAYERS,
                    1.0F, 1.0F, false
            );
            MorphState.broadcastSound(SoundEvents.FROG_AMBIENT, 1.0F, 1.0F);
        }
    }

    // ── F — Leap ──────────────────────────────────────────────────────────────
    public static void triggerLeap(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.FROG) return;
        if (client.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastLeapTime < LEAP_COOLDOWN_MS) return;
        lastLeapTime = now;

        leapTimer = LEAP_TICKS;

        Vec3 look = client.player.getLookAngle();
        Vec3 current = client.player.getDeltaMovement();
        client.player.setDeltaMovement(
                current.x + look.x * 0.6,
                0.8,
                current.z + look.z * 0.6
        );

        if (client.level != null) {
            client.level.playLocalSound(
                    client.player.getX(), client.player.getY(), client.player.getZ(),
                    SoundEvents.FROG_LONG_JUMP, SoundSource.PLAYERS,
                    1.0F, 1.0F, false
            );
            MorphState.broadcastSound(SoundEvents.FROG_LONG_JUMP, 1.0F, 1.0F);
        }
    }

    // ── B — Tongue grab ───────────────────────────────────────────────────────
    public static void triggerTongue(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.FROG) return;
        if (client.player == null || client.level == null) return;
        long now = System.currentTimeMillis();
        if (now - lastTongueTime < TONGUE_COOLDOWN_MS) return;
        lastTongueTime = now;

        tongueTimer = TONGUE_TICKS;

        // Play tongue sound immediately
        client.level.playLocalSound(
                client.player.getX(), client.player.getY(), client.player.getZ(),
                SoundEvents.FROG_TONGUE, SoundSource.PLAYERS,
                1.0F, 1.0F, false
        );
        MorphState.broadcastSound(SoundEvents.FROG_TONGUE, 1.0F, 1.0F);

        // ── Raycast target selection ──────────────────────────────────────────
        // Cast a ray from the player's eye in the look direction up to TONGUE_RANGE.
        // Only hit entities whose AABB intersects the expanded ray — this means you
        // must be roughly aimed at the target, not just near them.
        Vec3 eyePos = client.player.getEyePosition();
        Vec3 lookDir = client.player.getLookAngle();
        Vec3 rayEnd = eyePos.add(lookDir.scale(TONGUE_RANGE));

        // Expand the search box to cover the full ray sweep
        AABB searchBox = new AABB(eyePos, rayEnd).inflate(AIM_TOLERANCE);

        LivingEntity hit = null;
        double closestDist = Double.MAX_VALUE;

        for (net.minecraft.world.entity.Entity entity : client.level.getEntities(
                client.player, searchBox)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity.getUUID().equals(client.player.getUUID())) continue;

            // Check if the ray actually passes through (or close to) this entity's AABB
            AABB entityBox = entity.getBoundingBox().inflate(AIM_TOLERANCE, 0.0, AIM_TOLERANCE);
            java.util.Optional<Vec3> intersection = entityBox.clip(eyePos, rayEnd);
            if (intersection.isEmpty()) continue;

            double dist = eyePos.distanceTo(intersection.get());
            if (dist < closestDist) {
                closestDist = dist;
                hit = living;
            }
        }

        if (hit != null) {
            // Phase 1: pull — eat sound + damage follow 6 ticks later
            MorphState.sendAbilityAction("frog_tongue_pull", hit.getUUID().toString());
            tongueTarget = hit.getUUID();
            tongueEatDelay = 6;
        }
    }

    // ── Tick ──────────────────────────────────────────────────────────────────
    public static void tick(Minecraft client) {
        if (client.player == null) return;

        if (MorphState.getCurrentMorph() != EntityType.FROG) {
            croakTimer     = 0;
            leapTimer      = 0;
            tongueTimer    = 0;
            tongueEatDelay = 0;  // cancel pending eat if player unmorphs mid-tongue
            tongueTarget   = null;
            return;
        }

        // Clear leap once player lands
        if (leapTimer > 0 && client.player.onGround()) {
            leapTimer = 0;
        }

        if (croakTimer  > 0) croakTimer--;
        if (leapTimer   > 0) leapTimer--;
        if (tongueTimer > 0) tongueTimer--;

        // Phase 2: after 6-tick pull window, send eat packet
        if (tongueEatDelay > 0) {
            tongueEatDelay--;
            if (tongueEatDelay == 0 && tongueTarget != null) {
                MorphState.sendAbilityAction("frog_tongue_eat", tongueTarget.toString());
                // Eat sound plays at eat phase, not at trigger time (matches vanilla timing)
                if (client.level != null) {
                    client.level.playLocalSound(
                            client.player.getX(), client.player.getY(), client.player.getZ(),
                            SoundEvents.FROG_EAT, SoundSource.PLAYERS,
                            2.0F, 1.0F, false
                    );
                    MorphState.broadcastSound(SoundEvents.FROG_EAT, 2.0F, 1.0F);
                }
                tongueTarget = null;
            }
        }
    }

    // ── Animate ───────────────────────────────────────────────────────────────
    public static void tickAnimators(Minecraft client) {
        if (client.player == null) return;

        // Local player
        if (MorphState.getCurrentMorph() == EntityType.FROG
                && MorphState.getCachedEntity() instanceof Frog frog) {
            driveAnimators(frog, client.player, croakTimer > 0, leapTimer > 0, tongueTimer > 0);
        }

        // Remote players
        if (client.level != null) {
            for (var p : client.level.players()) {
                var data = RemoteMorphState.get(p.getUUID());
                if (data != null && data.cachedEntity instanceof Frog frog) {
                    driveAnimators(frog, p,
                            data.frogCroaking,
                            data.frogLeaping,
                            data.frogTongue);
                }
            }
        }
    }

    private static void driveAnimators(Frog frog, net.minecraft.world.entity.player.Player player,
                                       boolean croaking, boolean leaping, boolean tongue) {
        frog.tickCount = player.tickCount;

        // Jump animation
        if (leaping) {
            frog.jumpAnimationState.startIfStopped(frog.tickCount);
        } else {
            frog.jumpAnimationState.stop();
        }

        // Croak animation
        if (croaking) {
            frog.croakAnimationState.startIfStopped(frog.tickCount);
        } else {
            frog.croakAnimationState.stop();
        }

        // Tongue animation
        if (tongue) {
            frog.tongueAnimationState.startIfStopped(frog.tickCount);
        } else {
            frog.tongueAnimationState.stop();
        }

        // Swim idle — automatic when in water and not moving
        boolean swimIdle = player.isInWater() && !player.walkAnimation.isMoving();
        frog.swimIdleAnimationState.animateWhen(swimIdle, frog.tickCount);
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public static boolean isCroaking()  { return croakTimer  > 0; }
    public static boolean isLeaping()   { return leapTimer   > 0; }
    public static boolean isTonguing()  { return tongueTimer > 0; }
}