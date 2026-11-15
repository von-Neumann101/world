package dev.stillworld.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

/** Counts real custom-render submissions, cancelling only the fixture's model draw. */
final class EntityVisibilityChecks {
    private final Entity ghost;
    private int draws;
    private final Consumer<RenderLivingEvent.Pre<?,?>> listener=this::rendered;
    private EntityVisibilityChecks(Entity ghost){this.ghost=ghost;}

    @SuppressWarnings("unchecked")
    static void check(UUID id) throws ReflectiveOperationException {
        var mc=Minecraft.getInstance();var ghost=StillworldClient.ghosts().get(id);
        require(ghost!=null&&mc.levelRenderer.isSectionCompiled(ghost.blockPosition()),"Visibility fixture needs a snapshot in rendered terrain");
        var field=StillworldClient.class.getDeclaredField("GHOSTS");field.setAccessible(true);
        var ghosts=(Map<UUID,Entity>)field.get(null);var saved=new LinkedHashMap<>(ghosts);
        double scale=Entity.getViewScale(),setting=mc.options.entityDistanceScaling().get();var position=ghost.position();var playerPosition=mc.player.position();
        var data=ghost.getPersistentData().copy();
        var probe=new EntityVisibilityChecks(ghost);NeoForge.EVENT_BUS.addListener(probe.listener);
        try {
            ghosts.clear();ghosts.put(id,ghost);
            mc.options.entityDistanceScaling().set(1.0);
            Entity.setViewScale(1);
            probe.expect(6,true,"nearby snapshot remains visible");
            probe.expect(128,false,"snapshot outside vanilla entity render distance must stay hidden");
            Entity.setViewScale(.5);
            probe.expect(48,false,"reduced entity-distance setting also limits snapshots");
            Entity.setViewScale(2);
            probe.expect(48,true,"increased entity-distance setting restores a nearby snapshot");
            // A distant server-loaded actor must not bypass its network tracking range.
            ghost.getPersistentData().putInt("stillworld:tracking_range",32);
            ghost.getPersistentData().putInt("stillworld:tracking_base_range",32);
            mc.player.setPos(position.add(0,0,-48));
            probe.expect(48,false,"snapshot outside server tracking range must stay hidden");
            mc.player.setPos(position.add(0,0,-16));
            probe.expect(16,true,"approaching the captured position restores normal visibility");
            // A cow captured with a 50% tracking scale has an 80-block entry cap.
            // Keep native render distance permissive to isolate server tracking.
            ghost.getPersistentData().putInt("stillworld:tracking_range",80);
            ghost.getPersistentData().putInt("stillworld:tracking_base_range",160);
            mc.player.setPos(position.add(0,0,-96));
            mc.options.entityDistanceScaling().set(.5);
            probe.expect(96,false,"50% integrated-server tracking hides the distant cow");
            mc.options.entityDistanceScaling().set(1.0);
            probe.expect(96,true,"raising entity distance must update the captured tracking cap");
            mc.options.entityDistanceScaling().set(.5);
            probe.expect(96,false,"lowering entity distance must update the captured tracking cap");
            ghost.getPersistentData().putInt("stillworld:tracking_range",1024);
            ghost.getPersistentData().putInt("stillworld:tracking_base_range",1024);
            ghost.setPos(position.add(16000,0,0));mc.player.setPos(ghost.position().add(0,0,-6));
            probe.expect(6,false,"unloaded terrain must not expose a floating snapshot");
        } finally {
            ghost.setPos(position);mc.player.setPos(playerPosition);mc.options.entityDistanceScaling().set(setting);Entity.setViewScale(scale);
            ghost.getPersistentData().getAllKeys().clear();ghost.getPersistentData().merge(data);
            ghosts.clear();ghosts.putAll(saved);NeoForge.EVENT_BUS.unregister(probe.listener);
        }
        System.out.println("STILLWORLD ENTITY VISIBILITY PASS: distance, settings, tracking range, approach and unloaded terrain");
    }

    private void expect(double distance,boolean visible,String reason) {
        var mc=Minecraft.getInstance();var eye=ghost.position().add(0,.7,-distance);
        var view=new Matrix4f().rotateY((float)Math.PI);
        var projection=new Matrix4f().perspective((float)Math.toRadians(70),16f/9,.05f,2048);
        var frustum=new Frustum(view,projection);frustum.prepare(eye.x,eye.y,eye.z);
        require(frustum.isVisible(ghost.getBoundingBox().inflate(.5)),"Fixture must stay inside the view frustum");
        var event=new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_ENTITIES,mc.levelRenderer,
                new PoseStack(),view,projection,0,DeltaTracker.ONE,new ProbeCamera(eye),frustum);
        draws=0;StillworldRendering.render(event);
        require((draws>0)==visible,reason+"; model submissions="+draws);
    }
    private void rendered(RenderLivingEvent.Pre<?,?> event) {
        if(event.getEntity()==ghost&&StillworldRendering.visualPass()){draws++;event.setCanceled(true);}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static final class ProbeCamera extends Camera {
        ProbeCamera(Vec3 eye){setPosition(eye);}
    }
}
