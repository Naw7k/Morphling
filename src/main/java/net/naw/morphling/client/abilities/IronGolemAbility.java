package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.naw.morphling.client.core.MorphState;

public class IronGolemAbility {

    private static long lastActionTime = 0L;
    private static final long ACTION_COOLDOWN_MS = 300;
    private static long lastHealTime = 0L;
    private static final long HEAL_COOLDOWN_MS = 10000;

    private static boolean offeringFlower = false;

    public static void toggleFlower(Minecraft client) {
        if (!checkReady(client)) return;
        if (!(MorphState.getCachedEntity() instanceof IronGolem golem)) return;

        offeringFlower = !offeringFlower;
        golem.offerFlower(offeringFlower);
        MorphState.sendAbilityState("irongolem_flower", String.valueOf(offeringFlower));
    }

    public static boolean isOfferingFlower() {
        return offeringFlower;
    }

    private static boolean checkReady(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.IRON_GOLEM) return false;
        if (client.player == null) return false;
        long now = System.currentTimeMillis();
        if (now - lastActionTime < ACTION_COOLDOWN_MS) return false;
        lastActionTime = now;
        return true;
    }

    public static boolean tryHeal(Minecraft client) {
        if (client.player == null) return false;
        long now = System.currentTimeMillis();
        if (now - lastHealTime < HEAL_COOLDOWN_MS) return false;
        lastHealTime = now;
        MorphState.sendAbilityAction("irongolem_heal", "");
        return true;
    }

    public static void tick(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.IRON_GOLEM) {
            offeringFlower = false;
            return;
        }
        if (client.player == null) return;
        if (client.player.getFoodData().getFoodLevel() < 20) {
            client.player.getFoodData().setFoodLevel(20);
        }
        if (client.player.isUsingItem()) {
            net.minecraft.world.item.ItemStack using = client.player.getUseItem();
            if (using.has(net.minecraft.core.component.DataComponents.FOOD)) {
                client.player.stopUsingItem();
            }
        }
        if (!(MorphState.getCachedEntity() instanceof IronGolem golem)) return;

        float playerRatio = client.player.getHealth() / client.player.getMaxHealth();
        float targetHp = golem.getMaxHealth() * playerRatio;
        if (Math.abs(golem.getHealth() - targetHp) > 0.1F) {
            golem.setHealth(targetHp);
        }

        var attackAccessor = (net.naw.morphling.mixin.accessors.IronGolemAttackAccessor) golem;
        int currentTick = attackAccessor.morphling$getAttackAnimationTick();
        if (currentTick > 0) {
            attackAccessor.morphling$setAttackAnimationTick(currentTick - 1);
        }
    }
}
