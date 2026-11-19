package com.example.seethrough.mixin;

import com.example.seethrough.SeeThrough;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void seethrough$update(BlockView area, Entity focused, boolean thirdPerson,
                                   boolean inverted, float tickDelta, CallbackInfo ci) {
        if (SeeThrough.freecam) {
            setRotation(SeeThrough.camYaw, SeeThrough.camPitch);
            setPos(SeeThrough.camPos.x, SeeThrough.camPos.y, SeeThrough.camPos.z);
        }
    }
}
