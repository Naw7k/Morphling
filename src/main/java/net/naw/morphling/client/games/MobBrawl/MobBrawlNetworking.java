package net.naw.morphling.client.games.MobBrawl;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Networking layer for Mob Brawl — all packets between client and server.

 * Packet flow:

 * CLIENT → SERVER:
 *   MobBrawlConfigPayload    — host updates room config (health, abilities, time, lives, arena)
 *   MobBrawlMorphPickPayload — player picks their morph for the fight
 *   MobBrawlDamagePayload    — client reports damage dealt to opponent
 *   MobBrawlReadyPayload     — player confirms ready on morph select screen

 * SERVER → CLIENT:
 *   MobBrawlStatePayload     — full game state sync (phase, timer, health, lives)
 *   MobBrawlConfigSyncPayload — broadcasts host's config to all room players
 *   MobBrawlStartPayload     — tells clients to open the fight screen
 *   MobBrawlEndPayload       — announces winner UUID and stats
 *   MobBrawlHealthPayload    — real-time health update for both players
 *   MobBrawlCountdownPayload — countdown tick (3, 2, 1, GO)

 * Registration:
 *   Call MobBrawlNetworking.registerClient() from MorphlingClient.
 *   Call MobBrawlNetworking.registerServer() from Morphling (server entrypoint).
 */
public class MobBrawlNetworking {

    // ── Packet IDs ────────────────────────────────────────────────────────────

    // Client → Server
    public static final CustomPacketPayload.Type<MobBrawlConfigPayload>    CONFIG_TYPE     = type("brawl_config");
    public static final CustomPacketPayload.Type<MobBrawlMorphPickPayload> MORPH_PICK_TYPE = type("brawl_morph_pick");
    public static final CustomPacketPayload.Type<MobBrawlDamagePayload>    DAMAGE_TYPE     = type("brawl_damage");
    public static final CustomPacketPayload.Type<MobBrawlReadyPayload>     READY_TYPE      = type("brawl_ready");

    // Server → Client
    public static final CustomPacketPayload.Type<MobBrawlStatePayload>     STATE_TYPE      = type("brawl_state");
    public static final CustomPacketPayload.Type<MobBrawlConfigSyncPayload> CONFIG_SYNC_TYPE = type("brawl_config_sync");
    public static final CustomPacketPayload.Type<MobBrawlStartPayload>     START_TYPE      = type("brawl_start");
    public static final CustomPacketPayload.Type<MobBrawlEndPayload>       END_TYPE        = type("brawl_end");
    public static final CustomPacketPayload.Type<MobBrawlHealthPayload>    HEALTH_TYPE     = type("brawl_health");
    public static final CustomPacketPayload.Type<MobBrawlCountdownPayload> COUNTDOWN_TYPE  = type("brawl_countdown");
    public static final CustomPacketPayload.Type<MobBrawlDeathScreenPayload> DEATH_SCREEN_TYPE = type("brawl_death_screen");
    public static final CustomPacketPayload.Type<MobBrawlPlayerDiedPayload> DIED_TYPE = type("brawl_died");
    public static final CustomPacketPayload.Type<MobBrawlForfeitPayload>   FORFEIT_TYPE    = type("brawl_forfeit");
    public static final CustomPacketPayload.Type<MobBrawlMousePayload>     MOUSE_TYPE      = type("brawl_mouse");
    public static final CustomPacketPayload.Type<MobBrawlMouseSyncPayload> MOUSE_SYNC_TYPE = type("brawl_mouse_sync");

    private static CustomPacketPayload.Type<? extends CustomPacketPayload> typeRaw(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("morphling", path));
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return (CustomPacketPayload.Type<T>) typeRaw(path);
    }

    // ── Registration ─────────────────────────────────────────────────────────

    public static void registerCommon() {
        // Client → Server
        PayloadTypeRegistry.serverboundPlay().register(CONFIG_TYPE,      MobBrawlConfigPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MORPH_PICK_TYPE,  MobBrawlMorphPickPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DAMAGE_TYPE,      MobBrawlDamagePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(READY_TYPE,       MobBrawlReadyPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DIED_TYPE, MobBrawlPlayerDiedPayload.CODEC);


        // Server → Client
        PayloadTypeRegistry.clientboundPlay().register(STATE_TYPE,       MobBrawlStatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CONFIG_SYNC_TYPE, MobBrawlConfigSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(START_TYPE,       MobBrawlStartPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(END_TYPE,         MobBrawlEndPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HEALTH_TYPE,      MobBrawlHealthPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(COUNTDOWN_TYPE,   MobBrawlCountdownPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DEATH_SCREEN_TYPE, MobBrawlDeathScreenPayload.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(FORFEIT_TYPE,    MobBrawlForfeitPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MOUSE_TYPE,      MobBrawlMousePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MOUSE_SYNC_TYPE, MobBrawlMouseSyncPayload.CODEC);
    }

    /** Called from MorphlingClient — registers client-side packet receivers */
    public static void registerClient() {
        // Config sync — host changed config, update our local display
        ClientPlayNetworking.registerGlobalReceiver(CONFIG_SYNC_TYPE, (payload, ctx) ->
                ctx.client().execute(() -> MobBrawlClient.onConfigSync(payload)));

        // State sync — full game state update
        ClientPlayNetworking.registerGlobalReceiver(STATE_TYPE, (payload, ctx) ->
                ctx.client().execute(() -> MobBrawlClient.onStateSync(payload)));

        // Start — open morph select or fight screen depending on phase
        ClientPlayNetworking.registerGlobalReceiver(START_TYPE, (payload, ctx) ->
                ctx.client().execute(() -> MobBrawlClient.onStart(payload)));

        // End — game over, open end screen
        ClientPlayNetworking.registerGlobalReceiver(END_TYPE, (payload, ctx) ->
                ctx.client().execute(() -> MobBrawlClient.onEnd(payload)));

        // Health update — real-time health of both players
        ClientPlayNetworking.registerGlobalReceiver(HEALTH_TYPE, (payload, ctx) ->
                ctx.client().execute(() -> MobBrawlClient.onHealthUpdate(payload)));

        // Countdown tick
        ClientPlayNetworking.registerGlobalReceiver(COUNTDOWN_TYPE, (payload, ctx) ->
                ctx.client().execute(() -> MobBrawlClient.onCountdown(payload)));

        // Death screen — open the 3s respawn screen for this player only
        ClientPlayNetworking.registerGlobalReceiver(DEATH_SCREEN_TYPE, (payload, ctx) ->
                ctx.client().execute(() -> MobBrawlClient.onDeathScreen(payload)));

        // Opponent mouse sync — update stored coords for end screen animation
        ClientPlayNetworking.registerGlobalReceiver(MOUSE_SYNC_TYPE, (payload, ctx) ->
                ctx.client().execute(() -> MobBrawlClient.onMouseSync(payload)));
    }

    /** Called from MorphlingNetworkingServer — registers server-side packet receivers */
    public static void registerServer() {
        // Host updates config
        ServerPlayNetworking.registerGlobalReceiver(CONFIG_TYPE, (payload, ctx) ->
                ctx.server().execute(() -> MobBrawlNetworkingServer.onConfig(payload, ctx.player())));


        // Player picks morph
        ServerPlayNetworking.registerGlobalReceiver(MORPH_PICK_TYPE, (payload, ctx) ->
                ctx.server().execute(() -> MobBrawlNetworkingServer.onMorphPick(payload, ctx.player())));

        // Player reports damage dealt
        ServerPlayNetworking.registerGlobalReceiver(DAMAGE_TYPE, (payload, ctx) ->
                ctx.server().execute(() -> MobBrawlNetworkingServer.onDamage(payload, ctx.player())));

        // Player is ready
        ServerPlayNetworking.registerGlobalReceiver(READY_TYPE, (payload, ctx) ->
                ctx.server().execute(() -> MobBrawlNetworkingServer.onReady(payload, ctx.player())));

        ServerPlayNetworking.registerGlobalReceiver(DIED_TYPE, (payload, ctx) ->
                ctx.server().execute(() -> MobBrawlNetworkingServer.onPlayerDied(payload, ctx.player())));

        ServerPlayNetworking.registerGlobalReceiver(FORFEIT_TYPE, (payload, ctx) ->
                ctx.server().execute(() -> MobBrawlNetworkingServer.onForfeit(payload, ctx.player())));

        ServerPlayNetworking.registerGlobalReceiver(MOUSE_TYPE, (payload, ctx) ->
                ctx.server().execute(() -> MobBrawlNetworkingServer.onMouse(payload, ctx.player())));
    }

    // ── Payload definitions ───────────────────────────────────────────────────

    // ── CLIENT → SERVER ───────────────────────────────────────────────────────

    /**
     * Host sends updated config to server.
     * Server validates host status then broadcasts MobBrawlConfigSyncPayload to room.
     */
    public record MobBrawlConfigPayload(
            String roomId,
            int    healthMode,
            int    abilitiesMode,
            int    damageMode,
            int    timeLimit,
            int    lives,
            int    arenaType
    ) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, MobBrawlConfigPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeUtf(p.roomId()); buf.writeInt(p.healthMode()); buf.writeInt(p.abilitiesMode()); buf.writeInt(p.damageMode()); buf.writeInt(p.timeLimit()); buf.writeInt(p.lives()); buf.writeInt(p.arenaType()); },
                        buf -> new MobBrawlConfigPayload(buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt())
                );
        public @NonNull Type<? extends CustomPacketPayload> type() { return CONFIG_TYPE; }
    }

    /**
     * Player picks their morph for the fight.
     * Server stores the choice and checks if both players are ready.
     */
    public record MobBrawlMorphPickPayload(
            String roomId,
            String morphTypeId
    ) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, MobBrawlMorphPickPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeUtf(p.roomId()); buf.writeUtf(p.morphTypeId()); },
                        buf -> new MobBrawlMorphPickPayload(buf.readUtf(), buf.readUtf())
                );
        public @NonNull Type<? extends CustomPacketPayload> type() { return MORPH_PICK_TYPE; }
    }

    /**
     * Client reports damage dealt to opponent.
     * Server validates and updates health, checks for life loss.
     */
    public record MobBrawlDamagePayload(
            String roomId,
            float  damage
    ) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, MobBrawlDamagePayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeUtf(p.roomId()); buf.writeFloat(p.damage()); },
                        buf -> new MobBrawlDamagePayload(buf.readUtf(), buf.readFloat())
                );
        public @NonNull Type<? extends CustomPacketPayload> type() { return DAMAGE_TYPE; }
    }

    /**
     * Player signals they are ready on the morph select screen.
     */
    public record MobBrawlReadyPayload(
            String roomId,
            int    healthMode,
            int    abilitiesMode,
            int    damageMode,
            int    timeLimit,
            int    lives,
            int    arenaType
    ) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, MobBrawlReadyPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeUtf(p.roomId()); buf.writeInt(p.healthMode()); buf.writeInt(p.abilitiesMode()); buf.writeInt(p.damageMode()); buf.writeInt(p.timeLimit()); buf.writeInt(p.lives()); buf.writeInt(p.arenaType()); },
                        buf -> new MobBrawlReadyPayload(buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt())
                );
        public @NonNull Type<? extends CustomPacketPayload> type() { return READY_TYPE; }
    }

    // ── SERVER → CLIENT ───────────────────────────────────────────────────────

    /**
     * Full game state sync — sent when phase changes or on request.
     */
    public record MobBrawlStatePayload(
            String roomId,
            int    phase,          // MobBrawlServerGame.Phase ordinal
            float  timer,
            int    hostLives,
            int    guestLives,
            float  hostHealth,
            float  guestHealth,
            float  hostMaxHealth,
            float  guestMaxHealth,
            String hostMorphId,
            String guestMorphId
    ) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, MobBrawlStatePayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeUtf(p.roomId()); buf.writeInt(p.phase()); buf.writeFloat(p.timer());
                            buf.writeInt(p.hostLives()); buf.writeInt(p.guestLives());
                            buf.writeFloat(p.hostHealth()); buf.writeFloat(p.guestHealth()); buf.writeFloat(p.hostMaxHealth()); buf.writeFloat(p.guestMaxHealth());
                            buf.writeUtf(p.hostMorphId()); buf.writeUtf(p.guestMorphId());
                        },
                        buf -> new MobBrawlStatePayload(buf.readUtf(), buf.readInt(), buf.readFloat(),
                                buf.readInt(), buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                                buf.readUtf(), buf.readUtf())
                );
        public @NonNull Type<? extends CustomPacketPayload> type() { return STATE_TYPE; }
    }

    /**
     * Host config broadcast — sent to all room players when host updates config.
     */
    public record MobBrawlConfigSyncPayload(
            String roomId,
            int    healthMode,
            int    abilitiesMode,
            int    damageMode,
            int    timeLimit,
            int    lives,
            int    arenaType
    ) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, MobBrawlConfigSyncPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeUtf(p.roomId()); buf.writeInt(p.healthMode()); buf.writeInt(p.abilitiesMode()); buf.writeInt(p.damageMode()); buf.writeInt(p.timeLimit()); buf.writeInt(p.lives()); buf.writeInt(p.arenaType()); },
                        buf -> new MobBrawlConfigSyncPayload(buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt())
                );
        public @NonNull Type<? extends CustomPacketPayload> type() { return CONFIG_SYNC_TYPE; }
    }

    /**
     * Tells clients to transition screens.
     * phase: 1=open morph select, 2=open fight HUD
     */
    public record MobBrawlStartPayload(
            String roomId,
            int    phase,
            boolean isHost
    ) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, MobBrawlStartPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeUtf(p.roomId()); buf.writeInt(p.phase()); buf.writeBoolean(p.isHost()); },
                        buf -> new MobBrawlStartPayload(buf.readUtf(), buf.readInt(), buf.readBoolean())
                );
        public @NonNull Type<? extends CustomPacketPayload> type() { return START_TYPE; }
    }

    /**
     * Game over — announces winner and final stats.
     */
    public record MobBrawlEndPayload(
            String roomId,
            String winnerUUID,      // UUID string of winner
            String winnerMorphId,   // entity type id of winner's morph
            float  hostDamage,
            float  guestDamage,
            int    hostLivesLeft,
            int    guestLivesLeft,
            boolean isHost,         // whether this client is the host
            String hostName,        // display name of host player
            String guestName        // display name of guest player
    ) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, MobBrawlEndPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeUtf(p.roomId()); buf.writeUtf(p.winnerUUID()); buf.writeUtf(p.winnerMorphId());
                            buf.writeFloat(p.hostDamage()); buf.writeFloat(p.guestDamage());
                            buf.writeInt(p.hostLivesLeft()); buf.writeInt(p.guestLivesLeft()); buf.writeBoolean(p.isHost());
                            buf.writeUtf(p.hostName()); buf.writeUtf(p.guestName());
                        },
                        buf -> new MobBrawlEndPayload(buf.readUtf(), buf.readUtf(), buf.readUtf(),
                                buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readInt(), buf.readBoolean(),
                                buf.readUtf(), buf.readUtf())
                );
        public @NonNull Type<? extends CustomPacketPayload> type() { return END_TYPE; }
    }

    /**
     * Real-time health update — sent every time damage is dealt.
     */
    public record MobBrawlHealthPayload(
            String roomId,
            float  hostHealth,
            float  guestHealth,
            float  hostMaxHealth,
            float  guestMaxHealth,
            int    hostLives,
            int    guestLives
    ) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, MobBrawlHealthPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeUtf(p.roomId()); buf.writeFloat(p.hostHealth()); buf.writeFloat(p.guestHealth()); buf.writeFloat(p.hostMaxHealth()); buf.writeFloat(p.guestMaxHealth()); buf.writeInt(p.hostLives()); buf.writeInt(p.guestLives()); },
                        buf -> new MobBrawlHealthPayload(buf.readUtf(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readInt())
                );
        public @NonNull Type<? extends CustomPacketPayload> type() { return HEALTH_TYPE; }
    }

    /**
     * Countdown tick — sent once per second during countdown phase.
     * count: 3, 2, 1, 0 (0 = GO!)
     */
    public record MobBrawlCountdownPayload(
            String roomId,
            int    count
    ) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, MobBrawlCountdownPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeUtf(p.roomId()); buf.writeInt(p.count()); },
                        buf -> new MobBrawlCountdownPayload(buf.readUtf(), buf.readInt())
                );
        public @NonNull Type<? extends CustomPacketPayload> type() { return COUNTDOWN_TYPE; }
    }

    /** S→C: tells the dying player to open the 3s respawn death screen. Sent only to the victim. */
    public record MobBrawlDeathScreenPayload(
            int livesLeft
    ) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, MobBrawlDeathScreenPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeInt(p.livesLeft()),
                        buf -> new MobBrawlDeathScreenPayload(buf.readInt())
                );
        public @NonNull Type<? extends CustomPacketPayload> type() { return DEATH_SCREEN_TYPE; }
    }

    /**
     * Client notifies server that the brawl player died — triggers respawn handling.
     */
    public record MobBrawlPlayerDiedPayload(
            String roomId
    ) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, MobBrawlPlayerDiedPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeUtf(p.roomId()),
                        buf -> new MobBrawlPlayerDiedPayload(buf.readUtf())
                );
        public @NonNull Type<? extends CustomPacketPayload> type() { return DIED_TYPE; }
    }

    /**
     * Client notifies server that they forfeited — opponent wins.
     */
    public record MobBrawlForfeitPayload(
            String roomId
    ) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, MobBrawlForfeitPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeUtf(p.roomId()),
                        buf -> new MobBrawlForfeitPayload(buf.readUtf())
                );
        public @NonNull Type<? extends CustomPacketPayload> type() { return FORFEIT_TYPE; }
    }

    /**
     * Client sends their mouse position on the end screen.
     * Server relays it to the opponent via MobBrawlMouseSyncPayload.
     */
    public record MobBrawlMousePayload(
            String roomId,
            float  mouseX,
            float  mouseY
    ) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, MobBrawlMousePayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeUtf(p.roomId()); buf.writeFloat(p.mouseX()); buf.writeFloat(p.mouseY()); },
                        buf -> new MobBrawlMousePayload(buf.readUtf(), buf.readFloat(), buf.readFloat())
                );
        public @NonNull Type<? extends CustomPacketPayload> type() { return MOUSE_TYPE; }
    }

    /**
     * Server relays opponent's mouse position to each client for end screen animation.
     */
    public record MobBrawlMouseSyncPayload(
            String roomId,
            float  mouseX,
            float  mouseY
    ) implements CustomPacketPayload {
        public static final StreamCodec<RegistryFriendlyByteBuf, MobBrawlMouseSyncPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeUtf(p.roomId()); buf.writeFloat(p.mouseX()); buf.writeFloat(p.mouseY()); },
                        buf -> new MobBrawlMouseSyncPayload(buf.readUtf(), buf.readFloat(), buf.readFloat())
                );
        public @NonNull Type<? extends CustomPacketPayload> type() { return MOUSE_SYNC_TYPE; }
    }
}