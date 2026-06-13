package net.naw.morphling.client.games.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.naw.morphling.client.games.packet.GamesNetworking;
import net.naw.morphling.client.games.packet.RoomsNetworking;
import org.jspecify.annotations.NonNull;

/**
 * Room Browser screen — shown when a player clicks Multiplayer on any game mode.

 * Layout:
 *   - Title + game mode label at top
 *   - Scrollable list of open rooms (card per room)
 *   - "Create Room" button at bottom
 *   - Back button top-left (only when browsing, not inside a room)
 *   - Error message shown briefly if join fails

 * Each room card shows:
 *   - Host name + room name
 *   - Game mode icon + name
 *   - Player count / max (e.g. 3/8)
 *   - Status badge (Waiting / In Progress)
 *   - Click to join

 * Create Room dialog:
 *   - Room name text input
 *   - Max players selector (2-16)
 *   - Create / Cancel buttons

 * For MOB_BRAWL lobby:
 *   - Left side shows player list + arena preview
 *   - Right side shows config panel (host edits, guest reads)
 *   - Config changes sent to server immediately via MobBrawlConfigPayload
 *   - Config synced to guest via MobBrawlConfigSyncPayload

 * Static methods (onRoom*) called by RoomsNetworking when packets arrive.
 */
public class RoomBrowserScreen extends Screen {

    // ── Static reference ──────────────────────────────────────────────────────
    private static RoomBrowserScreen currentInstance = null;

    // Last joined room — used by MorphTriviaScreen to rejoin on quit
    public static String   lastJoinedRoomId = null;
    public static String lastJoinedGameMode = null;
    public static String   lastRoomHost     = null;
    public static String[] lastRoomPlayers  = new String[0];
    public static String   lastRoomName     = null;

    public static boolean roomInProgress = false;

    // ── Persisted Mob Brawl config ────────────────────────────────────────────
    // Mirrors the instance brawl* fields so config survives screen recreation
    // (Play Again, end screen, morph-select cancel all build a NEW RoomBrowserScreen).
    // Tied to the room lifecycle: kept while lastJoinedRoomId is set (same room),
    // reset to defaults when leaving/creating a room (see Leave Room handler).
    private static int savedBrawlHealthMode    = 1;
    private static int savedBrawlAbilitiesMode = 0;
    private static int savedBrawlTimeLimitIdx  = 1;
    private static int savedBrawlLivesIdx      = 1;
    private static int savedBrawlArenaType     = 0;

    /** Resets persisted brawl config to defaults — called when leaving/creating a room */
    private static void resetSavedBrawlConfig() {
        savedBrawlHealthMode    = 1;
        savedBrawlAbilitiesMode = 0;
        savedBrawlTimeLimitIdx  = 1;
        savedBrawlLivesIdx      = 1;
        savedBrawlArenaType     = 0;
    }

    // ── Game mode ─────────────────────────────────────────────────────────────
    private MorphGameModeSelect.GameMode gameMode;
    @SuppressWarnings("FieldMayBeFinal")
    private MorphGameModeSelect.GameMode originalGameMode;

    // ── Room data ─────────────────────────────────────────────────────────────
    // Each room: [roomId, hostName, gameMode, playerCount, maxPlayers, status]
    private static final int FIELDS_PER_ROOM = 6;
    private String[] roomData = new String[0];

    // Current room state
    private String   currentRoomId      = null;
    private String   currentRoomHost    = null;
    private String[] currentRoomPlayers = new String[0];
    private String   currentRoomName    = "";

    // ── Create Room dialog ────────────────────────────────────────────────────
    private boolean showCreateDialog = false;
    private String  roomNameInput    = "";
    private int     maxPlayersInput  = 8;

    // ── Error message ─────────────────────────────────────────────────────────
    private String errorMsg   = "";
    private float  errorTimer = 0f;

    // ── Animation ─────────────────────────────────────────────────────────────
    private float phaseTimer = 0f;
    private float hue        = 0f;

    // ── Scroll ────────────────────────────────────────────────────────────────
    private int scrollOffset = 0;
    private static final int CARD_HEIGHT  = 52;
    private static final int CARD_SPACING = 6;
    private static final int LIST_TOP     = 50;

    // ── Mob Brawl config state (host edits, guest reads via sync) ─────────────
    // Instance fields — initialized from saved statics so config persists across
    // screen recreations when returning to the same room.
    private int brawlHealthMode    = savedBrawlHealthMode;    // 0=Default, 1=Equal 20♥, 2=Double 40♥
    private int brawlAbilitiesMode = savedBrawlAbilitiesMode; // 0=All, 1=No Weapons, 2=No Abilities
    private static int savedBrawlDamageMode = 0;
    private int brawlDamageMode    = savedBrawlDamageMode;    // 0=Morph Default, 1=Equal Damage
    private int brawlTimeLimitIdx  = savedBrawlTimeLimitIdx;  // 0=2min, 1=5min, 2=No Limit
    private int brawlLivesIdx      = savedBrawlLivesIdx;      // 0=1, 1=3, 2=5
    private int brawlArenaType     = savedBrawlArenaType;     // 0=No Arena, 1=Gladiator, 2=Nature, 3=Night, 4=Ocean

    private static final String[] BRAWL_HEALTH_LABELS   = {"Morph Default", "Equal 20♥", "Double 40♥"};
    private static final String[] BRAWL_ABILITY_LABELS  = {"All", "No Weapons"};
    private static final String[] BRAWL_DAMAGE_LABELS   = {"Morph Default", "Equal Damage"};
    private static final String[] BRAWL_TIME_LABELS     = {"2 min", "5 min", "No Limit"};
    private static final int[]    BRAWL_TIME_VALUES     = {120, 300, -1};
    private static final String[] BRAWL_LIVES_LABELS    = {"1 Life", "3 Lives", "5 Lives"};
    private static final int[]    BRAWL_LIVES_VALUES    = {1, 3, 5};
    private static final String[] BRAWL_ARENA_LABELS    = {"None", "⚔ Gladiator", "🌿 Nature", "🌙 Night", "🐬 Ocean"};

    // Config button hover state — 5 rows, up to 5 options each
    private final float[][] brawlOptionHover = new float[6][5];

    // ── Accent colors per game mode ───────────────────────────────────────────
    private static int accentFor(String mode) {
        return switch (mode) {
            case "TRIVIA"     -> 0xFFCC55FF;
            case "ROULETTE"   -> 0xFF5599FF;
            case "RELAY_RACE" -> 0xFF55FFCC;
            case "HIDE_SEEK"  -> 0xFF55FF55;
            case "MOB_BRAWL"  -> 0xFFFF5555;
            case "HUNGER"     -> 0xFFFFAA00;
            default           -> 0xFF888888;
        };
    }

    private static String labelFor(String mode) {
        return switch (mode) {
            case "TRIVIA"     -> "🧠 Morph Trivia";
            case "ROULETTE"   -> "🎲 Morph Roulette";
            case "RELAY_RACE" -> "🏁 Relay Race";
            case "HIDE_SEEK"  -> "🙈 Hide & Seek";
            case "MOB_BRAWL"  -> "⚔ Mob Brawl";
            case "HUNGER"     -> "🏆 Hunger Games";
            default           -> mode;
        };
    }

    public RoomBrowserScreen(MorphGameModeSelect.GameMode gameMode) {
        super(Component.literal("Room Browser"));
        this.gameMode = gameMode;
        this.originalGameMode = gameMode;
    }

    @Override
    protected void init() {
        currentInstance = this;

        if (lastJoinedRoomId != null && lastRoomHost != null) {
            currentRoomId      = lastJoinedRoomId;
            currentRoomHost    = lastRoomHost;
            currentRoomPlayers = lastRoomPlayers;
            currentRoomName    = lastRoomName != null ? lastRoomName : "";
            // Re-entering the SAME room (Play Again, end screen, morph-select cancel etc.)
            // Restore saved config so host and guest both see the last selected options
            brawlHealthMode    = savedBrawlHealthMode;
            brawlAbilitiesMode = savedBrawlAbilitiesMode;
            brawlTimeLimitIdx  = savedBrawlTimeLimitIdx;
            brawlLivesIdx      = savedBrawlLivesIdx;
            brawlArenaType     = savedBrawlArenaType;
            if (lastJoinedGameMode != null) {
                try { this.gameMode = MorphGameModeSelect.GameMode.valueOf(lastJoinedGameMode); } catch (Exception ignored) {}
            }
            // Re-request room status from server to get accurate IN_PROGRESS state
            var mc2 = Minecraft.getInstance();
            if (mc2.player != null)
                ClientPlayNetworking.send(new RoomsNetworking.RoomListRequest(mc2.player.getUUID()));
        } else {
            // No room to restore — fresh browser, reset config to defaults
            brawlHealthMode    = savedBrawlHealthMode    = 1;
            brawlAbilitiesMode = savedBrawlAbilitiesMode = 0;
            brawlTimeLimitIdx  = savedBrawlTimeLimitIdx  = 1;
            brawlLivesIdx      = savedBrawlLivesIdx      = 1;
            brawlArenaType     = savedBrawlArenaType     = 0;
        }

        var mc = Minecraft.getInstance();
        if (mc.player != null)
            ClientPlayNetworking.send(new RoomsNetworking.RoomListRequest(mc.player.getUUID()));

        rebuildRoomWidgets();
    }

    private void rebuildRoomWidgets() {
        clearWidgets();

        if (currentRoomId == null) {
            // ── Room browser buttons ──────────────────────────────────────────
            this.addRenderableWidget(Button.builder(
                    Component.literal("← Back"),
                    _ -> { this.onClose(); Minecraft.getInstance().setScreen(new MorphGameModeSelect(originalGameMode)); }
            ).bounds(10, 8, 60, 20).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("+ Create Room"),
                    _ -> { roomNameInput = ""; maxPlayersInput = gameMode == MorphGameModeSelect.GameMode.MOB_BRAWL ? 2 : 8; showCreateDialog = true; }
            ).bounds(this.width / 2 - 55, this.height - 30, 110, 20).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("↺"),
                    _ -> { var mc = Minecraft.getInstance(); if (mc.player != null) ClientPlayNetworking.send(new RoomsNetworking.RoomListRequest(mc.player.getUUID())); }
            ).bounds(this.width - 36, 8, 26, 20).build());

        } else {
            // ── Room lobby buttons ────────────────────────────────────────────
            var mc = Minecraft.getInstance();
            boolean isHost = mc.player != null && currentRoomHost != null && currentRoomHost.equals(mc.player.getName().getString());

            if (isHost) {
                if (currentRoomPlayers.length >= 2) {
                    this.addRenderableWidget(Button.builder(
                            Component.literal("▶  Start Game"),
                            _ -> startGame()
                    ).bounds(this.width / 2 - 55, this.height - 30, 110, 20).build());
                } else {
                    this.addRenderableWidget(Button.builder(
                            Component.literal("Need 2+ players"),
                            _ -> {}
                    ).bounds(this.width / 2 - 55, this.height - 30, 110, 20).build());
                }
            }

            this.addRenderableWidget(Button.builder(
                    Component.literal("Leave Room"),
                    _ -> {
                        if (mc.player != null && currentRoomId != null)
                            ClientPlayNetworking.send(new RoomsNetworking.RoomLeavePayload(mc.player.getUUID(), currentRoomId));
                        currentRoomId      = null;
                        currentRoomHost    = null;
                        currentRoomPlayers = new String[0];
                        lastJoinedRoomId   = null;
                        lastRoomHost       = null;
                        lastRoomPlayers    = new String[0];
                        lastRoomName       = null;
                        lastJoinedGameMode = null;
                        resetSavedBrawlConfig(); // new room after this should start at defaults
                        gameMode = originalGameMode;
                        rebuildRoomWidgets();
                    }
            ).bounds(this.width / 2 + 60, this.height - 30, 80, 20).build());
        }
    }

    private void startGame() {
        if (roomInProgress) {
            errorMsg   = "Game is already in progress!";
            errorTimer = 3f;
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (gameMode == MorphGameModeSelect.GameMode.MOB_BRAWL) {
            // Send final config then tell server to start morph select for both players
            sendBrawlConfigUpdate();
            ClientPlayNetworking.send(new net.naw.morphling.client.games.MobBrawl.MobBrawlNetworking.MobBrawlReadyPayload(
                    currentRoomId,
                    brawlHealthMode,
                    brawlAbilitiesMode,
                    brawlDamageMode,
                    BRAWL_TIME_VALUES[brawlTimeLimitIdx],
                    BRAWL_LIVES_VALUES[brawlLivesIdx],
                    brawlArenaType
            ));
        } else {
            ClientPlayNetworking.send(new GamesNetworking.TriviaJoinPayload(
                    mc.player.getUUID(), "HOST_START:" + currentRoomId));
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float dt = partialTick * 0.05f;
        phaseTimer += dt;
        hue = (hue + dt * 0.3f) % 1.0f;

        if (errorTimer > 0) errorTimer -= dt;

        int cx     = this.width / 2;
        int accent = accentFor(gameMode.name());

        graphics.fill(0, 0, this.width, this.height, 0xF0050510);

        graphics.centeredText(this.font, Component.literal(labelFor(gameMode.name())), cx, 14, accent);
        graphics.centeredText(this.font,
                Component.literal(currentRoomId == null ? "§7Multiplayer — Room Browser" : "§7Multiplayer — Room Lobby"),
                cx, 26, withAlpha(0x888888, 200));
        graphics.fill(cx - 120, 36, cx + 120, 37, withAlpha(accent & 0x00FFFFFF, 80));

        if (currentRoomId == null) {
            renderRoomList(graphics, mouseX, mouseY);
        } else if (gameMode == MorphGameModeSelect.GameMode.MOB_BRAWL) {
            renderMobBrawlLobby(graphics, mouseX, mouseY);
        } else {
            renderRoomLobby(graphics, mouseX, mouseY);
        }

        if (errorTimer > 0) {
            int alpha = (int)(Math.min(1f, errorTimer) * 220);
            graphics.centeredText(this.font, Component.literal("§c" + errorMsg),
                    cx, this.height - 44, withAlpha(0xFF5555, alpha));
        }

        if (showCreateDialog) renderCreateDialog(graphics, mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    // ── Room list ─────────────────────────────────────────────────────────────

    private void renderRoomList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int cx         = this.width / 2;
        int cardW      = Math.min(400, this.width - 40);
        int cardStartX = cx - cardW / 2;
        int roomCount  = roomData.length / FIELDS_PER_ROOM;

        if (roomCount == 0) {
            float blink = (float)(Math.sin(phaseTimer * 2) * 0.5 + 0.5);
            graphics.centeredText(this.font, Component.literal("§8No rooms open. Create one!"),
                    cx, this.height / 2, withAlpha(0x555555, (int)(blink * 180)));
            return;
        }

        int listBottom = this.height - 45;
        graphics.enableScissor(0, LIST_TOP, this.width, listBottom);

        for (int i = 0; i < roomCount; i++) {
            int    base        = i * FIELDS_PER_ROOM;
            @SuppressWarnings("unused") String roomId = roomData[base];
            String hostName    = roomData[base + 1];
            String mode        = roomData[base + 2];
            int    playerCount = Integer.parseInt(roomData[base + 3]);
            int    maxPlayers  = Integer.parseInt(roomData[base + 4]);
            String status      = roomData[base + 5];

            int cardY = LIST_TOP + i * (CARD_HEIGHT + CARD_SPACING) - scrollOffset;
            if (cardY + CARD_HEIGHT < LIST_TOP || cardY > listBottom) continue;

            boolean full       = playerCount >= maxPlayers;
            boolean inProgress = status.equals("IN_PROGRESS");
            boolean hovered    = mouseX >= cardStartX && mouseX < cardStartX + cardW && mouseY >= cardY && mouseY < cardY + CARD_HEIGHT;
            boolean canJoin    = !full && !inProgress;

            int cardAccent  = accentFor(mode);
            int bgColor     = hovered && canJoin ? 0xFF1E1E2E : 0xFF111120;
            int borderColor = hovered && canJoin ? cardAccent : withAlpha(cardAccent & 0x00FFFFFF, 80);

            graphics.fill(cardStartX, cardY, cardStartX + cardW, cardY + CARD_HEIGHT, bgColor);
            graphics.fill(cardStartX,           cardY,                   cardStartX + cardW, cardY + 1,            borderColor);
            graphics.fill(cardStartX,           cardY + CARD_HEIGHT - 1, cardStartX + cardW, cardY + CARD_HEIGHT,  borderColor);
            graphics.fill(cardStartX,           cardY,                   cardStartX + 1,     cardY + CARD_HEIGHT,  borderColor);
            graphics.fill(cardStartX + cardW-1, cardY,                   cardStartX + cardW, cardY + CARD_HEIGHT,  borderColor);

            graphics.text(this.font, Component.literal("§f" + hostName + "§8's room"), cardStartX + 10, cardY + 8, 0xFFFFFFFF, false);
            graphics.text(this.font, Component.literal("§8" + labelFor(mode)), cardStartX + 10, cardY + 20, withAlpha(cardAccent & 0x00FFFFFF, 160), false);
            graphics.text(this.font, Component.literal(playerCount + "/" + maxPlayers), cardStartX + 10, cardY + 32, full ? 0xFFFF5555 : 0xFF55FF55, false);

            String statusLabel = inProgress ? "§cIn Progress" : full ? "§cFull" : "§aWaiting";
            int statusX = cardStartX + cardW - this.font.width(statusLabel.substring(2)) - 10;
            graphics.text(this.font, Component.literal(statusLabel), statusX, cardY + 8, 0xFFFFFFFF, false);

            if (canJoin && hovered)
                graphics.centeredText(this.font, Component.literal("§7Click to join"), cardStartX + cardW / 2, cardY + 36, withAlpha(0x888888, 180));
        }

        graphics.disableScissor();

        int totalH   = roomCount * (CARD_HEIGHT + CARD_SPACING);
        int visibleH = listBottom - LIST_TOP;
        if (totalH > visibleH)
            graphics.centeredText(this.font, Component.literal("§8↓ scroll for more"), cx, listBottom + 4, withAlpha(0x444444, 160));
    }

    // ── Mob Brawl lobby — split layout with config panel ─────────────────────

    private void renderMobBrawlLobby(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var mc     = Minecraft.getInstance();
        boolean isHost = mc.player != null && currentRoomHost != null && currentRoomHost.equals(mc.player.getName().getString());
        int cx     = this.width / 2;
        int panelW = (this.width - 60) / 2;
        int panelX1 = 20;       // left panel — players + arena preview
        int panelX2 = cx + 10;  // right panel — config
        int panelY  = 44;
        int panelH  = this.height - 80;

        // ── Left panel — Players + Arena preview ──────────────────────────────
        renderBrawlPanel(graphics, panelX1, panelY, panelW, panelH);
        graphics.centeredText(this.font, Component.literal("§7Players"),
                panelX1 + panelW / 2, panelY + 8, withAlpha(0xFF5555, 220));
        graphics.fill(panelX1 + 10, panelY + 18, panelX1 + panelW - 10, panelY + 19,
                withAlpha(0xFF5555, 40));

        // Player list — compact, max 2 players
        int pY = panelY + 26;
        for (String name : currentRoomPlayers) {
            boolean isMe    = mc.player != null && name.equals(mc.player.getName().getString());
            boolean isHostP = name.equals(currentRoomHost);
            String badge    = isHostP ? "§6HOST" : "§7Guest";
            String display  = "⚔ " + name + (isMe ? " §8(you)" : "");
            MorphFaceRenderConfig.renderPlayerFace(graphics, name, panelX1 + 14, pY - 1);
            graphics.text(this.font, Component.literal(display), panelX1 + 26, pY, withAlpha(0xCCCCCC, 220), false);
            graphics.text(this.font, Component.literal(badge),
                    panelX1 + panelW - this.font.width(isHostP ? "HOST" : "Guest") - 8, pY,
                    withAlpha(0xCCCCCC, 220), false);
            graphics.fill(panelX1 + 8, pY + 10, panelX1 + panelW - 8, pY + 11, withAlpha(0x222233, 180));
            pY += 20;
        }

        // Waiting animation if only one player
        if (currentRoomPlayers.length < 2) {
            float dots = (phaseTimer * 2f) % 3f;
            String waiting = "§8Waiting" + ".".repeat((int)dots + 1);
            graphics.text(this.font, Component.literal(waiting), panelX1 + 14, pY + 2, withAlpha(0x333344, 200), false);
        }

        // ── Arena preview image ───────────────────────────────────────────────
        int previewMargin = 8;
        int previewY      = panelY + panelH / 2 - 20;
        int previewW      = (panelW - previewMargin * 2) * 7 / 8;
        int previewH      = previewW * 9 / 16;

        String arenaName = brawlArenaType > 0 ? BRAWL_ARENA_LABELS[brawlArenaType] : "No Arena";
        graphics.centeredText(this.font, Component.literal("§8" + arenaName),
                panelX1 + panelW / 2, previewY - 9, withAlpha(0x555566, 200));

        int previewX = panelX1 + (panelW - previewW) / 2;
        if (brawlArenaType > 0) {
            String[] arenaFileNames = {"", "gladiator", "nature", "night", "ocean"};
            String fileName = arenaFileNames[brawlArenaType];
            net.minecraft.resources.Identifier texture = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    "morphling", "textures/gui/arenas/" + fileName + ".png");
            graphics.fill(previewX - 1, previewY - 1,
                    previewX + previewW + 1, previewY + previewH + 1,
                    withAlpha(0xFF5555, 60));
            graphics.blit(texture,
                    previewX, previewY,
                    previewX + previewW, previewY + previewH,
                    0f, 1f, 0f, 1f);
        } else {
            graphics.fill(previewX, previewY, previewX + previewW, previewY + previewH, withAlpha(0x0A0A14, 180));
            graphics.fill(previewX, previewY, previewX + previewW, previewY + 1, withAlpha(0xFF5555, 20));
            graphics.centeredText(this.font, Component.literal("§8Select an arena"),
                    panelX1 + panelW / 2, previewY + previewH / 2 - 4, withAlpha(0x333344, 180));
        }

        // Room status — bottom of left panel
        String status = currentRoomPlayers.length >= 2 ? "§a● Room Full — Ready!" : "§8● Waiting for opponent...";
        graphics.centeredText(this.font, Component.literal(status),
                panelX1 + panelW / 2, panelY + panelH - 12, withAlpha(0x888888, 200));

        // ── Right panel — Config ──────────────────────────────────────────────
        renderBrawlPanel(graphics, panelX2, panelY, panelW, panelH);
        graphics.centeredText(this.font,
                Component.literal(isHost ? "§7Config §8(Host)" : "§7Config §8(Read Only)"),
                panelX2 + panelW / 2, panelY + 8, withAlpha(0xFF5555, 220));
        graphics.fill(panelX2 + 10, panelY + 18, panelX2 + panelW - 10, panelY + 19,
                withAlpha(0xFF5555, 40));

        int rowY   = panelY + 26;
        int rowGap = (int)((panelH - 40) / 5.3f);

        renderBrawlConfigRow(graphics, mouseX, mouseY, isHost, 0, panelX2 + 4, rowY,              panelW - 8, "Health",    BRAWL_HEALTH_LABELS,  brawlHealthMode);
        renderBrawlConfigRow(graphics, mouseX, mouseY, isHost, 1, panelX2 + 4, rowY + rowGap,     panelW - 8, "Abilities", BRAWL_ABILITY_LABELS, brawlAbilitiesMode);
        renderBrawlConfigRow(graphics, mouseX, mouseY, isHost, 2, panelX2 + 4, rowY + rowGap * 2, panelW - 8, "Damage",    BRAWL_DAMAGE_LABELS,  brawlDamageMode);
        renderBrawlConfigRow(graphics, mouseX, mouseY, isHost, 3, panelX2 + 4, rowY + rowGap * 3, panelW - 8, "Time",      BRAWL_TIME_LABELS,    brawlTimeLimitIdx);
        renderBrawlConfigRow(graphics, mouseX, mouseY, isHost, 4, panelX2 + 4, rowY + rowGap * 4, panelW - 8, "Lives",     BRAWL_LIVES_LABELS,   brawlLivesIdx);
        renderBrawlConfigRow(graphics, mouseX, mouseY, isHost, 5, panelX2 + 4, rowY + rowGap * 5, panelW - 8, "Arena",     BRAWL_ARENA_LABELS,   brawlArenaType);

        // Start hint
        if (isHost) {
            float blink = (float)(Math.sin(phaseTimer * 3) * 0.5 + 0.5);
            String hint = currentRoomPlayers.length >= 2 ? "§7Press Start when ready!" : "§8Waiting for opponent...";
            graphics.centeredText(this.font, Component.literal(hint), cx, this.height - 42,
                    withAlpha(0x555566, (int)(blink * 180)));
        } else {
            graphics.centeredText(this.font, Component.literal("§8Waiting for host to start..."),
                    cx, this.height - 42, withAlpha(0x444455, 200));
        }
    }

    /** Dark panel with red border — Mob Brawl theme */
    private void renderBrawlPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, withAlpha(0x0A0A18, 220));
        graphics.fill(x,       y,       x + w, y + 1,   withAlpha(0xFF5555, 80));
        graphics.fill(x,       y + h-1, x + w, y + h,   withAlpha(0xFF5555, 40));
        graphics.fill(x,       y,       x + 1, y + h,   withAlpha(0xFF5555, 60));
        graphics.fill(x + w-1, y,       x + w, y + h,   withAlpha(0xFF5555, 60));
    }

    /**
     * Renders one config row with clickable option buttons.
     * Host: clickable. Guest: read-only (dimmed, no hover).
     */
    private void renderBrawlConfigRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                      boolean isHost, int rowIdx, int x, int y, int w,
                                      String label, String[] options, int selected) {
        graphics.text(this.font, Component.literal("§8" + label + ":"), x + 2, y, withAlpha(0x666677, 220), false);

        int optionAreaW = w - this.font.width(label + ":") - 8;
        int btnW        = (optionAreaW - (options.length - 1) * 3) / options.length;
        int btnH        = 14;
        int btnStartX   = x + w - optionAreaW + 2;

        for (int i = 0; i < options.length; i++) {
            int btnX = btnStartX + i * (btnW + 3);
            int btnY = y - 1;

            boolean isSelected = i == selected;
            boolean hovered    = isHost && mouseX >= btnX && mouseX < btnX + btnW
                    && mouseY >= btnY && mouseY < btnY + btnH;

            brawlOptionHover[rowIdx][i] += ((hovered ? 1f : 0f) - brawlOptionHover[rowIdx][i]) * 0.2f;
            float h = brawlOptionHover[rowIdx][i];

            int bg        = isSelected ? withAlpha(0xFF5555, 50) : withAlpha(0x111122, (int)(h * 30 + 10));
            int border    = isSelected ? withAlpha(0xFF5555, isHost ? 180 : 80) : withAlpha(0xFF5555, (int)(h * 60 + 10));
            int textColor = isSelected ? withAlpha(0xFF7777, 220) : withAlpha(0x888888, (int)((h * 60 + 80) * (isHost ? 1f : 0.5f)));

            graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, bg);
            graphics.fill(btnX, btnY,            btnX + btnW, btnY + 1,       border);
            graphics.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH,    border);
            graphics.fill(btnX, btnY,            btnX + 1,    btnY + btnH,    border);
            graphics.fill(btnX + btnW - 1, btnY, btnX + btnW, btnY + btnH,    border);

            String lbl = options[i];
            while (this.font.width(lbl) > btnW - 4 && lbl.length() > 2)
                lbl = lbl.substring(0, lbl.length() - 1);
            graphics.centeredText(this.font, Component.literal(lbl), btnX + btnW / 2, btnY + 3, textColor);
        }

        // Lock icon for guest
        if (!isHost) {
            graphics.text(this.font, Component.literal("§8🔒"), x + w - 10, y, withAlpha(0x333344, 220), false);
        }
    }

    // ── Room lobby (non-Mob-Brawl) ────────────────────────────────────────────

    private void renderRoomLobby(GuiGraphicsExtractor graphics, int ignoredMouseX, int ignoredMouseY) {
        int cx     = this.width / 2;
        int accent = accentFor(gameMode.name());
        var mc     = Minecraft.getInstance();
        boolean isHost = mc.player != null && currentRoomHost != null && currentRoomHost.equals(mc.player.getName().getString());

        graphics.centeredText(this.font, Component.literal("§7" + currentRoomName), cx, 44, withAlpha(0x666666, 180));

        int boxW = 200, boxX = cx - boxW / 2, boxY = 60;
        graphics.fill(boxX, boxY, boxX + boxW, boxY + 2, withAlpha(accent & 0x00FFFFFF, 80));
        graphics.centeredText(this.font, Component.literal("§7Players in room:"), cx, boxY + 8, 0xFF666666);

        int pY = boxY + 22;
        for (String name : currentRoomPlayers) {
            boolean isMe = mc.player != null && name.equals(mc.player.getName().getString());
            boolean host = name.equals(currentRoomHost);
            String prefix = host ? "§6[Host] " : "§7";
            String fullText = prefix + name + (isMe ? " §8(you)" : "");
            int textWidth = this.font.width(fullText.replaceAll("§.", ""));
            int textStartX = cx + 4 - textWidth / 2;
            MorphFaceRenderConfig.renderPlayerFace(graphics, name, textStartX - 10, pY - 1);
            graphics.centeredText(this.font, Component.literal(fullText), cx + 4, pY, 0xFFFFFFFF);
            pY += 12;
        }

        if (!isHost && !roomInProgress) {
            float blink = (float)(Math.sin(phaseTimer * 3) * 0.5 + 0.5);
            graphics.centeredText(this.font, Component.literal("§8Waiting for host to start..."),
                    cx, this.height / 2 + 20, withAlpha(0x555555, (int)(blink * 180)));
        }

        // Game info — bottom left
        graphics.text(this.font, Component.literal("§8📋 10 rounds"),       10, this.height - 66, withAlpha(0x333333, 200), false);
        graphics.text(this.font, Component.literal("§8•"),                  25, this.height - 55, withAlpha(0x333333, 200), false);
        graphics.text(this.font, Component.literal("§8🔥 streaks = bonus"), 10, this.height - 44, withAlpha(0x333333, 200), false);
        graphics.text(this.font, Component.literal("§8•"),                  25, this.height - 33, withAlpha(0x333333, 200), false);
        graphics.text(this.font, Component.literal("§8⏱ 20s each"),         10, this.height - 22, withAlpha(0x333333, 200), false);
    }

    // ── Create Room dialog ────────────────────────────────────────────────────

    private void renderCreateDialog(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, this.height, 0xAA000000);

        int cx = this.width / 2;
        int w  = 220, h = 110;
        int x  = cx - w / 2, y = this.height / 2 - h / 2;

        int dialogAccent = accentFor(gameMode.name());
        graphics.fill(x, y, x + w, y + h, 0xFF111122);
        graphics.fill(x,       y,       x + w, y + 1,   dialogAccent);
        graphics.fill(x,       y + h-1, x + w, y + h,   dialogAccent);
        graphics.fill(x,       y,       x + 1, y + h,   dialogAccent);
        graphics.fill(x + w-1, y,       x + w, y + h,   dialogAccent);

        graphics.centeredText(this.font, Component.literal("Create Room"), cx, y + 8, 0xFFFFFFFF);

        graphics.text(this.font, Component.literal("§7Name:"), x + 10, y + 26, 0xFF888888, false);
        String displayName = roomNameInput.isEmpty() ? "§8type a name..." : "§f" + roomNameInput;
        boolean showCursor = (int)(phaseTimer * 2) % 2 == 0;
        graphics.text(this.font, Component.literal(displayName + (showCursor ? "§7|" : " ")), x + 50, y + 26, 0xFFFFFFFF, false);

        graphics.text(this.font, Component.literal("§7Max:"), x + 10, y + 42, 0xFF888888, false);
        graphics.text(this.font, Component.literal("§f" + maxPlayersInput + " players"), x + 50, y + 42, 0xFFFFFFFF, false);

        if (gameMode != MorphGameModeSelect.GameMode.MOB_BRAWL) {
            boolean minusHov = mouseX >= x + 140 && mouseX < x + 154 && mouseY >= y + 40 && mouseY < y + 50;
            graphics.fill(x + 140, y + 40, x + 154, y + 50, minusHov ? 0xFF334466 : 0xFF222233);
            graphics.centeredText(this.font, Component.literal("§7-"), x + 147, y + 42, 0xFFFFFFFF);

            boolean plusHov = mouseX >= x + 158 && mouseX < x + 172 && mouseY >= y + 40 && mouseY < y + 50;
            graphics.fill(x + 158, y + 40, x + 172, y + 50, plusHov ? 0xFF334466 : 0xFF222233);
            graphics.centeredText(this.font, Component.literal("§7+"), x + 165, y + 42, 0xFFFFFFFF);
        }

        int btnY = y + h - 24;
        boolean createHov = mouseX >= x + 10  && mouseX < x + 100 && mouseY >= btnY && mouseY < btnY + 16;
        boolean cancelHov = mouseX >= x + 110 && mouseX < x + 210 && mouseY >= btnY && mouseY < btnY + 16;
        graphics.fill(x + 10,  btnY, x + 100, btnY + 16, createHov ? 0xFF1A3D1A : 0xFF111122);
        graphics.fill(x + 110, btnY, x + 210, btnY + 16, cancelHov ? 0xFF3D1A1A : 0xFF111122);
        graphics.fill(x + 10,  btnY, x + 100, btnY + 1,  0xFF55FF55);
        graphics.fill(x + 110, btnY, x + 210, btnY + 1,  0xFFFF5555);
        graphics.centeredText(this.font, Component.literal("§aCreate"), x + 55,  btnY + 4, 0xFF55FF55);
        graphics.centeredText(this.font, Component.literal("§cCancel"), x + 160, btnY + 4, 0xFFFF5555);
    }

    // ── Mob Brawl config helpers ──────────────────────────────────────────────

    /** Host sends updated config to server — also writes through to saved statics */
    private void sendBrawlConfigUpdate() {
        ClientPlayNetworking.send(new net.naw.morphling.client.games.MobBrawl.MobBrawlNetworking.MobBrawlConfigPayload(
                currentRoomId,
                brawlHealthMode,
                brawlAbilitiesMode,
                brawlDamageMode,
                BRAWL_TIME_VALUES[brawlTimeLimitIdx],
                BRAWL_LIVES_VALUES[brawlLivesIdx],
                brawlArenaType
        ));
    }

    /** Called when config sync arrives from server — updates guest's view and writes through to saved statics */
    public static void onBrawlConfigSync(int healthMode, int abilitiesMode, int damageMode, int timeLimitSeconds, int lives, int arenaType) {
        if (currentInstance == null) return;
        currentInstance.brawlHealthMode    = savedBrawlHealthMode    = healthMode;
        currentInstance.brawlAbilitiesMode = savedBrawlAbilitiesMode = abilitiesMode;
        currentInstance.brawlDamageMode    = savedBrawlDamageMode    = damageMode;
        for (int i = 0; i < BRAWL_TIME_VALUES.length; i++) {
            if (BRAWL_TIME_VALUES[i] == timeLimitSeconds) { currentInstance.brawlTimeLimitIdx = savedBrawlTimeLimitIdx = i; break; }
        }
        for (int i = 0; i < BRAWL_LIVES_VALUES.length; i++) {
            if (BRAWL_LIVES_VALUES[i] == lives) { currentInstance.brawlLivesIdx = savedBrawlLivesIdx = i; break; }
        }
        currentInstance.brawlArenaType = savedBrawlArenaType = arenaType;
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentRoomId == null) {
            int roomCount = roomData.length / FIELDS_PER_ROOM;
            int totalH    = roomCount * (CARD_HEIGHT + CARD_SPACING);
            int visibleH  = this.height - 40 - LIST_TOP;
            int maxScroll = Math.max(0, totalH - visibleH);
            scrollOffset  = (int) Math.clamp(scrollOffset - scrollY * 20, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.@NonNull MouseButtonEvent event, boolean doubleClick) {
        // Create Room dialog intercepts all clicks while open
        if (showCreateDialog) {
            int cx = this.width / 2;
            int w  = 220, h = 110;
            int x  = cx - w / 2, y = this.height / 2 - h / 2;
            int btnY = y + h - 24;

            if (gameMode != MorphGameModeSelect.GameMode.MOB_BRAWL) {
                if (event.x() >= x + 140 && event.x() < x + 154 && event.y() >= y + 40 && event.y() < y + 50) { maxPlayersInput = Math.max(2,  maxPlayersInput - 1); return true; }
                if (event.x() >= x + 158 && event.x() < x + 172 && event.y() >= y + 40 && event.y() < y + 50) { maxPlayersInput = Math.min(16, maxPlayersInput + 1); return true; }
            }
            if (event.x() >= x + 10 && event.x() < x + 100 && event.y() >= btnY && event.y() < btnY + 16) {
                showCreateDialog = false;
                this.setFocused(null);
                var mc = Minecraft.getInstance();
                if (mc.player != null) {
                    String name = roomNameInput.isEmpty() ? mc.player.getName().getString() + "'s Room" : roomNameInput;
                    ClientPlayNetworking.send(new RoomsNetworking.RoomCreatePayload(
                            mc.player.getUUID(), mc.player.getName().getString(), name, gameMode.name(), maxPlayersInput));
                }
                return true;
            }
            if (event.x() >= x + 110 && event.x() < x + 210 && event.y() >= btnY && event.y() < btnY + 16) { showCreateDialog = false; return true; }
            return true;
        }

        // Mob Brawl config clicks — host only
        if (currentRoomId != null && gameMode == MorphGameModeSelect.GameMode.MOB_BRAWL) {
            var mc = Minecraft.getInstance();
            boolean isHost = mc.player != null && currentRoomHost != null && currentRoomHost.equals(mc.player.getName().getString());
            if (isHost) {
                int cx      = this.width / 2;
                int panelX2 = cx + 10;
                int panelY  = 44;
                int panelW  = (this.width - 60) / 2;
                int panelH  = this.height - 80;
                int rowY    = panelY + 26;
                int rowGap = (int)((panelH - 40) / 5.3f);

                String[][] allOptions = {BRAWL_HEALTH_LABELS, BRAWL_ABILITY_LABELS, BRAWL_DAMAGE_LABELS, BRAWL_TIME_LABELS, BRAWL_LIVES_LABELS, BRAWL_ARENA_LABELS};

                for (int rowIdx = 0; rowIdx < 6; rowIdx++) {
                    String[] options = allOptions[rowIdx];
                    int y = rowY + rowGap * rowIdx;
                    int x = panelX2 + 4;
                    int w = panelW - 8;

                    int labelW      = this.font.width(new String[]{"Health:", "Abilities:", "Damage:", "Time:", "Lives:", "Arena:"}[rowIdx]);
                    int optionAreaW = w - labelW - 8;
                    int btnW        = (optionAreaW - (options.length - 1) * 3) / options.length;
                    int btnH        = 14;
                    int btnStartX   = x + w - optionAreaW + 2;

                    for (int i = 0; i < options.length; i++) {
                        int btnX = btnStartX + i * (btnW + 3);
                        int btnY = y - 1;

                        if (event.x() >= btnX && event.x() < btnX + btnW
                                && event.y() >= btnY && event.y() < btnY + btnH) {
                            // Write through to statics so config persists on next screen recreation
                            switch (rowIdx) {
                                case 0 -> brawlHealthMode    = savedBrawlHealthMode    = i;
                                case 1 -> brawlAbilitiesMode = savedBrawlAbilitiesMode = i;
                                case 2 -> brawlDamageMode    = savedBrawlDamageMode    = i;
                                case 3 -> brawlTimeLimitIdx  = savedBrawlTimeLimitIdx  = i;
                                case 4 -> brawlLivesIdx      = savedBrawlLivesIdx      = i;
                                case 5 -> brawlArenaType     = savedBrawlArenaType     = i;
                            }
                            var mcS = Minecraft.getInstance();
                            if (mcS.level != null && mcS.player != null) {
                                mcS.level.playLocalSound(mcS.player.getX(), mcS.player.getY(), mcS.player.getZ(),
                                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
                                        net.minecraft.sounds.SoundSource.PLAYERS, 0.3f, 1.3f, false);
                            }
                            sendBrawlConfigUpdate();
                            return true;
                        }
                    }
                }
            }
        }

        // Room card clicks when browsing
        if (currentRoomId == null) {
            int cardW      = Math.min(400, this.width - 40);
            int cardStartX = this.width / 2 - cardW / 2;
            int roomCount  = roomData.length / FIELDS_PER_ROOM;
            int listBottom = this.height - 40;

            for (int i = 0; i < roomCount; i++) {
                int cardY = LIST_TOP + i * (CARD_HEIGHT + CARD_SPACING) - scrollOffset;
                if (cardY + CARD_HEIGHT < LIST_TOP || cardY > listBottom) continue;

                if (event.y() < this.height - 35 && event.x() >= cardStartX && event.x() < cardStartX + cardW && event.y() >= cardY && event.y() < cardY + CARD_HEIGHT) {
                    int    base        = i * FIELDS_PER_ROOM;
                    String roomId      = roomData[base];
                    int    playerCount = Integer.parseInt(roomData[base + 3]);
                    int    maxPlayers  = Integer.parseInt(roomData[base + 4]);
                    String status      = roomData[base + 5];

                    if (!status.equals("IN_PROGRESS") && playerCount < maxPlayers) {
                        var mc = Minecraft.getInstance();
                        if (mc.player != null)
                            ClientPlayNetworking.send(new RoomsNetworking.RoomJoinPayload(mc.player.getUUID(), mc.player.getName().getString(), roomId));
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.@NonNull KeyEvent event) {
        if (showCreateDialog) {
            if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && !roomNameInput.isEmpty()) {
                roomNameInput = roomNameInput.substring(0, roomNameInput.length() - 1);
                return true;
            }
            if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { showCreateDialog = false; return true; }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.@NonNull CharacterEvent event) {
        if (showCreateDialog && roomNameInput.length() < 20) {
            roomNameInput += event.codepointAsString();
            return true;
        }
        return super.charTyped(event);
    }

    // ── Packet handlers (called by RoomsNetworking) ───────────────────────────

    /** Server sent updated room list */
    public static void onRoomList(String[] data) {
        if (currentInstance == null) return;
        currentInstance.roomData = data;
        if (currentInstance.currentRoomId != null) {
            for (int i = 0; i + 5 < data.length; i += FIELDS_PER_ROOM) {
                if (data[i].equals(currentInstance.currentRoomId)) {
                    roomInProgress = data[i + 5].equals("IN_PROGRESS");
                    currentInstance.rebuildRoomWidgets();
                    break;
                }
            }
        }
    }

    /** Server confirmed we joined a room */
    public static void onRoomJoined(String roomId, String roomName, String hostName, String joinedGameMode, String[] playerNames) {
        if (currentInstance == null) return;
        try {
            currentInstance.gameMode = MorphGameModeSelect.GameMode.valueOf(joinedGameMode);
        } catch (Exception ignored) {}
        currentInstance.currentRoomId      = roomId;
        currentInstance.currentRoomName    = roomName;
        currentInstance.currentRoomHost    = hostName;
        currentInstance.currentRoomPlayers = playerNames;
        lastRoomName       = roomName;
        lastJoinedRoomId   = roomId;
        lastJoinedGameMode = joinedGameMode;
        lastRoomHost       = hostName;
        lastRoomPlayers    = playerNames;
        currentInstance.rebuildRoomWidgets();

        // Set opponent name for Mob Brawl HUD
        var mc2 = Minecraft.getInstance();
        if (mc2.player != null) {
            for (String name : playerNames) {
                if (!name.equals(mc2.player.getName().getString())) {
                    net.naw.morphling.client.games.MobBrawl.MobBrawlClient.setOpponentName(name);
                    break;
                }
            }
        }
    }

    /** Server sent an error — shown briefly at bottom of screen */
    public static void onRoomError(String message) {
        if (currentInstance == null) return;
        currentInstance.errorMsg   = message;
        currentInstance.errorTimer = 3f;
    }

    /** Server sent updated player list for current room */
    public static void onRoomUpdate(String roomId, String hostName, String[] playerNames) {
        if (currentInstance == null) return;
        lastRoomPlayers = playerNames;
        lastRoomHost    = hostName;
        if (roomId.equals(currentInstance.currentRoomId)) {
            currentInstance.currentRoomPlayers = playerNames;
            currentInstance.currentRoomHost    = hostName;
            currentInstance.rebuildRoomWidgets();
            // If we're the host, resend config so new joiners see current settings.
            // Since init() already restored the correct config into the instance before
            // any packet can arrive (currentInstance is null before init), this now
            // pushes the correct saved config to the guest instead of defaults.
            var mc2 = Minecraft.getInstance();
            if (mc2.player != null && mc2.player.getName().getString().equals(hostName)) {
                currentInstance.sendBrawlConfigUpdate();
            }
        }

        // Set opponent name for Mob Brawl HUD
        var mc2 = Minecraft.getInstance();
        if (mc2.player != null) {
            for (String name : playerNames) {
                if (!name.equals(mc2.player.getName().getString())) {
                    net.naw.morphling.client.games.MobBrawl.MobBrawlClient.setOpponentName(name);
                    break;
                }
            }
        }
    }

    @Override
    public void onClose() { currentInstance = null; super.onClose(); }

    @Override
    public boolean isPauseScreen() { return false; }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }
}