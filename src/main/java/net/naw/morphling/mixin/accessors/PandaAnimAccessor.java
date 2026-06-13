package net.naw.morphling.mixin.accessors;

import net.minecraft.world.entity.animal.panda.Panda;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Panda.class)
public interface PandaAnimAccessor {

    // ── sitAmount ─────────────────────────────────────────────────────────────
    @Accessor("sitAmount")
    float morphling$getSitAmount();

    @Accessor("sitAmount")
    void morphling$setSitAmount(float value);

    @Accessor("sitAmountO")
    void morphling$setSitAmountO(float value);

    // ── onBackAmount ──────────────────────────────────────────────────────────
    @Accessor("onBackAmount")
    float morphling$getOnBackAmount();

    @Accessor("onBackAmount")
    void morphling$setOnBackAmount(float value);

    @Accessor("onBackAmountO")
    void morphling$setOnBackAmountO(float value);

    // ── rollAmount ────────────────────────────────────────────────────────────
    @Accessor("rollAmount")
    float morphling$getRollAmount();

    @Accessor("rollAmount")
    void morphling$setRollAmount(float value);

    @Accessor("rollAmountO")
    void morphling$setRollAmountO(float value);

    // ── rollCounter ───────────────────────────────────────────────────────────
    @Accessor("rollCounter")
    int morphling$getRollCounter();

    @Accessor("rollCounter")
    void morphling$setRollCounter(int value);

    // ── Invokers ──────────────────────────────────────────────────────────────
    @Invoker("sit")
    void morphling$sit(boolean value);

    @Invoker("roll")
    void morphling$roll(boolean value);

    @Invoker("setOnBack")
    void morphling$setOnBack(boolean value);
}