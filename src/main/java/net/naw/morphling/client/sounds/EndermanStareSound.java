package net.naw.morphling.client.sounds;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

public class EndermanStareSound extends AbstractTickableSoundInstance {
    private final Player player;
    private final java.util.function.BooleanSupplier activeCheck;

    public EndermanStareSound(Player player, java.util.function.BooleanSupplier activeCheck) {
        super(SoundEvents.ENDERMAN_STARE, SoundSource.PLAYERS, RandomSource.create());
        this.player = player;
        this.activeCheck = activeCheck;
        this.looping = false;
        this.delay = 0;
        this.volume = 2.5F;
        this.pitch = 1.0F;
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }

    @Override
    public void tick() {
        if (player.isRemoved() || !activeCheck.getAsBoolean()) {
            this.stop();
            return;
        }
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }
}
