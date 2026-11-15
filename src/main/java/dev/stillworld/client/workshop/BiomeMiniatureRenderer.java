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
import net.minecraft.world.level.block.state.BlockState;
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
    private static List<List<Piece>> createScenes() {
        var scenes=new ArrayList<List<Piece>>();
        for(int biome=0;biome<8;biome++) {
            var p=new ArrayList<Piece>();
            Block ground=switch(biome) {case 1,2->Blocks.SANDSTONE;case 4->Blocks.WARPED_NYLIUM;case 5->Blocks.CRIMSON_NYLIUM;case 6,7->Blocks.END_STONE;default->Blocks.MOSS_BLOCK;};
            add(p,ground,0,0,0,6,.35f,6);
            switch(biome) {
                case 0->{
                    add(p,Blocks.STONE,1,.35f,1,4,1,4);add(p,Blocks.STONE,2,1.35f,1.5f,2,1,3);
                    add(p,Blocks.SNOW_BLOCK,2,2.35f,2,2,.5f,2);add(p,Blocks.SNOW_BLOCK,2.5f,2.85f,2.5f,1,.6f,1);
                }
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
                    boolean warped=biome==4;tree(p,warped?Blocks.WARPED_STEM:Blocks.CRIMSON_STEM,warped?Blocks.WARPED_WART_BLOCK:Blocks.NETHER_WART_BLOCK,1,1);
                    tree(p,warped?Blocks.WARPED_STEM:Blocks.CRIMSON_STEM,warped?Blocks.WARPED_WART_BLOCK:Blocks.NETHER_WART_BLOCK,3.5f,3.5f);
                    // Hang beneath the cap with a small gap: the old embedded lamp shared its front/bottom planes.
                    add(p,Blocks.SHROOMLIGHT,1.2f,.5f,.7f,.55f,.55f,.55f);
                }
                case 6->{
                    add(p,Blocks.OBSIDIAN,.6f,.35f,.6f,.8f,2.4f,.8f);add(p,Blocks.OBSIDIAN,4.6f,.35f,4.6f,.8f,1.8f,.8f);
                    add(p,Blocks.BEDROCK,2,.35f,2,2,.2f,2);add(p,Blocks.BLACK_CONCRETE,2.3f,.56f,2.3f,1.4f,.1f,1.4f);
                }
                case 7->{
                    add(p,Blocks.END_STONE,1,.35f,1,4,.4f,4);
                    add(p,Blocks.CHORUS_PLANT,1.5f,.75f,1.5f,.4f,1.8f,.4f);add(p,Blocks.CHORUS_PLANT,1.5f,1.8f,1.5f,1.2f,.4f,.4f);
                    add(p,Blocks.CHORUS_FLOWER,2.3f,2.2f,1.3f,.8f,.8f,.8f);add(p,Blocks.CHORUS_FLOWER,1.3f,2.55f,1.3f,.8f,.8f,.8f);
                    add(p,Blocks.CHORUS_PLANT,4,.75f,4,.4f,1.2f,.4f);add(p,Blocks.CHORUS_FLOWER,3.8f,1.95f,3.8f,.8f,.8f,.8f);
                }
                default->{}
            }
            scenes.add(List.copyOf(p));
        }
        return List.copyOf(scenes);
    }
}
