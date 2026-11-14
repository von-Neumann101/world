package dev.stillworld.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Automated clients retain logical game input without taking control of the desktop pointer. */
@Mixin(MouseHandler.class)
public abstract class AutomatedTestMouseMixin {
    @Unique
    private static final boolean stillworld$automatedClient=Boolean.getBoolean("stillworld.automatedClient");

    @Redirect(method={"grabMouse","releaseMouse"},at=@At(value="INVOKE",
            target="Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(JIDD)V"))
    private void stillworld$leaveDesktopPointerFree(long window,int mode,double x,double y) {
        if(stillworld$automatedClient) {
            // Do not call the vanilla helper: it warps the cursor even when releasing it.
            GLFW.glfwSetInputMode(window,GLFW.GLFW_CURSOR,GLFW.GLFW_CURSOR_NORMAL);
        } else InputConstants.grabOrReleaseMouse(window,mode,x,y);
    }

    @Inject(method="onMove",at=@At("HEAD"),cancellable=true)
    private void stillworld$ignoreDesktopMotion(long window,double x,double y,CallbackInfo ci) {
        // The harness aims the camera itself; moving the user's free pointer must not turn it.
        if(stillworld$automatedClient)ci.cancel();
    }
}
