package net.naw.morphling;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.naw.morphling.client.debug.TestSpeedCommand;
import net.naw.morphling.network.MorphlingNetworking;

public class Morphling implements ModInitializer {

    @Override
    public void onInitialize() {
        // Register common and server-side networking (payloads, handlers, events)
        MorphlingNetworking.registerCommon();
        MorphlingNetworking.registerServer();

        // Register debug commands
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) ->
                TestSpeedCommand.register(dispatcher));
    }
}