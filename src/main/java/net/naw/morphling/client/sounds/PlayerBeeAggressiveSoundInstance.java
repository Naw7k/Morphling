package net.naw.morphling.client.sounds;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.client.core.MorphState;

public class PlayerBeeAggressiveSoundInstance extends AbstractTickableSoundInstance {

    private final Player player;

    public PlayerBeeAggressiveSoundInstance(Player player) {
        super(SoundEvents.BEE_LOOP_AGGRESSIVE, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = player;
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
    }

    @Override
    public void tick() {
        if (MorphState.getCurrentMorph() != EntityType.BEE || !MorphState.isFlightActive()) {
            this.stop();
            return;
        }

        if (player.isRemoved()) {
            this.stop();
            return;
        }

        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();

        float speed = (float) player.getDeltaMovement().horizontalDistance();

        if (speed >= 0.01F) {
            this.pitch = Mth.lerp(Mth.clamp(speed, 0.7F, 1.1F), 0.7F, 1.1F);
            this.volume = Mth.lerp(Mth.clamp(speed, 0.0F, 0.5F), 0.0F, 0.5F);
        } else {
            this.pitch = 0.7F;
            this.volume = 0.05F;
        }

    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public boolean canPlaySound() {
        return MorphState.getCurrentMorph() == EntityType.BEE && MorphState.isFlightActive();
    }
}