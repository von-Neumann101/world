package dev.stillworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.stillworld.ModContent;
import dev.stillworld.Stillworld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.joml.Matrix4f;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** Solid fractured material plus a screen-aligned aperture into the shared live scene. */
public final class RealityBladeRenderer extends BlockEntityWithoutLevelRenderer {
    static final ResourceLocation MATERIAL=Stillworld.id("textures/item/stillworld_blade.png");
    static final ResourceLocation APERTURE=Stillworld.id("textures/item/stillworld_blade_aperture.png");
    private static final ResourceLocation ICON=Stillworld.id("textures/item/stillworld_blade_icon.png");
    private static final float FRONT=.502f,BACK=.498f;
    private record Window(PoseStack.Pose pose,Matrix4f view,Matrix4f projection,int color){}
    private record Edge(float x1,float y1,float x2,float y2,float u,float v,float nx,float ny){}
    private static final List<Window> WINDOWS=new ArrayList<>();
    private static List<Edge> edges=List.of();
    private static int handDraws,leftDraws,worldDraws,wearerDraws,guiDraws;

    private RealityBladeRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    static void registerExtensions(RegisterClientExtensionsEvent event){
        event.registerItem(new IClientItemExtensions(){
            private final RealityBladeRenderer renderer=new RealityBladeRenderer();
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){return renderer;}
        },ModContent.BLADE.get());
    }
    static void registerReloads(RegisterClientReloadListenersEvent event){
        event.registerReloadListener((ResourceManagerReloadListener)resources->{
            clear();
            try(var input=resources.getResourceOrThrow(MATERIAL).open();var image=NativeImage.read(input)){
                var result=new ArrayList<Edge>();int width=image.getWidth(),height=image.getHeight();
                for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(solid(image,x,y)){
                    float x1=(float)x/width,x2=(float)(x+1)/width,y1=1f-(float)(y+1)/height,y2=1f-(float)y/height;
                    float u=(x+.5f)/width,v=(y+.5f)/height;
                    if(!solid(image,x-1,y))result.add(new Edge(x1,y1,x1,y2,u,v,-1,0));
                    if(!solid(image,x+1,y))result.add(new Edge(x2,y2,x2,y1,u,v,1,0));
                    if(!solid(image,x,y-1))result.add(new Edge(x1,y2,x2,y2,u,v,0,1));
                    if(!solid(image,x,y+1))result.add(new Edge(x2,y1,x1,y1,u,v,0,-1));
                }
                edges=List.copyOf(result);
            }catch(IOException exception){throw new UncheckedIOException("Cannot load reality blade material",exception);}
        });
    }
    private static boolean solid(NativeImage image,int x,int y){return x>=0&&y>=0&&x<image.getWidth()&&y<image.getHeight()&&(image.getPixelRGBA(x,y)>>>24)>=128;}

    /** Called only for the main level; nested reality rendering must preserve this frame's windows. */
    public static void beginWorldFrame(){WINDOWS.clear();RealityOrbRenderer.invalidateBladeFrame();}
    static boolean needsReality(){
        if(!WINDOWS.isEmpty())return true;
        var mc=Minecraft.getInstance();
        return mc.player!=null&&!mc.options.hideGui&&mc.options.getCameraType().isFirstPerson()
                &&(mc.player.getMainHandItem().is(ModContent.BLADE)||mc.player.getOffhandItem().is(ModContent.BLADE));
    }
    static int handDraws(){return handDraws;}
    static int leftDraws(){return leftDraws;}
    static int worldDraws(){return worldDraws;}
    static int wearerDraws(){return wearerDraws;}
    static int guiDraws(){return guiDraws;}
    static int queuedWindows(){return WINDOWS.size();}
    static void clear(){WINDOWS.clear();}

    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,
            MultiBufferSource buffers,int light,int overlay){
        boolean preview=context==ItemDisplayContext.GUI||StillworldRendering.bladeSilhouette();
        if(preview){
            faces(pose.last(),ItemRenderer.getFoilBufferDirect(buffers,RenderType.entityCutoutNoCull(ICON),true,stack.hasFoil()),light,overlay);
            if(context==ItemDisplayContext.GUI)guiDraws++;
            return;
        }
        var material=ItemRenderer.getFoilBufferDirect(buffers,RenderType.entityCutoutNoCull(MATERIAL),true,stack.hasFoil());
        faces(pose.last(),material,light,overlay);
        for(var edge:edges){
            vertex(material,pose.last(),edge.x1,edge.y1,BACK,edge.u,edge.v,edge.nx,edge.ny,0,light,overlay);
            vertex(material,pose.last(),edge.x2,edge.y2,BACK,edge.u,edge.v,edge.nx,edge.ny,0,light,overlay);
            vertex(material,pose.last(),edge.x2,edge.y2,FRONT,edge.u,edge.v,edge.nx,edge.ny,0,light,overlay);
            vertex(material,pose.last(),edge.x1,edge.y1,FRONT,edge.u,edge.v,edge.nx,edge.ny,0,light,overlay);
        }
        // In reality these openings simply reveal the real scene already behind the sword.
        if(!StillworldClient.active()||RealityOrbRenderer.rendering())return;
        if(context.firstPerson()){
            if(RealityOrbRenderer.bladeSceneReady()){
                aperture(pose.last(),buffers.getBuffer(BladeRenderType.APERTURE),-1);handDraws++;
                if(context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND)leftDraws++;
            }
        }else{
            WINDOWS.add(new Window(pose.last().copy(),new Matrix4f(RenderSystem.getModelViewMatrix()),
                    new Matrix4f(RenderSystem.getProjectionMatrix()),StillworldRendering.bladeTint()));
        }
    }

    /** Replay after world desaturation, before shared refraction, with original depth and matrices. */
    static void renderWorldWindows(){
        try{
            if(WINDOWS.isEmpty()||!RealityOrbRenderer.bladeSceneReady())return;
            var oldProjection=new Matrix4f(RenderSystem.getProjectionMatrix());
            var view=RenderSystem.getModelViewStack();view.pushMatrix();
            var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
            try{
                for(var window:WINDOWS){
                    view.set(window.view);RenderSystem.applyModelViewMatrix();
                    RenderSystem.setProjectionMatrix(window.projection,VertexSorting.DISTANCE_TO_ORIGIN);
                    var type=window.color==-1?BladeRenderType.APERTURE:BladeRenderType.TRANSLUCENT_APERTURE;
                    aperture(window.pose,buffers.getBuffer(type),window.color);
                    buffers.endBatch(type);worldDraws++;
                    if(window.color!=-1)wearerDraws++;
                }
            }finally{
                view.popMatrix();RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(oldProjection,VertexSorting.DISTANCE_TO_ORIGIN);
            }
        }finally{WINDOWS.clear();}
    }
    private static void faces(PoseStack.Pose pose,VertexConsumer out,int light,int overlay){
        vertex(out,pose,0,0,FRONT,0,1,0,0,1,light,overlay);vertex(out,pose,1,0,FRONT,1,1,0,0,1,light,overlay);
        vertex(out,pose,1,1,FRONT,1,0,0,0,1,light,overlay);vertex(out,pose,0,1,FRONT,0,0,0,0,1,light,overlay);
        vertex(out,pose,1,0,BACK,1,1,0,0,-1,light,overlay);vertex(out,pose,0,0,BACK,0,1,0,0,-1,light,overlay);
        vertex(out,pose,0,1,BACK,0,0,0,0,-1,light,overlay);vertex(out,pose,1,1,BACK,1,0,0,0,-1,light,overlay);
    }
    private static void aperture(PoseStack.Pose pose,VertexConsumer out,int color){
        // One double-sided plane: translucent wearers must not blend the opening twice.
        for(int i=0;i<4;i++){
            float x=i==1||i==2?1:0,y=i>=2?1:0;
            out.addVertex(pose,x,y,.5f).setColor(color).setUv(x,1-y).setOverlay(0).setLight(0).setNormal(pose,0,0,1);
        }
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,float x,float y,float z,float u,float v,
            float nx,float ny,float nz,int light,int overlay){
        out.addVertex(pose,x,y,z).setColor(255,255,255,255).setUv(u,v).setOverlay(overlay).setLight(light).setNormal(pose,nx,ny,nz);
    }
}
