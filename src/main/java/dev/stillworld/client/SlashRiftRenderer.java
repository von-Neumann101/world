package dev.stillworld.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** World-anchored slit into the same live scene used by the orb and blade. */
final class SlashRiftRenderer {
    static boolean visible(RenderLevelStageEvent event) {
        for(var slash:StillworldClient.slashes())if(visible(slash,event))return true;
        return false;
    }
    private static boolean visible(StillworldRendering.Slash slash,RenderLevelStageEvent event) {
        // Rotation-independent bound for the existing 4 x 3 plane.
        return event.getFrustum().isVisible(new AABB(slash.center(),slash.center()).inflate(2.6));
    }
    static void render(RenderLevelStageEvent event) {
        if(!RealityOrbRenderer.bladeSceneReady()||StillworldClient.slashes().isEmpty())return;
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());
        var view=RenderSystem.getModelViewStack();view.pushMatrix();
        view.identity().mul(event.getModelViewMatrix());RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(),VertexSorting.DISTANCE_TO_ORIGIN);
        var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
        var camera=event.getCamera().getPosition();var pose=new PoseStack();
        try {
            for(var slash:StillworldClient.slashes()) {
                if(!visible(slash,event))continue;
                pose.pushPose();
                pose.translate(slash.center().x-camera.x,slash.center().y-camera.y,slash.center().z-camera.z);
                pose.mulPose(Axis.YP.rotationDegrees(-slash.yaw()));
                pose.mulPose(Axis.XP.rotationDegrees(slash.pitch()));
                var out=buffers.getBuffer(SlashRiftRenderType.RIFT);
                // A single double-sided surface avoids blending two copies of the live scene.
                for(int i=0;i<4;i++) {
                    float u=i==1||i==2?1:0,v=i>=2?1:0;
                    out.addVertex(pose.last(),u*4-2,v*3-1.5f,0).setColor(-1).setUv(u,v)
                            .setOverlay(0).setLight(0).setNormal(pose.last(),0,0,1);
                }
                pose.popPose();
            }
            buffers.endBatch(SlashRiftRenderType.RIFT);
        } finally {
            view.popMatrix();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
        }
    }
    private SlashRiftRenderer(){}
}
