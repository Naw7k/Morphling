package net.naw.morphling.client.core;

import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

public interface MorphDataProvider {
    @Nullable EntityType<?> morphling$getSavedMorph();
    void morphling$setSavedMorph(@Nullable EntityType<?> type);

    @Nullable String morphling$getSavedVariants();
    void morphling$setSavedVariants(@Nullable String variants);
}