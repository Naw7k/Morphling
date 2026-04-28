package net.naw.morphling.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;

public class FpmCompat {

    public static void restoreHeadsIfNeeded() {
        // No-op — head hiding now handled by per-model mixins
    }

    public static void hideHeadIfNeeded(Entity morphEntity, LivingEntityRenderState state) {
        // No-op — head hiding now handled by per-model mixins
    }

    public static boolean shouldHideHeadNow() {
        if (!isFpmActive()) return false;
        if (!isFirstPerson()) return false;
        Entity morph = net.naw.morphling.client.core.MorphState.getCachedEntity();
        return morph != null;
    }

    private static boolean isFpmActive() {
        try {
            Class<?> coreCls = Class.forName("dev.tr7zw.firstperson.FirstPersonModelCore");
            Object instance = coreCls.getField("instance").get(null);
            Object logic = coreCls.getMethod("getLogicHandler").invoke(instance);
            return (Boolean) logic.getClass()
                    .getMethod("shouldApplyThirdPerson", boolean.class)
                    .invoke(logic, false);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isFirstPerson() {
        return Minecraft.getInstance().options.getCameraType() == net.minecraft.client.CameraType.FIRST_PERSON;
    }
}