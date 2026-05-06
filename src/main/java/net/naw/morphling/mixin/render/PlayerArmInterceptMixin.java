package net.naw.morphling.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * Intercepts ItemInHandRenderer.renderPlayerArm so that when we're morphed
 * into parrot/chicken/dolphin and FPM-style "two hand" rendering is forced,
 * we render the morph's wing/fin instead of the vanilla player arm.
 *
 * Zombie isn't in this list because it uses the humanoid arm directly and
 * vanilla rendering already works for it.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class PlayerArmInterceptMixin {

    private static final Set<EntityType<?>> SWAP_MORPHS = Set.of(
            EntityType.PARROT,
            EntityType.CHICKEN,
            EntityType.DOLPHIN
    );

    @Inject(method = "renderPlayerArm", at = @At("HEAD"), cancellable = true)
    private void morphling$swapInMorphArm(PoseStack poseStack,
                                          SubmitNodeCollector submitNodeCollector,
                                          int lightCoords,
                                          float equipProgress,
                                          float swingProgress,
                                          HumanoidArm arm,
                                          CallbackInfo ci) {
        if (!MorphState.isMorphed()) return;
        EntityType<?> morphType = MorphState.getCurrentMorph();
        if (!net.naw.morphling.client.config.TwoHandsConfig.shouldRenderSecondHand(morphType)) return;
        if (morphType == EntityType.ZOMBIE) return; // zombie uses vanilla arm, no swap needed

        Entity morph = MorphState.getCachedEntity();
        if (!(morph instanceof LivingEntity livingMorph)) return;

        Minecraft mc = Minecraft.getInstance();
        EntityRenderer<?, ?> renderer = mc.getEntityRenderDispatcher().getRenderer(livingMorph);
        if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer)) return;

        EntityModel<?> model = livingRenderer.getModel();
        boolean rightSide = arm == HumanoidArm.RIGHT;

        ModelPart part = findMorphPart(model, rightSide, morphType);
        if (part == null) return;

        Identifier texture = getTextureForMorph(livingMorph, livingRenderer);
        if (texture == null) return;

        // Apply the same arm transforms vanilla renderPlayerArm uses, then add
        // our hand placement offset so the morph part lands in the right spot.
        boolean bl = arm != HumanoidArm.LEFT;
        float f = bl ? 1.0F : -1.0F;
        float g = Mth.sqrt(swingProgress);
        float h = -0.3F * Mth.sin(g * (float) Math.PI);
        float i = 0.4F * Mth.sin(g * ((float) Math.PI * 2));
        float j = -0.4F * Mth.sin(swingProgress * (float) Math.PI);

        poseStack.pushPose();
        poseStack.translate(f * (h + 0.64000005F), i - 0.6F + equipProgress * -0.6F, j - 0.71999997F);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(f * 45.0F));
        float k = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        float l = Mth.sin(g * (float) Math.PI);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(f * l * 70.0F));
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(f * k * -20.0F));

        // Our hand placement offset
        net.naw.morphling.client.config.TwoHandsConfig.Offset offset =
                net.naw.morphling.client.config.TwoHandsConfig.getOffset(morphType);
        poseStack.translate(offset.x, offset.y, offset.z);

        // For chicken/parrot wings, drive flap from live values like the other mixin does
        if (morphType == EntityType.CHICKEN || morphType == EntityType.PARROT) {
            applyWingFlap(part, livingMorph, rightSide);
        } else {
            part.resetPose();
        }
        part.visible = true;

        submitNodeCollector.submitModelPart(
                part, poseStack,
                RenderTypes.entityTranslucent(texture),
                lightCoords, OverlayTexture.NO_OVERLAY, null
        );

        poseStack.popPose();

        ci.cancel();
    }

    private static ModelPart findMorphPart(EntityModel<?> model, boolean rightSide, EntityType<?> morphType) {
        // Wing for chicken/parrot
        if (morphType == EntityType.CHICKEN || morphType == EntityType.PARROT) {
            String wingField = rightSide ? "rightWing" : "leftWing";
            return getFieldPart(model, wingField);
        }
        // Fin for dolphin (vanilla model has body -> right_fin / left_fin)
        if (morphType == EntityType.DOLPHIN) {
            try {
                Field bodyField = model.getClass().getDeclaredField("body");
                bodyField.setAccessible(true);
                Object body = bodyField.get(model);
                if (body instanceof ModelPart bodyPart) {
                    String childName = rightSide ? "right_fin" : "left_fin";
                    return bodyPart.getChild(childName);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static ModelPart getFieldPart(EntityModel<?> model, String fieldName) {
        Class<?> currentClass = model.getClass();
        while (currentClass != null && currentClass != Object.class) {
            try {
                Field field = currentClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(model);
                if (value instanceof ModelPart mp) return mp;
            } catch (NoSuchFieldException | IllegalAccessException ignored) {}
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }

    private static void applyWingFlap(ModelPart wing, LivingEntity morph, boolean rightSide) {
        wing.resetPose();
        try {
            float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
            if (morph instanceof net.minecraft.world.entity.animal.chicken.Chicken chicken) {
                float flap = Mth.lerp(partialTick, chicken.oFlap, chicken.flap);
                float flapSpeed = Mth.lerp(partialTick, chicken.oFlapSpeed, chicken.flapSpeed);
                float flapAngle = (Mth.sin(flap) + 1.0F) * flapSpeed;
                wing.zRot = rightSide ? flapAngle : -flapAngle;
            } else if (morph instanceof net.minecraft.world.entity.animal.parrot.Parrot parrot) {
                float flap = Mth.lerp(partialTick, parrot.oFlap, parrot.flap);
                float flapSpeed = Mth.lerp(partialTick, parrot.oFlapSpeed, parrot.flapSpeed);
                float flapAngle = (Mth.sin(flap) + 1.0F) * flapSpeed;
                wing.zRot = rightSide ? (0.0873F + flapAngle) : (-0.0873F - flapAngle);
            }
        } catch (Exception ignored) {}
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