package net.naw.morphling.client.games.MorphRoulette;

import net.minecraft.world.entity.EntityType;
import net.naw.morphling.client.core.EntityRegistry;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.client.core.MorphVariantManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Single-player Morph Roulette game logic.

 * The player is forced into a random morph every SPIN_INTERVAL seconds.
 * A countdown HUD shows how long until the next spin.
 * The game runs indefinitely until the player manually stops it.

 * Flow:
 *   1. Player opens MorphRouletteConfigScreen → configures settings → hits Start
 *   2. setConfig() stores chosen settings, start() begins the game
 *   3. Every client tick (20/s via MorphlingClient), tick() decrements countdown
 *   4. When countdown hits 0 → spin() picks a random morph and applies it
 *   5. Player presses Escape → MorphlingClient intercepts and opens MorphRouletteScreen (pause overlay)
 *   6. Player can stop anytime via Stop Game button

 * Morph pool:
 *   All available morphs from EntityRegistry are shuffled each cycle.
 *   The same morph won't repeat until all others have been used.
 *   (deck-shuffle style, not pure random — avoids streaks of the same mob)

 * Scoring (simple):
 *   +1 point per morph survived until the next spin.
 *   Score shown on screen and on game end.
 */
public class MorphRouletteGame {

    // ── Config ────────────────────────────────────────────────────────────────
    /** Default seconds between each forced morph change (overridden by setConfig) */
    public static final float SPIN_INTERVAL = 30f;

    /** Seconds the "NEW MORPH!" flash animation lasts after a morph change */
    public static final float SPIN_FLASH_DURATION = 1.5f;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean running           = false;
    private float   countdown         = SPIN_INTERVAL; // seconds until next spin
    private float   spinFlash         = 0f;            // counts down after each spin for flash effect
    private int     score             = 0;             // morphs survived
    private int     spinCount         = 0;             // total number of spins so far
    private EntityType<?> currentMorph = null;         // currently forced morph
    private float   elapsed           = 0f;            // total elapsed game time
    private float   startCooldown     = 0f;            // grace period after start before escape intercept kicks in

    private boolean endScreenShown = false; // prevents end screen from showing twice
    private float fallDamageImmunity = 0f; // seconds of fall damage immunity after spin

    // ── Config values (set by setConfig before start) ─────────────────────────
    private float configSpinInterval    = SPIN_INTERVAL; // seconds between spins
    private int   configDurationSeconds = -1;            // -1 = endless
    @SuppressWarnings("FieldCanBeLocal")
    private int   configPoolIndex       = 0;             // 0 = all morphs

    // Deck-shuffle pool — reshuffled when exhausted to avoid repeats
    private final List<EntityType<?>> morphPool  = new ArrayList<>();
    private final List<EntityType<?>> usedMorphs = new ArrayList<>();

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static MorphRouletteGame INSTANCE;

    public static MorphRouletteGame getInstance() {
        if (INSTANCE == null) INSTANCE = new MorphRouletteGame();
        return INSTANCE;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Called by MorphRouletteConfigScreen before start() — applies player's chosen settings */
    public void setConfig(float spinInterval, int durationSeconds, int poolIndex) {
        this.configSpinInterval    = spinInterval;
        this.configDurationSeconds = durationSeconds;
        this.configPoolIndex       = poolIndex;
    }

    /** Awards points — called on kill during roulette */
    public void addScore(int points) { if (running) score += points; }

    /** Returns a human-readable config label for stats recording */
    public String getConfigLabel() {
        String duration = configDurationSeconds < 0 ? "Endless" : (configDurationSeconds / 60) + " min";
        String speed = configSpinInterval >= 60 ? "Slow" : configSpinInterval >= 30 ? "Normal" : configSpinInterval >= 15 ? "Fast" : "Chaos";
        String pool = switch (configPoolIndex) {
            case 1 -> "Passive";
            case 2 -> "Hostile";
            case 3 -> "Random Mix";
            default -> "All Morphs";
        };
        return duration + " • " + speed + " • " + pool;
    }

    /** Starts a new roulette game — resets all state and immediately spins */
    public void start() {
        MorphRouletteHud.register(); // register HUD render — safe to call multiple times
        running      = true;
        countdown    = configSpinInterval;
        score        = 0;
        spinCount    = 0;
        spinFlash    = 0f;
        currentMorph = null;
        elapsed      = 0f;
        startCooldown = 2.0f; // 2 second grace period before escape intercept activates
        endScreenShown = false;
        morphPool.clear();
        usedMorphs.clear();
        spin(); // immediately give the player a morph on start
    }

    /** Stops the game and resets the player's morph to normal */
    public void stop() {
        running         = false;
        currentMorph    = null;
        endScreenShown  = true; // prevent auto end screen after manual stop
        MorphState.reset();
    }

    /** Called every client tick (20/s from MorphlingClient) — drives countdown and auto-spin */
    public void tick(float deltaTime) {
        if (!running) return;

        if (startCooldown > 0) startCooldown -= deltaTime;
        if (spinFlash > 0)     spinFlash = Math.max(0f, spinFlash - deltaTime);

        if (fallDamageImmunity > 0) fallDamageImmunity -= deltaTime;

        countdown -= deltaTime;
        if (countdown <= 0f) {
            spin();
        }

        // Duration tracking — end game when time runs out (unless endless)
        if (configDurationSeconds > 0) {
            elapsed += deltaTime;
            if (elapsed >= configDurationSeconds) {
                running = false;
                MorphRouletteStats.recordSession(score, spinCount, elapsed, getConfigLabel());
            }
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean       isRunning()             { return running; }
    public float         getCountdown()          { return countdown; }
    public float         getSpinFlash()          { return spinFlash; }
    public int           getScore()              { return score; }
    public int           getSpinCount()          { return spinCount; }
    public EntityType<?> getCurrentMorph()       { return currentMorph; }
    public float         getConfigSpinInterval() { return configSpinInterval; }
    public float getElapsed()              { return elapsed; }
    public int   getConfigDurationSeconds() { return configDurationSeconds; }
    public boolean shouldShowEndScreen() { return !running && !endScreenShown && spinCount > 0; }
    public void markEndScreenShown()     { endScreenShown = true; }
    public boolean hasFallDamageImmunity() { return running && fallDamageImmunity > 0f; }


    public boolean isBlinkVisible() {
        if (!running || countdown > 3f) return true;
        int secsLeft = (int)Math.ceil(countdown);
        int blinksPerSec = Math.max(1, 4 - secsLeft); // 3s=1, 2s=2, 1s=3
        float blinkInterval = 1f / (blinksPerSec * 2f); // *2 for on/off cycle
        return (int)(countdown / blinkInterval) % 2 == 0;
    }


    // ── Internal ──────────────────────────────────────────────────────────────

    /** Forces a random morph on the player using deck-shuffle logic */
    private void spin() {
        // Refill pool if exhausted
        if (morphPool.isEmpty()) {
            morphPool.addAll(getAvailableMorphTypes());
            morphPool.removeAll(usedMorphs);
            if (morphPool.isEmpty()) {
                // All morphs used — full reset of deck
                usedMorphs.clear();
                morphPool.addAll(getAvailableMorphTypes());
            }
            Collections.shuffle(morphPool);
        }

        // Remove current morph to avoid immediate repeat
        if (currentMorph != null) morphPool.remove(currentMorph);
        if (morphPool.isEmpty()) {
            // Edge case: only one morph available
            morphPool.addAll(getAvailableMorphTypes());
            Collections.shuffle(morphPool);
        }

        EntityType<?> next = morphPool.removeFirst();
        usedMorphs.add(next);
        currentMorph = next;

        MorphState.setMorph(next);

        fallDamageImmunity = 2.0f;
        countdown = configSpinInterval;
        spinFlash = SPIN_FLASH_DURATION;
        spinCount++;


        // Randomize variant for morphs that have them
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level != null) {
            var rng = mc.level.getRandom();
            //noinspection unused
            var rma = mc.level.registryAccess();
            if (next == EntityType.PARROT) {
                var variants = net.minecraft.world.entity.animal.parrot.Parrot.Variant.values();
                net.naw.morphling.client.core.MorphVariantManager.setParrotVariant(variants[rng.nextInt(variants.length)]);
            } else if (next == EntityType.CAT) {
                var list = net.naw.morphling.client.core.MorphVariantManager.getCatVariantList();
                if (!list.isEmpty()) net.naw.morphling.client.core.MorphVariantManager.setCatVariant(list.get(rng.nextInt(list.size())));
            } else if (next == EntityType.WOLF) {
                var list = net.naw.morphling.client.core.MorphVariantManager.getWolfVariantList();
                if (!list.isEmpty()) net.naw.morphling.client.core.MorphVariantManager.setWolfVariant(list.get(rng.nextInt(list.size())));
            } else if (next == EntityType.COW) {
                var list = net.naw.morphling.client.core.MorphVariantManager.getCowVariantList();
                if (!list.isEmpty()) net.naw.morphling.client.core.MorphVariantManager.setCowVariant(list.get(rng.nextInt(list.size())));
            } else if (next == EntityType.SHEEP) {
                var colors = net.minecraft.world.item.DyeColor.values();
                net.naw.morphling.client.core.MorphVariantManager.setSheepColor(colors[rng.nextInt(colors.length)]);
            } else if (next == EntityType.PIG) {
                var list = net.naw.morphling.client.core.MorphVariantManager.getPigVariantList();
                if (!list.isEmpty()) net.naw.morphling.client.core.MorphVariantManager.setPigVariant(list.get(rng.nextInt(list.size())));
            } else if (next == EntityType.CHICKEN) {
                var list = net.naw.morphling.client.core.MorphVariantManager.getChickenVariantList();
                if (!list.isEmpty()) net.naw.morphling.client.core.MorphVariantManager.setChickenVariant(list.get(rng.nextInt(list.size())));
            } else if (next == EntityType.HORSE) {
                var colors = net.naw.morphling.client.core.MorphVariantManager.getHorseColors();
                var markings = net.naw.morphling.client.core.MorphVariantManager.getHorseMarkingsList();
                net.naw.morphling.client.core.MorphVariantManager.setHorseColor(colors[rng.nextInt(colors.length)]);
                net.naw.morphling.client.core.MorphVariantManager.setHorseMarkings(markings[rng.nextInt(markings.length)]);
            } else if (next == EntityType.FOX) {
                MorphVariantManager.setFoxVariant(
                        Math.random() < 0.5 ? net.minecraft.world.entity.animal.fox.Fox.Variant.RED
                                : net.minecraft.world.entity.animal.fox.Fox.Variant.SNOW);
            } else if (next == EntityType.RABBIT) {
                var variants = net.minecraft.world.entity.animal.rabbit.Rabbit.Variant.values();
                MorphVariantManager.setRabbitVariant(variants[rng.nextInt(variants.length)]);
            } else if (next == EntityType.AXOLOTL) {
                var variants = net.minecraft.world.entity.animal.axolotl.Axolotl.Variant.values();
                MorphVariantManager.setAxolotlVariant(variants[rng.nextInt(variants.length)]);
            } else if (next == EntityType.FROG) {
                var variants = net.naw.morphling.client.core.MorphVariantManager.getFrogVariantList();
                if (!variants.isEmpty())
                    MorphVariantManager.setFrogVariant(variants.get(rng.nextInt(variants.size())));
            } else if (next == EntityType.PANDA) {
                var genes = net.minecraft.world.entity.animal.panda.Panda.Gene.values();
                MorphVariantManager.setPandaGene(genes[rng.nextInt(genes.length)]);
            }
        }



    }

    /** Returns morph types filtered by configPoolIndex */
    private List<EntityType<?>> getAvailableMorphTypes() {
        List<EntityType<?>> passive = List.of(
                EntityType.CHICKEN, EntityType.COW, EntityType.PIG, EntityType.SHEEP,
                EntityType.CAT, EntityType.WOLF, EntityType.PARROT, EntityType.HORSE,
                EntityType.VILLAGER, EntityType.IRON_GOLEM, EntityType.DOLPHIN, EntityType.BEE,
                EntityType.FOX, EntityType.RABBIT, EntityType.AXOLOTL, EntityType.FROG, EntityType.POLAR_BEAR, EntityType.PANDA
        );
        List<EntityType<?>> hostile = List.of(
                EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER,
                EntityType.SPIDER, EntityType.ENDERMAN, EntityType.SLIME
        );

        return switch (configPoolIndex) {
            case 1 -> new ArrayList<>(passive);  // Passive Only
            case 2 -> new ArrayList<>(hostile);  // Hostile Only
            case 3 -> {                           // Random Mix — half passive half hostile
                var mix = new ArrayList<EntityType<?>>();
                var rPassive = new ArrayList<>(passive);
                var rHostile = new ArrayList<>(hostile);
                Collections.shuffle(rPassive);
                Collections.shuffle(rHostile);
                mix.addAll(rPassive.subList(0, rPassive.size() / 2));
                mix.addAll(rHostile.subList(0, rHostile.size() / 2));
                yield mix;
            }
            default -> {                          // All Morphs (0)
                List<EntityType<?>> all = new ArrayList<>();
                for (EntityRegistry.MorphEntry entry : EntityRegistry.getAvailableMorphs()) {
                    all.add(entry.type());
                }
                yield all;
            }
        };
    }
}