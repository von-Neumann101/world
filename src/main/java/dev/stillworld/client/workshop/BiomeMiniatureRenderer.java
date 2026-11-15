package dev.stillworld.client.workshop;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.stillworld.workshop.AuxiliaryBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.ArrayList;
import java.util.List;

/** Small, fixed terrain exhibits: no extra worlds, cameras, framebuffers or live chunk work. */
public final class BiomeMiniatureRenderer implements BlockEntityRenderer<AuxiliaryBlockEntity> {
    private record Piece(BlockState state,float x,float y,float z,float sx,float sy,float sz) {}
    private static final List<List<Piece>> SCENES=createScenes();
    public BiomeMiniatureRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(AuxiliaryBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).expandTowards(0,.5,0);
    }
    @Override public void render(AuxiliaryBlockEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        int biome=be.biomeIndex();if(biome<0||biome>=SCENES.size())return;
        var renderer=Minecraft.getInstance().getBlockRenderer();
        pose.pushPose();pose.translate(.125,.92,.125);pose.scale(.125f,.125f,.125f);
        for(var piece:SCENES.get(biome)) {
            pose.pushPose();pose.translate(piece.x,piece.y,piece.z);pose.scale(piece.sx,piece.sy,piece.sz);
            renderer.renderSingleBlock(piece.state,pose,buffers,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);pose.popPose();
        }
        if(biome==2)OceanMiniatureSurface.render(pose,buffers);
        pose.popPose();
    }
    private static void add(List<Piece> p,Block block,float x,float y,float z,float sx,float sy,float sz) {p.add(new Piece(block.defaultBlockState(),x,y,z,sx,sy,sz));}
    private static void tree(List<Piece> p,Block log,Block leaves,float x,float z) {
        add(p,log,x+.3f,.25f,z+.3f,.4f,1.45f,.4f);add(p,leaves,x-.3f,1.1f,z-.3f,1.6f,.8f,1.6f);add(p,leaves,x,1.9f,z,1,.5f,1);
    }
    private static void mountain(List<Piece> p) {
        // Three unequal summits form a diagonal ridge and saddles instead of concentric square terraces.
        for(int x=0;x<12;x++)for(int z=0;z<12;z++) {
            double ridge=Math.max(7.9-1.7*Math.abs(x-6)-1.15*Math.abs(z-7),
                    Math.max(6.4-1.4*Math.abs(x-3)-1.6*Math.abs(z-4),5.8-1.8*Math.abs(x-8)-1.4*Math.abs(z-4)));
            int height=Math.max(0,(int)Math.round(ridge));if(height==0)continue;
            float top=.35f+height*.4f;
            float snow=height>=5?.22f:height==4&&(x+z)%3!=0?.12f:0;
            Block rock=height==1?Blocks.MOSS_BLOCK:(x+2*z)%4==0?Blocks.ANDESITE:Blocks.STONE;
            add(p,rock,x*.5f,.35f,z*.5f,.5f,top-.35f-snow,.5f);
            if(snow>0)add(p,Blocks.SNOW_BLOCK,x*.5f,top-snow,z*.5f,.5f,snow,.5f);
        }
    }
    private static void fungus(List<Piece> p,boolean warped,float x,float z,boolean lit) {
        Block stem=warped?Blocks.WARPED_STEM:Blocks.CRIMSON_STEM;
        Block cap=warped?Blocks.WARPED_WART_BLOCK:Blocks.NETHER_WART_BLOCK;
        add(p,stem,x+.25f,.35f,z+.25f,.5f,.9f,.5f);
        // The lamp replaces one cap cell. Adjacent cells meet at their edges, with no overlapping face area.
        for(int dx=0;dx<3;dx++)for(int dz=0;dz<3;dz++)
            add(p,lit&&dx==1&&dz==0?Blocks.SHROOMLIGHT:cap,x-.25f+dx*.5f,1.25f,z-.25f+dz*.5f,.5f,.5f,.5f);
        add(p,cap,x,1.75f,z,1,.5f,1);
    }
    private static void chorus(List<Piece> p,float x,float z,boolean branched) {
        var stems=new ArrayList<BlockPos>(List.of(new BlockPos(0,0,0),new BlockPos(0,1,0),new BlockPos(0,2,0)));
        var flowers=new ArrayList<BlockPos>();
        if(branched) {
            stems.addAll(List.of(new BlockPos(0,3,0),new BlockPos(1,2,0),new BlockPos(2,2,0),new BlockPos(2,3,0)));
            flowers.addAll(List.of(new BlockPos(0,4,0),new BlockPos(2,4,0)));
        } else flowers.add(new BlockPos(0,3,0));
        for(var cell:stems) {
            var state=Blocks.CHORUS_PLANT.defaultBlockState();
            for(var direction:Direction.values()) {
                var neighbor=cell.relative(direction);
                boolean connected=stems.contains(neighbor)||flowers.contains(neighbor)||(direction==Direction.DOWN&&cell.getY()==0);
                state=state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction),connected);
            }
            p.add(new Piece(state,x+cell.getX()*.5f,.75f+cell.getY()*.5f,z+cell.getZ()*.5f,.5f,.5f,.5f));
        }
        for(var cell:flowers)add(p,Blocks.CHORUS_FLOWER,x+cell.getX()*.5f,.75f+cell.getY()*.5f,z+cell.getZ()*.5f,.5f,.5f,.5f);
    }
    private static List<List<Piece>> createScenes() {
        var scenes=new ArrayList<List<Piece>>();
        for(int biome=0;biome<8;biome++) {
            var p=new ArrayList<Piece>();
            Block ground=switch(biome) {case 1,2->Blocks.SANDSTONE;case 4->Blocks.WARPED_NYLIUM;case 5->Blocks.CRIMSON_NYLIUM;case 6,7->Blocks.END_STONE;default->Blocks.MOSS_BLOCK;};
            add(p,ground,0,0,0,6,.35f,6);
            switch(biome) {
                case 0->mountain(p);
                case 1->{
                    add(p,Blocks.SAND,0,.35f,0,6,.2f,6);add(p,Blocks.SAND,3,.55f,2,3,.5f,3);
                    add(p,Blocks.CACTUS,1,.55f,1,.7f,1.5f,.7f);add(p,Blocks.DEAD_BUSH,4,1.05f,3,1,1,1);
                }
                case 2->{
                    // Shelves descend toward the front; small reef forms sit below the animated water surface.
                    for(int x=0;x<3;x++)for(int z=0;z<4;z++)
                        add(p,Blocks.SAND,x*2,.35f,z*1.5f,2,.12f+z*.10f+(x==2?.08f:0),1.5f);
                    add(p,Blocks.ANDESITE,.3f,.7f,4.3f,1.1f,.42f,.9f);
                    add(p,Blocks.TUBE_CORAL_BLOCK,.8f,.57f,1.6f,.55f,.45f,.65f);
                    add(p,Blocks.BRAIN_CORAL_BLOCK,3.7f,.67f,2.5f,.7f,.35f,.6f);
                    add(p,Blocks.FIRE_CORAL_BLOCK,4.5f,.85f,4.2f,.5f,.4f,.55f);
                    add(p,Blocks.BUBBLE_CORAL_FAN,3.65f,1.03f,2.45f,.8f,.55f,.8f);
                    add(p,Blocks.HORN_CORAL_FAN,.7f,1.03f,1.55f,.75f,.6f,.75f);
                    add(p,Blocks.SEAGRASS,2,.58f,1.4f,.7f,.75f,.7f);
                    add(p,Blocks.SEAGRASS,4.7f,.55f,.5f,.6f,.85f,.6f);
                    add(p,Blocks.KELP_PLANT,2.2f,.8f,4.5f,.55f,.85f,.55f);
                    add(p,Blocks.KELP_PLANT,2.9f,.8f,4.8f,.45f,.65f,.45f);
                }
                case 3->{tree(p,Blocks.OAK_LOG,Blocks.OAK_LEAVES,1,1);tree(p,Blocks.BIRCH_LOG,Blocks.BIRCH_LEAVES,3.5f,3);}
                case 4,5->{
                    fungus(p,biome==4,1,1,true);fungus(p,biome==4,3.5f,3.5f,false);
                }
                case 6->{
                    add(p,Blocks.OBSIDIAN,.6f,.35f,.6f,.8f,2.4f,.8f);add(p,Blocks.OBSIDIAN,4.6f,.35f,4.6f,.8f,1.8f,.8f);
                    add(p,Blocks.BEDROCK,2,.35f,2,2,.2f,2);add(p,Blocks.BLACK_CONCRETE,2.3f,.56f,2.3f,1.4f,.1f,1.4f);
                }
                case 7->{
                    add(p,Blocks.END_STONE,1,.35f,1,4,.4f,4);
                    chorus(p,1.5f,1.5f,true);chorus(p,4,4,false);
                }
                default->{}
            }
            scenes.add(List.copyOf(p));
        }
        return List.copyOf(scenes);
    }
}
