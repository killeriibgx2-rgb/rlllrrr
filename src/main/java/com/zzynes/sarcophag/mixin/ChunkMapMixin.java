package com.zzynes.sarcophag.mixin;

import com.zzynes.sarcophag.DRONE.FpvCameraAnchor;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// подменяем позицию игрока на позицию дрона, иначе чанки грузятся вокруг тела
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

    @Redirect(
            method = "updatePlayerPos(Lnet/minecraft/server/level/ServerPlayer;)Lnet/minecraft/core/SectionPos;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"
            )
    )
    private SectionPos sarcophag$anchorUpdatePlayerPos(EntityAccess entityAccess) {
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
    private int sarcophag$anchorMoveBlockX(ServerPlayer player) {
        return FpvCameraAnchor.getAnchorBlockX(player);
    }

    @Redirect(
            method = "move(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;getBlockZ()I"
            )
    )
    private int sarcophag$anchorMoveBlockZ(ServerPlayer player) {
        return FpvCameraAnchor.getAnchorBlockZ(player);
    }

    @Redirect(
            method = "move(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"
            )
    )
    private SectionPos sarcophag$anchorMoveSectionPos(EntityAccess entityAccess) {
        if (entityAccess instanceof ServerPlayer player) {
            return FpvCameraAnchor.getAnchorSectionPos(player);
        }
        return SectionPos.of(entityAccess);
    }

    @Inject(
            method = "updatePlayerStatus(Lnet/minecraft/server/level/ServerPlayer;Z)V",
            at = @At("HEAD")
    )
    private void sarcophag$detachBeforeUnregister(ServerPlayer player, boolean track, CallbackInfo ci) {
        if (!track && FpvCameraAnchor.hasActiveAnchor(player)) {
            FpvCameraAnchor.detach(player);
        }
    }
}
