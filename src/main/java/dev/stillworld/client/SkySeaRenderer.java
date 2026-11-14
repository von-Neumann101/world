package dev.stillworld.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** A world-oriented sea of rigid sky plates. It never writes depth or participates in the snapshot. */
final class SkySeaRenderer extends RenderType {
    private static final int CELL=24, RADIUS=24;
    private static final float SKY_SCALE=.125f;
    private static final RenderType PLATES=create("stillworld_sky_sea",DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,8192,false,true,CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL)
                    .createCompositeState(false));

    static void render(RenderLevelStageEvent event) {
        var mc=Minecraft.getInstance();
        var view=event.getCamera();
        var camera=view.getPosition();
        var fog=view.getFluidInCamera();
        // AFTER_SKY fires even when vanilla suppresses the sky. Preserve those visibility rules.
        if(mc.level.effects().skyType()==DimensionSpecialEffects.SkyType.NONE
                ||mc.level.effects().isFoggyAt(Mth.floor(camera.x),Mth.floor(camera.y))
                ||mc.gui.getBossOverlay().shouldCreateWorldFog()
                ||fog==FogType.LAVA||fog==FogType.POWDER_SNOW
                ||(view.getEntity() instanceof LivingEntity living
                    &&(living.hasEffect(MobEffects.BLINDNESS)||living.hasEffect(MobEffects.DARKNESS))))return;
        float time=StillworldPostEffect.elapsedSeconds();
        float arrival=(float)Mth.smoothstep(Mth.clamp(time-1.4f,0,1));
        if(arrival<=0)return;
        int cellX=Mth.floor(camera.x/CELL),cellZ=Mth.floor(camera.z/CELL);
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());
        var modelView=RenderSystem.getModelViewStack();
        float[] color=RenderSystem.getShaderColor().clone();
        modelView.pushMatrix();modelView.identity().mul(event.getModelViewMatrix());RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(),VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.setShaderColor(1,1,1,1);
        try {
            var buffers=mc.renderBuffers().bufferSource();
            var vertices=buffers.getBuffer(PLATES);
            for(int dz=-RADIUS;dz<=RADIUS;dz++)for(int dx=-RADIUS;dx<=RADIUS;dx++) {
                int gx=cellX+dx,gz=cellZ+dz;
                long seed=seed(gx,gz);
                int divisions=(seed&3)==0?2:1;
                double spacing=(double)CELL/divisions;
                for(int iz=0;iz<divisions;iz++)for(int ix=0;ix<divisions;ix++) {
                    long part=seed(gx*2+ix,gz*2+iz);
                    double x=gx*(double)CELL+(ix+.5)*spacing,z=gz*(double)CELL+(iz+.5)*spacing;
                    float size=(float)(spacing*(.54+.34*((part>>>8)&255)/255.0));
                    double margin=(spacing-size)*.45;
                    x+=(((part>>>40)&255)/127.5-1)*margin;
                    z+=(((part>>>48)&255)/127.5-1)*margin;
                    float radial=(float)(Math.hypot(x-camera.x,z-camera.z)/CELL);
                    float fade=1-(float)Mth.smoothstep(Mth.clamp((radial-(RADIUS-5))/5,0,1));
                    if(fade>0)plate(vertices,x,z,x-camera.x,z-camera.z,size,time,arrival*fade,part);
                }
            }
            buffers.endBatch(PLATES);
        } finally {
            RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            modelView.popMatrix();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
        }
    }

    private static void plate(VertexConsumer out,double worldX,double worldZ,double x,double z,
                              float size,float time,float opacity,long seed) {
        double a=worldX*.012+worldZ*.006-time*.45,b=worldZ*.021-worldX*.004+time*.28;
        double y=96+10*Math.sin(a)+6*Math.sin(b);
        double slopeX=.12*Math.cos(a)-.024*Math.cos(b),slopeZ=.06*Math.cos(a)+.126*Math.cos(b);
        // Shared wave height and slope keep each panel rigid while the whole surface undulates.
        int shade=Mth.clamp((int)(66+((seed>>>24)&63)+slopeZ*100),38,156);
        int tint=((int)(opacity*.93f*255)<<24)|(shade<<16)|(shade<<8)|shade;
        float half=size*.5f;
        vertex(out,x-half,y-slopeX*half-slopeZ*half,z-half,tint);
        vertex(out,x-half,y-slopeX*half+slopeZ*half,z+half,tint);
        vertex(out,x+half,y+slopeX*half+slopeZ*half,z+half,tint);
        vertex(out,x+half,y+slopeX*half-slopeZ*half,z-half,tint);
    }

    private static void vertex(VertexConsumer out,double x,double y,double z,int tint) {
        // Uniform skybox scaling preserves perspective and keeps the sea inside short-view clip planes.
        out.addVertex((float)x*SKY_SCALE,(float)y*SKY_SCALE,(float)z*SKY_SCALE).setColor(tint);
    }

    private static long seed(int x,int z) {
        long value=x*341873128712L+z*132897987541L;
        value=(value^(value>>>33))*0xff51afd7ed558ccdL;
        return value^(value>>>33);
    }

    private SkySeaRenderer() {super("stillworld_sky_sea",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,8192,false,false,()->{},()->{});}
}
