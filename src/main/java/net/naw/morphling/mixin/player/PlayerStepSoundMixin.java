package net.naw.morphling.mixin.player;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.client.sounds.MorphStepSounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerStepSoundMixin {

    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void morphling$overrideStepSound(BlockPos pos, BlockState state, CallbackInfo ci) {
        Player self = (Player)(Object)this;
        if (Minecraft.getInstance().player == null) return;
        if (!self.getUUID().equals(Minecraft.getInstance().player.getUUID())) return;
        if (!MorphState.isMorphed()) return;

        Entity morphEntity = MorphState.getCachedEntity();
        if (!(morphEntity instanceof LivingEntity livingMorph)) return;

        // Try morph-specific step sound first
        net.minecraft.sounds.SoundEvent morphStep = MorphStepSounds.getStepSound(morphEntity);

        if (morphStep != null) {
            // Check for per-mob volume override
            float overrideVolume = MorphStepSounds.getStepVolume(morphEntity);
            float volume = overrideVolume >= 0 ? overrideVolume : 0.15F;

            self.level().playLocalSound(
                    self.getX(), self.getY(), self.getZ(),
                    morphStep,
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    volume, 1.0F, false
            );


        } else {
            // Fallback: use block's step sound, tuned by morph size
            net.minecraft.world.level.block.SoundType soundType = state.getSoundType();
            float height = livingMorph.getBbHeight();
            // Quieter for smaller/stealthy mobs, a bit softer pitch variation
            float volume = 0.1F * Math.max(0.5F, height / 1.8F);
            float pitch = 0.9F + (height / 1.8F) * 0.2F; // 0.9 to ~1.1 range
            self.level().playLocalSound(
                    self.getX(), self.getY(), self.getZ(),
                    soundType.getStepSound(),
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    volume, pitch, false
            );
        }


        ci.cancel();
    }
}