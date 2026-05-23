package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.naw.morphling.client.core.MorphState;

@SuppressWarnings("DataFlowIssue")
public class SkeletonAbility {

    private static boolean bowEquipped = false;
    private static int bowSlot = -1;

    public static boolean isBowEquipped() {
        if (!bowEquipped || MorphState.getCurrentMorph() != EntityType.SKELETON) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return mc.player.getInventory().getSelectedSlot() == bowSlot;
    }

    public static void toggleBow(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.SKELETON) return;
        if (client.player == null || client.level == null) return;

        if (bowEquipped) {
            unequipBow(client);
        } else {
            equipBow(client);
        }
    }

    private static void equipBow(Minecraft client) {
        Player player = client.player;
        int slot = player.getInventory().getSelectedSlot();
        ItemStack current = player.getInventory().getItem(slot);

        if (!current.isEmpty()) {
            client.gui.setOverlayMessage(
                    Component.literal("Clear selected hotbar slot to equip skeleton bow"),
                    false
            );
            return;
        }

        ItemStack bow = new ItemStack(Items.BOW);
        bow.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Skeleton Bow"));
        player.getInventory().setItem(slot, bow);
        bowSlot = slot;
        bowEquipped = true;

        client.level.playLocalSound(
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS,
                0.5F, 1.2F, false
        );

        MorphState.sendAbilityState("skeleton_bow", "true");
        syncInventory(client);
    }

    private static void unequipBow(Minecraft client) {
        Player player = client.player;
        if (bowSlot >= 0 && bowSlot < player.getInventory().getContainerSize()) {
            ItemStack stack = player.getInventory().getItem(bowSlot);
            if (stack.getItem() == Items.BOW) {
                player.getInventory().setItem(bowSlot, ItemStack.EMPTY);
            }
        }

        bowSlot = -1;
        bowEquipped = false;

        client.level.playLocalSound(
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                0.5F, 1.2F, false
        );

        MorphState.sendAbilityState("skeleton_bow", "false");
        syncInventory(client);
    }

    public static void onMorphChanged(Minecraft client) {
        if (client.player != null) {
            Player player = client.player;
            int slotToUnequip = bowSlot;
            bowEquipped = false;
            bowSlot = -1;
            // Full scan to remove any leftover skeleton bow regardless of bowSlot
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s.getItem() == Items.BOW) {
                    Component name = s.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
                    if (name != null && name.getString().equals("Skeleton Bow")) {
                        player.getInventory().setItem(i, ItemStack.EMPTY);
                        slotToUnequip = i;
                    }
                }
            }
            // Force unequip on server
            var server = client.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    var sp = server.getPlayerList().getPlayer(player.getUUID());
                    if (sp != null) {
                        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                            net.minecraft.world.item.ItemStack s = sp.getInventory().getItem(i);
                            if (s.getItem() == Items.BOW) {
                                net.minecraft.network.chat.Component name = s.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
                                if (name != null && name.getString().equals("Skeleton Bow")) {
                                    sp.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                                }
                            }
                        }
                        sp.inventoryMenu.broadcastChanges();
                    }
                });
            } else {
                MorphState.sendAbilityAction("skeleton_unequip_bow", String.valueOf(slotToUnequip));
            }
        }
        bowEquipped = false;
        bowSlot = -1;
    }

    public static void tickCleanup(Minecraft client) {
        if (bowEquipped) return;
        if (client.player == null) return;
        Player player = client.player;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() == Items.BOW) {
                Component name = s.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
                if (name != null && name.getString().equals("Skeleton Bow")) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                    syncInventory(client);
                }
            }
        }
    }

    private static void syncInventory(Minecraft client) {
        // Try singleplayer/LAN host path first
        var server = client.getSingleplayerServer();
        if (server != null && client.player != null) {
            var player = client.player;
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayer(player.getUUID());
                if (sp != null) {
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        sp.getInventory().setItem(i, player.getInventory().getItem(i).copy());
                    }
                }
            });
        } else {
            // Non-host on LAN — send via packet
            if (bowEquipped) {
                MorphState.sendAbilityAction("skeleton_equip_bow", String.valueOf(bowSlot));
            } else {
                MorphState.sendAbilityAction("skeleton_unequip_bow", String.valueOf(bowSlot));
            }
        }
    }
}
