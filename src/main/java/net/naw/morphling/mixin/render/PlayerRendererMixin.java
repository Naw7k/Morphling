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
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.client.abilities.EndermanMadMode;
import net.naw.morphling.client.abilities.SkeletonAbility;
import net.naw.morphling.client.abilities.CreeperAbility;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.client.core.RemoteMorphState;
import net.naw.morphling.mixin.accessors.CreeperSwellAccessor;
import net.naw.morphling.mixin.accessors.WalkAnimationStateAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.naw.morphling.client.compat.FpmCompat;

import java.util.UUID;

@Mixin(LivingEntityRenderer.class)
public abstract class PlayerRendererMixin {

    /**
     * Frog morph moves at baseScale 0.01 (vs player 0.1), so the displacement-driven
     * walk animation values are ~10x too small for FROG_WALK to be visible.
     * Speed and position are both amplified by this factor (position is the phase
     * accumulator — scaling both by the same constant keeps the animation consistent).
     */
    @Unique
    private static final float morphling$FROG_WALK_BOOST = 8.0F;

    /**
     * Swim speed isn't reduced by the frog's 0.01 ground-movement baseScale the way
     * walking is, so displacement in water is near-normal and the walk boost makes
     * FROG_SWIM paddle hyperactively. Water uses this gentler multiplier instead.
     * Tune: drop toward 1.0F if still frantic, nudge toward 2.5F if sluggish.
     */
    @Unique
    private static final float morphling$FROG_SWIM_BOOST = 1.5F;

    @Unique
    private static final java.util.Random morphling$SHAKE_RNG = new java.util.Random();

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void morphling$onSubmit(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState avatarState)) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        boolean isLocalPlayer = avatarState.id == client.player.getId();

        if (isLocalPlayer) {
            renderLocalPlayer(avatarState, poseStack, submitNodeCollector, camera, client, ci);
        } else {
            // Skip any invisible remote player entirely (clean hide, not vanilla's faint
            // outline). Covers the Mob Brawl 3s death window — server sets the player
            // invisible and the flag syncs here, whether or not they're morphed.
            net.minecraft.world.entity.player.Player rp = null;
            if (client.level != null) {
                for (net.minecraft.world.entity.player.Player p : client.level.players()) {
                    if (p.getId() == avatarState.id) { rp = p; break; }
                }
            }
            if (rp != null && rp.isInvisible()) { ci.cancel(); return; }

            renderRemotePlayer(avatarState, poseStack, submitNodeCollector, camera, client, ci);
        }
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void renderLocalPlayer(AvatarRenderState state, PoseStack poseStack,
                                   SubmitNodeCollector submitNodeCollector, CameraRenderState camera,
                                   Minecraft client, CallbackInfo ci) {
        if (!MorphState.isMorphed()) return;
        if (client.player == null || client.player.isSpectator()) return;

        // Roulette blink — skip render during invisible phase
        if (!net.naw.morphling.client.games.MorphRoulette.MorphRouletteGame.getInstance().isBlinkVisible()) {
            ci.cancel();
            return;
        }

        FpmCompat.restoreHeadsIfNeeded();

        Entity morphEntity = MorphState.getCachedEntity();
        if (morphEntity == null) { ci.cancel(); return; }

        // Sync morph entity position/rotation with player
        assert client.player != null;
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
            livingMorph.swingingArm = client.player.swingingArm;
            livingMorph.setPose(client.player.getPose());
            livingMorph.hurtTime = client.player.hurtTime;

            WalkAnimationStateAccessor playerAnim = (WalkAnimationStateAccessor) client.player.walkAnimation;
            WalkAnimationStateAccessor morphAnim = (WalkAnimationStateAccessor) livingMorph.walkAnimation;

            boolean isFrog = morphEntity instanceof net.minecraft.world.entity.animal.frog.Frog;
            // Water uses a gentler boost — swim displacement is near-normal unlike ground movement.
            // FROG_IDLE_WATER (swimIdleAnimationState) is unaffected by walkBoost entirely.
            float walkBoost = isFrog
                    ? (client.player.isInWater() ? morphling$FROG_SWIM_BOOST : morphling$FROG_WALK_BOOST)
                    : 1.0F;

            if (isFrog && !client.player.onGround() && !client.player.isInWater()) {
                // Airborne frog (leap/fall): no walk cycle — jump animation handles the pose
                morphAnim.morphling$setSpeed(0.0F);
                morphAnim.morphling$setSpeedOld(0.0F);
                morphAnim.morphling$setPosition(playerAnim.morphling$getPosition() * walkBoost);
            } else {
                morphAnim.morphling$setSpeed(Math.min(playerAnim.morphling$getSpeed() * walkBoost, 1.0F));
                morphAnim.morphling$setSpeedOld(Math.min(playerAnim.morphling$getSpeedOld() * walkBoost, 1.0F));
                morphAnim.morphling$setPosition(playerAnim.morphling$getPosition() * walkBoost);
            }

            // Skeleton bow visual
            if (morphEntity instanceof net.minecraft.world.entity.monster.skeleton.Skeleton skeleton) {
                if (SkeletonAbility.isBowEquipped()) {
                    skeleton.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
                    skeleton.setAggressive(client.player.isUsingItem() &&
                            client.player.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem);
                } else {
                    skeleton.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                            net.minecraft.world.item.ItemStack.EMPTY);
                    skeleton.setAggressive(false);
                }
            }
        }

        // Creeper swell visual
        if (morphEntity instanceof net.minecraft.world.entity.monster.Creeper creeper) {
            if (!client.isPaused()) {
                CreeperSwellAccessor accessor = (CreeperSwellAccessor) creeper;
                float progress = CreeperAbility.getSwellProgress();
                int targetSwell = (int)(progress * 28);
                accessor.morphling$setOldSwell(accessor.morphling$getSwell());
                accessor.morphling$setSwell(targetSwell);
            }
        }

        // Fox mouth item — mirror player's main hand onto cached fox
        if (morphEntity instanceof net.minecraft.world.entity.animal.fox.Fox fox) {
            fox.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    client.player.getMainHandItem());
        }

        // Rabbit hop animation — driven by RabbitAbility (client tick).
        // Renderer only REFLECTS state; physics/timing live in the tick.
        // R key toggles the idle head tilt (sitting) animation manually.
        if (morphEntity instanceof net.minecraft.world.entity.animal.rabbit.Rabbit rabbit) {
            if (net.naw.morphling.client.abilities.RabbitAbility.isHopping()) {
                rabbit.hopAnimationState.startIfStopped(client.player.tickCount);
                rabbit.idleHeadTiltAnimationState.stop();
            } else {
                rabbit.hopAnimationState.stop();
                if (net.naw.morphling.client.abilities.RabbitAbility.isSitting()) {
                    rabbit.idleHeadTiltAnimationState.startIfStopped(client.player.tickCount);
                } else {
                    rabbit.idleHeadTiltAnimationState.stop();
                }
            }
        }

        try {
            EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
            EntityRenderer renderer = dispatcher.getRenderer(morphEntity);
            float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);

            EntityRenderState morphState = renderer.createRenderState(morphEntity, partialTick);
            morphState.lightCoords = state.lightCoords;

            // Frog swim state
            if (morphState instanceof net.minecraft.client.renderer.entity.state.FrogRenderState frs) {
                frs.isSwimming = client.player.isInWater();
            }

            // Panda variant — drives texture selection in PandaRenderer
            if (morphState instanceof net.minecraft.client.renderer.entity.state.PandaRenderState prs
                    && morphEntity instanceof net.minecraft.world.entity.animal.panda.Panda panda) {
                prs.variant = panda.getVariant();
            }

            if (morphEntity instanceof net.minecraft.world.entity.animal.parrot.Parrot p && p.isPartyParrot()) {
                if (morphState instanceof net.minecraft.client.renderer.entity.state.ParrotRenderState ps) {
                    ps.ageInTicks = (client.player.tickCount + partialTick);
                }
            }

            // Rabbit hop completion float — drives body pitch through jump arc
            if (morphState instanceof net.minecraft.client.renderer.entity.state.RabbitRenderState rs) {
                rs.jumpCompletion = client.player.onGround()
                        ? 0.0F
                        : (float) Math.clamp(0.5 + client.player.getDeltaMovement().y * 0.8, 0.0, 1.0);
            }

            boolean doShake = morphEntity instanceof net.minecraft.world.entity.monster.EnderMan
                    && EndermanMadMode.isActive();
            if (doShake) {
                double d = 0.02;
                poseStack.pushPose();
                poseStack.translate(morphling$SHAKE_RNG.nextGaussian() * d, 0, morphling$SHAKE_RNG.nextGaussian() * d);
            }

            FpmCompat.hideHeadIfNeeded();
            renderer.submit(morphState, poseStack, submitNodeCollector, camera);

            if (doShake) poseStack.popPose();

        } catch (Exception e) {
            // swallow render errors
        }

        ci.cancel();
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void renderRemotePlayer(AvatarRenderState state, PoseStack poseStack,
                                    SubmitNodeCollector submitNodeCollector, CameraRenderState camera,
                                    Minecraft client, CallbackInfo ci) {
        // Find which player this is by looking up their ID in the world
        Player remotePlayer = null;
        if (client.level != null) {
            for (Player p : client.level.players()) {
                if (p.getId() == state.id) {
                    remotePlayer = p;
                    break;
                }
            }
        }
        if (remotePlayer == null) return;

        UUID uuid = remotePlayer.getUUID();
        RemoteMorphState.PlayerMorphData data = RemoteMorphState.get(uuid);
        if (data == null || data.morphType == null || data.cachedEntity == null) return;

        // Apply stored ability states to their cached entity
        RemoteMorphState.applyAbilityStates(uuid, remotePlayer);

        Entity morphEntity = data.cachedEntity;

        // Sync position/rotation from the remote player
        morphEntity.setPos(remotePlayer.getX(), remotePlayer.getY(), remotePlayer.getZ());
        morphEntity.xOld = remotePlayer.getX();
        morphEntity.yOld = remotePlayer.getY();
        morphEntity.zOld = remotePlayer.getZ();
        morphEntity.setYRot(remotePlayer.getYRot());
        morphEntity.setXRot(remotePlayer.getXRot());
        morphEntity.yRotO = remotePlayer.getYRot();
        morphEntity.xRotO = remotePlayer.getXRot();
        morphEntity.setYHeadRot(remotePlayer.getYHeadRot());
        morphEntity.setOnGround(remotePlayer.onGround());
        morphEntity.tickCount = remotePlayer.tickCount;
        morphEntity.fallDistance = remotePlayer.fallDistance;

        if (morphEntity instanceof LivingEntity livingMorph) {
            livingMorph.yBodyRot = remotePlayer.yBodyRot;
            livingMorph.yBodyRotO = remotePlayer.yBodyRotO;
            livingMorph.yHeadRotO = remotePlayer.yHeadRotO;
            livingMorph.attackAnim = remotePlayer.attackAnim;
            livingMorph.oAttackAnim = remotePlayer.oAttackAnim;
            livingMorph.swinging = remotePlayer.swinging;
            livingMorph.swingTime = remotePlayer.swingTime;
            livingMorph.setDeltaMovement(remotePlayer.getDeltaMovement());
            livingMorph.swingingArm = remotePlayer.swingingArm;
            livingMorph.setPose(remotePlayer.getPose());
            livingMorph.hurtTime = remotePlayer.hurtTime;

            WalkAnimationStateAccessor playerAnim = (WalkAnimationStateAccessor) remotePlayer.walkAnimation;
            WalkAnimationStateAccessor morphAnim = (WalkAnimationStateAccessor) livingMorph.walkAnimation;

            boolean isFrog = morphEntity instanceof net.minecraft.world.entity.animal.frog.Frog;
            // Water uses a gentler boost — swim displacement is near-normal unlike ground movement.
            // FROG_IDLE_WATER (swimIdleAnimationState) is unaffected by walkBoost entirely.
            float walkBoost = isFrog
                    ? (remotePlayer.isInWater() ? morphling$FROG_SWIM_BOOST : morphling$FROG_WALK_BOOST)
                    : 1.0F;

            if (isFrog && !remotePlayer.onGround() && !remotePlayer.isInWater()) {
                // Airborne frog (leap/fall): no walk cycle — jump animation handles the pose
                morphAnim.morphling$setSpeed(0.0F);
                morphAnim.morphling$setSpeedOld(0.0F);
                morphAnim.morphling$setPosition(playerAnim.morphling$getPosition() * walkBoost);
            } else {
                morphAnim.morphling$setSpeed(Math.min(playerAnim.morphling$getSpeed() * walkBoost, 1.0F));
                morphAnim.morphling$setSpeedOld(Math.min(playerAnim.morphling$getSpeedOld() * walkBoost, 1.0F));
                morphAnim.morphling$setPosition(playerAnim.morphling$getPosition() * walkBoost);
            }
        }

        // Fox mouth item — mirror remote player's main hand onto cached fox
        if (morphEntity instanceof net.minecraft.world.entity.animal.fox.Fox fox) {
            fox.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    remotePlayer.getMainHandItem());
        }

        // Rabbit hop animation (remote) — derive from the remote player's airborne state.
        // Their hop physics ran on their own client and replicated here as position/velocity.
        // Sitting animation driven by synced rabbit_sitting ability state (via applyAbilityStates).
        if (morphEntity instanceof net.minecraft.world.entity.animal.rabbit.Rabbit rabbit) {
            if (!remotePlayer.onGround()) {
                rabbit.hopAnimationState.startIfStopped(remotePlayer.tickCount);
                rabbit.idleHeadTiltAnimationState.stop();
            } else {
                rabbit.hopAnimationState.stop();
                // Sitting state is applied via RemoteMorphState.applyAbilityStates
            }
        }

        try {
            EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
            EntityRenderer renderer = dispatcher.getRenderer(morphEntity);
            float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            EntityRenderState morphRenderState = renderer.createRenderState(morphEntity, partialTick);
            morphRenderState.lightCoords = state.lightCoords;

            // Frog swim state (remote)
            if (morphRenderState instanceof net.minecraft.client.renderer.entity.state.FrogRenderState frs) {
                frs.isSwimming = remotePlayer.isInWater();
            }

            // Panda variant (remote)
            if (morphRenderState instanceof net.minecraft.client.renderer.entity.state.PandaRenderState prs
                    && morphEntity instanceof net.minecraft.world.entity.animal.panda.Panda panda) {
                prs.variant = panda.getVariant();
            }

            // Rabbit hop completion float for remote player
            if (morphRenderState instanceof net.minecraft.client.renderer.entity.state.RabbitRenderState rs) {
                rs.jumpCompletion = remotePlayer.onGround()
                        ? 0.0F
                        : (float) Math.clamp(0.5 + remotePlayer.getDeltaMovement().y * 0.8, 0.0, 1.0);
            }

            // Enderman mad mode shake for remote player
            boolean doShake = morphEntity instanceof net.minecraft.world.entity.monster.EnderMan && data.endermanMad;
            if (doShake) {
                double d = 0.02;
                poseStack.pushPose();
                poseStack.translate(morphling$SHAKE_RNG.nextGaussian() * d, 0, morphling$SHAKE_RNG.nextGaussian() * d);
            }

            FpmCompat.hideHeadIfNeeded();
            renderer.submit(morphRenderState, poseStack, submitNodeCollector, camera);

            if (doShake) poseStack.popPose();

        } catch (Exception e) {
            // swallow render errors
        }

        ci.cancel();
    }
}
