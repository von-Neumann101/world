package dev.stillworld.client;

import dev.stillworld.ModContent;
import dev.stillworld.Stillworld;
import dev.stillworld.core.Rank;
import dev.stillworld.server.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import top.theillusivec4.curios.api.CuriosApi;

@Mod(value=Stillworld.ID,dist=Dist.CLIENT)
public final class ForesightVisualSmoke {
    private final java.nio.file.Path output=java.nio.file.Path.of(System.getProperty("stillworld.visualTest.output"));
    private final List<String> checks=new ArrayList<>();
    private volatile UUID pigId,newId;
    private volatile boolean serverDone;
    private volatile Throwable failure;
    private boolean stopped;
    private int stage,age,total,draws,orbDraws,lowRankDraws,routeTick;
    private boolean originalHideGui;
    public ForesightVisualSmoke(IEventBus bus) {
        if(!Boolean.getBoolean("stillworld.foresightTest"))return;
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::moveFixture);
        NeoForge.EVENT_BUS.addListener(this::rendered);
    }
    private static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    private void server(Consumer<ServerPlayer> action) {
        var mc=Minecraft.getInstance();var id=mc.player.getUUID();serverDone=false;
        mc.getSingleplayerServer().execute(()->{try {action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));}catch(Throwable error){failure=error;}finally {serverDone=true;}});
    }
    private void next(){stage++;age=0;draws=0;}
    private void rendered(RenderLivingEvent.Post<?,?> event) {
        var mc=Minecraft.getInstance();var e=event.getEntity();
        if(mc.level!=null&&e.getUUID().equals(pigId)&&e==mc.level.getEntity(e.getId())&&StillworldRendering.visualPass()) {
            if(RealityOrbRenderer.rendering())orbDraws++;else {draws++;if(StillworldClient.active()&&!StillworldClient.seeLive())lowRankDraws++;}
        }
    }
    private void moveFixture(EntityTickEvent.Post event) {
        if(stopped||event.getEntity().level().isClientSide||!(event.getEntity() instanceof Mob pig)||!pig.getUUID().equals(pigId)||PhaseSkills.isLocked(pig))return;
        routeTick=(routeTick+1)%160;
        double travel=routeTick*.05;
        pig.setPos(50.5+Math.max(0,travel-4),100,-1.5+Math.min(4,travel));
        var path=pig.getNavigation().getPath();if(path!=null)path.setNextNodeIndex(travel<4?1:2);
    }
    private Mob live(UUID id) {
        for(var e:Minecraft.getInstance().level.entitiesForRendering())if(e instanceof Mob m&&e.getUUID().equals(id))return m;
        return null;
    }
    private void tick(ClientTickEvent.Post event) {
        if(stopped)return;var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
        try {
            if(failure!=null)throw new AssertionError("Server fixture failed",failure);
            if(++total>2400)throw new AssertionError("Foresight smoke timeout stage "+stage);
            if(mc.screen instanceof AccessibilityOnboardingScreen){mc.setScreen(null);return;}
            if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
            age++;
            switch(stage) {
                case 0->{
                    require(mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(mc.gameDirectory.toPath().resolve("saves/StillworldSmoke").toAbsolutePath().normalize()),"Wrong test world");
                    require(org.lwjgl.glfw.GLFW.glfwGetInputMode(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_CURSOR)==org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL,"Cursor captured");
                    require(org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_VISIBLE)==0,"Test window visible");
                    Files.createDirectories(output);Files.writeString(output.resolve("result.txt"),"RUNNING\n");originalHideGui=mc.options.hideGui;mc.options.hideGui=true;mc.setScreen(null);
                    server(this::prepare);next();
                }
                case 1->{
                    if(!serverDone||age<65)return;
                    var pig=live(pigId);require(pig!=null,"Real pig tracked");
                    require(ModContent.hasForesight(mc.player)&&ModContent.equippedRank(mc.player)==Rank.RANK4_5,"Both independent Curios slots synchronize");
                    require(ForesightView.eligible(pig)&&ForesightView.offset(pig,1)!=null&&ForesightView.offset(pig,1).length()>.5,"Real server prediction reaches client");
                    require(draws>0,"Future body actually rendered outside Stillworld");
                    require(mc.getItemRenderer().getModel(new ItemStack(ModContent.FORESIGHT_LENS.get()),mc.level,mc.player,0)!=mc.getModelManager().getMissingModel(),"Lens item model loaded");
                    shot("01-reality-prediction.png");checks.add("Independent slots, server prediction packets and actual future model draws outside Stillworld");
                    server(p->{PsycheManager.data(p).cooldownTicks=0;require(PhaseManager.enter(p),"Rank4.5 entry");});next();
                }
                case 2->{
                    if(!serverDone||age<80)return;var pig=live(pigId);
                    require(StillworldClient.active()&&StillworldClient.seeLive()&&StillworldClient.captured(pigId),"Captured rank4.5 session");
                    require(ForesightView.eligible(pig)&&ForesightView.offset(pig,1)!=null&&draws>0,"Future body rendered inside Stillworld");
                    require(orbDraws==0,"Live reality orb must not draw future echoes");shot("02-stillworld-prediction.png");
                    checks.add("Rank4.5 white projection uses future offset; no future projection leaked into reality orb");
                    server(p->{var fresh=EntityType.PIG.create(p.serverLevel());fresh.setNoAi(true);fresh.setPos(51,100,1);p.serverLevel().addFreshEntity(fresh);newId=fresh.getUUID();});next();
                }
                case 3->{
                    if(!serverDone||age<25)return;var fresh=live(newId);require(fresh!=null&&!ForesightView.eligible(fresh),"Post-entry actor stays excluded");
                    server(p->CuriosApi.getCuriosInventory(p).orElseThrow().getStacksHandler("foresight").orElseThrow().getStacks().setStackInSlot(0,ItemStack.EMPTY));next();
                }
                case 4->{
                    if(!serverDone||age<25)return;
                    require(!ModContent.hasForesight(mc.player)&&ForesightView.offset(live(pigId),1)==null,"Unequipping clears prediction cache");
                    require(StillworldClient.seeLive()&&draws>0,"Existing unshifted rank4.5 live model returns without lens");
                    checks.add("Post-entry entity exclusion and live-position model restoration on unequip");
                    server(p->{PhaseManager.exit(p);equip(p,Rank.RANK1,true);PsycheManager.data(p).cooldownTicks=0;require(PhaseManager.enter(p),"Rank1 entry");});next();
                }
                case 5->{
                    if(!serverDone||age<55)return;
                    require(StillworldClient.active()&&!StillworldClient.seeLive(),"Rank1 session reached client");
                    require(!ForesightView.eligible(live(pigId))&&lowRankDraws==0,"Lens does not bypass rank1 gate; low-rank draws="+lowRankDraws);
                    checks.add("Rank1 gate retained despite wearing the lens");server(p->PhaseManager.exit(p));next();
                }
                case 6->{
                    if(!serverDone||age<30)return;require(!StillworldClient.active()&&ForesightView.eligible(live(pigId))&&draws>0,"Prediction returns in reality");
                    server(p->CuriosApi.getCuriosInventory(p).orElseThrow().getStacksHandler("foresight").orElseThrow().getStacks().setStackInSlot(0,ItemStack.EMPTY));next();
                }
                case 7->{if(!serverDone||age<10)return;require(!ModContent.hasForesight(mc.player),"Fixture removes lens after test");finish(null);}
            }
        }catch(Throwable error){finish(error);}
    }
    private void prepare(ServerPlayer p) {
        if(PhaseManager.isPhased(p))PhaseManager.exit(p);
        var level=p.serverLevel();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
        for(int x=46;x<=57;x++)for(int z=-6;z<=6;z++){level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.SMOOTH_STONE.defaultBlockState());for(int y=100;y<105;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
        for(var e:level.getEntities(p,p.getBoundingBox().inflate(128)))if(!(e instanceof ServerPlayer))e.discard();
        p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.getInventory().clearContent();equip(p,Rank.RANK4_5,true);
        var data=PsycheManager.data(p);data.energy=100;data.cooldownTicks=0;data.doomTicks=-1;
        p.connection.teleport(54,102,-6,15,18);
        var pig=EntityType.PIG.create(level);pig.setNoAi(true);pig.setNoGravity(true);pig.setPos(50.5,100,-1.5);level.addFreshEntity(pig);pigId=pig.getUUID();
        pig.getNavigation().moveTo(new net.minecraft.world.level.pathfinder.Path(List.of(new Node(50,100,-2),new Node(50,100,2),new Node(55,100,2)),new BlockPos(55,100,2),true),1);
    }
    private static void equip(ServerPlayer p,Rank rank,boolean lens) {
        var inv=CuriosApi.getCuriosInventory(p).orElseThrow();inv.getStacksHandler("stillworld").orElseThrow().getStacks().setStackInSlot(0,new ItemStack(ModContent.CHARMS.get(rank).get()));
        inv.getStacksHandler("foresight").orElseThrow().getStacks().setStackInSlot(0,lens?new ItemStack(ModContent.FORESIGHT_LENS.get()):ItemStack.EMPTY);
    }
    private void shot(String name){try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(output.resolve(name));}catch(Exception e){throw new RuntimeException(e);}}
    private void finish(Throwable error) {
        if(stopped)return;stopped=true;var mc=Minecraft.getInstance();mc.options.hideGui=originalHideGui;
        try {Files.createDirectories(output);Files.writeString(output.resolve("result.txt"),(error==null?"PASS":"FAIL stage "+stage+": "+error)+"\n"+String.join("\n",checks));if(error!=null)error.printStackTrace();}catch(Exception e){e.printStackTrace();}
        mc.stop();
    }
}
