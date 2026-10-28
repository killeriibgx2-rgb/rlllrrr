package com.zzynes.flydrone.mixin;

import com.zzynes.flydrone.DRONE.FpvCameraAnchor;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

    @Redirect(
            method = "updatePlayerPos(Lnet/minecraft/server/level/ServerPlayer;)Lnet/minecraft/core/SectionPos;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"
            )
    )
    private SectionPos flyDrone$anchorUpdatePlayerPos(EntityAccess entityAccess) {
        if (entityAccess instanceof ServerPlayer player) {
            return FpvCameraAnchor.getAnchorSectionPos(player);
        }
        return SectionPos.of(entityAccess);
    }

    @Redirect(
            method = "move(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;getBlockX()I"
            )
    )
    private int flyDrone$anchorMoveBlockX(ServerPlayer player) {
        return FpvCameraAnchor.getAnchorBlockX(player);
    }

    @Redirect(
            method = "move(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;getBlockZ()I"
            )
    )
    private int flyDrone$anchorMoveBlockZ(ServerPlayer player) {
        return FpvCameraAnchor.getAnchorBlockZ(player);
    }

    @Redirect(
            method = "move(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"
            )
    )
    private SectionPos flyDrone$anchorMoveSectionPos(EntityAccess entityAccess) {
        if (entityAccess instanceof ServerPlayer player) {
            return FpvCameraAnchor.getAnchorSectionPos(player);
        }
        return SectionPos.of(entityAccess);
    }

    @Inject(
            method = "updatePlayerStatus(Lnet/minecraft/server/level/ServerPlayer;Z)V",
            at = @At("HEAD")
    )
    private void flyDrone$detachBeforeUnregister(ServerPlayer player, boolean track, CallbackInfo ci) {
        if (!track && FpvCameraAnchor.hasActiveAnchor(player)) {
            FpvCameraAnchor.detach(player);
        }
    }
}
