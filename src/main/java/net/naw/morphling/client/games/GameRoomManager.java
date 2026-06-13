package net.naw.morphling.client.games;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.naw.morphling.client.games.packet.RoomsNetworking;

import java.util.*;

/**
 * Server-side room manager for all Morph Games game modes.

 * Singleton — one manager per server, handles all rooms across all game modes.

 * Room lifecycle:
 *   1. Player clicks Multiplayer → createRoom() → room created, player becomes host
 *   2. Other players click Multiplayer → see room list → joinRoom()
 *   3. Host clicks Start → room transitions to IN_PROGRESS → game logic takes over
 *   4. Game ends or host leaves → room destroyed

 * Room data sent to clients:
 *   Flat array per room: [roomId, hostName, gameMode, playerCount, maxPlayers, status]
 *   6 fields per room.
 */
public class GameRoomManager {

    private static GameRoomManager INSTANCE;

    public static GameRoomManager getInstance() {
        if (INSTANCE == null) INSTANCE = new GameRoomManager();
        return INSTANCE;
    }

    // ── Room max sizes per game mode ─────────────────────────────────────────
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static final Map<String, Integer> MAX_PLAYERS = new HashMap<>();
    static {
        MAX_PLAYERS.put("TRIVIA",     8);
        MAX_PLAYERS.put("ROULETTE",   8);
        MAX_PLAYERS.put("RELAY_RACE", 8);
        MAX_PLAYERS.put("HIDE_SEEK",  16);
        MAX_PLAYERS.put("MOB_BRAWL",  2);
        MAX_PLAYERS.put("HUNGER",     8);
    }

    // ── Room ─────────────────────────────────────────────────────────────────

    public enum RoomStatus { WAITING, IN_PROGRESS }

    public static class Room {
        public final String     roomId;
        public final String     gameMode;
        public       UUID       hostUuid;
        public       String     hostName;
        public       String     roomName;
        public final List<UUID>   playerUuids = new ArrayList<>();
        public final List<String> playerNames = new ArrayList<>();
        public RoomStatus status = RoomStatus.WAITING;
        public final int maxPlayers;

        Room(UUID hostUuid, String hostName, String roomName, String gameMode, int maxPlayers) {
            this.roomId     = gameMode + "_" + hostName + "_" + (System.currentTimeMillis() % 10000);
            this.hostUuid   = hostUuid;
            this.hostName   = hostName;
            this.gameMode   = gameMode;
            this.roomName   = roomName;
            this.maxPlayers = maxPlayers;
            playerUuids.add(hostUuid);
            playerNames.add(hostName);
        }
    }

    // ── State ────────────────────────────────────────────────────────────────

    // roomId → Room
    private final Map<String, Room> rooms = new LinkedHashMap<>();

    // playerUuid → roomId (so we know which room a player is in)
    private final Map<UUID, String> playerRoomMap = new HashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /** Creates a new room and sends the joined confirmation back to the host */
    public void createRoom(ServerPlayer player, String playerName, String roomName, String gameMode, int maxPlayers) {
        // If player is already in a room, leave it first
        leaveCurrentRoom(player);

        Room room = new Room(player.getUUID(), playerName, roomName, gameMode, maxPlayers);
        rooms.put(room.roomId, room);
        playerRoomMap.put(player.getUUID(), room.roomId);

        // Send joined confirmation to host
        ServerPlayNetworking.send(player, new RoomsNetworking.RoomJoinedPayload(
                room.roomId, room.roomName, room.hostName, room.gameMode,
                room.playerNames.toArray(new String[0])));

        // Broadcast updated room list to everyone
        broadcastRoomList(player.level().getServer());
    }

    /** Joins an existing room */
    public void joinRoom(ServerPlayer player, String playerName, String roomId) {
        Room room = rooms.get(roomId);

        if (room == null) {
            ServerPlayNetworking.send(player, new RoomsNetworking.RoomErrorPayload("Room not found."));
            return;
        }
        if (room.status == RoomStatus.IN_PROGRESS) {
            ServerPlayNetworking.send(player, new RoomsNetworking.RoomErrorPayload("Game already in progress."));
            return;
        }
        if (room.playerUuids.size() >= room.maxPlayers) {
            ServerPlayNetworking.send(player, new RoomsNetworking.RoomErrorPayload("Room is full (" + room.maxPlayers + "/" + room.maxPlayers + ")."));
            return;
        }

        // Leave current room if in one
        leaveCurrentRoom(player);

        room.playerUuids.add(player.getUUID());
        room.playerNames.add(playerName);
        playerRoomMap.put(player.getUUID(), roomId);

        // Send joined confirmation to this player
        ServerPlayNetworking.send(player, new RoomsNetworking.RoomJoinedPayload(
                room.roomId, room.roomName, room.hostName, room.gameMode,
                room.playerNames.toArray(new String[0])));

        // Send room update to all players in the room
        broadcastRoomUpdate(room, player.level().getServer());

        // Broadcast updated room list to everyone
        broadcastRoomList(player.level().getServer());
    }

    /** Player leaves a room by roomId */
    public void leaveRoom(ServerPlayer player, String roomId) {
        Room room = rooms.get(roomId);
        if (room == null) return;

        String leftName = player.getName().getString();
        room.playerUuids.remove(player.getUUID());
        room.playerNames.remove(leftName);
        playerRoomMap.remove(player.getUUID());

        if (room.playerUuids.isEmpty()) {
            // Last player left — destroy room
            rooms.remove(roomId);
        } else if (player.getUUID().equals(room.hostUuid)) {
            // Host left — transfer to next player
            room.hostUuid = room.playerUuids.getFirst();
            room.hostName = room.playerNames.getFirst();
            broadcastRoomUpdate(room, player.level().getServer());
        } else {
            broadcastRoomUpdate(room, player.level().getServer());
        }

        broadcastRoomList(player.level().getServer());
    }

    /** Sends current room list to a specific player (on browser open) */
    public void sendRoomList(ServerPlayer player) {
        ServerPlayNetworking.send(player, buildRoomListPayload());
    }

    /** Marks a room as in progress (called when game starts) */
    @SuppressWarnings("unused")
    public void markInProgress(String roomId, net.minecraft.server.MinecraftServer server) {
        Room room = rooms.get(roomId);
        if (room != null) {
            room.status = RoomStatus.IN_PROGRESS;
            broadcastRoomList(server);
        }
    }

    public void markWaiting(String roomId, net.minecraft.server.MinecraftServer server) {
        Room room = rooms.get(roomId);
        if (room != null) {
            room.status = RoomStatus.WAITING;
            broadcastRoomList(server);
        }
    }

    /** Destroys a room when game ends */
    @SuppressWarnings("unused")
    public void destroyRoom(String roomId, net.minecraft.server.MinecraftServer server) {
        Room room = rooms.remove(roomId);
        if (room != null) {
            for (UUID uuid : room.playerUuids) {
                playerRoomMap.remove(uuid);
            }
            broadcastRoomList(server);
        }
    }

    /** Returns the room a player is currently in, or null */
    @SuppressWarnings("unused")
    public Room getRoomForPlayer(UUID playerUuid) {
        String roomId = playerRoomMap.get(playerUuid);
        return roomId != null ? rooms.get(roomId) : null;
    }

    /** Returns a room by its ID, or null if not found */
    public Room getRoomById(String roomId) {
        return rooms.get(roomId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void leaveCurrentRoom(ServerPlayer player) {
        String currentRoomId = playerRoomMap.get(player.getUUID());
        if (currentRoomId != null) {
            leaveRoom(player, currentRoomId);
        }
    }

    private void broadcastRoomList(net.minecraft.server.MinecraftServer server) {
        var payload = buildRoomListPayload();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(sp, payload);
        }
    }

    private void broadcastRoomUpdate(Room room, net.minecraft.server.MinecraftServer server) {
        var payload = new RoomsNetworking.RoomUpdatePayload(
                room.roomId, room.hostName, room.playerNames.toArray(new String[0]));
        for (UUID uuid : room.playerUuids) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) ServerPlayNetworking.send(sp, payload);
        }
    }

    private RoomsNetworking.RoomListPayload buildRoomListPayload() {
        // Each room: [roomId, hostName, gameMode, playerCount, maxPlayers, status]
        List<String> data = new ArrayList<>();
        for (Room room : rooms.values()) {
            data.add(room.roomId);
            data.add(room.hostName);
            data.add(room.gameMode);
            data.add(String.valueOf(room.playerUuids.size()));
            data.add(String.valueOf(room.maxPlayers));
            data.add(room.status.name());
        }
        return new RoomsNetworking.RoomListPayload(data.toArray(new String[0]));
    }


    /** Called on player disconnect — cleans up their room without needing a ServerPlayer reference */
    public void leaveCurrentRoomOnDisconnect(ServerPlayer player, net.minecraft.server.MinecraftServer server) {
        String roomId = playerRoomMap.get(player.getUUID());
        if (roomId == null) return;

        Room room = rooms.get(roomId);
        if (room == null) {
            playerRoomMap.remove(player.getUUID());
            return;
        }

        String leftName = player.getName().getString();
        room.playerUuids.remove(player.getUUID());
        room.playerNames.remove(leftName);
        playerRoomMap.remove(player.getUUID());

        if (room.playerUuids.isEmpty()) {
            // Last player left — destroy room
            rooms.remove(roomId);
        } else if (player.getUUID().equals(room.hostUuid)) {
            // Host left — transfer to next player
            room.hostUuid = room.playerUuids.getFirst();
            room.hostName = room.playerNames.getFirst();
            broadcastRoomUpdate(room, server);
        } else {
            broadcastRoomUpdate(room, server);
        }

        broadcastRoomList(server);
    }
}