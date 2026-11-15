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
    private static float displayedRadius=WINDOW_RADIUS,radiusVelocity,windowFar,windowFarVelocity;
    private static long frameTime;

    static void receive(CompoundTag tag) {
        boolean initialize=!known||!StillworldClient.active();
        known=true;energy=tag.getInt("energy");far=tag.getBoolean("far");
        if(initialize){displayedRadius=targetRadius();radiusVelocity=0;}
    }
    static void update() {
        long now=System.nanoTime();float dt=frameTime==0?0:Math.min(.1f,(now-frameTime)/1_000_000_000f);frameTime=now;
        if(Minecraft.getInstance().isPaused())return;
        farBlend=approach(farBlend,far?1:0,dt/1.2f);
        exhaustion=approach(exhaustion,known&&energy==0?1:0,dt/3f);
        windowFade=approach(windowFade,StillworldPostEffect.settled()?1:0,dt/.65f);
        // Critically damped display motion retains velocity when packets or region targets change.
        // The authoritative energy and the world's existing farBlend keep their original timing.
        float target=targetRadius(),offset=displayedRadius-target;
        float decay=(float)Math.exp(-18*dt),motion=(radiusVelocity+18*offset)*dt;
        displayedRadius=target+(offset+motion)*decay;
        radiusVelocity=(radiusVelocity-18*motion)*decay;
        if(Math.abs(displayedRadius-target)<.005f&&Math.abs(radiusVelocity)<.01f){displayedRadius=target;radiusVelocity=0;}
        float region=far?1:0;offset=windowFar-region;
        decay=(float)Math.exp(-2.5f*dt);motion=(windowFarVelocity+2.5f*offset)*dt;
        windowFar=region+(offset+motion)*decay;
        windowFarVelocity=(windowFarVelocity-2.5f*motion)*decay;
        // Integrating speed avoids phase jumps when the smoothed region blend changes.
        windowWaveTime+=dt*(.4f+.15f*windowFar);
    }
    private static float approach(float value,float target,float step) { return value+Math.clamp(target-value,-step,step); }
    static float farBlend() {return farBlend;}
    static float windowFarBlend() {return windowFar;}
    static float windowWaveTime() {return windowWaveTime;}
    static float windowOpacity() {return StillworldPostEffect.settled()?windowFade*windowFade*(3-2*windowFade):0;}
    static float windowRadius() {
        var mc=Minecraft.getInstance();
        if(!known||!StillworldClient.active()||mc.player==null||mc.options.hideGui)return 0;
        return displayedRadius;
    }
    private static float targetRadius(){return WINDOW_RADIUS*(float)Math.sqrt(Math.clamp(energy/100f,0,1));}
    static float exhaustion() {return exhaustion*exhaustion*(3-2*exhaustion);}
    static void clearSession() {farBlend=exhaustion=windowFade=windowWaveTime=windowFar=windowFarVelocity=radiusVelocity=0;displayedRadius=targetRadius();far=false;frameTime=0;}
    static void reset() {known=false;energy=100;clearSession();}
    private PsycheView(){}
}
