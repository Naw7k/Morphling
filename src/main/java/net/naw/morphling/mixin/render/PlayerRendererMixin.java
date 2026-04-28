package net.naw.morphling.mixin.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.mixin.accessors.WalkAnimationStateAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.naw.morphling.client.compat.FpmCompat;

@Mixin(LivingEntityRenderer.class)
public abstract class PlayerRendererMixin {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void morphling$onSubmit(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, CallbackInfo ci) {
        if (!MorphState.isMorphed()) return;
        if (!(state instanceof AvatarRenderState)) return;

        FpmCompat.restoreHeadsIfNeeded();

        Entity morphEntity = MorphState.getCachedEntity();

        if (morphEntity == null) {
            ci.cancel();
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            ci.cancel();
            return;
        }

        // Sync morph entity position/rotation with player
        morphEntity.setPos(client.player.getX(), client.player.getY(), client.player.getZ());
        morphEntity.xOld = client.player.getX();
        morphEntity.yOld = client.player.getY();
        morphEntity.zOld = client.player.getZ();
        morphEntity.setYRot(client.player.getYRot());
        morphEntity.setXRot(client.player.getXRot());
        morphEntity.yRotO = client.player.getYRot();
        morphEntity.xRotO = client.player.getXRot();
        morphEntity.setYHeadRot(client.player.getYHeadRot());
        morphEntity.setOnGround(client.player.onGround());

        if (morphEntity instanceof LivingEntity livingMorph) {
            livingMorph.yBodyRot = client.player.yBodyRot;
            livingMorph.yBodyRotO = client.player.yBodyRotO;
            livingMorph.yHeadRotO = client.player.yHeadRotO;
            livingMorph.attackAnim = client.player.attackAnim;
            livingMorph.oAttackAnim = client.player.oAttackAnim;
            livingMorph.swinging = client.player.swinging;
            livingMorph.swingTime = client.player.swingTime;

            livingMorph.setDeltaMovement(client.player.getDeltaMovement());
            livingMorph.swinging = client.player.swinging;
            livingMorph.swingTime = client.player.swingTime;
            livingMorph.swingingArm = client.player.swingingArm;
            livingMorph.setPose(client.player.getPose());
            livingMorph.hurtTime = client.player.hurtTime;


            // Perfectly clone walk animation state from player
            WalkAnimationStateAccessor playerAnim = (WalkAnimationStateAccessor)(Object) client.player.walkAnimation;
            WalkAnimationStateAccessor morphAnim = (WalkAnimationStateAccessor)(Object) livingMorph.walkAnimation;
            morphAnim.morphling$setSpeed(playerAnim.morphling$getSpeed());
            morphAnim.morphling$setSpeedOld(playerAnim.morphling$getSpeedOld());
            morphAnim.morphling$setPosition(playerAnim.morphling$getPosition());



            // Skeleton bow visual when bow equipped
            if (morphEntity instanceof net.minecraft.world.entity.monster.skeleton.Skeleton skeleton) {
                if (net.naw.morphling.client.abilities.SkeletonAbility.isBowEquipped()) {
                    skeleton.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));

                    if (client.player.isUsingItem() && client.player.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem) {
                        skeleton.setAggressive(true);
                    } else {
                        skeleton.setAggressive(false);
                    }
                } else {
                    skeleton.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                            net.minecraft.world.item.ItemStack.EMPTY);
                    skeleton.setAggressive(false);
                }
            }

        }


        // Creeper swell visual when priming ability
        if (morphEntity instanceof net.minecraft.world.entity.monster.Creeper creeper) {
            if (!client.isPaused()) {
                net.naw.morphling.mixin.accessors.CreeperSwellAccessor accessor =
                        (net.naw.morphling.mixin.accessors.CreeperSwellAccessor)(Object) creeper;

                float progress = net.naw.morphling.client.abilities.CreeperAbility.getSwellProgress();
                int currentSwell = accessor.morphling$getSwell();
                int targetSwell = (int)(progress * 28);

                accessor.morphling$setOldSwell(currentSwell);
                accessor.morphling$setSwell(targetSwell);
            }
        }

        try {
            EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
            EntityRenderer renderer = dispatcher.getRenderer(morphEntity);
            float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            EntityRenderState morphState = renderer.createRenderState(morphEntity, partialTick);
            morphState.lightCoords = state.lightCoords;

            // Slow parrot dance animation — ageInTicks drives the wobble, cap its speed
            if (morphEntity instanceof net.minecraft.world.entity.animal.parrot.Parrot p && p.isPartyParrot()) {
                if (morphState instanceof net.minecraft.client.renderer.entity.state.ParrotRenderState ps) {
                    ps.ageInTicks = (client.player.tickCount + partialTick);
                }
            }

            // ender man shake — translate pose stack directly
            boolean doShake = morphEntity instanceof net.minecraft.world.entity.monster.EnderMan
                    && net.naw.morphling.client.abilities.EndermanMadMode.isActive();
            if (doShake) {
                double d = 0.02;
                java.util.Random rng = new java.util.Random();
                poseStack.pushPose();
                poseStack.translate(rng.nextGaussian() * d, 0, rng.nextGaussian() * d);
            }

            FpmCompat.hideHeadIfNeeded(morphEntity, state);

            renderer.submit(morphState, poseStack, submitNodeCollector, camera);

            if (doShake) {
                poseStack.popPose();
            }


        } catch (Exception e) {
            // swallow render errors
        }

        ci.cancel();
    }
}