package net.naw.morphling.client.core;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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
import net.naw.morphling.network.MorphlingNetworking;

import java.util.Objects;

/**
 * Central client-side state machine for the local player's morph.

 * Tracks the current morph type, the cached entity used for rendering and attribute reads,
 * flight state, and original player attributes. Also drives per-tick logic for
 * attributes, flight physics, and fall behavior.

 * Key flows:
 *  - setMorph()  → applies attributes, health, hitbox, sends sync to server
 *  - reset()     → restores everything, sends unmorph sync
 *  - tickAttributes() → keeps speed/damage up to date every tick, syncs to server
 *  - tickFlight() → handles parrot/chicken flight physics

 * Note: MorphState is CLIENT-ONLY. Server-side morph tracking uses
 * MorphlingNetworking.playerMorphMap (UUID → entityTypeId).
 */
public class MorphState {

    private static EntityType<?> currentMorph = null;
    private static Entity cachedEntity = null;
    private static boolean flightActive = false;
    private static boolean jumpWasDown = false;
    private static FlightWindSound activeWindSound = null;
    private static int flapSoundTimer = 0;

    // Stored when first morphing so we can restore on unmorph
    private static double originalMovementSpeed = 0.1;
    private static double originalAttackDamage = 1.0;
    private static boolean originalsStored = false;

    // Tracks last damage value sent to server to avoid packet spam
    private static double lastSentDamage = 1.0;

    /**
     * Applies a new morph. Spawns the cached entity, applies attributes and health,
     * refreshes hitbox, triggers transition effect, and syncs to server.
     * No-op on dedicated multiplayer servers (handled via MorphMenuScreen instead).
     */
    public static void setMorph(EntityType<?> type) {

        if (net.naw.morphling.client.util.MultiplayerCheck.isOnMultiplayer()) return;
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.isSpectator()) return;

        boolean shouldTransition = type != currentMorph;

        currentMorph = type;
        cachedEntity = null;
        flightActive = false;
        var resetPlayer = Minecraft.getInstance().player;
        if (resetPlayer != null) {
            resetPlayer.setDeltaMovement(0, 0, 0);
        }
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
            broadcastSound(net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 0.2F, 1.3F);
        }

        net.naw.morphling.client.health.HealthSync.onMorph(getCachedEntity());

        // Save morph to player entity for persistence
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                server.execute(() -> {
                    var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                    if (serverPlayer != null) {
                        ((net.naw.morphling.client.core.MorphDataProvider) serverPlayer).morphling$setSavedMorph(type);
                        ((net.naw.morphling.client.core.MorphDataProvider) serverPlayer).morphling$setSavedVariants(
                                net.naw.morphling.client.core.MorphVariantManager.serializeVariants()
                        );
                    }
                });
            }
        }

        if (type == EntityType.IRON_GOLEM) {
            net.naw.morphling.client.hunger.IronGolemHunger.onMorphToGolem();
        } else {
            net.naw.morphling.client.hunger.IronGolemHunger.onUnmorph();
        }

        // Save morph to NBT on dedicated server
        if (net.naw.morphling.client.util.MultiplayerCheck.serverHasMorphling) {
            String typeId = type != null ? BuiltInRegistries.ENTITY_TYPE.getKey(type).toString() : "";
            ClientPlayNetworking.send(new MorphlingNetworking.SaveMorphPayload(typeId,
                    net.naw.morphling.client.core.MorphVariantManager.serializeVariants()));
        }

        // Sync to server/other players
        sendMorphSync(type);
    }

    /**
     * Applies a morph received from the server — bypasses the multiplayer check.
     * Used when restoring a saved morph on join via MorphRestorePayload.
     */
    public static void setMorphFromServer(EntityType<?> type) {
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.isSpectator()) return;

        boolean shouldTransition = type != currentMorph;
        currentMorph = type;
        cachedEntity = null;
        flightActive = false;
        var resetPlayer = Minecraft.getInstance().player;
        if (resetPlayer != null) {
            resetPlayer.setDeltaMovement(0, 0, 0);
        }
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

        // Resync to server so hitbox and other players are updated
        sendMorphSync(type);
    }

    /**
     * Clears the current morph, restores all attributes and hitbox,
     * triggers transition effect, and syncs unmorph to server.
     */
    public static void reset() {
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.isSpectator()) return;
        boolean wasMorphed = currentMorph != null;
        currentMorph = null;
        cachedEntity = null;
        flightActive = false;
        lastSentDamage = -1.0; // force damage resync on next morph
        refreshPlayerSize();
        restoreOriginalAttributes();
        SkeletonAbility.onMorphChanged(Minecraft.getInstance());

        if (wasMorphed) {
            net.naw.morphling.client.core.MorphTransition.trigger();
            broadcastSound(net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 0.2F, 1.3F);
        }

        net.naw.morphling.client.health.HealthSync.onUnmorph();

        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                server.execute(() -> {
                    var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                    if (serverPlayer != null) {
                        ((net.naw.morphling.client.core.MorphDataProvider) serverPlayer).morphling$setSavedMorph(null);
                    }
                });
            }
        }

        net.naw.morphling.client.hunger.IronGolemHunger.onUnmorph();

        // Clear saved morph on dedicated server
        if (net.naw.morphling.client.util.MultiplayerCheck.serverHasMorphling) {
            ClientPlayNetworking.send(new MorphlingNetworking.SaveMorphPayload("", ""));
        }

        // Sync unmorphed state
        sendMorphSync(null);
    }

    /**
     * Sends a MorphRequestPayload to the server with the current morph type and all variants.
     * Empty entityTypeId signals unmorph. Only sends if serverHasMorphling is true.
     */
    public static void sendMorphSync(EntityType<?> type) {
        if (!net.naw.morphling.client.util.MultiplayerCheck.serverHasMorphling) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String entityTypeId = "";
        if (type != null) {
            entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        }

        String parrotVariant = MorphVariantManager.getParrotVariant() != null
                ? MorphVariantManager.getParrotVariant().name() : "RED_BLUE";

        String catVariant = MorphVariantManager.getCatVariant() != null
                ? MorphVariantManager.getCatVariant().unwrapKey().orElseThrow().identifier().toString() : "";

        String wolfVariant = MorphVariantManager.getWolfVariant() != null
                ? MorphVariantManager.getWolfVariant().unwrapKey().orElseThrow().identifier().toString() : "";

        String cowVariant = MorphVariantManager.getCowVariant() != null
                ? MorphVariantManager.getCowVariant().unwrapKey().orElseThrow().identifier().toString() : "";

        String sheepColor = MorphVariantManager.getSheepColor() != null
                ? MorphVariantManager.getSheepColor().name() : "";

        String pigVariant = MorphVariantManager.getPigVariant() != null
                ? MorphVariantManager.getPigVariant().unwrapKey().orElseThrow().identifier().toString() : "";

        String chickenVariant = MorphVariantManager.getChickenVariant() != null
                ? MorphVariantManager.getChickenVariant().unwrapKey().orElseThrow().identifier().toString() : "";

        String horseColor = MorphVariantManager.getHorseColor().name();
        String horseMarkings = MorphVariantManager.getHorseMarkings().name();

        String villagerProfession = MorphVariantManager.getVillagerProfession() != null
                ? MorphVariantManager.getVillagerProfession().unwrapKey().orElseThrow().identifier().toString() : "";
        String villagerType = MorphVariantManager.getVillagerType() != null
                ? MorphVariantManager.getVillagerType().unwrapKey().orElseThrow().identifier().toString() : "";

        String slimeSize = String.valueOf(MorphVariantManager.getSlimeSize());

        ClientPlayNetworking.send(new MorphlingNetworking.MorphRequestPayload(
                entityTypeId, parrotVariant, catVariant, wolfVariant, cowVariant, sheepColor, pigVariant, chickenVariant, horseColor, horseMarkings, villagerProfession, villagerType, slimeSize
        ));
    }

    /**
     * Applies the morph's movement speed and attack damage to the player.
     * Stores original values on first call. Also syncs attack damage to the
     * integrated server (singleplayer/LAN host path).
     */
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
            double scaledSpeed = morphSpeed.getBaseValue() * (currentMorph == EntityType.DOLPHIN ? 0.08 : currentMorph == EntityType.HORSE ? 0.45 : 0.25);
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

            // Singleplayer / LAN host server sync
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

    /**
     * Called every tick to keep speed and damage attributes correct.
     * On singleplayer/LAN: syncs directly to integrated server.
     * On dedicated server: sends DamageRequestPayload only when value changes.
     * Shows a HUD message when damage indicator debug is enabled.
     */
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

            return;
        }

        if (!(cachedEntity instanceof LivingEntity livingMorph)) return;

        AttributeInstance playerSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance morphSpeed = livingMorph.getAttribute(Attributes.MOVEMENT_SPEED);
        if (playerSpeed != null && morphSpeed != null) {
            //noinspection ExtractMethodRecommender
            double baseScale = 0.25;
            if (currentMorph == EntityType.ENDERMAN) {
                baseScale = EndermanMadMode.isActive() ? 0.5 : 0.3;
            }
            if (currentMorph == EntityType.DOLPHIN) {
                baseScale = 0.08;
            }
            if (currentMorph == EntityType.SLIME) {
                baseScale = 0.0;
            }
            if (currentMorph == EntityType.HORSE) {
                baseScale = 0.45;
            }
            if (currentMorph == EntityType.BEE) {
                baseScale = 0.10;
            }
            double scrollRatio = net.naw.morphling.client.compat.ScrollWalkCompat.getSpeedRatio();
            double scaledSpeed = morphSpeed.getBaseValue() * baseScale * scrollRatio;
            if (player.isSprinting()) {
                scaledSpeed = morphSpeed.getBaseValue() * (baseScale + 0.1) * scrollRatio;
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
            } else if (net.naw.morphling.client.util.MultiplayerCheck.serverHasMorphling) {
                if (lastSentDamage != finalDamage) {
                    lastSentDamage = finalDamage;
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new MorphlingNetworking.DamageRequestPayload((float) finalDamage));
                    if (net.naw.morphling.client.debug.DebugSettings.isDamageIndicatorEnabled()) {
                        net.minecraft.client.Minecraft.getInstance().gui.setOverlayMessage(
                                Component.literal("§eDamage synced: " + String.format("%.1f", finalDamage) + " dmg"), false);
                    }
                }
            }
        }
    }

    /**
     * Handles flight physics for parrot and chicken morphs.
     * Space = toggle flight, Sprint = ascend, Shift = descend, W = thrust forward.
     * Caps horizontal speed at 0.4 blocks/tick.
     */
    public static void tickFlight() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (mc.isPaused()) return;

        if (currentMorph == null || !EntityRegistry.FLYING_MOBS.contains(currentMorph)) {
            if (flightActive) {
                flightActive = false;
                sendAbilityState("flying", "false");
            }
            return;
        }

        if (flightActive && player.isSprinting()) {
            player.setSprinting(false);
        }

        // Bug fix: double-tap-space triggers its own creative/spectator flight mode
        // which bypasses our speed cap and causes the speed bug. Kill it every tick.
        if (flightActive) {
            player.getAbilities().flying = false;
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
                sendAbilityState("flying", String.valueOf(flightActive));
                if (flightActive) {
                    player.setDeltaMovement(player.getDeltaMovement().x, 0.15, player.getDeltaMovement().z);
                    // Immediately kill vanilla flight in case it was triggered by this same tap
                    player.getAbilities().flying = false;

                    if (currentMorph == EntityType.BEE) {
                        net.naw.morphling.client.abilities.BeeAbility.activeBeeSound =
                                net.naw.morphling.client.abilities.BeeAbility.isAngry()
                                        ? new net.naw.morphling.client.sounds.PlayerBeeAggressiveSoundInstance(player)
                                        : new net.naw.morphling.client.sounds.PlayerBeeFlyingSoundInstance(player);
                        mc.getSoundManager().queueTickingSound(
                                net.naw.morphling.client.abilities.BeeAbility.activeBeeSound);
                    }
                }
            }
        }

        if (player.onGround()) {
            if (flightActive) {
                flightActive = false;
                sendAbilityState("flying", "false");
            }
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

        // Bee has no flight sounds
        if (currentMorph == EntityType.BEE) return;

        flapSoundTimer++;
        if (flapSoundTimer >= 12) {
            flapSoundTimer = 0;
            mc.level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.PARROT_FLY,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F, false);
            broadcastSound(net.minecraft.sounds.SoundEvents.PARROT_FLY, 0.8F, 1.0F);
        }
        if (activeWindSound == null || activeWindSound.isStopped()) {
            activeWindSound = new FlightWindSound(player);
            mc.getSoundManager().play(activeWindSound);
        }
        if (activeWindSound != null && activeWindSound.isStopped()) {
            activeWindSound = null;
        }
    }

    /** Slows falling speed for chicken morph — mimics vanilla chicken float. */
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

    /** Slows falling speed for parrot morph when not in active flight. */
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

    public static void tickBeeFall() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.isPaused()) return;
        if (currentMorph != EntityType.BEE) return;
        if (flightActive) return;
        if (player.onGround()) return;
        Vec3 velocity = player.getDeltaMovement();
        if (velocity.y < 0) {
            player.setDeltaMovement(velocity.x, velocity.y * 0.8, velocity.z);
        }
    }

    /**
     * Refreshes the player's hitbox dimensions on both client and server.
     * Must be called after any morph change to ensure collision matches the morph size.
     */
    public static void refreshPlayerSize() {
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

    /** Send visual ability state to server for broadcast to other clients. */
    public static void sendAbilityState(String key, String value) {
        if (!net.naw.morphling.client.util.MultiplayerCheck.serverHasMorphling) return;
        ClientPlayNetworking.send(new MorphlingNetworking.AbilityStatePayload(key, value));
    }

    /** Send a server-side ability action (world effects like explosions, teleports, block changes). */
    public static void sendAbilityAction(String action, String data) {
        ClientPlayNetworking.send(new MorphlingNetworking.AbilityActionPayload(action, data));
    }

    public static EntityType<?> getCurrentMorph() {
        return currentMorph;
    }

    public static Entity getCachedEntity() {
        return cachedEntity;
    }

    /** Broadcast a sound to other players at our position via the server. */
    public static void broadcastSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (!net.naw.morphling.client.util.MultiplayerCheck.serverHasMorphling) return;
        String soundId = Objects.requireNonNull(BuiltInRegistries.SOUND_EVENT.getKey(sound)).toString();
        ClientPlayNetworking.send(new MorphlingNetworking.SoundBroadcastPayload(soundId, volume, pitch));
    }

    public static boolean isMorphed() {
        return currentMorph != null;
    }

    public static void clearOnDisconnect() {
        currentMorph = null;
        cachedEntity = null;
        flightActive = false;
        // Reset server-side hitbox
        var server = Minecraft.getInstance().getSingleplayerServer();
        var player = Minecraft.getInstance().player;
        if (server != null && player != null) {
            server.execute(() -> {
                var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                if (serverPlayer != null) {
                    serverPlayer.refreshDimensions();
                }
            });
        }
    }
}
