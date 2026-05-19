package net.naw.morphling.mixin.accessors;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface LocalPlayerCrouchAccessor {
    @Accessor("crouching")
    boolean morphling$isCrouching();

    @Mutable
    @Accessor("crouching")
    void morphling$setCrouching(boolean crouching);
}