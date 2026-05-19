package net.naw.morphling.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * Central networking hub for Morphling.

 * All packets are defined here as inner records. Each packet has a TYPE and CODEC.
 * Packet flow:
 *   Client → Server: MorphRequestPayload, AbilityActionPayload, AbilityStatePayload,
 *                    SoundBroadcastPayload, HealthRequestPayload, DamageRequestPayload
 *   Server → Client: HandshakePayload, MorphSyncPayload, AbilitySyncPayload,
 *                    SoundAtPlayerPayload, HealthUpdatePayload

 * Server-side player state is tracked in:
 *   playerMorphMap   — UUID → entityTypeId (current morph)
 *   playerVariantMap — UUID → String[12] (parrot, cat, wolf, cow, sheep, pig, chicken, horseColor, horseMarkings, villagerProfession, villagerType, slimeSize)
 */
public class MorphlingNetworking {

    // Server-side map of each player's current morph — used for damage scaling, hitbox, fall damage
    public static final java.util.Map<UUID, String> playerMorphMap = new java.util.concurrent.ConcurrentHashMap<>();

    // Server-side map of each player's current variant selections (index matches MorphSyncPayload field order)
    // Indices: 0=parrot, 1=cat, 2=wolf, 3=cow, 4=sheep, 5=pig, 6=chicken, 7=horseColor, 8=horseMarkings, 9=villagerProfession, 10=villagerType, 11=slimeSize
    public static final java.util.Map<UUID, String[]> playerVariantMap = new java.util.concurrent.ConcurrentHashMap<>();

    // ─── Handshake (server → client) ────────────────────────────────────────
    // Sent to the client on join to confirm the server has Morphling installed.
    // Client sets serverHasMorphling = true on receipt, enabling multiplayer features.

    public record HandshakePayload() implements CustomPacketPayload {
        public static final Type<HandshakePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "handshake"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HandshakePayload> CODEC =
                StreamCodec.unit(new HandshakePayload());
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ─── Morph sync (server → client) ───────────────────────────────────────
    // Broadcast to all other players when someone morphs or unmorphs.
    // Empty entityTypeId = unmorphed. Also carries all variant data.

    public record MorphSyncPayload(
            UUID playerUuid,
            String entityTypeId,
            String parrotVariant,
            String catVariant,
            String wolfVariant,
            String cowVariant,
            String sheepColor,
            String pigVariant,
            String chickenVariant,
            String horseColor,
            String horseMarkings,
            String villagerProfession,
            String villagerType,
            String slimeSize
    ) implements CustomPacketPayload {
        public static final Type<MorphSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "morph_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MorphSyncPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUUID(p.playerUuid());
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
                },
                buf -> new MorphSyncPayload(
                        buf.readUUID(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf()
                )
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ─── Ability sync (server → client) ─────────────────────────────────────
    // Broadcast to other players when a morph ability state changes (e.g. flying, sitting, angry).
    // Key-value format — see AbilitySyncPayload handler in MorphlingClient for all supported keys.

    public record AbilitySyncPayload(
            UUID playerUuid,
            String abilityKey,
            String value
    ) implements CustomPacketPayload {
        public static final Type<AbilitySyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "ability_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AbilitySyncPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUUID(p.playerUuid()); buf.writeUtf(p.abilityKey()); buf.writeUtf(p.value()); },
                buf -> new AbilitySyncPayload(buf.readUUID(), buf.readUtf(), buf.readUtf())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ─── Client → Server: morph change ──────────────────────────────────────
    // Sent when the local player selects a new morph or unmorphs.
    // Server updates playerMorphMap, refreshes hitbox, and broadcasts MorphSyncPayload to others.

    public record MorphRequestPayload(
            String entityTypeId,
            String parrotVariant,
            String catVariant,
            String wolfVariant,
            String cowVariant,
            String sheepColor,
            String pigVariant,
            String chickenVariant,
            String horseColor,
            String horseMarkings,
            String villagerProfession,
            String villagerType,
            String slimeSize
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
                },
                buf -> new MorphRequestPayload(
                        buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf()
                )
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ─── Client → Server: server-side ability action ────────────────────────
    // Used for abilities that require actual server-side world interaction:
    // creeper explosions, enderman teleport/block pickup, zombie door breaking,
    // sheep eating, iron golem knockback, skeleton bow equip/unequip, etc.

    public record AbilityActionPayload(String action, String data) implements CustomPacketPayload {
        public static final Type<AbilityActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "ability_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AbilityActionPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUtf(p.action()); buf.writeUtf(p.data()); },
                buf -> new AbilityActionPayload(buf.readUtf(), buf.readUtf())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ─── Client → Server: broadcast a sound to other players ────────────────
    // Client sends this when it wants to play a sound at its position for others.
    // Server relays it as SoundAtPlayerPayload to all other players.

    public record SoundBroadcastPayload(String soundId, float volume, float pitch) implements CustomPacketPayload {
        public static final Type<SoundBroadcastPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "sound_broadcast"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SoundBroadcastPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUtf(p.soundId()); buf.writeFloat(p.volume()); buf.writeFloat(p.pitch()); },
                buf -> new SoundBroadcastPayload(buf.readUtf(), buf.readFloat(), buf.readFloat())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ─── Server → Client: play a sound at a player's position ───────────────
    // Received by clients to play a morph sound at a specific remote player's location.

    public record SoundAtPlayerPayload(UUID playerUuid, String soundId, float volume, float pitch) implements CustomPacketPayload {
        public static final Type<SoundAtPlayerPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "sound_at_player"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SoundAtPlayerPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUUID(p.playerUuid()); buf.writeUtf(p.soundId()); buf.writeFloat(p.volume()); buf.writeFloat(p.pitch()); },
                buf -> new SoundAtPlayerPayload(buf.readUUID(), buf.readUtf(), buf.readFloat(), buf.readFloat())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ─── Client → Server: sync morph attack damage ──────────────────────────
    // Sent by non-host players when their morph's attack damage changes.
    // Only sent when the value actually changes (tracked via lastSentDamage in MorphState).

    public record DamageRequestPayload(float damage) implements CustomPacketPayload {
        public static final Type<DamageRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "damage_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DamageRequestPayload> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeFloat(p.damage()),
                buf -> new DamageRequestPayload(buf.readFloat())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ─── Client → Server: visual ability state change ───────────────────────
    // Sent when a morph ability state changes that other players need to see visually
    // (e.g. flying, sitting, angry mode). Server broadcasts as AbilitySyncPayload.

    public record AbilityStatePayload(String abilityKey, String value) implements CustomPacketPayload {
        public static final Type<AbilityStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "ability_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AbilityStatePayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUtf(p.abilityKey()); buf.writeUtf(p.value()); },
                buf -> new AbilityStatePayload(buf.readUtf(), buf.readUtf())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ─── Client → Server: request health change for morph ───────────────────
    // Sent on morph/unmorph to apply the correct max health on the server.
    // maxHealth = morph's real HP (e.g. 4 for chicken, 100 for iron golem).
    // healthRatio = current HP as a fraction, used to scale HP proportionally.

    public record HealthRequestPayload(float maxHealth, float healthRatio) implements CustomPacketPayload {
        public static final Type<HealthRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "health_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HealthRequestPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeFloat(p.maxHealth()); buf.writeFloat(p.healthRatio()); },
                buf -> new HealthRequestPayload(buf.readFloat(), buf.readFloat())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ─── Server → Client: update health display for morphed player ──────────
    // Sent back to the client after the server applies the health change,
    // so the client can smoothly transition to the confirmed values.

    public record HealthUpdatePayload(float maxHealth, float currentHealth) implements CustomPacketPayload {
        public static final Type<HealthUpdatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morphling", "health_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HealthUpdatePayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeFloat(p.maxHealth()); buf.writeFloat(p.currentHealth()); },
                buf -> new HealthUpdatePayload(buf.readFloat(), buf.readFloat())
        );
        @Override public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ─── Registration ────────────────────────────────────────────────────────

    /** Register all payload types. Must be called on both client and server (common init). */
    public static void registerCommon() {
        PayloadTypeRegistry.clientboundPlay().register(HandshakePayload.TYPE, HandshakePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MorphSyncPayload.TYPE, MorphSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AbilitySyncPayload.TYPE, AbilitySyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SoundAtPlayerPayload.TYPE, SoundAtPlayerPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MorphRequestPayload.TYPE, MorphRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AbilityActionPayload.TYPE, AbilityActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AbilityStatePayload.TYPE, AbilityStatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SoundBroadcastPayload.TYPE, SoundBroadcastPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HealthUpdatePayload.TYPE, HealthUpdatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HealthRequestPayload.TYPE, HealthRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DamageRequestPayload.TYPE, DamageRequestPayload.CODEC);
    }

    /** Register server-side packet handlers and connection events. Called on server init only. */
    public static void registerServer() {

        // On player join: send handshake, then sync all existing morphs to the new player
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, _, _) -> {
            ServerPlayNetworking.send(handler.player, new HandshakePayload());

            for (java.util.Map.Entry<UUID, String> entry : playerMorphMap.entrySet()) {
                if (entry.getKey().equals(handler.player.getUUID())) continue;
                String[] variants = playerVariantMap.getOrDefault(entry.getKey(), new String[]{"", "", "", "", "", "", "", "", "", "", "", ""});
                MorphSyncPayload syncPayload = new MorphSyncPayload(
                        entry.getKey(), entry.getValue(),
                        variants[0], variants[1], variants[2], variants[3], variants[4], variants[5], variants[6],
                        variants.length > 7 ? variants[7] : "",
                        variants.length > 8 ? variants[8] : "",
                        variants.length > 9 ? variants[9] : "",
                        variants.length > 10 ? variants[10] : "",
                        variants.length > 11 ? variants[11] : "2"
                );
                ServerPlayNetworking.send(handler.player, syncPayload);
            }
        });

        // On player disconnect: notify all remaining players that this player has left/unmorphed
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID leftUuid = handler.player.getUUID();
            MorphSyncPayload syncPayload = new MorphSyncPayload(leftUuid, "", "", "", "", "", "", "", "", "", "", "", "", "2");
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(other, syncPayload);
            }
        });

        // Player changed morph — update server state, refresh hitbox, broadcast to others
        ServerPlayNetworking.registerGlobalReceiver(MorphRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer senderPlayer = context.player();

            if (payload.entityTypeId() == null || payload.entityTypeId().isEmpty()) {
                playerMorphMap.remove(senderPlayer.getUUID());
            } else {
                playerMorphMap.put(senderPlayer.getUUID(), payload.entityTypeId());
            }

            // Refresh hitbox server-side so block collision matches morph size
            context.server().execute(() -> {
                senderPlayer.refreshDimensions();
                senderPlayer.setBoundingBox(senderPlayer.getDimensions(senderPlayer.getPose())
                        .makeBoundingBox(senderPlayer.position()));
            });

            // Grant or revoke flight ability based on morph
            context.server().execute(() -> {
                String morphId = payload.entityTypeId();
                senderPlayer.getAbilities().mayfly = morphId != null && (
                        morphId.contains("parrot") || morphId.contains("bee") ||
                                morphId.contains("bat") || morphId.contains("phantom") ||
                                morphId.contains("blaze") || morphId.contains("allay") ||
                                morphId.contains("ghast") || morphId.contains("vex")
                );
                senderPlayer.getAbilities().flying = false;
                senderPlayer.onUpdateAbilities();
            });

            playerVariantMap.put(senderPlayer.getUUID(), new String[]{
                    payload.parrotVariant(), payload.catVariant(), payload.wolfVariant(),
                    payload.cowVariant(), payload.sheepColor(), payload.pigVariant(), payload.chickenVariant(),
                    payload.horseColor(), payload.horseMarkings(),
                    payload.villagerProfession(), payload.villagerType(),
                    payload.slimeSize()
            });

            MorphSyncPayload syncPayload = new MorphSyncPayload(
                    senderPlayer.getUUID(), payload.entityTypeId(),
                    payload.parrotVariant(), payload.catVariant(), payload.wolfVariant(),
                    payload.cowVariant(), payload.sheepColor(), payload.pigVariant(), payload.chickenVariant(),
                    payload.horseColor(), payload.horseMarkings(),
                    payload.villagerProfession(), payload.villagerType(),
                    payload.slimeSize()
            );
            for (ServerPlayer other : context.server().getPlayerList().getPlayers()) {
                if (other == senderPlayer) continue;
                ServerPlayNetworking.send(other, syncPayload);
            }
        });

        // Visual ability state changed — relay to all other players
        ServerPlayNetworking.registerGlobalReceiver(AbilityStatePayload.TYPE, (payload, context) -> {
            ServerPlayer senderPlayer = context.player();
            AbilitySyncPayload syncPayload = new AbilitySyncPayload(
                    senderPlayer.getUUID(), payload.abilityKey(), payload.value()
            );
            for (ServerPlayer other : context.server().getPlayerList().getPlayers()) {
                if (other == senderPlayer) continue;
                ServerPlayNetworking.send(other, syncPayload);
            }
        });

        // Server-side ability action or respawn refresh
        ServerPlayNetworking.registerGlobalReceiver(AbilityActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();

            // Special case: respawn_refresh — re-applies hitbox and resyncs morph to others after death
            if (payload.action().equals("respawn_refresh")) {
                context.server().execute(() -> {
                    player.refreshDimensions();
                    String morphTypeId = playerMorphMap.get(player.getUUID());
                    if (morphTypeId != null && !morphTypeId.isEmpty()) {
                        String[] variants = playerVariantMap.getOrDefault(player.getUUID(), new String[]{"", "", "", "", "", "", "", "", "", "", "", ""});
                        MorphSyncPayload syncPayload = new MorphSyncPayload(
                                player.getUUID(), morphTypeId,
                                variants[0], variants[1], variants[2], variants[3], variants[4], variants[5], variants[6],
                                variants.length > 7 ? variants[7] : "",
                                variants.length > 8 ? variants[8] : "",
                                variants.length > 9 ? variants[9] : "",
                                variants.length > 10 ? variants[10] : "",
                                variants.length > 11 ? variants[11] : "2"
                        );
                        for (ServerPlayer other : context.server().getPlayerList().getPlayers()) {
                            if (other == player) continue;
                            ServerPlayNetworking.send(other, syncPayload);
                        }
                    }
                });
                return;
            }

            context.server().execute(() -> handleAbilityAction(player, payload.action(), payload.data()));
        });

        // Sound broadcast — relay to all other players at the sender's position
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

        // Health request — apply morph max health and attack damage on the server
        ServerPlayNetworking.registerGlobalReceiver(HealthRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                AttributeInstance attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (attr == null) return;

                // Apply max health modifier (ADD_VALUE relative to base 20)
                Identifier modifierId = Identifier.fromNamespaceAndPath("morphling", "morph_health");
                attr.removeModifier(modifierId);
                float modifier = payload.maxHealth() - 20.0F;
                if (modifier != 0F) {
                    attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            modifierId, modifier,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
                    ));
                }

                // Scale current health proportionally to new max
                float newHealth = Math.clamp(payload.healthRatio() * player.getMaxHealth(), 1.0F, player.getMaxHealth());
                player.setHealth(newHealth);

                // Also apply morph attack damage server-side
                String morphTypeId = playerMorphMap.get(player.getUUID());
                if (morphTypeId != null && !morphTypeId.isEmpty()) {
                    try {
                        EntityType<?> dmgType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(morphTypeId));
                        var morphEntity = dmgType.create(player.level(), EntitySpawnReason.LOAD);
                        if (morphEntity instanceof net.minecraft.world.entity.LivingEntity morphLiving) {
                            var morphDamage = morphLiving.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                            var playerDamage = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                            if (morphDamage != null && playerDamage != null) {
                                playerDamage.setBaseValue(morphDamage.getBaseValue());
                            }
                        }
                    } catch (Exception ignored) {}
                } else {
                    // Unmorphed — restore default attack damage
                    var playerDamage = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                    if (playerDamage != null) playerDamage.setBaseValue(1.0);
                }

                ServerPlayNetworking.send(player, new HealthUpdatePayload(player.getMaxHealth(), newHealth));
            });
        });

        // Damage request — non-host players send this when their morph attack damage changes
        ServerPlayNetworking.registerGlobalReceiver(DamageRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                var playerDamage = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                if (playerDamage != null) {
                    playerDamage.setBaseValue(payload.damage());
                }
            });
        });
    }

    /**
     * Handles server-side ability actions sent via AbilityActionPayload.
     * All world mutations (explosions, block changes, entity modifications) happen here.
     */
    private static void handleAbilityAction(ServerPlayer player, String action, String data) {
        switch (action) {
            case "creeper_explode" -> player.level().explode(
                    player, player.getX(), player.getY(), player.getZ(),
                    3.0F, Level.ExplosionInteraction.MOB
            );
            case "chicken_egg" -> {
                net.minecraft.world.phys.Vec3 look = player.getLookAngle();
                net.minecraft.world.entity.item.ItemEntity egg = new net.minecraft.world.entity.item.ItemEntity(
                        player.level(),
                        player.getX() - look.x, player.getY() + 0.1, player.getZ() - look.z,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EGG)
                );
                egg.setDeltaMovement(
                        (player.getRandom().nextDouble() - 0.5) * 0.05, 0.05,
                        (player.getRandom().nextDouble() - 0.5) * 0.05
                );
                player.level().addFreshEntity(egg);
            }
            case "zombie_break_door" -> {
                try {
                    String[] p = data.split(",");
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(
                            Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])
                    );
                    net.minecraft.world.level.block.state.BlockState state = player.level().getBlockState(pos);
                    if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                        player.level().levelEvent(1021, pos, 0);
                        player.level().levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
                        player.level().removeBlock(pos, false);
                    }
                } catch (Exception ignored) {}
            }
            case "sheep_heal" -> { if (player.getHealth() < player.getMaxHealth()) player.heal(0.5F); }
            case "sheep_hunger" -> {
                var food = player.getFoodData();
                food.setFoodLevel(Math.min(food.getFoodLevel() + 1, 20));
            }
            case "sheep_grass" -> {
                try {
                    String[] p = data.split(",");
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(
                            Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])
                    );
                    if (player.level().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) {
                        player.level().setBlock(pos, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 3);
                        player.level().levelEvent(2001, pos,
                                net.minecraft.world.level.block.Block.getId(net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState()));
                    }
                } catch (Exception ignored) {}
            }
            case "dolphin_dry_damage" -> //noinspection deprecation
                    player.hurt(player.damageSources().dryOut(), 1.0F);
            case "enderman_teleport" -> {
                try {
                    String[] p = data.split(",");
                    player.teleportTo(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
                } catch (Exception ignored) {}
            }
            case "enderman_pickup" -> {
                try {
                    String[] p = data.split(",");
                    player.level().destroyBlock(new net.minecraft.core.BlockPos(
                            Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])
                    ), false);
                } catch (Exception ignored) {}
            }
            case "enderman_place" -> {
                try {
                    String[] p = data.split(",");
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(
                            Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])
                    );
                    net.minecraft.world.level.block.Block block =
                            BuiltInRegistries.BLOCK.getValue(Identifier.parse(p[3]));
                    player.level().setBlock(pos, block.defaultBlockState(), 3);
                } catch (Exception ignored) {}
            }
            case "skeleton_equip_bow" -> {
                // Place a named "Skeleton Bow" in the specified inventory slot
                try {
                    int slot = Integer.parseInt(data);
                    net.minecraft.world.item.ItemStack bow = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW);
                    bow.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Skeleton Bow"));
                    player.getInventory().setItem(slot, bow);
                    player.inventoryMenu.broadcastChanges();
                } catch (Exception ignored) {}
            }
            case "skeleton_unequip_bow" -> {
                // Remove the Skeleton Bow from the specified inventory slot
                try {
                    int slot = Integer.parseInt(data);
                    net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(slot);
                    if (stack.getItem() == net.minecraft.world.item.Items.BOW) {
                        player.getInventory().setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
                        player.inventoryMenu.broadcastChanges();
                    }
                } catch (Exception ignored) {}
            }
            case "irongolem_knockback" -> {
                // Apply upward knockback to target — higher boost for players than mobs
                try {
                    String[] p = data.split(",");
                    int entityId = Integer.parseInt(p[0]);
                    double scale = Double.parseDouble(p[1]);
                    net.minecraft.world.entity.Entity target = player.level().getEntity(entityId);
                    if (target != null) {
                        double yBoost = target instanceof net.minecraft.server.level.ServerPlayer ? 0.7 : 0.4;
                        target.setDeltaMovement(target.getDeltaMovement().add(0.0, yBoost * scale, 0.0));
                        target.hurtMarked = true;
                    }
                } catch (Exception ignored) {}
            }

            case "slime_contact_damage" -> {
                String[] parts = data.split(",");
                if (parts.length == 2) {
                    try {
                        java.util.UUID targetUuid = java.util.UUID.fromString(parts[0]);
                        float damage = Float.parseFloat(parts[1]);
                        net.minecraft.world.entity.Entity target = player.level().getEntity(targetUuid);
                        if (target instanceof net.minecraft.world.entity.LivingEntity living) {
                            //noinspection deprecation
                            living.hurt(player.level().damageSources().mobAttack(player), damage);
                        }
                    } catch (Exception ignored) {}
                }
            }

            case "bee_sting" -> {
                try {
                    java.util.UUID targetUuid = java.util.UUID.fromString(data);
                    net.minecraft.world.entity.Entity target = player.level().getEntity(targetUuid);
                    if (target instanceof net.minecraft.world.entity.LivingEntity living) {
                        //noinspection deprecation
                        living.hurt(player.level().damageSources().sting(player), 2.0F);
                        living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.POISON, 200, 0), player);
                    }
                } catch (Exception ignored) {}
            }
        }
    }
}
