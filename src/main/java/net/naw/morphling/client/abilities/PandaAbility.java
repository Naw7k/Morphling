package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.panda.Panda;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.client.core.RemoteMorphState;
import net.naw.morphling.mixin.accessors.PandaAnimAccessor;

/**
 * Panda morph ability.

 * R         = sit toggle (auto-cancels on movement)
 * Shift+R   = roll (playful flip, auto-stops after 32 ticks)
 * Ctrl+R    = lie on back toggle (lazy)
 * Shift+B   = sneeze (head tilt + sneeze particle + sound)
 * B         = ambient sound (free via playMorphSound)

 * Eating animation plays automatically when sitting + using a food item.

 * Animation fields (sitAmount, onBackAmount, rollAmount, rollCounter) are
 * private on Panda — accessed via PandaAnimAccessor.
 * panda.tickCount is advanced in driveAnimations so ageInTicks-driven
 * wiggles (lie-on-back legs, eating chew) actually animate.
 */
@SuppressWarnings("unused")
public class PandaAbility {

    // ── State ─────────────────────────────────────────────────────────────────
    private static boolean sitting    = false;
    private static boolean onBack     = false;
    private static boolean rolling    = false;
    private static boolean sneezing   = false;
    private static boolean eating     = false;
    private static int     sneezeCounter = 0;

    private static final long COOLDOWN_MS = 500;
    private static long lastActionTime = 0L;

    // ── R — Sit toggle ────────────────────────────────────────────────────────
    public static void toggleSit(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.PANDA) return;
        if (client.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastActionTime < COOLDOWN_MS) return;
        lastActionTime = now;

        onBack  = false;
        rolling = false;
        sitting = !sitting;

        MorphState.sendAbilityState("panda_sitting",  String.valueOf(sitting));
        MorphState.sendAbilityState("panda_on_back",  "false");
        MorphState.sendAbilityState("panda_rolling",  "false");
    }

    // ── Shift+R — Roll ────────────────────────────────────────────────────────
    public static void triggerRoll(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.PANDA) return;
        if (client.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastActionTime < COOLDOWN_MS) return;
        lastActionTime = now;

        sitting = false;
        onBack  = false;
        rolling = true;

        MorphState.sendAbilityState("panda_sitting",  "false");
        MorphState.sendAbilityState("panda_on_back",  "false");
        MorphState.sendAbilityState("panda_rolling",  "true");
    }

    // ── Ctrl+R — Lie on back ──────────────────────────────────────────────────
    public static void toggleOnBack(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.PANDA) return;
        if (client.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastActionTime < COOLDOWN_MS) return;
        lastActionTime = now;

        sitting = false;
        rolling = false;
        onBack  = !onBack;

        MorphState.sendAbilityState("panda_sitting",  "false");
        MorphState.sendAbilityState("panda_on_back",  String.valueOf(onBack));
        MorphState.sendAbilityState("panda_rolling",  "false");
    }

    // ── Shift+B — Sneeze ──────────────────────────────────────────────────────
    public static void triggerSneeze(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.PANDA) return;
        if (client.player == null || client.level == null) return;
        if (sneezing) return;

        sneezing      = true;
        sneezeCounter = 0;

        client.level.playLocalSound(
                client.player.getX(), client.player.getY(), client.player.getZ(),
                SoundEvents.PANDA_PRE_SNEEZE, SoundSource.PLAYERS,
                1.0F, 1.0F, false
        );
        MorphState.broadcastSound(SoundEvents.PANDA_PRE_SNEEZE, 1.0F, 1.0F);
        MorphState.sendAbilityState("panda_sneezing", "true");
        MorphState.sendAbilityAction("panda_sneeze_start", "");
    }

    // ── Tick ──────────────────────────────────────────────────────────────────
    public static void tick(Minecraft client) {
        if (client.player == null) return;

        if (MorphState.getCurrentMorph() != EntityType.PANDA) {
            sitting      = false;
            onBack       = false;
            rolling      = false;
            sneezing     = false;
            eating       = false;
            sneezeCounter = 0;
            return;
        }

        // Cancel sit and on-back if player starts moving
        if (sitting || onBack) {
            var vel = client.player.getDeltaMovement();
            if (Math.abs(vel.x) > 0.03 || Math.abs(vel.z) > 0.03) {
                sitting = false;
                onBack  = false;
                eating  = false;
                MorphState.sendAbilityState("panda_sitting", "false");
                MorphState.sendAbilityState("panda_on_back", "false");
                MorphState.sendAbilityState("panda_eating",  "false");
            }
        }

        // Eating — only while sitting and using a food item
        boolean newEating = sitting
                && client.player.isUsingItem()
                && client.player.getUseItem().has(DataComponents.FOOD);
        if (newEating != eating) {
            eating = newEating;
            MorphState.sendAbilityState("panda_eating", String.valueOf(eating));
        }

        // Drive sneeze counter — 20 ticks then done
        if (sneezing) {
            sneezeCounter++;
            if (sneezeCounter >= 20) {
                sneezing      = false;
                sneezeCounter = 0;
                MorphState.sendAbilityState("panda_sneezing", "false");
                if (client.level != null) {
                    client.level.playLocalSound(
                            client.player.getX(), client.player.getY(), client.player.getZ(),
                            SoundEvents.PANDA_SNEEZE, SoundSource.PLAYERS,
                            1.0F, 1.0F, false
                    );
                    MorphState.broadcastSound(SoundEvents.PANDA_SNEEZE, 1.0F, 1.0F);
                }
                MorphState.sendAbilityAction("panda_sneeze_finish", "");
            }
        }

        // Auto-stop rolling after 32 ticks (matches vanilla TOTAL_ROLL_STEPS)
        if (rolling) {
            if (MorphState.getCachedEntity() instanceof Panda panda) {
                PandaAnimAccessor acc = (PandaAnimAccessor) panda;
                if (acc.morphling$getRollCounter() >= 32) {
                    rolling = false;
                    MorphState.sendAbilityState("panda_rolling", "false");
                }
            }
        }
    }

    // ── Animate ───────────────────────────────────────────────────────────────
    public static void tickAnimators(Minecraft client) {
        if (client.player == null) return;

        // Local player
        if (MorphState.getCurrentMorph() == EntityType.PANDA
                && MorphState.getCachedEntity() instanceof Panda panda) {
            driveAnimations(panda, sitting, onBack, rolling, sneezing, sneezeCounter, eating);
        }

        // Remote players
        if (client.level != null) {
            for (var p : client.level.players()) {
                var data = RemoteMorphState.get(p.getUUID());
                if (data != null && data.cachedEntity instanceof Panda panda) {
                    driveAnimations(panda,
                            data.pandaSitting,
                            data.pandaOnBack,
                            data.pandaRolling,
                            data.pandaSneezing,
                            data.pandaSneezeCounter,
                            data.pandaEating);
                }
            }
        }
    }

    private static void driveAnimations(Panda panda, boolean sit, boolean back,
                                        boolean roll, boolean sneeze, int sneezeTick,
                                        boolean isEating) {
        PandaAnimAccessor acc = (PandaAnimAccessor) panda;

        // Advance tickCount so ageInTicks-driven animations (lie-on-back wiggle,
        // eating chew) actually animate. Without this the local cached entity's
        // tickCount stays frozen and Mth.sin(constant) produces no motion.
        panda.tickCount++;

        // ── Sit ───────────────────────────────────────────────────────────────
        acc.morphling$sit(sit);
        float sitCurrent = acc.morphling$getSitAmount();
        acc.morphling$setSitAmountO(sitCurrent);
        acc.morphling$setSitAmount(sit
                ? Math.min(1.0F, sitCurrent + 0.15F)
                : Math.max(0.0F, sitCurrent - 0.19F));

        // ── Eating ────────────────────────────────────────────────────────────
        panda.eat(isEating);

        // ── On back ───────────────────────────────────────────────────────────
        acc.morphling$setOnBack(back);
        float backCurrent = acc.morphling$getOnBackAmount();
        acc.morphling$setOnBackAmountO(backCurrent);
        acc.morphling$setOnBackAmount(back
                ? Math.min(1.0F, backCurrent + 0.15F)
                : Math.max(0.0F, backCurrent - 0.19F));

        // ── Roll ──────────────────────────────────────────────────────────────
        acc.morphling$roll(roll);
        float rollCurrent = acc.morphling$getRollAmount();
        acc.morphling$setRollAmountO(rollCurrent);
        acc.morphling$setRollAmount(roll
                ? Math.min(1.0F, rollCurrent + 0.15F)
                : Math.max(0.0F, rollCurrent - 0.19F));
        if (roll) {
            acc.morphling$setRollCounter(acc.morphling$getRollCounter() + 1);
        } else {
            acc.morphling$setRollCounter(0);
        }

        // ── Sneeze ────────────────────────────────────────────────────────────
        panda.setSneezeCounter(sneeze ? sneezeTick : 0);
        panda.sneeze(sneeze);
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public static boolean isSitting()    { return sitting;  }
    public static boolean isOnBack()     { return onBack;   }
    public static boolean isRolling()    { return rolling;  }
    public static boolean isSneezing()   { return sneezing; }
    public static boolean isEating()     { return eating;   }
    public static int getSneezeCounter() { return sneezeCounter; }
}