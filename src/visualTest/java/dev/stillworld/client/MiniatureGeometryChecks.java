package dev.stillworld.client;

import dev.stillworld.client.workshop.BiomeMiniatureRenderer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import java.util.List;
import java.util.ArrayList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/** Inspects actual render geometry, catching embedded lamps with competing coplanar faces. */
final class MiniatureGeometryChecks {
    static void verify() throws ReflectiveOperationException {
        var field=BiomeMiniatureRenderer.class.getDeclaredField("SCENES");field.setAccessible(true);
        var scenes=(List<?>)field.get(null);
        var attachmentErrors=new ArrayList<String>();
        for(int biome:new int[]{4,5}) {
            var pieces=(List<?>)scenes.get(biome);int lamps=0;
            for(var piece:pieces)if(state(piece).is(Blocks.SHROOMLIGHT)) {
                lamps++;var lamp=box(piece);int canopyContacts=0;
                for(var other:pieces)if(other!=piece) {
                    var bounds=box(other);
                    if(lamp.deflate(1e-5).intersects(bounds))attachmentErrors.add("Biome "+biome+" shroomlight intersects "+state(other));
                    if((state(other).is(Blocks.WARPED_WART_BLOCK)||state(other).is(Blocks.NETHER_WART_BLOCK))&&touches(lamp,bounds))canopyContacts++;
                }
                if(canopyContacts<3)attachmentErrors.add("Biome "+biome+" shroomlight must be embedded in the canopy, not detached below it");
            }
            if(lamps==0)throw new AssertionError("Nether miniature lost its shroomlight");
        }
        var highlands=(List<?>)scenes.get(7);
        for(var flower:highlands)if(state(flower).is(Blocks.CHORUS_FLOWER)) {
            var head=box(flower);boolean joined=false;
            for(var plant:highlands)if(state(plant).is(Blocks.CHORUS_PLANT)) {
                var stem=box(plant);
                if(Math.abs(stem.maxY-head.minY)<1e-5&&Math.abs(stem.getCenter().x-head.getCenter().x)<1e-5
                        &&Math.abs(stem.getCenter().z-head.getCenter().z)<1e-5&&state(plant).getValue(BlockStateProperties.UP))joined=true;
            }
            if(!joined)attachmentErrors.add("Chorus flower must meet a centered upward-connected stem at "+head);
        }
        if(!attachmentErrors.isEmpty())throw new AssertionError(String.join("; ",attachmentErrors));
        verifyWaterFaces((List<?>)scenes.get(2));
    }
    private static boolean touches(AABB a,AABB b) {
        boolean xOverlap=Math.min(a.maxX,b.maxX)-Math.max(a.minX,b.minX)>1e-5;
        boolean yOverlap=Math.min(a.maxY,b.maxY)-Math.max(a.minY,b.minY)>1e-5;
        boolean zOverlap=Math.min(a.maxZ,b.maxZ)-Math.max(a.minZ,b.minZ)>1e-5;
        return (yOverlap&&zOverlap&&(Math.abs(a.maxX-b.minX)<1e-5||Math.abs(b.maxX-a.minX)<1e-5))
                ||(xOverlap&&zOverlap&&(Math.abs(a.maxY-b.minY)<1e-5||Math.abs(b.maxY-a.minY)<1e-5))
                ||(xOverlap&&yOverlap&&(Math.abs(a.maxZ-b.minZ)<1e-5||Math.abs(b.maxZ-a.minZ)<1e-5));
    }
    private static void verifyWaterFaces(List<?> terrain) throws ReflectiveOperationException {
        var consumer=new WaterVertices();
        var render=Class.forName("dev.stillworld.client.workshop.OceanMiniatureSurface").getDeclaredMethod("render",PoseStack.class,MultiBufferSource.class);
        render.setAccessible(true);render.invoke(null,new PoseStack(),(MultiBufferSource)type->consumer);
        if(consumer.vertices.size()!=240)throw new AssertionError("Incomplete ocean surface/perimeter mesh");
        for(int i=0;i<consumer.vertices.size();i+=4) {
            var first=consumer.vertices.get(i);double x0=first.x,x1=first.x,y0=first.y,y1=first.y,z0=first.z,z1=first.z;
            for(int j=1;j<4;j++) {var v=consumer.vertices.get(i+j);x0=Math.min(x0,v.x);x1=Math.max(x1,v.x);y0=Math.min(y0,v.y);y1=Math.max(y1,v.y);z0=Math.min(z0,v.z);z1=Math.max(z1,v.z);}
            for(var piece:terrain) {
                var b=box(piece);
                boolean xFace=Math.abs(x1-x0)<1e-5&&(Math.abs(x0-b.minX)<1e-5||Math.abs(x0-b.maxX)<1e-5)&&y1>b.minY&&y0<b.maxY&&z1>b.minZ&&z0<b.maxZ;
                boolean zFace=Math.abs(z1-z0)<1e-5&&(Math.abs(z0-b.minZ)<1e-5||Math.abs(z0-b.maxZ)<1e-5)&&y1>b.minY&&y0<b.maxY&&x1>b.minX&&x0<b.maxX;
                if(xFace||zFace)throw new AssertionError("Water side is coplanar with "+state(piece));
            }
        }
    }
    private static final class WaterVertices implements VertexConsumer {
        private final List<Vec3> vertices=new ArrayList<>();
        @Override public VertexConsumer addVertex(float x,float y,float z) {vertices.add(new Vec3(x,y,z));return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a) {return this;}
        @Override public VertexConsumer setUv(float u,float v) {return this;}
        @Override public VertexConsumer setUv1(int u,int v) {return this;}
        @Override public VertexConsumer setUv2(int u,int v) {return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z) {return this;}
    }
    private static Object value(Object piece,String name) throws ReflectiveOperationException {
        var field=piece.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(piece);
    }
    private static double number(Object piece,String name) throws ReflectiveOperationException {return ((Number)value(piece,name)).doubleValue();}
    private static BlockState state(Object piece) throws ReflectiveOperationException {return (BlockState)value(piece,"state");}
    private static AABB box(Object piece) throws ReflectiveOperationException {
        double x=number(piece,"x"),y=number(piece,"y"),z=number(piece,"z");
        return new AABB(x,y,z,x+number(piece,"sx"),y+number(piece,"sy"),z+number(piece,"sz"));
    }
    private MiniatureGeometryChecks() {}
}
