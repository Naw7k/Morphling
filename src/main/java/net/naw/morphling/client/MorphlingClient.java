package net.naw.morphling.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.naw.morphling.client.abilities.*;
import net.naw.morphling.client.abilities.SkeletonAbility;
import net.naw.morphling.client.config.HandPlacementConfig;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.client.core.RemoteMorphState;
import net.naw.morphling.client.debug.DamageIndicator;
import net.naw.morphling.client.games.MobBrawl.MobBrawlClient;
import net.naw.morphling.client.games.packet.GamesNetworking;
import net.naw.morphling.client.games.ui.RoomBrowserScreen;
import net.naw.morphling.client.games.packet.RoomsNetworking;
import net.naw.morphling.client.health.HealthSync;
import net.naw.morphling.client.hunger.HungerSync;
import net.naw.morphling.client.ui.MorphMenuScreen;
import net.naw.morphling.client.util.MultiplayerCheck;
import net.naw.morphling.mixin.accessors.LivingEntityAccessor;
import net.naw.morphling.network.MorphlingNetworking;
import org.lwjgl.glfw.GLFW;
import net.naw.morphling.client.abilities.WolfAbility;
import net.naw.morphling.client.abilities.ParrotAbility;
import net.naw.morphling.client.abilities.SheepAbility;
import net.naw.morphling.client.abilities.ZombieAbility;
import net.naw.morphling.client.abilities.IronGolemAbility;
import net.naw.morphling.client.abilities.DolphinAbility;
import net.naw.morphling.client.abilities.HorseAbility;
import net.naw.morphling.client.abilities.VillagerAbility;
import net.naw.morphling.client.abilities.SpiderAbility;
import net.naw.morphling.client.abilities.SlimeAbility;
import net.naw.morphling.client.abilities.BeeAbility;

import java.util.Objects;

/**
 * Client-side entry point for Morphling.

 * Responsibilities:
 *  - Register all client-side packet receivers (handshake, morph sync, ability sync, sounds, health)
 *  - Register keybinds (open menu, play sound, ability, mad mode)
 *  - Drive all per-tick client logic (attributes, flight, abilities, animations, morph sync)
 *  - Handle respawn hitbox refresh for self and remote players
 */
public class MorphlingClient implements ClientModInitializer {

    // Keybinds — registered in onInitializeClient, used throughout the tick loop
    public static KeyMapping openMenuKey;
    public static KeyMapping playSoundKey;
    public static KeyMapping abilityKey;
    public static KeyMapping madModeKey;


    // Cooldown for the manual morph sound (B key) — prevents spam
    private static long lastSoundTime = 0L;
    private static final long SOUND_COOLDOWN_MS = 1500;

    // After respawn, wait a few ticks before refreshing remote players' hitboxes.
    // Needed because other players may not be fully loaded at the exact respawn moment.
    private static int respawnRefreshTicker = 0;


    public static final KeyMapping.Category MORPHLING_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("morphling", "general")
    );

    @Override
    public void onInitializeClient() {

        HandPlacementConfig.loadFromFile();
        net.naw.morphling.client.config.TwoHandsConfig.loadFromFile();

        // ── Handshake: server confirmed it has Morphling ─────────────────────
        // Sets serverHasMorphling = true, which gates all multiplayer packet sending.
        // Also resyncs our morph to the server in case we were already morphed.

        // ── Morph restore: server sends our saved morph on join ──────────────────
        ClientPlayNetworking.registerGlobalReceiver(MorphlingNetworking.MorphRestorePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (payload.entityTypeId().isEmpty()) return;
                    net.minecraft.world.entity.EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                            .getValue(net.minecraft.resources.Identifier.parse(payload.entityTypeId()));

                    var mc = context.client();
                    if (mc.getSingleplayerServer() != null && mc.player != null) {
                        var server = mc.getSingleplayerServer();
                        server.execute(() -> {
                            var sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                            if (sp != null) {
                                String variants = ((net.naw.morphling.client.core.MorphDataProvider) sp).morphling$getSavedVariants();
                                if (variants != null && !variants.isEmpty()) {
                                    mc.execute(() -> {
                                        net.naw.morphling.client.core.MorphVariantManager.deserializeVariants(variants);
                                        if (!Objects.requireNonNull(mc.player).isSpectator()) {
                                            MorphState.setMorphFromServer(type);
                                        }
                                    });
                                } else {
                                    if (!Objects.requireNonNull(mc.player).isSpectator()) {
                                        MorphState.setMorphFromServer(type);
                                    }
                                }
                            }
                        });
                    } else {
                        if (!Objects.requireNonNull(mc.player).isSpectator()) {
                            if (!payload.variants().isEmpty()) {
                                net.naw.morphling.client.core.MorphVariantManager.deserializeVariants(payload.variants());
                            }
                            MorphState.setMorphFromServer(type);
                        }
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(MorphlingNetworking.HandshakePayload.TYPE, (_, context) ->
                context.client().execute(() -> {
                    MultiplayerCheck.serverHasMorphling = true;
                    if (MorphState.isMorphed()) {
                        MorphState.sendMorphSync(MorphState.getCurrentMorph());
                        MorphState.refreshPlayerSize();
                    }
                }));

        // ── Morph sync: another player morphed or unmorphed ──────────────────
        // Updates RemoteMorphState and refreshes their client-side hitbox.
        // Empty entityTypeId = that player unmorphed.
        ClientPlayNetworking.registerGlobalReceiver(MorphlingNetworking.MorphSyncPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            java.util.UUID uuid = payload.playerUuid();
            String typeId = payload.entityTypeId();

            if (typeId == null || typeId.isEmpty()) {
                RemoteMorphState.PlayerMorphData existingData = RemoteMorphState.get(uuid);
                if (existingData != null) {
                    existingData.flying = false;
                    existingData.morphType = null;
                }
                RemoteMorphState.remove(uuid);
                // Refresh hitbox for the remote player so their collision matches morph size
                Minecraft mc = context.client();
                mc.execute(() -> {
                    if (mc.level != null) {
                        for (net.minecraft.world.entity.player.Player p : mc.level.players()) {
                            if (p.getUUID().equals(uuid)) {
                                p.refreshDimensions();
                                p.setBoundingBox(p.getDimensions(p.getPose()).makeBoundingBox(p.position()));
                                break;
                            }
                        }
                    }
                });
                return;
            }

            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(typeId));

            RemoteMorphState.setMorph(uuid, type,
                    payload.parrotVariant(),
                    payload.catVariant(),
                    payload.wolfVariant(),
                    payload.cowVariant(),
                    payload.sheepColor(),
                    payload.pigVariant(),
                    payload.chickenVariant(),
                    payload.horseColor(),
                    payload.horseMarkings(),
                    payload.villagerProfession(),
                    payload.villagerType(),
                    payload.slimeSize(),
                    payload.foxVariant(),
                    payload.rabbitVariant(),
                    payload.axolotlVariant(),
                    payload.frogVariant(),
                    payload.pandaGene()
            );

            // Refresh hitbox for the remote player so their collision matches morph size
            if (context.client().level != null) {
                for (net.minecraft.world.entity.player.Player p : Objects.requireNonNull(context.client().level).players()) {
                    if (p.getUUID().equals(uuid)) {
                        p.refreshDimensions();
                        break;
                    }
                }
            }
        }));

        // ── Entity load: handle respawn ───────────────────────────────────────
        // Fires when the local player entity reloads (e.g. after death/respawn).
        // Re-applies morph health and hitbox, and schedules a delayed remote hitbox refresh.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_LOAD.register((entity, _) -> {
            Minecraft mc = Minecraft.getInstance();

            if (entity == mc.player) {
                respawnRefreshTicker = 5;
            }
        });

        // ── Ability sync: another player's ability state changed ─────────────
        // Key-value pairs update the corresponding field in RemoteMorphState.
        // Used for flying, sitting, angry mode, skeleton bow, creeper swell, etc.
        ClientPlayNetworking.registerGlobalReceiver(MorphlingNetworking.AbilitySyncPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            java.util.UUID uuid = payload.playerUuid();
            RemoteMorphState.PlayerMorphData data = RemoteMorphState.get(uuid);
            if (data == null) return;

            String key = payload.abilityKey();
            String val = payload.value();

            switch (key) {
                case "flying" -> {
                    data.flying = Boolean.parseBoolean(val);
                    if (data.flying && data.morphType == EntityType.BEE) {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.level != null) {
                            for (net.minecraft.world.entity.player.Player rp : mc.level.players()) {
                                if (rp.getUUID().equals(uuid)) {
                                    mc.getSoundManager().queueTickingSound(
                                            new net.naw.morphling.client.sounds.RemoteBeeFlyingSoundInstance(rp, data));
                                    break;
                                }
                            }
                        }
                    }
                }
                case "bee_pollinate" -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level != null) {
                        for (net.minecraft.world.entity.player.Player rp : mc.level.players()) {
                            if (rp.getUUID().equals(uuid)) {
                                for (int i = 0; i < 8; i++) {
                                    double offsetX = (mc.level.getRandom().nextDouble() - 0.5) * 0.6;
                                    double offsetZ = (mc.level.getRandom().nextDouble() - 0.5) * 0.6;
                                    double offsetY = mc.level.getRandom().nextDouble() * 0.5;
                                    mc.level.addParticle(
                                            net.minecraft.core.particles.ParticleTypes.FALLING_NECTAR,
                                            rp.getX() + offsetX,
                                            rp.getY() + offsetY,
                                            rp.getZ() + offsetZ,
                                            0, 0, 0
                                    );
                                }
                                break;
                            }
                        }
                    }
                }

                case "cat_pose"            -> data.catPose = val;
                case "wolf_sitting"        -> data.wolfSitting = Boolean.parseBoolean(val);
                case "wolf_headtilt"       -> data.wolfHeadTilt = Boolean.parseBoolean(val);
                case "wolf_angry"          -> data.wolfAngry = Boolean.parseBoolean(val);
                case "wolf_shaking"        -> data.wolfShaking = Boolean.parseBoolean(val);
                case "parrot_sitting"      -> data.parrotSitting = Boolean.parseBoolean(val);
                case "parrot_dancing"      -> data.parrotDancing = Boolean.parseBoolean(val);
                case "enderman_carried"    -> data.endermanCarriedBlock = val;
                case "skeleton_bow"        -> data.skeletonBowEquipped = Boolean.parseBoolean(val);
                case "skeleton_drawing"    -> data.skeletonDrawingBow = Boolean.parseBoolean(val);
                case "creeper_swell"       -> { try { data.creeperSwellProgress = Float.parseFloat(val); } catch (Exception ignored) {} }
                case "irongolem_flower"    -> data.ironGolemFlower = Boolean.parseBoolean(val);
                case "irongolem_attack"    -> data.ironGolemAttacking = true;
                case "horse_rearing"       -> data.horseRearing = Boolean.parseBoolean(val);
                case "horse_eating"        -> data.horseEating = Boolean.parseBoolean(val);
                case "bee_angry" -> data.beeAngry = Boolean.parseBoolean(val);
                case "fox_sitting"    -> data.foxSitting = Boolean.parseBoolean(val);
                case "fox_sleeping"   -> data.foxSleeping = Boolean.parseBoolean(val);
                case "fox_crouching"  -> data.foxCrouching = Boolean.parseBoolean(val);
                case "fox_interested" -> data.foxInterested = Boolean.parseBoolean(val);
                case "fox_pouncing"   -> data.foxPouncing = Boolean.parseBoolean(val);
                case "rabbit_sitting" -> data.rabbitSitting = Boolean.parseBoolean(val);
                case "polar_bear_standing" -> data.polarBearStanding = Boolean.parseBoolean(val);
                case "panda_sitting"   -> data.pandaSitting  = Boolean.parseBoolean(val);
                case "panda_on_back"   -> data.pandaOnBack   = Boolean.parseBoolean(val);
                case "panda_rolling"   -> data.pandaRolling  = Boolean.parseBoolean(val);
                case "panda_sneezing"  -> {
                    data.pandaSneezing = Boolean.parseBoolean(val);
                    if (!data.pandaSneezing) data.pandaSneezeCounter = 0;
                }
                case "panda_eating"    -> data.pandaEating = Boolean.parseBoolean(val);
                case "axolotl_playdead" -> data.axolotlPlayingDead = Boolean.parseBoolean(val);
                case "frog_croaking"    -> data.frogCroaking = Boolean.parseBoolean(val);
                case "frog_leaping"     -> data.frogLeaping  = Boolean.parseBoolean(val);
                case "frog_tongue"      -> data.frogTongue   = Boolean.parseBoolean(val);
                case "bee_nectar" -> data.beeNectar = Boolean.parseBoolean(val);
                case "bee_roll" -> { try { data.beeRollAmount = Float.parseFloat(val); } catch (Exception ignored) {} }
                case "villager_unhappy"    -> data.villagerUnhappy = Boolean.parseBoolean(val);
                case "villager_sleeping"   -> {
                    data.villagerSleeping = Boolean.parseBoolean(val);
                    if (data.villagerSleeping) {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.level != null) {
                            for (net.minecraft.world.entity.player.Player rp : mc.level.players()) {
                                if (rp.getUUID().equals(uuid)) {
                                    data.villagerSleepYRot = rp.getYRot();
                                    break;
                                }
                            }
                        }
                    }
                }
                case "sheep_eating"        -> { data.sheepEating = Boolean.parseBoolean(val); if (!data.sheepEating) data.sheepEatTick = 0; }
                case "enderman_mad" -> {
                    data.endermanMad = Boolean.parseBoolean(val);
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level != null && data.endermanMad) {
                        for (net.minecraft.world.entity.player.Player rp : mc.level.players()) {
                            if (rp.getUUID().equals(uuid)) {
                                mc.getSoundManager().play(new net.naw.morphling.client.sounds.EndermanStareSound(rp, () -> data.endermanMad));
                                break;
                            }
                        }
                    }
                }
            }
        }));

        // ── Sound at player: play a morph sound at a remote player's position ─
        ClientPlayNetworking.registerGlobalReceiver(MorphlingNetworking.SoundAtPlayerPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            for (net.minecraft.world.entity.player.Player p : mc.level.players()) {
                if (p.getUUID().equals(payload.playerUuid())) {
                    try {
                        SoundEvent sound = BuiltInRegistries.SOUND_EVENT
                                .getValue(Identifier.parse(payload.soundId()));
                        if (sound != null) {
                            mc.level.playLocalSound(
                                    p.getX(), p.getY(), p.getZ(),
                                    sound, SoundSource.PLAYERS,
                                    payload.volume(), payload.pitch(), false
                            );
                        }
                    } catch (Exception ignored) {}
                    break;
                }
            }
        }));

        // ── Health update: server confirmed our new morph health values ────────
        // Re-applies hitbox after health update to ensure collision stays correct.
        ClientPlayNetworking.registerGlobalReceiver(MorphlingNetworking.HealthUpdatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null) return;
                    HealthSync.onHealthUpdate(payload.maxHealth(), payload.currentHealth());
                    MorphState.refreshPlayerSize();
                }));

        // ── Disconnect: wipe all remote morph state ───────────────────────────
        ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> {
            MultiplayerCheck.serverHasMorphling = false;
            RemoteMorphState.clear();
            MorphState.clearOnDisconnect();

            // Clear last joined room so room browser opens fresh on rejoin
            RoomBrowserScreen.lastJoinedRoomId = null;
            RoomBrowserScreen.lastRoomHost     = null;
            RoomBrowserScreen.lastRoomPlayers  = new String[0];
            RoomBrowserScreen.lastRoomName     = null;
            RoomBrowserScreen.roomInProgress   = false;

            // If in spectator, clear saved morph in NBT too
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.isSpectator()) {
                var server = mc.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> {
                        var serverPlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
                        if (serverPlayer != null) {
                            ((net.naw.morphling.client.core.MorphDataProvider) serverPlayer).morphling$setSavedMorph(null);
                        }
                    });
                }
            }
        });

        // Clear brawl session on reconnect to prevent stale HUD
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((_, _, client) ->
                client.execute(MobBrawlClient::clearSession));

        // ── Games networking ─────────────────────────────────────────────────
        GamesNetworking.registerClient();
        RoomsNetworking.registerClient();

        net.naw.morphling.client.games.MobBrawl.MobBrawlNetworking.registerClient();

        // ── Keybinds ──────────────────────────────────────────────────────────
        openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.morphling.open_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                MORPHLING_CATEGORY
        ));

        playSoundKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.morphling.play_sound",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                MORPHLING_CATEGORY
        ));

        abilityKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.morphling.ability",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                MORPHLING_CATEGORY
        ));

        madModeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.morphling.mad_mode",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F,
                MORPHLING_CATEGORY
        ));

        DamageIndicator.register();

        // ── START_CLIENT_TICK: skeleton bow Q-drop prevention ─────────────────
// Consuming the drop key here prevents the Skeleton Bow from being dropped
// while equipped. The bow is a fake item that should never leave inventory.
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (SkeletonAbility.isBowEquipped() && client.player != null) {
                net.minecraft.world.item.ItemStack held = client.player.getMainHandItem();
                boolean holdingSkeletonBow = held.getItem() == net.minecraft.world.item.Items.BOW;
                net.minecraft.network.chat.Component name = holdingSkeletonBow ? held.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME) : null;
                if (name != null && name.getString().equals("Skeleton Bow")) {
                    while (client.options.keyDrop.consumeClick()) {
                        SkeletonAbility.toggleBow(client);
                    }
                }
            }

            if (VillagerAbility.isSleeping() && client.player != null) {
                client.player.xxa = 0;
                client.player.yya = 0;
                client.player.zza = 0;
                //noinspection StatementWithEmptyBody
                while (client.options.keyJump.consumeClick()) {}
                client.options.keyJump.setDown(false);
            }

            if (VillagerAbility.isSleeping() && client.player != null) {
                client.options.keyShift.setDown(false);
            }

            if (MorphState.getCurrentMorph() == EntityType.SLIME && client.player != null) {
                if (client.options.keyJump.consumeClick()) {
                    SlimeAbility.triggerSmallJump(client);
                }
            }
            if (MorphState.getCurrentMorph() == EntityType.SLIME && client.player != null) {
                client.player.xxa = 0;
                client.player.zza = 0;
                client.player.yya = 0;
                client.player.setSpeed(0);
                client.player.setSprinting(false);
                client.options.keySprint.setDown(false);
                //noinspection StatementWithEmptyBody
                while (client.options.keyAttack.consumeClick()) {}
            }
        });

        // ── END_CLIENT_TICK: main per-tick driver ─────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            // Delayed remote hitbox refresh after respawn
            if (respawnRefreshTicker > 0) {
                respawnRefreshTicker--;
                if (respawnRefreshTicker == 0 && client.level != null) {
                    if (client.player != null && !client.player.isSpectator()) {
                        if (MorphState.isMorphed()) {
                            HealthSync.onRespawn();
                            MorphState.refreshPlayerSize();
                        }
                        MorphState.sendAbilityAction("respawn_refresh", "");
                    }
                    for (net.minecraft.world.entity.player.Player p : client.level.players()) {
                        if (p == client.player) continue;
                        RemoteMorphState.PlayerMorphData data = RemoteMorphState.get(p.getUUID());
                        if (data != null && data.cachedEntity != null) {
                            p.refreshDimensions();
                            p.setBoundingBox(p.getDimensions(p.getPose()).makeBoundingBox(p.position()));
                        }
                    }
                }
            }

            // Core morph systems
            MorphState.tickAttributes();
            MorphState.tickChickenFall();
            MorphState.tickFlight();

            // Ability ticks
            CreeperAbility.tick(client);
            MobAbilities.tick(client);
            EndermanMadMode.tick();
            SkeletonAbility.tickCleanup(client);
            SheepAbility.tick(client);
            CatAbility.tick(client);
            WolfAbility.tick(client);
            WolfAngryMode.tick();
            ParrotAbility.tick(client);
            MorphState.tickParrotFall();
            ZombieAbility.tick(client);
            IronGolemAbility.tick(client);
            DolphinAbility.tick(client);
            HorseAbility.tick(client);
            VillagerAbility.tick(client);
            SpiderAbility.tick(client);
            SlimeAbility.tick(client);
            BeeAbility.tick(client);
            FoxAbility.tick(client);
            RabbitAbility.tick(client);
            AxolotlAbility.tick(client);
            AxolotlAbility.tickAnimators(client);
            FrogAbility.tick(client);
            FrogAbility.tickAnimators(client);
            PolarBearAbility.tick(client);
            PolarBearAbility.tickAnimators(client);
            PandaAbility.tick(client);
            PandaAbility.tickAnimators(client);
            MorphState.tickBeeFall();

            if (MorphState.getCurrentMorph() == EntityType.ENDERMAN && client.player != null && client.player.isInWater()) {
                EndermanAbility.tickWaterTeleport(client);
            }

            // Animation and sync
            tickFlapAnimations(client);
            tickMorphSync(client);

            // Health and transition systems
            net.naw.morphling.client.core.MorphTransition.tick();
            HealthSync.tick();
            HungerSync.tick();

            // Tick roulette game — runs at server tick rate (20/s) for accurate countdown
            // Only tick when no screen is open so pause overlay actually pauses the game
            var rouletteGame = net.naw.morphling.client.games.MorphRoulette.MorphRouletteGame.getInstance();
            net.naw.morphling.client.games.MobBrawl.MobBrawlClient.tick(0.05f);
            if (client.screen == null) {
                rouletteGame.tick(0.05f);
            }

            if (rouletteGame.shouldShowEndScreen() && client.screen == null) {
                rouletteGame.markEndScreenShown();
                if (client.level != null && client.player != null) {
                    client.level.playLocalSound(client.player.getX(), client.player.getY(), client.player.getZ(),
                            net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(),
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 0.8f, false);
                    client.level.playLocalSound(client.player.getX(), client.player.getY(), client.player.getZ(),
                            net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(),
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.8f, false);
                }
                client.setScreen(new net.naw.morphling.client.games.MorphRoulette.MorphRouletteScreen(
                        rouletteGame.getScore(), rouletteGame.getSpinCount()));
            }



            // ── Open morph menu (G) ──────────────────────────────────────────
            while (openMenuKey.consumeClick()) {
                // Block all morph menu interaction during countdown and fight start
                if (net.naw.morphling.client.games.MobBrawl.MobBrawlClient.isInCountdown()) continue;
                if (net.naw.morphling.client.games.MobBrawl.MobBrawlClient.getCountdownFlash() > 0f) continue;

                //noinspection IfCanBeSwitch
                if (client.screen == null) {

                    if (net.naw.morphling.client.games.MorphRoulette.MorphRouletteGame.getInstance().isRunning()) {
                        client.setScreen(new net.naw.morphling.client.games.MorphRoulette.MorphRouletteScreen());
                    } else if (net.naw.morphling.client.games.MobBrawl.MobBrawlClient.isActive() && net.naw.morphling.client.games.MobBrawl.MobBrawlClient.getCountdownFlash() <= 0f) {
                        client.setScreen(new net.naw.morphling.client.games.MobBrawl.MobBrawlPauseScreen());
                    } else if (!net.naw.morphling.client.games.MobBrawl.MobBrawlClient.isInCountdown()) {
                        client.setScreen(new MorphMenuScreen());
                    }

                } else if (client.screen instanceof MorphMenuScreen) {
                    client.setScreen(null);
                } else if (client.screen instanceof net.naw.morphling.client.games.MorphRoulette.MorphRouletteScreen) {
                    client.setScreen(null);
                } else if (client.screen instanceof net.naw.morphling.client.games.MobBrawl.MobBrawlPauseScreen) {
                    client.setScreen(null);
                }
            }

            // ── Play morph ambient sound (B) ─────────────────────────────────
            // Shift = alt sound (hiss, etc), Ctrl = third sound (purr, pant, etc)
            while (playSoundKey.consumeClick()) {
                boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(
                        client.getWindow().handle(),
                        org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT
                ) == 1;

                boolean ctrl = org.lwjgl.glfw.GLFW.glfwGetKey(
                        client.getWindow().handle(),
                        org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL
                ) == 1;

                // ── Cat — hiss / purr / ambient ─────────────────────────────
                if (MorphState.getCurrentMorph() == EntityType.CAT) {
                    if (shift) CatAbility.playHiss(client);
                    else if (ctrl) CatAbility.playPurr(client);
                    else playMorphSound(client);

                    // ── Wolf — pant / ambient ────────────────────────────────────
                } else if (MorphState.getCurrentMorph() == EntityType.WOLF) {
                    if (ctrl) WolfAbility.playPant(client);
                    else playMorphSound(client);

                    // ── Iron Golem — repair sound + heal ────────────────────────
                } else if (MorphState.getCurrentMorph() == EntityType.IRON_GOLEM) {
                    if (client.level != null && client.player != null) {
                        if (IronGolemAbility.tryHeal(client)) {
                            client.level.playLocalSound(
                                    client.player.getX(), client.player.getY(), client.player.getZ(),
                                    net.minecraft.sounds.SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS,
                                    0.7F, 1.0F, false
                            );
                            MorphState.broadcastSound(net.minecraft.sounds.SoundEvents.IRON_GOLEM_REPAIR, 0.7F, 1.0F);
                        }
                    }

                    // ── Villager — yes / celebrate / ambient ─────────────────────
                } else if (MorphState.getCurrentMorph() == EntityType.VILLAGER) {
                    if (shift) VillagerAbility.playYes(client);
                    else if (ctrl) VillagerAbility.playCelebrate(client);
                    else VillagerAbility.playAmbient(client);

                    // ── Bee — nectar / pollinate ─────────────────────────────────
                } else if (MorphState.getCurrentMorph() == EntityType.BEE) {
                    if (shift) BeeAbility.toggleNectar();
                    else BeeAbility.triggerPollinate(client);

                } else if (MorphState.getCurrentMorph() == EntityType.FOX) {
                    long now = System.currentTimeMillis();
                    if (shift) { if (now - lastSoundTime >= SOUND_COOLDOWN_MS) { lastSoundTime = now; FoxAbility.playScreech(client); } }
                    else if (ctrl) FoxAbility.playSniff(client);
                    else playMorphSound(client);

                } else if (MorphState.getCurrentMorph() == EntityType.FROG) {
                    FrogAbility.triggerCroak(client);
                } else if (MorphState.getCurrentMorph() == EntityType.PANDA) {
                    if (shift) PandaAbility.triggerSneeze(client);
                    else playMorphSound(client);

                    // ── All others — generic ambient sound ───────────────────────────────────
                } else {
                    playMorphSound(client);
                }
            }



            // ── Toggle mad/angry mode (F) ────────────────────────────────────
            while (madModeKey.consumeClick()) {
                if (MorphState.getCurrentMorph() == EntityType.ENDERMAN) {
                    EndermanMadMode.toggle(client);
                } else if (MorphState.getCurrentMorph() == EntityType.WOLF) {
                    WolfAngryMode.toggle();
                } else if (MorphState.getCurrentMorph() == EntityType.BEE) {
                    BeeAbility.toggleAngry();

                } else if (MorphState.getCurrentMorph() == EntityType.FOX) {
                    FoxAbility.triggerPounce(client);
                } else if (MorphState.getCurrentMorph() == EntityType.FROG) {
                    FrogAbility.triggerLeap(client);
                }
            }

            // ── Use morph ability (R) ────────────────────────────────────────
            // Shift/Ctrl modify what ability fires for some morphs
            while (abilityKey.consumeClick()) {
                boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(
                        client.getWindow().handle(),
                        org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT
                ) == 1;

                boolean ctrl = org.lwjgl.glfw.GLFW.glfwGetKey(
                        client.getWindow().handle(),
                        org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL
                ) == 1;

                if (MorphState.getCurrentMorph() == EntityType.ENDERMAN && shift) {
                    EndermanCarryAbility.trigger(client);
                } else if (MorphState.getCurrentMorph() == EntityType.SKELETON) {
                    SkeletonAbility.toggleBow(client);
                } else if (MorphState.getCurrentMorph() == EntityType.SHEEP) {
                    SheepAbility.trigger(client);
                } else if (MorphState.getCurrentMorph() == EntityType.CAT) {
                    if (shift) CatAbility.toggleLying(client);
                    else if (ctrl) CatAbility.toggleRelaxed(client);
                    else CatAbility.toggleSit(client);
                } else if (MorphState.getCurrentMorph() == EntityType.WOLF) {
                    if (shift) WolfAbility.triggerShake(client);
                    else if (ctrl) WolfAbility.toggleHeadTilt(client);
                    else WolfAbility.toggleSit(client);
                } else if (MorphState.getCurrentMorph() == EntityType.PARROT) {
                    if (shift) ParrotAbility.toggleDance(client);
                    else if (ctrl) ParrotAbility.imitateNearbyMob(client);
                    else ParrotAbility.toggleSit(client);
                } else if (MorphState.getCurrentMorph() == EntityType.IRON_GOLEM) {
                    IronGolemAbility.toggleFlower(client);
                } else if (MorphState.getCurrentMorph() == EntityType.DOLPHIN) {
                    DolphinAbility.doSplashJump();
                } else if (MorphState.getCurrentMorph() == EntityType.CHICKEN) {
                    ChickenAbility.layEgg(client);
                } else if (MorphState.getCurrentMorph() == EntityType.HORSE) {
                    if (shift) HorseAbility.triggerEat(client);
                    else HorseAbility.triggerRear(client);
                } else if (MorphState.getCurrentMorph() == EntityType.VILLAGER) {
                    if (shift) VillagerAbility.toggleSleep(client);
                    else if (ctrl) VillagerAbility.playWorkSound(client);
                    else VillagerAbility.triggerUnhappy(client);
                } else if (MorphState.getCurrentMorph() == EntityType.SPIDER) {
                    SpiderAbility.triggerLeap(client);
                } else if (MorphState.getCurrentMorph() == EntityType.SLIME) {
                    SlimeAbility.triggerBigJump(client);
                } else if (MorphState.getCurrentMorph() == EntityType.BEE) {
                    if (shift) BeeAbility.triggerRoll();
                    else BeeAbility.triggerSting(client);
                } else if (MorphState.getCurrentMorph() == EntityType.FOX) {
                    if (shift) FoxAbility.toggleSleep(client);
                    else if (ctrl) FoxAbility.toggleCrouch(client);
                    else FoxAbility.toggleSit(client);
                } else if (MorphState.getCurrentMorph() == EntityType.RABBIT) {
                    RabbitAbility.toggleSit();
                } else if (MorphState.getCurrentMorph() == EntityType.AXOLOTL) {
                    AxolotlAbility.togglePlayDead(client);
                } else if (MorphState.getCurrentMorph() == EntityType.FROG) {
                    FrogAbility.triggerTongue(client);
                } else if (MorphState.getCurrentMorph() == EntityType.POLAR_BEAR) {
                    PolarBearAbility.toggleStand(client);
                } else if (MorphState.getCurrentMorph() == EntityType.PANDA) {
                    if (shift) PandaAbility.triggerRoll(client);
                    else if (ctrl) PandaAbility.toggleOnBack(client);
                    else PandaAbility.toggleSit(client);

                } else {
                    MobAbilities.trigger(client);
                }
            }
        });
    }

    /**
     * Plays the morph's ambient sound at the player's position.
     * Broadcasts to other players via SoundBroadcastPayload.
     * Respects the ambient volume multiplier from SoundConfig.
     */
    private static void playMorphSound(Minecraft client) {
        if (client.player == null || client.level == null) return;
        if (!MorphState.isMorphed()) return;

        long now = System.currentTimeMillis();
        if (now - lastSoundTime < SOUND_COOLDOWN_MS) return;

        Entity morphEntity = MorphState.getCachedEntity();
        if (!(morphEntity instanceof Mob mobMorph)) return;

        SoundEvent sound = ((LivingEntityAccessor) mobMorph).morphling$getAmbientSound();
        if (sound != null) {
            client.level.playLocalSound(
                    client.player.getX(), client.player.getY(), client.player.getZ(),
                    sound, SoundSource.NEUTRAL,
                    net.naw.morphling.client.debug.SoundConfig.ambientVolumeMultiplier, 1.0F, false
            );
            lastSoundTime = now;
            if (MultiplayerCheck.serverHasMorphling) {
                String soundId = Objects.requireNonNull(BuiltInRegistries.SOUND_EVENT.getKey(sound)).toString();
                ClientPlayNetworking.send(new MorphlingNetworking.SoundBroadcastPayload(soundId, 1.0F, 1.0F));
            }
        }
    }

    /**
     * Ticks wing flap animations for parrot and chicken morphs.
     * Runs every client tick to keep flap speed smooth and frame-rate independent.
     */
    private static void tickFlapAnimations(Minecraft client) {
        if (!MorphState.isMorphed()) return;
        if (client.player == null) return;
        if (client.isPaused()) return;

        var morph = MorphState.getCachedEntity();
        if (morph == null) return;

        if (morph instanceof net.minecraft.world.entity.animal.parrot.Parrot parrot) {
            parrot.oFlap = parrot.flap;
            parrot.oFlapSpeed = parrot.flapSpeed;
            if (MorphState.isFlightActive()) {
                parrot.flap += parrot.flapSpeed * 1.8F;
                parrot.flapSpeed = Math.min(parrot.flapSpeed + 0.30F, 1.0F);
            } else {
                parrot.flapSpeed = Math.max(parrot.flapSpeed - 0.1F, 0.0F);
            }
        }

        if (morph instanceof net.minecraft.world.entity.animal.chicken.Chicken chicken) {
            chicken.oFlap = chicken.flap;
            chicken.oFlapSpeed = chicken.flapSpeed;
            if (!client.player.onGround()) {
                chicken.flapSpeed = 1.0F;
                chicken.flap += chicken.flapSpeed * 1.8F;
            } else {
                chicken.flapSpeed = 0.0F;
            }
        }
    }

    /**
     * Syncs morph entity state with the local player every tick.
     * Keeps tickCount, fallDistance, and EMF variables in sync.
     * Also broadcasts skeleton bow states to other players.
     */
    private static void tickMorphSync(Minecraft client) {
        if (!MorphState.isMorphed()) return;
        if (client.player == null) return;
        var morph = MorphState.getCachedEntity();
        if (morph == null) return;

        // Chicken handles its own tick via tickFlapAnimations
        if (morph instanceof net.minecraft.world.entity.animal.chicken.Chicken) return;

        morph.tickCount = client.player.tickCount;
        morph.fallDistance = client.player.fallDistance;

        net.naw.morphling.client.compat.FaCompat.lockEmfVariables(morph);

        // Sync skeleton bow states every tick so other players see the correct animation
        if (MorphState.getCurrentMorph() == EntityType.SKELETON) {
            MorphState.sendAbilityState("skeleton_bow", String.valueOf(SkeletonAbility.isBowEquipped()));
        }
        if (MorphState.getCurrentMorph() == EntityType.SKELETON && SkeletonAbility.isBowEquipped()) {
            boolean drawing = client.player.isUsingItem() &&
                    client.player.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem;
            MorphState.sendAbilityState("skeleton_drawing", String.valueOf(drawing));
        }
        // Sync frog animation states so other players see croak/leap/tongue animations
        if (MorphState.getCurrentMorph() == EntityType.FROG) {
            MorphState.sendAbilityState("frog_croaking", String.valueOf(FrogAbility.isCroaking()));
            MorphState.sendAbilityState("frog_leaping",  String.valueOf(FrogAbility.isLeaping()));
            MorphState.sendAbilityState("frog_tongue",   String.valueOf(FrogAbility.isTonguing()));
        }
    }
}
