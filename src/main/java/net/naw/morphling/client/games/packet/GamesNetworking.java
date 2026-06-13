package net.naw.morphling.client.games.packet;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.naw.morphling.client.games.trivia.MorphTriviaScreen;
import net.naw.morphling.client.games.ui.RoomBrowserScreen;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * Networking for Morph Games — separate from MorphlingNetworking.

 * Packets:
 *   Client → Server:
 *     TriviaJoinPayload       — player wants to join/host a trivia game
 *     TriviaAnswerPayload     — player submitted an answer
 *     TriviaLeavePayload      — player left the game

 *   Server → Client:
 *     TriviaStartPayload      — game is starting
 *     TriviaQuestionPayload   — question clue + answer choices
 *     TriviaResultPayload     — who answered correctly, scores update
 *     TriviaEndPayload        — game over, final scores
 *     TriviaPlayerListPayload — current player list + scores for lobby display

 * Room-aware routing:
 *   All server handlers look up the player's room via GameRoomManager,
 *   then route to the correct TriviaServerGame instance for that room.
 *   This allows multiple simultaneous games across different rooms.
 */
public class GamesNetworking {

    private static final String NS = "morphling";

    // ── Client → Server ──────────────────────────────────────────────────────

    /** Player requests to host or join a trivia game */
    public record TriviaJoinPayload(UUID playerUuid, String playerName) implements CustomPacketPayload {
        public static final Type<TriviaJoinPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "trivia_join"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TriviaJoinPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUUID(p.playerUuid()); buf.writeUtf(p.playerName()); },
                buf -> new TriviaJoinPayload(buf.readUUID(), buf.readUtf())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Player submitted an answer — answerIndex is index into the choices list */
    public record TriviaAnswerPayload(UUID playerUuid, int answerIndex, float timeRemaining) implements CustomPacketPayload {
        public static final Type<TriviaAnswerPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "trivia_answer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TriviaAnswerPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUUID(p.playerUuid()); buf.writeInt(p.answerIndex()); buf.writeFloat(p.timeRemaining()); },
                buf -> new TriviaAnswerPayload(buf.readUUID(), buf.readInt(), buf.readFloat())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Player left the game mid-session */
    public record TriviaLeavePayload(UUID playerUuid) implements CustomPacketPayload {
        public static final Type<TriviaLeavePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "trivia_leave"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TriviaLeavePayload> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUUID(p.playerUuid()),
                buf -> new TriviaLeavePayload(buf.readUUID())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ── Server → Client ──────────────────────────────────────────────────────

    /** Sent to all players when the game starts */
    public record TriviaStartPayload(int totalRounds, int lives) implements CustomPacketPayload {
        public static final Type<TriviaStartPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "trivia_start"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TriviaStartPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeInt(p.totalRounds()); buf.writeInt(p.lives()); },
                buf -> new TriviaStartPayload(buf.readInt(), buf.readInt())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Sent to all players at the start of each round.
     * clue is the question text. choiceIds are entity type ids for the 6 choices.
     * correctIndex is which choice is correct (only used server-side, NOT sent to clients).
     */
    public record TriviaQuestionPayload(int round, String clue, String[] choiceIds) implements CustomPacketPayload {
        public static final Type<TriviaQuestionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "trivia_question"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TriviaQuestionPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeInt(p.round());
                    buf.writeUtf(p.clue());
                    buf.writeInt(p.choiceIds().length);
                    for (String id : p.choiceIds()) buf.writeUtf(id);
                },
                buf -> {
                    int round = buf.readInt();
                    String clue = buf.readUtf();
                    int count = buf.readInt();
                    String[] ids = new String[count];
                    for (int i = 0; i < count; i++) ids[i] = buf.readUtf();
                    return new TriviaQuestionPayload(round, clue, ids);
                }
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Sent to all players after someone answers or timer runs out.
     * winnerName is empty string if timed out.
     * correctEntityId is the entity type id of the correct answer.
     * scoreData is a flat array: [name, score, name, score, ...]
     */
    public record TriviaResultPayload(String winnerName, String correctEntityId, String correctClue,
                                      int winnerScore, String[] scoreData) implements CustomPacketPayload {
        public static final Type<TriviaResultPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "trivia_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TriviaResultPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.winnerName());
                    buf.writeUtf(p.correctEntityId());
                    buf.writeUtf(p.correctClue());
                    buf.writeInt(p.winnerScore());
                    buf.writeInt(p.scoreData().length);
                    for (String s : p.scoreData()) buf.writeUtf(s);
                },
                buf -> {
                    String winner = buf.readUtf();
                    String correct = buf.readUtf();
                    String clue = buf.readUtf();
                    int wscore = buf.readInt();
                    int count = buf.readInt();
                    String[] scores = new String[count];
                    for (int i = 0; i < count; i++) scores[i] = buf.readUtf();
                    return new TriviaResultPayload(winner, correct, clue, wscore, scores);
                }
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Sent to all players when the game ends */
    public record TriviaEndPayload(String[] scoreData) implements CustomPacketPayload {
        public static final Type<TriviaEndPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "trivia_end"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TriviaEndPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeInt(p.scoreData().length);
                    for (String s : p.scoreData()) buf.writeUtf(s);
                },
                buf -> {
                    int count = buf.readInt();
                    String[] scores = new String[count];
                    for (int i = 0; i < count; i++) scores[i] = buf.readUtf();
                    return new TriviaEndPayload(scores);
                }
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Sent to players — current player list and scores for lobby/in-game display */
    public record TriviaPlayerListPayload(String[] playerNames, int[] scores) implements CustomPacketPayload {
        public static final Type<TriviaPlayerListPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NS, "trivia_player_list"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TriviaPlayerListPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeInt(p.playerNames().length);
                    for (String n : p.playerNames()) buf.writeUtf(n);
                    for (int s : p.scores()) buf.writeInt(s);
                },
                buf -> {
                    int count = buf.readInt();
                    String[] names = new String[count];
                    int[] scores = new int[count];
                    for (int i = 0; i < count; i++) names[i] = buf.readUtf();
                    for (int i = 0; i < count; i++) scores[i] = buf.readInt();
                    return new TriviaPlayerListPayload(names, scores);
                }
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ── Registration ─────────────────────────────────────────────────────────

    /** Call from client-side mod initializer */
    public static void registerClient() {
        // Game starting — open MorphTriviaScreen then trigger countdown
        ClientPlayNetworking.registerGlobalReceiver(TriviaStartPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> {
                    RoomBrowserScreen.roomInProgress = true;
                    Minecraft.getInstance().setScreen(new MorphTriviaScreen().asMultiplayer());
                    MorphTriviaScreen.onMultiplayerStart(payload.totalRounds(), payload.lives());
                }));

        ClientPlayNetworking.registerGlobalReceiver(TriviaQuestionPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() ->
                        MorphTriviaScreen.onMultiplayerQuestion(payload.round(), payload.clue(), payload.choiceIds())));

        ClientPlayNetworking.registerGlobalReceiver(TriviaResultPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() ->
                        MorphTriviaScreen.onMultiplayerResult(
                                payload.winnerName(), payload.correctEntityId(),
                                payload.correctClue(), payload.winnerScore(), payload.scoreData())));

        ClientPlayNetworking.registerGlobalReceiver(TriviaEndPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> {
                    RoomBrowserScreen.roomInProgress = false;
                    MorphTriviaScreen.onMultiplayerEnd(payload.scoreData());
                }));

        ClientPlayNetworking.registerGlobalReceiver(TriviaPlayerListPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() ->
                        MorphTriviaScreen.onMultiplayerPlayerList(payload.playerNames(), payload.scores())));
    }
}