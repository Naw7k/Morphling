package net.naw.morphling.mixin.player;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.naw.morphling.network.MorphlingNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Broadcasts the correct morph hurt/death sound to nearby players on the server.
 * Vanilla's getHurtSound/getDeathSound are called server-side but the result is only
 * sent to nearby clients via a sound packet — our mixin overrides the return value
 * and explicitly broadcasts to all nearby players excluding the hurt player themselves
 * (to avoid double sound, since PlayerSoundsMixin handles the local player's own sound).
 * Uses reflection to call the protected getHurtSound/getDeathSound on the morph entity
 * since LivingEntityHurtSoundAccessor is client-only and unavailable on the server.
 */
@Mixin(Player.class)
public abstract class MorphHurtSoundMixin {

    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
    private void morphling$broadcastHurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.level().isClientSide()) return;
        if (!(self instanceof ServerPlayer player)) return;

        String morphTypeId = MorphlingNetworking.playerMorphMap.get(player.getUUID());
        if (morphTypeId == null || morphTypeId.isEmpty()) return;

        try {
            var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(morphTypeId));
            var morphEntity = type.create(self.level(), EntitySpawnReason.LOAD);
            if (!(morphEntity instanceof LivingEntity livingMorph)) return;

            SoundEvent morphHurt = null;
            try {
                java.lang.reflect.Method m = LivingEntity.class.getDeclaredMethod("getHurtSound", DamageSource.class);
                m.setAccessible(true);
                morphHurt = (SoundEvent) m.invoke(livingMorph, source);
            } catch (Exception ignored) {}

            if (morphHurt == null) return;
            cir.setReturnValue(morphHurt);
            // Exclude the hurt player from the broadcast to avoid double sound
            self.level().playSound(
                    player, player.getX(), player.getY(), player.getZ(),
                    morphHurt, SoundSource.PLAYERS, 1.0F, 1.0F
            );
        } catch (Exception ignored) {}
    }

    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
    private void morphling$broadcastDeathSound(CallbackInfoReturnable<SoundEvent> cir) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.level().isClientSide()) return;
        if (!(self instanceof ServerPlayer player)) return;

        String morphTypeId = MorphlingNetworking.playerMorphMap.get(player.getUUID());
        if (morphTypeId == null || morphTypeId.isEmpty()) return;

        try {
            var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(morphTypeId));
            var morphEntity = type.create(self.level(), EntitySpawnReason.LOAD);
            if (!(morphEntity instanceof LivingEntity livingMorph)) return;

            SoundEvent morphDeath = null;
            try {
                java.lang.reflect.Method m = LivingEntity.class.getDeclaredMethod("getDeathSound");
                m.setAccessible(true);
                morphDeath = (SoundEvent) m.invoke(livingMorph);
            } catch (Exception ignored) {}

            if (morphDeath == null) return;
            cir.setReturnValue(morphDeath);
            // Exclude the dying player from the broadcast to avoid double sound
            self.level().playSound(
                    player, player.getX(), player.getY(), player.getZ(),
                    morphDeath, SoundSource.PLAYERS, 1.0F, 1.0F
            );
        } catch (Exception ignored) {}
    }
}