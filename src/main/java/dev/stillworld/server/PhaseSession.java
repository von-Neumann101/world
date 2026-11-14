package dev.stillworld.server;
import dev.stillworld.core.DamageLedger;
import dev.stillworld.core.Rank;
import dev.stillworld.snapshot.SnapshotWorld;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class PhaseSession {
    public final UUID id = UUID.randomUUID();
    public final ServerPlayer player;
    public final ServerLevel level;
    public final Rank rank;
    public final Vec3 entry;
    public final long entryTime;
    public final Map<UUID, CompoundTag> actors = new LinkedHashMap<>();
    public final Set<UUID> laterEntities = new HashSet<>();
    public final DamageLedger damage = new DamageLedger();
    private final SnapshotWorld snapshot;
    public boolean finishing;
    public PhaseSession(ServerPlayer player, Rank rank) {
        this.player = player; this.level = player.serverLevel(); this.rank = rank;
        entry = player.position(); entryTime = level.getGameTime(); snapshot = new SnapshotWorld(level);
    }
    public SnapshotWorld snapshot() { return snapshot; }
}
