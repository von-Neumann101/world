package dev.stillworld.client;

import com.mojang.authlib.GameProfile;
import dev.stillworld.ModContent;
import dev.stillworld.Stillworld;
import dev.stillworld.core.Rank;
import dev.stillworld.mixin.client.WalkAnimationAccessor;
import dev.stillworld.mixin.client.AnimationStateAccessor;
import dev.stillworld.network.PhaseNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/** All state here belongs to the physical client. Visual entities never enter ClientLevel's entity list. */
@Mod(value = Stillworld.ID, dist = Dist.CLIENT)
public final class StillworldClient {
    private static final KeyMapping TOGGLE = new KeyMapping("key.stillworld.toggle", GLFW.GLFW_KEY_V, "key.categories.stillworld");
    private static final KeyMapping MARK = new KeyMapping("key.stillworld.mark", GLFW.GLFW_KEY_R, "key.categories.stillworld");
    private static UUID session;
    private static Rank rank;
    private static final Map<UUID, Entity> GHOSTS = new LinkedHashMap<>();
    private static final Map<UUID, Entity> LOCKED = new HashMap<>();
    private static final Map<UUID, Entity> ENTRY_ANIMATIONS = new HashMap<>();
    private static final Set<UUID> PHASED = new HashSet<>();
    private static final Set<UUID> DEAD = new HashSet<>();
    private static final List<StillworldRendering.Slash> SLASHES = new ArrayList<>();
    private static final Set<BlockPos> PLACED = new HashSet<>();
    private static BlockPos breaking;
    private static int breakingTicks;
    private static int attackDelay;
    private static int useDelay;
    private static long frozenDay, frozenTime;
    private static float frozenRain, frozenThunder;
    private static net.minecraft.client.multiplayer.ClientLevel knownLevel;

    public StillworldClient(IEventBus modBus) {
        modBus.addListener(StillworldClient::keys);
        modBus.addListener(StillworldClient::setup);
        modBus.addListener(SilhouetteRenderType::registerShaders);
        modBus.addListener(StillworldPostEffect::registerReloads);
        NeoForge.EVENT_BUS.addListener(StillworldClient::tick);
        NeoForge.EVENT_BUS.addListener(StillworldClient::interaction);
        NeoForge.EVENT_BUS.addListener(StillworldClient::hud);
        NeoForge.EVENT_BUS.addListener(StillworldRendering::render);
        NeoForge.EVENT_BUS.addListener(StillworldClient::logout);
        PhaseNetwork.clientHandler = StillworldClient::receive;
        PhaseNetwork.clientPhased = StillworldClient::phased;
    }

    private static void keys(RegisterKeyMappingsEvent event) { event.register(TOGGLE); event.register(MARK); }
    @SuppressWarnings("deprecation")
    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModContent.BLACK_MIST.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModContent.GRAY_MIST.get(), RenderType.translucent());
        });
    }
    public static boolean active() { return session != null; }
    public static boolean phased(Entity entity) {
        return PHASED.contains(entity.getUUID()) || (active() && entity == Minecraft.getInstance().player);
    }
    public static Map<UUID, Entity> ghosts() { return Collections.unmodifiableMap(GHOSTS); }
    public static boolean isDead(UUID uuid) { return DEAD.contains(uuid); }
    static List<StillworldRendering.Slash> slashes() { return SLASHES; }
    public static boolean locked(Entity entity) { return LOCKED.containsKey(entity.getUUID()); }
    public static Entity frozenReplacement(Entity entity) { return LOCKED.get(entity.getUUID()); }
    public static boolean frozenVisual(Entity entity) { return GHOSTS.get(entity.getUUID())==entity || LOCKED.get(entity.getUUID())==entity; }
    /** Consume the authoritative rewind without waiting for an interpolation tick which is deliberately frozen. */
    public static void alignLocked(Entity entity) {
        var visual = LOCKED.get(entity.getUUID());
        if (visual == null) return;
        entity.absMoveTo(visual.getX(),visual.getY(),visual.getZ(),visual.getYRot(),visual.getXRot());
        entity.setOldPosAndRot();
    }
    public static boolean seeLive() { return rank != null && rank.canSeeLive(); }
    public static long frozenDay() { return frozenDay; }
    public static long frozenTime() { return frozenTime; }
    public static float frozenRain() { return frozenRain; }
    public static float frozenThunder() { return frozenThunder; }

    private static void receive(CompoundTag tag) {
        var mc = Minecraft.getInstance();
        if (!mc.isSameThread()) { mc.execute(() -> receive(tag)); return; }
        switch (tag.getString("op")) {
            case "phased" -> {
                PHASED.clear();
                var list = tag.getList("players", Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) {
                    try { PHASED.add(UUID.fromString(list.getString(i))); }
                    catch (IllegalArgumentException ignored) { /* Ignore malformed remote entries. */ }
                }
            }
            case "freeze" -> {
                var clone = createVisual(tag.getCompound("visual"));
                if (clone != null) LOCKED.put(tag.getUUID("uuid"), clone);
            }
            case "unfreeze" -> LOCKED.remove(tag.getUUID("uuid"));
            case "begin" -> {
                clearSession();
                if (mc.level == null || mc.player == null) return;
                session = tag.getUUID("session");
                rank = Rank.values()[Math.clamp(tag.getInt("rank"), 0, Rank.values().length - 1)];
                frozenDay = tag.getLong("dayTime"); frozenTime = tag.getLong("gameTime");
                frozenRain = tag.getFloat("rain"); frozenThunder = tag.getFloat("thunder");
                // Preserve client-only walk/model state before future live network updates arrive.
                for (var entity : mc.level.entitiesForRendering()) {
                    var saved = new CompoundTag();
                    entity.saveWithoutId(saved);
                    saved.putString("id", EntityType.getKey(entity.getType()).toString());
                    saved.remove("Passengers");
                    var visual = new CompoundTag();
                    visual.putUUID("uuid", entity.getUUID()); visual.putInt("entityId", entity.getId()); visual.put("entity", saved);
                    if (entity instanceof Player player) {
                        visual.putBoolean("player", true); visual.putString("name", player.getGameProfile().getName());
                    }
                    var clone = createVisual(visual);
                    if (clone != null) { copyAnimation(entity, clone, true); ENTRY_ANIMATIONS.put(entity.getUUID(), clone); }
                }
                mc.level.setDayTime(frozenDay); mc.level.setGameTime(frozenTime);
                StillworldPostEffect.begin(mc.player.position().add(0,1,0));
            }
            case "end" -> clearSession();
            default -> {
                if (!active() || !tag.hasUUID("session") || !session.equals(tag.getUUID("session"))) return;
                switch (tag.getString("op")) {
                    case "ghost" -> {
                        var clone = createVisual(tag.getCompound("visual"));
                        if (clone != null) {
                            var entry = ENTRY_ANIMATIONS.remove(clone.getUUID());
                            if (entry != null) {
                                copyAnimation(entry, clone, true);
                                var captured = action("capture_pose"); captured.putUUID("target", clone.getUUID());
                                captured.put("clientPose", ClientPose.capture(clone)); PhaseNetwork.sendAction(captured);
                            }
                            GHOSTS.put(clone.getUUID(), clone);
                        }
                    }
                    case "block" -> {
                        if (mc.level != null) {
                            var pos = BlockPos.of(tag.getLong("pos"));
                            var state = Block.stateById(tag.getInt("state"));
                            mc.level.setBlock(pos, state, 19);
                            FrozenBlockEntities.edited(mc.level,pos);
                            if (tag.getBoolean("placed")) PLACED.add(pos); else PLACED.remove(pos);
                        }
                    }
                    case "dead" -> DEAD.add(tag.getUUID("uuid"));
                    case "slash" -> SLASHES.add(new StillworldRendering.Slash(new Vec3(tag.getDouble("cx"), tag.getDouble("cy"), tag.getDouble("cz")), tag.getFloat("yaw"), tag.getFloat("pitch")));
                    default -> { }
                }
            }
        }
    }

    private static Entity createVisual(CompoundTag visual) {
        var level = Minecraft.getInstance().level;
        if (level == null || !visual.hasUUID("uuid")) return null;
        Entity entity;
        if (visual.getBoolean("player")) {
            entity = new RemotePlayer(level, new GameProfile(visual.getUUID("uuid"), visual.getString("name")));
            entity.load(visual.getCompound("entity"));
        } else {
            entity = EntityType.loadEntityRecursive(visual.getCompound("entity"), level, e -> e);
        }
        if (entity == null) return null;
        entity.setUUID(visual.getUUID("uuid"));
        // Render-only ID preserves vanilla ID-derived phases, e.g. phantom wing flapping. Not registered in ClientLevel.
        if (visual.contains("entityId", Tag.TAG_INT)) entity.setId(visual.getInt("entityId"));
        entity.tickCount = visual.getInt("tick");
        try { if (visual.contains("pose")) entity.setPose(Pose.valueOf(visual.getString("pose"))); }
        catch (IllegalArgumentException ignored) { }
        entity.setOldPosAndRot();
        entity.xOld = entity.getX(); entity.yOld = entity.getY(); entity.zOld = entity.getZ();
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = living.yBodyRotO = visual.getFloat("bodyYaw");
            living.yHeadRot = living.yHeadRotO = visual.getFloat("headYaw");
            living.attackAnim = living.oAttackAnim = visual.getFloat("attack");
            living.hurtTime = visual.getInt("hurt"); living.deathTime = visual.getInt("death");
            var walk = (WalkAnimationAccessor)(Object)living.walkAnimation;
            walk.stillworld$position(visual.getFloat("walkPosition"));
            walk.stillworld$speed(visual.getFloat("walkSpeed"));
            walk.stillworld$speedOld(visual.getFloat("walkSpeed"));
            var original = level.getEntity(visual.getInt("entityId"));
            if (original != null && original.getUUID().equals(entity.getUUID())) copyAnimation(original, entity, false);
        }
        if (visual.contains("clientPose", Tag.TAG_COMPOUND)) ClientPose.apply(entity, visual.getCompound("clientPose"));
        return entity;
    }

    private static void copyAnimation(Entity original, Entity clone, boolean all) {
        clone.tickCount = original.tickCount;
        if (all) {
            VanillaAnimationSnapshot.copy(original, clone);
            clone.tickCount = original.tickCount;
            clone.setPose(original.getPose());
        }
        if (original instanceof LivingEntity source && clone instanceof LivingEntity target) {
            var walk = (WalkAnimationAccessor)(Object)target.walkAnimation;
            walk.stillworld$position(source.walkAnimation.position());
            walk.stillworld$speed(source.walkAnimation.speed());
            walk.stillworld$speedOld(source.walkAnimation.speed());
            if (all) {
                target.yBodyRot = target.yBodyRotO = source.yBodyRot;
                target.yHeadRot = target.yHeadRotO = source.yHeadRot;
                target.attackAnim = target.oAttackAnim = source.attackAnim;
                target.swingingArm=source.swingingArm;
                target.hurtTime = source.hurtTime; target.deathTime = source.deathTime;
            }
            if (all && source instanceof AbstractClientPlayer from && target instanceof AbstractClientPlayer to) {
                to.elytraRotX=from.elytraRotX;to.elytraRotY=from.elytraRotY;to.elytraRotZ=from.elytraRotZ;
            }
            // Vanilla public AnimationState fields (frog, warden, camel, sniffer, ...) are client-only.
            // Field-type matching is independent of obfuscated field names and does not touch entity state.
            for (var field : original.getClass().getFields()) {
                if (field.getType() != AnimationState.class || !field.getDeclaringClass().isInstance(clone)) continue;
                try {
                    var from = (AnimationStateAccessor)field.get(original);
                    var to = (AnimationStateAccessor)field.get(clone);
                    to.stillworld$lastTime(from.stillworld$lastTime());
                    to.stillworld$accumulatedTime(from.stillworld$accumulatedTime());
                }
                catch (IllegalAccessException exception) { throw new IllegalStateException("Cannot preserve vanilla animation", exception); }
            }
        }
    }

    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearSession(); LOCKED.clear(); PHASED.clear(); knownLevel = null;
    }
    private static void clearSession() {
        session = null; rank = null; GHOSTS.clear(); ENTRY_ANIMATIONS.clear(); DEAD.clear(); SLASHES.clear(); PLACED.clear();
        breaking = null; breakingTicks = 0; attackDelay = useDelay = 0;
        StillworldRendering.clear();
        FrozenBlockEntities.clear();
        StillworldPostEffect.clear();
    }

    private static CompoundTag action(String op) {
        var tag = PhaseNetwork.message(op);
        tag.putUUID("session", session);
        return tag;
    }
    private static void targetAction(String operation, Entity entity) {
        var tag = action(operation); tag.putUUID("target", entity.getUUID()); PhaseNetwork.sendAction(tag);
    }

    private static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (knownLevel != mc.level) {
            if (knownLevel != null) { clearSession(); LOCKED.clear(); }
            knownLevel = mc.level;
        }
        if (mc.player == null || mc.level == null) { if (active()) clearSession(); return; }
        if (mc.screen != null) { stopBreak(); return; }
        while (TOGGLE.consumeClick()) PhaseNetwork.sendAction(PhaseNetwork.message("toggle"));
        while (MARK.consumeClick()) {
            if (active() && rank.canMark() && pick(1) instanceof EntityHitResult hit) targetAction("mark", hit.getEntity());
        }
        spawnLockSmoke();
        if (!active()) return;
        mc.level.setDayTime(frozenDay); mc.level.setGameTime(frozenTime);
        mc.level.setRainLevel(frozenRain); mc.level.setThunderLevel(frozenThunder);
        if (attackDelay > 0) attackDelay--;
        if (useDelay > 0) useDelay--;
        if (mc.options.keyAttack.isDown()) attack(false); else stopBreak();
        if (mc.options.keyUse.isDown() && useDelay == 0) use(InteractionHand.MAIN_HAND);
        for (var uuid : DEAD) {
            var ghost = GHOSTS.get(uuid);
            if (ghost != null && mc.player.distanceToSqr(ghost) < 4096) {
                var random = mc.level.random;
                SkillParticles.spawn(ParticleTypes.CLOUD, ghost.getX() + (random.nextDouble() - .5) * ghost.getBbWidth(),
                        ghost.getY() + random.nextDouble() * ghost.getBbHeight(), ghost.getZ() + (random.nextDouble() - .5) * ghost.getBbWidth(), 0, .015, 0);
            }
        }
    }

    private static void spawnLockSmoke() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        for (var uuid : LOCKED.keySet()) {
            var ghost = LOCKED.get(uuid);
            if (mc.player.distanceToSqr(ghost) < 4096) {
                var random = mc.level.random;
                SkillParticles.spawn(ParticleTypes.SMOKE, ghost.getX() + (random.nextDouble() - .5) * ghost.getBbWidth(),
                        ghost.getY() + random.nextDouble() * ghost.getBbHeight(), ghost.getZ() + (random.nextDouble() - .5) * ghost.getBbWidth(), 0, .01, 0);
            }
        }
    }

    private static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!active()) return;
        event.setCanceled(true); event.setSwingHand(false);
        if (event.isAttack()) attack(true);
        else if (event.isUseItem()) use(event.getHand());
    }

    /** Uses frozen block collision data already installed by the server and independent frozen entity AABBs. */
    public static HitResult pick(float partial) {
        var mc = Minecraft.getInstance();
        if (!active() || mc.player == null || mc.level == null) return mc.hitResult;
        Vec3 start = mc.player.getEyePosition(partial);
        double reach = Math.max(mc.player.blockInteractionRange(), mc.player.entityInteractionRange());
        Vec3 end = start.add(mc.player.getViewVector(partial).scale(reach));
        HitResult best = mc.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        double distance = best.getType() == HitResult.Type.MISS ? reach * reach : best.getLocation().distanceToSqr(start);
        double entityReach = mc.player.entityInteractionRange();
        for (var ghost : GHOSTS.values()) {
            if (!(ghost instanceof LivingEntity) || ghost instanceof Player || DEAD.contains(ghost.getUUID())) continue;
            var hit = ghost.getBoundingBox().inflate(ghost.getPickRadius()).clip(start, end);
            if (hit.isPresent()) {
                double d = hit.get().distanceToSqr(start);
                if (d <= entityReach * entityReach && d < distance) { distance = d; best = new EntityHitResult(ghost, hit.get()); }
            }
        }
        return best;
    }

    private static void attack(boolean click) {
        var mc = Minecraft.getInstance();
        if (!active() || mc.player == null) return;
        var hit = pick(1);
        if (hit instanceof EntityHitResult entityHit) {
            stopBreak();
            if (click && rank.canMark() && attackDelay == 0) {
                targetAction("attack", entityHit.getEntity()); mc.player.swing(InteractionHand.MAIN_HAND); attackDelay = 4;
            }
        } else if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && (rank.canBreak() || PLACED.contains(block.getBlockPos()))) {
            BlockPos pos = block.getBlockPos();
            if (!pos.equals(breaking)) {
                stopBreak(); breaking = pos.immutable();
                var tag = action("start_break"); tag.putLong("pos", breaking.asLong()); PhaseNetwork.sendAction(tag);
            }
            if (++breakingTicks % 5 == 1) mc.player.swing(InteractionHand.MAIN_HAND);
        } else stopBreak();
    }

    private static void stopBreak() {
        if (breaking != null && active()) PhaseNetwork.sendAction(action("stop_break"));
        breaking = null; breakingTicks = 0;
    }

    private static void use(InteractionHand eventHand) {
        var mc = Minecraft.getInstance();
        if (!active() || mc.player == null || useDelay > 0) return;
        if (rank.canSlash() && mc.player.getMainHandItem().is(ModContent.BLADE.get())) {
            PhaseNetwork.sendAction(action("slash")); mc.player.swing(InteractionHand.MAIN_HAND); useDelay = 6; return;
        }
        if (!rank.canPlace()) return;
        // Vanilla emits both hand events. Select one valid block hand once to avoid duplicate placements.
        InteractionHand hand = mc.player.getMainHandItem().getItem() instanceof BlockItem ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (!(mc.player.getItemInHand(hand).getItem() instanceof BlockItem)) return;
        var hit = pick(1);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) return;
        var tag = action("place"); tag.putLong("pos", block.getBlockPos().asLong()); tag.putInt("face", block.getDirection().ordinal());
        tag.putDouble("hitX", block.getLocation().x); tag.putDouble("hitY", block.getLocation().y); tag.putDouble("hitZ", block.getLocation().z);
        tag.putInt("hand", hand == InteractionHand.MAIN_HAND ? 0 : 1);
        PhaseNetwork.sendAction(tag); mc.player.swing(hand); useDelay = 4;
    }

    private static void hud(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        var equipped = active() ? rank : ModContent.equippedRank(mc.player);
        if (equipped == null) return;
        var gui = event.getGuiGraphics();
        gui.drawString(mc.font, Component.translatableWithFallback(active() ? "hud.stillworld.inside" : "hud.stillworld.outside",
                active() ? "Stillworld · Rank %s" : "Reality · Rank %s", equipped.label()), 8, 8, 0xFFFFFFFF);
        if (!active()) {
            gui.drawString(mc.font, Component.translatableWithFallback("hud.stillworld.enter", "%s to enter", TOGGLE.getTranslatedKeyMessage()), 8, 20, 0xFFDDDDDD);
            return;
        }
        gui.drawString(mc.font, Component.translatableWithFallback("hud.stillworld.status", "%s to leave", TOGGLE.getTranslatedKeyMessage()), 8, 20, 0xFFDDDDDD);
        if (rank.canSlash()) {
            gui.drawString(mc.font, Component.translatableWithFallback("hud.stillworld.slashes", "Slashes: %s", SLASHES.size()), 8, 32, 0xFFDDDDDD);
        }
    }
}
