package net.naw.morphling.mixin.bee;

import net.minecraft.client.model.animal.bee.BeeModel;
import net.minecraft.client.renderer.entity.state.BeeRenderState;
import net.minecraft.world.entity.EntityType;
import net.naw.morphling.client.core.MorphState;
import net.naw.morphling.mixin.accessors.BeeModelWingAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeeModel.class)
public class BeeModelMixin {

    @Inject(method = "setupAnim*", at = @At("TAIL"))
    private void morphling$restingWingAngle(BeeRenderState state, CallbackInfo ci) {
        boolean isMorphedBee = MorphState.getCurrentMorph() == EntityType.BEE;
        boolean isRemoteBee = false;

        if (!isMorphedBee) {
            for (net.naw.morphling.client.core.RemoteMorphState.PlayerMorphData data : net.naw.morphling.client.core.RemoteMorphState.getAllStates().values()) {
                if (data.morphType == EntityType.BEE) {
                    isRemoteBee = true;
                    break;
                }
            }
        }

        if (!isMorphedBee && !isRemoteBee) return;
        if (!state.isOnGround) return;

        BeeModelWingAccessor accessor = (BeeModelWingAccessor) this;
        accessor.morphling$getRightWing().zRot = -1.10F;
        accessor.morphling$getLeftWing().zRot = 1.10F;
        accessor.morphling$getRightWing().xRot = 0.25F;
        accessor.morphling$getLeftWing().xRot = 0.25F;
    }
}