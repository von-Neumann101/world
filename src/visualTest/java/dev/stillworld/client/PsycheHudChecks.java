package dev.stillworld.client;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Compare the actual composed HUD with its live framebuffer, not a mocked texture. */
final class PsycheHudChecks {
    static void visible(Path output,String name,float minimum,float maximum) throws Exception {
        var mc=Minecraft.getInstance();
        float radius=PsycheView.windowRadius();
        require(radius>=minimum&&radius<=maximum,"Unexpected energy aperture radius: "+radius);
        require(RealityOrbRenderer.scene()!=null,"HUD did not render reality while inside the entry orb");
        require(!RealityOrbRenderer.rendering()&&!RealityWorld.isMirror(mc.level),"HUD leaked the live render context");
        double scale=mc.getWindow().getGuiScale();
        int cx=(int)(PsycheView.WINDOW_X*scale),cy=(int)(PsycheView.WINDOW_Y*scale);
        int samples=0,matched=0,colored=0,sourceColored=0;
        try(var main=Screenshot.takeScreenshot(mc.getMainRenderTarget());var live=Screenshot.takeScreenshot(RealityOrbRenderer.scene())) {
            int extent=Math.max(1,(int)(radius*scale*.3));
            for(int y=cy-extent;y<=cy+extent;y++)for(int x=cx-extent;x<=cx+extent;x++) {
                int pixel=main.getPixelRGBA(x,y);
                int source=live.getPixelRGBA(x*live.getWidth()/main.getWidth(),y*live.getHeight()/main.getHeight());
                int r=pixel&255,g=(pixel>>8)&255,b=(pixel>>16)&255;
                int sr=source&255,sg=(source>>8)&255,sb=(source>>16)&255;
                if(Math.abs(r-sr)<12&&Math.abs(g-sg)<12&&Math.abs(b-sb)<12)matched++;
                if(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))>20)colored++;
                if(Math.max(sr,Math.max(sg,sb))-Math.min(sr,Math.min(sg,sb))>20)sourceColored++;
                samples++;
            }
            main.writeToFile(output.resolve(name+".png"));
        } finally {mc.getMainRenderTarget().bindWrite(true);}
        require(matched>samples*.9&&Math.abs(colored-sourceColored)<samples*.1,
                "HUD changed live colors: "+matched+" matched, "+colored+"/"+sourceColored+" colored of "+samples);
        System.out.println("STILLWORLD PSYCHE HUD PASS: "+name+", radius="+radius+", live pixels="+matched+"/"+samples);
    }
    static void hidden(Path output,String name) throws Exception {
        require(PsycheView.windowRadius()==0,"HUD retained a positive radius while hidden");
        var mc=Minecraft.getInstance();double scale=mc.getWindow().getGuiScale();
        int cx=(int)(PsycheView.WINDOW_X*scale),cy=(int)(PsycheView.WINDOW_Y*scale);
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int pixel=image.getPixelRGBA(cx,cy),r=pixel&255,g=(pixel>>8)&255,b=(pixel>>16)&255;
            require(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<8,"Hidden HUD left a colored center");
            image.writeToFile(output.resolve(name+".png"));
        } finally {mc.getMainRenderTarget().bindWrite(true);}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private PsycheHudChecks(){}
}
