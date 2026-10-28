package com.zzynes.flydrone.mixin;

import com.zzynes.flydrone.DRONE.FpvKillcamManager;
import com.zzynes.flydrone.DRONE.client.BlastParticlesClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Redirect(
            method = "handleExplosion(Lnet/minecraft/network/protocol/game/ClientboundExplodePacket;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Explosion;finalizeExplosion(Z)V"
            )
    )
    private void flyDrone$deferKillcamExplosionFx(Explosion explosion, boolean spawnParticles, ClientboundExplodePacket packet) {
        if (BlastParticlesClient.shouldSuppressVanillaAt(packet.getX(), packet.getY(), packet.getZ())) {
            return;
        }
        if (packet.getToBlow().isEmpty() && FpvKillcamManager.shouldSuppressEarlyExplosion(packet.getX(), packet.getY(), packet.getZ())) {
            return;
        }
        explosion.finalizeExplosion(spawnParticles);
    }
}
