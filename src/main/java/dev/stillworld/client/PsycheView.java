package dev.stillworld.client;

import dev.stillworld.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Owner-only resource display. It never drives gameplay or modifies player movement. */
final class PsycheView {
    static final int WINDOW_X=41,WINDOW_Y=77;
    private static final float WINDOW_RADIUS=28;
    private static int energy=100,cooldown,doom=-1;
    private static boolean known,far;
    private static float farBlend,exhaustion;
    private static long frameTime;

    static void receive(CompoundTag tag) {
        known=true;energy=tag.getInt("energy");cooldown=tag.getInt("cooldownTicks");doom=tag.getInt("doomTicks");far=tag.getBoolean("far");
    }
    static void tick() {
        if(Minecraft.getInstance().isPaused())return;
        if(cooldown>0)cooldown--;if(doom>0)doom--;
    }
    static void update() {
        long now=System.nanoTime();float dt=frameTime==0?0:Math.min(.1f,(now-frameTime)/1_000_000_000f);frameTime=now;
        if(Minecraft.getInstance().isPaused())return;
        farBlend=approach(farBlend,far?1:0,dt/1.2f);
        exhaustion=approach(exhaustion,known&&energy==0?1:0,dt/3f);
    }
    private static float approach(float value,float target,float step) { return value+Math.clamp(target-value,-step,step); }
    static float farBlend() {return farBlend;}
    static float windowRadius() {
        var mc=Minecraft.getInstance();
        if(!known||!StillworldClient.active()||mc.player==null||mc.options.hideGui)return 0;
        // The aperture's area represents the remaining resource, with no residual rim at zero.
        return WINDOW_RADIUS*(float)Math.sqrt(Math.clamp(energy/100f,0,1));
    }
    static float exhaustion() {return exhaustion*exhaustion*(3-2*exhaustion);}
    static void clearSession() {farBlend=exhaustion=0;far=false;frameTime=0;}
    static void reset() {clearSession();known=false;energy=100;cooldown=0;doom=-1;}
    static void hud(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();if(!known||mc.player==null||mc.options.hideGui)return;
        var gui=event.getGuiGraphics();int y=StillworldClient.active()?114:44;
        if(StillworldClient.active()||ModContent.equippedRank(mc.player)!=null) {
            if(cooldown>0){gui.drawString(mc.font,Component.translatable("hud.stillworld.cooldown",(cooldown+19)/20),8,y,0xFFBBBBBB);y+=12;}
        }
        if(doom>=0)gui.drawString(mc.font,Component.translatable("hud.stillworld.doom",(doom+19)/20),8,y,0xFFFF7777);
    }
    private PsycheView(){}
}
