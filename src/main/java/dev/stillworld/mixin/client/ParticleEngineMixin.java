package dev.stillworld.mixin.client;
import dev.stillworld.client.SkillParticles;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "tickParticle", at = @At("HEAD"), cancellable = true)
    private void stillworld$freezeParticle(Particle particle, CallbackInfo ci) {
        if (SkillParticles.freeze(particle)) ci.cancel();
    }
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void stillworld$noNewAmbientParticles(Particle particle, CallbackInfo ci) {
        if (!SkillParticles.accept(particle)) ci.cancel();
    }
}
