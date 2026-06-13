package net.naw.morphling.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.naw.morphling.client.core.MorphDataProvider;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Central networking hub for Morphling.

 * All packets are defined here as inner records.
 * Packet flow:
 *   Client → Server: MorphRequestPayload, AbilityActionPayload, AbilityStatePayload,
 *                    SoundBroadcastPayload, HealthRequestPayload, DamageRequestPayload
 *   Server → Client: HandshakePayload, MorphSyncPayload, AbilitySyncPayload,
 *                    SoundAtPlayerPayload, HealthUpdatePayload

 * Server-side player state:
 *   playerMorphMap   — UUID → entityTypeId
 *   playerVariantMap — UUID → String[17] (variants, index 15 = frogVariant, index 16 = pandaGene)

 * Ability actions are handled in AbilityActionHandler.
 */
public class MorphlingNetworking {

    public static final java.util.Map<UUID, String> playerMorphMap = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<UUID, String[]> playerVariantMap = new java.util.concurrent.ConcurrentHashMap<>();

    public static final java.util.Set<UUID> axolotlPlayingDead = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // ─── Server → Client ────────────────────────────────────────────────────

    public record HandshakePayload() implements CustomPacketPayload {
        public static final Type<HandshakePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "handshake"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HandshakePayload> CODEC = StreamCodec.unit(new HandshakePayload());
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ── Fox variant = index 12, Rabbit = index 13, Axolotl = index 14, Frog = index 15, Panda gene = index 16 ──
    public record MorphSyncPayload(
            UUID playerUuid, String entityTypeId,
            String parrotVariant, String catVariant, String wolfVariant, String cowVariant,
            String sheepColor, String pigVariant, String chickenVariant,
            String horseColor, String horseMarkings,
            String villagerProfession, String villagerType, String slimeSize,
            String foxVariant, String rabbitVariant, String axolotlVariant, String frogVariant,
            String pandaGene
    ) implements CustomPacketPayload {

        public static final Type<MorphSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "morph_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MorphSyncPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUUID(p.playerUuid()); buf.writeUtf(p.entityTypeId());
                    buf.writeUtf(p.parrotVariant() != null ? p.parrotVariant() : "");
                    buf.writeUtf(p.catVariant() != null ? p.catVariant() : "");
                    buf.writeUtf(p.wolfVariant() != null ? p.wolfVariant() : "");
                    buf.writeUtf(p.cowVariant() != null ? p.cowVariant() : "");
                    buf.writeUtf(p.sheepColor() != null ? p.sheepColor() : "");
                    buf.writeUtf(p.pigVariant() != null ? p.pigVariant() : "");
                    buf.writeUtf(p.chickenVariant() != null ? p.chickenVariant() : "");
                    buf.writeUtf(p.horseColor() != null ? p.horseColor() : "");
                    buf.writeUtf(p.horseMarkings() != null ? p.horseMarkings() : "");
                    buf.writeUtf(p.villagerProfession() != null ? p.villagerProfession() : "");
                    buf.writeUtf(p.villagerType() != null ? p.villagerType() : "");
                    buf.writeUtf(p.slimeSize() != null ? p.slimeSize() : "2");
                    buf.writeUtf(p.foxVariant() != null ? p.foxVariant() : "RED");
                    buf.writeUtf(p.rabbitVariant() != null ? p.rabbitVariant() : "BROWN");
                    buf.writeUtf(p.axolotlVariant() != null ? p.axolotlVariant() : "LUCY");
                    buf.writeUtf(p.frogVariant() != null ? p.frogVariant() : "");
                    buf.writeUtf(p.pandaGene() != null ? p.pandaGene() : "NORMAL");
                },
                buf -> new MorphSyncPayload(
                        buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf() // 19 reads total
                )
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record AbilitySyncPayload(UUID playerUuid, String abilityKey, String value) implements CustomPacketPayload {
        public static final Type<AbilitySyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "ability_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AbilitySyncPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUUID(p.playerUuid()); buf.writeUtf(p.abilityKey()); buf.writeUtf(p.value()); },
                buf -> new AbilitySyncPayload(buf.readUUID(), buf.readUtf(), buf.readUtf())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SoundAtPlayerPayload(UUID playerUuid, String soundId, float volume, float pitch) implements CustomPacketPayload {
        public static final Type<SoundAtPlayerPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "sound_at_player"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SoundAtPlayerPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUUID(p.playerUuid()); buf.writeUtf(p.soundId()); buf.writeFloat(p.volume()); buf.writeFloat(p.pitch()); },
                buf -> new SoundAtPlayerPayload(buf.readUUID(), buf.readUtf(), buf.readFloat(), buf.readFloat())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record HealthUpdatePayload(float maxHealth, float currentHealth) implements CustomPacketPayload {
        public static final Type<HealthUpdatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "health_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HealthUpdatePayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeFloat(p.maxHealth()); buf.writeFloat(p.currentHealth()); },
                buf -> new HealthUpdatePayload(buf.readFloat(), buf.readFloat())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ─── Server → Client: restore saved morph on join ───────────────────────
    // Sent to the client on join if they have a saved morph in their NBT.
    public record MorphRestorePayload(String entityTypeId, String variants) implements CustomPacketPayload {
        public static final Type<MorphRestorePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "morph_restore"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MorphRestorePayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUtf(p.entityTypeId()); buf.writeUtf(p.variants() != null ? p.variants() : ""); },
                buf -> new MorphRestorePayload(buf.readUtf(), buf.readUtf())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ─── Client → Server: save morph to NBT on dedicated server ─────────────
    public record SaveMorphPayload(String entityTypeId, String variants) implements CustomPacketPayload {
        public static final Type<SaveMorphPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "save_morph"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SaveMorphPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUtf(p.entityTypeId()); buf.writeUtf(p.variants() != null ? p.variants() : ""); },
                buf -> new SaveMorphPayload(buf.readUtf(), buf.readUtf())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ─── Client → Server ────────────────────────────────────────────────────

    // ── Fox variant = index 12, Rabbit = index 13, Axolotl = index 14, Frog = index 15, Panda gene = index 16 ──
    public record MorphRequestPayload(
            String entityTypeId,
            String parrotVariant, String catVariant, String wolfVariant, String cowVariant,
            String sheepColor, String pigVariant, String chickenVariant,
            String horseColor, String horseMarkings,
            String villagerProfession, String villagerType, String slimeSize,
            String foxVariant, String rabbitVariant, String axolotlVariant, String frogVariant,
            String pandaGene
    ) implements CustomPacketPayload {

        public static final Type<MorphRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "morph_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MorphRequestPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.entityTypeId());
                    buf.writeUtf(p.parrotVariant() != null ? p.parrotVariant() : "");
                    buf.writeUtf(p.catVariant() != null ? p.catVariant() : "");
                    buf.writeUtf(p.wolfVariant() != null ? p.wolfVariant() : "");
                    buf.writeUtf(p.cowVariant() != null ? p.cowVariant() : "");
                    buf.writeUtf(p.sheepColor() != null ? p.sheepColor() : "");
                    buf.writeUtf(p.pigVariant() != null ? p.pigVariant() : "");
                    buf.writeUtf(p.chickenVariant() != null ? p.chickenVariant() : "");
                    buf.writeUtf(p.horseColor() != null ? p.horseColor() : "");
                    buf.writeUtf(p.horseMarkings() != null ? p.horseMarkings() : "");
                    buf.writeUtf(p.villagerProfession() != null ? p.villagerProfession() : "");
                    buf.writeUtf(p.villagerType() != null ? p.villagerType() : "");
                    buf.writeUtf(p.slimeSize() != null ? p.slimeSize() : "2");
                    buf.writeUtf(p.foxVariant() != null ? p.foxVariant() : "RED");
                    buf.writeUtf(p.rabbitVariant() != null ? p.rabbitVariant() : "BROWN");
                    buf.writeUtf(p.axolotlVariant() != null ? p.axolotlVariant() : "LUCY");
                    buf.writeUtf(p.frogVariant() != null ? p.frogVariant() : "");
                    buf.writeUtf(p.pandaGene() != null ? p.pandaGene() : "NORMAL");
                },
                buf -> new MorphRequestPayload(
                        buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf() // 18 reads total
                )
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record AbilityActionPayload(String action, String data) implements CustomPacketPayload {
        public static final Type<AbilityActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "ability_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AbilityActionPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUtf(p.action()); buf.writeUtf(p.data()); },
                buf -> new AbilityActionPayload(buf.readUtf(), buf.readUtf())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SoundBroadcastPayload(String soundId, float volume, float pitch) implements CustomPacketPayload {
        public static final Type<SoundBroadcastPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "sound_broadcast"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SoundBroadcastPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUtf(p.soundId()); buf.writeFloat(p.volume()); buf.writeFloat(p.pitch()); },
                buf -> new SoundBroadcastPayload(buf.readUtf(), buf.readFloat(), buf.readFloat())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record DamageRequestPayload(float damage) implements CustomPacketPayload {
        public static final Type<DamageRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "damage_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DamageRequestPayload> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeFloat(p.damage()),
                buf -> new DamageRequestPayload(buf.readFloat())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record AbilityStatePayload(String abilityKey, String value) implements CustomPacketPayload {
        public static final Type<AbilityStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "ability_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AbilityStatePayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUtf(p.abilityKey()); buf.writeUtf(p.value()); },
                buf -> new AbilityStatePayload(buf.readUtf(), buf.readUtf())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record HealthRequestPayload(float maxHealth, float healthRatio) implements CustomPacketPayload {
        public static final Type<HealthRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "health_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HealthRequestPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeFloat(p.maxHealth()); buf.writeFloat(p.healthRatio()); },
                buf -> new HealthRequestPayload(buf.readFloat(), buf.readFloat())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ─── Registration ────────────────────────────────────────────────────────

    public static void registerCommon() {
        PayloadTypeRegistry.clientboundPlay().register(HandshakePayload.TYPE, HandshakePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MorphSyncPayload.TYPE, MorphSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AbilitySyncPayload.TYPE, AbilitySyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SoundAtPlayerPayload.TYPE, SoundAtPlayerPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HealthUpdatePayload.TYPE, HealthUpdatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MorphRequestPayload.TYPE, MorphRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AbilityActionPayload.TYPE, AbilityActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AbilityStatePayload.TYPE, AbilityStatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SoundBroadcastPayload.TYPE, SoundBroadcastPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HealthRequestPayload.TYPE, HealthRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DamageRequestPayload.TYPE, DamageRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SaveMorphPayload.TYPE, SaveMorphPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MorphRestorePayload.TYPE, MorphRestorePayload.CODEC);
    }

    public static void registerServer() {

        // On join: send handshake and sync all existing morphs to the new player
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, _, minecraftServer) -> {
            ServerPlayNetworking.send(handler.player, new HandshakePayload());

            // Delay morph restore by one tick so NBT is fully loaded first
            minecraftServer.execute(() -> {
                String savedMorphId = ((MorphDataProvider) handler.player).morphling$getSavedMorph() != null
                        ? BuiltInRegistries.ENTITY_TYPE.getKey(
                        Objects.requireNonNull(((MorphDataProvider) handler.player).morphling$getSavedMorph())).toString()
                        : "";
                if (!savedMorphId.isEmpty()) {
                    String savedVariants = ((MorphDataProvider) handler.player).morphling$getSavedVariants();
                    ServerPlayNetworking.send(handler.player, new MorphRestorePayload(savedMorphId, savedVariants != null ? savedVariants : ""));
                }
            });

            for (Map.Entry<UUID, String> entry : playerMorphMap.entrySet()) {
                if (entry.getKey().equals(handler.player.getUUID())) continue;
                String[] variants = playerVariantMap.getOrDefault(entry.getKey(), new String[]{"", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""});
                ServerPlayNetworking.send(handler.player, new MorphSyncPayload(
                        entry.getKey(), entry.getValue(),
                        variants[0], variants[1], variants[2], variants[3], variants[4], variants[5], variants[6],
                        variants.length > 7  ? variants[7]  : "",
                        variants.length > 8  ? variants[8]  : "",
                        variants.length > 9  ? variants[9]  : "",
                        variants.length > 10 ? variants[10] : "",
                        variants.length > 11 ? variants[11] : "2",
                        variants.length > 12 ? variants[12] : "RED",
                        variants.length > 13 ? variants[13] : "BROWN",
                        variants.length > 14 ? variants[14] : "LUCY",
                        variants.length > 15 ? variants[15] : "",       // frog variant
                        variants.length > 16 ? variants[16] : "NORMAL"  // panda gene
                ));
            }
        });

        // On disconnect: notify all remaining players that this player unmorphed
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID leftUuid = handler.player.getUUID();
            axolotlPlayingDead.remove(leftUuid);
            handler.player.removeEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
            MorphSyncPayload syncPayload = new MorphSyncPayload(leftUuid, "", "", "", "", "", "", "", "", "", "", "", "", "2", "RED", "BROWN", "LUCY", "", "NORMAL");
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(other, syncPayload);
            }
        });

        // Morph change — update server state, refresh hitbox, broadcast to others
        ServerPlayNetworking.registerGlobalReceiver(MorphRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer senderPlayer = context.player();

            if (payload.entityTypeId() == null || payload.entityTypeId().isEmpty()) {
                playerMorphMap.remove(senderPlayer.getUUID());
            } else {
                playerMorphMap.put(senderPlayer.getUUID(), payload.entityTypeId());
            }

            context.server().execute(() -> {
                senderPlayer.refreshDimensions();
                senderPlayer.setBoundingBox(senderPlayer.getDimensions(senderPlayer.getPose())
                        .makeBoundingBox(senderPlayer.position()));
            });

            context.server().execute(() -> {
                String morphId = payload.entityTypeId();
                senderPlayer.getAbilities().mayfly = senderPlayer.isCreative() || (morphId != null && (
                        morphId.contains("parrot") || morphId.contains("bee") ||
                                morphId.contains("bat") || morphId.contains("phantom") ||
                                morphId.contains("blaze") || morphId.contains("allay") ||
                                morphId.contains("ghast") || morphId.contains("vex")
                ));
                senderPlayer.getAbilities().flying = false;
                senderPlayer.onUpdateAbilities();
            });

            // Store all variants including fox (12), rabbit (13), axolotl (14), frog (15), panda gene (16)
            playerVariantMap.put(senderPlayer.getUUID(), new String[]{
                    payload.parrotVariant(), payload.catVariant(), payload.wolfVariant(),
                    payload.cowVariant(), payload.sheepColor(), payload.pigVariant(), payload.chickenVariant(),
                    payload.horseColor(), payload.horseMarkings(),
                    payload.villagerProfession(), payload.villagerType(), payload.slimeSize(),
                    payload.foxVariant(),      // index 12
                    payload.rabbitVariant(),   // index 13
                    payload.axolotlVariant(),  // index 14
                    payload.frogVariant(),     // index 15
                    payload.pandaGene()        // index 16
            });

            MorphSyncPayload syncPayload = new MorphSyncPayload(
                    senderPlayer.getUUID(), payload.entityTypeId(),
                    payload.parrotVariant(), payload.catVariant(), payload.wolfVariant(),
                    payload.cowVariant(), payload.sheepColor(), payload.pigVariant(), payload.chickenVariant(),
                    payload.horseColor(), payload.horseMarkings(),
                    payload.villagerProfession(), payload.villagerType(), payload.slimeSize(),
                    payload.foxVariant(), payload.rabbitVariant(), payload.axolotlVariant(),
                    payload.frogVariant(), payload.pandaGene()
            );
            for (ServerPlayer other : context.server().getPlayerList().getPlayers()) {
                if (other == senderPlayer) continue;
                ServerPlayNetworking.send(other, syncPayload);
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(AbilityStatePayload.TYPE, (payload, context) -> {
            ServerPlayer senderPlayer = context.player();

            // Store axolotl play dead state server-side for mob targeting
            if ("axolotl_playdead".equals(payload.abilityKey())) {
                if (Boolean.parseBoolean(payload.value())) {
                    axolotlPlayingDead.add(senderPlayer.getUUID());
                } else {
                    axolotlPlayingDead.remove(senderPlayer.getUUID());
                }
            }

            AbilitySyncPayload syncPayload = new AbilitySyncPayload(
                    senderPlayer.getUUID(), payload.abilityKey(), payload.value()
            );
            for (ServerPlayer other : context.server().getPlayerList().getPlayers()) {
                if (other == senderPlayer) continue;
                ServerPlayNetworking.send(other, syncPayload);
            }
        });

        // Ability action or respawn refresh
        ServerPlayNetworking.registerGlobalReceiver(AbilityActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();

            if (payload.action().equals("respawn_refresh")) {
                context.server().execute(() -> {
                    player.refreshDimensions();
                    String morphTypeId = playerMorphMap.get(player.getUUID());
                    if (morphTypeId != null && !morphTypeId.isEmpty()) {
                        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(morphTypeId));
                        ((MorphDataProvider) player).morphling$setSavedMorph(type);
                        String[] variants = playerVariantMap.getOrDefault(player.getUUID(), new String[]{"", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""});
                        MorphSyncPayload syncPayload = new MorphSyncPayload(
                                player.getUUID(), morphTypeId,
                                variants[0], variants[1], variants[2], variants[3], variants[4], variants[5], variants[6],
                                variants.length > 7  ? variants[7]  : "",
                                variants.length > 8  ? variants[8]  : "",
                                variants.length > 9  ? variants[9]  : "",
                                variants.length > 10 ? variants[10] : "",
                                variants.length > 11 ? variants[11] : "2",
                                variants.length > 12 ? variants[12] : "RED",
                                variants.length > 13 ? variants[13] : "BROWN",
                                variants.length > 14 ? variants[14] : "LUCY",
                                variants.length > 15 ? variants[15] : "",       // frog variant
                                variants.length > 16 ? variants[16] : "NORMAL"  // panda gene
                        );
                        for (ServerPlayer other : context.server().getPlayerList().getPlayers()) {
                            if (other == player) continue;
                            ServerPlayNetworking.send(other, syncPayload);
                        }
                    }

                    // Send all other players' morphs to the respawning player
                    for (ServerPlayer other : context.server().getPlayerList().getPlayers()) {
                        if (other == player) continue;
                        String otherMorphId = playerMorphMap.get(other.getUUID());
                        if (otherMorphId != null && !otherMorphId.isEmpty()) {
                            String[] variants = playerVariantMap.getOrDefault(other.getUUID(), new String[]{"", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""});
                            ServerPlayNetworking.send(player, new MorphSyncPayload(
                                    other.getUUID(), otherMorphId,
                                    variants[0], variants[1], variants[2], variants[3], variants[4], variants[5], variants[6],
                                    variants.length > 7  ? variants[7]  : "",
                                    variants.length > 8  ? variants[8]  : "",
                                    variants.length > 9  ? variants[9]  : "",
                                    variants.length > 10 ? variants[10] : "",
                                    variants.length > 11 ? variants[11] : "2",
                                    variants.length > 12 ? variants[12] : "RED",
                                    variants.length > 13 ? variants[13] : "BROWN",
                                    variants.length > 14 ? variants[14] : "LUCY",
                                    variants.length > 15 ? variants[15] : "",       // frog variant
                                    variants.length > 16 ? variants[16] : "NORMAL"  // panda gene
                            ));
                        }
                    }
                });
                return;
            }

            context.server().execute(() -> AbilityActionHandler.handle(player, payload.action(), payload.data()));
        });

        // Sound broadcast — relay to all other players
        ServerPlayNetworking.registerGlobalReceiver(SoundBroadcastPayload.TYPE, (payload, context) -> {
            ServerPlayer senderPlayer = context.player();
            SoundAtPlayerPayload soundPayload = new SoundAtPlayerPayload(
                    senderPlayer.getUUID(), payload.soundId(), payload.volume(), payload.pitch()
            );
            for (ServerPlayer other : context.server().getPlayerList().getPlayers()) {
                if (other == senderPlayer) continue;
                ServerPlayNetworking.send(other, soundPayload);
            }
        });

        // Health request — apply morph max health on the server
        ServerPlayNetworking.registerGlobalReceiver(HealthRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();

            context.server().execute(() -> {
                // Mob Brawl owns health for players in an active fight — don't let the morph
                // health system re-add the morph modifier (would override Equal/Double mode,
                // especially on respawn where this fires right after the brawl heal).
                var brawl = net.naw.morphling.client.games.MobBrawl.MobBrawlServerGame.getByPlayer(player.getUUID());
                if (brawl != null
                        && brawl.getPhase() == net.naw.morphling.client.games.MobBrawl.MobBrawlServerGame.Phase.FIGHTING
                        && brawl.getHealthMode() != 0) {
                    return;
                }
                AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);

                if (attr == null) return;

                Identifier modifierId = Identifier.fromNamespaceAndPath("morphling", "morph_health");
                attr.removeModifier(modifierId);
                float modifier = payload.maxHealth() - 20.0F;
                if (modifier != 0F) {
                    attr.addTransientModifier(new AttributeModifier(
                            modifierId, modifier,
                            AttributeModifier.Operation.ADD_VALUE
                    ));
                }

                float newHealth = Math.clamp(payload.healthRatio() * player.getMaxHealth(), 1.0F, player.getMaxHealth());
                player.setHealth(newHealth);

                String morphTypeId = playerMorphMap.get(player.getUUID());
                if (morphTypeId != null && !morphTypeId.isEmpty()) {
                    try {
                        EntityType<?> dmgType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(morphTypeId));
                        var morphEntity = dmgType.create(player.level(), EntitySpawnReason.LOAD);
                        if (morphEntity instanceof LivingEntity morphLiving) {
                            var morphDamage = morphLiving.getAttribute(Attributes.ATTACK_DAMAGE);
                            var playerDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
                            if (morphDamage != null && playerDamage != null) {
                                playerDamage.setBaseValue(morphDamage.getBaseValue());
                            }
                        }
                    } catch (Exception ignored) {}
                } else {
                    var playerDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
                    if (playerDamage != null) playerDamage.setBaseValue(1.0);
                }

                ServerPlayNetworking.send(player, new HealthUpdatePayload(player.getMaxHealth(), newHealth));
            });
        });

        // Save morph to NBT — dedicated server path
        ServerPlayNetworking.registerGlobalReceiver(SaveMorphPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (payload.entityTypeId().isEmpty()) {
                    ((MorphDataProvider) player).morphling$setSavedMorph(null);
                } else {
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(payload.entityTypeId()));
                    ((MorphDataProvider) player).morphling$setSavedMorph(type);
                    ((MorphDataProvider) player).morphling$setSavedVariants(payload.variants());
                }
            });
        });

        // Damage request — non-host players sync attack damage
        ServerPlayNetworking.registerGlobalReceiver(DamageRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                var playerDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
                if (playerDamage != null) {
                    playerDamage.setBaseValue(payload.damage());
                }
            });
        });
    }
}
