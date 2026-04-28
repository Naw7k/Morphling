package net.naw.morphling.client.core;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.naw.morphling.client.abilities.EndermanMadMode;
import net.naw.morphling.client.sounds.FlightWindSound;
import net.naw.morphling.client.abilities.SkeletonAbility;
import net.naw.morphling.client.config.MorphDamageConfig;


public class MorphState {
    private static EntityType<?> currentMorph = null;
    private static Entity cachedEntity = null;
    private static boolean flightActive = false;
    private static boolean jumpWasDown = false;
    private static FlightWindSound activeWindSound = null;
    private static int flapSoundTimer = 0;

    private static double originalMovementSpeed = 0.1;
    private static double originalAttackDamage = 1.0;
    private static boolean originalsStored = false;


    public static void setMorph(EntityType<?> type) {

        if (net.naw.morphling.client.util.MultiplayerCheck.isOnMultiplayer()) return;


        boolean shouldTransition = (type != currentMorph) && (type != null || currentMorph != null);

        currentMorph = type;
        cachedEntity = null;
        flightActive = false;
        SkeletonAbility.onMorphChanged(Minecraft.getInstance());

        if (type != null) {
            Level world = Minecraft.getInstance().level;
            if (world != null) {
                cachedEntity = type.create(world, EntitySpawnReason.LOAD);
                MorphVariantManager.applyVariant(cachedEntity);
            }
        }

        refreshPlayerSize();
        applyMorphAttributes();

        if (shouldTransition) {
            net.naw.morphling.client.core.MorphTransition.trigger();
        }

        net.naw.morphling.client.health.HealthSync.onMorph(getCachedEntity());
    }

    public static void reset() {
        boolean wasMorphed = currentMorph != null;

        currentMorph = null;
        cachedEntity = null;
        flightActive = false;
        refreshPlayerSize();
        restoreOriginalAttributes();
        SkeletonAbility.onMorphChanged(Minecraft.getInstance());

        if (wasMorphed) {
            net.naw.morphling.client.core.MorphTransition.trigger();
        }

        net.naw.morphling.client.health.HealthSync.onUnmorph();
    }

    private static void applyMorphAttributes() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!(cachedEntity instanceof LivingEntity livingMorph)) {
            restoreOriginalAttributes();
            return;
        }

        if (!originalsStored) {
            AttributeInstance playerSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            AttributeInstance playerDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (playerSpeed != null) originalMovementSpeed = playerSpeed.getBaseValue();
            if (playerDamage != null) originalAttackDamage = playerDamage.getBaseValue();
            originalsStored = true;
        }

        AttributeInstance playerSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance morphSpeed = livingMorph.getAttribute(Attributes.MOVEMENT_SPEED);
        if (playerSpeed != null && morphSpeed != null) {
            double scaledSpeed = morphSpeed.getBaseValue() * 0.25;
            playerSpeed.setBaseValue(scaledSpeed);
        }

        AttributeInstance playerDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance morphDamage = livingMorph.getAttribute(Attributes.ATTACK_DAMAGE);
        if (playerDamage != null) {
            final double targetDamage;
            if (morphDamage != null) {
                targetDamage = morphDamage.getBaseValue();
            } else {
                targetDamage = originalAttackDamage;
            }
            playerDamage.setBaseValue(targetDamage);

            var server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                    if (serverPlayer != null) {
                        var serverPlayerDamage = serverPlayer.getAttribute(Attributes.ATTACK_DAMAGE);
                        if (serverPlayerDamage != null) {
                            serverPlayerDamage.setBaseValue(targetDamage);
                        }
                    }
                });
            }
        }
    }

    private static void restoreOriginalAttributes() {
        Player player = Minecraft.getInstance().player;
        if (player == null || !originalsStored) return;

        AttributeInstance playerSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance playerDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (playerSpeed != null) playerSpeed.setBaseValue(originalMovementSpeed);
        if (playerDamage != null) playerDamage.setBaseValue(originalAttackDamage);

        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                if (serverPlayer != null) {
                    var serverDamage = serverPlayer.getAttribute(Attributes.ATTACK_DAMAGE);
                    var serverSpeed = serverPlayer.getAttribute(Attributes.MOVEMENT_SPEED);
                    if (serverDamage != null) serverDamage.setBaseValue(originalAttackDamage);
                    if (serverSpeed != null) serverSpeed.setBaseValue(originalMovementSpeed);
                }
            });
        }
    }

    public static boolean isFlightActive() {
        return flightActive;
    }

    public static void tickAttributes() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        if (!isMorphed()) {
            AttributeInstance pd = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (pd != null && pd.getBaseValue() != 1.0) {
                pd.setBaseValue(1.0);
            }
            var server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    var sp = server.getPlayerList().getPlayer(player.getUUID());
                    if (sp != null) {
                        var spd = sp.getAttribute(Attributes.ATTACK_DAMAGE);
                        if (spd != null && spd.getBaseValue() != 1.0) {
                            spd.setBaseValue(1.0);
                        }
                    }
                });
            }
            AttributeInstance ps = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (ps != null && ps.getBaseValue() != 0.1) {
                ps.setBaseValue(0.1);
            }
            return;
        }

        if (!(cachedEntity instanceof LivingEntity livingMorph)) return;

        AttributeInstance playerSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance morphSpeed = livingMorph.getAttribute(Attributes.MOVEMENT_SPEED);
        if (playerSpeed != null && morphSpeed != null) {
            double baseScale = 0.25;
            if (currentMorph == EntityType.ENDERMAN) {
                baseScale = EndermanMadMode.isActive() ? 0.5 : 0.3;
            }
            double scaledSpeed = morphSpeed.getBaseValue() * baseScale;
            if (player.isSprinting()) {
                scaledSpeed = morphSpeed.getBaseValue() * (baseScale + 0.1);
            }
            if (playerSpeed.getBaseValue() != scaledSpeed) {
                playerSpeed.setBaseValue(scaledSpeed);
            }
        }

        AttributeInstance playerDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (playerDamage != null) {
            AttributeInstance morphDamage = livingMorph.getAttribute(Attributes.ATTACK_DAMAGE);
            double baseDamage;
            double override = MorphDamageConfig.getOverride(currentMorph);
            if (override >= 0) {
                baseDamage = override;
            } else if (morphDamage != null) {
                baseDamage = morphDamage.getBaseValue();
                if (currentMorph == EntityType.ENDERMAN && EndermanMadMode.isActive()) {
                    baseDamage *= 1.5;
                }
            } else {
                baseDamage = originalAttackDamage;
            }
            if (playerDamage.getBaseValue() != baseDamage) {
                playerDamage.setBaseValue(baseDamage);
            }
            final double finalDamage = baseDamage;
            var server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                    if (serverPlayer != null) {
                        var serverPlayerDamage = serverPlayer.getAttribute(Attributes.ATTACK_DAMAGE);
                        if (serverPlayerDamage != null && serverPlayerDamage.getBaseValue() != finalDamage) {
                            serverPlayerDamage.setBaseValue(finalDamage);
                        }
                    }
                });
            }
        }
    }

    public static void tickFlight() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (mc.isPaused()) return;

        if (currentMorph == null || !EntityRegistry.FLYING_MOBS.contains(currentMorph)) {
            flightActive = false;
            return;
        }

        if (flightActive && player.isSprinting()) {
            player.setSprinting(false);
        }

        if (flightActive) {
            playFlightSounds(mc, player);
        }

        boolean inMenu = mc.screen != null;

        if (!inMenu) {
            boolean jumpDown = mc.options.keyJump.isDown();
            boolean jumpJustPressed = jumpDown && !jumpWasDown;
            jumpWasDown = jumpDown;

            if (jumpJustPressed) {
                flightActive = !flightActive;
                if (flightActive) {
                    player.setDeltaMovement(player.getDeltaMovement().x, 0.15, player.getDeltaMovement().z);
                }
            }
        }

        if (player.onGround()) {
            flightActive = false;
            flapSoundTimer = 0;
            activeWindSound = null;
            return;
        }

        if (!flightActive) return;

        Vec3 velocity = player.getDeltaMovement();
        double newY = (velocity.y + 0.078) * 0.9;
        player.setDeltaMovement(velocity.x * 0.95, newY, velocity.z * 0.95);
        player.resetFallDistance();

        if (inMenu) return;

        if (mc.options.keySprint.isDown()) {
            player.setDeltaMovement(player.getDeltaMovement().x, player.getDeltaMovement().y + 0.012, player.getDeltaMovement().z);
        }
        if (mc.options.keyShift.isDown()) {
            player.setDeltaMovement(player.getDeltaMovement().x, player.getDeltaMovement().y - 0.012, player.getDeltaMovement().z);
        }
        if (mc.options.keyUp.isDown()) {
            Vec3 look = player.getLookAngle();
            double thrust = 0.04;
            double vThrust = look.y > 0 ? look.y * thrust * 0.7 : look.y * thrust * 0.6;
            player.setDeltaMovement(player.getDeltaMovement().x + look.x * thrust, player.getDeltaMovement().y + vThrust, player.getDeltaMovement().z + look.z * thrust);
        }

        Vec3 current = player.getDeltaMovement();
        double maxSpeed = 0.4;
        double horizSpeedSq = current.x * current.x + current.z * current.z;
        if (horizSpeedSq > maxSpeed * maxSpeed) {
            double scale = maxSpeed / Math.sqrt(horizSpeedSq);
            player.setDeltaMovement(current.x * scale, current.y, current.z * scale);
        }
    }

    private static void playFlightSounds(Minecraft mc, Player player) {
        if (mc.level == null) return;
        flapSoundTimer++;
        if (flapSoundTimer >= 12) {
            flapSoundTimer = 0;
            mc.level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.PARROT_FLY,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F, false);
        }
        if (activeWindSound == null || activeWindSound.isStopped()) {
            activeWindSound = new FlightWindSound(player);
            mc.getSoundManager().play(activeWindSound);
        }
        if (activeWindSound != null && activeWindSound.isStopped()) {
            activeWindSound = null;
        }
    }

    public static void tickChickenFall() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.isPaused()) return;
        if (currentMorph != EntityType.CHICKEN) return;
        if (player.onGround()) return;
        Vec3 velocity = player.getDeltaMovement();
        if (velocity.y < 0) {
            player.setDeltaMovement(velocity.x, velocity.y * 0.6, velocity.z);
        }
    }

    public static void tickParrotFall() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.isPaused()) return;
        if (currentMorph != EntityType.PARROT) return;
        if (flightActive) return;
        if (player.onGround()) return;
        Vec3 velocity = player.getDeltaMovement();
        if (velocity.y < 0) {
            player.setDeltaMovement(velocity.x, velocity.y * 0.8, velocity.z);
        }
    }

    private static void refreshPlayerSize() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.refreshDimensions();
            player.setBoundingBox(player.getDimensions(player.getPose()).makeBoundingBox(player.position()));
            var server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                    if (serverPlayer != null) {
                        serverPlayer.refreshDimensions();
                    }
                });
            }
        }
    }

    public static EntityType<?> getCurrentMorph() {
        return currentMorph;
    }

    public static Entity getCachedEntity() {
        return cachedEntity;
    }

    public static boolean isMorphed() {
        return currentMorph != null;
    }
}