package net.naw.morphling.client.abilities;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.naw.morphling.client.core.MorphState;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public class ParrotAbility {

    private static long lastActionTime = 0L;
    private static final long ACTION_COOLDOWN_MS = 300;

    private static boolean sitting = false;
    private static boolean dancing = false;

    public static void toggleSit(Minecraft client) {
        if (!checkReady(client)) return;
        if (!(MorphState.getCachedEntity() instanceof Parrot parrot)) return;
        sitting = !sitting;
        parrot.setInSittingPose(sitting);
    }

    public static void toggleDance(Minecraft client) {
        if (!checkReady(client)) return;
        if (!(MorphState.getCachedEntity() instanceof Parrot parrot)) return;
        if (client.player == null) return;
        dancing = !dancing;
        parrot.setRecordPlayingNearby(client.player.blockPosition(), dancing);
    }

    public static void imitateNearbyMob(Minecraft client) {
        if (!checkReady(client)) return;
        if (client.level == null || client.player == null) return;

        try {
            Field f = Parrot.class.getDeclaredField("MOB_SOUND_MAP");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<EntityType<?>, SoundEvent> map = (Map<EntityType<?>, SoundEvent>) f.get(null);

            List<Mob> mobs = client.level.getEntitiesOfClass(
                    Mob.class,
                    client.player.getBoundingBox().inflate(20.0),
                    m -> !(m instanceof Parrot) && map.containsKey(m.getType())
            );
            if (mobs.isEmpty()) return;
            Mob mob = mobs.get(client.level.getRandom().nextInt(mobs.size()));
            SoundEvent sound = map.get(mob.getType());
            if (sound != null) {
                client.level.playLocalSound(
                        client.player.getX(), client.player.getY(), client.player.getZ(),
                        sound, SoundSource.PLAYERS,
                        0.7F, 1.0F, false
                );
            }
        } catch (Exception ignored) {}
    }

    private static boolean checkReady(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.PARROT) return false;
        if (client.player == null) return false;
        long now = System.currentTimeMillis();
        if (now - lastActionTime < ACTION_COOLDOWN_MS) return false;
        lastActionTime = now;
        return true;
    }

    public static void tick(Minecraft client) {
        if (MorphState.getCurrentMorph() != EntityType.PARROT) {
            sitting = false;
            dancing = false;
            return;
        }
        if (client.player == null) return;
        if (!(MorphState.getCachedEntity() instanceof Parrot parrot)) return;

        double speedSqr = client.player.getDeltaMovement().horizontalDistanceSqr();
        boolean inAir = !client.player.onGround();
        if (speedSqr > 0.001 || inAir) {
            if (sitting) { sitting = false; parrot.setInSittingPose(false); }
            if (dancing) { dancing = false; parrot.setRecordPlayingNearby(client.player.blockPosition(), false); }
        }
    }
}