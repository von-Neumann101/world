package dev.stillworld.mixin.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.stillworld.client.RealityWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Empty mirror sections need no GPU objects until vanilla uploads a rendered layer. */
@Mixin(VertexBuffer.class)
public abstract class MirrorVertexBufferMixin {
    @Shadow private int vertexBufferId;
    @Shadow private int indexBufferId;
    @Shadow private int arrayObjectId;
    @Unique private boolean stillworld$deferred;

    @Redirect(method="<init>",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/GlStateManager;_glGenBuffers()I"))
    private int stillworld$bufferHandle() {
        return RealityWorld.deferVertexBuffers()?0:GlStateManager._glGenBuffers();
    }
    @Redirect(method="<init>",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/GlStateManager;_glGenVertexArrays()I"))
    private int stillworld$arrayHandle() {
        return RealityWorld.deferVertexBuffers()?0:GlStateManager._glGenVertexArrays();
    }
    @Inject(method="<init>",at=@At("RETURN"))
    private void stillworld$rememberDeferred(CallbackInfo ci) { stillworld$deferred=RealityWorld.deferVertexBuffers(); }

    @Inject(method="bind",at=@At("HEAD"))
    private void stillworld$allocateOnUse(CallbackInfo ci) {
        if(!stillworld$deferred)return;
        RenderSystem.assertOnRenderThread();
        vertexBufferId=GlStateManager._glGenBuffers();
        indexBufferId=GlStateManager._glGenBuffers();
        arrayObjectId=GlStateManager._glGenVertexArrays();
        stillworld$deferred=false;
    }

    @Inject(method="close",at=@At("HEAD"))
    private void stillworld$closeUnused(CallbackInfo ci) {
        if(!stillworld$deferred)return;
        // -1 is vanilla's closed sentinel. Pending uploads must not resurrect an exited session.
        vertexBufferId=indexBufferId=arrayObjectId=-1;
        stillworld$deferred=false;
    }
}
