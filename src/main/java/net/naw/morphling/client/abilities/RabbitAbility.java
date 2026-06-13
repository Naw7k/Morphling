package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.naw.morphling.client.core.MorphState;

/**
 * Rabbit morph hop movement.

 * Runs on the CLIENT TICK (20/sec). Base movement speed is set to 0 in MorphState
 * so the player can't walk — all movement comes from hop impulses fired here.
 * The renderer reads isHopping() / isMoving() to drive the hop animation.
 */
@SuppressWarnings("unused")
public class RabbitAbility {

    private static boolean hopping  = false;
    private static boolean moving   = false;
    private static int     cooldown = 0;

    // Hop tuning — interval controls cadence, Y = height, H = forward distance
    private static final int    HOP_INTERVAL_WALK   = 14;
    private static final int    HOP_INTERVAL_SPRINT = 8;
    private static final double HOP_POWER_WALK_Y    = 0.28;
    private static final double HOP_POWER_SPRINT_Y  = 0.38;
    private static final double HOP_POWER_WALK_H    = 0.18;
    private static final double HOP_POWER_SPRINT_H  = 0.30;

    public static void tick(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.RABBIT || !(client.player instanceof LocalPlayer player)) {
            hopping  = false;
            moving   = false;
            sitting = false;
            cooldown = 0;
            return;
        }

        if (cooldown > 0) cooldown--;

        // Movement intent — only true when actually pressing a key
        moving = player.zza != 0.0F || player.xxa != 0.0F;

        if (moving && sitting) {
            sitting = false;
            MorphState.sendAbilityState("rabbit_sitting", "false");
        }

        if (player.onGround()) {
            hopping = false;
            if (moving && cooldown == 0) {
                // Fire hop — use look direction for forward thrust
                boolean sprint = player.isSprinting();
                double powY = sprint ? HOP_POWER_SPRINT_Y : HOP_POWER_WALK_Y;
                double powH = sprint ? HOP_POWER_SPRINT_H : HOP_POWER_WALK_H;

                Vec3 look = player.getLookAngle();
                double len = Math.sqrt(look.x * look.x + look.z * look.z);
                double dirX = len > 0.0001 ? look.x / len : 0;
                double dirZ = len > 0.0001 ? look.z / len : 0;

                player.setDeltaMovement(dirX * powH, powY, dirZ * powH);
                hopping  = true;
                cooldown = sprint ? HOP_INTERVAL_SPRINT : HOP_INTERVAL_WALK;
            }
        }
        // While airborne — leave velocity alone, hop arc plays out naturally
    }

    private static boolean sitting = false;

    public static void toggleSit() {
        sitting = !sitting;
        MorphState.sendAbilityState("rabbit_sitting", String.valueOf(sitting));
    }

    public static boolean isSitting() { return sitting; }

    /** True while mid-hop — renderer uses this to drive hopAnimationState. */
    public static boolean isHopping() { return hopping; }

    /** True while the player is pressing a movement key. */
    public static boolean isMoving()  { return moving; }
}