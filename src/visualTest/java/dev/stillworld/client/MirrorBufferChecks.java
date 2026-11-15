package dev.stillworld.client;

import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ViewArea;

/** Inspect real GPU ownership in the pinned development runtime, without production test hooks. */
final class MirrorBufferChecks {
    static void check(boolean uploaded) throws ReflectiveOperationException {
        var field=LevelRenderer.class.getDeclaredField("viewArea");field.setAccessible(true);
        var handle=VertexBuffer.class.getDeclaredField("arrayObjectId");handle.setAccessible(true);
        var mirror=(ViewArea)field.get(RealityWorld.renderer());
        int pending=0,allocated=0;
        for(var section:mirror.sections)for(var layer:RenderType.chunkBufferLayers()) {
            var buffer=section.getBuffer(layer);
            if(buffer.isInvalid())throw new AssertionError("Active mirror contains closed mesh buffers");
            if(handle.getInt(buffer)==0)pending++;else allocated++;
        }
        if(pending==0)throw new AssertionError("Unused mirror layers allocated GPU handles eagerly");
        if(uploaded&&allocated==0)throw new AssertionError("Visible mirror geometry never allocated its GPU handles");
        if(!uploaded&&allocated!=0)throw new AssertionError("Hidden mirror allocated unused GPU handles: "+allocated);
        var main=(ViewArea)field.get(Minecraft.getInstance().levelRenderer);
        for(var section:main.sections)for(var layer:RenderType.chunkBufferLayers())
            if(handle.getInt(section.getBuffer(layer))<=0)throw new AssertionError("Main view was affected by mirror deferral");
        System.out.println("STILLWORLD MIRROR GPU: pending="+pending+", allocated="+allocated+", uploaded="+uploaded);
    }
    private MirrorBufferChecks() { }
}
