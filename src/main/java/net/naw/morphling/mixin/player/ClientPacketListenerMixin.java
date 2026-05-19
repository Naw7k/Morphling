package net.naw.morphling.mixin.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.naw.morphling.client.core.RemoteMorphState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleSoundEvent", at = @At("HEAD"), cancellable = true)
    private void morphling$cancelRemoteMorphStepPositional(ClientboundSoundPacket packet, CallbackInfo ci) {
        String soundPath = packet.getSound().value().location().getPath();
        if (!soundPath.contains("step")) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Cancel vanilla step sounds near remote morphed players
        for (net.minecraft.world.entity.player.Player p : mc.level.players()) {
            if (p == mc.player) continue;
            RemoteMorphState.PlayerMorphData data = RemoteMorphState.get(p.getUUID());
            if (data == null || data.morphType == null) continue;

            double dx = p.getX() - packet.getX();
            double dy = p.getY() - packet.getY();
            double dz = p.getZ() - packet.getZ();
            if (dx * dx + dy * dy + dz * dz < 4.0) {
                ci.cancel();
                return;
            }
        }
    }
}