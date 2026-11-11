package com.zzynes.sarcophag.mixin;

import com.zzynes.sarcophag.DRONE.DroneControlPacket;
import com.zzynes.sarcophag.DRONE.FpvCameraAnchor;
import com.zzynes.sarcophag.DRONE.entity.FpvDroneEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin {

    @Shadow
    @Final
    Entity entity;

    @Redirect(
            method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 sarcophag$useDroneOrPlayerTrackingOrigin(ServerPlayer player) {
        Vec3 anchorPos = FpvCameraAnchor.getAnchorPos(player);
        if (anchorPos != null) {
            Vec3 playerPos = player.position();
            Vec3 targetPos = this.entity.position();
            return anchorPos.distanceToSqr(targetPos) <= playerPos.distanceToSqr(targetPos) ? anchorPos : playerPos;
        }
        return player.position();
    }

    @Redirect(
            method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I")
    )
    private int sarcophag$extendDroneTrackingRange(int effectiveRange, int serverViewRangeBlocks) {
        if (this.entity instanceof FpvDroneEntity) {
            return (int) DroneControlPacket.MAX_RANGE + 64;
        }
        return Math.min(effectiveRange, serverViewRangeBlocks);
    }
}
