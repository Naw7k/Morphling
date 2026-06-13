package net.naw.morphling.client.games.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.EntityType;
import java.util.Map;

/**
 * Per-morph face icon render values used in RoomBrowserScreen and MorphTriviaScreen.
 * Defines offsetY and scale per mob type so face icons look correctly framed.
 * Call renderPlayerFace() statically from any screen that needs player face icons.
 * Add new entries to CONFIG as more morphs are added to Morphling.
 */
public class MorphFaceRenderConfig {

    public record FaceValues(float offsetY, int scale) {}

    /** Fallback used for any morph not explicitly listed. */
    private static final FaceValues DEFAULT = new FaceValues(-0.04f, 16);

    // Shared render box offsets — same for all morphs
    private static final int X1 = -8, Y1 = -40, X2 = 16, Y2 = 72;

    private static final Map<EntityType<?>, FaceValues> CONFIG = Map.ofEntries(
            Map.entry(EntityType.CHICKEN,    new FaceValues(-0.01f, 28)),
            Map.entry(EntityType.COW,        new FaceValues(-0.19f, 16)),
            Map.entry(EntityType.PIG,        new FaceValues(-0.44f, 16)),
            Map.entry(EntityType.SHEEP,      new FaceValues(-0.14f, 18)),
            Map.entry(EntityType.CAT,        new FaceValues(-0.29f, 28)),
            Map.entry(EntityType.WOLF,       new FaceValues(-0.44f, 17)),
            Map.entry(EntityType.PARROT,     new FaceValues(-0.24f, 33)),
            Map.entry(EntityType.HORSE,      new FaceValues(0.16f, 12)),
            Map.entry(EntityType.VILLAGER,   new FaceValues(-0.04f, 16)),
            Map.entry(EntityType.IRON_GOLEM, new FaceValues( 0.21f, 15)),
            Map.entry(EntityType.DOLPHIN,    new FaceValues(-0.64f, 17)),
            Map.entry(EntityType.BEE,        new FaceValues(-0.64f, 17)),
            Map.entry(EntityType.ZOMBIE,     new FaceValues( 0.06f, 17)),
            Map.entry(EntityType.SKELETON,   new FaceValues( 0.06f, 17)),
            Map.entry(EntityType.CREEPER,    new FaceValues(-0.19f, 17)),
            Map.entry(EntityType.SPIDER,     new FaceValues(-0.69f, 14)),
            Map.entry(EntityType.ENDERMAN,   new FaceValues( 0.46f, 18)),
            Map.entry(EntityType.SLIME,      new FaceValues(-1.89f,  6)),
            Map.entry(EntityType.FOX,        new FaceValues(-0.29f, 22)),
            Map.entry(EntityType.RABBIT,     new FaceValues(-0.14f, 22)),
            Map.entry(EntityType.AXOLOTL,    new FaceValues(-0.44f, 16)),
            Map.entry(EntityType.FROG,       new FaceValues(-0.44f, 22)),
            Map.entry(EntityType.POLAR_BEAR, new FaceValues(-0.44f, 22)),
            Map.entry(EntityType.PANDA,      new FaceValues(-0.44f, 22))
    );

    public static FaceValues get(EntityType<?> type) {
        return CONFIG.getOrDefault(type, DEFAULT);
    }

    /**
     * Renders a player's face icon (or their current morph) at the given screen position.
     * Clips to an 8x8 box at (x, y). Safe to call from any GUI screen.
     */
    public static void renderPlayerFace(GuiGraphicsExtractor graphics, String playerName, int x, int y) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (var p : level.players()) {
            if (!p.getName().getString().equals(playerName)) continue;

            net.minecraft.world.entity.LivingEntity renderEntity = p;

            if (p == Minecraft.getInstance().player) {
                // Local player — use MorphState
                var localMorph = net.naw.morphling.client.core.MorphState.getCachedEntity();
                if (localMorph instanceof net.minecraft.world.entity.LivingEntity living) renderEntity = living;
            } else {
                // Remote player — use RemoteMorphState
                var morphData = net.naw.morphling.client.core.RemoteMorphState.get(p.getUUID());
                if (morphData != null && morphData.cachedEntity instanceof net.minecraft.world.entity.LivingEntity living) renderEntity = living;
            }

            FaceValues fv = get(renderEntity.getType());

            try {
                float savedYaw      = renderEntity.getYRot();
                float savedYawO     = renderEntity.yRotO;
                float savedPitch    = renderEntity.getXRot();
                float savedPitchO   = renderEntity.xRotO;
                float savedBodyRot  = renderEntity.yBodyRot;
                float savedBodyRotO = renderEntity.yBodyRotO;
                float savedHeadRot  = renderEntity.yHeadRot;
                float savedHeadRotO = renderEntity.yHeadRotO;

                renderEntity.setYRot(0f);   renderEntity.yRotO    = 0f;
                renderEntity.setXRot(0f);   renderEntity.xRotO    = 0f;
                renderEntity.yBodyRot  = 0f; renderEntity.yBodyRotO = 0f;
                renderEntity.yHeadRot  = 0f; renderEntity.yHeadRotO = 0f;

                graphics.enableScissor(x, y, x + 8, y + 8);
                InventoryScreen.extractEntityInInventoryFollowsMouse(
                        graphics, x + X1, y + Y1, x + X2, y + Y2,
                        fv.scale(), fv.offsetY(),
                        (float) (x + X1 + x + X2) / 2,
                        (float) (y + Y1 + y + Y2) / 2,
                        renderEntity);
                graphics.disableScissor();

                renderEntity.setYRot(savedYaw);      renderEntity.yRotO    = savedYawO;
                renderEntity.setXRot(savedPitch);    renderEntity.xRotO    = savedPitchO;
                renderEntity.yBodyRot  = savedBodyRot;  renderEntity.yBodyRotO = savedBodyRotO;
                renderEntity.yHeadRot  = savedHeadRot;  renderEntity.yHeadRotO = savedHeadRotO;
            } catch (Exception ignored) {}
            return;
        }
    }
}