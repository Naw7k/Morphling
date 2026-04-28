package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.naw.morphling.client.core.MorphState;

public class DolphinAbility {

    private static int splashCooldown = 0;
    private static int moistness = 2400;

    public static void tick(Minecraft client) {
        if (!MorphState.isMorphed()) return;
        if (client.player == null) return;
        var morph = MorphState.getCachedEntity();
        if (morph == null || morph.getType() != EntityType.DOLPHIN) {
            moistness = 2400;
            return;
        }

        Player player = client.player;

        // Moistness — drain on land, reset in water
        if (player.isInWaterOrRain()) {
            moistness = 2400;
        } else {
            moistness--;
            if (moistness <= 0) {
                var server = client.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> {
                        var sp = server.getPlayerList().getPlayer(player.getUUID());
                        if (sp != null) {
                            sp.hurt(sp.damageSources().dryOut(), 1.0F);
                        }
                    });
                }
            }
        }

        // Sprint in water = Grace boost
        if (player.isSprinting() && player.isInWater()) {
            player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 20, 0, false, false));
        } else {
            if (player.hasEffect(MobEffects.DOLPHINS_GRACE)) {
                player.removeEffect(MobEffects.DOLPHINS_GRACE);
            }
        }

        if (splashCooldown > 0) splashCooldown--;
    }

    public static void doSplashJump() {
        Minecraft client = Minecraft.getInstance();
        if (!MorphState.isMorphed()) return;
        if (client.player == null) return;
        var morph = MorphState.getCachedEntity();
        if (morph == null || morph.getType() != EntityType.DOLPHIN) return;
        if (splashCooldown > 0) return;
        if (!client.player.isInWater()) return;

        Player player = client.player;
        Vec3 look = player.getLookAngle();
        Vec3 current = player.getDeltaMovement();
        player.setDeltaMovement(current.x + look.x * 0.3, 1, current.z + look.z * 0.3);
        splashCooldown = 30;
    }
}