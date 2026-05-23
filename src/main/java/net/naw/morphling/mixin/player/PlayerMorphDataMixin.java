package net.naw.morphling.mixin.player;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.naw.morphling.client.core.MorphDataProvider;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stores the player's last selected morph in their NBT save data.
 * This allows each world to remember the player's morph independently.
 * Load/save hooks into Player's readAdditionalSaveData and addAdditionalSaveData.
 */
@Mixin(Player.class)
public abstract class PlayerMorphDataMixin implements MorphDataProvider {

    @Unique
    private @Nullable EntityType<?> morphling$savedMorph = null;

    @Unique
    private @Nullable String morphling$savedVariants = null;

    @Override
    public @Nullable EntityType<?> morphling$getSavedMorph() {
        return morphling$savedMorph;
    }

    @Override
    public void morphling$setSavedMorph(@Nullable EntityType<?> type) {
        this.morphling$savedMorph = type;
    }

    @Override
    public @Nullable String morphling$getSavedVariants() {
        return morphling$savedVariants;
    }

    @Override
    public void morphling$setSavedVariants(@Nullable String variants) {
        this.morphling$savedVariants = variants;
    }

    // Load saved morph from player NBT on world join/respawn
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void morphling$onLoad(ValueInput input, CallbackInfo ci) {
        String id = input.getStringOr("morphling_morph", "");
        if (id.isEmpty()) {
            morphling$savedMorph = null;
        } else {
            morphling$savedMorph = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));
        }
        morphling$savedVariants = input.getStringOr("morphling_variants", "");
    }

    // Save current morph to player NBT on world save/quit
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void morphling$onSave(ValueOutput output, CallbackInfo ci) {
        if (morphling$savedMorph != null) {
            output.putString("morphling_morph", BuiltInRegistries.ENTITY_TYPE.getKey(morphling$savedMorph).toString());
        } else {
            output.putString("morphling_morph", "");
        }
        output.putString("morphling_variants", morphling$savedVariants != null ? morphling$savedVariants : "");
    }
}