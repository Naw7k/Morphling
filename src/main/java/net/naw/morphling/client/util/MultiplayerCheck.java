package net.naw.morphling.client.util;

import net.minecraft.client.Minecraft;

/**
 * Utility for checking the current multiplayer context.

 * serverHasMorphling — set to true when the server sends the handshake packet,
 * confirming Morphling is installed server-side. Gates all multiplayer packet sending.

 * isOnMultiplayer() — returns true only for dedicated servers WITHOUT Morphling.
 * LAN and singleplayer are treated the same way (integrated server path).
 * Used in MorphState.setMorph() to skip client-only morph logic on dedicated servers.
 */
public class MultiplayerCheck {

    // True once the server handshake is received — enables multiplayer features
    public static boolean serverHasMorphling = false;

    /**
     * Returns true if the player is on a dedicated server that does NOT have Morphling.
     * In that case, morphing is disabled since there's no server-side support.
     */
    public static boolean isOnMultiplayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() == null) return false;
        if (mc.hasSingleplayerServer()) return false;
        if (mc.isLocalServer()) return false;
        return !serverHasMorphling;
    }
}