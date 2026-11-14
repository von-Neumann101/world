package dev.stillworld.server;
import dev.stillworld.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public final class FogRecovery extends SavedData {
    private record Entry(UUID owner, boolean gray) { }
    private final Map<BlockPos,Entry> entries=new HashMap<>();
    public static FogRecovery get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(FogRecovery::new,FogRecovery::load),"stillworld_mist");
    }
    private static FogRecovery load(CompoundTag tag, HolderLookup.Provider registries) {
        var data=new FogRecovery(); var list=tag.getList("mist",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++) { var entry=list.getCompound(i); if(entry.hasUUID("owner")) data.entries.put(BlockPos.of(entry.getLong("pos")),new Entry(entry.getUUID("owner"),entry.getBoolean("gray"))); }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list=new ListTag();entries.forEach((pos,entry)->{var item=new CompoundTag();item.putLong("pos",pos.asLong());item.putUUID("owner",entry.owner);item.putBoolean("gray",entry.gray);list.add(item);});tag.put("mist",list);return tag;
    }
    public boolean occupied(BlockPos pos) { return entries.containsKey(pos); }
    public void track(BlockPos pos, UUID owner, boolean gray) { entries.put(pos.immutable(),new Entry(owner,gray));setDirty(); }
    public void remove(ServerLevel level, BlockPos pos, UUID owner) {
        var entry=entries.get(pos); if(entry==null || !entry.owner.equals(owner)) return;
        clear(level,pos,entry);entries.remove(pos);setDirty();
    }
    public void finish(ServerLevel level, UUID owner) {
        for(var pos:List.copyOf(entries.keySet())) if(entries.get(pos).owner.equals(owner)) {
            if(level.hasChunkAt(pos)) { clear(level,pos,entries.get(pos));entries.remove(pos);setDirty(); }
        }
    }
    public void recoverChunk(ServerLevel level, ChunkPos chunk) {
        var active=new HashSet<UUID>();for(var session:PhaseManager.sessions()) if(session.level==level) active.add(session.id);
        for(var pos:List.copyOf(entries.keySet())) if((pos.getX()>>4)==chunk.x && (pos.getZ()>>4)==chunk.z && !active.contains(entries.get(pos).owner)) {
            clear(level,pos,entries.get(pos));entries.remove(pos);setDirty();
        }
    }
    private static void clear(ServerLevel level,BlockPos pos,Entry entry) {
        if(level.getBlockState(pos).is(entry.gray?ModContent.GRAY_MIST.get():ModContent.BLACK_MIST.get())) level.removeBlock(pos,false);
    }
}
