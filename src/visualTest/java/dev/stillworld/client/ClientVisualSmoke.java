package dev.stillworld.client;

import dev.stillworld.ModContent;
import dev.stillworld.Stillworld;
import dev.stillworld.core.Rank;
import dev.stillworld.server.PhaseManager;
import dev.stillworld.server.EntityVisual;
import dev.stillworld.network.PhaseNetwork;
import dev.stillworld.mixin.client.ChestLidHolderAccessor;
import dev.stillworld.mixin.client.ChestLidAccessor;
import dev.stillworld.mixin.client.ShulkerPoseAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;
import top.theillusivec4.curios.api.CuriosApi;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;

/** Exclusive development source set; no world-writing test code is shipped in the main jar. */
@Mod(value = Stillworld.ID, dist = Dist.CLIENT)
public final class ClientVisualSmoke {
    private enum Step { WORLD, SETUP, BASELINE, ENTER, FROZEN, BE_RELOAD, VISION_RESIZE, VISION_RELOAD, VISION_FABULOUS, MARK, ATTACK, SLASH, MINE_AIM, MINING, PLACE_AIM, PLACING, DEAD, EXIT, RESTORED }
    private static final Vec3 ENTRY = new Vec3(.5,100,.5), COW = new Vec3(.5,100,2.9);
    private static final BlockPos MINE = new BlockPos(1,100,2), SUPPORT = new BlockPos(-1,99,2), REAL_CHANGE = new BlockPos(3,100,2);
    private static final BlockPos CHEST=new BlockPos(-3,100,2), ENDER=new BlockPos(-4,100,3), SHULKER=new BlockPos(2,100,5), BOOK=new BlockPos(-3,100,5), BELL=new BlockPos(3,100,5);
    private final Map<BlockPos,BlockEntity> originalBlockEntities=new HashMap<>();
    private CompoundTag blockEntityPose;
    private final Path output = Path.of(System.getProperty("stillworld.visualTest.output", "screenshots/stillworld-smoke")).toAbsolutePath().normalize();
    private final long started = System.nanoTime();
    private final List<String> checks = new ArrayList<>();
    private Step step = Step.WORLD;
    private int age;
    private boolean finished, sent;
    private volatile boolean serverDone;
    private volatile Throwable serverFailure;
    private UUID cowId, pigId, skeletonId;
    private int markedProjectionFrames;
    private CompoundTag frozenModel;
    private long frozenTime;
    private int ghostTick, liveTick;
    private int originalWidth,originalHeight;
    private java.util.concurrent.CompletableFuture<Void> reload;
    private net.minecraft.client.GraphicsStatus originalGraphics;

    public ClientVisualSmoke(IEventBus bus) {
        if (Boolean.getBoolean("stillworld.visualTest.auto")) {
            System.out.println("STILLWORLD VISUAL HARNESS LOADED -> "+output);
            try { Files.createDirectories(output); Files.writeString(output.resolve("result.txt"),"RUNNING: waiting for StillworldSmoke\n"); }
            catch (Exception exception) { throw new IllegalStateException("Cannot create the visual test report",exception); }
            NeoForge.EVENT_BUS.addListener(this::tick);
            NeoForge.EVENT_BUS.addListener(this::livingRendered);
        }
    }

    private void tick(ClientTickEvent.Post event) {
        if (finished) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        try {
            if (serverFailure != null) throw new AssertionError("Server-side visual smoke check failed", serverFailure);
            if ((System.nanoTime()-started)/1_000_000_000L > 240) throw new AssertionError("Visual smoke timed out at "+step);
            if (step != Step.WORLD && ++age > 400) throw new AssertionError("Stage timed out: "+step);
            if (step == Step.WORLD) {
                if (mc.screen instanceof AccessibilityOnboardingScreen screen) {
                    System.out.println("STILLWORLD VISUAL: completing development-profile accessibility onboarding");
                    screen.onClose();
                    return;
                }
                if (++age % 200 == 0) System.out.println("STILLWORLD VISUAL: waiting for world, screen="+(mc.screen==null?"none":mc.screen.getClass().getName()));
                if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
                var expected = mc.gameDirectory.toPath().resolve("saves/StillworldSmoke").toAbsolutePath().normalize();
                var actual = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                require(expected.equals(actual), "Refused to modify a world outside the dedicated StillworldSmoke folder: "+actual);
                Files.createDirectories(output); Files.writeString(output.resolve("result.txt"), "RUNNING\n");
                mc.options.hideGui = false; mc.options.fov().set(70); mc.options.guiScale().set(2);
                originalGraphics=mc.options.graphicsMode().get();mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FANCY);
                onServer(this::prepare); advance(Step.SETUP); return;
            }
            if (mc.player == null || mc.level == null) return;
            switch (step) {
                case SETUP -> {
                    if (!serverDone || age<60 || ModContent.equippedRank(mc.player)!=Rank.RANK6) return;
                    mc.setScreen(null); aim(COW.add(0,.85,0)); advance(Step.BASELINE);
                }
                case BASELINE -> {
                    if(age<30)return;
                    require(mc.player.position().distanceTo(ENTRY)<.2,"Test player did not reach the prepared scene");
                    require(live(cowId)!=null,"Prepared cow did not reach the real client entity list");
                    require(mc.player.getInventory().selected==0 && mc.player.getMainHandItem().is(ModContent.BLADE.get()),"Prepared sword slot was not synchronized to the actual client");
                    shot("01-reality-color.png"); seedBlockEntityAnimations();
                    mc.player.elytraRotX=.41f;mc.player.elytraRotY=.22f;mc.player.elytraRotZ=-.63f;mc.player.swingingArm=InteractionHand.OFF_HAND;
                    click("key.stillworld.toggle"); advance(Step.ENTER);
                }
                case ENTER -> {
                    var ghost=StillworldClient.ghosts().get(cowId);
                    if(StillworldClient.active() && (age==4||age==9||age==16||age==28))shot("scan-"+age+".png");
                    if(!StillworldClient.active()||ghost==null||age<65)return;
                    require(StillworldPostEffect.settled(),"World linework shader did not finish its entry wave");
                    ghostTick=ghost.tickCount;liveTick=live(cowId).tickCount;frozenTime=mc.level.getGameTime();frozenModel=ClientPose.capture(ghost);
                    for(var entry:originalBlockEntities.entrySet())require(mc.level.getBlockEntity(entry.getKey())!=entry.getValue(),"Entry did not exercise chunk replacement for "+entry.getKey());
                    blockEntityPose=readBlockEntityPose();
                    require(blockEntityPose.getFloat("chest")>.1f && blockEntityPose.getFloat("ender")>.1f && blockEntityPose.getFloat("shulker")>.1f
                            && blockEntityPose.getInt("bookTime")>=500 && blockEntityPose.getBoolean("bellShaking"),"Entry lost nonzero chest/book/shulker/bell model progress");
                    checks.add("V entered a real server-backed session and loaded monochrome post-processing");
                    shot("02-frozen-entry.png");
                    var ownPose=ClientPose.capture(mc.player);
                    onServer(p->{
                        p.serverLevel().getEntity(cowId).setPos(COW.add(2,0,1));p.serverLevel().setBlockAndUpdate(REAL_CHANGE,Blocks.DIAMOND_BLOCK.defaultBlockState());
                        p.serverLevel().getEntity(skeletonId).setPos(-1.7,100,3.6);
                        // An additional detached self visual is invisible during phase, but exercises the vanilla player model in isolation.
                        var visual=EntityVisual.capture(p);visual.put("clientPose",ownPose);
                        var data=PhaseNetwork.message("ghost");data.putUUID("session",PhaseManager.get(p).id);data.put("visual",visual);PhaseNetwork.sendState(p,data);
                    });
                    advance(Step.FROZEN);
                }
                case FROZEN -> {
                    if(age<65||!serverDone)return;
                    checkLinework();
                    var ghost=StillworldClient.ghosts().get(cowId);var real=live(cowId);
                    require(ghost.tickCount==ghostTick && frozenModel.equals(ClientPose.capture(ghost)),"Frozen vanilla model or animation time advanced");
                    require(real!=null && real.tickCount>liveTick+20,"Real client entity updates stopped while the frozen clone was displayed");
                    require(ghost.position().distanceTo(COW)<.01 && real.position().distanceTo(COW)>1,"Frozen and real entity positions were not separated");
                    require(StillworldRendering.hideOriginal(real),"Normal real entity remained visible beneath the snapshot");
                    require(mc.level.getGameTime()==frozenTime,"Frozen world time advanced");
                    require(blockEntityPose.equals(readBlockEntityPose()),"Frozen block-entity model progress advanced");
                    require(mc.level.getBlockState(REAL_CHANGE).is(Blocks.RED_WOOL),"A real terrain update leaked into the frozen view");
                    for(var entity:mc.level.entitiesForRendering())require(entity!=ghost,"A visual clone was registered as a real level entity");
                    var playerVisual=(AbstractClientPlayer)StillworldClient.ghosts().get(mc.player.getUUID());
                    require(playerVisual!=null&&playerVisual.swingingArm==InteractionHand.OFF_HAND,"Frozen player lost the recorded off-hand swing side");
                    require(playerVisual.elytraRotX==.41f&&playerVisual.elytraRotY==.22f&&playerVisual.elytraRotZ==-.63f,"Frozen player lost its recorded wing angles during visual construction");
                    var wings=new ElytraModel<AbstractClientPlayer>(mc.getEntityModels().bakeLayer(ModelLayers.ELYTRA));
                    var beforeWings=ClientPose.capture(playerVisual);
                    for(int i=0;i<30;i++)wings.setupAnim(playerVisual,0,0,playerVisual.tickCount,0,0);
                    require(beforeWings.equals(ClientPose.capture(playerVisual)),"Vanilla render-time Elytra smoothing advanced a frozen player's wing pose");
                    checks.add("Player off-hand pose survived capture and thirty vanilla Elytra render-model updates preserved its recorded wing angles");
                    checks.add("Snapshot model/time/terrain froze while live entity ticks and positions continued independently");
                    shot("03-live-white-projection.png");
                    mc.level.getChunkSource().drop(new ChunkPos(-1,0));
                    onServer(p->{var snapshot=PhaseManager.get(p).snapshot().chunk(-1,0);require(snapshot!=null,"Missing saved chunk for unload/reload smoke check");p.connection.send(snapshot.packet());});
                    advance(Step.BE_RELOAD);
                }
                case BE_RELOAD -> {
                    if(!serverDone)return;
                    if(!sent&&age>=15){require(blockEntityPose.equals(readBlockEntityPose()),"Unloading/reloading a saved chunk replaced frozen BE progress with defaults");aim(new Vec3(-2.5,100.8,3.5));sent=true;}
                    if(!sent||age<30)return;
                    shot("07-frozen-block-entities.png");checks.add("Chest/ender/shulker/book/bell progress survived initial chunk replacement, stayed frozen, and survived actual chunk unload/reload");
                    aim(new Vec3(8,102,10));
                    originalWidth=mc.getWindow().getWidth();originalHeight=mc.getWindow().getHeight();
                    org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1024,576);
                    advance(Step.VISION_RESIZE);
                }
                case VISION_RESIZE -> {
                    if(age<15)return;
                    require(mc.getMainRenderTarget().width==1024&&mc.getMainRenderTarget().height==576,"Client window did not really resize");
                    require(StillworldPostEffect.settled(),"Resize lost the settled world effect");
                    checkFoliage();
                    shot("08-linework-resized.png");
                    reload=mc.reloadResourcePacks();advance(Step.VISION_RELOAD);
                }
                case VISION_RELOAD -> {
                    if(reload==null||!reload.isDone()||mc.getOverlay()!=null||age<45||!StillworldPostEffect.settled())return;
                    reload.join();
                    shot("09-linework-resource-reload.png");
                    require(frozenModel.equals(ClientPose.capture(StillworldClient.ghosts().get(cowId))),"Resource reload changed the frozen entity pose");
                    org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),originalWidth,originalHeight);
                    checks.add("Actual window resize and resource reload rebuilt linework and retained the settled state/frozen model");
                    aim(StillworldClient.ghosts().get(cowId).getBoundingBox().getCenter());
                    mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FABULOUS);mc.levelRenderer.allChanged();
                    advance(Step.VISION_FABULOUS);
                }
                case VISION_FABULOUS -> {
                    if(age<50)return;
                    require(Minecraft.useShaderTransparency(),"Fabulous transparency was not actually enabled");
                    checkLinework();shot("10-linework-fabulous.png");
                    checks.add("Fabulous transparency composition retained scene depth and sparse structural contours");
                    mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FANCY);mc.levelRenderer.allChanged();
                    advance(Step.MARK);
                }
                case MARK -> {
                    if(!sent && age>=5){
                        require(StillworldClient.pick(1) instanceof EntityHitResult hit && hit.getEntity().getUUID().equals(cowId),"Aim did not use the old frozen AABB");
                        click("key.stillworld.mark");sent=true;
                    }
                    if(!sent || age<25 || live(cowId)==null || !StillworldClient.locked(live(cowId)))return;
                    require(markedProjectionFrames==0,"Marked creature still rendered an extra white projection over the original snapshot");
                    require(StillworldClient.ghosts().get(cowId).tickCount==ghostTick
                            && frozenModel.equals(ClientPose.capture(StillworldClient.ghosts().get(cowId))),"Mark changed the original frozen creature model");
                    require(live(cowId).position().distanceTo(COW)<.05,"Locked live projection remained at the old pre-rewind interpolation position");
                    onServer(p->{var s=PhaseManager.get(p);require(s!=null&&s.marked.containsKey(cowId),"R did not mark the server's original cow");
                        var cow=(LivingEntity)p.serverLevel().getEntity(cowId);require(cow.position().distanceTo(COW)<.01,"Mark did not rewind the same real cow");
                        require(s.actors.get(cowId).contains("clientPose"),"Captured client pose did not reach the server");});
                    checks.add("R selected the snapshot AABB, rewound the same real creature, and forwarded the cosmetic pose");advance(Step.ATTACK);
                }
                case ATTACK -> {
                    if(!serverDone)return;
                    if(!sent && age>=6){click(mc.options.keyAttack);sent=true;}
                    if(!sent||age<25)return;
                    onServer(p->{var s=PhaseManager.get(p);var cow=(LivingEntity)p.serverLevel().getEntity(cowId);
                        require(s.damage.accumulated(cowId)>0,"Left click did not enter delayed damage into the ledger");require(cow.getHealth()==100,"Rank6 attack damaged the real target before exit");});
                    checks.add("Left click recorded damage without reducing the live target's health");advance(Step.SLASH);
                }
                case SLASH -> {
                    if(!serverDone)return;
                    if(!sent && age>=8){click(mc.options.keyUse);sent=true;}
                    if(!sent||age<25||StillworldClient.slashes().size()!=1)return;
                    shot("04-marked-ledger-slash.png");checks.add("Right click with the Stillworld blade placed one stationary slash");
                    mc.player.getInventory().selected=2;aim(Vec3.atCenterOf(MINE));advance(Step.MINE_AIM);
                }
                case MINE_AIM -> {if(age<6)return;mc.options.keyAttack.setDown(true);advance(Step.MINING);}
                case MINING -> {
                    if(!mc.level.getBlockState(MINE).isAir())return;
                    mc.options.keyAttack.setDown(false);
                    onServer(p->{require(p.serverLevel().getBlockState(MINE).is(ModContent.GRAY_MIST.get()),"Continuous mining did not create gray mist in reality");});
                    checks.add("Holding left click completed server-timed mining against frozen terrain");
                    mc.player.getInventory().selected=1;aim(Vec3.atCenterOf(SUPPORT).add(0,.5,0));advance(Step.PLACE_AIM);
                }
                case PLACE_AIM -> {if(!serverDone||age<8)return;click(mc.options.keyUse);advance(Step.PLACING);}
                case PLACING -> {
                    if(age<15||!mc.level.getBlockState(SUPPORT.above()).is(Blocks.DIRT))return;
                    onServer(p->{require(p.serverLevel().getBlockState(SUPPORT.above()).is(ModContent.BLACK_MIST.get()),"Placement did not make real black mist");
                        require(p.getInventory().getItem(1).getCount()==63,"Placement did not consume exactly one normal block");
                        var pig=(LivingEntity)p.serverLevel().getEntity(pigId);pig.hurt(pig.damageSources().genericKill(),10000);});
                    checks.add("Right click placed snapshot dirt, made real black mist, and consumed one item");aim(new Vec3(-.5,100.8,3.5));advance(Step.DEAD);
                }
                case DEAD -> {
                    if(!serverDone||age<30||!StillworldClient.isDead(pigId))return;
                    shot("05-edits-and-white-death-smoke.png");checks.add("A real death removed the frozen body and enabled white smoke");
                    click("key.stillworld.toggle");advance(Step.EXIT);
                }
                case EXIT -> {
                    if(StillworldClient.active()||age<30)return;
                    require(StillworldClient.ghosts().isEmpty(),"Exit retained snapshot entities");
                    require(!StillworldPostEffect.loaded(),"Exit retained the world linework effect");
                    require(mc.level.getBlockState(MINE).isAir(),"Permanent mining was restored on exit");
                    require(mc.level.getBlockState(SUPPORT.above()).isAir(),"Temporary placement remained after exit");
                    require(mc.level.getBlockState(REAL_CHANGE).is(Blocks.DIAMOND_BLOCK),"Exit did not restore current real terrain");
                    onServer(p->{require(PhaseManager.get(p)==null,"V did not finish the server session");var cow=(LivingEntity)p.serverLevel().getEntity(cowId);require(cow.getHealth()<100,"Exit failed to settle stored damage/slash");});
                    checks.add("V restored live color/terrain, removed temporary placement, retained permanent mining, and settled damage");advance(Step.RESTORED);
                }
                case RESTORED -> {if(!serverDone||age<25)return;require(mc.level.getGameTime()>frozenTime,"Live environment time did not resume");shot("06-reality-restored.png");finish(null);}
                default -> { }
            }
        } catch(Throwable failure){finish(failure);}
    }

    private void prepare(ServerPlayer player) {
        var server=player.getServer();var level=player.serverLevel();
        require(server!=null && level==server.overworld(),"Smoke world must be in the overworld");
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true,server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);
        for(var entity:level.getEntities((Entity)null,new AABB(-16,98,-16,16,110,16)))if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
        for(int x=-12;x<=12;x++)for(int z=-8;z<=14;z++){
            level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.SMOOTH_STONE.defaultBlockState());
            for(int y=100;y<=106;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        for(int y=100;y<=103;y++){level.setBlockAndUpdate(new BlockPos(-4,y,6),Blocks.LIME_WOOL.defaultBlockState());level.setBlockAndUpdate(new BlockPos(4,y,6),Blocks.RED_WOOL.defaultBlockState());}
        // Textured terrain/foliage fixture for judging whether structural lines drown out surface detail.
        for(int x=5;x<=12;x++)for(int z=8;z<=14;z++) {
            int top=99+(x>8?1:0)+(z>11?1:0);
            for(int y=99;y<=top;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),
                    (y==top?Blocks.GRASS_BLOCK:Blocks.STONE).defaultBlockState());
        }
        for(int y=101;y<=104;y++)level.setBlockAndUpdate(new BlockPos(9,y,10),Blocks.OAK_LOG.defaultBlockState());
        for(int x=7;x<=11;x++)for(int z=8;z<=12;z++)for(int y=104;y<=106;y++)
            if((x!=9||z!=10||y>104)&&Math.abs(x-9)+Math.abs(z-10)+(y-104)<5)
                level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT,true));
        for(int x=5;x<=6;x++)for(int z=11;z<=13;z++)level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.WATER.defaultBlockState());
        level.setBlockAndUpdate(MINE,Blocks.DIRT.defaultBlockState());level.setBlockAndUpdate(REAL_CHANGE,Blocks.RED_WOOL.defaultBlockState());
        level.setBlockAndUpdate(CHEST,Blocks.CHEST.defaultBlockState());level.setBlockAndUpdate(ENDER,Blocks.ENDER_CHEST.defaultBlockState());
        level.setBlockAndUpdate(SHULKER,Blocks.PURPLE_SHULKER_BOX.defaultBlockState());level.setBlockAndUpdate(BOOK,Blocks.ENCHANTING_TABLE.defaultBlockState());level.setBlockAndUpdate(BELL,Blocks.BELL.defaultBlockState());
        player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();player.getInventory().setItem(0,new ItemStack(ModContent.BLADE.get()));
        player.getInventory().setItem(1,new ItemStack(Items.DIRT,64));player.getInventory().setItem(2,new ItemStack(Items.NETHERITE_PICKAXE));player.getInventory().selected=0;
        player.connection.send(new ClientboundSetCarriedItemPacket(0));
        CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler("stillworld").orElseThrow().getStacks().setStackInSlot(0,new ItemStack(ModContent.CHARMS.get(Rank.RANK6).get()));
        player.connection.teleport(ENTRY.x,ENTRY.y,ENTRY.z,0,12);player.setHealth(player.getMaxHealth());player.getFoodData().setFoodLevel(20);player.containerMenu.broadcastChanges();
        var cow=EntityType.COW.create(level);require(cow!=null,"Cannot spawn cow");cow.setPos(COW);cow.setNoAi(true);cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);cow.setHealth(100);level.addFreshEntity(cow);cowId=cow.getUUID();
        var pig=EntityType.PIG.create(level);require(pig!=null,"Cannot spawn pig");pig.setPos(-2,100,3.5);pig.setNoAi(true);level.addFreshEntity(pig);pigId=pig.getUUID();
        var skeleton=EntityType.SKELETON.create(level);require(skeleton!=null,"Cannot spawn skeleton");skeleton.setPos(-3.5,100,6.5);skeleton.setNoAi(true);
        skeleton.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));
        skeleton.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));
        skeleton.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,new ItemStack(Items.TORCH));
        level.addFreshEntity(skeleton);skeletonId=skeleton.getUUID();
    }
    private void livingRendered(RenderLivingEvent.Pre<?,?> event) {
        var entity=event.getEntity();
        if(cowId!=null&&entity.getUUID().equals(cowId)&&StillworldClient.active()&&StillworldRendering.visualPass()
                &&StillworldClient.locked(entity)&&entity!=StillworldClient.ghosts().get(cowId))markedProjectionFrames++;
    }
    private void onServer(Consumer<ServerPlayer> action) {
        var mc=Minecraft.getInstance();serverDone=false;var id=mc.player.getUUID();
        mc.getSingleplayerServer().execute(()->{try {var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);require(player!=null,"Test player disconnected");action.accept(player);}
            catch(Throwable failure){serverFailure=failure;}finally{serverDone=true;}});
    }
    private void advance(Step next){step=next;age=0;sent=false;System.out.println("STILLWORLD VISUAL STEP "+next);}
    private void seedBlockEntityAnimations(){
        var level=Minecraft.getInstance().level;
        for(var pos:List.of(CHEST,ENDER,SHULKER,BOOK,BELL)){
            var entity=level.getBlockEntity(pos);require(entity!=null,"Prepared BE was not loaded: "+pos);originalBlockEntities.put(pos,entity);
        }
        for(var pos:List.of(CHEST,ENDER)){
            var lid=(ChestLidAccessor)(Object)((ChestLidHolderAccessor)level.getBlockEntity(pos)).stillworld$lid();
            lid.stillworld$open(.6f);lid.stillworld$previous(.5f);lid.stillworld$shouldOpen(true);
        }
        var shulker=(ShulkerPoseAccessor)level.getBlockEntity(SHULKER);shulker.stillworld$progress(.6f);shulker.stillworld$previous(.5f);shulker.stillworld$status(ShulkerBoxBlockEntity.AnimationStatus.OPENING);
        var book=(EnchantingTableBlockEntity)level.getBlockEntity(BOOK);book.time=500;book.open=book.oOpen=.8f;book.flip=book.oFlip=.7f;book.rot=book.oRot=book.tRot=.8f;
        var bell=(BellBlockEntity)level.getBlockEntity(BELL);
        bell.triggerEvent(1,Direction.EAST.get3DDataValue()); // Vanilla event initializes the nearby-entity list before ringing ticks.
        bell.ticks=15;
    }
    private CompoundTag readBlockEntityPose(){
        var level=Minecraft.getInstance().level;var tag=new CompoundTag();
        tag.putFloat("chest",((ChestBlockEntity)level.getBlockEntity(CHEST)).getOpenNess(1));
        tag.putFloat("ender",((EnderChestBlockEntity)level.getBlockEntity(ENDER)).getOpenNess(1));
        tag.putFloat("shulker",((ShulkerBoxBlockEntity)level.getBlockEntity(SHULKER)).getProgress(1));
        var book=(EnchantingTableBlockEntity)level.getBlockEntity(BOOK);tag.putInt("bookTime",book.time);tag.putFloat("bookOpen",book.open);tag.putFloat("bookFlip",book.flip);tag.putFloat("bookRotation",book.rot);
        var bell=(BellBlockEntity)level.getBlockEntity(BELL);tag.putInt("bellTicks",bell.ticks);tag.putBoolean("bellShaking",bell.shaking);tag.putString("bellDirection",bell.clickDirection==null?"none":bell.clickDirection.getName());return tag;
    }
    private void aim(Vec3 target){var player=Minecraft.getInstance().player;var d=target.subtract(player.getEyePosition());player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));}
    private static void click(String name){click(Arrays.stream(Minecraft.getInstance().options.keyMappings).filter(k->k.getName().equals(name)).findFirst().orElseThrow());}
    private static void click(KeyMapping mapping){KeyMapping.click(mapping.getKey());}
    private static Entity live(UUID id){for(var entity:Minecraft.getInstance().level.entitiesForRendering())if(entity.getUUID().equals(id))return entity;return null;}
    private void shot(String name)throws Exception{try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(output.resolve(name));}}
    private void checkLinework() throws Exception {
        try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            int count=0,bright=0,black=0;double sum=0;
            // Frozen cow and its silhouette: excludes sky/void, HUD and held weapon.
            for(int y=image.getHeight()*30/100;y<image.getHeight()*70/100;y++)for(int x=image.getWidth()*43/100;x<image.getWidth()*56/100;x++) {
                int c=image.getPixelRGBA(x,y);double l=((c&255)+((c>>8)&255)+((c>>16)&255))/765.0;
                sum+=l;count++;if(l>.72)bright++;if(l<.035)black++;
            }
            double mean=sum/count,lines=(double)bright/count,dark=(double)black/count;
            Files.writeString(output.resolve("linework-metrics.txt"),"mean="+mean+" white="+lines+" black="+dark+"\n");
            require(mean>.08&&mean<.30,"Frozen surface did not become readable dark-gray linework: mean="+mean);
            require(lines>.001&&lines<.15,"World lacks sparse bright contours, or has too many: "+lines);
            require(dark<.15,"Linework crushed world surfaces into black: "+dark);
            int contour=0,contourCount=0,leak=0,leakCount=0;
            for(int y=image.getHeight()*35/100;y<image.getHeight()*58/100;y++)for(int x=image.getWidth()*395/1000;x<image.getWidth()*425/1000;x++) {
                int c=image.getPixelRGBA(x,y);if(((c&255)+((c>>8)&255)+((c>>16)&255))>551)contour++;contourCount++;
            }
            for(int y=image.getHeight()*50/100;y<image.getHeight()*60/100;y++)for(int x=image.getWidth()*27/100;x<image.getWidth()*36/100;x++) {
                int c=image.getPixelRGBA(x,y);if(((c&255)+((c>>8)&255)+((c>>16)&255))>497)leak++;leakCount++;
            }
            require((double)contour/contourCount>.02,"Frozen cow silhouette lost its fine structural contour");
            require((double)leak/leakCount<.02,"Live white projection leaked through the foreground opaque block");
            checks.add("Rendered world retained gray surface tones with sparse white contours and no crushed black surfaces");
        }
    }
    private void checkFoliage() {
        try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            int white=0,count=0;
            for(int y=image.getHeight()*22/100;y<image.getHeight()*32/100;y++)for(int x=image.getWidth()*395/1000;x<image.getWidth()*48/100;x++) {
                int c=image.getPixelRGBA(x,y);if(((c&255)+((c>>8)&255)+((c>>16)&255))>497)white++;count++;
            }
            require((double)white/count<.18,"Leaf cutouts became dense white linework: "+(double)white/count);
            checks.add("Textured foliage retained restrained line density after window resizing");
        }
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private void finish(Throwable failure){
        if(originalGraphics!=null)Minecraft.getInstance().options.graphicsMode().set(originalGraphics);
        if(finished)return;finished=true;var mc=Minecraft.getInstance();mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);
        try {Files.createDirectories(output);Files.writeString(output.resolve("result.txt"),(failure==null?"PASS":"FAIL at "+step+": "+failure)+"\n"+String.join("\n",checks)+"\n");
            if(failure!=null){shot("failure.png");failure.printStackTrace();}System.out.println("STILLWORLD CLIENT SMOKE "+(failure==null?"PASS":"FAIL")+" -> "+output);
        }catch(Exception exception){exception.printStackTrace();}mc.stop();
    }
}
