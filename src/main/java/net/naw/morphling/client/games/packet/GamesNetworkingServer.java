package net.naw.morphling.client.games.packet;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.naw.morphling.client.games.GameRoomManager;
import net.naw.morphling.client.games.MobBrawl.MobBrawlNetworkingServer;
import net.naw.morphling.client.games.MobBrawl.MobBrawlServerGame;
import net.naw.morphling.client.games.trivia.TriviaServerGame;

/**
 * Server-side only networking registration for Morph Games.
 * Split from GamesNetworking and RoomsNetworking to avoid loading
 * client-only classes (Screen, Minecraft, ClientPlayNetworking) on the server.

 * Call registerServer() from Morphling.java (the server entrypoint).
 * GamesNetworking.registerClient() and RoomsNetworking.registerClient()
 * are still called from MorphlingClient.java as before.
 */
public class GamesNetworkingServer {

    /** Call from Morphling.java (server entrypoint) — no client imports here */
    public static void registerServer() {

        // ── GamesNetworking payload types ─────────────────────────────────────
        PayloadTypeRegistry.serverboundPlay().register(GamesNetworking.TriviaJoinPayload.TYPE,   GamesNetworking.TriviaJoinPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(GamesNetworking.TriviaAnswerPayload.TYPE, GamesNetworking.TriviaAnswerPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(GamesNetworking.TriviaLeavePayload.TYPE,  GamesNetworking.TriviaLeavePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GamesNetworking.TriviaStartPayload.TYPE,      GamesNetworking.TriviaStartPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GamesNetworking.TriviaQuestionPayload.TYPE,   GamesNetworking.TriviaQuestionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GamesNetworking.TriviaResultPayload.TYPE,     GamesNetworking.TriviaResultPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GamesNetworking.TriviaEndPayload.TYPE,        GamesNetworking.TriviaEndPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GamesNetworking.TriviaPlayerListPayload.TYPE, GamesNetworking.TriviaPlayerListPayload.CODEC);

        // ── RoomsNetworking payload types ─────────────────────────────────────
        PayloadTypeRegistry.serverboundPlay().register(RoomsNetworking.RoomCreatePayload.TYPE,  RoomsNetworking.RoomCreatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RoomsNetworking.RoomJoinPayload.TYPE,    RoomsNetworking.RoomJoinPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RoomsNetworking.RoomLeavePayload.TYPE,   RoomsNetworking.RoomLeavePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RoomsNetworking.RoomListRequest.TYPE,    RoomsNetworking.RoomListRequest.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RoomsNetworking.RoomListPayload.TYPE,    RoomsNetworking.RoomListPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RoomsNetworking.RoomJoinedPayload.TYPE,  RoomsNetworking.RoomJoinedPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RoomsNetworking.RoomErrorPayload.TYPE,   RoomsNetworking.RoomErrorPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RoomsNetworking.RoomUpdatePayload.TYPE,  RoomsNetworking.RoomUpdatePayload.CODEC);

        // ── GamesNetworking server receivers ──────────────────────────────────

        // HOST_START — host sends "HOST_START:<roomId>" to trigger game start
        ServerPlayNetworking.registerGlobalReceiver(GamesNetworking.TriviaJoinPayload.TYPE,
                (payload, ctx) -> ctx.server().execute(() -> {
                    if (payload.playerName().startsWith("HOST_START:")) {
                        String roomId = payload.playerName().substring("HOST_START:".length());
                        TriviaServerGame.getInstance(roomId).hostStart(ctx.server(), roomId);
                    }
                }));

        // Answer — look up player's room, route to correct game instance
        ServerPlayNetworking.registerGlobalReceiver(GamesNetworking.TriviaAnswerPayload.TYPE,
                (payload, ctx) -> {
                    ServerPlayer player = ctx.player();
                    ctx.server().execute(() -> {
                        GameRoomManager.Room room = GameRoomManager.getInstance().getRoomForPlayer(player.getUUID());
                        if (room == null) return;
                        TriviaServerGame.getInstance(room.roomId).onPlayerAnswer(
                                player, payload.answerIndex(), payload.timeRemaining());
                    });
                });

        // Leave — look up player's room, route to correct game instance
        ServerPlayNetworking.registerGlobalReceiver(GamesNetworking.TriviaLeavePayload.TYPE,
                (_, ctx) -> {
                    ServerPlayer player = ctx.player();
                    ctx.server().execute(() -> {
                        GameRoomManager.Room room = GameRoomManager.getInstance().getRoomForPlayer(player.getUUID());
                        if (room == null) return;
                        TriviaServerGame.getInstance(room.roomId).onPlayerLeave(player);
                    });
                });

        // ── RoomsNetworking server receivers ──────────────────────────────────

        ServerPlayNetworking.registerGlobalReceiver(RoomsNetworking.RoomCreatePayload.TYPE,
                (payload, ctx) -> {
                    ServerPlayer player = ctx.player();
                    ctx.server().execute(() ->
                            GameRoomManager.getInstance().createRoom(
                                    player, payload.playerName(), payload.roomName(), payload.gameMode(), payload.maxPlayers()));
                });

        ServerPlayNetworking.registerGlobalReceiver(RoomsNetworking.RoomJoinPayload.TYPE,
                (payload, ctx) -> {
                    ServerPlayer player = ctx.player();
                    ctx.server().execute(() ->
                            GameRoomManager.getInstance().joinRoom(
                                    player, payload.playerName(), payload.roomId()));
                });

        ServerPlayNetworking.registerGlobalReceiver(RoomsNetworking.RoomLeavePayload.TYPE,
                (payload, ctx) -> {
                    ServerPlayer player = ctx.player();
                    ctx.server().execute(() ->
                            GameRoomManager.getInstance().leaveRoom(player, payload.roomId()));
                });

        ServerPlayNetworking.registerGlobalReceiver(RoomsNetworking.RoomListRequest.TYPE,
                (_, ctx) -> {
                    ServerPlayer player = ctx.player();
                    ctx.server().execute(() ->
                            GameRoomManager.getInstance().sendRoomList(player));
                });

        // ── Safety net — teleport players out of brawl dimension on join ──────
        // Handles the case where a player crashed while inside an arena
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((joinHandler, _, joinServer) ->
                joinServer.execute(() -> {
                    ServerPlayer player = joinHandler.player;
                    if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                        if (sl.dimension().equals(net.naw.morphling.client.games.MobBrawl.BrawlDimension.DIMENSION_KEY) ||
                                sl.dimension().equals(net.naw.morphling.client.games.MobBrawl.BrawlDimension.NIGHT_DIMENSION_KEY)) {
                            net.minecraft.server.level.ServerLevel overworld = joinServer.overworld();

                            // Use getSharedSpawnPos() — safe in 26.1, never null unlike getRespawnData()
                            net.minecraft.core.BlockPos spawnPos = overworld.getRespawnData().pos();
                            net.minecraft.core.BlockPos safePos = overworld.getHeightmapPos(
                                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                    spawnPos);
                            player.teleportTo(overworld,
                                    safePos.getX() + 0.5,
                                    safePos.getY(),
                                    safePos.getZ() + 0.5,
                                    java.util.Set.of(), 0f, 0f, false);

                            // Heal + clear damage so a player arriving mid-death can't
                            // re-trigger the chunkPlayers NPE before the teleport lands
                            player.setHealth(player.getMaxHealth());
                            player.clearFire();
                            player.resetFallDistance();
                        }
                    }
                }));

        // ── Disconnect handler ────────────────────────────────────────────────
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                server.execute(() -> {
                    // Notify trivia game so it can broadcast updated player list
                    GameRoomManager.Room room = GameRoomManager.getInstance().getRoomForPlayer(handler.player.getUUID());
                    if (room != null) {
                        TriviaServerGame.getInstance(room.roomId).onPlayerLeave(handler.player);
                    }

                    // Mob Brawl — if player disconnects during fight, opponent wins
                    MobBrawlServerGame brawlGame = MobBrawlServerGame.getByPlayer(handler.player.getUUID());
                    if (brawlGame != null && brawlGame.getPhase() == MobBrawlServerGame.Phase.FIGHTING) {
                        boolean isHost = handler.player.getUUID().equals(brawlGame.hostUUID);
                        java.util.UUID winner = isHost ? brawlGame.guestUUID : brawlGame.hostUUID;

                        // Teleport disconnecting player to overworld before their position saves
                        // so they never rejoin inside the arena void (prevents chunkPlayers NPE)
                        try {
                            net.minecraft.server.level.ServerLevel overworld = server.overworld();
                            net.minecraft.core.BlockPos safePos = overworld.getHeightmapPos(
                                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                    overworld.getRespawnData().pos());
                            handler.player.teleportTo(overworld,
                                    safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5,
                                    java.util.Set.of(), 0f, 0f, false);
                        } catch (Exception ignored) {}

                        brawlGame.endGame(winner);
                        MobBrawlNetworkingServer.broadcastEnd(brawlGame, server);
                    }

                    GameRoomManager.getInstance().leaveCurrentRoomOnDisconnect(handler.player, server);
                }));
    }
}