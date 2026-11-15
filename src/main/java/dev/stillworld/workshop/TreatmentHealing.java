package dev.stillworld.workshop;

import dev.stillworld.server.PsycheManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Repairs only Stillworld's multiplier, keeping base health and unrelated modifiers intact. */
public final class TreatmentHealing {
    private static double rawMaximum(ServerPlayer player) {
        var attribute=player.getAttribute(Attributes.MAX_HEALTH);
        if(attribute==null)return 0;
        double base=attribute.getBaseValue();
        for(var m:attribute.getModifiers())if(!m.id().equals(PsycheManager.HEALTH_LOSS)&&m.operation()==AttributeModifier.Operation.ADD_VALUE)base+=m.amount();
        double result=base;
        for(var m:attribute.getModifiers())if(!m.id().equals(PsycheManager.HEALTH_LOSS)&&m.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_BASE)result+=base*m.amount();
        for(var m:attribute.getModifiers())if(!m.id().equals(PsycheManager.HEALTH_LOSS)&&m.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)result*=1+m.amount();
        return result;
    }
    public static double missing(ServerPlayer player) {
        if(PsycheManager.data(player).healthScale>=1)return 0;
        return Math.max(0,Attributes.MAX_HEALTH.value().sanitizeValue(rawMaximum(player))-player.getMaxHealth());
    }
    /** Returns whole charge units spent. A final fractional health point costs one unit. */
    public static int repair(ServerPlayer player,int available) {
        if(available<=0||PsycheManager.data(player).healthScale>=1)return 0;
        double raw=rawMaximum(player),full=Attributes.MAX_HEALTH.value().sanitizeValue(raw);
        double gain=Math.min(Math.max(0,full-player.getMaxHealth()),available);
        if(gain<=0)return 0;
        double target=player.getMaxHealth()+gain;
        PsycheManager.data(player).healthScale=target>=full-1e-6?1:Math.clamp(target/raw,0,1);
        PsycheManager.applyHealth(player);
        PsycheManager.sync(player);
        return (int)Math.ceil(gain-1e-6);
    }
    private TreatmentHealing() {}
}
