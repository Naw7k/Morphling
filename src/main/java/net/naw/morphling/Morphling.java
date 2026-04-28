package net.naw.morphling;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.naw.morphling.client.debug.TestSpeedCommand;
import net.naw.morphling.network.MorphlingNetworking;

public class Morphling implements ModInitializer {

    @Override
    public void onInitialize() {
        MorphlingNetworking.registerCommon();
        MorphlingNetworking.registerServer();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            TestSpeedCommand.register(dispatcher);
        });
    }
}