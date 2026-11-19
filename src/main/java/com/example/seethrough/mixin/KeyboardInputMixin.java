package com.example.seethrough.mixin;

import com.example.seethrough.SeeThrough;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void seethrough$freeze(boolean slow, float f, CallbackInfo ci) {
        if (SeeThrough.freecam) {
            Input in = (Input) (Object) this;
            in.movementForward = 0;
            in.movementSideways = 0;
            in.jumping = false;
            in.sneaking = false;
        }
    }
}
