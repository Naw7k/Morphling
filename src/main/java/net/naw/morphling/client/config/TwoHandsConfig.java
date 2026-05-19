package net.naw.morphling.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.EntityType;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Two-hands feature config.
 * - "enabled" toggle persisted to disk (default OFF)
 * - Hardcoded per-morph offsets (NOT tunable in the hand placement menu)

 * Only morphs in TWO_HAND_MORPHS get the second hand rendered.
 */
@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public class TwoHandsConfig {

    public record Offset(float x, float y, float z) {
    }

    private static final Map<EntityType<?>, Offset> OFFSETS = new HashMap<>();
    private static final Offset ZERO = new Offset(0F, 0F, 0F);

    public static final Set<EntityType<?>> TWO_HAND_MORPHS = Set.of(
            EntityType.PARROT,
            EntityType.CHICKEN,
            EntityType.DOLPHIN,
            EntityType.ZOMBIE
    );

    static {
        OFFSETS.put(EntityType.PARROT,  new Offset( 0.000F, -0.950F,  0.200F));
        OFFSETS.put(EntityType.CHICKEN, new Offset(-0.000F, -0.750F,  0.150F));
        OFFSETS.put(EntityType.DOLPHIN, new Offset( 0.000F,  0.250F, -0.150F));
        OFFSETS.put(EntityType.ZOMBIE,  new Offset( 0.000F,  0.000F,  0.000F));
    }

    private static boolean enabled = false;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        saveToFile();
    }

    public static boolean shouldRenderSecondHand(EntityType<?> morphType) {
        return enabled && morphType != null && TWO_HAND_MORPHS.contains(morphType);
    }

    public static Offset getOffset(EntityType<?> type) {
        Offset o = OFFSETS.get(type);
        return o != null ? o : ZERO;
    }

    private static Path getSavePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("morphling-twohands.json");
    }

    private static class State {
        boolean enabled;
    }

    public static void saveToFile() {
        try {
            State s = new State();
            s.enabled = enabled;
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (Writer w = Files.newBufferedWriter(getSavePath())) {
                gson.toJson(s, w);
            }
        } catch (Exception ignored) {}
    }

    public static void loadFromFile() {
        try {
            Path p = getSavePath();
            if (!Files.exists(p)) return;
            Gson gson = new Gson();
            try (Reader r = Files.newBufferedReader(p)) {
                State s = gson.fromJson(r, State.class);
                if (s != null) enabled = s.enabled;
            }
        } catch (Exception ignored) {}
    }
}