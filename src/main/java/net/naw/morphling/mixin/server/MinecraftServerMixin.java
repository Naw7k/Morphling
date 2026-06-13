package net.naw.morphling.mixin.server;

import com.google.common.collect.ImmutableList;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.naw.morphling.client.games.MobBrawl.BrawlDimension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers Mob Brawl void dimensions at server startup.

 * Two dimensions are injected after vanilla createLevels():
 *   morphling:brawl_arena       — day arenas (Gladiator, Nature, Ocean)
 *   morphling:brawl_arena_night — Night arena (no skybox, always dark)

 * Both use DerivedLevelData from the overworld and have noSave = true
 * since arena terrain is generated procedurally each match.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Final
    @org.spongepowered.asm.mixin.Shadow
    protected net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess storageSource;

    @Final
    @org.spongepowered.asm.mixin.Shadow
    private java.util.concurrent.Executor executor;

    @Inject(method = "createLevels", at = @At("TAIL"))
    private void morphling$addBrawlDimensions(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer)(Object)this;
        MinecraftServerAccessor accessor = (MinecraftServerAccessor) this;

        ServerLevelData overworldData = server.getWorldData().overworldData();
        DerivedLevelData derivedData = new DerivedLevelData(server.getWorldData(), overworldData);
        var dimensions = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);

        // ── Main brawl dimension — Gladiator, Nature, Ocean ───────────────────
        try {
            net.minecraft.resources.ResourceKey<LevelStem> stemKey = net.minecraft.resources.ResourceKey.create(
                    Registries.LEVEL_STEM,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("morphling", "brawl_arena"));
            LevelStem stem = dimensions.getValue(stemKey);
            if (stem == null) {
                System.err.println("[Morphling] brawl_arena LevelStem not found — falling back to overworld");
                stem = dimensions.getValue(LevelStem.OVERWORLD);
            }
            ServerLevel level = null;
            if (stem != null) {
                level = new ServerLevel(
                        server, this.executor, this.storageSource, derivedData,
                        BrawlDimension.DIMENSION_KEY, stem,
                        false, server.getWorldGenSettings().options().seed(),
                        ImmutableList.of(), false
                );
            }
            if (level != null) {
                level.noSave = true;
            }
            accessor.morphling$getLevels().put(BrawlDimension.DIMENSION_KEY, level);
            BrawlDimension.serverLevel = level;
        } catch (Exception e) {
            System.err.println("[Morphling] Failed to register brawl dimension: " + e.getMessage());
        }

        // ── Night brawl dimension — no skybox, always dark ────────────────────
        try {
            net.minecraft.resources.ResourceKey<LevelStem> nightStemKey = net.minecraft.resources.ResourceKey.create(
                    Registries.LEVEL_STEM,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("morphling", "brawl_arena_night"));
            LevelStem nightStem = dimensions.getValue(nightStemKey);
            if (nightStem == null) {
                System.err.println("[Morphling] brawl_arena_night LevelStem not found — falling back to overworld");
                nightStem = dimensions.getValue(LevelStem.OVERWORLD);
            }
            ServerLevel nightLevel = null;
            if (nightStem != null) {
                nightLevel = new ServerLevel(
                        server, this.executor, this.storageSource, derivedData,
                        BrawlDimension.NIGHT_DIMENSION_KEY, nightStem,
                        false, server.getWorldGenSettings().options().seed(),
                        ImmutableList.of(), false
                );
            }
            if (nightLevel != null) {
                nightLevel.noSave = true;
            }
            accessor.morphling$getLevels().put(BrawlDimension.NIGHT_DIMENSION_KEY, nightLevel);
            BrawlDimension.nightServerLevel = nightLevel;
        } catch (Exception e) {
            System.err.println("[Morphling] Failed to register brawl night dimension: " + e.getMessage());
        }
    }
}