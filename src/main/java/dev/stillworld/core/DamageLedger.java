package dev.stillworld.core;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public final class DamageLedger {
    private final Map<UUID, Double> damage = new HashMap<>();
    public void add(UUID target, float amount) {
        if (target != null && Float.isFinite(amount) && amount > 0) {
            damage.merge(target, (double) amount, Double::sum);
        }
    }
    public float drain(UUID target) {
        Double amount = damage.remove(target);
        return amount == null ? 0 : (float) Math.min(amount * 1.6, Float.MAX_VALUE);
    }
    public float accumulated(UUID target) { return damage.getOrDefault(target, 0.0).floatValue(); }
    public void discard(UUID target) { damage.remove(target); }
}
