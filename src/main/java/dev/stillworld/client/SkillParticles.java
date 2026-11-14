package dev.stillworld.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
/** Skill effects keep animating while pre-entry environmental particles remain frozen. */
public final class SkillParticles {
    private static final Set<Particle> OWNED = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean spawning;
    public static boolean accept(Particle particle) {
        if (spawning) OWNED.add(particle);
        return !StillworldClient.active() || OWNED.contains(particle);
    }
    public static boolean freeze(Particle particle) { return StillworldClient.active() && !OWNED.contains(particle); }
    public static void spawn(ParticleOptions type, double x, double y, double z, double vx, double vy, double vz) {
        spawning = true;
        try { Minecraft.getInstance().particleEngine.createParticle(type, x,y,z,vx,vy,vz); }
        finally { spawning = false; }
    }
    private SkillParticles() { }
}
