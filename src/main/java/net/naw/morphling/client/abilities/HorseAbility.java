package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.naw.morphling.client.core.MorphState;

public class HorseAbility {

    // Cooldowns
    private static final long REAR_COOLDOWN_MS = 1500;
    private static final long EAT_COOLDOWN_MS = 3000;
    private static long lastRearTime = 0L;
    private static long lastEatTime = 0L;

    // State
    private static boolean isRearing = false;
    private static boolean isEating = false;
    private static int eatTicksRemaining = 0;
    private static final int EAT_DURATION_TICKS = 50;

    // Charged jump state
    private static boolean jumpCharging = false;
    private static int jumpChargeTicks = 0;
    private static final int MAX_CHARGE_TICKS = 40; // 2 seconds max charge

    /** Trigger rear-up animation — horse stands on hind legs */
    public static void triggerRear(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.HORSE) return;
        if (client.player == null || client.level == null) return;
        long now = System.currentTimeMillis();
        if (now - lastRearTime < REAR_COOLDOWN_MS) return;
        lastRearTime = now;

        if (!(MorphState.getCachedEntity() instanceof Horse horse)) return;

        isRearing = true;
        // setStanding(ticks) triggers the rear-up animation for that many ticks
        ((net.naw.morphling.mixin.accessors.AbstractHorseAccessor) horse).morphling$setStanding(30);
        MorphState.sendAbilityState("horse_rearing", "true");

        client.level.playLocalSound(
                client.player.getX(), client.player.getY(), client.player.getZ(),
                SoundEvents.HORSE_ANGRY, SoundSource.PLAYERS,
                0.8F, 1.0F, false
        );
        MorphState.broadcastSound(SoundEvents.HORSE_ANGRY, 0.8F, 1.0F);
    }

    /** Trigger grass eating animation — only works when standing on grass */
    public static void triggerEat(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.HORSE) return;
        if (client.player == null || client.level == null) return;
        long now = System.currentTimeMillis();
        if (now - lastEatTime < EAT_COOLDOWN_MS) return;

        // Only eat when standing on grass
        BlockPos below = client.player.blockPosition().below();
        if (!client.level.getBlockState(below).is(Blocks.GRASS_BLOCK)) return;

        lastEatTime = now;

        if (!(MorphState.getCachedEntity() instanceof Horse horse)) return;

        isEating = true;
        eatTicksRemaining = EAT_DURATION_TICKS;
        ((net.naw.morphling.mixin.accessors.AbstractHorseAccessor) horse).morphling$setEating(true);
        MorphState.sendAbilityState("horse_eating", "true");

        client.level.playLocalSound(
                client.player.getX(), client.player.getY(), client.player.getZ(),
                SoundEvents.HORSE_EAT, SoundSource.PLAYERS,
                0.6F, 1.0F, false
        );
        MorphState.broadcastSound(SoundEvents.HORSE_EAT, 0.6F, 1.0F);
    }

    /** Called when jump key is held — starts charging */
    public static void onJumpPressed(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.HORSE) return;
        if (client.player == null) return;
        if (!client.player.onGround()) return;
        jumpCharging = true;
    }

    /** Called when jump key is released — execute charged jump */
    public static void onJumpReleased(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.HORSE) return;
        if (!jumpCharging) return;
        if (client.player == null) return;

        // Scale jump power 0.4 to 1.0 based on charge time
        float chargeRatio = Math.min((float) jumpChargeTicks / MAX_CHARGE_TICKS, 1.0F);
        double jumpPower = 0.4 + chargeRatio * 0.6;

        client.player.setDeltaMovement(
                client.player.getDeltaMovement().x,
                jumpPower,
                client.player.getDeltaMovement().z
        );

        if (client.level != null) {
            client.level.playLocalSound(
                    client.player.getX(), client.player.getY(), client.player.getZ(),
                    SoundEvents.HORSE_JUMP, SoundSource.PLAYERS,
                    0.4F, 1.0F, false
            );
            MorphState.broadcastSound(SoundEvents.HORSE_JUMP, 0.4F, 1.0F);
        }

        jumpCharging = false;
        jumpChargeTicks = 0;
    }

    public static void tick(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.HORSE) {
            // Reset all state when not a horse
            isRearing = false;
            isEating = false;
            eatTicksRemaining = 0;
            jumpCharging = false;
            jumpChargeTicks = 0;
            return;
        }

        if (client.player == null) return;
        if (!(MorphState.getCachedEntity() instanceof Horse horse)) return;

        // Drive animation floats by ticking the cached entity
        // Drive animation floats by ticking the cached entity
        horse.tickCount = client.player.tickCount;
        horse.setPos(client.player.getX(), client.player.getY(), client.player.getZ());
        try {
            horse.tick();
            horse.setPos(client.player.getX(), client.player.getY(), client.player.getZ());
            horse.setDeltaMovement(0, 0, 0);
        } catch (Exception ignored) {}

        // Tick rear animation — clear flag once horse is no longer standing
        if (isRearing && !horse.isStanding()) {
            isRearing = false;
            MorphState.sendAbilityState("horse_rearing", "false");
        }

        // Tick eat animation — heal player every 25 ticks while eating
        if (isEating) {
            eatTicksRemaining--;

            Player player = client.player;
            if (eatTicksRemaining == 25 || eatTicksRemaining == 10) {
                var server = client.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> {
                        var sp = server.getPlayerList().getPlayer(player.getUUID());
                        if (sp != null && sp.getHealth() < sp.getMaxHealth()) {
                            sp.heal(0.5F);
                        }
                        if (sp != null) {
                            var food = sp.getFoodData();
                            food.setFoodLevel(Math.min(food.getFoodLevel() + 1, 20));
                        }
                    });
                } else {
                    MorphState.sendAbilityAction("sheep_heal", "");
                    MorphState.sendAbilityAction("sheep_hunger", "");
                }
            }

            if (eatTicksRemaining <= 0) {
                isEating = false;
                ((net.naw.morphling.mixin.accessors.AbstractHorseAccessor) horse).morphling$setEating(false);
                MorphState.sendAbilityState("horse_eating", "false");
            }
        }

        // Tick jump charge
        if (jumpCharging) {
            jumpChargeTicks++;
            // Cap at max charge
            if (jumpChargeTicks > MAX_CHARGE_TICKS) {
                jumpChargeTicks = MAX_CHARGE_TICKS;
            }
        }

        // Drive horse animations manually each tick (like WolfAbility does for wolf)
        tickHorseAnimations(horse);
    }

    /** Drives the horse's eat/stand animation floats each tick so they render correctly */
    private static void tickHorseAnimations(AbstractHorse horse) {
        // The animation floats are driven internally by AbstractHorse.tick()
        // which runs on the cached entity since we call tick on it in RemoteMorphState.
        // Nothing extra needed here — vanilla handles it via setEating/setStanding flags.
    }

    public static boolean isJumpCharging() { return jumpCharging; }
    public static int getJumpChargeTicks() { return jumpChargeTicks; }
    public static int getMaxChargeTicks() { return MAX_CHARGE_TICKS; }
}
