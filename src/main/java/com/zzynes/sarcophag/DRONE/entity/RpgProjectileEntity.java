package com.zzynes.sarcophag.DRONE.entity;

import com.zzynes.sarcophag.DRONE.ModNetwork;
import com.zzynes.sarcophag.DRONE.PayloadExplosion;
import com.zzynes.sarcophag.DRONE.RpgImpactPreviewPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RpgProjectileEntity extends Entity {

    private static final int MAX_TRAIL_POINTS = 240;
    private static final float BLAST_RADIUS = 5.0f;
    private final List<Vec3> trail = new ArrayList<>();
    private UUID controllerUuid;
    private Vec3 lastMotion = Vec3.ZERO;
    private boolean detonated;

    public RpgProjectileEntity(EntityType<? extends RpgProjectileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public RpgProjectileEntity(EntityType<? extends RpgProjectileEntity> type, Level level, UUID controllerUuid) {
        this(type, level);
        this.controllerUuid = controllerUuid;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        if (this.detonated) {
            return;
        }
        Vec3 start = this.position();
        if (!this.level().isClientSide()) {
            recordTrail(start);
        }

        Vec3 movement = this.getDeltaMovement();
        this.lastMotion = movement;
        Vec3 end = start.add(movement);
        BlockHitResult blockHit = this.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        EntityHitResult entityHit = findEntityHit(start, end);

        if (entityHit != null && (blockHit.getType() == HitResult.Type.MISS || start.distanceToSqr(entityHit.getLocation()) < start.distanceToSqr(blockHit.getLocation()))) {
            this.setPos(entityHit.getLocation().x, entityHit.getLocation().y, entityHit.getLocation().z);
            detonate(entityHit.getLocation(), null, entityHit.getEntity());
            return;
        }
        if (blockHit.getType() != HitResult.Type.MISS) {
            this.setPos(blockHit.getLocation().x, blockHit.getLocation().y, blockHit.getLocation().z);
            detonate(blockHit.getLocation(), Vec3.atLowerCornerOf(blockHit.getDirection().getNormal()), null);
            return;
        }

        this.setPos(end.x, end.y, end.z);
        this.setDeltaMovement(movement.x * 0.992, movement.y * 0.992 - 0.035, movement.z * 0.992);
        updateRotation();
        if (this.tickCount >= 240) {
            detonate(this.position(), null, null);
        }
    }

    private EntityHitResult findEntityHit(Vec3 start, Vec3 end) {
        AABB box = this.getBoundingBox().expandTowards(end.subtract(start)).inflate(0.2);
        EntityHitResult closest = null;
        double best = Double.MAX_VALUE;
        for (Entity target : this.level().getEntities(this, box, this::canHit)) {
            AABB bounds = target.getBoundingBox().inflate(0.1);
            var hit = bounds.clip(start, end);
            if (hit.isPresent()) {
                double distance = start.distanceToSqr(hit.get());
                if (distance < best) {
                    best = distance;
                    closest = new EntityHitResult(target, hit.get());
                }
            }
        }
        return closest;
    }

    private boolean canHit(Entity entity) {
        return entity != this && entity.isAlive() && !(entity instanceof FpvDroneEntity) && entity.isPickable();
    }

    private void recordTrail(Vec3 point) {
        if (this.trail.isEmpty() || this.trail.get(this.trail.size() - 1).distanceToSqr(point) > 1.0e-8) {
            if (this.trail.size() < MAX_TRAIL_POINTS) {
                this.trail.add(point);
            } else {
                for (int i = 1; i < this.trail.size() - 1; i += 2) {
                    this.trail.set(i / 2, this.trail.get(i));
                }
                int keep = (this.trail.size() + 1) / 2;
                while (this.trail.size() > keep) {
                    this.trail.remove(this.trail.size() - 1);
                }
                this.trail.add(point);
            }
        }
    }

    private void updateRotation() {
        Vec3 v = this.getDeltaMovement();
        double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
        this.setYRot((float) Math.toDegrees(Math.atan2(-v.x, v.z)));
        this.setXRot((float) Math.toDegrees(Math.atan2(-v.y, horizontal)));
    }

    private void detonate(Vec3 impact, Vec3 surfaceNormal, Entity hitEntity) {
        if (this.detonated || this.level().isClientSide()) {
            return;
        }
        this.detonated = true;
        if (this.trail.isEmpty() || this.trail.get(this.trail.size() - 1).distanceToSqr(impact) > 1.0e-6) {
            recordTrail(impact);
        }
        PayloadExplosion.detonate(this.level(), this, impact, BLAST_RADIUS, surfaceNormal == null ? this.lastMotion : surfaceNormal.scale(-1.0), true);
        sendPreview();
        this.discard();
    }

    private void sendPreview() {
        if (!(this.level() instanceof ServerLevel serverLevel) || this.controllerUuid == null || this.trail.size() < 2) {
            return;
        }
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(this.controllerUuid);
        if (player != null && player.level() == serverLevel) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new RpgImpactPreviewPacket(this.trail));
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Controller")) {
            this.controllerUuid = tag.getUUID("Controller");
        }
        this.detonated = tag.getBoolean("Detonated");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.controllerUuid != null) {
            tag.putUUID("Controller", this.controllerUuid);
        }
        tag.putBoolean("Detonated", this.detonated);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }
}
