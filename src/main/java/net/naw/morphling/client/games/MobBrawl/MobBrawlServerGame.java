package net.naw.morphling.client.games.MobBrawl;

import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side Mob Brawl game state — one instance per active room.

 * Manages the full lifecycle of a 1v1 Mob Brawl match:
 *   1. LOBBY        — waiting for both players, host configures
 *   2. MORPH_SELECT — both players picking their morph (timed)
 *   3. COUNTDOWN    — 3..2..1 before fight begins
 *   4. FIGHTING     — active combat, tracking damage and lives
 *   5. ENDED        — winner determined, cleanup pending

 * Config (set by host, broadcast to guest):
 *   healthMode    — 0=Morph Default, 1=Equal 20♥, 2=Double 40♥
 *   abilitiesMode — 0=All, 1=No Weapons, 2=No Abilities
 *   timeLimit     — seconds (-1 = no limit)
 *   lives         — 1 / 3 / 5
 *   arenaType     — 0=No Arena, 1=Gladiator, 2=Nature, 3=Night, 4=Ocean

 * Damage tracking:
 *   Each time a player deals damage to the opponent, it's recorded.
 *   When a player's lives reach 0, the other player wins.
 *   If time runs out, the player with more lives (or more damage dealt) wins.
 */
public class MobBrawlServerGame {

    // ── Game phases ───────────────────────────────────────────────────────────
    public enum Phase {
        LOBBY,        // waiting for players + config
        MORPH_SELECT, // both players picking morph
        COUNTDOWN,    // 3..2..1
        FIGHTING,     // active combat
        ENDED         // game over
    }

    // ── Config ────────────────────────────────────────────────────────────────
    public int healthMode    = 1;   // 0=Default, 1=Equal 20♥, 2=Double 40♥
    public int abilitiesMode = 0;   // 0=All, 1=No Weapons, 2=No Abilities
    public int damageMode    = 0;   // 0=Morph Default, 1=Equal Damage
    public int timeLimit     = 300; // seconds (-1 = no limit), default 5 min
    public int lives         = 3;   // lives per player
    public int arenaType     = 0;   // 0=No Arena, 1=Gladiator, 2=Nature, 3=Night, 4=Ocean

    // ── State ─────────────────────────────────────────────────────────────────
    public Phase phase           = Phase.LOBBY;
    public float timer           = 0f;   // countdown or fight timer
    public float morphSelectTimer = 30f; // 30s to pick morph

    // ── Players ───────────────────────────────────────────────────────────────
    public UUID hostUUID;
    public UUID guestUUID;

    // Chosen morphs
    public EntityType<?> hostMorph  = null;
    public EntityType<?> guestMorph = null;
    public boolean hostReady  = false;
    public boolean guestReady = false;

    // Saved positions for restoring after arena match
    public double[] hostSavedPos  = null; // x, y, z
    public double[] guestSavedPos = null;

    // Lives remaining
    public int hostLives;
    public int guestLives;

    // Damage dealt this round (for tiebreaker)
    public float hostDamageDealt  = 0f;
    public float guestDamageDealt = 0f;

    // Current and max health — separate since morphs have different HP
    public float hostCurrentHealth  = 20f;
    public float guestCurrentHealth = 20f;
    public float hostMaxHealth      = 20f;
    public float guestMaxHealth     = 20f;
    public float maxHealth          = 20f; // config-based fallback (Equal/Double mode)

    // Winner — null until game ends
    public UUID winnerUUID = null;

    // Room ID this game belongs to
    public final String roomId;

    // ── Static registry — one game per room ──────────────────────────────────
    private static final Map<String, MobBrawlServerGame> GAMES = new HashMap<>();

    public MobBrawlServerGame(String roomId, UUID hostUUID) {
        this.roomId   = roomId;
        this.hostUUID = hostUUID;
        this.hostLives  = lives;
        this.guestLives = lives;
    }

    // ── Registry ──────────────────────────────────────────────────────────────

    public static void create(String roomId, UUID hostUUID) {
        GAMES.put(roomId, new MobBrawlServerGame(roomId, hostUUID));
    }

    public static MobBrawlServerGame get(String roomId) {
        return GAMES.get(roomId);
    }

    public static void remove(String roomId) {
        GAMES.remove(roomId);
    }

    public static boolean exists(String roomId) {
        return GAMES.containsKey(roomId);
    }

    public static java.util.Collection<MobBrawlServerGame> getAllGames() {
        return GAMES.values();
    }

    public static MobBrawlServerGame getByPlayer(UUID playerUUID) {
        for (MobBrawlServerGame game : GAMES.values()) {
            if (playerUUID.equals(game.hostUUID) || playerUUID.equals(game.guestUUID)) return game;
        }
        return null;
    }

    // ── Player management ─────────────────────────────────────────────────────

    /** Called when the guest joins the room */
    public void setGuest(UUID guestUUID) {
        this.guestUUID = guestUUID;
    }

    @SuppressWarnings("unused")
    public boolean isFull() {
        return hostUUID != null && guestUUID != null;
    }

    public boolean isHost(UUID uuid) {
        return uuid.equals(hostUUID);
    }

    @SuppressWarnings("unused")
    public UUID getOpponent(UUID uuid) {
        return uuid.equals(hostUUID) ? guestUUID : hostUUID;
    }

    // ── Config update ─────────────────────────────────────────────────────────

    public void applyConfig(int healthMode, int abilitiesMode, int damageMode, int timeLimit, int lives, int arenaType) {
        this.healthMode    = healthMode;
        this.abilitiesMode = abilitiesMode;
        this.damageMode    = damageMode;
        this.timeLimit     = timeLimit;
        this.lives         = lives;
        this.arenaType     = arenaType;
        this.hostLives     = lives;
        this.guestLives    = lives;
        this.maxHealth     = healthMode == 2 ? 40f : 20f;
    }

    // ── Morph selection ───────────────────────────────────────────────────────

    public void setMorphChoice(UUID uuid, EntityType<?> morph) {
        if (uuid.equals(hostUUID))  { hostMorph  = morph; hostReady  = true; }
        if (uuid.equals(guestUUID)) { guestMorph = morph; guestReady = true; }
    }

    public boolean bothReady() {
        return hostReady && guestReady;
    }

    // ── Phase transitions ─────────────────────────────────────────────────────

    public void startMorphSelect() {
        phase             = Phase.MORPH_SELECT;
        morphSelectTimer  = 30f;
        hostReady         = false;
        guestReady        = false;
    }

    public void startCountdown() {
        phase = Phase.COUNTDOWN;
        timer = 3f;
    }

    public void startFight(net.minecraft.server.MinecraftServer server) {
        phase            = Phase.FIGHTING;
        timer            = timeLimit > 0 ? timeLimit : -1f;
        hostDamageDealt  = 0f;
        guestDamageDealt = 0f;

        // Use actual morph max health
        hostMaxHealth      = getMorphMaxHealth(hostMorph,  server);
        guestMaxHealth     = getMorphMaxHealth(guestMorph, server);
        hostCurrentHealth  = hostMaxHealth;
        guestCurrentHealth = guestMaxHealth;
    }

    private float getMorphMaxHealth(EntityType<?> morph, net.minecraft.server.MinecraftServer server) {
        // Equal 20♥ or Double 40♥ — override morph health
        if (healthMode == 1) return 20f;
        if (healthMode == 2) return 40f;

        // Morph Default — use actual morph max health
        if (morph == null || server == null) return 20f;
        try {
            net.minecraft.server.level.ServerLevel level = server.overworld();
            net.minecraft.world.entity.Entity e = morph.create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
            if (e instanceof net.minecraft.world.entity.LivingEntity living) {
                float hp = living.getMaxHealth();
                e.discard();
                return hp;
            }
        } catch (Exception ignored) {}
        return 20f;
    }

    public void endGame(UUID winner) {
        phase      = Phase.ENDED;
        winnerUUID = winner;
    }

    // ── Combat ────────────────────────────────────────────────────────────────

    /**
     * Records damage dealt from attacker to defender.
     * Returns true if the defender lost a life (health reached 0).
     */
    @SuppressWarnings("unused")
    public boolean recordDamage(UUID attackerUUID, float damage) {
        boolean defenderDied = false;

        if (attackerUUID.equals(hostUUID)) {
            hostDamageDealt    += damage;
            guestCurrentHealth -= damage;
            if (guestCurrentHealth <= 0) {
                guestCurrentHealth = guestMaxHealth;
                guestLives--;
                defenderDied = true;
            }
        } else {
            guestDamageDealt  += damage;
            hostCurrentHealth -= damage;
            if (hostCurrentHealth <= 0) {
                hostCurrentHealth = hostMaxHealth;
                hostLives--;
                defenderDied = true;
            }
        }

        return defenderDied;
    }

    /** Tracks damage dealt for stats only — no health/lives logic */
    public void trackDamage(UUID attackerUUID, float damage) {
        if (attackerUUID.equals(hostUUID)) hostDamageDealt += damage;
        else guestDamageDealt += damage;
    }

    /** Resets host health to full — called after respawn */
    public void resetHostHealth()  { hostCurrentHealth  = hostMaxHealth; }

    /** Resets guest health to full — called after respawn */
    public void resetGuestHealth() { guestCurrentHealth = guestMaxHealth; }

    /** Returns the winner UUID based on lives — null if game still ongoing */
    public UUID checkWinner() {
        if (hostLives  <= 0) return guestUUID;
        if (guestLives <= 0) return hostUUID;
        return null;
    }

    /** Called when time runs out — winner by lives, then damage dealt as tiebreaker */
    public UUID determineTimerWinner() {
        if (hostLives != guestLives) {
            return hostLives > guestLives ? hostUUID : guestUUID;
        }
        return hostDamageDealt >= guestDamageDealt ? hostUUID : guestUUID;
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    /**
     * Ticks the game state — called from server tick event.
     * Returns true if phase changed (caller should broadcast update).
     * MORPH_SELECT and COUNTDOWN are intentionally not handled here — see tickMorphSelect().
     */
    public boolean tick(float deltaTime) {
        if (phase == Phase.FIGHTING) {
            if (timeLimit > 0) {
                timer -= deltaTime;
                if (timer <= 0) {
                    endGame(determineTimerWinner());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Server-authoritative morph-select countdown.
     * Called every server tick while in MORPH_SELECT.
     * Returns true ONLY when the 30s expires without both players ready —
     * the caller then cancels the match and sends both players back to the room browser.
     * The "both ready" start is owned by onMorphPick (immediate), so it's a no-op here.
     */
    public boolean tickMorphSelect(float deltaTime) {
        if (phase != Phase.MORPH_SELECT) return false;
        if (bothReady()) return false;
        morphSelectTimer -= deltaTime;
        return morphSelectTimer <= 0f;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Phase   getPhase()            { return phase; }
    public float   getTimer()            { return timer; }
    @SuppressWarnings("unused")
    public float   getMorphSelectTimer() { return morphSelectTimer; }
    public int     getHostLives()        { return hostLives; }
    public int     getGuestLives()       { return guestLives; }
    public float   getHostHealth()       { return hostCurrentHealth; }
    public float   getGuestHealth()      { return guestCurrentHealth; }
    public float   getMaxHealth()        { return maxHealth; }
    public float   getHostMaxHealth()    { return hostMaxHealth; }
    public float   getGuestMaxHealth()   { return guestMaxHealth; }
    public UUID    getWinner()           { return winnerUUID; }
    public boolean isArenaMode()         { return arenaType > 0; }
    public int     getArenaType()        { return arenaType; }
    public int     getLives()            { return lives; }
    public int     getHealthMode()       { return healthMode; }
    @SuppressWarnings("unused")
    public int     getAbilitiesMode()    { return abilitiesMode; }
    public int     getDamageMode()       { return damageMode; }
    @SuppressWarnings("unused")
    public int     getTimeLimit()        { return timeLimit; }
}