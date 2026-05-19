package net.naw.morphling.client.health;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.client.core.MorphState;

public class HealthSync {

    private static final Identifier MODIFIER_ID = Identifier.fromNamespaceAndPath("morphling", "morph_health");
    private static final int TRANSITION_TICKS = 20; // 2 seconds

    private static float savedRatio = 1.0F;
    private static boolean wasMorphedLastTick = false;
    private static float currentModifier = 0F;
    private static float targetModifier = 0F;
    private static int transitionTicks = 0;

    public static void onMorph(net.minecraft.world.entity.Entity morphEntity) {
        if (!(morphEntity instanceof LivingEntity morph)) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        // Save current health ratio so we scale HP when max health changes
        savedRatio = player.getHealth() / player.getMaxHealth();

        float morphMaxHealth = morph.getMaxHealth();



        targetModifier = morphMaxHealth - 20.0F;
        transitionTicks = TRANSITION_TICKS;

        // Apply immediately on singleplayer/LAN host server side so damage is real
        applyServerHealth(player, morphMaxHealth);

        // Send health request to server for LAN/multiplayer
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new net.naw.morphling.network.MorphlingNetworking.HealthRequestPayload(morphMaxHealth, savedRatio)
        );
    }

    public static void onUnmorph() {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            savedRatio = player.getHealth() / player.getMaxHealth();
        }

        targetModifier = 0F;
        transitionTicks = TRANSITION_TICKS;

        // Restore full 20 HP on server side
        applyServerHealth(player, 20.0F);

        // Restore health on server for LAN/multiplayer
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new net.naw.morphling.network.MorphlingNetworking.HealthRequestPayload(20.0F, savedRatio)
        );
    }

    public static void tick() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        boolean morphedNow = MorphState.isMorphed();

        if (!morphedNow && wasMorphedLastTick) {
            onUnmorph();
        }

        wasMorphedLastTick = morphedNow;

        // Smoothly lerp current modifier toward target
        if (transitionTicks > 0) {
            float diff = targetModifier - currentModifier;
            currentModifier += diff / transitionTicks;
            transitionTicks--;

            applyModifier(player);

            // Keep same HP percentage during transition
            player.setHealth(savedRatio * player.getMaxHealth());

            if (transitionTicks == 0) {
                currentModifier = targetModifier;
            }
        } else {
            applyModifier(player);
        }
    }

    private static void applyModifier(Player player) {
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;

        attr.removeModifier(MODIFIER_ID);

        if (currentModifier != 0F) {
            attr.addTransientModifier(new AttributeModifier(
                    MODIFIER_ID,
                    currentModifier,
                    AttributeModifier.Operation.ADD_VALUE
            ));
        }
    }

    /**
     * Applies the morph's max health on the server side (singleplayer/LAN host).
     * This makes damage real — a chicken morph actually has 4 HP server-side,
     * not just visually. Dedicated multiplayer packet syncing is TODO.
     */
    private static void applyServerHealth(Player player, float morphMaxHealth) {
        if (player == null) return;

        var minecraft = Minecraft.getInstance();
        var server = minecraft.getSingleplayerServer();

        // Only run on integrated server
        if (server == null || !minecraft.isLocalServer()) return;

        final float targetMax = morphMaxHealth;
        final float ratio = savedRatio;

        server.execute(() -> {
            var sp = server.getPlayerList().getPlayer(player.getUUID());
            if (sp == null) return;

            AttributeInstance attr = sp.getAttribute(Attributes.MAX_HEALTH);
            if (attr == null) return;

            attr.removeModifier(MODIFIER_ID);

            float modifier = targetMax - 20.0F;

            if (modifier != 0F) {
                attr.addTransientModifier(new AttributeModifier(
                        MODIFIER_ID,
                        modifier,
                        AttributeModifier.Operation.ADD_VALUE
                ));
            }

            // Scale current health to match new max
            float newHealth = ratio * sp.getMaxHealth();

            newHealth = Math.clamp(newHealth, 1.0F, sp.getMaxHealth());

            sp.setHealth(newHealth);
        });
    }

    /**
     * Re-applies morph health after respawn.
     */
    public static void onRespawn() {
        if (!MorphState.isMorphed()) return;

        savedRatio = 1.0F;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        applyServerHealth(player, 20.0F + targetModifier);

        // Re-send health request to server after respawn
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new net.naw.morphling.network.MorphlingNetworking.HealthRequestPayload(20.0F + targetModifier, savedRatio)
        );
    }

    /**
     * Called when server confirms the new health values for this morphed player.
     */
    public static void onHealthUpdate(float maxHealth, float currentHealth) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        savedRatio = currentHealth / maxHealth;
        targetModifier = maxHealth - 20.0F;
        transitionTicks = TRANSITION_TICKS;

        player.setHealth(currentHealth);
    }
}