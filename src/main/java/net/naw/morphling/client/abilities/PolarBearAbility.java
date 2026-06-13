package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.client.core.RemoteMorphState;

/**
 * Polar Bear morph ability.

 * R — toggle rear up (stand on hind legs, plays warning roar sound)
 * B — warning roar sound (handled automatically by playMorphSound via B key)

 * Standing state is synced to server via "polar_bear_standing" ability action,
 * which sets DATA_STANDING_ID on the server-side PolarBear entity so the
 * standScale animates correctly on all clients.

 * tickAnimators() drives the standScale on the cached PolarBear entity every
 * frame so the rear-up animation plays smoothly.
 */
@SuppressWarnings("unused")
public class PolarBearAbility {

    // ── Standing ──────────────────────────────────────────────────────────────
    private static boolean standing = false;
    private static final long STAND_COOLDOWN_MS = 500;
    private static long lastStandTime = 0L;
    private static int standTimer = 0;          // ticks remaining standing
    private static final int STAND_TICKS = 60;  // 3 seconds, then auto-drop

    // ── R — Toggle rear up ────────────────────────────────────────────────────
    public static void toggleStand(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.POLAR_BEAR) return;
        if (client.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastStandTime < STAND_COOLDOWN_MS) return;
        lastStandTime = now;

        standing = !standing;
        standTimer = standing ? STAND_TICKS : 0;

        // Sync to server so standScale animates on all clients
        MorphState.sendAbilityAction("polar_bear_stand", Boolean.toString(standing));
        MorphState.sendAbilityState("polar_bear_standing", String.valueOf(standing));

        // Play warning sound when rearing up
        if (standing && client.level != null) {
            client.level.playLocalSound(
                    client.player.getX(), client.player.getY(), client.player.getZ(),
                    SoundEvents.POLAR_BEAR_WARNING, SoundSource.PLAYERS,
                    1.0F, 1.0F, false
            );
            MorphState.broadcastSound(SoundEvents.POLAR_BEAR_WARNING, 1.0F, 1.0F);
        }
    }

    // ── Tick ──────────────────────────────────────────────────────────────────
    public static void tick(Minecraft client) {
        if (client.player == null) return;

        if (MorphState.getCurrentMorph() != EntityType.POLAR_BEAR) {
            if (standing) {
                standing = false;
                standTimer = 0;
                MorphState.sendAbilityAction("polar_bear_stand", "false");
                MorphState.sendAbilityState("polar_bear_standing", "false");
            }
            return;
        }

        // Auto-drop after STAND_TICKS (vanilla bears only rear up briefly to warn)
        if (standing && standTimer > 0) {
            standTimer--;
            if (standTimer == 0) {
                standing = false;
                MorphState.sendAbilityAction("polar_bear_stand", "false");
                MorphState.sendAbilityState("polar_bear_standing", "false");
            }
        }
    }

    // ── Animate ───────────────────────────────────────────────────────────────
    public static void tickAnimators(Minecraft client) {
        if (client.player == null) return;

        // Local player
        if (MorphState.getCurrentMorph() == EntityType.POLAR_BEAR
                && MorphState.getCachedEntity() instanceof PolarBear bear) {
            driveStand(bear, standing);
        }

        // Remote players
        if (client.level != null) {
            for (var p : client.level.players()) {
                var data = RemoteMorphState.get(p.getUUID());
                if (data != null && data.cachedEntity instanceof PolarBear bear) {
                    driveStand(bear, data.polarBearStanding);
                }
            }
        }
    }

    /**
     * Drives the PolarBear entity's clientSideStandAnimation toward the target state.
     * Mirrors vanilla PolarBear.tick(): old = current, then current steps ±1, clamped 0-6.
     * Runs at tick rate (END_CLIENT_TICK); the renderer lerps old→current with
     * partialTick via getStandingAnimationScale(), so the rear-up is smooth.
     * We never call bear.tick() — it calls refreshDimensions() and shakes the model.
     */
    private static void driveStand(PolarBear bear, boolean shouldStand) {
        bear.setStanding(shouldStand); // keep entity flag consistent (harmless, aids any vanilla reads)
        var acc = (net.naw.morphling.mixin.accessors.PolarBearStandAccessor) bear;
        float current = acc.morphling$getStandAnimation();
        acc.morphling$setStandAnimationO(current);
        acc.morphling$setStandAnimation(shouldStand
                ? Math.min(current + 1.0F, 6.0F)
                : Math.max(current - 1.0F, 0.0F));
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public static boolean isStanding() { return standing; }
}