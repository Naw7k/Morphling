package net.naw.morphling.client.games.MorphRoulette;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent stats manager for Morph Roulette.

 * Stats are saved to .minecraft/config/morphling_roulette_stats.json
 * and loaded on first access. Call save() after each session ends.

 * Tracked stats:
 *   bestScore           — highest score in a single session
 *   totalMorphsSurvived — all-time score across all sessions
 *   totalSpins          — all-time total spins across all sessions
 *   totalSessions       — number of completed sessions
 *   longestSession      — most spins in a single session
 *   totalTimePlayed     — total seconds played across all sessions
 *   topRuns             — top 5 runs with score, config label, and date
 */
public class MorphRouletteStats {

    // ── Run entry — one per session, top 5 kept ───────────────────────────────
    public static class RunEntry {
        public int    score       = 0;
        public int    spins       = 0;
        public String configLabel = ""; // e.g. "10 min • Normal • All Morphs"
        public String date        = ""; // e.g. "2026-05-27"
    }

    // ── Data class (serialized to JSON) ───────────────────────────────────────
    public static class Data {
        public int          bestScore           = 0;
        public int          totalMorphsSurvived = 0;
        public int          totalSpins          = 0;
        public int          totalSessions       = 0;
        public int          longestSession      = 0; // most spins in one session
        public float        totalTimePlayed     = 0f; // seconds
        public List<RunEntry> topRuns           = new ArrayList<>(); // top 5 runs
    }

    private static final Gson   GSON      = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "morphling_roulette_stats.json";
    private static final int    MAX_RUNS  = 5;

    private static Data data = null;

    // ── Load / Save ───────────────────────────────────────────────────────────

    public static Data get() {
        if (data == null) load();
        return data;
    }

    public static void load() {
        try {
            File file = getFile();
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    data = GSON.fromJson(reader, Data.class);
                }
            }
        } catch (Exception ignored) {}
        if (data == null) data = new Data();
        if (data.topRuns == null) data.topRuns = new ArrayList<>();
    }

    public static void save() {
        try {
            File file = getFile();
            //noinspection ResultOfMethodCallIgnored
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception ignored) {}
    }

    // ── Update after session ──────────────────────────────────────────────────

    /**
     * Called when a roulette session ends.
     * Updates all stats, records the run in top 5, and saves to disk.
     *
     * @param score        score this session (kill points)
     * @param spins        total spins this session
     * @param seconds      total seconds played this session
     * @param configLabel  human-readable config string e.g. "10 min • Normal • All Morphs"
     */
    public static void recordSession(int score, int spins, float seconds, String configLabel) {
        Data d = get();
        if (score > d.bestScore)      d.bestScore = score;
        if (spins > d.longestSession) d.longestSession = spins;
        d.totalMorphsSurvived += score;
        d.totalSpins          += spins;
        d.totalTimePlayed     += seconds;
        d.totalSessions++;

        // Add run entry
        RunEntry entry = new RunEntry();
        entry.score       = score;
        entry.spins       = spins;
        entry.configLabel = configLabel;
        entry.date        = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        d.topRuns.add(entry);

        // Sort by score descending and keep top 5
        d.topRuns.sort((a, b) -> b.score - a.score);
        if (d.topRuns.size() > MAX_RUNS) {
            d.topRuns = d.topRuns.subList(0, MAX_RUNS);
        }

        save();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static File getFile() {
        return new File(FabricLoader.getInstance().getConfigDir().toFile(), FILE_NAME);
    }

    /** Formats seconds into a readable string e.g. "2h 14m" or "45m 30s" */
    public static String formatTime(float totalSeconds) {
        int secs  = (int)totalSeconds;
        int hours = secs / 3600;
        int mins  = (secs % 3600) / 60;
        int s     = secs % 60;
        if (hours > 0)  return hours + "h " + mins + "m";
        if (mins > 0)   return mins + "m " + s + "s";
        return s + "s";
    }
}