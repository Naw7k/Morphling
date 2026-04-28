package net.naw.morphling.client.sounds;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.client.abilities.EndermanMadMode;

public class EndermanStareSound extends AbstractTickableSoundInstance {
    private final Player player;

    public EndermanStareSound(Player player) {
        super(SoundEvents.ENDERMAN_STARE, SoundSource.PLAYERS, RandomSource.create());
        this.player = player;
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
        // Stop immediately if mad mode is off or player gone
        if (player.isRemoved() || !EndermanMadMode.isActive()) {
            this.stop();
            return;
        }
        // Follow the player
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }
}
