package dev.stillworld.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.stillworld.ModContent;
import dev.stillworld.Stillworld;
import dev.stillworld.mixin.client.RealityMinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.joml.Matrix4f;
import java.nio.file.Path;

/** Uses Minecraft's actual renderer, vertex shader and sampler binding against its live scene. */
final class BladeVisualChecks {
    static void check(Path output)throws Exception {
        var mc=Minecraft.getInstance();var original=mc.getMainRenderTarget();
        var access=(RealityMinecraftAccessor)mc;
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());
        var view=RenderSystem.getModelViewStack();view.pushMatrix();
        var target=new TextureTarget(512,512,true,Minecraft.ON_OSX);
        try(var input=mc.getResourceManager().getResourceOrThrow(RealityBladeRenderer.APERTURE).open();
                var mask=NativeImage.read(input);var live=Screenshot.takeScreenshot(RealityOrbRenderer.scene())){
            require(RealityBladeRenderer.queuedWindows()==0,"Previous world windows leaked into the next tick");
            access.stillworld$target(target);view.identity();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,1,0,1,-1,1),VertexSorting.ORTHOGRAPHIC_Z);
            Lighting.setupForFlatItems();RenderSystem.setShaderColor(1,1,1,1);
            var sword=new ItemStack(ModContent.BLADE.get());
            try(var hand=draw(target,sword,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,false)){
                hand.writeToFile(output.resolve("blade-material-live.png"));live.writeToFile(output.resolve("blade-source-live.png"));matches(hand,live,mask,false);
            }
            try(var world=draw(target,sword,ItemDisplayContext.GROUND,true)){
                matches(world,live,mask,true);world.writeToFile(output.resolve("blade-world-occlusion.png"));
            }
            sword.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);
            try(var enchanted=draw(target,sword,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,false)){
                matches(enchanted,live,mask,false);enchanted.writeToFile(output.resolve("blade-enchanted.png"));
            }
            try(var icon=draw(target,sword,ItemDisplayContext.GUI,false)){
                icon.writeToFile(output.resolve("blade-inventory.png"));
            }
            require(RealityBladeRenderer.queuedWindows()==0,"World replay retained stale windows");
            System.out.println("STILLWORLD BLADE PIXEL PASS: live samplers, material mask, world occlusion and enchanted aperture");
        }finally{
            access.stillworld$target(original);target.destroyBuffers();original.bindWrite(true);
            view.popMatrix();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
            Lighting.setupFor3DItems();RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
        }
    }
    private static NativeImage draw(TextureTarget target,ItemStack sword,ItemDisplayContext context,boolean occluder){
        target.setClearColor(.08f,.08f,.08f,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
        RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
        var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();var pose=new PoseStack();
        IClientItemExtensions.of(sword).getCustomRenderer().renderByItem(sword,context,pose,buffers,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
        buffers.endBatch();
        if(occluder){
            var out=buffers.getBuffer(RenderType.entityCutoutNoCull(Stillworld.id("textures/misc/white.png")));
            for(int i=0;i<4;i++)out.addVertex(pose.last(),i==1||i==2?.5f:0,i>=2?1:0,.7f)
                    .setColor(60,60,60,255).setUv(.5f,.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,1);
            buffers.endBatch();
        }
        if(!context.firstPerson()&&context!=ItemDisplayContext.GUI)RealityBladeRenderer.renderWorldWindows();
        return Screenshot.takeScreenshot(target);
    }
    private static void matches(NativeImage image,NativeImage live,NativeImage mask,boolean occluded){
        int matched=0,samples=0,covered=0;
        for(int y=2;y<mask.getHeight()-2;y++)for(int x=2;x<mask.getWidth()-2;x++){
            boolean inside=true;
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)if((mask.getPixelRGBA(x+dx,y+dy)>>>24)==0)inside=false;
            if(!inside)continue;
            int px=(2*x+1),py=2*y+1;
            int actual=image.getPixelRGBA(px,py);
            if(occluded&&x<mask.getWidth()/2-2){
                int r=actual&255,g=actual>>8&255,b=actual>>16&255;
                require(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<3,"Foreground leaked original world color");covered++;continue;
            }
            // gl_FragCoord addresses pixel centers, including when the live target has odd dimensions.
            int expected=live.getPixelRGBA((int)((px+.5)*live.getWidth()/image.getWidth()),
                    (int)((py+.5)*live.getHeight()/image.getHeight()));
            boolean match=true;for(int shift=0;shift<=16;shift+=8)if(Math.abs((actual>>shift&255)-(expected>>shift&255))>12)match=false;
            if(match)matched++;samples++;
        }
        require(samples>150&&matched>samples*.92,"Actual blade sampler/mask mismatch: "+matched+"/"+samples);
        if(occluded)require(covered>100,"No foreground aperture samples tested");
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
