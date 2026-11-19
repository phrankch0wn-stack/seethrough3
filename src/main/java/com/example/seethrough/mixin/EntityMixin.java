package com.example.seethrough.mixin;

import com.example.seethrough.SeeThrough;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void seethrough$look(double dx, double dy, CallbackInfo ci) {
        if (SeeThrough.freecam && (Object) this == MinecraftClient.getInstance().player) {
            SeeThrough.camYaw += (float) dx * 0.15f;
            SeeThrough.camPitch = MathHelper.clamp(SeeThrough.camPitch + (float) dy * 0.15f, -90f, 90f);
            ci.cancel();
        }
    }
}
