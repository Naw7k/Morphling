package net.naw.morphling.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Handles all server-side ability actions sent via AbilityActionPayload.
 * All world mutations (explosions, block changes, teleports, damage, etc.) happen here.
 * Called from MorphlingNetworking.registerServer() when AbilityActionPayload is received.
 */
public class AbilityActionHandler {

    public static void handle(ServerPlayer player, String action, String data) {
        switch (action) {

            // ── Creeper ──────────────────────────────────────────────────────
            case "creeper_explode" -> player.level().explode(
                    player, player.getX(), player.getY(), player.getZ(),
                    3.0F, net.naw.morphling.client.games.MobBrawl.BrawlDimension.isBrawlDimension(player.level())
                            ? Level.ExplosionInteraction.NONE
                            : Level.ExplosionInteraction.MOB
            );

            // ── Chicken ──────────────────────────────────────────────────────
            case "chicken_egg" -> {
                net.minecraft.world.phys.Vec3 look = player.getLookAngle();
                net.minecraft.world.entity.item.ItemEntity egg = new net.minecraft.world.entity.item.ItemEntity(
                        player.level(),
                        player.getX() - look.x, player.getY() + 0.1, player.getZ() - look.z,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EGG)
                );
                egg.setDeltaMovement(
                        (player.getRandom().nextDouble() - 0.5) * 0.05, 0.05,
                        (player.getRandom().nextDouble() - 0.5) * 0.05
                );
                player.level().addFreshEntity(egg);
            }

            // ── Zombie ───────────────────────────────────────────────────────
            case "zombie_break_door" -> {
                try {
                    String[] p = data.split(",");
                    BlockPos pos = new BlockPos(
                            Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])
                    );
                    net.minecraft.world.level.block.state.BlockState state = player.level().getBlockState(pos);
                    if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                        player.level().levelEvent(1021, pos, 0);
                        player.level().levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
                        player.level().removeBlock(pos, false);
                    }
                } catch (Exception ignored) {}
            }

            // ── Sheep ────────────────────────────────────────────────────────
            case "sheep_heal" -> { if (player.getHealth() < player.getMaxHealth()) player.heal(0.5F); }
            case "sheep_hunger" -> {
                var food = player.getFoodData();
                food.setFoodLevel(Math.min(food.getFoodLevel() + 1, 20));
            }
            case "sheep_grass" -> {
                try {
                    String[] p = data.split(",");
                    BlockPos pos = new BlockPos(
                            Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])
                    );
                    if (player.level().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) {
                        player.level().setBlock(pos, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 3);
                        player.level().levelEvent(2001, pos,
                                net.minecraft.world.level.block.Block.getId(net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState()));
                    }
                } catch (Exception ignored) {}
            }

            // ── Dolphin ──────────────────────────────────────────────────────
            case "dolphin_dry_damage" -> //noinspection deprecation
                    player.hurt(player.damageSources().dryOut(), 1.0F);

            // ── Enderman ─────────────────────────────────────────────────────
            case "enderman_teleport" -> {
                try {
                    String[] p = data.split(",");
                    player.teleportTo(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
                } catch (Exception ignored) {}
            }
            case "enderman_water_damage" -> player.hurtServer(
                    player.level(),
                    player.damageSources().drown(), 4.0F
            );
            case "enderman_pickup" -> {
                try {
                    String[] p = data.split(",");
                    player.level().destroyBlock(new BlockPos(
                            Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])
                    ), false);
                } catch (Exception ignored) {}
            }
            case "enderman_place" -> {
                try {
                    String[] p = data.split(",");
                    BlockPos pos = new BlockPos(
                            Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])
                    );
                    net.minecraft.world.level.block.Block block =
                            BuiltInRegistries.BLOCK.getValue(Identifier.parse(p[3]));
                    player.level().setBlock(pos, block.defaultBlockState(), 3);
                } catch (Exception ignored) {}
            }

            // ── Skeleton ─────────────────────────────────────────────────────
            case "skeleton_equip_bow" -> {
                try {
                    int slot = Integer.parseInt(data);
                    net.minecraft.world.item.ItemStack bow = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW);
                    bow.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                            net.minecraft.network.chat.Component.literal("Skeleton Bow"));
                    player.getInventory().setItem(slot, bow);
                    player.inventoryMenu.broadcastChanges();
                } catch (Exception ignored) {}
            }
            case "skeleton_unequip_bow" -> {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    net.minecraft.world.item.ItemStack s = player.getInventory().getItem(i);
                    if (s.getItem() == net.minecraft.world.item.Items.BOW) {
                        net.minecraft.network.chat.Component name = s.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
                        if (name != null && name.getString().equals("Skeleton Bow")) {
                            player.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                        }
                    }
                }
                player.inventoryMenu.broadcastChanges();
            }

            case "irongolem_knockback" -> {
                try {
                    String[] p = data.split(",");
                    int entityId = Integer.parseInt(p[0]);
                    double scale = Double.parseDouble(p[1]);
                    net.minecraft.world.entity.Entity target = player.level().getEntity(entityId);
                    if (target != null) {
                        double yBoost = target instanceof ServerPlayer ? 0.7 : 0.4;
                        target.setDeltaMovement(target.getDeltaMovement().add(0.0, yBoost * scale, 0.0));
                        target.hurtMarked = true;
                    }
                } catch (Exception ignored) {}
            }

            case "irongolem_heal" -> {
                if (player.getHealth() < player.getMaxHealth()) {
                    player.heal(2.0F);
                }
            }

            // ── Slime ────────────────────────────────────────────────────────
            case "slime_contact_damage" -> {
                String[] parts = data.split(",");
                if (parts.length == 2) {
                    try {
                        UUID targetUuid = UUID.fromString(parts[0]);
                        float damage = Float.parseFloat(parts[1]);
                        net.minecraft.world.entity.Entity target = player.level().getEntity(targetUuid);
                        if (target instanceof net.minecraft.world.entity.LivingEntity living) {
                            //noinspection deprecation
                            living.hurt(player.level().damageSources().mobAttack(player), damage);
                        }
                    } catch (Exception ignored) {}
                }
            }

            // ── Bee ──────────────────────────────────────────────────────────
            case "bee_sting" -> {
                try {
                    UUID targetUuid = UUID.fromString(data);
                    net.minecraft.world.entity.Entity target = player.level().getEntity(targetUuid);
                    if (target instanceof net.minecraft.world.entity.LivingEntity living) {
                        //noinspection deprecation
                        living.hurt(player.level().damageSources().sting(player), 2.0F);
                        living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.POISON, 200, 0), player);
                    }
                } catch (Exception ignored) {}
            }

            // Axolotl — apply/remove regen when playing dead (200 ticks = 10s, matches vanilla)
            case "axolotl_playdead_on" -> player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.REGENERATION, 200, 0));
            case "axolotl_playdead_off" -> player.removeEffect(net.minecraft.world.effect.MobEffects.REGENERATION);

            case "axolotl_dry_damage" -> player.hurtServer(
                    player.level(),
                    player.damageSources().dryOut(), 2.0F
            );


// ── Frog ─────────────────────────────────────────────────────────────────
            // Phase 1: apply pull velocity, no damage yet
            case "frog_tongue_pull" -> {
                try {
                    java.util.UUID targetUuid = java.util.UUID.fromString(data);
                    net.minecraft.world.entity.Entity target = player.level().getEntity(targetUuid);
                    if (target instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()) {
                        // Vanilla-accurate pull: vectorTo = from target toward player
                        net.minecraft.world.phys.Vec3 dir = target.position().vectorTo(player.position()).normalize().scale(0.75);
                        target.setDeltaMovement(dir.x, dir.y + 0.2, dir.z);
                        target.hurtMarked = true; // critical — replicates velocity to other clients
                    }
                } catch (Exception ignored) {}
            }

            // Phase 2: deal damage after pull window (client sends this 6 ticks later)
            case "frog_tongue_eat" -> {
                try {
                    java.util.UUID targetUuid = java.util.UUID.fromString(data);
                    net.minecraft.world.entity.Entity target = player.level().getEntity(targetUuid);
                    if (target instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()) {
                        //noinspection deprecation
                        living.hurt(player.level().damageSources().mobAttack(player), 4.0F);
                    }
                } catch (Exception ignored) {}
            }

            // ── Panda ─────────────────────────────────────────────────────────
            case "panda_sneeze_finish" -> {
                // Spawn sneeze particle at player position
                if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    net.minecraft.world.phys.Vec3 movement = player.getDeltaMovement();
                    serverLevel.sendParticles(
                            net.minecraft.core.particles.ParticleTypes.SNEEZE,
                            player.getX() - Math.sin(Math.toRadians(player.yBodyRot)) * 0.5,
                            player.getEyeY() - 0.1,
                            player.getZ() + Math.cos(Math.toRadians(player.yBodyRot)) * 0.5,
                            1, movement.x, 0.0, movement.z, 0.0
                    );
                }
            }
        }
    }
}
