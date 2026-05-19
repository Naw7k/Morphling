package net.naw.morphling.client.core;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks the morph state of all OTHER players (remote players) on the client.

 * Each remote player has a PlayerMorphData entry containing:
 *  - Their current morph type and a cached entity used for rendering
 *  - All variant selections (parrot color, cat breed, wolf variant, etc.)
 *  - All ability states (flying, sitting, angry, skeleton bow, creeper swell, etc.)

 * State is updated via MorphSyncPayload (morph change) and AbilitySyncPayload (ability change),
 * both received in MorphlingClient.

 * applyAbilityStates() is called every render frame from PlayerRendererMixin to keep
 * the cached entity's visual state in sync with what the remote player is doing.

 * Note: This is CLIENT-ONLY. The server tracks morphs via MorphlingNetworking.playerMorphMap.
 */
public class RemoteMorphState {

    /**
     * All state for a single remote player's morph.
     * Fields are written by packet receivers and read by applyAbilityStates() every frame.
     */
    public static class PlayerMorphData {
        public EntityType<?> morphType = null;
        public Entity cachedEntity = null;

        // Variant selections — applied once when the morph is set
        public String parrotVariant = "RED_BLUE";
        public String catVariant = null;
        public String wolfVariant = null;
        public String cowVariant = null;
        public String sheepColor = null;
        public String pigVariant = null;
        public String chickenVariant = null;

        // Ability states — updated every tick via AbilitySyncPayload
        public boolean flying = false;
        public String catPose = "STAND";
        public boolean wolfSitting = false;
        public boolean wolfHeadTilt = false;
        public boolean wolfAngry = false;
        public boolean wolfShaking = false;
        public boolean parrotSitting = false;
        public boolean parrotDancing = false;
        public boolean endermanMad = false;
        public String endermanCarriedBlock = ""; // block id or empty
        public boolean skeletonBowEquipped = false;
        public boolean skeletonDrawingBow = false;
        public float creeperSwellProgress = 0f;
        public boolean ironGolemFlower = false;
        public boolean ironGolemAttacking = false; // set true on hit, consumed in applyAbilityStates
        public boolean sheepEating = false;
        public int sheepEatTick = 0;
        public boolean horseRearing = false;
        public boolean horseEating = false;
        public boolean villagerUnhappy = false;
        public boolean villagerSleeping = false;
        public float villagerSleepYRot = 0f;
        public boolean beeAngry = false;
        public boolean beeNectar = false;
        public float beeRollAmount = 0.0F;

        // Animation tick counters for rate-limiting remote animation updates
        public int ironGolemAnimTicker = 0;
        public int sheepAnimTicker = 0;
    }

    private static final Map<UUID, PlayerMorphData> states = new HashMap<>();

    public static PlayerMorphData getOrCreate(UUID uuid) {
        return states.computeIfAbsent(uuid, _ -> new PlayerMorphData());
    }

    public static PlayerMorphData get(UUID uuid) {
        return states.get(uuid);
    }

    public static void remove(UUID uuid) {
        states.remove(uuid);
    }

    /** Called on disconnect — wipes all remote state. */
    public static void clear() {
        states.clear();
    }

    public static Map<UUID, PlayerMorphData> getAllStates() {
        return states;
    }

    /**
     * Sets or updates a remote player's morph and variant data.
     * Creates a new cached entity for rendering and resets all ability states.
     */
    public static void setMorph(UUID uuid, EntityType<?> type, String parrotVariant,
                                String catVariant, String wolfVariant,
                                String cowVariant, String sheepColor,
                                String pigVariant, String chickenVariant,
                                String horseColor, String horseMarkings,
                                String villagerProfession, String villagerType,
                                String slimeSize) {
        PlayerMorphData data = getOrCreate(uuid);
        data.morphType = type;
        data.cachedEntity = null;

        // Reset ability states on morph change
        data.flying = false;
        data.catPose = "STAND";
        data.wolfSitting = false;
        data.wolfHeadTilt = false;
        data.wolfAngry = false;
        data.wolfShaking = false;
        data.parrotSitting = false;
        data.parrotDancing = false;
        data.endermanMad = false;
        data.endermanCarriedBlock = "";
        data.skeletonBowEquipped = false;
        data.skeletonDrawingBow = false;
        data.creeperSwellProgress = 0f;
        data.ironGolemFlower = false;
        data.sheepEating = false;
        data.sheepEatTick = 0;
        data.horseRearing = false;
        data.horseEating = false;
        data.villagerUnhappy = false;
        data.villagerSleeping = false;
        data.beeAngry = false;
        data.beeNectar = false;
        data.beeRollAmount = 0.0F;

        if (type == null) {
            return;
        }

        Level world = Minecraft.getInstance().level;
        if (world == null) return;

        data.cachedEntity = type.create(world, EntitySpawnReason.LOAD);
        if (data.cachedEntity == null) return;

        applyVariantToEntity(data.cachedEntity, parrotVariant, catVariant, wolfVariant, cowVariant, sheepColor, pigVariant, chickenVariant, horseColor, horseMarkings, villagerProfession, villagerType, slimeSize, world);

        data.parrotVariant = parrotVariant;
        data.catVariant = catVariant;
        data.wolfVariant = wolfVariant;
        data.cowVariant = cowVariant;
        data.sheepColor = sheepColor;
        data.pigVariant = pigVariant;
        data.chickenVariant = chickenVariant;
    }

    /** Applies variant data (color, breed, etc.) to the newly created cached entity. */
    private static void applyVariantToEntity(Entity entity,
                                             String parrotVariant, String catVariant,
                                             String wolfVariant, String cowVariant,
                                             String sheepColor, String pigVariant,
                                             String chickenVariant, String horseColor,
                                             String horseMarkings, String villagerProfession,
                                             String villagerType, String slimeSize, Level world) {
        if (entity instanceof Parrot parrot && parrotVariant != null && !parrotVariant.isEmpty()) {
            try {
                Parrot.Variant v = Parrot.Variant.valueOf(parrotVariant);
                ((net.naw.morphling.mixin.accessors.ParrotVariantAccessor) parrot).morphling$setVariant(v);
            } catch (Exception ignored) {}
        }
        if (entity instanceof Cat cat && catVariant != null && !catVariant.isEmpty()) {
            try {
                var registry = world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.CAT_VARIANT);
                registry.listElements()
                        .filter(h -> h.unwrapKey().orElseThrow().identifier().toString().equals(catVariant))
                        .findFirst()
                        .ifPresent(h -> ((net.naw.morphling.mixin.accessors.CatVariantAccessor) cat).morphling$setVariant(h));
            } catch (Exception ignored) {}
        }
        if (entity instanceof Wolf wolf && wolfVariant != null && !wolfVariant.isEmpty()) {
            try {
                var registry = world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.WOLF_VARIANT);
                registry.listElements()
                        .filter(h -> h.unwrapKey().orElseThrow().identifier().toString().equals(wolfVariant))
                        .findFirst()
                        .ifPresent(h -> ((net.naw.morphling.mixin.accessors.WolfVariantAccessor) wolf).morphling$setVariant(h));
            } catch (Exception ignored) {}
        }
        if (entity instanceof net.minecraft.world.entity.animal.cow.Cow cow && cowVariant != null && !cowVariant.isEmpty()) {
            try {
                var registry = world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.COW_VARIANT);
                registry.listElements()
                        .filter(h -> h.unwrapKey().orElseThrow().identifier().toString().equals(cowVariant))
                        .findFirst()
                        .ifPresent(cow::setVariant);
            } catch (Exception ignored) {}
        }
        if (entity instanceof Sheep sheep && sheepColor != null && !sheepColor.isEmpty()) {
            try {
                sheep.setColor(net.minecraft.world.item.DyeColor.valueOf(sheepColor));
            } catch (Exception ignored) {}
        }
        if (entity instanceof net.minecraft.world.entity.animal.pig.Pig pig && pigVariant != null && !pigVariant.isEmpty()) {
            try {
                var registry = world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.PIG_VARIANT);
                registry.listElements()
                        .filter(h -> h.unwrapKey().orElseThrow().identifier().toString().equals(pigVariant))
                        .findFirst()
                        .ifPresent(h -> ((net.naw.morphling.mixin.accessors.PigVariantAccessor) pig).morphling$setVariant(h));
            } catch (Exception ignored) {}
        }
        if (entity instanceof net.minecraft.world.entity.animal.chicken.Chicken chicken && chickenVariant != null && !chickenVariant.isEmpty()) {
            try {
                var registry = world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.CHICKEN_VARIANT);
                registry.listElements()
                        .filter(h -> h.unwrapKey().orElseThrow().identifier().toString().equals(chickenVariant))
                        .findFirst()
                        .ifPresent(chicken::setVariant);
            } catch (Exception ignored) {}
        }
        if (entity instanceof net.minecraft.world.entity.animal.equine.Horse horse && horseColor != null && !horseColor.isEmpty()) {
            try {
                net.minecraft.world.entity.animal.equine.Variant color = net.minecraft.world.entity.animal.equine.Variant.valueOf(horseColor);
                net.minecraft.world.entity.animal.equine.Markings markings = net.minecraft.world.entity.animal.equine.Markings.valueOf(
                        horseMarkings != null && !horseMarkings.isEmpty() ? horseMarkings : "NONE"
                );
                ((net.naw.morphling.mixin.accessors.HorseVariantAccessor) horse).morphling$setVariantAndMarkings(color, markings);
            } catch (Exception ignored) {}
        }

        if (entity instanceof net.minecraft.world.entity.npc.villager.Villager v) {
            try {
                if (villagerProfession != null && !villagerProfession.isEmpty()) {
                    var registry = world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION);
                    registry.listElements()
                            .filter(h -> h.unwrapKey().orElseThrow().identifier().toString().equals(villagerProfession))
                            .findFirst()
                            .ifPresent(h -> v.setVillagerData(v.getVillagerData().withProfession(h)));
                }
                if (villagerType != null && !villagerType.isEmpty()) {
                    var registry = world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_TYPE);
                    registry.listElements()
                            .filter(h -> h.unwrapKey().orElseThrow().identifier().toString().equals(villagerType))
                            .findFirst()
                            .ifPresent(h -> v.setVillagerData(v.getVillagerData().withType(h)));
                }
            } catch (Exception ignored) {}
        }
        if (entity instanceof net.minecraft.world.entity.monster.Slime slime && slimeSize != null && !slimeSize.isEmpty()) {
            try {
                slime.setSize(Integer.parseInt(slimeSize), false);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Called every render frame from PlayerRendererMixin for each remote morphed player.
     * Applies all current ability states to the cached entity so animations and visuals
     * match what the remote player is actually doing.
     */
    public static void applyAbilityStates(UUID uuid, Entity remotePlayer) {
        PlayerMorphData data = states.get(uuid);
        if (data == null || data.cachedEntity == null) return;

        Entity e = data.cachedEntity;

        if (e instanceof Cat cat) {
            cat.setInSittingPose(false);
            cat.setLying(false);
            ((net.naw.morphling.mixin.accessors.CatRelaxAccessor) cat).morphling$setRelaxStateOne(false);
            switch (data.catPose) {
                case "SIT"     -> cat.setInSittingPose(true);
                case "LYING"   -> cat.setLying(true);
                case "RELAXED" -> ((net.naw.morphling.mixin.accessors.CatRelaxAccessor) cat).morphling$setRelaxStateOne(true);
            }
            // Tick lie-down animation
            try {
                ((net.naw.morphling.mixin.accessors.CatTickAccessor) cat).morphling$handleLieDown();
            } catch (Exception ignored) {}
        }

        if (e instanceof Wolf wolf) {
            wolf.setInSittingPose(data.wolfSitting);
            wolf.setIsInterested(data.wolfHeadTilt);
            wolf.setPersistentAngerEndTime(data.wolfAngry ? Long.MAX_VALUE : -1L);

            // Smoothly lerp head tilt angle
            var tickAccessor = (net.naw.morphling.mixin.accessors.WolfTickAccessor) wolf;
            float current = tickAccessor.morphling$getInterestedAngle();
            tickAccessor.morphling$setInterestedAngleO(current);
            float target = data.wolfHeadTilt ? 1.0F : 0.0F;
            tickAccessor.morphling$setInterestedAngle(current + (target - current) * 0.4F);

            if (data.wolfShaking) {
                var shakeAccessor = (net.naw.morphling.mixin.accessors.WolfShakeAccessor) wolf;
                shakeAccessor.morphling$setIsWet(true);
                shakeAccessor.morphling$setIsShaking(true);
                float shakeAnim = shakeAccessor.morphling$getShakeAnim();
                shakeAccessor.morphling$setShakeAnimO(shakeAnim);
                shakeAnim += 0.02F;
                shakeAccessor.morphling$setShakeAnim(shakeAnim);
                if (shakeAnim >= 2.0F) {
                    data.wolfShaking = false;
                    shakeAccessor.morphling$setIsWet(false);
                    shakeAccessor.morphling$setIsShaking(false);
                    shakeAccessor.morphling$setShakeAnim(0.0F);
                    shakeAccessor.morphling$setShakeAnimO(0.0F);
                }
            }
        }

        if (e instanceof Parrot parrot) {
            parrot.setInSittingPose(data.parrotSitting);
            if (remotePlayer instanceof net.minecraft.world.entity.player.Player rp) {
                parrot.setRecordPlayingNearby(rp.blockPosition(), data.parrotDancing);
            }
            // Flap animation — rate-limited to avoid jitter on remote clients
            parrot.oFlap = parrot.flap;
            parrot.oFlapSpeed = parrot.flapSpeed;
            if (data.flying) {
                parrot.flapSpeed = Math.min(parrot.flapSpeed + 0.02F, 1.0F);
                parrot.flap += parrot.flapSpeed * 0.2F;
            } else {
                parrot.flapSpeed = Math.max(parrot.flapSpeed - 0.02F, 0.0F);
            }
        }

        if (e instanceof net.minecraft.world.entity.animal.chicken.Chicken chicken) {
            chicken.oFlap = chicken.flap;
            chicken.oFlapSpeed = chicken.flapSpeed;
            if (remotePlayer != null && !remotePlayer.onGround()) {
                chicken.flapSpeed = Math.min(chicken.flapSpeed + 0.02F, 1.0F);
                chicken.flap += chicken.flapSpeed * 0.2F;
            } else {
                chicken.flapSpeed = Math.max(chicken.flapSpeed - 0.02F, 0.0F);
            }
        }

        if (e instanceof EnderMan enderman) {
            enderman.getEntityData().set(
                    net.naw.morphling.mixin.accessors.EndermanCreepyAccessor.morphling$getDataCreepy(),
                    data.endermanMad
            );
            // Apply carried block visual
            if (data.endermanCarriedBlock != null && !data.endermanCarriedBlock.isEmpty()) {
                try {
                    Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(data.endermanCarriedBlock));
                    enderman.setCarriedBlock(block.defaultBlockState());
                } catch (Exception ignored) {}
            } else {
                enderman.setCarriedBlock(null);
            }
        }

        // Enderman ambient portal particles for remote players
        if (data.morphType == EntityType.ENDERMAN && remotePlayer != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                net.minecraft.util.RandomSource rng = remotePlayer.getRandom();
                if (rng.nextInt(3) == 0) {
                    mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL,
                            remotePlayer.getX() + (rng.nextDouble() - 0.5) * remotePlayer.getBbWidth(),
                            remotePlayer.getY() + rng.nextDouble() * remotePlayer.getBbHeight() - 0.25,
                            remotePlayer.getZ() + (rng.nextDouble() - 0.5) * remotePlayer.getBbWidth(),
                            (rng.nextDouble() - 0.5) * 2,
                            -rng.nextDouble(),
                            (rng.nextDouble() - 0.5) * 2);
                }
            }
        }

        if (e instanceof Skeleton skeleton) {
            if (data.skeletonBowEquipped) {
                skeleton.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
                skeleton.setAggressive(data.skeletonDrawingBow);
            } else {
                skeleton.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                skeleton.setAggressive(false);
            }
        }

        if (e instanceof Creeper creeper) {
            net.naw.morphling.mixin.accessors.CreeperSwellAccessor accessor =
                    (net.naw.morphling.mixin.accessors.CreeperSwellAccessor) creeper;
            int targetSwell = (int)(data.creeperSwellProgress * 28);
            accessor.morphling$setOldSwell(accessor.morphling$getSwell());
            accessor.morphling$setSwell(targetSwell);
        }

        if (e instanceof IronGolem golem) {
            golem.offerFlower(data.ironGolemFlower);

            // Mirror remote player's health ratio on the cached golem for crack visuals
            if (remotePlayer instanceof LivingEntity lp) {
                float ratio = lp.getHealth() / lp.getMaxHealth();
                float targetHp = golem.getMaxHealth() * ratio;
                if (Math.abs(golem.getHealth() - targetHp) > 0.1F) {
                    golem.setHealth(targetHp);
                }
            }

            // Trigger arm slam animation when attack event received
            if (data.ironGolemAttacking) {
                golem.handleEntityEvent((byte) 4);
                data.ironGolemAttacking = false;
            }

            // Tick down attack animation at reduced rate to match visual speed
            var attackAccessor = (net.naw.morphling.mixin.accessors.IronGolemAttackAccessor) golem;
            int currentTick = attackAccessor.morphling$getAttackAnimationTick();
            if (currentTick > 0) {
                data.ironGolemAnimTicker++;
                if (data.ironGolemAnimTicker % 8 == 0) {
                    attackAccessor.morphling$setAttackAnimationTick(currentTick - 1);
                }
            }
        }

        if (e instanceof Sheep sheep) {
            var accessor = (net.naw.morphling.mixin.accessors.SheepEatAccessor) sheep;
            if (data.sheepEating) {
                // Rate-limited tick to keep eating animation smooth on remote clients
                data.sheepAnimTicker++;
                if (data.sheepAnimTicker % 8 == 0) {
                    int tick = accessor.morphling$getEatAnimationTick();
                    accessor.morphling$setEatAnimationTick((tick + 1) % 40);
                }
            } else {
                accessor.morphling$setEatAnimationTick(0);
                data.sheepAnimTicker = 0;
            }
        }

        if (e instanceof net.minecraft.world.entity.animal.equine.Horse horse) {
            if (data.horseRearing) ((net.naw.morphling.mixin.accessors.AbstractHorseAccessor) horse).morphling$setStanding(30);
            ((net.naw.morphling.mixin.accessors.AbstractHorseAccessor) horse).morphling$setEating(data.horseEating);
            // Tick the horse entity so animation floats (standAnim, eatAnim) actually update
            assert remotePlayer != null;
            horse.setPos(remotePlayer.getX(), remotePlayer.getY(), remotePlayer.getZ());
            horse.setDeltaMovement(0, 0, 0);
            try { horse.tick(); } catch (Exception ignored) {}
            horse.setPos(remotePlayer.getX(), remotePlayer.getY(), remotePlayer.getZ());
            horse.setDeltaMovement(0, 0, 0);
        }

        if (e instanceof net.minecraft.world.entity.npc.villager.Villager villager) {
            // Only tick when not sleeping so rotation lock isn't overridden
            if (!data.villagerSleeping) {
                if (remotePlayer != null) villager.setPos(remotePlayer.getX(), remotePlayer.getY(), remotePlayer.getZ());
                try { villager.tick(); } catch (Exception ignored) {}
                villager.setDeltaMovement(0, 0, 0);
                if (remotePlayer != null) villager.setPos(remotePlayer.getX(), remotePlayer.getY(), remotePlayer.getZ());
            }

            if (data.villagerUnhappy) {
                villager.setUnhappyCounter(250);
                data.villagerUnhappy = false;
            }

            if (data.villagerSleeping) {
                if (remotePlayer != null) {
                    remotePlayer.setPose(net.minecraft.world.entity.Pose.SLEEPING);
                    if (remotePlayer instanceof net.minecraft.world.entity.LivingEntity livingRemote) {
                        livingRemote.yHeadRot = data.villagerSleepYRot;
                        livingRemote.yHeadRotO = data.villagerSleepYRot;
                        livingRemote.yBodyRot = data.villagerSleepYRot;
                        livingRemote.yBodyRotO = data.villagerSleepYRot;
                    }
                }
            } else {
                if (remotePlayer != null && remotePlayer.getPose() == net.minecraft.world.entity.Pose.SLEEPING) {
                    remotePlayer.setPose(net.minecraft.world.entity.Pose.STANDING);
                }
            }
        }

        if (data.morphType == EntityType.BEE && remotePlayer != null && data.flying) {
            if (remotePlayer.getPose() == net.minecraft.world.entity.Pose.CROUCHING) {
                remotePlayer.setPose(net.minecraft.world.entity.Pose.STANDING);
            }
        }

    }
}