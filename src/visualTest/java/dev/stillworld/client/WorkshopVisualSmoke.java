package dev.stillworld.client;

import dev.stillworld.ModContent;
import dev.stillworld.Stillworld;
import dev.stillworld.workshop.*;
import dev.stillworld.client.workshop.StabilizerScreen;
import dev.stillworld.server.PsycheManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Actual server/client workstation flow in the dedicated hidden smoke world only. */
@Mod(value=Stillworld.ID,dist=Dist.CLIENT)
public final class WorkshopVisualSmoke {
    private static final BlockPos CENTER=new BlockPos(20,100,0),CHAMBER=new BlockPos(24,100,0);
    private final Path output=Path.of(System.getProperty("stillworld.visualTest.output","docs/verification/workshops-client")).toAbsolutePath();
    private final List<String> checks=new ArrayList<>();
    private int stage,age,total;
    private boolean stopped,started;
    private volatile boolean serverDone;
    private volatile Throwable failure;
    public WorkshopVisualSmoke(IEventBus bus) {if(Boolean.getBoolean("stillworld.workshopTest"))NeoForge.EVENT_BUS.addListener(this::tick);}
    private static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    private void server(Consumer<ServerPlayer> task) {
        var mc=Minecraft.getInstance();var id=mc.player.getUUID();serverDone=false;
        mc.getSingleplayerServer().execute(()->{try {task.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));}catch(Throwable t){failure=t;}finally{serverDone=true;}});
    }
    private void next() {stage++;age=0;}
    private void tick(ClientTickEvent.Post event) {
        if(stopped)return;var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
        try {
            if(failure!=null)throw new AssertionError("Server workshop check",failure);
            if(++total>2400)throw new AssertionError("Workshop smoke timed out at "+stage);
            if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
            age++;
            switch(stage) {
                case 0->{
                    var expected=mc.gameDirectory.toPath().resolve("saves/StillworldSmoke").toAbsolutePath().normalize();
                    require(expected.equals(mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()),"Wrong test world");
                    require(org.lwjgl.glfw.GLFW.glfwGetInputMode(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_CURSOR)==org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL,"Cursor captured");
                    require(org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_VISIBLE)==0,"Test window visible");
                    Files.createDirectories(output);Files.writeString(output.resolve("result.txt"),"RUNNING\n");
                    mc.options.hideGui=false;mc.options.guiScale().set(2);mc.options.fov().set(70);mc.setScreen(null);
                    server(this::prepare);next();
                }
                case 1->{
                    if(!serverDone||age<80)return;
                    int count=0;for(var pos:auxiliaries())if(mc.level.getBlockEntity(pos) instanceof AuxiliaryBlockEntity be&&be.biomeIndex()>=0)count++;
                    require(count==8,"All8miniature scenes must sync to realclient");
                    MiniatureGeometryChecks.verify();checks.add("Nether shroomlights and ocean water sides have no overlapping solid geometry planes");
                    var models=mc.getBlockRenderer().getBlockModelShaper();var missing=mc.getModelManager().getMissingModel();
                    for(var block:List.of(WorkshopContent.ORE.get(),WorkshopContent.DEEPSLATE_ORE.get(),WorkshopContent.STABILIZER.get(),WorkshopContent.OVERWORLD_AUXILIARY.get(),WorkshopContent.NETHER_AUXILIARY.get(),WorkshopContent.END_AUXILIARY.get(),ModContent.TREATMENT_MACHINE.get()))
                        for(var state:block.getStateDefinition().getPossibleStates())require(models.getBlockModel(state)!=missing,"Missing block model: "+state);
                    for(var item:List.of(WorkshopContent.ANCHOR_RECORDER.get(),WorkshopContent.UNSTABLE_MATERIAL.get(),WorkshopContent.STABLE_MATERIAL.get()))require(mc.getItemRenderer().getModel(new ItemStack(item),mc.level,mc.player,0)!=missing,"Missing item model");
                    shot("01-workstations.png");checks.add("All8auxiliary displays synchronized; all blockstate/item models loaded");
                    server(p->{p.connection.teleport(20.5,100,-1.2,0,15);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.setShiftKeyDown(false);p.serverLevel().getBlockState(CENTER).useWithoutItem(p.serverLevel(),p,new BlockHitResult(Vec3.atCenterOf(CENTER),Direction.NORTH,CENTER,false));});next();
                }
                case 2->{
                    if(!serverDone||age<30)return;require(mc.screen instanceof StabilizerScreen,"Actualservermenu must openStabilizerScreen");
                    var menu=(StabilizerMenu)mc.player.containerMenu;require(menu.active()&&menu.authorized(),"Menuactivation synced");
                    require(menu.progress()>0,"Processingprogress synced");shot("02-processing-menu.png");checks.add("Real menu opens, activates and synchronizes processing without fuel");next();
                }
                case 3->{
                    if(age<210)return;var menu=(StabilizerMenu)mc.player.containerMenu;require(menu.getSlot(1).hasItem(),"Stableoutput reachedclient after200ticks");
                    shot("03-processing-output.png");mc.player.closeContainer();
                    server(p->{
                        p.setGameMode(GameType.SURVIVAL);p.getAbilities().flying=false;p.onUpdateAbilities();
                        p.connection.teleport(CHAMBER.getX()+.5,100,CHAMBER.getZ()-1.3,0,0);
                        PsycheManager.data(p).healthScale=1;PsycheManager.applyHealth(p);PsycheManager.loseFraction(p,.5);
                        var be=(TreatmentBlockEntity)p.serverLevel().getBlockEntity(CHAMBER);be.activate(full(),p);be.addCharge();be.addCharge();
                        p.serverLevel().getBlockState(CHAMBER).useWithoutItem(p.serverLevel(),p,new BlockHitResult(Vec3.atCenterOf(CHAMBER),Direction.NORTH,CHAMBER,false));
                    });next();
                }
                case 4->{
                    if(!serverDone||age<30)return;require(mc.level.getBlockState(CHAMBER).getValue(TreatmentBlock.OPEN),"Dooropen must sync");shot("04-treatment-open.png");
                    server(p->p.connection.teleport(CHAMBER.getX()+.5,CHAMBER.getY()+.0625,CHAMBER.getZ()+.5,0,0));next();
                }
                case 5->{
                    if(!serverDone||age<25)return;require(TreatmentBlock.isTreating(mc.player),"OccupantUUID must sync");require(!mc.level.getBlockState(CHAMBER).getValue(TreatmentBlock.OPEN),"Patient entry closesdoor");
                    mc.options.keyShift.setDown(true);next();
                }
                case 6->{
                    if(age<30)return;require(!mc.player.input.shiftKeyDown&&!mc.player.isCrouching(),"Real input andpose must reject crouch during treatment");
                    shot("05-treatment-inside.png");checks.add("Entry automatically closes chamber; actual client crouch input suppressed");mc.options.keyShift.setDown(false);next();
                }
                case 7->{
                    if(age<180)return;require(!TreatmentBlock.isTreating(mc.player)&&mc.level.getBlockState(CHAMBER).getValue(TreatmentBlock.OPEN),"Completion opensdoor and releasesinput");
                    require(Math.abs(mc.player.getMaxHealth()-18)<.001,"8charge repairs10->18maxHP onactualclient");
                    server(p->{var be=(TreatmentBlockEntity)p.serverLevel().getBlockEntity(CHAMBER);require(be.charge()==0&&Math.abs(p.getMaxHealth()-18)<.001,"Serverpartialheal andchargeagree");});next();
                }
                case 8->{
                    if(!serverDone)return;checks.add("Ten-second treatment restores exactly8maxHP with2materials and releases door");
                    mc.options.hideGui=true;mc.options.fov().set(45);
                    server(p->{p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.connection.teleport(20.5,100.7,5.2,180,36);});next();
                }
                case 9->{
                    if(!serverDone||age<30)return;shot("06-ocean-close.png");
                    server(p->p.connection.teleport(22.5,100.7,-3.8,0,27));next();
                }
                case 10->{
                    if(!serverDone)return;
                    if(age==30)shot("07-crimson-close-a.png");
                    if(age<50)return;shot("08-crimson-close-b.png");
                    mc.options.hideGui=false;mc.options.fov().set(70);finish(null);
                }
                default->{}
            }
        } catch(Throwable t) {finish(t);}
    }
    private static ItemStack full() {var stack=new ItemStack(WorkshopContent.ANCHOR_RECORDER.get());stack.set(WorkshopContent.ANCHOR_VISITS.get(),255);return stack;}
    private static List<BlockPos> auxiliaries() {return List.of(CENTER.north(3),CENTER.east(3),CENTER.south(3),CENTER.west(3),CENTER.offset(-2,0,-2),CENTER.offset(2,0,-2),CENTER.offset(2,0,2),CENTER.offset(-2,0,2));}
    private void prepare(ServerPlayer p) {
        var level=p.serverLevel();
        for(int x=15;x<=27;x++)for(int z=-6;z<=6;z++) {
            level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.SMOOTH_STONE.defaultBlockState());
            for(int y=100;y<=105;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
        p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.getInventory().clearContent();p.setItemInHand(InteractionHand.MAIN_HAND,full());
        p.getInventory().setItem(1,new ItemStack(WorkshopContent.UNSTABLE_MATERIAL.get()));p.getInventory().setItem(2,new ItemStack(WorkshopContent.STABLE_MATERIAL.get()));
        p.connection.teleport(20.5,102.8,-6,0,30);p.containerMenu.broadcastChanges();
        level.setBlockAndUpdate(CENTER,WorkshopContent.STABILIZER.get().defaultBlockState());var aux=auxiliaries();
        for(int i=0;i<8;i++)level.setBlockAndUpdate(aux.get(i),(i<4?WorkshopContent.OVERWORLD_AUXILIARY.get():i<6?WorkshopContent.NETHER_AUXILIARY.get():WorkshopContent.END_AUXILIARY.get()).defaultBlockState());
        var machine=(StabilizerBlockEntity)level.getBlockEntity(CENTER);require(machine.activate(full(),p),"Visualfixturemultiblockactivation");machine.setItem(0,new ItemStack(WorkshopContent.UNSTABLE_MATERIAL.get(),8));
        var state=ModContent.TREATMENT_MACHINE.get().defaultBlockState();level.setBlock(CHAMBER,state,3);level.setBlock(CHAMBER.above(),state.setValue(TreatmentBlock.HALF,DoubleBlockHalf.UPPER),3);
        level.setBlockAndUpdate(new BlockPos(16,100,0),WorkshopContent.ORE.get().defaultBlockState());level.setBlockAndUpdate(new BlockPos(16,100,1),WorkshopContent.DEEPSLATE_ORE.get().defaultBlockState());
    }
    private void shot(String name) {
        var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {image.writeToFile(output.resolve(name));}catch(Exception e){throw new RuntimeException(e);}
    }
    private void finish(Throwable problem) {
        if(stopped)return;stopped=true;var mc=Minecraft.getInstance();mc.options.keyShift.setDown(false);
        try {Files.createDirectories(output);Files.writeString(output.resolve("result.txt"),(problem==null?"PASS":"FAIL stage "+stage+": "+problem)+"\n"+String.join("\n",checks));if(problem!=null){problem.printStackTrace();shot("failure.png");}}catch(Exception e){e.printStackTrace();}
        System.out.println("STILLWORLD WORKSHOP SMOKE "+(problem==null?"PASS":"FAIL"));mc.stop();
    }
}
