package dev.stillworld.client.workshop;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/** Uses vanilla's animated water sprite, with a transparent surface and shaded water-column edges. */
final class OceanMiniatureSurface {
    private static final ResourceLocation WATER=ResourceLocation.withDefaultNamespace("block/water_still");
    private static final float SURFACE=1.72f,BOTTOM=.36f;
    static void render(PoseStack pose,MultiBufferSource buffers) {
        var sprite=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(WATER);
        var out=buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        var matrix=pose.last();
        for(int x=0;x<6;x++)for(int z=0;z<6;z++) {
            vertex(out,matrix,sprite,x,SURFACE,z,0,0,0,1,0,55,135,225,185);
            vertex(out,matrix,sprite,x,SURFACE,z+1,0,1,0,1,0,55,135,225,185);
            vertex(out,matrix,sprite,x+1,SURFACE,z+1,1,1,0,1,0,55,135,225,185);
            vertex(out,matrix,sprite,x+1,SURFACE,z,1,0,0,1,0,55,135,225,185);
        }
        // The four side strips meet only at edges; no overlapping glass slab or interior water faces.
        for(int i=0;i<6;i++) {
            side(out,matrix,sprite,i,0,i+1,0,0,0,-1);
            side(out,matrix,sprite,i+1,6,i,6,0,0,1);
            side(out,matrix,sprite,0,i+1,0,i,-1,0,0);
            side(out,matrix,sprite,6,i,6,i+1,1,0,0);
        }
    }
    private static void side(VertexConsumer out,PoseStack.Pose pose,TextureAtlasSprite sprite,float x0,float z0,float x1,float z1,float nx,float ny,float nz) {
        vertex(out,pose,sprite,x0,BOTTOM,z0,0,1,nx,ny,nz,42,100,150,150);
        vertex(out,pose,sprite,x0,SURFACE,z0,0,0,nx,ny,nz,55,135,225,140);
        vertex(out,pose,sprite,x1,SURFACE,z1,1,0,nx,ny,nz,55,135,225,140);
        vertex(out,pose,sprite,x1,BOTTOM,z1,1,1,nx,ny,nz,42,100,150,150);
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,TextureAtlasSprite sprite,float x,float y,float z,float u,float v,float nx,float ny,float nz,int r,int g,int b,int a) {
        // Offset the entire water perimeter from the sand's x/z=0/6 faces, including the top's shared edges.
        out.addVertex(pose,edge(x),y,edge(z)).setColor(r,g,b,a).setUv(sprite.getU(u),sprite.getV(v))
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose,nx,ny,nz);
    }
    private static float edge(float coordinate) {return coordinate==0?-.02f:coordinate==6?6.02f:coordinate;}
    private OceanMiniatureSurface() {}
}
