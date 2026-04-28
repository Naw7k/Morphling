package net.naw.morphling.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.naw.morphling.client.abilities.*;
import net.naw.morphling.client.config.HandPlacementConfig;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.client.debug.DamageIndicator;
import net.naw.morphling.client.debug.DebugScreen;
import net.naw.morphling.client.ui.MorphMenuScreen;
import net.naw.morphling.client.util.MultiplayerCheck;
import net.naw.morphling.mixin.accessors.LivingEntityAccessor;
import net.naw.morphling.network.MorphlingNetworking;
import org.lwjgl.glfw.GLFW;
import net.naw.morphling.client.abilities.WolfAbility;
import net.naw.morphling.client.abilities.ParrotAbility;
import net.naw.morphling.client.abilities.SheepAbility;
import net.naw.morphling.client.abilities.ZombieAbility;
import net.naw.morphling.client.abilities.IronGolemAbility;
import net.naw.morphling.client.abilities.DolphinAbility;


public class MorphlingClient implements ClientModInitializer {
    public static KeyMapping openMenuKey;
    public static KeyMapping playSoundKey;
    public static KeyMapping abilityKey;
    public static KeyMapping madModeKey;
    public static KeyMapping debugMenuKey;
    private static long lastSoundTime = 0L;
    private static final long SOUND_COOLDOWN_MS = 1500;

    public static final KeyMapping.Category MORPHLING_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("morphling", "general")
    );

    @Override
    public void onInitializeClient() {

        HandPlacementConfig.loadFromFile();

        ClientPlayNetworking.registerGlobalReceiver(MorphlingNetworking.HandshakePayload.TYPE, (payload, context) -> {
            MultiplayerCheck.serverHasMorphling = true;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MultiplayerCheck.serverHasMorphling = false;
        });


        openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.morphling.open_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                MORPHLING_CATEGORY
        ));

        playSoundKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.morphling.play_sound",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                MORPHLING_CATEGORY
        ));

        abilityKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.morphling.ability",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                MORPHLING_CATEGORY
        ));

        madModeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.morphling.mad_mode",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F,
                MORPHLING_CATEGORY
        ));

        debugMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.morphling.debug_menu",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),  // unbound by default
                MORPHLING_CATEGORY
        ));

        DamageIndicator.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            MorphState.tickAttributes();
            MorphState.tickChickenFall();
            MorphState.tickFlight();
            CreeperAbility.tick(client);
            MobAbilities.tick(client);
            EndermanMadMode.tick(client);
            SkeletonAbility.tickCleanup(client);
            SheepAbility.tick(client);
            CatAbility.tick(client);
            WolfAbility.tick(client);
            WolfAngryMode.tick(client);
            ParrotAbility.tick(client);
            MorphState.tickParrotFall();
            ZombieAbility.tick(client);
            IronGolemAbility.tick(client);
            DolphinAbility.tick(client);
            tickFlapAnimations(client);
            tickMorphSync(client);
            net.naw.morphling.client.core.MorphTransition.tick();
            net.naw.morphling.client.health.HealthSync.tick();
            net.naw.morphling.client.health.HungerSync.tick();


            while (openMenuKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new MorphMenuScreen());
                } else if (client.screen instanceof MorphMenuScreen) {
                    client.setScreen(null);
                }
            }

            while (playSoundKey.consumeClick()) {


                boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(
                        client.getWindow().handle(),
                        org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT
                ) == 1;


                boolean ctrl = org.lwjgl.glfw.GLFW.glfwGetKey(
                        client.getWindow().handle(),
                        org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL
                ) == 1;

                if (MorphState.getCurrentMorph() == EntityType.CAT) {
                    if (shift) CatAbility.playHiss(client);
                    else if (ctrl) CatAbility.playPurr(client);
                    else playMorphSound(client);

                } else if (MorphState.getCurrentMorph() == EntityType.WOLF) {
                    if (ctrl) WolfAbility.playPant(client);
                    else playMorphSound(client);
                } else if (MorphState.getCurrentMorph() == EntityType.IRON_GOLEM) {
                    if (client.level != null && client.player != null) {
                        client.level.playLocalSound(
                                client.player.getX(), client.player.getY(), client.player.getZ(),
                                net.minecraft.sounds.SoundEvents.IRON_GOLEM_REPAIR, net.minecraft.sounds.SoundSource.PLAYERS,
                                1.0F, 1.0F, false
                        );
                    }
                } else {
                    playMorphSound(client);
                }


            }

            while (madModeKey.consumeClick()) {
                if (MorphState.getCurrentMorph() == EntityType.ENDERMAN) {
                    EndermanMadMode.toggle(client);
                } else if (MorphState.getCurrentMorph() == EntityType.WOLF) {
                    WolfAngryMode.toggle(client);
                }
            }


            while (abilityKey.consumeClick()) {


                boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(
                        client.getWindow().handle(),
                        org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT
                ) == 1;

                boolean ctrl = org.lwjgl.glfw.GLFW.glfwGetKey(
                        client.getWindow().handle(),
                        org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL
                ) == 1;


                // Abilities
                if (MorphState.getCurrentMorph() == EntityType.ENDERMAN && shift) {
                    EndermanCarryAbility.trigger(client);
                }

                else if (MorphState.getCurrentMorph() == EntityType.SKELETON) {
                    SkeletonAbility.toggleBow(client);
                }

                else if (MorphState.getCurrentMorph() == EntityType.SHEEP) {
                    SheepAbility.trigger(client);
                }

                else if (MorphState.getCurrentMorph() == EntityType.CAT) {
                    if (shift) CatAbility.toggleLying(client);
                    else if (ctrl) CatAbility.toggleRelaxed(client);
                    else CatAbility.toggleSit(client);
                }

                else if (MorphState.getCurrentMorph() == EntityType.WOLF) {
                    if (shift) WolfAbility.triggerShake(client);
                    else if (ctrl) WolfAbility.toggleHeadTilt(client);
                    else WolfAbility.toggleSit(client);
                }

                else if (MorphState.getCurrentMorph() == EntityType.PARROT) {
                    if (shift) ParrotAbility.toggleDance(client);
                    else if (ctrl) ParrotAbility.imitateNearbyMob(client);
                    else ParrotAbility.toggleSit(client);
                }

                else if (MorphState.getCurrentMorph() == EntityType.IRON_GOLEM) {
                    IronGolemAbility.toggleFlower(client);
                }

                else if (MorphState.getCurrentMorph() == EntityType.DOLPHIN) {
                    DolphinAbility.doSplashJump();
                }

                else {
                    MobAbilities.trigger(client);
                }
            }

            while (debugMenuKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new DebugScreen());
                }
            }
        });
    }

    private static void playMorphSound(Minecraft client) {
        if (client.player == null || client.level == null) return;
        if (!MorphState.isMorphed()) return;

        long now = System.currentTimeMillis();
        if (now - lastSoundTime < SOUND_COOLDOWN_MS) return;

        Entity morphEntity = MorphState.getCachedEntity();
        if (!(morphEntity instanceof Mob mobMorph)) return;

        SoundEvent sound = ((LivingEntityAccessor)(Object) mobMorph).morphling$getAmbientSound();
        if (sound != null) {
            client.level.playLocalSound(
                    client.player.getX(),
                    client.player.getY(),
                    client.player.getZ(),
                    sound,
                    SoundSource.NEUTRAL,
                    1.0F,
                    1.0F,
                    false
            );
            lastSoundTime = now;
        }
    }

    private static void tickFlapAnimations(Minecraft client) {
        if (!MorphState.isMorphed()) return;
        if (client.player == null) return;
        var morph = MorphState.getCachedEntity();
        if (morph == null) return;

        if (morph instanceof net.minecraft.world.entity.animal.parrot.Parrot parrot) {
            parrot.oFlap = parrot.flap;
            parrot.oFlapSpeed = parrot.flapSpeed;
            if (MorphState.isFlightActive()) {
                parrot.flap += parrot.flapSpeed * 1.8F;
                parrot.flapSpeed = Math.min(parrot.flapSpeed + 0.30F, 1.0F);
            } else {
                parrot.flapSpeed = Math.max(parrot.flapSpeed - 0.1F, 0.0F);
            }
        }

        if (morph instanceof net.minecraft.world.entity.animal.chicken.Chicken chicken) {
            chicken.oFlap = chicken.flap;
            chicken.oFlapSpeed = chicken.flapSpeed;
            if (!client.player.onGround()) {
                chicken.flapSpeed = 1.0F;
                chicken.flap += chicken.flapSpeed * 1.8F;
            } else {
                chicken.flapSpeed = 0.0F;
            }
        }
    }

    private static void tickMorphSync(Minecraft client) {
        if (!MorphState.isMorphed()) return;
        if (client.player == null) return;
        var morph = MorphState.getCachedEntity();
        if (morph == null) return;

        if (morph instanceof net.minecraft.world.entity.animal.chicken.Chicken) return;

        morph.tickCount = client.player.tickCount;
        morph.fallDistance = client.player.fallDistance;

        net.naw.morphling.client.compat.FaCompat.lockEmfVariables(morph);
    }
}