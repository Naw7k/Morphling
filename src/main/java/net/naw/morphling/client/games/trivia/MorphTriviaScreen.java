package net.naw.morphling.client.games.trivia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.naw.morphling.client.games.packet.GamesNetworking;
import net.naw.morphling.client.games.packet.RoomsNetworking;
import net.naw.morphling.client.games.ui.MorphFaceRenderConfig;
import net.naw.morphling.client.games.ui.MorphGameModeSelect;
import net.naw.morphling.client.games.ui.RoomBrowserScreen;
import net.naw.morphling.client.games.ui.MorphGamesScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Morph Trivia — game show style trivia screen.

 * Flow:
 *   INTRO → COUNTDOWN (3..2..1) → QUESTION (timer drains) → REVEAL → (next or game over) → GAME_OVER

 * Solo: 3 lives, game ends when lives run out or 10 rounds done.
 * Multiplayer: lives are cosmetic, game always goes full 10 rounds.
 *   - Correct = +100 pts + speed bonus + streak bonus
 *   - Wrong/timeout = -50 pts (floor 0), life deducted, streak resets

 * Streak system (multiplayer):
 *   - Correct answers in a row build a streak
 *   - Streak badge shows max streak reached this game (persists even after breaking)
 *   - Streak badge hidden during question phase (would spoil who answered)
 *   - Streak badge shown on reveal screen leaderboard
 *   - Fire color pulse (orange/yellow) not RGB

 * Player status list (multiplayer, during question):
 *   ● = hasn't answered yet
 *   ✓ = answered correctly (shown after reveal, not during question)
 *   ✗ = answered wrong (shown after reveal, not during question)

 * Score sync: score is synced from server scoreData on each result packet
 * so deductions (-50) are reflected immediately in the HUD star counter.

 * Tile highlight rules:
 *   - During question: clicked tile shows pulsing grey while waiting
 *   - After round ends: correct tile green, all others red
 */
public class MorphTriviaScreen extends Screen {

    // ── Game constants ───────────────────────────────────────────────────────
    private static final int   TOTAL_ROUNDS = 10;
    private static final int   LIVES_START  = 3;
    private static final float ROUND_TIME   = 20f;
    private static final float REVEAL_TIME  = 5f;

    private static final List<TriviaQuestions.Question> QUESTION_BANK = TriviaQuestions.getAll();

    // ── Multiplayer static reference ─────────────────────────────────────────
    private static MorphTriviaScreen currentInstance = null;

    // ── Mode flag ────────────────────────────────────────────────────────────
    private boolean isMultiplayer = false;

    // ── Multiplayer state ────────────────────────────────────────────────────
    private String[] mpRevealScoreData  = new String[0];
    private String   mpWinnerName       = "";
    private boolean  mpIAnsweredCorrectly = false;

    // Player list encoded as "✓NAME:streak" / "✗NAME:0" / "NAME:streak"
    // Updated every tick via TriviaPlayerListPayload
    private String[] mpPlayerNames = new String[0];

    // My current streak and max streak this game
    // maxStreak persists even after streak breaks — shown as badge
    private int mpMyStreak    = 0;
    private int mpMyMaxStreak = 0;

    // Points change this round — shown on reveal screen (+pts or -pts)
    private int mpRoundPointsDelta = 0;

    @SuppressWarnings("unused")
    private String[] mpPlayerAnswers = new String[0];

    // ── Game state ───────────────────────────────────────────────────────────
    private enum Phase { INTRO, COUNTDOWN, QUESTION, REVEAL, GAME_OVER }
    private Phase phase = Phase.INTRO;

    private int   score          = 0;
    private int   lives          = LIVES_START;
    private int   round          = 0;
    private float timer          = ROUND_TIME;
    private float revealTimer    = 0f;
    private float phaseTimer     = 0f;
    private float countdownTimer = 3f;

    private String notificationMsg   = "";
    private float  notificationTimer = 0f;

    private EntityType<?> correctAnswer  = null;
    private String        clueText       = "";
    private List<EntityType<?>> choices  = new ArrayList<>();

    private boolean       answeredCorrect  = false;
    private boolean       answeredWrong    = false;
    private EntityType<?> myPickedAnswer   = null;
    private float         flashTimer       = 0f;
    private float         shakeTimer       = 0f;
    private float         shakeX           = 0f;
    private float         shakeY           = 0f;

    private final List<Integer> usedQuestions = new ArrayList<>();
    private LivingEntity        revealEntity  = null;

    private static final int TILE_SIZE    = 54;
    private static final int TILE_SPACING = 6;
    private static final int COLUMNS      = 6;

    private final List<TriviaAnswerTile> answerTiles = new ArrayList<>();

    private float   introAlpha      = 0f;
    private float   hue             = 0f;
    private boolean showQuitConfirm = false;

    public MorphTriviaScreen() {
        super(Component.literal("Morph Trivia"));
    }

    /** Opens the screen in multiplayer mode — waits for server to drive countdown */
    public MorphTriviaScreen asMultiplayer() {
        this.isMultiplayer   = true;
        this.phase           = Phase.COUNTDOWN;
        this.countdownTimer  = 999f;
        this.mpMyMaxStreak   = 0;
        this.mpMyStreak      = 0;
        return this;
    }

    // ── Init ─────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        if (phase == Phase.INTRO) TriviaPBManager.load();
        answerTiles.clear();
        clearWidgets();
        addQuitButton();
        currentInstance = this;

        if (phase == Phase.INTRO || phase == Phase.COUNTDOWN) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("← Back"),
                    _ -> {
                        if (isMultiplayer) {
                            var mc = Minecraft.getInstance();
                            if (mc.player != null) {
                                ClientPlayNetworking.send(new GamesNetworking.TriviaLeavePayload(mc.player.getUUID()));
                            }
                        }
                        this.onClose();
                        Minecraft.getInstance().setScreen(new MorphGameModeSelect(MorphGameModeSelect.GameMode.TRIVIA));
                    }
            ).bounds(10, 8, 60, 20).build());
        }

        if (phase == Phase.INTRO) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("▶  Start"),
                    _ -> { phase = Phase.COUNTDOWN; countdownTimer = 3f; clearWidgets(); addQuitButton(); }
            ).bounds(this.width / 2 - 50, this.height / 2 + 20, 100, 24).build());
        }

        if (phase == Phase.GAME_OVER) {
            if (isMultiplayer) {
                this.addRenderableWidget(Button.builder(
                        Component.literal("↩ Back to Room"),
                        _ -> Minecraft.getInstance().setScreen(new RoomBrowserScreen(MorphGameModeSelect.GameMode.TRIVIA))
                ).bounds(this.width / 2 - 55, this.height / 2 + 90, 110, 24).build());
            } else {
                this.addRenderableWidget(Button.builder(
                        Component.literal("Play Again"),
                        _ -> { phase = Phase.INTRO; introAlpha = 0f; usedQuestions.clear(); score = 0; lives = LIVES_START; round = 0; init(); }
                ).bounds(this.width / 2 - 55, this.height / 2 + 90, 110, 24).build());
            }
        }

        if (phase == Phase.QUESTION || phase == Phase.REVEAL) rebuildTiles();
    }

    /** Adds the ✕ quit button to top-right. Hidden during countdown and game over. */
    private void addQuitButton() {
        if (phase == Phase.COUNTDOWN || phase == Phase.GAME_OVER) return;
        this.addRenderableWidget(Button.builder(
                Component.literal("✕"),
                _ -> {
                    if (phase == Phase.INTRO || phase == Phase.COUNTDOWN) {
                        if (isMultiplayer) {
                            var mc = Minecraft.getInstance();
                            if (mc.player != null) {
                                ClientPlayNetworking.send(new GamesNetworking.TriviaLeavePayload(mc.player.getUUID()));
                                if (RoomBrowserScreen.lastJoinedRoomId != null) {
                                    ClientPlayNetworking.send(new RoomsNetworking.RoomLeavePayload(
                                            mc.player.getUUID(), RoomBrowserScreen.lastJoinedRoomId));
                                }
                            }
                        }
                        this.onClose();
                        if (isMultiplayer) {
                            Minecraft.getInstance().setScreen(new RoomBrowserScreen(MorphGameModeSelect.GameMode.TRIVIA));
                        } else {
                            Minecraft.getInstance().setScreen(new MorphGamesScreen());
                        }
                    } else {
                        showQuitConfirm = true;
                    }
                }
        ).bounds(this.width - 28, 8, 20, 20).build());
    }

    // ── Solo game logic ───────────────────────────────────────────────────────

    private void startGame() {
        score = 0; lives = LIVES_START; round = 0;
        usedQuestions.clear();
        nextQuestion();
    }

    private void nextQuestion() {
        phase           = Phase.QUESTION;
        timer           = ROUND_TIME;
        flashTimer      = 0f;
        shakeTimer      = 0f;
        answeredCorrect = false;
        answeredWrong   = false;
        myPickedAnswer  = null;
        phaseTimer      = 0f;

        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < QUESTION_BANK.size(); i++) {
            if (!usedQuestions.contains(i)) available.add(i);
        }
        if (available.isEmpty()) {
            usedQuestions.clear();
            for (int i = 0; i < QUESTION_BANK.size(); i++) available.add(i);
        }
        Collections.shuffle(available);
        int picked = available.getFirst();
        usedQuestions.add(picked);

        correctAnswer = QUESTION_BANK.get(picked).answer();
        clueText      = QUESTION_BANK.get(picked).clue();

        List<EntityType<?>> pool = new ArrayList<>();
        for (TriviaQuestions.Question q : QUESTION_BANK) {
            if (!pool.contains(q.answer())) pool.add(q.answer());
        }
        pool.remove(correctAnswer);
        Collections.shuffle(pool);
        choices = new ArrayList<>();
        choices.add(correctAnswer);
        for (int i = 0; i < 5 && i < pool.size(); i++) choices.add(pool.get(i));
        Collections.shuffle(choices);

        clearWidgets();
        addQuitButton();
        rebuildTiles();
    }

    private void rebuildTiles() {
        answerTiles.forEach(this::removeWidget);
        answerTiles.clear();

        int gridWidth  = TILE_SIZE * COLUMNS + TILE_SPACING * (COLUMNS - 1);
        int gridStartX = (this.width - gridWidth) / 2;
        int gridStartY = this.height - TILE_SIZE - 30;

        for (int i = 0; i < choices.size(); i++) {
            EntityType<?> type = choices.get(i);
            int x = gridStartX + i * (TILE_SIZE + TILE_SPACING);
            TriviaAnswerTile tile = new TriviaAnswerTile(x, gridStartY, TILE_SIZE, type, this);
            answerTiles.add(tile);
            this.addRenderableWidget(tile);
        }
    }

    void onAnswer(EntityType<?> chosen) {
        if (phase != Phase.QUESTION) return;
        if (answeredCorrect || answeredWrong) return;

        myPickedAnswer = chosen;

        if (isMultiplayer) {
            int choiceIndex = choices.indexOf(chosen);
            var mc = Minecraft.getInstance();
            if (mc.player != null && choiceIndex >= 0) {
                ClientPlayNetworking.send(new GamesNetworking.TriviaAnswerPayload(
                        mc.player.getUUID(), choiceIndex, timer));
            }
            answeredWrong = true;
            return;
        }

        if (chosen == correctAnswer) {
            answeredCorrect = true;
            flashTimer = 1f;
            int bonus = (int)(timer / ROUND_TIME * 100);
            score += 100 + bonus;
            playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.5f);
            playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 2.0f);
        } else {
            answeredWrong = true;
            flashTimer    = 1f;
            shakeTimer    = 0.5f;
            lives--;
            playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.5f);
        }

        spawnRevealEntity();
        phase       = Phase.REVEAL;
        revealTimer = REVEAL_TIME;
    }

    private void spawnRevealEntity() {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var e = correctAnswer.create(level, EntitySpawnReason.LOAD);
        if (e instanceof LivingEntity le) revealEntity = le;
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    sound, SoundSource.PLAYERS, volume, pitch, false);
        }
    }

    // ── Multiplayer packet handlers ───────────────────────────────────────────

    public static void onMultiplayerStart(int ignoredTotalRounds, int ignoredLives) {
        if (currentInstance == null) return;
        currentInstance.phase          = Phase.COUNTDOWN;
        currentInstance.countdownTimer = 3f;
        currentInstance.clearWidgets();
        currentInstance.addQuitButton();
    }

    public static void onMultiplayerQuestion(int round, String clue, String[] choiceIds) {
        if (currentInstance == null) return;
        var screen = currentInstance;
        screen.round              = round;
        screen.clueText           = clue;
        screen.answeredCorrect    = false;
        screen.answeredWrong      = false;
        screen.myPickedAnswer     = null;
        screen.timer              = ROUND_TIME;
        screen.phaseTimer         = 0f;
        screen.flashTimer         = 0f;
        screen.mpWinnerName       = "";
        screen.mpPlayerAnswers    = new String[0];
        screen.mpIAnsweredCorrectly = false;
        screen.mpRoundPointsDelta = 0;

        screen.choices = new ArrayList<>();
        for (String id : choiceIds) {
            screen.choices.add(BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id)));
        }
        screen.correctAnswer = null;

        screen.phase = Phase.QUESTION;
        screen.clearWidgets();
        screen.addQuitButton();
        screen.rebuildTiles();
    }

    /**
     * Server sent round result.
     * Syncs score from server scoreData so deductions (-50) are reflected immediately.
     * Calculates round points delta for reveal screen display (+pts or -pts).
     */
    public static void onMultiplayerResult(String winnerName, String correctEntityId,
                                           String correctClue, int ignoredWinnerScore,
                                           String[] scoreData) {
        if (currentInstance == null) return;
        var screen = currentInstance;

        screen.correctAnswer     = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(correctEntityId));
        screen.clueText          = correctClue;
        screen.mpWinnerName      = winnerName;
        screen.mpRevealScoreData = scoreData;

        // Sync score from server — captures both gains and deductions (-50 penalty)
        var mc = Minecraft.getInstance();
        int prevScore = screen.score;
        if (mc.player != null) {
            String myName = mc.player.getName().getString();
            for (int i = 0; i + 1 < scoreData.length; i += 2) {
                if (scoreData[i].equals(myName)) {
                    try {
                        screen.score = Integer.parseInt(scoreData[i + 1]);
                    } catch (Exception ignored) {}
                    break;
                }
            }
        }
        // Calculate delta for reveal screen (+pts or -pts display)
        screen.mpRoundPointsDelta = screen.score - prevScore;



        if (screen.mpIAnsweredCorrectly) {
            screen.answeredCorrect = true;
            screen.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.5f);
            screen.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 2.0f);

        } else {
            screen.answeredWrong = true;
            screen.lives--;
            screen.mpMyStreak = 0; // streak broken — update immediately before streak loop below
            screen.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.5f);
        }
        screen.mpIAnsweredCorrectly = false;

        screen.spawnRevealEntity();
        screen.flashTimer  = 1f;
        screen.phase       = Phase.REVEAL;
        screen.revealTimer = REVEAL_TIME;
        // Update streak only if answered correctly — wrong already reset to 0 above
        if (screen.answeredCorrect && mc.player != null) {
            String myName = mc.player.getName().getString();
            for (String encoded : screen.mpPlayerNames) {
                boolean hasPrefix = encoded.startsWith("✓") || encoded.startsWith("✗");
                String  stripped  = hasPrefix ? encoded.substring(1) : encoded;
                String  cleanName = stripped.contains(":") ? stripped.substring(0, stripped.lastIndexOf(':')) : stripped;
                if (cleanName.equals(myName)) {
                    try {
                        if (stripped.contains(":")) {
                            int streak = Integer.parseInt(stripped.substring(stripped.lastIndexOf(':') + 1));
                            screen.mpMyStreak = streak;
                            if (streak > screen.mpMyMaxStreak) screen.mpMyMaxStreak = streak;
                        }
                    } catch (Exception ignored) {}
                    break;
                }
            }
        }
        screen.rebuildTiles();
    }

    public static void onMultiplayerEnd(String[] scoreData) {
        if (currentInstance == null) return;
        currentInstance.mpRevealScoreData = scoreData;
        currentInstance.phase = Phase.GAME_OVER;
        currentInstance.clearWidgets();
        currentInstance.addQuitButton();
        TriviaPBManager.save(currentInstance.score);

        currentInstance.addRenderableWidget(Button.builder(
                Component.literal("↩ Back to Room"),
                _ -> Minecraft.getInstance().setScreen(new RoomBrowserScreen(MorphGameModeSelect.GameMode.TRIVIA))
        ).bounds(currentInstance.width / 2 - 55, currentInstance.height / 2 + 90, 110, 24).build());
    }

    /**
     * Server sent player list update.
     * Names encoded as: "✓NAME:streak" / "✗NAME:0" / "NAME:streak"
     * Parses our own status and updates streak tracking.
     * Only rebuilds widgets if not in QUESTION or REVEAL phase.
     */
    public static void onMultiplayerPlayerList(String[] playerNames, int[] ignoredScores) {
        if (currentInstance == null) return;
        if (playerNames.length >= 2 && playerNames[0].equals("__NOTIFY__")) {
            showNotification(playerNames[1]);
            return;
        }

        currentInstance.mpPlayerNames = playerNames;

        // Parse our own answer status and streak
        var mc2 = Minecraft.getInstance();
        if (mc2.player != null) {
            String myName = mc2.player.getName().getString();
            for (String encoded : playerNames) {
                boolean hasCorrect = encoded.startsWith("✓");
                boolean hasWrong   = encoded.startsWith("✗");
                String  stripped   = (hasCorrect || hasWrong) ? encoded.substring(1) : encoded;
                String  cleanName  = stripped.contains(":") ? stripped.substring(0, stripped.lastIndexOf(':')) : stripped;
                int     streak     = 0;
                try {
                    if (stripped.contains(":"))
                        streak = Integer.parseInt(stripped.substring(stripped.lastIndexOf(':') + 1));
                } catch (Exception ignored) {}

                if (cleanName.equals(myName)) {
                    if (hasCorrect) currentInstance.mpIAnsweredCorrectly = true;
                    // Only update streak display during reveal/game over — not during question to avoid spoiling
                    if (currentInstance.phase != Phase.QUESTION) {
                        currentInstance.mpMyStreak = streak;
                        if (streak > currentInstance.mpMyMaxStreak)
                            currentInstance.mpMyMaxStreak = streak;
                    }
                    break;
                }
            }
        }

        if (currentInstance.phase == Phase.QUESTION || currentInstance.phase == Phase.REVEAL) return;

        currentInstance.clearWidgets();
        currentInstance.addQuitButton();
        currentInstance.addRenderableWidget(Button.builder(
                Component.literal("← Back"),
                _ -> {
                    if (currentInstance.isMultiplayer) {
                        var mc = Minecraft.getInstance();
                        if (mc.player != null)
                            ClientPlayNetworking.send(new GamesNetworking.TriviaLeavePayload(mc.player.getUUID()));
                    }
                    currentInstance.onClose();
                    Minecraft.getInstance().setScreen(new MorphGameModeSelect(MorphGameModeSelect.GameMode.TRIVIA));
                }
        ).bounds(10, 8, 60, 20).build());

        if (playerNames.length > 0
                && Minecraft.getInstance().player != null
                && playerNames[0].equals(Minecraft.getInstance().player.getName().getString())) {
            currentInstance.addRenderableWidget(Button.builder(
                    Component.literal("▶  Start Game"),
                    _ -> {
                        var mc = Minecraft.getInstance();
                        if (mc.player != null)
                            ClientPlayNetworking.send(new GamesNetworking.TriviaJoinPayload(
                                    mc.player.getUUID(), "HOST_START"));
                    }
            ).bounds(currentInstance.width / 2 - 55, currentInstance.height / 2 + 30, 110, 24).build());
        }
    }

    public static void showNotification(String msg) {
        if (currentInstance == null) return;
        currentInstance.notificationMsg   = msg;
        currentInstance.notificationTimer = 3f;
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float dt = partialTick * 0.05f;
        hue        = (hue + 0.005f) % 1.0f;
        phaseTimer += dt;

        if (shakeTimer > 0) {
            shakeTimer = Math.max(0, shakeTimer - dt);
            float intensity = shakeTimer * 6f;
            shakeX = (float)(Math.sin(phaseTimer * 80) * intensity);
            shakeY = (float)(Math.cos(phaseTimer * 60) * intensity * 0.5f);
        } else { shakeX = 0; shakeY = 0; }

        if (flashTimer > 0) flashTimer = Math.max(0, flashTimer - dt * 2.5f);

        switch (phase) {
            case INTRO     -> renderIntro(graphics, mouseX, mouseY, dt);
            case COUNTDOWN -> renderCountdown(graphics, mouseX, mouseY, dt);
            case QUESTION  -> renderQuestion(graphics, mouseX, mouseY, dt);
            case REVEAL    -> renderReveal(graphics, mouseX, mouseY, dt);
            case GAME_OVER -> renderGameOver(graphics, mouseX, mouseY, dt);
        }

        if (showQuitConfirm) renderQuitConfirm(graphics, mouseX, mouseY);

        if (notificationTimer > 0) {
            notificationTimer -= 0.016f;
            int notifAlpha = (int)(Math.min(1f, notificationTimer) * 200);
            graphics.centeredText(this.font, Component.literal("§8" + notificationMsg),
                    this.width / 2, this.height / 17, withAlpha(0x888888, notifAlpha));
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    // ── INTRO ────────────────────────────────────────────────────────────────

    private void renderIntro(GuiGraphicsExtractor graphics, int ignoredMouseX, int ignoredMouseY, float dt) {
        introAlpha = Math.min(1f, introAlpha + dt * 1.5f);
        graphics.fill(0, 0, this.width, this.height, 0xE0050510);

        for (int i = 0; i < 5; i++) {
            float prog  = ((phaseTimer * 0.3f + i * 0.2f) % 1.0f);
            int   alpha = (int)(Math.sin(prog * Math.PI) * 2);
            int   lx    = (int)(prog * this.width * 1.5f) - this.width / 4;
            graphics.fill(lx, 0, lx + 60, this.height, (alpha << 24) | 0xFFFFFF);
        }

        int cx = this.width / 2;
        int titleAlpha = (int)(introAlpha * 255);
        graphics.centeredText(this.font, Component.literal("MORPH TRIVIA"), cx, this.height / 2 - 40, withAlpha(0xCC55FF, titleAlpha));

        int subAlpha = (int)(Math.max(0, introAlpha - 0.3f) * 255 / 0.7f);
        graphics.centeredText(this.font, Component.literal("§7Guess the mob from its ability"), cx, this.height / 2 - 10, withAlpha(0xAAAAAA, subAlpha));
        graphics.centeredText(this.font, Component.literal("§7" + TOTAL_ROUNDS + " rounds  •  " + LIVES_START + " lives  •  " + (int)ROUND_TIME + " seconds each"), cx, this.height / 2 + 4, withAlpha(0x666666, subAlpha));
        graphics.text(this.font, Component.literal("§8+200 pts instant answer  •  faster = more points"), 10, this.height - 20, withAlpha(0x333333, subAlpha), false);

        if (TriviaPBManager.getPB() > 0)
            graphics.centeredText(this.font, Component.literal("§7Personal Best: §e" + TriviaPBManager.getPB()), cx, this.height / 2 - 22, withAlpha(0x666666, subAlpha));
    }

    // ── COUNTDOWN ────────────────────────────────────────────────────────────

    private void renderCountdown(GuiGraphicsExtractor graphics, int ignoredMouseX, int ignoredMouseY, float dt) {
        countdownTimer -= dt;
        int num = (int)Math.ceil(countdownTimer);
        if (countdownTimer <= 0) { if (!isMultiplayer) startGame(); return; }

        graphics.fill(0, 0, this.width, this.height, 0xF0050510);
        int   cx    = this.width / 2;
        float frac  = countdownTimer % 1.0f;
        float pulse = (float)(Math.sin((1f - frac) * Math.PI) * 0.5 + 0.5);
        int   alpha = 180 + (int)(pulse * 75);

        if (frac > 1f - dt * 2f || countdownTimer >= 3f - dt) {
            float pitch = num == 1 ? 2.0f : 1.0f + (3 - num) * 0.25f;
            playSound(SoundEvents.NOTE_BLOCK_HARP.value(), 0.6f, pitch);
        }

        int countColor = num == 1 ? 0xFF55FF55 : 0xFFCC55FF;
        graphics.centeredText(this.font, Component.literal(String.valueOf(num)), cx, this.height / 2 - 20, withAlpha(countColor & 0x00FFFFFF, alpha));
        graphics.centeredText(this.font, Component.literal("§7Get ready..."), cx, this.height / 2 + 30, withAlpha(0x666666, (int)(pulse * 180)));
    }

    // ── QUESTION ─────────────────────────────────────────────────────────────

    private void renderQuestion(GuiGraphicsExtractor graphics, int ignoredMouseX, int ignoredMouseY, float dt) {
        timer -= dt;
        if (timer <= 0 && !answeredCorrect && !answeredWrong) {
            timer = 0; answeredWrong = true; flashTimer = 1f; shakeTimer = 0.5f; lives--;
            playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.5f);
            if (!isMultiplayer) spawnRevealEntity();
            phase = Phase.REVEAL; revealTimer = REVEAL_TIME;
            return;
        }

        int ox = (int)shakeX, oy = (int)shakeY;
        graphics.fill(0, 0, this.width, this.height, 0xF0050510);

        if (flashTimer > 0) {
            int fc = answeredCorrect ? 0x55FF55 : 0xFF3333;
            graphics.fill(0, 0, this.width, this.height, ((int)(flashTimer * 80) << 24) | fc);
        }

        // ── HUD ──────────────────────────────────────────────────────────────
        graphics.text(this.font, Component.literal("§e⭐ " + score), 12 + ox, 12 + oy, 0xFFFFAA00, false);

        if (!isMultiplayer) {
            // Solo: show hearts
            StringBuilder hearts = new StringBuilder();
            for (int i = 0; i < LIVES_START; i++) hearts.append(i < lives ? "§c❤ " : "§8❤ ");
            graphics.text(this.font, Component.literal(hearts.toString()), 12 + ox, 24 + oy, 0xFFFFFFFF, false);
        } else if (mpMyMaxStreak >= 2) {
            // Multiplayer: streak badge — pulses only when streak is active, static when broken
            // Shows current streak if active, best streak if broken
            boolean isStreakActive = mpMyStreak >= 2;
            float streakPulse = isStreakActive ? (float)(Math.sin(phaseTimer * 5) * 0.5 + 0.5) : 0.5f;
            float fireHue   = 0.05f + streakPulse * 0.07f;
            int   fireRgb   = java.awt.Color.HSBtoRGB(fireHue, 1f, 1f);
            int   fireColor = withAlpha(fireRgb & 0x00FFFFFF, 180 + (int)(streakPulse * 20));
            String label = isStreakActive ? "🔥 " + mpMyStreak + "x streak" : "🔥 " + mpMyMaxStreak + "x best";
            graphics.text(this.font, Component.literal(label), 12 + ox, 24 + oy, fireColor, false);
        }

        String roundStr = "Round " + (round + 1) + " / " + TOTAL_ROUNDS;
        graphics.text(this.font, Component.literal("§7" + roundStr),
                this.width - this.font.width(roundStr) - 36 + ox, 12 + oy, 0xFF888888, false);

        // ── Player status list (multiplayer only) ─────────────────────────────
        // Shows ● for unanswered, no status shown for answered (avoids spoiling)
        // Streak badge NOT shown here — only on reveal screen
        if (isMultiplayer && mpPlayerNames.length > 0) {
            int lbX = 10 + ox;
            int lbY = 50 + oy;
            graphics.text(this.font, Component.literal("§8Players"), lbX, lbY, 0xFF444455, false);
            lbY += 11;
            for (String encoded : mpPlayerNames) {
                boolean hasAnswered = encoded.startsWith("✓") || encoded.startsWith("✗");
                String  stripped    = hasAnswered ? encoded.substring(1) : encoded;
                String  cleanName   = stripped.contains(":") ? stripped.substring(0, stripped.lastIndexOf(':')) : stripped;
                boolean isMe        = Minecraft.getInstance().player != null &&
                        cleanName.equals(Minecraft.getInstance().player.getName().getString());

                // During question: show ● for all (don't reveal who answered to avoid spoiling)
// Show grey dot for answered, slightly brighter for unanswered
                String indicator = hasAnswered ? "§7● " : "§f● ";
                graphics.text(this.font,
                        Component.literal(indicator),
                        lbX, lbY, isMe ? 0xFFCC55FF : 0xFF666677, false);
                int dotWidth = this.font.width(indicator);
                MorphFaceRenderConfig.renderPlayerFace(graphics, cleanName, lbX + dotWidth, lbY - 1);
                graphics.text(this.font,
                        Component.literal((isMe ? "§e" : "§7") + cleanName),
                        lbX + dotWidth + 10, lbY, isMe ? 0xFFCC55FF : 0xFF666677, false);
                lbY += 10;
            }
        }

        // ── Timer bar ────────────────────────────────────────────────────────
        float timerRatio = timer / ROUND_TIME;
        int barW = this.width - 40, barX = 20 + ox, barY = 40 + oy, barH = 5;
        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF222233);
        graphics.fill(barX, barY, barX + (int)(barW * timerRatio), barY + barH, lerpColor(timerRatio));

        if (timerRatio < 0.3f) {
            float pulse     = (float)(Math.sin(phaseTimer * 10) * 0.5 + 0.5);
            int   glowAlpha = (int)(pulse * 60);
            graphics.fill(barX, barY - 1, barX + (int)(barW * timerRatio), barY + barH + 1,
                    (glowAlpha << 24) | 0xFF3333);
        }

        // ── Clue area ────────────────────────────────────────────────────────
        int clueY = 65 + oy;
        float triviaHue = 0.75f + (float)(Math.sin(phaseTimer * 0.8f) * 0.02f);
        int labelColor = 0xFF000000 | (java.awt.Color.HSBtoRGB(triviaHue, 0.8f, 1f) & 0x00FFFFFF);
        graphics.centeredText(this.font, Component.literal("✦  WHAT MOB IS THIS?  ✦"), this.width / 2 + ox, clueY + oy, labelColor);
        graphics.fill(this.width / 2 - 120 + ox, clueY + 14 + oy, this.width / 2 + 120 + ox, clueY + 15 + oy, 0xFF333355);

        int textY = clueY + 24 + oy;
        for (String line : clueText.split("\n")) {
            graphics.centeredText(this.font, Component.literal(line), this.width / 2 + ox, textY, 0xFFDDDDFF);
            textY += 13;
        }

        graphics.centeredText(this.font, Component.literal("§7Click the correct mob"),
                this.width / 2 + ox, this.height - TILE_SIZE - 48 + oy, 0xFF666677);

        if (isMultiplayer && (answeredCorrect || answeredWrong)) {
            float blink = (float)(Math.sin(phaseTimer * 4) * 0.5 + 0.5);
            graphics.centeredText(this.font, Component.literal("§8Waiting for others..."),
                    this.width / 2, this.height - TILE_SIZE - 62, withAlpha(0x888888, (int)(blink * 200)));
        }
    }

    // ── REVEAL ───────────────────────────────────────────────────────────────

    private void renderReveal(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float dt) {
        revealTimer -= dt;
        if (revealTimer <= 0) {
            if (!isMultiplayer) {
                round++;
                if (lives <= 0 || round >= TOTAL_ROUNDS) {
                    phase = Phase.GAME_OVER; clearWidgets(); addQuitButton(); init(); return;
                }
                nextQuestion();
            }
            return;
        }

        float prog = 1f - (revealTimer / REVEAL_TIME);

        int tileGridTop    = this.height - TILE_SIZE - 30;
        int textAreaBottom = tileGridTop - 30;
        int heartsY        = textAreaBottom - 2;
        int bonusY         = heartsY - 14;
        int clueY2         = bonusY - 14;
        int nameY          = clueY2 - 14;
        int hintY          = heartsY + 14;

        graphics.fill(0, 0, this.width, this.height, 0xF2050510);

        if (flashTimer > 0) {
            int fc = answeredCorrect ? 0x55FF55 : 0xFF3333;
            graphics.fill(0, 0, this.width, this.height, ((int)(flashTimer * 60) << 24) | fc);
        }

        int cx = this.width / 2;
        int cy = this.height / 2;

        // ── Winner banner ─────────────────────────────────────────────────────
        if (answeredCorrect) {
            graphics.centeredText(this.font, Component.literal("CORRECT!"), cx, cy - 100, 0xFF55FF55);

        } else if (!mpWinnerName.isEmpty() && isMultiplayer) {
            boolean iWon = Minecraft.getInstance().player != null &&
                    mpWinnerName.equals(Minecraft.getInstance().player.getName().getString());
            if (iWon) {
                graphics.centeredText(this.font, Component.literal("CORRECT!"), cx, cy - 100, 0xFF55FF55);
            } else {
                graphics.centeredText(this.font, Component.literal("WRONG!"), cx, cy - 100, 0xFFFF3333);
                int rgb = java.awt.Color.HSBtoRGB(hue, 1f, 1f);
                graphics.centeredText(this.font,
                        Component.literal("§e" + mpWinnerName + " §awon this round!"),
                        cx, cy - 84, 0xFF000000 | (rgb & 0x00FFFFFF));
            }
        } else {
            graphics.centeredText(this.font, Component.literal("WRONG!"), cx, cy - 100, 0xFFFF3333);
            if (!isMultiplayer && lives <= 0)
                graphics.centeredText(this.font, Component.literal("§cNo lives remaining"), cx, tileGridTop - 15, 0xFFFF5555);
        }

        // ── Entity preview ────────────────────────────────────────────────────
        float scale = Math.min(1f, prog * 5f);
        if (revealEntity != null && scale > 0.1f) {
            int size = (int)(Math.min(50, this.height / 8) * scale);
            try {
                InventoryScreen.extractEntityInInventoryFollowsMouse(
                        graphics, cx - size, nameY - 4 - size * 2, cx + size, nameY - 4,
                        size, 0.0625f, mouseX, mouseY, revealEntity);
            } catch (Exception ignored) {}
        }

        int nameAlpha = (int)(Math.min(1f, prog * 3f) * 255);

        if (correctAnswer != null)
            graphics.centeredText(this.font,
                    Component.literal(correctAnswer.getDescription().getString().toUpperCase()),
                    cx, nameY, withAlpha(0xFFFFFF, nameAlpha));

        graphics.centeredText(this.font,
                Component.literal("§7" + clueText.replace("\n", "  •  ")),
                cx, clueY2, withAlpha(0x888888, nameAlpha));

        // Points delta — show +pts or -pts depending on round result
        if (mpRoundPointsDelta != 0 && isMultiplayer) {
            String deltaStr = mpRoundPointsDelta > 0
                    ? "§a+" + mpRoundPointsDelta + " pts"
                    : "§c" + mpRoundPointsDelta + " pts";
            graphics.centeredText(this.font, Component.literal(deltaStr), cx, bonusY, withAlpha(mpRoundPointsDelta > 0 ? 0x55FF55 : 0xFF5555, nameAlpha));
        } else if (answeredCorrect && !isMultiplayer) {
            // Solo: show pts gain
            int bonus = (int)(timer / ROUND_TIME * 100);
            graphics.centeredText(this.font, Component.literal("§e+" + (100 + bonus) + " pts"), cx, bonusY, withAlpha(0xFFAA00, nameAlpha));
        }

        // Solo: show hearts
        if (!isMultiplayer) {
            StringBuilder hearts = new StringBuilder();
            for (int i = 0; i < LIVES_START; i++) hearts.append(i < lives ? "§c❤ " : "§8❤ ");
            graphics.centeredText(this.font, Component.literal(hearts.toString()), cx, heartsY, 0xFFFFFFFF);
        }

        // ── Multiplayer leaderboard — left side during reveal ─────────────────
        // Shows name + score + streak badge for players with streak >= 2
        if (isMultiplayer && mpRevealScoreData.length > 1 && prog > 0.3f) {
            int lbX     = 10;
            int lbY     = cy - 100;
            int lbAlpha = (int)(Math.min(1f, (prog - 0.3f) / 0.3f) * 200);
            graphics.text(this.font, Component.literal("§8── Scores ──"), lbX, lbY, withAlpha(0x444455, lbAlpha), false);
            lbY += 12;
            for (int i = 0; i + 1 < mpRevealScoreData.length; i += 2) {
                String  name     = mpRevealScoreData[i];
                String  pts      = mpRevealScoreData[i + 1];
                boolean isMe     = Minecraft.getInstance().player != null && name.equals(Minecraft.getInstance().player.getName().getString());
                boolean isWinner = name.equals(mpWinnerName);

                // Find streak for this player from mpPlayerNames
                //noinspection ExtractMethodRecommender
                int playerStreak = 0;
                for (String encoded : mpPlayerNames) {
                    boolean hasPrefix = encoded.startsWith("✓") || encoded.startsWith("✗");
                    String  stripped  = hasPrefix ? encoded.substring(1) : encoded;
                    String  cleanN    = stripped.contains(":") ? stripped.substring(0, stripped.lastIndexOf(':')) : stripped;
                    if (cleanN.equals(name)) {
                        try { if (stripped.contains(":")) playerStreak = Integer.parseInt(stripped.substring(stripped.lastIndexOf(':') + 1)); }
                        catch (Exception ignored) {}
                        boolean isMyName = Minecraft.getInstance().player != null && name.equals(Minecraft.getInstance().player.getName().getString());
                        if (isMyName && answeredWrong && !answeredCorrect) playerStreak = 0;
                        else if (encoded.startsWith("✗")) playerStreak = 0;
                        break;
                    }
                }

                String streakSuffix = playerStreak >= 2 ? " §6🔥" + playerStreak : "";
                int    nameColor    = isWinner ? 0xFFFFAA00 : (isMe ? 0xFFCC55FF : 0xFF888888);
                graphics.text(this.font,
                        Component.literal((isWinner ? "★ " : "  ") + name + " §8" + pts + streakSuffix),
                        lbX, lbY, withAlpha(nameColor, lbAlpha), false);
                lbY += 11;
            }
        }

        if (!isMultiplayer && prog > 0.6f && round + 1 < TOTAL_ROUNDS && lives > 0) {
            float blink = (float)(Math.sin(phaseTimer * 6) * 0.5 + 0.5);
            graphics.centeredText(this.font,
                    Component.literal("Next round in " + (int)(revealTimer + 1) + "s..."),
                    cx, hintY, withAlpha(0x555566, (int)(blink * 200)));
        }
    }

    // ── GAME OVER ────────────────────────────────────────────────────────────

    private void renderGameOver(GuiGraphicsExtractor graphics, int ignoredMouseX, int ignoredMouseY, float ignoredDt) {
        graphics.fill(0, 0, this.width, this.height, 0xF5050510);
        for (int y = 0; y < this.height; y += 4) graphics.fill(0, y, this.width, y + 1, 0x08000000);

        int cx = this.width / 2;

        if (isMultiplayer) {
            // Multiplayer: pulsing green "GAME COMPLETE"
            float pulse = (float)(Math.sin(phaseTimer * 3) * 0.5 + 0.5);
            int   gc    = withAlpha(0x55FF55, 180 + (int)(pulse * 75));
            graphics.centeredText(this.font, Component.literal("GAME COMPLETE!"), cx, this.height / 2 - 60, gc);
        } else if (lives > 0) {
            int rgb = java.awt.Color.HSBtoRGB(hue, 1f, 1f);
            graphics.centeredText(this.font, Component.literal("GAME COMPLETE!"), cx, this.height / 2 - 60, 0xFF000000 | (rgb & 0x00FFFFFF));
        } else {
            graphics.centeredText(this.font, Component.literal("GAME OVER"), cx, this.height / 2 - 60, 0xFFFF3333);
        }

        graphics.centeredText(this.font, Component.literal("§eFinal Score"), cx, this.height / 2 - 30, 0xFFFFAA00);

        int scoreColor = TriviaPBManager.isNewPB()
                ? 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 1f, 1f) & 0x00FFFFFF)
                : 0xFFFFAA00;
        graphics.centeredText(this.font, Component.literal(String.valueOf(score)), cx, this.height / 2 - 15, scoreColor);

        graphics.centeredText(this.font, Component.literal("§6Rank: §e" + getRank(score)), cx, this.height / 2 + 10, 0xFFFFDD55);

        int displayedRound = isMultiplayer ? round + 1 : Math.min(round, TOTAL_ROUNDS);
        graphics.centeredText(this.font, Component.literal("§7Rounds completed: §f" + displayedRound + " / " + TOTAL_ROUNDS), cx, this.height / 2 + 22, 0xFF888888);

        if (!isMultiplayer) {
            graphics.centeredText(this.font, Component.literal("§7Lives remaining: §c" + lives), cx, this.height / 2 + 34, 0xFF888888);
            graphics.centeredText(this.font, Component.literal("§7PB: §e" + TriviaPBManager.getPB()), cx, this.height / 2 + 46, 0xFF888888);
        } else if (mpMyMaxStreak >= 2) {
            // Show best streak on game over screen
            float sp      = (float)(Math.sin(phaseTimer * 3) * 0.5 + 0.5);
            float fireHue = 0.05f + sp * 0.07f;
            int   fireRgb = java.awt.Color.HSBtoRGB(fireHue, 1f, 1f);
            graphics.centeredText(this.font,
                    Component.literal("🔥 Best streak: " + mpMyMaxStreak + "x"),
                    cx, this.height / 2 + 34, withAlpha(fireRgb & 0x00FFFFFF, 200));
        }

        // ── Multiplayer final leaderboard — top left, sorted by score ────────────
        if (isMultiplayer && mpRevealScoreData.length > 1) {
            int lbY = 10;
            graphics.text(this.font, Component.literal("§7── Final Leaderboard ──"), 10, lbY, 0xFF555566, false);
            lbY += 14;

            // Sort entries by score descending
            List<String[]> entries = new ArrayList<>();
            for (int i = 0; i + 1 < mpRevealScoreData.length; i += 2) {
                entries.add(new String[]{mpRevealScoreData[i], mpRevealScoreData[i + 1]});
            }
            entries.sort((a, b) -> Integer.compare(Integer.parseInt(b[1]), Integer.parseInt(a[1])));

            int place = 1;
            for (String[] entry : entries) {
                String  name  = entry[0];
                String  pts   = entry[1];
                boolean isMe  = Minecraft.getInstance().player != null && name.equals(Minecraft.getInstance().player.getName().getString());
                String placeStr = place == 1 ? "§6#1 " : place == 2 ? "§7#2 " : "§8#" + place + " ";
                graphics.text(this.font, Component.literal(placeStr), 10, lbY, 0xFFFFFFFF, false);
                int placeWidth = this.font.width(placeStr);
                MorphFaceRenderConfig.renderPlayerFace(graphics, name, 10 + placeWidth, lbY - 1);
                graphics.text(this.font,
                        Component.literal((isMe ? "§e" : "§f") + name + " §8— §f" + pts + " pts"),
                        10 + placeWidth + 10, lbY, 0xFFFFFFFF, false);
                lbY += 12;
                place++;
            }
        }
    }

    private String getRank(int s) {
        if (s >= (isMultiplayer ? 2000 : 1700)) return "S  — Mob Master";
        if (s >= 1400) return "A  — Morph Expert";
        if (s >= 1000) return "B  — Getting There";
        if (s >= 600)  return "C  — Still Learning";
        return                "D  — Keep Practicing";
    }

    // ── QUIT CONFIRM ─────────────────────────────────────────────────────────

    private void renderQuitConfirm(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, this.height, 0xAA000000);
        int cx = this.width / 2;
        int tileGridTop = this.height - TILE_SIZE - 30;
        int cy = tileGridTop / 2 + 20;
        int w = 200, h = 80;
        int x = cx - w / 2, y = cy - h / 2;

        graphics.fill(x, y, x + w, y + h, 0xFF111122);
        graphics.fill(x, y, x + w, y + 1, 0xFFCC55FF);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFFCC55FF);
        graphics.fill(x, y, x + 1, y + h, 0xFFCC55FF);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFFCC55FF);

        graphics.centeredText(this.font, Component.literal("Quit the game?"),               cx, y + 14, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.literal("§7Your progress will be lost."), cx, y + 26, 0xFF888888);

        int btnW = 80, btnH = 18, btnY = y + h - 28;
        int yesX = cx - btnW - 8;
        boolean yesHov = mouseX >= yesX && mouseX < yesX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
        graphics.fill(yesX, btnY, yesX + btnW, btnY + btnH, yesHov ? 0xFFAA3333 : 0xFF551111);
        graphics.centeredText(this.font, Component.literal("✕ Quit"), yesX + btnW / 2, btnY + 5, 0xFFFF5555);

        int noX = cx + 8;
        boolean noHov = mouseX >= noX && mouseX < noX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
        graphics.fill(noX, btnY, noX + btnW, btnY + btnH, noHov ? 0xFF2A4D2A : 0xFF1A331A);
        graphics.centeredText(this.font, Component.literal("▶ Keep Playing"), noX + btnW / 2, btnY + 5, 0xFF55FF55);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(net.minecraft.client.input.@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (showQuitConfirm) {
            int cx = this.width / 2;
            int tileGridTop = this.height - TILE_SIZE - 30;
            int cy = tileGridTop / 2 + 20;
            int w = 200, h = 80;
            @SuppressWarnings("unused") int x = cx - w / 2, y = cy - h / 2;
            int btnW = 70, btnH = 18, btnY = y + h - 28;

            int yesX = cx - btnW - 8;
            if (event.x() >= yesX && event.x() < yesX + btnW && event.y() >= btnY && event.y() < btnY + btnH) {
                showQuitConfirm = false;
                if (isMultiplayer) {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null)
                        ClientPlayNetworking.send(new GamesNetworking.TriviaLeavePayload(mc.player.getUUID()));
                }
                this.onClose();
                if (isMultiplayer) Minecraft.getInstance().setScreen(new RoomBrowserScreen(MorphGameModeSelect.GameMode.TRIVIA));
                else Minecraft.getInstance().setScreen(new MorphGamesScreen());
                return true;
            }
            int noX = cx + 8;
            if (event.x() >= noX && event.x() < noX + btnW && event.y() >= btnY && event.y() < btnY + btnH) {
                showQuitConfirm = false;
                return true;
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static int lerpColor(float t) {
        int ar = (0xFFFF3333 >> 16) & 0xFF, ag = (0xFFFF3333 >> 8) & 0xFF, ab = 0xFFFF3333 & 0xFF;
        int br = (0xFF55FF55 >> 16) & 0xFF, bg = (0xFF55FF55 >> 8) & 0xFF, bb = 0xFF55FF55 & 0xFF;
        return 0xFF000000 | ((int)(ar + (br - ar) * t) << 16) | ((int)(ag + (bg - ag) * t) << 8) | (int)(ab + (bb - ab) * t);
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() { currentInstance = null; super.onClose(); }

    @Override
    public boolean keyPressed(net.minecraft.client.input.@NonNull KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (phase == Phase.INTRO || phase == Phase.COUNTDOWN || phase == Phase.GAME_OVER) {
                this.onClose();
                Minecraft.getInstance().setScreen(new MorphGamesScreen());
            } else {
                showQuitConfirm = !showQuitConfirm;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    // ── Answer tile ──────────────────────────────────────────────────────────

    /**
     * A single mob tile shown as an answer choice in the grid.

     * Highlight rules:
     *   DURING question:
     *     - Clicked tile shows pulsing grey while waiting for others to answer
     *     - All other tiles stay neutral
     *   AFTER round ends (phase = REVEAL):
     *     - Correct tile green, all others red
     */
    static class TriviaAnswerTile extends AbstractWidget {
        private final EntityType<?>     type;
        private final MorphTriviaScreen screen;
        private       LivingEntity      previewEntity;

        TriviaAnswerTile(int x, int y, int size, EntityType<?> type, MorphTriviaScreen screen) {
            super(x, y, size, size, Component.translatable(type.getDescriptionId()));
            this.type   = type;
            this.screen = screen;
            var level = Minecraft.getInstance().level;
            if (level != null) {
                var e = type.create(level, EntitySpawnReason.LOAD);
                if (e instanceof LivingEntity le) previewEntity = le;
            }
        }

        @Override
        protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            boolean roundOver = screen.phase == MorphTriviaScreen.Phase.REVEAL || screen.answeredCorrect;
            boolean isCorrect = type == screen.correctAnswer;
            boolean iMyPick   = type == screen.myPickedAnswer;
            boolean hovered   = !roundOver && !screen.answeredWrong && isHovered();

            int bgColor, borderColor;

            if (roundOver) {
                bgColor     = isCorrect ? 0xFF1A3D1A : 0xFF3D1A1A;
                borderColor = isCorrect ? 0xFF55FF55 : 0xFF553333;
            } else if (iMyPick && screen.answeredWrong) {
                // Pulsing grey while waiting for others
                float pulse    = (float)(Math.sin(screen.phaseTimer * 4) * 0.5 + 0.5);
                int   pulseVal = (int)(pulse * 15);
                bgColor     = (0xFF << 24) | ((0x1A + pulseVal) << 16) | ((0x1A + pulseVal) << 8) | (0x2A + pulseVal);
                borderColor = 0xFF444466;
            } else if (iMyPick && screen.answeredCorrect) {
                bgColor     = 0xFF1A3D1A;
                borderColor = 0xFF55FF55;
            } else {
                bgColor     = hovered ? 0xFF3A3A55 : 0xFF1A1A2A;
                borderColor = hovered ? 0xFF8888FF : 0xFF333355;
            }

            graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
            graphics.fill(getX(), getY(),              getX() + width, getY() + 1,      borderColor);
            graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, borderColor);
            graphics.fill(getX(), getY(),              getX() + 1,     getY() + height, borderColor);
            graphics.fill(getX() + width - 1, getY(),  getX() + width, getY() + height, borderColor);

            if (previewEntity != null) {
                float maxDim = Math.max(previewEntity.getBbHeight(), previewEntity.getBbWidth());
                int   size   = Math.max(8, (int)(28f / Math.max(1.5f, maxDim)));
                try {
                    InventoryScreen.extractEntityInInventoryFollowsMouse(
                            graphics, getX() + 4, getY() + 4, getX() + width - 4, getY() + height - 14,
                            size, 0.0625f, mouseX, mouseY, previewEntity);
                } catch (Exception ignored) {}
            }

            boolean showAnswered = roundOver || (iMyPick && (screen.answeredWrong || screen.answeredCorrect));
            int nameColor = showAnswered && isCorrect ? 0xFF55FF55 : 0xFFAAAAAA;
            graphics.centeredText(Minecraft.getInstance().font,
                    Component.literal(type.getDescription().getString()),
                    getX() + width / 2, getY() + height - 10, nameColor);

            if (roundOver && isCorrect)
                graphics.centeredText(Minecraft.getInstance().font,
                        Component.literal("§a✔"), getX() + width / 2, getY() + 3, 0xFF55FF55);
        }

        @Override
        public void onClick(net.minecraft.client.input.@NonNull MouseButtonEvent event, boolean doubleClick) {
            if (screen.answeredCorrect || screen.answeredWrong) return;
            screen.onAnswer(type);
        }

        @Override
        public void playDownSound(@NonNull SoundManager soundManager) {
            AbstractWidget.playButtonClickSound(soundManager);
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
            defaultButtonNarrationText(narrationElementOutput);
        }
    }
}