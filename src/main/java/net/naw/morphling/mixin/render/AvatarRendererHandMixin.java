package net.naw.morphling.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Set;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererHandMixin {

    // Mobs where we render no hand at all
    private static final Set<EntityType<?>> NO_HAND_MOBS = Set.of(
            EntityType.CHICKEN,
            EntityType.PARROT
    );

    @Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true)
    private void morphling$renderMorphRightHand(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                                int lightCoords, Identifier skinTexture, boolean hasSleeve,
                                                CallbackInfo ci) {
        if (handleMorphArm(poseStack, submitNodeCollector, lightCoords, true, ci)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
    private void morphling$renderMorphLeftHand(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                               int lightCoords, Identifier skinTexture, boolean hasSleeve,
                                               CallbackInfo ci) {
        if (handleMorphArm(poseStack, submitNodeCollector, lightCoords, false, ci)) {
            ci.cancel();
        }
    }

    /**
     * Returns true if we handled the render (vanilla should cancel).
     */
    private static boolean handleMorphArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                          int lightCoords, boolean rightSide, CallbackInfo ci) {
        if (!MorphState.isMorphed()) return false;
        Entity morph = MorphState.getCachedEntity();
        if (!(morph instanceof LivingEntity livingMorph)) return false;

        // No-hand mobs: suppress rendering entirely (we cancel vanilla without drawing anything)
        if (NO_HAND_MOBS.contains(morph.getType())) {
            return true;
        }

        Minecraft mc = Minecraft.getInstance();
        EntityRenderer<?, ?> renderer = mc.getEntityRenderDispatcher().getRenderer(livingMorph);
        if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer)) return false;

        EntityModel<?> model = livingRenderer.getModel();
        ModelPart arm = findArmPart(model, rightSide);
        if (arm == null) return false;

        Identifier texture = getTextureForMorph(livingMorph, livingRenderer);
        if (texture == null) return false;

        // Save and reset arm state for clean display
        arm.resetPose();
        arm.visible = true;

        // Per-mob positioning adjustments — read from live config
        EntityType<?> morphType = livingMorph.getType();

        poseStack.pushPose();
        net.naw.morphling.client.config.HandPlacementConfig.Offset offset =
                net.naw.morphling.client.config.HandPlacementConfig.getOrDefault(morphType);
        poseStack.translate(offset.x, offset.y, offset.z);

        submitNodeCollector.submitModelPart(
                arm, poseStack,
                RenderTypes.entityTranslucent(texture),
                lightCoords, OverlayTexture.NO_OVERLAY, null
        );

        poseStack.popPose();

        return true;
    }

    private static ModelPart findArmPart(EntityModel<?> model, boolean rightSide) {
        if (model instanceof HumanoidModel<?> humanoid) {
            return rightSide ? humanoid.rightArm : humanoid.leftArm;
        }

        String[] candidateFieldNames = rightSide
                ? new String[]{"rightArm", "rightFrontLeg", "rightLeg", "arm", "frontRightLeg", "rightFront"}
                : new String[]{"leftArm", "leftFrontLeg", "leftLeg", "arm", "frontLeftLeg", "leftFront"};

        Class<?> currentClass = model.getClass();
        while (currentClass != null && currentClass != Object.class) {
            for (String fieldName : candidateFieldNames) {
                try {
                    Field field = currentClass.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(model);
                    if (value instanceof ModelPart mp) return mp;
                } catch (NoSuchFieldException | IllegalAccessException ignored) {}
            }
            currentClass = currentClass.getSuperclass();
        }

        // Special case: try to find fins via body's child (dolphin)
        try {
            Field bodyField = model.getClass().getDeclaredField("body");
            bodyField.setAccessible(true);
            Object body = bodyField.get(model);
            if (body instanceof ModelPart bodyPart) {
                String childName = rightSide ? "right_fin" : "left_fin";
                return bodyPart.getChild(childName);
            }
        } catch (Exception ignored) {}

        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Identifier getTextureForMorph(LivingEntity morph, LivingEntityRenderer renderer) {
        try {
            var state = renderer.createRenderState();
            if (!(state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState livingState)) {
                return null;
            }
            renderer.extractRenderState(morph, livingState, 1.0F);
            return renderer.getTextureLocation(livingState);
        } catch (Exception e) {
            return null;
        }
    }
}