package dev.stillworld.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/** Owner-only resource display. It never drives gameplay or modifies player movement. */
final class PsycheView {
    static final int WINDOW_X=41,WINDOW_Y=77;
    private static final float WINDOW_RADIUS=28;
    private static int energy=100;
    private static boolean known,far;
    private static float farBlend,exhaustion,windowFade,windowWaveTime;
    private static long frameTime;

    static void receive(CompoundTag tag) {
        known=true;energy=tag.getInt("energy");far=tag.getBoolean("far");
    }
    static void update() {
        long now=System.nanoTime();float dt=frameTime==0?0:Math.min(.1f,(now-frameTime)/1_000_000_000f);frameTime=now;
        if(Minecraft.getInstance().isPaused())return;
        farBlend=approach(farBlend,far?1:0,dt/1.2f);
        exhaustion=approach(exhaustion,known&&energy==0?1:0,dt/3f);
        windowFade=approach(windowFade,StillworldPostEffect.settled()?1:0,dt/.65f);
        // Integrating speed avoids phase jumps when the smoothed region blend changes.
        windowWaveTime+=dt*(.4f+.15f*farBlend);
    }
    private static float approach(float value,float target,float step) { return value+Math.clamp(target-value,-step,step); }
    static float farBlend() {return farBlend;}
    static float windowWaveTime() {return windowWaveTime;}
    static float windowOpacity() {return StillworldPostEffect.settled()?windowFade*windowFade*(3-2*windowFade):0;}
    static float windowRadius() {
        var mc=Minecraft.getInstance();
        if(!known||!StillworldClient.active()||mc.player==null||mc.options.hideGui)return 0;
        // The aperture's area represents the remaining resource, with no residual rim at zero.
        return WINDOW_RADIUS*(float)Math.sqrt(Math.clamp(energy/100f,0,1));
    }
    static float exhaustion() {return exhaustion*exhaustion*(3-2*exhaustion);}
    static void clearSession() {farBlend=exhaustion=windowFade=windowWaveTime=0;far=false;frameTime=0;}
    static void reset() {clearSession();known=false;energy=100;}
    private PsycheView(){}
}
