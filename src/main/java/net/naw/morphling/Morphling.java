package net.naw.morphling;

import net.fabricmc.api.ModInitializer;
import net.naw.morphling.client.games.packet.GamesNetworkingServer;
import net.naw.morphling.client.games.trivia.TriviaServerGame;
import net.naw.morphling.network.MorphlingNetworking;

public class Morphling implements ModInitializer {

    @Override
    public void onInitialize() {
        MorphlingNetworking.registerCommon();
        MorphlingNetworking.registerServer();

        //noinspection CodeBlock2Expr
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> {
            net.naw.morphling.client.debug.TestSpeedCommand.register(dispatcher);
        });

        // Register Games networking — server-only class, no client imports
        GamesNetworkingServer.registerServer();

        // Register Mob Brawl networking — server-side receivers
        net.naw.morphling.client.games.MobBrawl.MobBrawlNetworking.registerCommon();
        net.naw.morphling.client.games.MobBrawl.MobBrawlNetworking.registerServer();

        // Server tick for trivia game state — ticks all active room game instances
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(
                TriviaServerGame::tickAll);

        // Server tick for Mob Brawl game state — ticks all active brawl games
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Snapshot — cancel/end paths remove games from the registry mid-loop
            for (var game : new java.util.ArrayList<>(net.naw.morphling.client.games.MobBrawl.MobBrawlServerGame.getAllGames())) {
                // Morph select is server-authoritative: if 30s elapse without both players
                // ready, cancel the match and send both back to the room browser (phase=0).
                if (game.getPhase() == net.naw.morphling.client.games.MobBrawl.MobBrawlServerGame.Phase.MORPH_SELECT) {
                    if (game.tickMorphSelect(0.05f)) {
                        net.naw.morphling.client.games.MobBrawl.MobBrawlNetworkingServer.cancelMorphSelect(game, server);
                    }
                    continue;
                }
                boolean changed = game.tick(0.05f);
                if (changed) {
                    if (game.getPhase() == net.naw.morphling.client.games.MobBrawl.MobBrawlServerGame.Phase.ENDED) {
                        net.naw.morphling.client.games.MobBrawl.MobBrawlNetworkingServer.broadcastEnd(game, server);
                    } else {
                        net.naw.morphling.client.games.MobBrawl.MobBrawlNetworkingServer.broadcastState(game, server);
                    }
                }
            }
        });
    }
}
