package dev.stillworld.client;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Observe real rendered frames, including the brief interval after the gameplay session ends. */
final class WindowTransitionChecks {
    private final Path output;
    private boolean beforeScan,partialFade,fullFade,exitEarly,exitMiddle,exitLate,exitFinished;
    private Throwable failure;
    private float waveStart;
    private long timeStart;
    private float nearSpeed;
    WindowTransitionChecks(Path output){this.output=output;}
    void frame(RenderLevelStageEvent event) {
        if(failure!=null||RealityOrbRenderer.rendering()||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        try {
            if(StillworldClient.active()) {
                float opacity=PsycheView.windowOpacity();
                if(!StillworldPostEffect.settled()) {
                    require(opacity==0,"HUD appeared before the entry scan finished");beforeScan=true;
                } else if(opacity>.05f&&opacity<.8f&&!partialFade) {
                    shot("hud-fade-in.png");partialFade=true;
                } else if(opacity==1)fullFade=true;
            } else if(StillworldExitEffect.active()) {
                require(StillworldClient.ghosts().isEmpty()&&RealityWorld.level()==null,"Exit animation delayed session cleanup");
                var field=StillworldExitEffect.class.getDeclaredField("started");field.setAccessible(true);
                float progress=(System.nanoTime()-field.getLong(null))/950_000_000f;
                if(progress>.05f&&progress<.3f&&!exitEarly){checkLiveCenter();shot("exit-early.png");exitEarly=true;}
                if(progress>.4f&&progress<.65f&&!exitMiddle){checkLiveCenter();shot("exit-middle.png");exitMiddle=true;}
                if(progress>.8f&&!exitLate){checkLiveCenter();shot("exit-late.png");exitLate=true;}
            } else if(exitLate&&!exitFinished) {
                require(org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                        ==Minecraft.getInstance().getMainRenderTarget().frameBufferId,"Exit completion lost the main framebuffer before hand rendering");
                exitFinished=true;
            }
        } catch(Throwable exception){failure=exception;}
    }
    void entry() {
        checkFailure();require(beforeScan&&partialFade&&fullFade,"Did not observe scan-hidden, partial-fade and fully-visible HUD frames");
    }
    void startSpeed(){waveStart=PsycheView.windowWaveTime();timeStart=System.nanoTime();}
    void nearSpeed(){nearSpeed=speed();require(PsycheView.farBlend()<.01&&nearSpeed>.3f&&nearSpeed<.48f,"Near aperture wave was not slow: "+nearSpeed);}
    void farSpeed(){float speed=speed();require(PsycheView.farBlend()>.99&&speed>nearSpeed*1.1&&speed<nearSpeed*1.6,"Far aperture speed should increase slightly: "+speed+" / "+nearSpeed);}
    private float speed(){return (PsycheView.windowWaveTime()-waveStart)/((System.nanoTime()-timeStart)/1_000_000_000f);}
    void exit() {
        checkFailure();require(exitEarly&&exitMiddle&&exitLate&&exitFinished,"Did not observe expanding exit across early/middle/late/completed rendered frames");
        require(!StillworldExitEffect.active()&&!StillworldPostEffect.loaded(),"Exit visual resources remained active");
    }
    private void checkFailure(){if(failure!=null)throw new AssertionError("Transition frame failed",failure);}
    private void checkLiveCenter() throws Exception {
        var mc=Minecraft.getInstance();var field=StillworldExitEffect.class.getDeclaredField("live");field.setAccessible(true);
        var live=(RenderTarget)field.get(null);require(live!=null,"Exit has no current reality frame");
        int x=(int)(PsycheView.WINDOW_X*mc.getWindow().getGuiScale()),y=(int)(PsycheView.WINDOW_Y*mc.getWindow().getGuiScale());
        try(var frame=Screenshot.takeScreenshot(mc.getMainRenderTarget());var source=Screenshot.takeScreenshot(live)) {
            for(int cy=y-2;cy<=y+2;cy++)for(int cx=x-2;cx<=x+2;cx++) {
                int actual=frame.getPixelRGBA(cx,cy),expected=source.getPixelRGBA(cx,cy);
                for(int shift=0;shift<24;shift+=8)require(Math.abs(((actual>>shift)&255)-((expected>>shift)&255))<=1,"Exit opening did not reveal current real pixels");
            }
        } finally {mc.getMainRenderTarget().bindWrite(true);}
    }
    private void shot(String name) throws Exception {
        var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(output.resolve(name));}
        finally{mc.getMainRenderTarget().bindWrite(true);}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
