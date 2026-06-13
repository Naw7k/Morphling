package net.naw.morphling.client.games.MobBrawl;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.client.games.ui.MorphGameModeSelect;
import net.naw.morphling.client.games.ui.RoomBrowserScreen;

import java.util.UUID;

/**
 * Client-side state manager for Mob Brawl.

 * Holds all game state the client needs to render the HUD, screens, and
 * respond to server events. Updated by MobBrawlNetworking packet receivers.

 * State is static — only one brawl game can be active per client at a time.

 * Key responsibilities:
 *   - Receive and store server state updates
 *   - Open/close screens on phase transitions
 *   - Play sounds for key events (countdown, life lost, win/lose)
 *   - Expose state to HUD and screens via getters
 */
public class MobBrawlClient {

    // ── Active game state ─────────────────────────────────────────────────────
    private static String  activeRoomId = null;
    private static boolean isHost       = false;
    private static boolean isActive     = false; // true when in FIGHTING phase

    // ── Config (synced from server) ───────────────────────────────────────────
    private static int     healthMode    = 1;
    private static int     abilitiesMode = 0;
    private static int     timeLimit     = 300;
    private static int     lives         = 3;
    private static boolean arenaMode     = false;

    // ── Game state (synced from server) ──────────────────────────────────────
    private static int   phase          = 0; // MobBrawlServerGame.Phase ordinal
    private static float timer          = 0f;
    private static int   myLives        = 3;
    private static int   opponentLives  = 3;
    private static float myHealth       = 20f;
    private static float opponentHealth = 20f;
    private static float myMaxHealth    = 20f;
    private static float opponentMaxHealth = 20f;
    private static int   countdownValue = 3;

    // ── Morph info ────────────────────────────────────────────────────────────
    private static EntityType<?> myMorph       = null;
    private static EntityType<?> opponentMorph = null;

    // ── Opponent info ─────────────────────────────────────────────────────────
    private static String myName       = "You";
    private static String opponentName = "Opponent";

    // ── Opponent mouse (end screen animation) ─────────────────────────────────
    private static float opponentMouseX       = 0f; // raw target from packets
    private static float opponentMouseY       = 0f;
    private static float opponentMouseXSmooth = 0f; // smoothed value used for rendering
    private static float opponentMouseYSmooth = 0f;
    private static boolean opponentMouseInit  = false;
    private static int   mouseTickTimer = 0;

    // ── Death detection ───────────────────────────────────────────────────────
    private static boolean deathReported = false;

    // ── Input freeze during countdown ─────────────────────────────────────────
    private static net.minecraft.client.player.ClientInput savedInput = null;

    // ── End screen state ──────────────────────────────────────────────────────
    private static boolean didWin        = false;
    private static float   myDamageDealt = 0f;
    private static float   oppDamageDealt = 0f;
    private static int     myLivesLeft   = 0;
    private static int     oppLivesLeft  = 0;

    // ── Animation state ───────────────────────────────────────────────────────
    private static float lifeFlashTimer = 0f; // flashes when a life is lost
    private static float countdownFlash = 0f; // flashes on countdown number change
    private static float localTimer     = 0f;

    // ── Packet receivers (called by MobBrawlNetworking) ──────────────────────

    /** Config sync from server — host updated config */
    public static void onConfigSync(MobBrawlNetworking.MobBrawlConfigSyncPayload payload) {
        if (activeRoomId != null && !payload.roomId().equals(activeRoomId)) return;
        healthMode    = payload.healthMode();
        abilitiesMode = payload.abilitiesMode();
        timeLimit     = payload.timeLimit();
        lives         = payload.lives();
        arenaMode     = payload.arenaType() > 0;
        RoomBrowserScreen.onBrawlConfigSync(payload.healthMode(), payload.abilitiesMode(),
                payload.damageMode(), payload.timeLimit(), payload.lives(), payload.arenaType());
    }

    /** Full state sync from server */
    public static void onStateSync(MobBrawlNetworking.MobBrawlStatePayload payload) {
        if (!payload.roomId().equals(activeRoomId)) return;
        phase      = payload.phase();
        timer      = payload.timer();
        localTimer = payload.timer();

        // Assign health/lives correctly based on whether we're host or guest
        if (isHost) {
            myHealth          = payload.hostHealth();
            opponentHealth    = payload.guestHealth();
            myMaxHealth       = payload.hostMaxHealth();
            opponentMaxHealth = payload.guestMaxHealth();
            myLives           = payload.hostLives();
            opponentLives     = payload.guestLives();
            myMorph           = resolveType(payload.hostMorphId());
            opponentMorph     = resolveType(payload.guestMorphId());
        } else {
            myHealth          = payload.guestHealth();
            opponentHealth    = payload.hostHealth();
            myMaxHealth       = payload.guestMaxHealth();
            opponentMaxHealth = payload.hostMaxHealth();
            myLives           = payload.guestLives();
            opponentLives     = payload.hostLives();
            myMorph           = resolveType(payload.guestMorphId());
            opponentMorph     = resolveType(payload.hostMorphId());
        }
    }

    /** Server tells client to open a screen (morph select or fight) */
    public static void onStart(MobBrawlNetworking.MobBrawlStartPayload payload) {
        // phase 0 = cancel morph select, go back to room browser
        if (payload.phase() == 0) {
            clearSession();
            Minecraft.getInstance().setScreen(new RoomBrowserScreen(MorphGameModeSelect.GameMode.MOB_BRAWL));
            return;
        }

        // Initialize session if not already done
        if (activeRoomId == null || !activeRoomId.equals(payload.roomId())) {
            initSession(payload.roomId(), payload.isHost());
        }

        if (!payload.roomId().equals(activeRoomId)) return;
        isHost = payload.isHost();

        if (payload.phase() == 1) {
            // Open morph select screen
            Minecraft.getInstance().setScreen(new MobBrawlMorphSelectScreen(activeRoomId, isHost));
        } else if (payload.phase() == 2) {
            // Fight started — show HUD
            isActive = true;
            MobBrawlHud.register();
            Minecraft.getInstance().setScreen(null);
            if (Minecraft.getInstance().player instanceof net.minecraft.client.player.LocalPlayer lp) {
                lp.setShowDeathScreen(false);
            }
        }
    }

    /** Game ended — open end screen */
    public static void onEnd(MobBrawlNetworking.MobBrawlEndPayload payload) {
        if (activeRoomId == null) initSession(payload.roomId(), payload.isHost());
        if (!payload.roomId().equals(activeRoomId)) return;

        isActive = false;

        // Determine if we won
        try {
            UUID winnerUUID = UUID.fromString(payload.winnerUUID());
            UUID myUUID = Minecraft.getInstance().player != null
                    ? Minecraft.getInstance().player.getUUID() : null;
            didWin = winnerUUID.equals(myUUID);
        } catch (Exception e) {
            didWin = false;
        }

        // Store final stats
        myDamageDealt  = isHost ? payload.hostDamage()    : payload.guestDamage();
        oppDamageDealt = isHost ? payload.guestDamage()   : payload.hostDamage();
        myLivesLeft    = isHost ? payload.hostLivesLeft() : payload.guestLivesLeft();
        oppLivesLeft   = isHost ? payload.guestLivesLeft(): payload.hostLivesLeft();
        myName         = isHost ? payload.hostName()      : payload.guestName();
        opponentName   = isHost ? payload.guestName()     : payload.hostName();

        if (Minecraft.getInstance().player instanceof net.minecraft.client.player.LocalPlayer lp) {
            lp.setShowDeathScreen(false);
        }

        Minecraft.getInstance().setScreen(new MobBrawlEndScreen(
                didWin, myMorph, opponentMorph,
                myDamageDealt, oppDamageDealt,
                myLivesLeft, oppLivesLeft,
                activeRoomId, myName, opponentName
        ));
    }

    /** Real-time health update */
    public static void onHealthUpdate(MobBrawlNetworking.MobBrawlHealthPayload payload) {
        if (!payload.roomId().equals(activeRoomId)) return;

        float prevOpponentHealth = opponentHealth;

        if (isHost) {
            myHealth          = payload.hostHealth();
            opponentHealth    = payload.guestHealth();
            myMaxHealth       = payload.hostMaxHealth();
            opponentMaxHealth = payload.guestMaxHealth();
            myLives           = payload.hostLives();
            opponentLives     = payload.guestLives();
        } else {
            myHealth          = payload.guestHealth();
            opponentHealth    = payload.hostHealth();
            myMaxHealth       = payload.guestMaxHealth();
            opponentMaxHealth = payload.hostMaxHealth();
            myLives           = payload.guestLives();
            opponentLives     = payload.hostLives();
        }

        // Flash if a life was lost (health jumped up = life reset)
        if (opponentHealth > prevOpponentHealth + 5f || myHealth > (opponentMaxHealth * 0.5f)) {
            lifeFlashTimer = 1.0f;
            playLifeLostSound();
        }
    }

    /** Opponent mouse position relayed from server — used on end screen */
    public static void onMouseSync(MobBrawlNetworking.MobBrawlMouseSyncPayload payload) {
        if (!payload.roomId().equals(activeRoomId)) return;
        opponentMouseX = payload.mouseX();
        opponentMouseY = payload.mouseY();
        // Snap smooth to target on first receive to avoid sliding in from (0,0)
        if (!opponentMouseInit) {
            opponentMouseInit  = true;
            opponentMouseXSmooth = opponentMouseX;
            opponentMouseYSmooth = opponentMouseY;
        }
    }

    /** Server told us we died (and will respawn) — open the 3s death screen. */
    public static void onDeathScreen(MobBrawlNetworking.MobBrawlDeathScreenPayload payload) {
        if (!isActive) return;
        myLives = payload.livesLeft();
        Minecraft.getInstance().setScreen(new MobBrawlDeathScreen(myMorph, payload.livesLeft()));
    }

    /** Countdown tick from server */
    public static void onCountdown(MobBrawlNetworking.MobBrawlCountdownPayload payload) {

        if (!payload.roomId().equals(activeRoomId)) return;
        countdownValue = payload.count();
        countdownFlash = 1.0f;
        MobBrawlHud.register();
        Minecraft.getInstance().setScreen(null);

        // Freeze/unfreeze player input during countdown
        if (Minecraft.getInstance().player != null) {
            if (payload.count() > 0) {
                if (savedInput == null) savedInput = Minecraft.getInstance().player.input;
                Minecraft.getInstance().player.input = new net.minecraft.client.player.ClientInput();
            } else {
                // GO! — restore input
                if (savedInput != null) {
                    Minecraft.getInstance().player.input = savedInput;
                    savedInput = null;
                }
            }
        }

        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (payload.count() == 0) {
            // GO! — exciting sound
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.8f, 2.0f, false);
        } else {
            // 3..2..1 — ticking sound, gets higher pitched
            float pitch = 0.8f + (3 - payload.count()) * 0.2f;
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.6f, pitch, false);
        }
    }

    // ── Damage sending ────────────────────────────────────────────────────────

    /**
     * Called from PlayerAttackMixin when damage is dealt during a brawl.
     * Sends damage to server for authoritative health tracking.
     */
    @SuppressWarnings("unused")
    public static void sendDamage(float damage) {
        if (!isActive || activeRoomId == null) return;
        ClientPlayNetworking.send(new MobBrawlNetworking.MobBrawlDamagePayload(activeRoomId, damage));
    }

    // ── Session management ────────────────────────────────────────────────────

    /** Initializes client state for a new session */
    public static void initSession(String roomId, boolean asHost) {
        activeRoomId   = roomId;
        isHost         = asHost;
        isActive       = false;
        phase          = 0;
        myLives        = lives;
        opponentLives  = lives;
        myHealth       = myMaxHealth;
        opponentHealth = opponentMaxHealth;
        lifeFlashTimer = 0f;
        countdownFlash = 0f;
        myMorph        = null;
        opponentMorph  = null;
    }

    /** Clears all state when leaving a brawl room */
    public static void clearSession() {
        activeRoomId  = null;
        isActive      = false;
        myMorph       = null;
        opponentMorph = null;
        // Restore death screen for normal gameplay
        var mc = Minecraft.getInstance();
        if (mc.player instanceof net.minecraft.client.player.LocalPlayer lp) {
            lp.setShowDeathScreen(true);
        }
    }

    // ── Tick (called from MorphlingClient.END_CLIENT_TICK) ───────────────────

    public static void tick(float dt) {
        Minecraft mc = Minecraft.getInstance();

        // Detect local player death and notify server
        if (isActive && mc.player != null) {
            if (mc.player.isDeadOrDying() && !deathReported && activeRoomId != null) {
                deathReported = true;
                ClientPlayNetworking.send(new MobBrawlNetworking.MobBrawlPlayerDiedPayload(activeRoomId));
            }
            if (deathReported && mc.player.getHealth() >= mc.player.getMaxHealth() * 0.5f) {
                deathReported = false;
            }
        }

        // Send mouse position to server every 3 ticks when on the end screen
        if (!isActive && activeRoomId != null && mc.screen instanceof MobBrawlEndScreen) {
            mouseTickTimer++;
            if (mouseTickTimer >= 3) {
                mouseTickTimer = 0;
                double mx = mc.mouseHandler.xpos();
                double my = mc.mouseHandler.ypos();
                ClientPlayNetworking.send(new MobBrawlNetworking.MobBrawlMousePayload(
                        activeRoomId, (float) mx, (float) my));
            }
        }

        if (lifeFlashTimer > 0) lifeFlashTimer = Math.max(0f, lifeFlashTimer - dt);
        if (countdownFlash > 0) countdownFlash = Math.max(0f, countdownFlash - dt);
        if (isActive && localTimer > 0) localTimer -= dt;
    }

    // ── Morph select helpers ──────────────────────────────────────────────────

    /** Sends morph pick to server and applies locally */
    public static void sendMorphPick(EntityType<?> morph) {
        if (activeRoomId == null) return;
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(morph).toString();
        ClientPlayNetworking.send(new MobBrawlNetworking.MobBrawlMorphPickPayload(activeRoomId, typeId));
        MorphState.setMorph(morph);
    }

    // ── Opponent info ─────────────────────────────────────────────────────────

    @SuppressWarnings("unused")
    public static String getMyName()       { return myName; }
    public static String getOpponentName() { return opponentName; }
    public static void   setOpponentName(String name) { opponentName = name; }

    // ── Getters ───────────────────────────────────────────────────────────────

    public static boolean isActive()             { return isActive; }
    public static boolean isHost()               { return isHost; }
    public static String  getActiveRoomId()      { return activeRoomId; }
    public static float   getMyHealth()          { return myHealth; }
    public static float   getOpponentHealth()    { return opponentHealth; }
    public static float   getMyMaxHealth()       { return myMaxHealth; }
    public static float   getOpponentMaxHealth() { return opponentMaxHealth; }
    public static int     getMyLives()           { return myLives; }
    public static int     getOpponentLives()     { return opponentLives; }
    public static float   getTimer()             { return timer; }
    public static int     getTimeLimitSetting()  { return timeLimit; }
    @SuppressWarnings("unused")
    public static int     getPhase()             { return phase; }
    public static int     getCountdownValue()    { return countdownValue; }
    public static float   getCountdownFlash()    { return countdownFlash; }
    public static boolean isInCountdown()        { return countdownFlash > 0f && countdownValue > 0; }
    public static float   getLifeFlashTimer()    { return lifeFlashTimer; }
    public static EntityType<?> getMyMorph()     { return myMorph; }
    public static EntityType<?> getOpponentMorph(){ return opponentMorph; }
    @SuppressWarnings("unused")
    public static boolean didWin()               { return didWin; }
    @SuppressWarnings("unused")
    public static float   getMyDamageDealt()     { return myDamageDealt; }
    @SuppressWarnings("unused")
    public static float   getOppDamageDealt()    { return oppDamageDealt; }
    @SuppressWarnings("unused")
    public static int     getMyLivesLeft()       { return myLivesLeft; }
    @SuppressWarnings("unused")
    public static int     getOppLivesLeft()      { return oppLivesLeft; }
    public static float   getLocalTimer()        { return localTimer; }

    // Config getters
    @SuppressWarnings("unused")
    public static int getHealthMode() { return healthMode; }
    @SuppressWarnings("unused")
    public static int     getAbilitiesMode()     { return abilitiesMode; }
    public static int     getLives()             { return lives; }
    @SuppressWarnings("unused")
    public static boolean isArenaMode()          { return arenaMode; }

    public static void tickOpponentMouseSmoothing(float dt) {
        float f = Math.min(1f, dt * 12f);
        opponentMouseXSmooth += (opponentMouseX - opponentMouseXSmooth) * f;
        opponentMouseYSmooth += (opponentMouseY - opponentMouseYSmooth) * f;
    }

    public static float   getOpponentMouseX()    { return opponentMouseXSmooth; }
    public static float   getOpponentMouseY()    { return opponentMouseYSmooth; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static EntityType<?> resolveType(String id) {
        if (id == null || id.isEmpty()) return null;
        try { return BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id)); }
        catch (Exception e) { return null; }
    }

    private static void playLifeLostSound() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.6f, 0.8f, false);
    }
}