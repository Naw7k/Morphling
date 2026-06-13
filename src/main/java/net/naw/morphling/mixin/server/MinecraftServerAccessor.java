package net.naw.morphling.mixin.server;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Accessor mixin for MinecraftServer — exposes the internal levels map.
 * Used by MinecraftServerMixin to register custom brawl dimensions.
 */
@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {

    @Accessor("levels")
    Map<ResourceKey<Level>, ServerLevel> morphling$getLevels();
}