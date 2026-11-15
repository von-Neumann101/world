package dev.stillworld.client;

import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Test-only long-lived visual fixture, followed by the harness's real timed attack. */
final class RiftVisualChecks {
    private final Path output;
    private final UUID id=UUID.randomUUID();
    private int stage,age,liveFrames,backPixels;
    private boolean done;
    private volatile boolean serverDone;
    private volatile Throwable failure;
    private GraphicsStatus graphics;
    private CompletableFuture<Void> reload;
    RiftVisualChecks(Path output){this.output=output;}
    boolean tick()throws Exception {
        if(done)return true;
        if(failure!=null)throw new AssertionError("Rift scene setup failed",failure);
        var mc=Minecraft.getInstance();age++;
        switch(stage) {
            case 0->{
                graphics=mc.options.graphicsMode().get();mc.options.graphicsMode().set(GraphicsStatus.FANCY);mc.levelRenderer.allChanged();
                mc.options.hideGui=true;mc.player.getInventory().selected=8;
                server(p->{p.connection.teleport(8.5,100,.5,0,0);wall(p,8,Blocks.RED_WOOL);wall(p,0,Blocks.LIME_WOOL);});next();
            }
            case 1->{
                if(!serverDone||age<35)return false;
                require(!StillworldClient.slashes().isEmpty()||!RealityBladeRenderer.needsReality(),"Rift fixture must not depend on a held blade");
                StillworldClient.slashes().add(new StillworldRendering.Slash(id,new Vec3(8.5,101.62,4.5),0,0));
                liveFrames=RealityOrbRenderer.liveFrames();next();
            }
            case 2->{
                if(age<20)return false;
                require(PsycheView.windowRadius()==0,"HUD should be hidden");
                require(RealityOrbRenderer.liveFrames()>liveFrames+3,"Slash-only scene did not refresh live reality");
                require(colored("rift-front-red.png",0)>150,"Sharp slit did not show the red live-only wall");
                server(p->wall(p,8,Blocks.LIME_WOOL));next();
            }
            case 3->{
                if(!serverDone||age<25)return false;
                require(colored("rift-front-live-update.png",1)>150,"Slit did not update to the new green live wall");
                require(mc.level.getBlockState(new BlockPos(8,102,8)).isAir(),"Live wall leaked into the frozen main world");
                server(p->p.connection.teleport(8.5,100,8,180,0));next();
            }
            case 4->{
                if(!serverDone||age<25)return false;
                backPixels=colored("rift-back.png",1);require(backPixels>150,"Reverse side of slit disappeared");
                foreground(Blocks.STONE);next();
            }
            case 5->{
                if(age<25)return false;
                require(colored("rift-occluded.png",1)<backPixels*.1,"Slit showed reality through solid foreground terrain");
                foreground(Blocks.AIR);mc.options.graphicsMode().set(GraphicsStatus.FABULOUS);mc.levelRenderer.allChanged();next();
            }
            case 6->{
                if(age<35)return false;
                require(Minecraft.useShaderTransparency(),"Fabulous did not activate");
                require(colored("rift-fabulous.png",1)>150,"Fabulous lost the live opening");
                foreground(Blocks.STONE);next();
            }
            case 7->{
                if(age<25)return false;
                require(colored("rift-fabulous-occluded.png",1)<backPixels*.1,"Fabulous lost foreground depth");
                foreground(Blocks.AIR);reload=mc.reloadResourcePacks();next();
            }
            case 8->{
                if(!reload.isDone()||mc.getOverlay()!=null||!StillworldPostEffect.settled()||age<50)return false;
                reload.join();require(colored("rift-reloaded.png",1)>150,"Resource reload lost the live slit");
                StillworldClient.slashes().removeIf(s->s.id().equals(id));
                mc.options.hideGui=false;mc.player.getInventory().selected=0;
                mc.options.graphicsMode().set(graphics);mc.levelRenderer.allChanged();
                server(p->{wall(p,8,Blocks.AIR);wall(p,0,Blocks.AIR);p.connection.teleport(.5,100,.5,0,12);});next();
            }
            case 9->{if(!serverDone||age<20)return false;done=true;return true;}
            default->throw new AssertionError("Unknown rift check stage");
        }
        return false;
    }
    private static void wall(ServerPlayer p,int z,Block block){for(int x=4;x<=12;x++)for(int y=100;y<=104;y++)p.serverLevel().setBlockAndUpdate(new BlockPos(x,y,z),block.defaultBlockState());}
    private static void foreground(Block block){var level=Minecraft.getInstance().level;for(int x=5;x<=11;x++)for(int y=100;y<=104;y++)level.setBlock(new BlockPos(x,y,6),block.defaultBlockState(),19);}
    private int colored(String name,int channel)throws Exception {
        var mc=Minecraft.getInstance();int count=0;
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(output.resolve(name));
            for(int y=image.getHeight()/5;y<image.getHeight()*4/5;y++)for(int x=image.getWidth()/5;x<image.getWidth()*4/5;x++) {
                int pixel=image.getPixelRGBA(x,y),r=pixel&255,g=(pixel>>>8)&255,b=(pixel>>>16)&255;
                if(channel==0?r>g*1.5&&r>b*1.5&&r>55:g>r*1.2&&g>b*1.5&&g>55)count++;
            }
        }
        Files.writeString(output.resolve(name+".txt"),"coloredPixels="+count+" liveFrames="+RealityOrbRenderer.liveFrames()+"\n");
        return count;
    }
    private void server(Consumer<ServerPlayer> task){var mc=Minecraft.getInstance();var player=mc.player.getUUID();serverDone=false;mc.getSingleplayerServer().execute(()->{try{task.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(player));}catch(Throwable t){failure=t;}finally{serverDone=true;}});}
    private void next(){stage++;age=0;}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
