package dev.stillworld.snapshot;
import java.util.function.Supplier;
public final class SnapshotReadContext {
    private static final ThreadLocal<SnapshotWorld> CURRENT = new ThreadLocal<>();
    public static SnapshotWorld current() { return CURRENT.get(); }
    public static <T> T with(SnapshotWorld world, Supplier<T> action) {
        var previous = CURRENT.get(); CURRENT.set(world);
        try { return action.get(); } finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
    private SnapshotReadContext() { }
}
