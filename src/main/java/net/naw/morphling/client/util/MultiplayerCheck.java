package net.naw.morphling.client.util;

import net.minecraft.client.Minecraft;

public class MultiplayerCheck {
    public static boolean serverHasMorphling = false;

    public static boolean isOnMultiplayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() == null) return false;
        if (mc.hasSingleplayerServer()) return false;

        return !serverHasMorphling;
    }
}