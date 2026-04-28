package net.naw.morphling.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public class MorphlingNetworking {

    public record HandshakePayload() implements CustomPacketPayload {
        public static final Type<HandshakePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "handshake"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HandshakePayload> CODEC =
                StreamCodec.unit(new HandshakePayload());

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void registerCommon() {
        PayloadTypeRegistry.clientboundPlay().register(HandshakePayload.TYPE, HandshakePayload.CODEC);
    }

    public static void registerServer() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayNetworking.send(handler.player, new HandshakePayload());
        });
    }
}