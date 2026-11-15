package dev.stillworld.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import net.minecraft.nbt.CompoundTag;

/** Exercise actual HUD packet/update state at controlled frame intervals, then restore it. */
final class WindowMotionChecks {
    static void check() throws Exception {
        var saved=new LinkedHashMap<Field,Object>();
        for(var field:PsycheView.class.getDeclaredFields())if(!Modifier.isFinal(field.getModifiers())&&field.getType().isPrimitive()) {
            field.setAccessible(true);saved.put(field,field.get(null));
        }
        try {
            send(100,false);advance(180);
            float full=PsycheView.windowRadius();
            send(99,false);
            require(PsycheView.windowRadius()==full,"Passive psyche packet snapped the aperture radius immediately");
            advance(1);float first=PsycheView.windowRadius();
            require(first<full&&full-first<.02f,"Passive drain needs a small first-frame radius change");
            advance(60);
            require(Math.abs(PsycheView.windowRadius()-28*(float)Math.sqrt(.99))<.01f,"Smoothed radius failed to reach the real energy size");
            float beforeCost=PsycheView.windowRadius();send(25,false);
            require(PsycheView.windowRadius()==beforeCost,"Skill cost snapped the radius");
            advance(1);float speed=beforeCost-PsycheView.windowRadius();
            send(24,false);float beforeRetarget=PsycheView.windowRadius();advance(1);
            require(beforeRetarget-PsycheView.windowRadius()>speed,"A new cost restarted contraction from rest");
            advance(90);send(0,false);advance(45);
            require(PsycheView.windowRadius()==0,"Depleted aperture must finish disappearing without a residual rim");
            send(100,true);advance(240);
            float far=PsycheView.windowFarBlend();send(100,false);advance(1);
            require(far-PsycheView.windowFarBlend()<.006f,"Returning to log zone starts boundary contraction abruptly");
            float previous=PsycheView.windowFarBlend(),largestStep=0;
            for(int i=0;i<60;i++){advance(1);float next=PsycheView.windowFarBlend();largestStep=Math.max(largestStep,previous-next);previous=next;}
            require(largestStep<.018f,"Region easing replaced the initial jump with a fast inward lurch");
            send(100,true);advance(240);send(100,false);
            advance(8);float before=PsycheView.windowFarBlend();send(100,true);advance(1);
            require(PsycheView.windowFarBlend()<before,"Reversing at the region boundary abruptly reverses the moving outline");
            System.out.println("STILLWORLD WINDOW MOTION PASS: no packet snaps, continuous retargeting, zero completion and smooth region reversal");
        } finally {
            for(var entry:saved.entrySet())entry.getKey().set(null,entry.getValue());
        }
    }
    private static void send(int energy,boolean far) {
        var tag=new CompoundTag();tag.putInt("energy",energy);tag.putBoolean("far",far);PsycheView.receive(tag);
    }
    private static void advance(int frames) throws Exception {
        var clock=PsycheView.class.getDeclaredField("frameTime");clock.setAccessible(true);
        for(int i=0;i<frames;i++){clock.setLong(null,System.nanoTime()-16_666_667L);PsycheView.update();}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private WindowMotionChecks(){}
}
