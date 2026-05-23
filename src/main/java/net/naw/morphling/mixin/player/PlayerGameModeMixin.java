package net.naw.morphling.mixin.player;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.naw.morphling.client.core.MorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.player.LocalPlayer.class)
public abstract class PlayerGameModeMixin {

    @Inject(method = "onGameModeChanged", at = @At("RETURN"))
    private void morphling$onGameModeChanged(GameType gameType, CallbackInfo ci) {
        if (gameType == GameType.SPECTATOR) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (MorphState.isMorphed()) {
            MorphState.sendMorphSync(MorphState.getCurrentMorph());
            MorphState.refreshPlayerSize();
        }
    }
}