package net.naw.morphling.client.debug;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

public class TestSpeedCommand {



    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("testspeed")
                        .executes(ctx -> {
                            DebugSettings.setTestSpeedEnabled(!DebugSettings.isTestSpeedEnabled());
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Test speed: " + (DebugSettings.isTestSpeedEnabled() ? "ON" : "OFF")),
                                    false
                            );
                            return 1;
                        })
        );

        // Tick handler — forces nearby mobs to walk forward while active
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!DebugSettings.isTestSpeedEnabled()) return;

            for (ServerLevel level : server.getAllLevels()) {
                for (var player : level.players()) {
                    AABB area = player.getBoundingBox().inflate(30.0);
                    for (Mob mob : level.getEntitiesOfClass(Mob.class, area)) {
                        // Force max walking speed forward in mob's facing direction
                        double speed = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                        float yaw = mob.getYRot();
                        double radians = Math.toRadians(yaw);
                        double forwardX = -Math.sin(radians) * speed;
                        double forwardZ = Math.cos(radians) * speed;
                        mob.setDeltaMovement(forwardX, mob.getDeltaMovement().y, forwardZ);
                    }
                }
            }
        });
    }
}