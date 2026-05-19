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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Set;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererHandMixin {

    // Mobs where we render no hand at all
    // (Chicken & Parrot used to be here — now they render wings instead)
    @Unique
    private static final Set<EntityType<?>> NO_HAND_MOBS = Set.of(
            // empty for now
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
    @Unique
    private static boolean handleMorphArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                          int lightCoords, boolean rightSide, CallbackInfo ignoredCi) {
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
        ModelPart arm = findArmPart(model, rightSide, livingMorph.getType());
        if (arm == null) return false;

        Identifier texture = getTextureForMorph(livingMorph, livingRenderer);
        if (texture == null) return false;

        // Save and reset arm state for clean display.
        // Skip the reset for chicken/parrot wings so the flap animation
        // (set by setupAnim from the live flap/flapSpeed values) carries
        // into first person.
        EntityType<?> typeForReset = livingMorph.getType();
        boolean keepFlap = typeForReset == EntityType.CHICKEN || typeForReset == EntityType.PARROT;
        if (!keepFlap) {
            arm.resetPose();
        } else {
            // First person doesn't trigger setupAnim, so the wing rotation
            // would freeze. Drive it manually from the entity's live flap state.
            applyWingFlap(arm, livingMorph, rightSide);
        }
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

        // Iron Golem poppy visual — render a poppy at the hand when offering flower
        if (morphType == EntityType.IRON_GOLEM
                && net.naw.morphling.client.abilities.IronGolemAbility.isOfferingFlower()) {
            net.naw.morphling.client.debug.HeldItemTuner.Offset poppyOff =
                    net.naw.morphling.client.debug.HeldItemTuner.poppy;
            poseStack.pushPose();
            poseStack.translate(poppyOff.x, poppyOff.y, poppyOff.z);
            poseStack.scale(poppyOff.scale, poppyOff.scale, poppyOff.scale);
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(poppyOff.rotX));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(poppyOff.rotY));
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(poppyOff.rotZ));
            net.minecraft.world.item.ItemStack poppyStack =
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POPPY);
            mc.gameRenderer.itemInHandRenderer.renderItem(
                    livingMorph,
                    poppyStack,
                    net.minecraft.world.item.ItemDisplayContext.GROUND,
                    poseStack,
                    submitNodeCollector,
                    lightCoords
            );
            poseStack.popPose();
        }

        // Enderman carry block visual — render the carried block at the hand
        if (morphType == EntityType.ENDERMAN
                && livingMorph instanceof net.minecraft.world.entity.monster.EnderMan enderman
                && enderman.getCarriedBlock() != null) {
            net.minecraft.world.level.block.state.BlockState carried = enderman.getCarriedBlock();
            net.naw.morphling.client.debug.HeldItemTuner.Offset blockOff =
                    net.naw.morphling.client.debug.HeldItemTuner.endermanBlock;
            poseStack.pushPose();
            poseStack.translate(blockOff.x, blockOff.y, blockOff.z);
            poseStack.scale(blockOff.scale, blockOff.scale, blockOff.scale);
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(blockOff.rotX));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(blockOff.rotY));
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(blockOff.rotZ));
            net.minecraft.world.item.ItemStack blockStack =
                    new net.minecraft.world.item.ItemStack(carried.getBlock().asItem());
            mc.gameRenderer.itemInHandRenderer.renderItem(
                    livingMorph,
                    blockStack,
                    net.minecraft.world.item.ItemDisplayContext.GROUND,
                    poseStack,
                    submitNodeCollector,
                    lightCoords
            );
            poseStack.popPose();
        }

        poseStack.popPose();

        return true;
    }


    @Unique
    private static ModelPart findArmPart(EntityModel<?> model, boolean rightSide, EntityType<?> morphType) {
        // Chicken & parrot — use wings as hands.
        // Their model classes have leftWing/rightWing fields directly, so we
        // target those explicitly to avoid picking up legs first.
        if (morphType == EntityType.CHICKEN || morphType == EntityType.PARROT) {
            String wingField = rightSide ? "rightWing" : "leftWing";
            ModelPart wing = getFieldPart(model, wingField);
            if (wing != null) return wing;
        }

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

    /**
     * Manually applies the wing flap rotation in first person.
     * setupAnim only runs when the third-person model is actively rendered,
     * so in first person the wing rotation freezes. This drives it from the
     * cached entity's live flap/flapSpeed values every frame.

     * Uses partial-tick interpolation between the previous tick (oFlap/oFlapSpeed)
     * and current tick (flap/flapSpeed) so the animation is smooth at high FPS
     * instead of stepping at 20Hz.
     */
    @Unique
    private static void applyWingFlap(ModelPart wing, LivingEntity morph, boolean rightSide) {
        wing.resetPose();
        try {
            float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);

            if (morph instanceof net.minecraft.world.entity.animal.chicken.Chicken chicken) {
                float flap = net.minecraft.util.Mth.lerp(partialTick, chicken.oFlap, chicken.flap);
                float flapSpeed = net.minecraft.util.Mth.lerp(partialTick, chicken.oFlapSpeed, chicken.flapSpeed);
                // Match ChickenModel.setupAnim:
                //   float flapAngle = (sin(flap) + 1) * flapSpeed
                //   rightWing.zRot = flapAngle
                //   leftWing.zRot  = -flapAngle
                float flapAngle = (net.minecraft.util.Mth.sin(flap) + 1.0F) * flapSpeed;
                wing.zRot = rightSide ? flapAngle : -flapAngle;
            } else if (morph instanceof net.minecraft.world.entity.animal.parrot.Parrot parrot) {
                float flap = net.minecraft.util.Mth.lerp(partialTick, parrot.oFlap, parrot.flap);
                float flapSpeed = net.minecraft.util.Mth.lerp(partialTick, parrot.oFlapSpeed, parrot.flapSpeed);
                // Match ParrotModel.setupAnim flying/standing branch:
                //   leftWing.zRot  = -0.0873 - flapAngle
                //   rightWing.zRot =  0.0873 + flapAngle
                float flapAngle = (net.minecraft.util.Mth.sin(flap) + 1.0F) * flapSpeed;
                wing.zRot = rightSide ? (0.0873F + flapAngle) : (-0.0873F - flapAngle);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Helper — look up a single field by name on a model and return it as a ModelPart.
     */
    @Unique
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

    @Unique
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