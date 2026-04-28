package net.naw.morphling.mixin.player;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.mixin.accessors.LivingEntityDeathSoundAccessor;
import net.naw.morphling.mixin.accessors.LivingEntityHurtSoundAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerSoundsMixin {

    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
    private void morphling$overrideHurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        System.out.println("[Morphling] getHurtSound fired on player morph!");


        LivingEntity self = (LivingEntity)(Object)this;
        if (!(self instanceof Player)) return;
        if (Minecraft.getInstance().player == null) return;
        if (!self.getUUID().equals(Minecraft.getInstance().player.getUUID())) return;
        if (!MorphState.isMorphed()) return;

        Entity morphEntity = MorphState.getCachedEntity();
        if (!(morphEntity instanceof LivingEntity livingMorph)) return;

        SoundEvent morphHurt = ((LivingEntityHurtSoundAccessor)(Object) livingMorph).morphling$getHurtSound(source);
        if (morphHurt != null) cir.setReturnValue(morphHurt);
    }

    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
    private void morphling$overrideDeathSound(CallbackInfoReturnable<SoundEvent> cir) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!(self instanceof Player)) return;
        if (Minecraft.getInstance().player == null) return;
        if (!self.getUUID().equals(Minecraft.getInstance().player.getUUID())) return;
        if (!MorphState.isMorphed()) return;

        Entity morphEntity = MorphState.getCachedEntity();
        if (!(morphEntity instanceof LivingEntity livingMorph)) return;

        SoundEvent morphDeath = ((LivingEntityDeathSoundAccessor)(Object) livingMorph).morphling$getDeathSound();
        if (morphDeath != null) cir.setReturnValue(morphDeath);
    }
}