package dev.stillworld.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.stillworld.ModContent;
import dev.stillworld.Stillworld;
import dev.stillworld.mixin.client.CompositeRenderTypeAccessor;
import dev.stillworld.mixin.client.CompositeStateAccessor;
import dev.stillworld.mixin.client.TextureStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Visual passes call the renderer directly: no real entity, shadow, fire quad or hitbox can leak. */
public final class StillworldRendering {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(Stillworld.ID, "textures/misc/white.png");
    private static boolean renderingVisual;
    private static RenderTarget savedDepth;
    private static Matrix4f worldProjection, worldView;
    private static Vec3 worldCamera;
    private static float worldPartial;
    public record Slash(java.util.UUID id,Vec3 center, float yaw, float pitch) { }
    public static boolean visualPass() { return renderingVisual; }

    /** Cancel at the dispatcher before renderer layers, names, shadows, or debug hitboxes run. */
    public static boolean hideOriginal(Entity entity) {
        if (renderingVisual) return false;
        if (RealityOrbRenderer.rendering()) return StillworldClient.phased(entity) || StillworldClient.locked(entity);
        var mc = Minecraft.getInstance();
        if (entity == mc.player) return StillworldClient.phased(entity);
        return StillworldClient.phased(entity) || StillworldClient.locked(entity) || StillworldClient.active();
    }

    public static void render(RenderLevelStageEvent event) {
        boolean reality=RealityOrbRenderer.rendering();
        if(reality && event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_PARTICLES && StillworldClient.active() && Minecraft.useShaderTransparency()) {
            // Fabulous clears main depth when compositing its separate translucent targets.
            // Preserve the opaque world before that composite, while leaving its active target intact.
            captureDepth();
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL && StillworldClient.active()) {
            if(!Minecraft.useShaderTransparency())captureDepth();
            if(savedDepth==null)return;
            var main = Minecraft.getInstance().getMainRenderTarget();
            main.copyDepthFrom(savedDepth); main.bindWrite(false);
            worldProjection = new Matrix4f(event.getProjectionMatrix());
            worldView = new Matrix4f(event.getModelViewMatrix());
            worldCamera = event.getCamera().getPosition();
            worldPartial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            StillworldPostEffect.render(event);
            afterPostEffect(event);
            RealityOrbRenderer.render(event);
            StillworldPostEffect.renderSpatial();
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        var pose = event.getPoseStack();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (StillworldClient.active() && !reality) {
            for (var ghost : StillworldClient.ghosts().values()) {
                if (StillworldClient.isDead(ghost.getUUID()) || StillworldClient.phased(ghost)) continue;
                if (event.getFrustum().isVisible(ghost.getBoundingBox().inflate(.5))) draw(ghost, 1, camera, pose, buffers, -1, false);
            }
        } else {
            for (var original : mc.level.entitiesForRendering()) {
                var clone = StillworldClient.frozenReplacement(original);
                if (clone != null && original.isAlive() && event.getFrustum().isVisible(clone.getBoundingBox().inflate(.5)))
                    draw(clone, 1, camera, pose, buffers, -1, false);
            }
        }
        if (!StillworldClient.active() || reality) renderPhasedPlayers(pose, buffers, camera, partial);
        buffers.endBatch();
    }

    private static void captureDepth() {
        var main=Minecraft.getInstance().getMainRenderTarget();
        int previous=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING);
        if(savedDepth==null)savedDepth=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);
        else if(savedDepth.width!=main.width||savedDepth.height!=main.height)savedDepth.resize(main.width,main.height,Minecraft.ON_OSX);
        savedDepth.copyDepthFrom(main);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER,previous);
    }

    private static void renderPhasedPlayers(PoseStack pose, MultiBufferSource buffers, Vec3 camera, float partial) {
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null && ModContent.equippedRank(mc.player) != null) {
            long flicker = System.nanoTime() / 140_000_000L;
            int color = switch ((int)(flicker % 3)) { case 0 -> 0x7A090909; case 1 -> 0x6A888888; default -> 0x88FFFFFF; };
            boolean silhouette = flicker % 3 != 2;
            for (var player : mc.level.players()) {
                if (!StillworldClient.phased(player) || (player == mc.player && mc.options.getCameraType().isFirstPerson())) continue;
                if (player.distanceToSqr(camera) < 16384) draw(player, partial, camera, pose, buffers, color, silhouette);
            }
        }
    }

    /** Restore scene depth so unfiltered skills and colored residuals remain occluded by terrain. */
    private static void afterPostEffect(RenderLevelStageEvent event) {
        if (!StillworldClient.active() || savedDepth == null || worldProjection == null) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var stack = RenderSystem.getModelViewStack();
        stack.pushMatrix(); stack.identity().mul(worldView); RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(worldProjection, VertexSorting.DISTANCE_TO_ORIGIN);
        mc.getMainRenderTarget().copyDepthFrom(savedDepth); mc.getMainRenderTarget().bindWrite(false);
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(true);
        mc.gameRenderer.lightTexture().turnOnLightLayer();
        try {
            var buffers = mc.renderBuffers().bufferSource();
            var pose=new PoseStack();
            if(StillworldClient.seeLive())for(var entity:mc.level.entitiesForRendering()) {
                if(!(entity instanceof LivingEntity)||entity==mc.player||StillworldClient.phased(entity)
                        ||StillworldClient.locked(entity)||!entity.isAlive()||!StillworldClient.captured(entity.getUUID()))continue;
                if(event.getFrustum().isVisible(entity.getBoundingBox().inflate(.5)))
                    draw(entity,worldPartial,worldCamera,pose,buffers,0x28D8D8D8,true);
            }
            renderSlashes(pose,buffers,worldCamera);
            renderPhasedPlayers(pose, buffers, worldCamera, worldPartial);
            buffers.endBatch();
            SkillParticles.renderEffects(event);
        } finally {
            mc.gameRenderer.lightTexture().turnOffLightLayer();
            stack.popMatrix(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        }
    }

    public static void clear() {
        if (savedDepth != null) { savedDepth.destroyBuffers(); savedDepth = null; }
        worldProjection = worldView = null;
    }

    private static void draw(Entity entity, float partial, Vec3 camera, PoseStack pose, MultiBufferSource buffers,
                             int tint, boolean silhouette) {
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        var renderer = dispatcher.getRenderer(entity);
        pose.pushPose();
        double x = Mth.lerp(partial, entity.xOld, entity.getX());
        double y = Mth.lerp(partial, entity.yOld, entity.getY());
        double z = Mth.lerp(partial, entity.zOld, entity.getZ());
        Vec3 offset = renderer.getRenderOffset(entity, partial);
        pose.translate(x - camera.x + offset.x, y - camera.y + offset.y, z - camera.z + offset.z);
        boolean previous = renderingVisual;
        renderingVisual = true;
        try {
            MultiBufferSource target = buffers;
            if (tint != -1) {
                target = original -> {
                    // Glint reuses the item's full quads with an unrelated opaque texture.
                    // Its extra pass must not fill the alpha holes of the actual item.
                    if (original==RenderType.glint() || original==RenderType.glintTranslucent()
                            || original==RenderType.entityGlint() || original==RenderType.entityGlintDirect()
                            || original==RenderType.armorEntityGlint()) return DiscardingConsumer.INSTANCE;
                    ResourceLocation texture = WHITE;
                    if (original instanceof CompositeRenderTypeAccessor composite) {
                        var textureState = ((CompositeStateAccessor)(Object)composite.stillworld$state()).stillworld$textureState();
                        texture = ((TextureStateAccessor)textureState).stillworld$texture().orElse(WHITE);
                    }
                    var type = silhouette ? SilhouetteRenderType.forTexture(texture) : RenderType.entityTranslucent(texture);
                    return new TintedConsumer(buffers.getBuffer(type), tint, silhouette);
                };
            } else if (StillworldClient.active() && entity instanceof LivingEntity) {
                // Shade the frozen model without replacing its texture, cutout or world lighting.
                target = original -> new SnapshotTintConsumer(buffers.getBuffer(original));
            }
            renderer.render(entity, Mth.rotLerp(partial, entity.yRotO, entity.getYRot()), partial, pose, target,
                    tint == -1 ? dispatcher.getPackedLightCoords(entity, partial) : LightTexture.FULL_BRIGHT);
        } finally {
            renderingVisual = previous;
            pose.popPose();
        }
    }

    private static void renderSlashes(PoseStack pose, MultiBufferSource buffers, Vec3 camera) {
        var consumer = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        for (var slash : StillworldClient.slashes()) {
            pose.pushPose();
            pose.translate(slash.center.x - camera.x, slash.center.y - camera.y, slash.center.z - camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(-slash.yaw));
            pose.mulPose(Axis.XP.rotationDegrees(slash.pitch));
            // Both sides of the fixed 4 x 3 plane plus a narrow luminous diagonal.
            quad(pose, consumer, -2, -1.5f, 2, 1.5f, -.15f, 0x38D8E4FF, false);
            quad(pose, consumer, -2, -1.5f, 2, 1.5f, .15f, 0x38D8E4FF, true);
            pose.pushPose(); pose.mulPose(Axis.ZP.rotationDegrees(30));
            quad(pose, consumer, -1.9f, -.035f, 1.9f, .035f, -.16f, 0xDDEEF5FF, false);
            quad(pose, consumer, -1.9f, -.035f, 1.9f, .035f, .16f, 0xDDEEF5FF, true);
            pose.popPose(); pose.popPose();
        }
    }

    private static void quad(PoseStack pose, VertexConsumer consumer, float x1, float y1, float x2, float y2,
                             float z, int color, boolean reverse) {
        float[][] vertices = reverse ? new float[][]{{x1,y1},{x1,y2},{x2,y2},{x2,y1}} : new float[][]{{x1,y1},{x2,y1},{x2,y2},{x1,y2}};
        for (var vertex : vertices) consumer.addVertex(pose.last().pose(), vertex[0], vertex[1], z).setColor(color).setUv(.5f,.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0,0, reverse ? -1 : 1);
    }

    private enum DiscardingConsumer implements VertexConsumer {
        INSTANCE;
        public VertexConsumer addVertex(float x,float y,float z) { return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) { return this; }
        public VertexConsumer setUv(float u,float v) { return this; }
        public VertexConsumer setUv1(int u,int v) { return this; }
        public VertexConsumer setUv2(int u,int v) { return this; }
        public VertexConsumer setNormal(float x,float y,float z) { return this; }
    }

    /** Dim frozen creatures without turning their original materials into full-bright silhouettes. */
    static record SnapshotTintConsumer(VertexConsumer delegate) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x,float y,float z) { delegate.addVertex(x,y,z); return this; }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) { delegate.setColor(r*3/5,g*3/5,b*3/5,a); return this; }
        @Override public VertexConsumer setUv(float u,float v) { delegate.setUv(u,v); return this; }
        @Override public VertexConsumer setUv1(int u,int v) { delegate.setUv1(u,v); return this; }
        @Override public VertexConsumer setUv2(int u,int v) { delegate.setUv2(u,v); return this; }
        @Override public VertexConsumer setNormal(float x,float y,float z) { delegate.setNormal(x,y,z); return this; }
    }

    /** Apply tint/opacity while preserving each original layer's texture coordinates and cutout. */
    private record TintedConsumer(VertexConsumer delegate, int tint, boolean solid) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(x,y,z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) {
            delegate.setColor(solid ? (tint >> 16 & 255) : r * (tint >> 16 & 255) / 255,
                    solid ? (tint >> 8 & 255) : g * (tint >> 8 & 255) / 255,
                    solid ? (tint & 255) : b * (tint & 255) / 255, a * (tint >>> 24) / 255); return this;
        }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u,v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(0,10); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u,v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x,y,z); return this; }
    }
    private StillworldRendering() { }
}
