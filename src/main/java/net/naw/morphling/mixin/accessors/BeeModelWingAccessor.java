package net.naw.morphling.mixin.accessors;

import net.minecraft.client.model.animal.bee.BeeModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BeeModel.class)
public interface BeeModelWingAccessor {
    @Accessor("rightWing")
    ModelPart morphling$getRightWing();

    @Accessor("leftWing")
    ModelPart morphling$getLeftWing();
}