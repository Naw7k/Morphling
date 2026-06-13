package net.naw.morphling.client.games.MobBrawl;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.naw.morphling.client.games.GameRoomManager;

import java.util.UUID;

/**
 * Server-side packet receivers for Mob Brawl.

 * No client imports allowed here — this runs on the server thread.
 * Called from MobBrawlNetworking.registerServer().

 * Responsibilities:
 *   - Validate packets (correct player, correct phase, correct room)
 *   - Update MobBrawlServerGame state
 *   - Broadcast updated state back to room players
 *   - Drive phase transitions (lobby → morph select → countdown → fight → end)
 *   - Handle arena teleportation via BrawlDimension (void dimension)
 *   - Apply health config (Equal 20♥ / Double 40♥) at fight start

 * Arenas (handled by BrawlDimension):
 *   0 = No Arena (current world)
 *   1 = Gladiator — stone/sandstone, noon, thunder storm
 *   2 = Nature    — grass, trees, dawn, light rain
 *   3 = Night     — deepslate, glowstone — night dimension (no skybox, always dark)
 *   4 = Ocean     — prismarine, water, noon, clear
 */
public class MobBrawlNetworkingServer {

    /** Modifier ID used by HealthSync — we remove it when overriding health config */
    private static final Identifier HEALTH_MODIFIER_ID = Identifier.fromNamespaceAndPath("morphling", "morph_health");

    // Players currently in their 3s death/respawn window. Guards against double-processing
    // a single death — both the damage mixin (PvP) and the client death packet (fall/fire/
    // self-explosion deaths the mixin can't catch) call handleBrawlDeath, and without this
    // a death could decrement lives twice and fire two respawns.
    private static final java.util.Set<UUID> respawnPending = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // ── Config update (host only) ─────────────────────────────────────────────

    public static void onConfig(MobBrawlNetworking.MobBrawlConfigPayload payload, ServerPlayer player) {
        MinecraftServer server = player.level().getServer();

        MobBrawlServerGame game = MobBrawlServerGame.get(payload.roomId());
        if (game != null) {
            if (!game.isHost(player.getUUID())) return;

            game.applyConfig(
                    payload.healthMode(),
                    payload.abilitiesMode(),
                    payload.damageMode(),
                    payload.timeLimit(),
                    payload.lives(),
                    payload.arenaType()
            );
        }

        GameRoomManager.Room room = GameRoomManager.getInstance().getRoomById(payload.roomId());
        if (room == null) return;
        for (UUID uuid : room.playerUuids) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                ServerPlayNetworking.send(p, new MobBrawlNetworking.MobBrawlConfigSyncPayload(
                        payload.roomId(),
                        payload.healthMode(),
                        payload.abilitiesMode(),
                        payload.damageMode(),
                        payload.timeLimit(),
                        payload.lives(),
                        payload.arenaType()
                ));
            }
        }
    }

    // ── Morph pick ────────────────────────────────────────────────────────────

    public static void onMorphPick(MobBrawlNetworking.MobBrawlMorphPickPayload payload, ServerPlayer player) {
        MobBrawlServerGame game = MobBrawlServerGame.get(payload.roomId());
        if (game == null) return;
        if (game.getPhase() != MobBrawlServerGame.Phase.MORPH_SELECT) return;

        EntityType<?> morph = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(payload.morphTypeId()));
        game.setMorphChoice(player.getUUID(), morph);
        broadcastState(game, player.level().getServer());

        if (game.bothReady()) {
            // Both players picked — start immediately
            game.startCountdown();
            if (game.isArenaMode()) teleportToArena(game, player.level().getServer());
            sendToRoomDirect(game, player.level().getServer(),
                    new MobBrawlNetworking.MobBrawlCountdownPayload(game.roomId, 3));
            scheduleCountdownTicks(game, player.level().getServer());
        }
    }

    // ── Ready signal ──────────────────────────────────────────────────────────

    public static void onReady(MobBrawlNetworking.MobBrawlReadyPayload payload, ServerPlayer player) {
        // Don't start if opponent hasn't joined yet
        GameRoomManager.Room roomCheck = GameRoomManager.getInstance().getRoomById(payload.roomId());
        if (roomCheck == null || roomCheck.playerUuids.size() < 2) return;

        if (!MobBrawlServerGame.exists(payload.roomId())) {
            MobBrawlServerGame.create(payload.roomId(), player.getUUID());
            GameRoomManager.Room room = GameRoomManager.getInstance().getRoomById(payload.roomId());
            if (room != null) {
                for (UUID uuid : room.playerUuids) {
                    if (!uuid.equals(player.getUUID())) {
                        MobBrawlServerGame.get(payload.roomId()).setGuest(uuid);
                        break;
                    }
                }
            }
        }

        MobBrawlServerGame game = MobBrawlServerGame.get(payload.roomId());
        if (game == null) return;

        game.applyConfig(
                payload.healthMode(),
                payload.abilitiesMode(),
                payload.damageMode(),
                payload.timeLimit(),
                payload.lives(),
                payload.arenaType()
        );

        game.startMorphSelect();

        ServerPlayer host  = player.level().getServer().getPlayerList().getPlayer(game.hostUUID);
        ServerPlayer guest = player.level().getServer().getPlayerList().getPlayer(game.guestUUID);
        if (host  != null) ServerPlayNetworking.send(host,  new MobBrawlNetworking.MobBrawlStartPayload(payload.roomId(), 1, true));
        if (guest != null) ServerPlayNetworking.send(guest, new MobBrawlNetworking.MobBrawlStartPayload(payload.roomId(), 1, false));
    }

    // ── Damage report ─────────────────────────────────────────────────────────

    @SuppressWarnings("unused")
    public static void onDamage(MobBrawlNetworking.MobBrawlDamagePayload payload, ServerPlayer player) {
        // Damage is handled server-side via MobBrawlDamageMixin — unused but kept for networking registration
    }

    // ── Broadcast helpers ─────────────────────────────────────────────────────

    public static void broadcastState(MobBrawlServerGame game, MinecraftServer server) {
        sendToRoomDirect(game, server, new MobBrawlNetworking.MobBrawlStatePayload(
                game.roomId,
                game.getPhase().ordinal(),
                game.getTimer(),
                game.getHostLives(),
                game.getGuestLives(),
                game.getHostHealth(),
                game.getGuestHealth(),
                game.getHostMaxHealth(),
                game.getGuestMaxHealth(),
                game.hostMorph  != null ? BuiltInRegistries.ENTITY_TYPE.getKey(game.hostMorph).toString()  : "",
                game.guestMorph != null ? BuiltInRegistries.ENTITY_TYPE.getKey(game.guestMorph).toString() : ""
        ));
    }

    public static void broadcastHealth(MobBrawlServerGame game, MinecraftServer server) {
        ServerPlayer host  = server.getPlayerList().getPlayer(game.hostUUID);
        ServerPlayer guest = server.getPlayerList().getPlayer(game.guestUUID);

        float hostHealth  = host  != null ? host.getHealth()     : game.getHostHealth();
        float guestHealth = guest != null ? guest.getHealth()    : game.getGuestHealth();
        float hostMax     = host  != null ? host.getMaxHealth()  : game.getHostMaxHealth();
        float guestMax    = guest != null ? guest.getMaxHealth() : game.getGuestMaxHealth();

        sendToRoomDirect(game, server, new MobBrawlNetworking.MobBrawlHealthPayload(
                game.roomId,
                hostHealth, guestHealth,
                hostMax, guestMax,
                game.getHostLives(), game.getGuestLives()
        ));
    }

    public static void broadcastEnd(MobBrawlServerGame game, MinecraftServer server) {
        GameRoomManager.getInstance().markWaiting(game.roomId, server);

        ServerPlayer host  = server.getPlayerList().getPlayer(game.hostUUID);
        ServerPlayer guest = server.getPlayerList().getPlayer(game.guestUUID);
        restoreHealthAttribute(host);
        restoreHealthAttribute(guest);

        // Safety: if the match ends while a player is in their 3s death window, make sure
        // they don't get stranded invisible/invulnerable.
        if (host  != null) { host.setInvisible(false);  host.setInvulnerable(false); }
        if (guest != null) { guest.setInvisible(false); guest.setInvulnerable(false); }
        respawnPending.remove(game.hostUUID);
        respawnPending.remove(game.guestUUID);

        if (game.isArenaMode()) restorePositions(game, server);

        MobBrawlServerGame.remove(game.roomId);

        String winnerStr   = game.getWinner() != null ? game.getWinner().toString() : "";
        String winnerMorph = "";
        if (game.getWinner() != null) {
            boolean winnerIsHost = game.getWinner().equals(game.hostUUID);
            EntityType<?> wm = winnerIsHost ? game.hostMorph : game.guestMorph;
            if (wm != null) winnerMorph = BuiltInRegistries.ENTITY_TYPE.getKey(wm).toString();
        }

        String hostName  = host  != null ? host.getName().getString()  : "Host";
        String guestName = guest != null ? guest.getName().getString() : "Guest";

        if (host != null) {
            ServerPlayNetworking.send(host, new MobBrawlNetworking.MobBrawlEndPayload(
                    game.roomId, winnerStr, winnerMorph,
                    game.hostDamageDealt, game.guestDamageDealt,
                    game.getHostLives(), game.getGuestLives(), true,
                    hostName, guestName));
        }
        if (guest != null) {
            ServerPlayNetworking.send(guest, new MobBrawlNetworking.MobBrawlEndPayload(
                    game.roomId, winnerStr, winnerMorph,
                    game.hostDamageDealt, game.guestDamageDealt,
                    game.getHostLives(), game.getGuestLives(), false,
                    hostName, guestName));
        }
    }

    // ── Countdown scheduler ───────────────────────────────────────────────────

    public static void scheduleCountdownTicks(MobBrawlServerGame game, MinecraftServer server) {
        scheduleAfterRealTicks(server, () -> {
            if (game.getPhase() != MobBrawlServerGame.Phase.COUNTDOWN) return;
            sendToRoomDirect(game, server, new MobBrawlNetworking.MobBrawlCountdownPayload(game.roomId, 2));
            scheduleAfterRealTicks(server, () -> {
                if (game.getPhase() != MobBrawlServerGame.Phase.COUNTDOWN) return;
                sendToRoomDirect(game, server, new MobBrawlNetworking.MobBrawlCountdownPayload(game.roomId, 1));
                scheduleAfterRealTicks(server, () -> {
                    if (game.getPhase() != MobBrawlServerGame.Phase.COUNTDOWN) return;
                    sendToRoomDirect(game, server, new MobBrawlNetworking.MobBrawlCountdownPayload(game.roomId, 0)); // GO!
                    GameRoomManager.getInstance().markInProgress(game.roomId, server);

                    game.startFight(server);
                    applyHealthConfig(game, server);
                    broadcastState(game, server);

                    ServerPlayer host  = server.getPlayerList().getPlayer(game.hostUUID);
                    ServerPlayer guest = server.getPlayerList().getPlayer(game.guestUUID);
                    if (host  != null) host.getFoodData().setFoodLevel(20);
                    if (guest != null) guest.getFoodData().setFoodLevel(20);

                    // None arena: capture fight-start positions so handleBrawlDeath has a
                    // respawn anchor. Arena mode captures positions in teleportToArena instead.
                    if (!game.isArenaMode()) {
                        if (host  != null) game.hostSavedPos  = new double[]{host.getX(),  host.getY(),  host.getZ()};
                        if (guest != null) game.guestSavedPos = new double[]{guest.getX(), guest.getY(), guest.getZ()};
                    }

                    if (host  != null) ServerPlayNetworking.send(host,  new MobBrawlNetworking.MobBrawlStartPayload(game.roomId, 2, true));
                    if (guest != null) ServerPlayNetworking.send(guest, new MobBrawlNetworking.MobBrawlStartPayload(game.roomId, 2, false));
                });
            });
        });
    }

    // ── Health config ─────────────────────────────────────────────────────────

    private static void applyHealthConfig(MobBrawlServerGame game, MinecraftServer server) {
        if (game.getHealthMode() == 0) return;
        ServerPlayer host  = server.getPlayerList().getPlayer(game.hostUUID);
        ServerPlayer guest = server.getPlayerList().getPlayer(game.guestUUID);
        float hp = game.getHealthMode() == 2 ? 40f : 20f;
        overrideHealth(host,  hp);
        overrideHealth(guest, hp);
    }

    private static void overrideHealth(ServerPlayer player, float hp) {
        if (player == null) return;
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.removeModifier(HEALTH_MODIFIER_ID);
        attr.setBaseValue(hp);
        player.setHealth(hp);
    }

    private static void restoreHealthAttribute(ServerPlayer player) {
        if (player == null) return;
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.setBaseValue(20f);
    }

    /**
     * Mode 0 (Morph Default): re-applies the player's morph natural max health on respawn.
     * Mirrors the morph health system (base 20 + transient ADD_VALUE modifier) so that when
     * the fight ends and restoreHealthAttribute() resets base to 20, HealthSync resumes
     * cleanly. Falls back to vanilla 20 if the player isn't morphed.
     */
    private static void applyMorphDefaultHealth(ServerPlayer player) {
        if (player == null) return;
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;

        attr.removeModifier(HEALTH_MODIFIER_ID);
        attr.setBaseValue(20f);

        float morphMax = 20f;
        String morphTypeId = net.naw.morphling.network.MorphlingNetworking.playerMorphMap.get(player.getUUID());
        if (morphTypeId != null && !morphTypeId.isEmpty()) {
            try {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(morphTypeId));
                var morphEntity = type.create(player.level(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
                if (morphEntity instanceof net.minecraft.world.entity.LivingEntity le) {
                    morphMax = le.getMaxHealth();
                }
            } catch (Exception ignored) {}
        }

        float modifier = morphMax - 20f;
        if (modifier != 0f) {
            attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    HEALTH_MODIFIER_ID,
                    modifier,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
            ));
        }

        player.setHealth(player.getMaxHealth());
    }

    // ── Arena teleportation ───────────────────────────────────────────────────

    private static void teleportToArena(MobBrawlServerGame game, MinecraftServer server) {
        if (!BrawlDimension.isAvailable()) {
            System.err.println("[Morphling] BrawlDimension not available");
            return;
        }

        ServerPlayer host  = server.getPlayerList().getPlayer(game.hostUUID);
        ServerPlayer guest = server.getPlayerList().getPlayer(game.guestUUID);

        // Save positions for restore after game ends
        if (host  != null) game.hostSavedPos  = new double[]{host.getX(),  host.getY(),  host.getZ()};
        if (guest != null) game.guestSavedPos = new double[]{guest.getX(), guest.getY(), guest.getZ()};

        // Get arena type (1-indexed in game, 0-indexed in enum)
        BrawlDimension.ArenaType type = BrawlDimension.ArenaType.values()[game.getArenaType() - 1];

        // Night uses nightServerLevel, all other arenas use serverLevel
        ServerLevel targetLevel = (type == BrawlDimension.ArenaType.NIGHT)
                ? BrawlDimension.nightServerLevel
                : BrawlDimension.serverLevel;

        // Generate arena terrain
        BrawlDimension.generateArena(targetLevel, type);

        // Keep the brawl level permanently clear server-side — real weather bleeds to the
        // overworld client on integrated servers. Arena rain/thunder is driven purely as
        // visual-only per-client packets below (wantRain/wantThunder), never real weather.
        BrawlDimension.serverLevel.getWeatherData().setRaining(false);
        BrawlDimension.serverLevel.getWeatherData().setThundering(false);
        BrawlDimension.serverLevel.getWeatherData().setClearWeatherTime(6000);
        BrawlDimension.serverLevel.getWeatherData().setRainTime(0);
        BrawlDimension.serverLevel.getWeatherData().setThunderTime(0);

        boolean wantRain   = type == BrawlDimension.ArenaType.GLADIATOR || type == BrawlDimension.ArenaType.NATURE;
        boolean wantThunder = type == BrawlDimension.ArenaType.GLADIATOR;

        final BrawlDimension.ArenaType finalType = type;
        final ServerLevel finalTargetLevel = targetLevel;

        // Teleport both players first, then sync weather/time after 1 second
        BrawlDimension.teleportToArena(host,  type, true);
        BrawlDimension.teleportToArena(guest, type, false);

        final boolean finalWantRain    = wantRain;
        final boolean finalWantThunder = wantThunder;

        scheduleAfterRealTicks(server, () -> {
            for (ServerPlayer p : finalTargetLevel.players()) {
                if (finalType != BrawlDimension.ArenaType.NIGHT) {
                    p.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                            finalWantRain ? net.minecraft.network.protocol.game.ClientboundGameEventPacket.START_RAINING
                                    : net.minecraft.network.protocol.game.ClientboundGameEventPacket.STOP_RAINING, 0f));
                    p.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                            net.minecraft.network.protocol.game.ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, finalWantRain ? 1f : 0f));
                    p.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                            net.minecraft.network.protocol.game.ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, finalWantThunder ? 1f : 0f));
                }
                p.connection.send(server.clockManager().createFullSyncPacket());
            }
        });
    }

    private static void restorePositions(MobBrawlServerGame game, MinecraftServer server) {
        ServerPlayer host  = server.getPlayerList().getPlayer(game.hostUUID);
        ServerPlayer guest = server.getPlayerList().getPlayer(game.guestUUID);
        ServerLevel overworld = server.overworld();

        if (BrawlDimension.isAvailable() && game.getArenaType() > 0) {
            BrawlDimension.ArenaType type = BrawlDimension.ArenaType.values()[game.getArenaType() - 1];
            ServerLevel arenaLevel = (type == BrawlDimension.ArenaType.NIGHT)
                    ? BrawlDimension.nightServerLevel
                    : BrawlDimension.serverLevel;
            BrawlDimension.cleanupArena(arenaLevel, type);
        }

        BrawlDimension.teleportBack(host,  game.hostSavedPos,  overworld);
        BrawlDimension.teleportBack(guest, game.guestSavedPos, overworld);
    }

    // ── Player died ───────────────────────────────────────────────────────────

    public static void onPlayerDied(MobBrawlNetworking.MobBrawlPlayerDiedPayload payload, ServerPlayer player) {
        MobBrawlServerGame game = MobBrawlServerGame.get(payload.roomId());
        if (game == null) return;
        handleBrawlDeath(game, player, player.level().getServer());
    }

    /**
     * Server-authoritative brawl death + respawn. Called both from the client death
     * packet (onPlayerDied) and directly from the damage mixin when a hit would be
     * lethal (so one-shot kills can't escape via the vanilla death screen).
     * Idempotent via respawnPending: if both the mixin and the client packet fire for
     * the same death, only the first call processes — the second returns immediately.
     */
    public static void handleBrawlDeath(MobBrawlServerGame game, ServerPlayer player, MinecraftServer server) {
        if (game.getPhase() != MobBrawlServerGame.Phase.FIGHTING) return;

        // De-dup: ignore if this player is already in their death/respawn window.
        // Both the damage mixin and the client death packet route here; only the first wins.
        if (!respawnPending.add(player.getUUID())) return;

        // Play the morph's death sound at the death location. Vanilla's getDeathSound no
        // longer fires because we cancel the lethal hit before die() runs, so replay it here.
        playMorphDeathSound(player);

        boolean isHost = player.getUUID().equals(game.hostUUID);
        if (isHost) game.hostLives--;
        else game.guestLives--;

        UUID winner = game.checkWinner();
        if (winner != null) {
            respawnPending.remove(player.getUUID()); // no respawn will clear it; match is ending
            game.endGame(winner);
            broadcastEnd(game, server);
            return;
        }

        // Tell the dying player to open the 3s death screen
        int livesLeft = isHost ? game.hostLives : game.guestLives;
        ServerPlayNetworking.send(player, new MobBrawlNetworking.MobBrawlDeathScreenPayload(livesLeft));

        // Broadcast the new lives to BOTH HUDs immediately. The full respawn/reveal is
        // deferred to +60 ticks, but lives must update now — otherwise the dying player's
        // decremented count and the opponent's stale view disagree for 3s.
        broadcastHealth(game, server);

        // Respawn — restore the brawl's configured health (not whatever the rebuilt
        // attribute map defaults to). Respawn wipes the transient morph modifier, so
        // getMaxHealth() is back to vanilla 20 here regardless of mode — we must
        // re-assert health for ALL modes, including Morph Default.
        if (game.getHealthMode() == 1) {
            overrideHealth(player, 20f);
        } else if (game.getHealthMode() == 2) {
            overrideHealth(player, 40f);
        } else {
            // Morph Default — re-apply the morph's natural max health server-side.
            // The client morph health path is intentionally suppressed during a fight,
            // so the brawl owns this too.
            applyMorphDefaultHealth(player);
        }

        player.getFoodData().setFoodLevel(20);
        player.removeAllEffects();

        // Make the dying player invisible + invulnerable immediately so the opponent
        // doesn't see them standing/sliding. Teleport is deferred to ~2.5s (still invisible)
        // so they reappear fresh at the new spot as the death screen closes.
        player.setInvisible(true);
        player.setInvulnerable(true);

        // Defer teleport to ~2.5s into the death screen window (50 ticks)
        scheduleAfterRealTicks(server, () -> {
            if (game.getPhase() != MobBrawlServerGame.Phase.FIGHTING) {
                // Match ended during the window — just reveal, don't touch game state
                player.setInvisible(false);
                player.setInvulnerable(false);
                return;
            }
            if (game.isArenaMode() && BrawlDimension.isAvailable()) {
                BrawlDimension.ArenaType type = BrawlDimension.ArenaType.values()[game.getArenaType() - 1];
                BrawlDimension.teleportRespawn(player, type);
            } else if (!game.isArenaMode()) {
                // None arena — respawn at a random nearby spot so the opponent can't camp the
                // respawn. Anchored to the captured fight-start position (keeps respawns near
                // the fight area), with safe-landing checks. Falls back to the start position
                // if no safe spot is found.
                double[] pos = isHost ? game.hostSavedPos : game.guestSavedPos;
                if (pos != null && player.level() instanceof ServerLevel sl) {
                    randomSafeRespawn(player, sl, pos);
                }
            }
            if (isHost) game.resetHostHealth();
            else game.resetGuestHealth();
            broadcastHealth(game, server);
        }, 50);

        // Reveal at ~3s (60 ticks) — death screen closes, player reappears at new spot
        scheduleAfterRealTicks(server, () -> {
            // Unconditionally reveal first — even if match ended, never strand invisible
            player.setInvisible(false);
            player.setInvulnerable(false);
            respawnPending.remove(player.getUUID());

            if (game.getPhase() != MobBrawlServerGame.Phase.FIGHTING) return;

            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.RESISTANCE, 60, 4, false, false
            ));

            if (isHost) game.resetHostHealth();
            else game.resetGuestHealth();

        }, 60);
    }

    /**
     * None-arena respawn: teleports the player to a random safe spot within radius blocks
     * of anchor (their fight-start position). Mirrors the Enderman safe-landing search —
     * drop to the first sturdy surface, require dry ground with 2 air blocks of clearance.
     * Falls back to the anchor position if no safe spot is found in 32 tries.
     */
    private static void randomSafeRespawn(ServerPlayer player, ServerLevel level, double[] anchor) {
        net.minecraft.util.RandomSource rng = player.getRandom();

        for (int i = 0; i < 32; i++) {
            double tx = anchor[0] + (rng.nextDouble() - 0.5) * 16 * 2.0;
            double tz = anchor[2] + (rng.nextDouble() - 0.5) * 16 * 2.0;
            double ty = anchor[1] + (rng.nextInt(16) - 8);

            net.minecraft.core.BlockPos.MutableBlockPos pos =
                    new net.minecraft.core.BlockPos.MutableBlockPos(tx, ty, tz);

            // Drop down to the first sturdy surface
            while (pos.getY() > level.getMinY()
                    && !level.getBlockState(pos).isFaceSturdy(level, pos, net.minecraft.core.Direction.UP)) {
                pos.move(net.minecraft.core.Direction.DOWN);
            }

            var ground = level.getBlockState(pos);
            boolean isWet = ground.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
            boolean clearAbove = level.getBlockState(pos.above()).isAir()
                    && level.getBlockState(pos.above(2)).isAir();

            if (ground.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP) && !isWet && clearAbove) {
                player.teleportTo(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                        java.util.Set.of(), rng.nextFloat() * 360f, 0f, false);
                return;
            }
        }

        // Fallback — no safe spot found, use the start position
        player.teleportTo(level, anchor[0], anchor[1], anchor[2],
                java.util.Set.of(), player.getYRot(), player.getXRot(), false);
    }

    /**
     * Plays the morph's death sound at the player's location, broadcast to nearby players.
     * Mirrors MorphHurtSoundMixin's getDeathSound logic — needed because we cancel the
     * lethal hit before vanilla die() runs, so the normal death-sound path never fires.
     */
    private static void playMorphDeathSound(ServerPlayer player) {
        if (player == null) return;
        String morphTypeId = net.naw.morphling.network.MorphlingNetworking.playerMorphMap.get(player.getUUID());
        if (morphTypeId == null || morphTypeId.isEmpty()) return;
        try {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(morphTypeId));
            var morphEntity = type.create(player.level(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
            if (!(morphEntity instanceof net.minecraft.world.entity.LivingEntity livingMorph)) return;

            java.lang.reflect.Method m = net.minecraft.world.entity.LivingEntity.class.getDeclaredMethod("getDeathSound");
            m.setAccessible(true);
            net.minecraft.sounds.SoundEvent morphDeath = (net.minecraft.sounds.SoundEvent) m.invoke(livingMorph);
            if (morphDeath == null) return;

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    morphDeath, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        } catch (Exception ignored) {}
    }

    // ── Forfeit ───────────────────────────────────────────────────────────────

    public static void onForfeit(MobBrawlNetworking.MobBrawlForfeitPayload payload, ServerPlayer player) {
        MobBrawlServerGame game = MobBrawlServerGame.get(payload.roomId());
        if (game == null) return;
        if (game.getPhase() != MobBrawlServerGame.Phase.FIGHTING &&
                game.getPhase() != MobBrawlServerGame.Phase.MORPH_SELECT) return;

        // Forfeit during morph select — send both players back to room browser, no end screen
        if (game.getPhase() == MobBrawlServerGame.Phase.MORPH_SELECT) {
            cancelMorphSelect(game, player.level().getServer());
            return;
        }

        // Forfeit during fight — opponent wins
        boolean isHost = player.getUUID().equals(game.hostUUID);
        UUID winner = isHost ? game.guestUUID : game.hostUUID;
        game.endGame(winner);
        broadcastEnd(game, player.level().getServer());
    }

    // ── Mouse relay ───────────────────────────────────────────────────────────

    public static void onMouse(MobBrawlNetworking.MobBrawlMousePayload payload, ServerPlayer player) {
        // Game may already be removed after end — relay via room membership instead
        GameRoomManager.Room room = GameRoomManager.getInstance().getRoomById(payload.roomId());
        if (room == null) return;

        // Send to all other players in the room
        for (java.util.UUID uuid : room.playerUuids) {
            if (uuid.equals(player.getUUID())) continue;
            ServerPlayer opponent = player.level().getServer().getPlayerList().getPlayer(uuid);
            if (opponent != null) {
                ServerPlayNetworking.send(opponent, new MobBrawlNetworking.MobBrawlMouseSyncPayload(
                        payload.roomId(), payload.mouseX(), payload.mouseY()));
            }
        }
    }

    /**
     * Cancels a match still in MORPH_SELECT and returns both players to the room browser.
     * Called by the server-authoritative morph-select timer (Morphling tick loop) when the
     * 30s expires without both players ready, and reused by onForfeit during morph select.
     * Sends phase=0 START to both clients (handled by MobBrawlClient.onStart → clearSession).
     */
    public static void cancelMorphSelect(MobBrawlServerGame game, MinecraftServer server) {
        ServerPlayer host  = server.getPlayerList().getPlayer(game.hostUUID);
        ServerPlayer guest = server.getPlayerList().getPlayer(game.guestUUID);
        if (host  != null) ServerPlayNetworking.send(host,  new MobBrawlNetworking.MobBrawlStartPayload(game.roomId, 0, true));
        if (guest != null) ServerPlayNetworking.send(guest, new MobBrawlNetworking.MobBrawlStartPayload(game.roomId, 0, false));
        GameRoomManager.getInstance().markWaiting(game.roomId, server);
        MobBrawlServerGame.remove(game.roomId);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    public static void sendToRoomDirect(MobBrawlServerGame game, MinecraftServer server,
                                        net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        ServerPlayer host  = server.getPlayerList().getPlayer(game.hostUUID);
        ServerPlayer guest = server.getPlayerList().getPlayer(game.guestUUID);
        if (host  != null) ServerPlayNetworking.send(host,  payload);
        if (guest != null) ServerPlayNetworking.send(guest, payload);
    }

    private static void scheduleAfterRealTicks(MinecraftServer server, Runnable task) {
        scheduleAfterRealTicks(server, task, 20);
    }

    // ── Scheduled-task system ─────────────────────────────────────────────────
    // Single persistent END_SERVER_TICK listener drains this queue, instead of
    // registering a new (never-removed) listener per schedule call. Prevents a
    // listener leak that would slowly accumulate on busy servers running many games.
    private record ScheduledTask(long targetTick, Runnable task) {}
    private static final java.util.List<ScheduledTask> SCHEDULED = new java.util.ArrayList<>();
    private static boolean schedulerRegistered = false;

    @SuppressWarnings("SameParameterValue")
    private static void scheduleAfterRealTicks(MinecraftServer server, Runnable task, int ticks) {
        if (!schedulerRegistered) {
            schedulerRegistered = true;
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(s -> {
                if (SCHEDULED.isEmpty()) return;
                long now = s.getTickCount();
                // Collect due tasks first, then run — a task may schedule another
                // (countdown chain nests calls), which would mutate SCHEDULED mid-iteration.
                java.util.List<ScheduledTask> due = null;
                var it = SCHEDULED.iterator();
                while (it.hasNext()) {
                    ScheduledTask st = it.next();
                    if (now >= st.targetTick()) {
                        if (due == null) due = new java.util.ArrayList<>();
                        due.add(st);
                        it.remove();
                    }
                }
                if (due != null) for (ScheduledTask st : due) st.task().run();
            });
        }
        SCHEDULED.add(new ScheduledTask(server.getTickCount() + ticks, task));
    }
}