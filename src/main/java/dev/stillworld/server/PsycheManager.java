package dev.stillworld.server;

import dev.stillworld.ModContent;
import dev.stillworld.Stillworld;
import dev.stillworld.network.PhaseNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** All resource and health decisions are made on the server, independent of the visual world. */
public final class PsycheManager {
    public static final int SWITCH_TICKS=1400;
    public static final double FAR_DISTANCE=64;
    public static final ResourceLocation HEALTH_LOSS=Stillworld.id("distortion_health_loss");

    public static PsycheData data(ServerPlayer p) { return p.getData(ModContent.PSYCHE); }
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,PsycheManager::death);
        NeoForge.EVENT_BUS.addListener(PsycheManager::login);
        NeoForge.EVENT_BUS.addListener(PsycheManager::respawn);
    }
    public static boolean far(PhaseSession s) {
        // Dimension-change events run after teleport; retain the last position in the old level.
        return s.player.serverLevel()==s.level?s.player.position().distanceToSqr(s.entry)>FAR_DISTANCE*FAR_DISTANCE:s.farZone;
    }
    public static int cost(PhaseSession s,int base) { return base*(far(s)?2:1); }
    public static boolean canSpend(PhaseSession s,int base) {
        int cost=cost(s,base);
        if(data(s.player).energy>=cost)return true;
        s.player.displayClientMessage(Component.translatable("message.stillworld.low_psyche",cost),true);
        return false;
    }
    public static void spend(PhaseSession s,int base) {
        data(s.player).energy=Math.max(0,data(s.player).energy-cost(s,base));sync(s.player);
    }
    public static boolean ready(ServerPlayer p) {
        if(data(p).cooldownTicks==0)return true;
        p.displayClientMessage(Component.translatable("message.stillworld.switch_cooldown",(data(p).cooldownTicks+19)/20),true);
        return false;
    }
    public static void switched(ServerPlayer p) { data(p).cooldownTicks=SWITCH_TICKS;sync(p); }
    public static void payExit(PhaseSession s) {
        // A healthy End-return player is already removed before Clone is posted.
        // Removal is a lifecycle state, not death; its exit still has a cost.
        if(s.player.getHealth()<=0||!far(s))return;
        var data=data(s.player);
        if(data.energy>=40)data.energy-=40;
        else {
            data.energy=0;
            loseFraction(s.player,.4);
            // Repeated forced exits may not postpone a death already due sooner.
            data.doomTicks=data.doomTicks<0?800:Math.min(800,data.doomTicks);
            s.player.displayClientMessage(Component.translatable("message.stillworld.forced_exit"),false);
        }
    }
    public static void setEnergy(ServerPlayer p,int value) {
        data(p).energy=Math.clamp(value,0,100);
        var s=PhaseManager.get(p);if(s!=null&&data(p).energy>0)s.exhaustedTicks=0;
        sync(p);
    }
    public static void applyHealth(ServerPlayer p) {
        var attribute=p.getAttribute(Attributes.MAX_HEALTH);if(attribute==null)return;
        var state=data(p);
        if(state.healthScale>=1)attribute.removeModifier(HEALTH_LOSS);
        else attribute.addOrUpdateTransientModifier(new AttributeModifier(HEALTH_LOSS,state.healthScale-1,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if(p.getHealth()>p.getMaxHealth())p.setHealth(p.getMaxHealth());
    }
    public static void loseFraction(ServerPlayer p,double fraction) {
        data(p).healthScale*=Math.clamp(1-fraction,0,1);applyHealth(p);
    }
    public static void tick(ServerPlayer p) {
        if(!p.isAlive())return;
        var data=data(p);
        if(data.cooldownTicks>0)data.cooldownTicks--;
        if(data.doomTicks>0&&--data.doomTicks==0) {
            kill(p);return;
        }
        var s=PhaseManager.get(p);
        boolean zoneChanged=false;
        if(s!=null&&!s.finishing) {
            boolean far=far(s);zoneChanged=far!=s.farZone;s.farZone=far;
            if(++s.psycheTicks%40==0)data.energy=Math.max(0,data.energy-(far?2:1));
            if(data.energy==0) {
                s.exhaustedTicks++;
                if(s.exhaustedTicks>=200&&(s.exhaustedTicks-200)%40==0) {
                    float max=p.getMaxHealth();
                    if(max<=1.0001f){kill(p);return;}
                    loseFraction(p,1.0/max);
                }
            } else s.exhaustedTicks=0;
        }
        if(zoneChanged||p.tickCount%10==0)sync(p);
    }
    private static void kill(ServerPlayer p) {
        // genericKill bypasses armor, creative immunity and totems; death/respawn remains vanilla.
        p.hurt(p.damageSources().genericKill(),Float.MAX_VALUE);
        // If another mod cancels death, keep it due instead of silently removing the consequence.
        if(p.isAlive()&&data(p).doomTicks==0)data(p).doomTicks=1;
    }
    private static void death(LivingDeathEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer p)||event.isCanceled())return;
        if(PhaseManager.get(p)!=null)loseFraction(p,.5);
        data(p).doomTicks=-1;
    }
    private static void login(PlayerEvent.PlayerLoggedInEvent e) { if(e.getEntity() instanceof ServerPlayer p){applyHealth(p);sync(p);} }
    private static void respawn(PlayerEvent.PlayerRespawnEvent e) { if(e.getEntity() instanceof ServerPlayer p){applyHealth(p);sync(p);} }
    public static void sync(ServerPlayer p) {
        var state=data(p);var s=PhaseManager.get(p);var tag=PhaseNetwork.message("psyche");
        tag.putInt("energy",state.energy);tag.putInt("cooldownTicks",state.cooldownTicks);tag.putInt("doomTicks",state.doomTicks);
        tag.putInt("exhaustedTicks",s==null?0:s.exhaustedTicks);tag.putBoolean("far",s!=null&&far(s));
        PhaseNetwork.sendState(p,tag);
    }
    private PsycheManager(){}
}
