package net.naw.morphling.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forces the off-hand to render an arm even when empty, for selected morphs.
 * Inspired by FirstPersonModel mod's "double hands" trick.
 * <p>
 * Vanilla normally skips off-hand rendering when the off-hand item is empty —
 * that's why you only see one hand when holding nothing. We override that for
 * morphs that look better with two visible hands/wings/fins.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    // Morphs where we always want two hands visible, even with empty off-hand

    @Shadow
    protected abstract void renderPlayerArm(PoseStack poseStack,
                                            SubmitNodeCollector submitNodeCollector,
                                            int lightCoords,
                                            float inverseArmHeight,
                                            float attackValue,
                                            HumanoidArm arm);

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void morphling$forceOffHandArm(AbstractClientPlayer player, float frameInterp, float xRot,
                                           InteractionHand hand, float attack, ItemStack itemStack,
                                           float inverseArmHeight, PoseStack poseStack,
                                           SubmitNodeCollector submitNodeCollector, int lightCoords,
                                           CallbackInfo ci) {
        // Only act when morphed into one of the chosen morphs
        if (!MorphState.isMorphed()) return;
        EntityType<?> morphType = MorphState.getCurrentMorph();
        if (!net.naw.morphling.client.config.TwoHandsConfig.shouldRenderSecondHand(morphType)) return;

        // Only intervene on the empty off-hand pass.
        // Main hand and held off-hand items run vanilla as normal.
        boolean isMainHand = hand == InteractionHand.MAIN_HAND;
        if (isMainHand) return;
        if (!itemStack.isEmpty()) return;
        if (player.isInvisible()) return;

        // Render an arm in the off-hand slot. Our AvatarRendererHandMixin will
        // intercept renderLeftHand/renderRightHand and swap in the morph's body part.
        HumanoidArm arm = player.getMainArm().getOpposite();
        poseStack.pushPose();
        renderPlayerArm(poseStack, submitNodeCollector, lightCoords, inverseArmHeight, attack, arm);
        poseStack.popPose();

        ci.cancel();
    }
}