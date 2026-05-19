package net.naw.morphling.mixin.player;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.client.debug.SoundConfig;
import net.naw.morphling.client.sounds.MorphStepSounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerStepSoundMixin {

    // Gallop sound counter — mirrors vanilla AbstractHorse.gallopSoundCounter logic
    // Gallop only plays every 3rd step after the 5th, regular clop plays for the first 5
    @Unique
    private int morphling$gallopCounter = 0;

    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void morphling$overrideStepSound(BlockPos onPos, BlockState onState, CallbackInfo ci) {
        if (!net.minecraft.client.Minecraft.getInstance().isSameThread()) return;
        Player self = (Player)(Object)this;
        if (Minecraft.getInstance().player == null) return;
        if (!self.getUUID().equals(Minecraft.getInstance().player.getUUID())) return;
        if (!MorphState.isMorphed()) return;

// Slime has no step sound
        if (MorphState.getCurrentMorph() == EntityType.SLIME || MorphState.getCurrentMorph() == EntityType.BEE) {
            ci.cancel();
            return;
        }

        Entity morphEntity = MorphState.getCachedEntity();
        if (!(morphEntity instanceof LivingEntity livingMorph)) return;

        // Try morph-specific step sound first
        net.minecraft.sounds.SoundEvent morphStep = MorphStepSounds.getStepSound(morphEntity);

        if (morphStep != null) {
            // Horse gallop — rate-limited to match vanilla AbstractHorse behavior
            net.minecraft.sounds.SoundEvent gallopSound = MorphStepSounds.getGallopSound(morphEntity);
            if (gallopSound != null && self.isSprinting()) {
                morphling$gallopCounter++;
                if (morphling$gallopCounter > 5 && morphling$gallopCounter % 2 == 0) {
                    // Full gallop after 5 steps, every 3rd step
                    morphStep = gallopSound;
                } else if (morphling$gallopCounter <= 5) {
                    // First 5 steps: play quiet clop instead
                    morphStep = MorphStepSounds.getStepSound(morphEntity);
                } else {
                    // Skip — not on a gallop step
                    ci.cancel();
                    return;
                }
            } else {
                // Not sprinting — reset counter
                morphling$gallopCounter = 0;
            }

            // Check for per-mob volume override
            float overrideVolume = MorphStepSounds.getStepVolume(morphEntity);
            float volume = (overrideVolume >= 0 ? overrideVolume : 0.15F) * SoundConfig.stepVolumeMultiplier;

            self.level().playLocalSound(
                    self.getX(), self.getY(), self.getZ(),
                    morphStep,
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    volume, 1.0F, false
            );

            MorphState.broadcastSound(morphStep, volume, 1.0F);

        } else {
            // Not sprinting or no gallop — reset counter
            if (MorphState.getCurrentMorph() == EntityType.HORSE) {
                morphling$gallopCounter = 0;
            }

            // Fallback: use block's step sound, tuned by morph size
            net.minecraft.world.level.block.SoundType soundType = onState.getSoundType();
            float height = livingMorph.getBbHeight();
            // Quieter for smaller/stealthy mobs, a bit softer pitch variation
            float volume = 0.1F * Math.max(0.5F, height / 1.8F) * net.naw.morphling.client.debug.SoundConfig.stepVolumeMultiplier;
            float pitch = 0.9F + (height / 1.8F) * 0.2F; // 0.9 to ~1.1 range

            self.level().playLocalSound(
                    self.getX(), self.getY(), self.getZ(),
                    soundType.getStepSound(),
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    volume, pitch, false
            );

            MorphState.broadcastSound(soundType.getStepSound(), volume, pitch);
        }

        ci.cancel();
    }
}