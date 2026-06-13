package net.naw.morphling.client.games.trivia;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * Manages the Morph Trivia personal best score.

 * Saves to: .minecraft/morphling_trivia_pb.json
 * Persists across sessions, worlds, and game restarts.

 * Usage:
 *   TriviaPBManager.load()        — call once at game over to get current PB
 *   TriviaPBManager.getPB()       — returns the loaded PB value
 *   TriviaPBManager.save(score)   — saves a new PB if score beats the current one
 *   TriviaPBManager.isNewPB()     — true if the last save() call set a new record
 */
public class TriviaPBManager {

    private static final String FILE_NAME = "morphling_trivia_pb.json";
    private static final Gson   GSON      = new Gson();

    private static int     pb       = 0;
    private static boolean isNewPB  = false;

    /** Loads PB from disk. Call this when the game over screen appears. */
    public static void load() {
        File file = getPBFile();
        if (!file.exists()) {
            pb = 0;
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            if (obj != null && obj.has("pb")) {
                pb = obj.get("pb").getAsInt();
            }
        } catch (Exception e) {
            pb = 0;
        }
    }

    /**
     * Saves a new PB if the given score beats the current one.
     * Sets isNewPB to true if it was beaten.
     */
    public static void save(int score) {
        load(); // always load fresh before comparing
        if (score > pb) {
            pb      = score;
            isNewPB = true;
            File file = getPBFile();
            try (FileWriter writer = new FileWriter(file)) {
                JsonObject obj = new JsonObject();
                obj.addProperty("pb", pb);
                GSON.toJson(obj, writer);
            } catch (Exception ignored) {}
        } else {
            isNewPB = false;
        }
    }

    /** Returns the current loaded PB value. Call load() first. */
    public static int getPB() { return pb; }

    /** Returns true if the last save() call set a new personal best. */
    public static boolean isNewPB() { return isNewPB; }

    private static File getPBFile() {
        File gameDir = Minecraft.getInstance().gameDirectory;
        return new File(gameDir, FILE_NAME);
    }
}