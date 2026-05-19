package net.naw.morphling.client.sounds;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.client.core.RemoteMorphState;

public class RemoteBeeAggressiveSoundInstance extends AbstractTickableSoundInstance {

    private final Player remotePlayer;
    private final RemoteMorphState.PlayerMorphData data;
    private boolean hasSwitched = false;

    public RemoteBeeAggressiveSoundInstance(Player remotePlayer, RemoteMorphState.PlayerMorphData data) {
        super(SoundEvents.BEE_LOOP_AGGRESSIVE, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.remotePlayer = remotePlayer;
        this.data = data;
        this.x = remotePlayer.getX();
        this.y = remotePlayer.getY();
        this.z = remotePlayer.getZ();
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
    }

    @Override
    public void tick() {
        if (data.morphType == null || data.morphType != EntityType.BEE || !data.flying) {
            this.stop();
            return;
        }

        if (remotePlayer.isRemoved()) {
            this.stop();
            return;
        }

        this.x = remotePlayer.getX();
        this.y = remotePlayer.getY();
        this.z = remotePlayer.getZ();

        if (!data.beeAngry && !hasSwitched) {
            hasSwitched = true;
            net.minecraft.client.Minecraft.getInstance().getSoundManager().queueTickingSound(
                    new RemoteBeeFlyingSoundInstance(remotePlayer, data)
            );
            this.stop();
            return;
        }

        float speed = (float) remotePlayer.getDeltaMovement().horizontalDistance();
        if (speed >= 0.01F) {
            this.pitch = Mth.lerp(Mth.clamp(speed, 0.7F, 1.1F), 0.7F, 1.1F);
            this.volume = Mth.lerp(Mth.clamp(speed, 0.0F, 0.5F), 0.0F, 0.5F);
        } else {
            this.pitch = 0.7F;
            this.volume = 0.05F;
        }
    }

    @Override
    public boolean canStartSilent() { return true; }

    @Override
    public boolean canPlaySound() {
        return data.morphType == EntityType.BEE && data.flying;
    }
}