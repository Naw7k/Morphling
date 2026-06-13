package net.naw.morphling.client.games.trivia;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.naw.morphling.client.games.GameRoomManager;
import net.naw.morphling.client.games.packet.GamesNetworking;

import java.util.*;

/**
 * Server-side game state manager for Morph Trivia multiplayer.

 * One instance per room — allows multiple simultaneous games.
 * Use getInstance(roomId) to get or create the game for a specific room.
 * Use removeInstance(roomId) when a room is destroyed.

 * Tracks: players, scores, streaks, current question, round, timer.

 * Game flow:
 *   1. Host clicks Start → hostStart() registers room players, broadcasts TriviaStartPayload
 *   2. After 3.5s delay → server sends TriviaQuestionPayload to all
 *   3. Round ends when ALL players answer OR timer runs out
 *   4. After REVEAL_TIME → next question or end game
 *   5. After TOTAL_ROUNDS → TriviaEndPayload broadcast (no early end from lives)

 * Answer logic (multiplayer):
 *   - Every player must answer (or timeout) before round ends
 *   - Correct answer = +100 pts + speed bonus + streak bonus
 *   - Wrong answer = -50 pts (floor 0) + streak resets
 *   - Timeout = -50 pts + streak resets
 *   - Winner = first player who answered correctly (by time submitted)
 *   - Lives are cosmetic only in multiplayer — 3 hearts, lose one per wrong/timeout

 * Streak system:
 *   - Correct answers in a row increase streak
 *   - Streak 2 = +20 bonus, 3 = +40, 4 = +60, etc (+20 per level)
 *   - Wrong answer or timeout resets streak to 0
 *   - Streak sent to clients via broadcastPlayerList name prefix: "✓NAME:streak"

 * Timer is server-tick based (20 ticks/sec).
 */
public class TriviaServerGame {

    // ── Per-room instance map ─────────────────────────────────────────────────
    // Maps roomId → TriviaServerGame instance
    // Allows multiple simultaneous games across different rooms
    private static final Map<String, TriviaServerGame> INSTANCES = new HashMap<>();

    public static TriviaServerGame getInstance(String roomId) {
        return INSTANCES.computeIfAbsent(roomId, _ -> new TriviaServerGame());
    }

    @SuppressWarnings("unused")
    public static void removeInstance(String roomId) {
        INSTANCES.remove(roomId);
    }

    /** Ticks all active game instances — call from server tick event */
    public static void tickAll(net.minecraft.server.MinecraftServer server) {
        for (TriviaServerGame game : INSTANCES.values()) {
            game.tick(server);
        }
    }

    // ── Config ───────────────────────────────────────────────────────────────
    private static final int   TOTAL_ROUNDS       = 10;
    private static final int   LIVES_START        = 3;   // cosmetic only in multiplayer
    private static final float ROUND_TIME         = 20f; // seconds
    private static final float REVEAL_TIME        = 5f;  // seconds between rounds
    private static final int   WRONG_PENALTY      = 50;  // pts deducted for wrong/timeout
    private static final int   STREAK_BONUS_PER   = 20;  // bonus pts per streak level (2x=+20, 3x=+40...)

    // ── State ────────────────────────────────────────────────────────────────
    private enum GameState { IDLE, LOBBY, QUESTION, REVEAL, ENDED }
    private GameState state = GameState.IDLE;

    private final Map<UUID, String>  playerNames = new LinkedHashMap<>();
    private final Map<UUID, Integer> scores      = new LinkedHashMap<>();
    private final Map<UUID, Integer> lives       = new LinkedHashMap<>();
    private final Map<UUID, Integer> streaks     = new LinkedHashMap<>(); // correct answer streaks

    // Per-round tracking — who answered and whether they were correct
    // answeredThisRound: all UUIDs who submitted any answer this round (insertion order = submission order)
    // correctThisRound: UUID → true if correct, false if wrong
    // firstCorrectUuid: first player to answer correctly this round (wins the round)
    private final Set<UUID>          answeredThisRound = new LinkedHashSet<>();
    private final Map<UUID, Boolean> correctThisRound  = new LinkedHashMap<>();
    private UUID   firstCorrectUuid  = null;
    private int    firstCorrectBonus = 0;

    private final List<TriviaQuestions.Question> questionBank = TriviaQuestions.getAll();
    private final List<Integer> usedQuestions = new ArrayList<>();

    private int    currentRound       = 0;
    private int    correctAnswerIndex = -1;
    private String correctEntityId    = "";
    private String currentClue        = "";
    private String roomId             = null;
    @SuppressWarnings("FieldCanBeLocal")
    private String[] currentChoiceIds = new String[0];

    private float   stateTimer      = 0f;
    private boolean roundAnswered   = false;
    private boolean pendingStart    = false;
    private float   startDelayTimer = 0f;

    // ── Server tick ──────────────────────────────────────────────────────────

    /**
     * Drives the question timer and auto-advances rounds.
     * Called every server tick via tickAll().
     */
    public void tick(net.minecraft.server.MinecraftServer server) {
        if (state == GameState.IDLE || state == GameState.ENDED) return;

        stateTimer += 1f / 20f; // one server tick = 1/20 second

        if (state == GameState.QUESTION && !roundAnswered && stateTimer >= ROUND_TIME) {
            // Time ran out — end round for everyone who hasn't answered
            roundAnswered = true;
            endRound(server);
        }

        if (state == GameState.REVEAL && stateTimer >= REVEAL_TIME) {
            currentRound++;
            // Multiplayer: game always goes full TOTAL_ROUNDS regardless of lives
            if (currentRound >= TOTAL_ROUNDS) {
                endGame(server);
            } else {
                startNextQuestion(server);
            }
        }

        if (pendingStart) {
            startDelayTimer -= 1f / 20f;
            if (startDelayTimer <= 0) {
                pendingStart = false;
                startNextQuestion(server);
            }
        }
    }

    // ── Player events ────────────────────────────────────────────────────────

    public void onPlayerLeave(ServerPlayer player) {
        String leftName = playerNames.getOrDefault(player.getUUID(), "A player");
        UUID   uuid     = player.getUUID();

        playerNames.remove(uuid);
        scores.remove(uuid);
        lives.remove(uuid);
        streaks.remove(uuid);
        answeredThisRound.remove(uuid);
        correctThisRound.remove(uuid);

        if (playerNames.isEmpty()) {
            if (roomId != null) GameRoomManager.getInstance().markWaiting(roomId, player.level().getServer());
            reset();
        } else {
            broadcastNotification(player.level().getServer(), leftName + " left the game");
            broadcastPlayerList(player.level().getServer());

            // If everyone remaining has now answered, end the round
            if (state == GameState.QUESTION && !roundAnswered
                    && answeredThisRound.containsAll(playerNames.keySet())) {
                roundAnswered = true;
                endRound(player.level().getServer());
            }
        }
    }

    private void broadcastNotification(net.minecraft.server.MinecraftServer server, String msg) {
        var payload = new GamesNetworking.TriviaPlayerListPayload(
                new String[]{"__NOTIFY__", msg}, new int[]{0, 0});
        for (UUID uuid : playerNames.keySet()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) ServerPlayNetworking.send(sp, payload);
        }
    }

    public void onPlayerAnswer(ServerPlayer player, int answerIndex, float timeRemaining) {
        if (state != GameState.QUESTION) return;
        if (roundAnswered) return;

        UUID uuid = player.getUUID();
        if (!playerNames.containsKey(uuid)) return;
        if (answeredThisRound.contains(uuid)) return; // already answered this round

        // Record the answer
        answeredThisRound.add(uuid);
        boolean correct = answerIndex == correctAnswerIndex;
        correctThisRound.put(uuid, correct);

        if (correct) {
            int speedBonus  = (int)(timeRemaining / ROUND_TIME * 100);
            int streak      = streaks.getOrDefault(uuid, 0) + 1;
            int streakBonus = streak >= 2 ? (streak - 1) * STREAK_BONUS_PER : 0;
            int pts         = 100 + speedBonus + streakBonus;
            scores.merge(uuid, pts, Integer::sum);
            streaks.put(uuid, streak);

            // Track first correct answerer (by submission order via LinkedHashSet)
            if (firstCorrectUuid == null) {
                firstCorrectUuid  = uuid;
                firstCorrectBonus = pts;
            }
        }

        // Broadcast updated player list so clients can show who answered
        broadcastPlayerList(player.level().getServer());

        // Check if all players have now answered
        if (answeredThisRound.containsAll(playerNames.keySet())) {
            roundAnswered = true;
            endRound(player.level().getServer());
        }
    }

    /**
     * Ends the current round — applies penalties, deducts lives, broadcasts result, transitions to REVEAL.
     * Called either when all players answer or when timer runs out.
     */
    private void endRound(net.minecraft.server.MinecraftServer server) {
        String winnerName  = firstCorrectUuid != null ? playerNames.get(firstCorrectUuid) : "";
        int    winnerScore = firstCorrectBonus;
        broadcastResult(server, winnerName, winnerScore);
        state      = GameState.REVEAL;
        stateTimer = 0f;
    }

    // ── Game flow ─────────────────────────────────────────────────────────────

    /**
     * Called when host clicks Start Game.
     * Registers all players from the room, resets state, starts countdown.
     */
    public void hostStart(net.minecraft.server.MinecraftServer server, String roomId) {
        GameRoomManager.Room room = GameRoomManager.getInstance().getRoomById(roomId);
        if (room == null) return;

        // Full reset before new game
        reset();
        this.roomId = roomId;

        for (int i = 0; i < room.playerUuids.size(); i++) {
            UUID   uuid = room.playerUuids.get(i);
            String name = room.playerNames.get(i);
            playerNames.put(uuid, name);
            scores.put(uuid, 0);
            lives.put(uuid, LIVES_START);
            streaks.put(uuid, 0);
        }
        state = GameState.LOBBY;
        GameRoomManager.getInstance().markInProgress(roomId, server);

        // Broadcast start to all players in the room
        var payload = new GamesNetworking.TriviaStartPayload(TOTAL_ROUNDS, LIVES_START);
        for (UUID uuid : playerNames.keySet()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) ServerPlayNetworking.send(sp, payload);
        }

        // Wait 3.5s for client countdown to finish before sending first question
        pendingStart    = true;
        startDelayTimer = 3.5f;
    }

    private void startNextQuestion(net.minecraft.server.MinecraftServer server) {
        state             = GameState.QUESTION;
        roundAnswered     = false;
        stateTimer        = 0f;
        firstCorrectUuid  = null;
        firstCorrectBonus = 0;
        answeredThisRound.clear();
        correctThisRound.clear();

        // Pick unused question
        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < questionBank.size(); i++) {
            if (!usedQuestions.contains(i)) available.add(i);
        }
        if (available.isEmpty()) {
            usedQuestions.clear();
            for (int i = 0; i < questionBank.size(); i++) available.add(i);
        }
        Collections.shuffle(available);
        int picked = available.getFirst();
        usedQuestions.add(picked);

        TriviaQuestions.Question q = questionBank.get(picked);
        EntityType<?> correct = q.answer();
        currentClue     = q.clue();
        correctEntityId = BuiltInRegistries.ENTITY_TYPE.getKey(correct).toString();

        // Build 6 choices: correct + 5 random others
        List<EntityType<?>> pool = new ArrayList<>();
        for (TriviaQuestions.Question tq : questionBank) {
            if (!pool.contains(tq.answer())) pool.add(tq.answer());
        }
        pool.remove(correct);
        Collections.shuffle(pool);

        List<EntityType<?>> choices = new ArrayList<>();
        choices.add(correct);
        for (int i = 0; i < 5 && i < pool.size(); i++) choices.add(pool.get(i));
        Collections.shuffle(choices);

        correctAnswerIndex = choices.indexOf(correct);
        currentChoiceIds   = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) {
            currentChoiceIds[i] = BuiltInRegistries.ENTITY_TYPE.getKey(choices.get(i)).toString();
        }

        // Broadcast question to all players (correct index NOT sent to clients)
        var payload = new GamesNetworking.TriviaQuestionPayload(currentRound, currentClue, currentChoiceIds);
        for (UUID uuid : playerNames.keySet()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) ServerPlayNetworking.send(sp, payload);
        }

        // Broadcast fresh player list so clients reset answered status display
        broadcastPlayerList(server);
    }

    private void broadcastResult(net.minecraft.server.MinecraftServer server, String winnerName, int winnerScore) {
        // Apply penalties and deduct lives for wrong/unanswered players
        for (UUID uuid : playerNames.keySet()) {
            boolean answeredCorrectly = Boolean.TRUE.equals(correctThisRound.get(uuid));
            if (!answeredCorrectly) {
                // Wrong answer or timeout — deduct points (floor 0), reset streak, lose a life
                int current = scores.getOrDefault(uuid, 0);
                scores.put(uuid, Math.max(0, current - WRONG_PENALTY));
                streaks.put(uuid, 0);
                lives.merge(uuid, -1, Integer::sum);
            }
        }

        String[] scoreData = buildScoreData();
        var payload = new GamesNetworking.TriviaResultPayload(
                winnerName, correctEntityId, currentClue, winnerScore, scoreData);
        for (UUID uuid : playerNames.keySet()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) ServerPlayNetworking.send(sp, payload);
        }
    }

    private void endGame(net.minecraft.server.MinecraftServer server) {
        if (roomId != null) GameRoomManager.getInstance().markWaiting(roomId, server);
        state = GameState.ENDED;
        String[] scoreData = buildScoreData();
        var payload = new GamesNetworking.TriviaEndPayload(scoreData);
        for (UUID uuid : playerNames.keySet()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) ServerPlayNetworking.send(sp, payload);
        }
    }

    /**
     * Broadcasts current player list to all players.
     * Name encoding for client-side display:
     *   "✓NAME:streak" = answered correctly this round (streak = current streak count)
     *   "✗NAME:0"      = answered wrong this round
     *   "NAME:streak"  = hasn't answered yet (streak from previous rounds)
     * Client parses prefix and suffix to show status indicators and streak.
     */
    private void broadcastPlayerList(net.minecraft.server.MinecraftServer server) {
        String[] names     = new String[playerNames.size()];
        int[]    scoresArr = new int[playerNames.size()];
        int i = 0;
        for (Map.Entry<UUID, String> entry : playerNames.entrySet()) {
            UUID   uuid   = entry.getKey();
            String name   = entry.getValue();
            int    streak = streaks.getOrDefault(uuid, 0);

            // Encode answer status as prefix and streak as suffix
            if (correctThisRound.containsKey(uuid)) {
                String prefix = Boolean.TRUE.equals(correctThisRound.get(uuid)) ? "✓" : "✗";
                names[i] = prefix + name + ":" + streak;
            } else {
                names[i] = name + ":" + streak;
            }
            scoresArr[i] = scores.getOrDefault(uuid, 0);
            i++;
        }
        var payload = new GamesNetworking.TriviaPlayerListPayload(names, scoresArr);
        for (UUID uuid : playerNames.keySet()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) ServerPlayNetworking.send(sp, payload);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String[] buildScoreData() {
        List<String> data = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : playerNames.entrySet()) {
            data.add(entry.getValue());
            data.add(String.valueOf(scores.getOrDefault(entry.getKey(), 0)));
        }
        return data.toArray(new String[0]);
    }

    @SuppressWarnings("unused")
    private boolean allPlayersOutOfLives() {
        for (int l : lives.values()) if (l > 0) return false;
        return true;
    }

    private void reset() {
        state = GameState.IDLE;
        playerNames.clear();
        scores.clear();
        lives.clear();
        streaks.clear();
        usedQuestions.clear();
        answeredThisRound.clear();
        correctThisRound.clear();
        firstCorrectUuid  = null;
        firstCorrectBonus = 0;
        currentRound  = 0;
        roundAnswered = false;
        stateTimer    = 0f;
        pendingStart  = false;
        roomId        = null;
    }
}