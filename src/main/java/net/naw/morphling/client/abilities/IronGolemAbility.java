package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.naw.morphling.client.core.MorphState;

public class IronGolemAbility {

    private static long lastActionTime = 0L;
    private static final long ACTION_COOLDOWN_MS = 300;

    private static boolean offeringFlower = false;

    public static void toggleFlower(Minecraft client) {
        if (!checkReady(client)) return;
        if (!(MorphState.getCachedEntity() instanceof IronGolem golem)) return;

        offeringFlower = !offeringFlower;
        golem.offerFlower(offeringFlower);
    }

    private static boolean checkReady(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.IRON_GOLEM) return false;
        if (client.player == null) return false;
        long now = System.currentTimeMillis();
        if (now - lastActionTime < ACTION_COOLDOWN_MS) return false;
        lastActionTime = now;
        return true;
    }

    public static void tick(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.IRON_GOLEM) {
            offeringFlower = false;
            return;
        }
        if (client.player == null) return;
        if (!(MorphState.getCachedEntity() instanceof IronGolem golem)) return;

        // Sync cached golem's HP to match player's HP ratio — drives the crack visual
        float playerRatio = client.player.getHealth() / client.player.getMaxHealth();
        float targetHp = golem.getMaxHealth() * playerRatio;
        if (Math.abs(golem.getHealth() - targetHp) > 0.1F) {
            golem.setHealth(targetHp);
        }

        // Manually decrement attackAnimationTick — cached entity never runs aiStep
        var attackAccessor = (net.naw.morphling.mixin.accessors.IronGolemAttackAccessor)(Object) golem;
        int currentTick = attackAccessor.morphling$getAttackAnimationTick();
        if (currentTick > 0) {
            attackAccessor.morphling$setAttackAnimationTick(currentTick - 1);
        }
    }
}