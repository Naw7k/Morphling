package net.naw.morphling.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public class HandPlacementConfig {

    public static class Offset {
        public float x;
        public float y;
        public float z;

        public Offset(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final Map<EntityType<?>, Offset> OFFSETS = new HashMap<>();
    private static final Map<EntityType<?>, Offset> FA_OFFSETS = new HashMap<>();

    private static final Offset DEFAULT_QUADRUPED = new Offset(0.0F, -0.35F, 0.5F);
    private static final Offset DEFAULT_HUMANOID = new Offset(0.0F, 0.0F, 0.0F);

    static {
        // Normal (no Fresh Animations)
        OFFSETS.put(EntityType.CAT,        new Offset(0.000F, -0.350F, 0.500F));
        OFFSETS.put(EntityType.COW,        new Offset(0.000F, -0.350F, 0.500F));
        OFFSETS.put(EntityType.SHEEP,      new Offset(0.000F, -0.350F, 0.500F));
        OFFSETS.put(EntityType.PIG,        new Offset(-0.200F, -0.800F, 0.300F));
        OFFSETS.put(EntityType.CREEPER,    new Offset(-0.450F, -0.750F, 0.300F));
        OFFSETS.put(EntityType.WOLF,       new Offset(0.000F, -0.400F, 0.500F));
        OFFSETS.put(EntityType.ZOMBIE,     new Offset(0.000F, 0.000F, 0.000F));
        OFFSETS.put(EntityType.SKELETON,   new Offset(0.000F, 0.000F, 0.000F));
        OFFSETS.put(EntityType.ENDERMAN,   new Offset(0.000F, 0.000F, 0.000F));
        OFFSETS.put(EntityType.IRON_GOLEM, new Offset(0.000F, 0.000F, 0.000F));
        OFFSETS.put(EntityType.DOLPHIN, new Offset(0.000F, -0.350F, 0.500F));


        // Fresh Animations values
        FA_OFFSETS.put(EntityType.CAT,        new Offset(-0.100F, 1.150F, 0.250F));
        FA_OFFSETS.put(EntityType.COW,        new Offset(-0.250F, 1.150F, 0.200F));
        FA_OFFSETS.put(EntityType.SHEEP,      new Offset(-0.200F, 1.150F, 0.200F));
        FA_OFFSETS.put(EntityType.PIG,        new Offset(-0.400F, 0.700F, 0.000F));
        FA_OFFSETS.put(EntityType.CREEPER,    new Offset(-0.400F, 0.400F, -0.250F));
        FA_OFFSETS.put(EntityType.WOLF,       new Offset(-0.100F, 1.100F, 0.250F));
        FA_OFFSETS.put(EntityType.ZOMBIE,     new Offset(-0.300F, 0.100F, 0.000F));
        FA_OFFSETS.put(EntityType.SKELETON,   new Offset(-0.300F, 0.150F, 0.000F));
        FA_OFFSETS.put(EntityType.ENDERMAN,   new Offset(-0.200F, -0.450F, 0.100F));
        FA_OFFSETS.put(EntityType.IRON_GOLEM, new Offset(0.000F, 0.000F, 0.000F));
        FA_OFFSETS.put(EntityType.DOLPHIN, new Offset(0.000F, 0.500F, 0.500F));

    }

    public static void resetToDefault(EntityType<?> type) {
        boolean faActive = isFreshAnimationsActive();
        Offset target = faActive ? getFaDefault(type) : getNormalDefault(type);
        Map<EntityType<?>, Offset> active = faActive ? FA_OFFSETS : OFFSETS;
        Offset current = active.get(type);
        if (current != null) {
            current.x = target.x;
            current.y = target.y;
            current.z = target.z;
        }
        saveToFile();
    }

    private static Offset getNormalDefault(EntityType<?> type) {
        if (type == EntityType.PIG)        return new Offset(-0.200F, -0.800F, 0.300F);
        if (type == EntityType.CREEPER)    return new Offset(-0.450F, -0.750F, 0.300F);
        if (type == EntityType.WOLF)       return new Offset(0.000F, -0.400F, 0.500F);
        if (isHumanoid(type))              return new Offset(0.000F, 0.000F, 0.000F);
        return new Offset(0.000F, -0.350F, 0.500F);
    }

    private static Offset getFaDefault(EntityType<?> type) {
        if (type == EntityType.CAT)        return new Offset(-0.100F, 1.150F, 0.250F);
        if (type == EntityType.COW)        return new Offset(-0.250F, 1.150F, 0.200F);
        if (type == EntityType.SHEEP)      return new Offset(-0.200F, 1.150F, 0.200F);
        if (type == EntityType.PIG)        return new Offset(-0.400F, 0.700F, 0.000F);
        if (type == EntityType.CREEPER)    return new Offset(-0.400F, 0.400F, -0.250F);
        if (type == EntityType.WOLF)       return new Offset(-0.100F, 1.100F, 0.250F);
        if (type == EntityType.ZOMBIE)     return new Offset(-0.300F, 0.100F, 0.000F);
        if (type == EntityType.SKELETON)   return new Offset(-0.300F, 0.150F, 0.000F);
        if (type == EntityType.ENDERMAN)   return new Offset(-0.200F, -0.450F, 0.100F);
        if (type == EntityType.IRON_GOLEM) return new Offset(0.000F, 0.000F, 0.000F);
        return new Offset(0.000F, 0.500F, 0.500F);
    }

    public static boolean isFreshAnimationsActive() {
        return net.naw.morphling.client.compat.FaCompat.isFreshAnimationsActive();
    }

    private static boolean isHumanoid(EntityType<?> type) {
        return type == EntityType.ZOMBIE || type == EntityType.SKELETON
                || type == EntityType.ENDERMAN || type == EntityType.IRON_GOLEM;
    }

    public static Offset getOrDefault(EntityType<?> type) {
        boolean faActive = isFreshAnimationsActive();
        Map<EntityType<?>, Offset> active = faActive ? FA_OFFSETS : OFFSETS;
        Offset fallback = isHumanoid(type) ? DEFAULT_HUMANOID
                : (faActive ? new Offset(0.0F, 0.5F, 0.5F) : DEFAULT_QUADRUPED);
        return active.computeIfAbsent(type, t -> new Offset(fallback.x, fallback.y, fallback.z));
    }

    public static EntityType<?>[] getTunableMobs() {
        return new EntityType<?>[] {
                EntityType.PIG, EntityType.COW, EntityType.SHEEP,
                EntityType.WOLF, EntityType.CAT, EntityType.CREEPER,
                EntityType.ZOMBIE, EntityType.SKELETON, EntityType.ENDERMAN, EntityType.IRON_GOLEM,
                EntityType.DOLPHIN
        };
    }

    private static Path getSavePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("morphling-handplacement.json");
    }

    public static void saveToFile() {
        try {
            Map<String, Map<String, Offset>> data = new HashMap<>();
            Map<String, Offset> normalMap = new HashMap<>();
            Map<String, Offset> faMap = new HashMap<>();
            OFFSETS.forEach((k, v) -> normalMap.put(BuiltInRegistries.ENTITY_TYPE.getKey(k).toString(), v));
            FA_OFFSETS.forEach((k, v) -> faMap.put(BuiltInRegistries.ENTITY_TYPE.getKey(k).toString(), v));
            data.put("normal", normalMap);
            data.put("fa", faMap);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (Writer w = Files.newBufferedWriter(getSavePath())) {
                gson.toJson(data, w);
            }
        } catch (Exception ignored) {}
    }

    public static void loadFromFile() {
        try {
            Path p = getSavePath();
            if (!Files.exists(p)) return;

            Gson gson = new Gson();
            try (Reader r = Files.newBufferedReader(p)) {
                Map<String, Map<String, Offset>> data = gson.fromJson(r,
                        new TypeToken<Map<String, Map<String, Offset>>>(){}.getType());
                if (data == null) return;
                applyMap(data.get("normal"), OFFSETS);
                applyMap(data.get("fa"), FA_OFFSETS);
            }
        } catch (Exception ignored) {}
    }

    private static void applyMap(Map<String, Offset> source, Map<EntityType<?>, Offset> target) {
        if (source == null) return;
        source.forEach((id, offset) -> {
            try {
                BuiltInRegistries.ENTITY_TYPE.get(Identifier.parse(id))
                        .map(net.minecraft.core.Holder::value)
                        .ifPresent(type -> target.put(type, offset));
            } catch (Exception ignored) {}
        });
    }
}