package net.naw.morphling.client.games.packet;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.naw.morphling.client.games.ui.RoomBrowserScreen;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * Networking for the Room Browser system.
 * Separate from GamesNetworking which handles in-game packets.

 * Packets:
 *   Client → Server:
 *     RoomCreatePayload  — player wants to create a new room
 *     RoomJoinPayload    — player wants to join an existing room
 *     RoomLeavePayload   — player left a room (before game starts)
 *     RoomListRequest    — player opened room browser, wants current list

 *   Server → Client:
 *     RoomListPayload    — full list of open rooms broadcast to all / sent on request
 *     RoomJoinedPayload  — confirms join, sends room details to the joining player
 *     RoomErrorPayload   — room full, not found, etc.
 *     RoomUpdatePayload  — player list changed in a room
 */
public class RoomsNetworking {

    private static final String NS = "morphling";

    // ── Client → Server ──────────────────────────────────────────────────────

    /** Player creates a new room for a specific game mode */
    public record RoomCreatePayload(UUID playerUuid, String playerName, String roomName, String gameMode, int maxPlayers) implements CustomPacketPayload {
        public static final Type<RoomCreatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "room_create"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RoomCreatePayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUUID(p.playerUuid()); buf.writeUtf(p.playerName()); buf.writeUtf(p.roomName()); buf.writeUtf(p.gameMode()); buf.writeInt(p.maxPlayers()); },
                buf -> new RoomCreatePayload(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readInt())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Player joins an existing room by room ID */
    public record RoomJoinPayload(UUID playerUuid, String playerName, String roomId) implements CustomPacketPayload {
        public static final Type<RoomJoinPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "room_join"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RoomJoinPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUUID(p.playerUuid()); buf.writeUtf(p.playerName()); buf.writeUtf(p.roomId()); },
                buf -> new RoomJoinPayload(buf.readUUID(), buf.readUtf(), buf.readUtf())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Player leaves a room before the game starts */
    public record RoomLeavePayload(UUID playerUuid, String roomId) implements CustomPacketPayload {
        public static final Type<RoomLeavePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "room_leave"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RoomLeavePayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUUID(p.playerUuid()); buf.writeUtf(p.roomId()); },
                buf -> new RoomLeavePayload(buf.readUUID(), buf.readUtf())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Player opened room browser — server sends current list */
    public record RoomListRequest(UUID playerUuid) implements CustomPacketPayload {
        public static final Type<RoomListRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "room_list_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RoomListRequest> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUUID(p.playerUuid()),
                buf -> new RoomListRequest(buf.readUUID())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ── Server → Client ──────────────────────────────────────────────────────

    /**
     * Full room list sent to client.
     * Flat array of room data: [roomId, roomName, hostName, gameMode, playerCount, maxPlayers, status, ...]
     * Each room takes 7 slots.
     */
    public record RoomListPayload(String[] roomData) implements CustomPacketPayload {
        public static final Type<RoomListPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "room_list"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RoomListPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeInt(p.roomData().length);
                    for (String s : p.roomData()) buf.writeUtf(s);
                },
                buf -> {
                    int count = buf.readInt();
                    String[] data = new String[count];
                    for (int i = 0; i < count; i++) data[i] = buf.readUtf();
                    return new RoomListPayload(data);
                }
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Sent to a player when they successfully join a room.
     * roomName is the custom name the host picked.
     * Contains the room ID so the client knows which room they're in.
     */
    public record RoomJoinedPayload(String roomId, String roomName, String hostName, String gameMode,
                                    String[] playerNames) implements CustomPacketPayload {
        public static final Type<RoomJoinedPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "room_joined"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RoomJoinedPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.roomId()); buf.writeUtf(p.roomName()); buf.writeUtf(p.hostName()); buf.writeUtf(p.gameMode());
                    buf.writeInt(p.playerNames().length);
                    for (String n : p.playerNames()) buf.writeUtf(n);
                },
                buf -> {
                    String roomId = buf.readUtf(), roomName = buf.readUtf(), host = buf.readUtf(), mode = buf.readUtf();
                    int count = buf.readInt();
                    String[] names = new String[count];
                    for (int i = 0; i < count; i++) names[i] = buf.readUtf();
                    return new RoomJoinedPayload(roomId, roomName, host, mode, names);
                }
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Sent when something goes wrong — room full, not found, etc. */
    public record RoomErrorPayload(String message) implements CustomPacketPayload {
        public static final Type<RoomErrorPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "room_error"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RoomErrorPayload> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.message()),
                buf -> new RoomErrorPayload(buf.readUtf())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Sent to all players in a room when player list changes */
    public record RoomUpdatePayload(String roomId, String hostName, String[] playerNames) implements CustomPacketPayload {
        public static final Type<RoomUpdatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "room_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RoomUpdatePayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.roomId());
                    buf.writeUtf(p.hostName());
                    buf.writeInt(p.playerNames().length);
                    for (String n : p.playerNames()) buf.writeUtf(n);
                },
                buf -> {
                    String roomId = buf.readUtf();
                    String hostName = buf.readUtf();
                    int count = buf.readInt();
                    String[] names = new String[count];
                    for (int i = 0; i < count; i++) names[i] = buf.readUtf();
                    return new RoomUpdatePayload(roomId, hostName, names);
                }
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ── Registration ─────────────────────────────────────────────────────────

    /** Call from client-side mod initializer */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(RoomListPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() ->
                        RoomBrowserScreen.onRoomList(payload.roomData())));

        ClientPlayNetworking.registerGlobalReceiver(RoomJoinedPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() ->
                        RoomBrowserScreen.onRoomJoined(payload.roomId(), payload.roomName(),
                                payload.hostName(), payload.gameMode(), payload.playerNames())));

        ClientPlayNetworking.registerGlobalReceiver(RoomErrorPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() ->
                        RoomBrowserScreen.onRoomError(payload.message())));

        ClientPlayNetworking.registerGlobalReceiver(RoomUpdatePayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() ->
                        RoomBrowserScreen.onRoomUpdate(payload.roomId(), payload.hostName(), payload.playerNames())));
    }
}