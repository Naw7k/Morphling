package net.naw.morphling.client.games.MobBrawl;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Manages the Mob Brawl void dimension and all arena types.

 * Two dimensions are registered:
 *   morphling:brawl_arena       — day arenas (Gladiator, Nature, Ocean) — overworld sky
 *   morphling:brawl_arena_night — Night arena — no skybox (always dark)

 * Each arena type occupies a different Z offset to avoid overlap:
 *   GLADIATOR  — Z=0      (colosseum style, seating, fountain in center)
 *   NATURE     — Z=2000   (grass, 2 trees, flowers, firefly bushes)
 *   NIGHT      — Z=0      (dark stone, soul fire far from spawn, chains)
 *   OCEAN      — Z=6000   (prismarine, 1 block water, sea lanterns, coral)

 * First spawn: opposite corners facing the middle (dramatic face-off)
 * Respawn: random position within arena bounds
 */
public class BrawlDimension {

    // ── Dimension keys ────────────────────────────────────────────────────────

    /** Main dimension — Gladiator, Nature, Ocean arenas */
    public static final ResourceKey<Level> DIMENSION_KEY =
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath("morphling", "brawl_arena"));

    /** Night dimension — no skybox, always dark */
    public static final ResourceKey<Level> NIGHT_DIMENSION_KEY =
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath("morphling", "brawl_arena_night"));

    /** Set by MinecraftServerMixin after dimensions are created */
    public static ServerLevel serverLevel      = null;
    public static ServerLevel nightServerLevel = null;

    // ── Arena types ───────────────────────────────────────────────────────────

    public enum ArenaType {
        GLADIATOR(0,    "⚔ Gladiator",  "Colosseum arena. Stone seating. No mercy."),
        NATURE   (2000, "🌿 Nature",     "Grassy highlands. Fireflies. Morning mist."),
        NIGHT    (0,    "🌙 Night",      "Dark stone. Soul fire. Midnight."),
        OCEAN    (6000, "🐬 Ocean",      "Prismarine floors. Shallow water. Dolphins.");

        public final int zOffset;
        public final String label;
        public final String description;

        ArenaType(int zOffset, String label, String description) {
            this.zOffset = zOffset;
            this.label = label;
            this.description = description;
        }
    }

    // Arena dimensions
    public static final int ARENA_RADIUS  = 25;
    public static final int ARENA_Y       = 64;
    @SuppressWarnings("unused")
    public static final int HOST_X  = -15;
    @SuppressWarnings("unused")
    public static final int GUEST_X = 15;

    // Spawn corners (used for first spawn)
    private static final int SPAWN_OFFSET = ARENA_RADIUS - 5; // 20

    // ── Arena generation ──────────────────────────────────────────────────────

    /**
     * Generates the arena platform for the given type.
     * Night arena is generated in nightServerLevel, others in serverLevel.
     */
    public static void generateArena(ServerLevel level, ArenaType type) {
        int centerX = 0;
        int centerZ = type.zOffset;
        int y = ARENA_Y;
        int r = ARENA_RADIUS;

        switch (type) {
            case GLADIATOR -> generateGladiatorArena(level, centerX, y, centerZ, r);
            case NATURE    -> generateNatureArena(level, centerX, y, centerZ, r);
            case NIGHT     -> generateNightArena(level, centerX, y, centerZ, r);
            case OCEAN     -> generateOceanArena(level, centerX, y, centerZ, r);
        }

        // Barrier walls + ceiling for all arenas
        generateBarriers(level, centerX, y, centerZ, r);
    }

    // ── Gladiator Arena — colosseum with fountain ─────────────────────────────

    private static void generateGladiatorArena(ServerLevel level, int cx, int y, int cz, int r) {
        BlockState stone        = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState sandstone    = Blocks.SANDSTONE.defaultBlockState();
        BlockState stoneBrick   = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState crackedBrick = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        BlockState mossyBrick   = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState air          = Blocks.AIR.defaultBlockState();
        BlockState torch        = Blocks.TORCH.defaultBlockState();
        BlockState water        = Blocks.WATER.defaultBlockState();
        BlockState obsidian     = Blocks.OBSIDIAN.defaultBlockState();

        // Floor — checkerboard smooth stone and sandstone
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                level.setBlockAndUpdate(new BlockPos(x, y - 1, z),
                        ((x + z) % 2 == 0) ? stone : sandstone);
                for (int dy = 0; dy <= 10; dy++)
                    level.setBlockAndUpdate(new BlockPos(x, y + dy, z), air);
            }
        }

        // Seating stands — North side
        for (int x = cx - r + 2; x <= cx + r - 2; x++) {
            for (int row = 0; row < 4; row++) {
                level.setBlockAndUpdate(new BlockPos(x, y + row, cz - r + row + 1),
                        row % 2 == 0 ? stoneBrick : crackedBrick);
            }
        }
        // Seating stands — South side
        for (int x = cx - r + 2; x <= cx + r - 2; x++) {
            for (int row = 0; row < 4; row++) {
                level.setBlockAndUpdate(new BlockPos(x, y + row, cz + r - row - 1),
                        row % 2 == 0 ? stoneBrick : crackedBrick);
            }
        }
        // Seating stands — East side
        for (int z = cz - r + 2; z <= cz + r - 2; z++) {
            for (int row = 0; row < 4; row++) {
                level.setBlockAndUpdate(new BlockPos(cx + r - row - 1, y + row, z),
                        row % 2 == 0 ? stoneBrick : crackedBrick);
            }
        }
        // Seating stands — West side
        for (int z = cz - r + 2; z <= cz + r - 2; z++) {
            for (int row = 0; row < 4; row++) {
                level.setBlockAndUpdate(new BlockPos(cx - r + row + 1, y + row, z),
                        row % 2 == 0 ? stoneBrick : crackedBrick);
            }
        }

        // Corner pillars
        int[] corners = {-r + 2, r - 2};
        for (int px : corners) {
            for (int pz : new int[]{cz - r + 2, cz + r - 2}) {
                for (int dy = 0; dy <= 7; dy++)
                    level.setBlockAndUpdate(new BlockPos(cx + px, y + dy, pz),
                            dy % 3 == 0 ? mossyBrick : stoneBrick);
                level.setBlockAndUpdate(new BlockPos(cx + px, y + 8, pz), torch);
            }
        }

        // Torches on seating walls
        for (int x = cx - r + 5; x <= cx + r - 5; x += 6) {
            level.setBlockAndUpdate(new BlockPos(x, y + 5, cz - r + 1), torch);
            level.setBlockAndUpdate(new BlockPos(x, y + 5, cz + r - 1), torch);
        }
        for (int z = cz - r + 5; z <= cz + r - 5; z += 6) {
            level.setBlockAndUpdate(new BlockPos(cx - r + 1, y + 5, z), torch);
            level.setBlockAndUpdate(new BlockPos(cx + r - 1, y + 5, z), torch);
        }

        // Center fountain — 3x3 obsidian base with water in middle
        for (int x = -1; x <= 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                level.setBlockAndUpdate(new BlockPos(cx + x, y - 1, z), obsidian);
            }
        }
        // Fountain walls
        for (int x = -2; x <= 2; x++) {
            level.setBlockAndUpdate(new BlockPos(cx + x, y, cz - 2), mossyBrick);
            level.setBlockAndUpdate(new BlockPos(cx + x, y, cz + 2), mossyBrick);
        }
        for (int z = cz - 1; z <= cz + 1; z++) {
            level.setBlockAndUpdate(new BlockPos(cx - 2, y, z), mossyBrick);
            level.setBlockAndUpdate(new BlockPos(cx + 2, y, z), mossyBrick);
        }
        // Water inside fountain
        for (int x = -1; x <= 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                level.setBlockAndUpdate(new BlockPos(cx + x, y, z), water);
            }
        }
        // Center pillar above water
        level.setBlockAndUpdate(new BlockPos(cx, y + 1, cz), stoneBrick);
        level.setBlockAndUpdate(new BlockPos(cx, y + 2, cz), water);
    }

    // ── Nature Arena — grass glade with fireflies ─────────────────────────────

    private static void generateNatureArena(ServerLevel level, int cx, int y, int cz, int r) {
        BlockState grass       = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt        = Blocks.DIRT.defaultBlockState();
        BlockState air         = Blocks.AIR.defaultBlockState();
        BlockState oakLog      = Blocks.OAK_LOG.defaultBlockState();
        BlockState oakLeaves   = Blocks.OAK_LEAVES.defaultBlockState();
        BlockState flower1     = Blocks.DANDELION.defaultBlockState();
        BlockState flower2     = Blocks.POPPY.defaultBlockState();
        BlockState flower3     = Blocks.BLUE_ORCHID.defaultBlockState();
        BlockState tallGrass   = Blocks.SHORT_GRASS.defaultBlockState();
        BlockState fern        = Blocks.FERN.defaultBlockState();
        BlockState fireflyBush = Blocks.FIREFLY_BUSH.defaultBlockState();
        BlockState mossyCobble = Blocks.MOSSY_COBBLESTONE.defaultBlockState();

        // Grass floor
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                level.setBlockAndUpdate(new BlockPos(x, y - 1, z), grass);
                level.setBlockAndUpdate(new BlockPos(x, y - 2, z), dirt);
                for (int dy = 0; dy <= 10; dy++)
                    level.setBlockAndUpdate(new BlockPos(x, y + dy, z), air);
            }
        }

        // 2 trees — on the sides (not corners, not near spawn corners)
        int[][] treePos = {{cx - 10, cz}, {cx + 10, cz}};
        for (int[] tp : treePos) {
            int tx = tp[0], tz = tp[1];
            for (int dy = 0; dy <= 4; dy++)
                level.setBlockAndUpdate(new BlockPos(tx, y + dy, tz), oakLog);
            for (int lx = -2; lx <= 2; lx++)
                for (int lz = -2; lz <= 2; lz++)
                    for (int ly = 3; ly <= 6; ly++)
                        if (Math.abs(lx) + Math.abs(lz) + Math.abs(ly - 4) <= 4)
                            level.setBlockAndUpdate(new BlockPos(tx + lx, y + ly, tz + lz), oakLeaves);
        }

        // Mossy cobble rocks scattered (not near spawn corners)
        int[][] rocks = {
                {cx - 5, cz + 8}, {cx + 5, cz - 8},
                {cx - 12, cz + 3}, {cx + 12, cz - 3}
        };
        for (int[] rp : rocks)
            level.setBlockAndUpdate(new BlockPos(rp[0], y, rp[1]), mossyCobble);

        // Flowers, ferns, tall grass (avoid spawn corners and trees)
        java.util.Random rand = new java.util.Random(42);
        BlockState[] plants = {flower1, flower2, flower3, tallGrass, fern, tallGrass};
        for (int i = 0; i < 40; i++) {
            int fx = cx + rand.nextInt(r * 2 - 8) - (r - 4);
            int fz = cz + rand.nextInt(r * 2 - 8) - (r - 4);
            // Avoid spawn corners
            if (Math.abs(fx - (-SPAWN_OFFSET)) < 4 && Math.abs(fz - (cz - SPAWN_OFFSET)) < 4) continue;
            if (Math.abs(fx - SPAWN_OFFSET) < 4 && Math.abs(fz - (cz + SPAWN_OFFSET)) < 4) continue;
            // Avoid tree trunks
            if (Math.abs(fx - (cx - 10)) < 3 && Math.abs(fz - cz) < 3) continue;
            if (Math.abs(fx - (cx + 10)) < 3 && Math.abs(fz - cz) < 3) continue;
            level.setBlockAndUpdate(new BlockPos(fx, y, fz), plants[i % plants.length]);
        }

        // Firefly bushes — away from spawn corners
        int[][] fireflyPos = {
                {cx - 8,  cz + 10}, {cx + 8,  cz - 10},
                {cx - 15, cz + 5},  {cx + 15, cz - 5},
                {cx - 5,  cz + 15}, {cx + 5,  cz - 15}
        };
        for (int[] fp : fireflyPos) {
            level.setBlockAndUpdate(new BlockPos(fp[0], y - 1, fp[1]), grass);
            level.setBlockAndUpdate(new BlockPos(fp[0], y,     fp[1]), fireflyBush);
        }
    }

    // ── Night Arena — dark stone, soul fire far from spawn ────────────────────

    private static void generateNightArena(ServerLevel level, int cx, int y, int cz, int r) {
        BlockState deepTiles    = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState crackedTiles = Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
        BlockState blackstone   = Blocks.BLACKSTONE.defaultBlockState();
        BlockState air          = Blocks.AIR.defaultBlockState();
        BlockState glowstone    = Blocks.GLOWSTONE.defaultBlockState();
        BlockState soulFire     = Blocks.SOUL_FIRE.defaultBlockState();
        BlockState soulSand     = Blocks.SOUL_SAND.defaultBlockState();
        BlockState chain        = Blocks.IRON_CHAIN.defaultBlockState();
        BlockState obsidian     = Blocks.OBSIDIAN.defaultBlockState();
        BlockState fireflyBush  = Blocks.FIREFLY_BUSH.defaultBlockState();
        BlockState deepslate    = Blocks.POLISHED_DEEPSLATE.defaultBlockState();

        // Floor
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                int p = ((x + z) % 5 + 5) % 5;
                BlockState floor = p == 0 ? crackedTiles : (p == 1 ? blackstone : deepTiles);
                level.setBlockAndUpdate(new BlockPos(x, y - 1, z), floor);
                for (int dy = 0; dy <= 10; dy++)
                    level.setBlockAndUpdate(new BlockPos(x, y + dy, z), air);
            }
        }

        // Corner obsidian pillars with glowstone and chains
        int[] corners = {-r + 3, r - 3};
        for (int px : corners) {
            for (int pz : new int[]{cz - r + 3, cz + r - 3}) {
                for (int dy = 0; dy <= 7; dy++)
                    level.setBlockAndUpdate(new BlockPos(cx + px, y + dy, pz), obsidian);
                level.setBlockAndUpdate(new BlockPos(cx + px, y + 8, pz), glowstone);
                for (int dy = 9; dy <= 11; dy++)
                    level.setBlockAndUpdate(new BlockPos(cx + px, y + dy, pz), chain);
            }
        }

        // Soul fire — only on the 4 side midpoints, away from spawn corners
        // Spawn corners are at (-20, cz-20) and (+20, cz+20) — midpoints are safe
        int[][] soulFirePositions = {
                {cx - r + 6, cz},
                {cx + r - 6, cz},
                {cx, cz - r + 6},
                {cx, cz + r - 6}
        };
        for (int[] pos : soulFirePositions) {
            level.setBlockAndUpdate(new BlockPos(pos[0], y - 1, pos[1]), soulSand);
            level.setBlockAndUpdate(new BlockPos(pos[0], y,     pos[1]), soulFire);
        }

        // Glowstone lights scattered on floor
        int[][] glowPositions = {
                {cx - 8, cz - 8}, {cx + 8, cz + 8},
                {cx - 8, cz + 8}, {cx + 8, cz - 8},
                {cx,     cz}
        };
        for (int[] gp : glowPositions)
            level.setBlockAndUpdate(new BlockPos(gp[0], y - 1, gp[1]), glowstone);

        // Firefly bushes near edges
        int[][] fireflyPos = {
                {cx - r + 4, cz - 5}, {cx - r + 4, cz + 5},
                {cx + r - 4, cz - 5}, {cx + r - 4, cz + 5},
                {cx - 5, cz - r + 4}, {cx + 5, cz - r + 4},
                {cx - 5, cz + r - 4}, {cx + 5, cz + r - 4}
        };
        for (int[] fp : fireflyPos) {
            level.setBlockAndUpdate(new BlockPos(fp[0], y - 1, fp[1]), deepslate);
            level.setBlockAndUpdate(new BlockPos(fp[0], y,     fp[1]), fireflyBush);
        }
    }

    // ── Ocean Arena — restored original simple version ────────────────────────

    private static void generateOceanArena(ServerLevel level, int cx, int y, int cz, int r) {
        BlockState prismarine = Blocks.PRISMARINE.defaultBlockState();
        BlockState seaLantern = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState water      = Blocks.WATER.defaultBlockState();
        BlockState air        = Blocks.AIR.defaultBlockState();
        BlockState coral      = Blocks.TUBE_CORAL_BLOCK.defaultBlockState();

        // Prismarine floor with 1 block shallow water
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                level.setBlockAndUpdate(new BlockPos(x, y - 4, z), prismarine);
                level.setBlockAndUpdate(new BlockPos(x, y - 3, z), water);
                level.setBlockAndUpdate(new BlockPos(x, y - 2, z), water);
                level.setBlockAndUpdate(new BlockPos(x, y - 1, z), water);
                level.setBlockAndUpdate(new BlockPos(x, y,     z), water);
                for (int dy = 1; dy <= 10; dy++)
                    level.setBlockAndUpdate(new BlockPos(x, y + dy, z), air);
            }
        }

        // Sea lanterns in grid
        for (int x = cx - r + 4; x <= cx + r - 4; x += 8) {
            for (int z = cz - r + 4; z <= cz + r - 4; z += 8) {
                level.setBlockAndUpdate(new BlockPos(x, y - 4, z), seaLantern);
            }
        }

        // Coral decorations at edges
        java.util.Random rand = new java.util.Random(123);
        for (int i = 0; i < 15; i++) {
            int fx = cx + rand.nextInt(r * 2) - r;
            int fz = cz + rand.nextInt(r * 2) - r;
            if (Math.abs(fx - cx) > 8 || Math.abs(fz - cz) > 8)
                level.setBlockAndUpdate(new BlockPos(fx, y - 4, fz), coral);
        }
    }

    // ── Shared barrier walls ──────────────────────────────────────────────────

    private static void generateBarriers(ServerLevel level, int cx, int y, int cz, int r) {
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        int wall = r + 1;

        // Walls start at y-4 to cover the Ocean arena's underwater area
        for (int x = cx - wall; x <= cx + wall; x++) {
            for (int dy = -4; dy <= 11; dy++) {
                level.setBlockAndUpdate(new BlockPos(x, y + dy, cz - wall), barrier);
                level.setBlockAndUpdate(new BlockPos(x, y + dy, cz + wall), barrier);
            }
        }
        for (int z = cz - wall; z <= cz + wall; z++) {
            for (int dy = -4; dy <= 11; dy++) {
                level.setBlockAndUpdate(new BlockPos(cx - wall, y + dy, z), barrier);
                level.setBlockAndUpdate(new BlockPos(cx + wall, y + dy, z), barrier);
            }
        }
        for (int x = cx - wall; x <= cx + wall; x++) {
            for (int z = cz - wall; z <= cz + wall; z++) {
                level.setBlockAndUpdate(new BlockPos(x, y + 11, z), barrier);
            }
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    /** Clears the arena after game ends */
    public static void cleanupArena(ServerLevel level, ArenaType type) {
        int cx = 0;
        int cz = type.zOffset;
        int wall = ARENA_RADIUS + 1;
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int x = cx - wall; x <= cx + wall; x++) {
            for (int z = cz - wall; z <= cz + wall; z++) {
                for (int dy = -3; dy <= 12; dy++) {
                    level.setBlockAndUpdate(new BlockPos(x, ARENA_Y + dy, z), air);
                }
            }
        }

        // Reset weather when arena ends
        level.getWeatherData().setRaining(false);
        level.getWeatherData().setThundering(false);
        level.getWeatherData().setClearWeatherTime(6000);
    }

    // ── Teleport helpers ──────────────────────────────────────────────────────

    /**
     * First spawn — opposite corners facing the middle for dramatic face-off.
     * Night arena uses nightServerLevel, others use serverLevel.
     */
    public static void teleportToArena(ServerPlayer player, ArenaType type, boolean isHost) {
        ServerLevel targetLevel = (type == ArenaType.NIGHT) ? nightServerLevel : serverLevel;
        if (targetLevel == null) return;
        int cz = type.zOffset;
        int x    = isHost ? -SPAWN_OFFSET : SPAWN_OFFSET;
        int z    = isHost ? cz - SPAWN_OFFSET : cz + SPAWN_OFFSET;
        float yRot = isHost ? 45f : 225f;
        player.teleportTo(targetLevel, x, ARENA_Y + 1, z, Set.of(), yRot, 0f, false);
    }

    /**
     * Respawn — random position within arena bounds so it's unpredictable.
     */
    public static void teleportRespawn(ServerPlayer player, ArenaType type) {
        ServerLevel targetLevel = (type == ArenaType.NIGHT) ? nightServerLevel : serverLevel;
        if (targetLevel == null) return;
        java.util.Random rand = new java.util.Random();
        int x    = rand.nextInt((ARENA_RADIUS - 4) * 2) - (ARENA_RADIUS - 4);
        int z    = type.zOffset + rand.nextInt((ARENA_RADIUS - 4) * 2) - (ARENA_RADIUS - 4);
        float yRot = rand.nextFloat() * 360f;
        player.teleportTo(targetLevel, x, ARENA_Y + 1, z, Set.of(), yRot, 0f, false);
    }

    public static void teleportBack(ServerPlayer player, double[] savedPos, ServerLevel originalLevel) {
        if (savedPos == null || originalLevel == null) return;
        // Clear any arena rain we pushed to this client — the client's rainLevel is a single
        // global value, so a leftover RAIN_LEVEL_CHANGE 1.0 from the arena bleeds into the
        // overworld view until explicitly cleared.
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                net.minecraft.network.protocol.game.ClientboundGameEventPacket.STOP_RAINING, 0f));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                net.minecraft.network.protocol.game.ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 0f));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                net.minecraft.network.protocol.game.ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0f));
        player.teleportTo(originalLevel, savedPos[0], savedPos[1], savedPos[2],
                Set.of(), player.getYRot(), player.getXRot(), false);
    }

    /** Returns true if the given level is either brawl arena dimension */
    public static boolean isBrawlDimension(net.minecraft.world.level.Level level) {
        if (level == null) return false;
        return level.dimension().equals(DIMENSION_KEY) || level.dimension().equals(NIGHT_DIMENSION_KEY);
    }

    public static boolean isAvailable() {
        return serverLevel != null;
    }
}