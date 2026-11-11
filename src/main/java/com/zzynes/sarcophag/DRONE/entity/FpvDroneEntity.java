package com.zzynes.sarcophag.DRONE.entity;

import com.zzynes.sarcophag.DRONE.DroneControlPacket;
import com.zzynes.sarcophag.DRONE.DroneDisconnectPacket;
import com.zzynes.sarcophag.DRONE.DroneImpactCamPacket;
import com.zzynes.sarcophag.DRONE.PayloadExplosion;
import com.zzynes.sarcophag.DRONE.FpvCameraAnchor;
import com.zzynes.sarcophag.DRONE.ModNetwork;
import com.zzynes.sarcophag.DRONE.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Quaternionf;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class FpvDroneEntity extends PathfinderMob implements GeoEntity {

    private static final TicketType<UUID> DRONE_CHUNK_TICKET =
            TicketType.create("sarcophag_entity", UUID::compareTo);
    private static final int DRONE_TICKET_RADIUS = 2;
    private static final int IMPACT_ANCHOR_HOLD_TICKS = 88;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation FLY_ANIM = RawAnimation.begin().thenLoop("fly");
    private static final RawAnimation STOP_ANIM = RawAnimation.begin().thenLoop("stop");

    private static final EntityDataAccessor<Float> THROTTLE = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> BATTERY_TICKS = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BATTERY_TEMP = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BATTERY_VOLTAGE = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED_KMH = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_FLYING = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DRONE_HP = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PAYLOAD_TYPE = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> QUAT_X = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> QUAT_Y = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> QUAT_Z = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> QUAT_W = SynchedEntityData.defineId(FpvDroneEntity.class, EntityDataSerializers.FLOAT);

    private float pitchRate = 0f;
    private float rollRate = 0f;
    private float yawRate = 0f;
    private final Quaternionf orientation = new Quaternionf();

    private final Quaternionf renderPrevQuat = new Quaternionf();
    private final Quaternionf renderQuat = new Quaternionf();

    private boolean killcamFlying = false;
    @Nullable
    private Quaternionf killcamQuat = null;

    private boolean dead = false;
    private UUID controllerUUID = null;
    private int renderDistance = 10;
    private double prevVelY = 0;
    private double prevHoriz = 0;
    private ChunkPos ticketChunk = null;
    private ServerLevel ticketLevel = null;

    private static final float MAX_THROTTLE = 1.0f;
    private static final float THROTTLE_RAMP_SPEED = 0.9f;
    private static final float ANGULAR_DAMPING = 0.65f;
    private static final float GRAVITY_FORCE = 0.05f;
    private static final float THRUST_FORCE = 0.14f;
    private static final float DRAG = 0.06f;
    private static final float MAX_ANGULAR_RATE = 6.5f;

    public FpvDroneEntity(EntityType<? extends FpvDroneEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.orientation.identity();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(THROTTLE, 0.0f);
        this.entityData.define(BATTERY_TICKS, 4800);
        this.entityData.define(BATTERY_TEMP, 32.0f);
        this.entityData.define(BATTERY_VOLTAGE, 25.2f);
        this.entityData.define(SPEED_KMH, 0.0f);
        this.entityData.define(IS_FLYING, false);
        this.entityData.define(DRONE_HP, 5);
        this.entityData.define(PAYLOAD_TYPE, 0);
        this.entityData.define(QUAT_X, 0f);
        this.entityData.define(QUAT_Y, 0f);
        this.entityData.define(QUAT_Z, 0f);
        this.entityData.define(QUAT_W, 1f);
    }

    @Override
    public void tick() {
        this.prevVelY = this.getDeltaMovement().y;
        this.prevHoriz = Math.sqrt(this.getDeltaMovement().x * this.getDeltaMovement().x + this.getDeltaMovement().z * this.getDeltaMovement().z);

        super.tick();

        if (this.level().isClientSide()) {
            renderPrevQuat.set(renderQuat);
            renderQuat.set(this.entityData.get(QUAT_X), this.entityData.get(QUAT_Y), this.entityData.get(QUAT_Z), this.entityData.get(QUAT_W));
            return;
        }

        ServerLevel sl = (ServerLevel) this.level();

        keepOwnChunkLoaded(sl);

        if (this.controllerUUID != null && sl.getServer() != null) {
            ServerPlayer cp = sl.getServer().getPlayerList().getPlayer(this.controllerUUID);
            if (cp == null || cp.level() != sl) {
                this.forceDisconnect(null);
            } else if (cp.position().distanceTo(this.position()) >= DroneControlPacket.MAX_RANGE) {
                this.forceDisconnect("Пульт отключён — потеря сигнала");
            } else {
                FpvCameraAnchor.attachOrSync(cp, this);
            }
        }

        if (this.isInWater() || this.isInLava()) {
            this.explodeDrone(false);
            return;
        }

        if (this.verticalCollision && this.prevVelY < -0.45) {
            this.explodeDrone(true);
            return;
        }

        if (this.horizontalCollision && this.prevHoriz > 0.2) {
            this.explodeDrone(true);
            return;
        }

        if (checkEntityImpact(sl)) {
            this.explodeDrone(true);
            return;
        }

        updatePowerCutoff();

        float throttle = this.entityData.get(THROTTLE);

        if (this.onGround()) {
            pitchRate = 0f;
            rollRate = 0f;
            yawRate = 0f;
        } else {
            pitchRate *= ANGULAR_DAMPING;
            rollRate *= ANGULAR_DAMPING;
            yawRate *= ANGULAR_DAMPING;

            pitchRate = Mth.clamp(pitchRate, -MAX_ANGULAR_RATE, MAX_ANGULAR_RATE);
            rollRate = Mth.clamp(rollRate, -MAX_ANGULAR_RATE, MAX_ANGULAR_RATE);
            yawRate = Mth.clamp(yawRate, -MAX_ANGULAR_RATE, MAX_ANGULAR_RATE);

            Quaternionf deltaRotation = new Quaternionf()
                    .rotateX(pitchRate * 0.01745f)
                    .rotateZ(rollRate * 0.01745f)
                    .rotateY(yawRate * 0.01745f);
            orientation.mul(deltaRotation);
            orientation.normalize();
        }

        this.entityData.set(QUAT_X, orientation.x);
        this.entityData.set(QUAT_Y, orientation.y);
        this.entityData.set(QUAT_Z, orientation.z);
        this.entityData.set(QUAT_W, orientation.w);

        Vec3 forward = rotateVec(orientation, 0f, 0f, 1f);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        this.setYRot(targetYaw);
        this.yBodyRot = targetYaw;
        this.yHeadRot = targetYaw;
        this.setXRot((float) Math.toDegrees(-Math.asin(Mth.clamp(forward.y, -1.0f, 1.0f))));

        Vec3 up = rotateVec(orientation, 0f, 1f, 0f);
        Vec3 motion = this.getDeltaMovement();

        double ax = up.x * throttle * THRUST_FORCE;
        double ay = up.y * throttle * THRUST_FORCE - GRAVITY_FORCE;
        double az = up.z * throttle * THRUST_FORCE;

        this.setDeltaMovement(
                (motion.x + ax) * (1.0 - DRAG),
                (motion.y + ay) * (1.0 - DRAG),
                (motion.z + az) * (1.0 - DRAG)
        );

        if (throttle > 0.01f) {
            int ticks = this.entityData.get(BATTERY_TICKS);
            ticks -= Math.max(1, (int) (throttle * 3));
            if (ticks < 0) ticks = 0;
            this.entityData.set(BATTERY_TICKS, ticks);

            float battTemp = this.entityData.get(BATTERY_TEMP);
            battTemp += 0.019f * throttle * throttle;
            battTemp = Math.min(battTemp, 120.0f);
            this.entityData.set(BATTERY_TEMP, battTemp);
        } else {
            float battTemp = this.entityData.get(BATTERY_TEMP);
            battTemp = Math.max(32.0f, battTemp - 0.038f);
            this.entityData.set(BATTERY_TEMP, battTemp);
        }

        updatePowerCutoff();
        updateTelemetry(throttle);

        if (this.entityData.get(BATTERY_TEMP) >= 100.0f) {
            this.forceDisconnect("Пульт отключён — перегрев аккумулятора");
        } else if (this.entityData.get(BATTERY_TICKS) <= 0) {
            this.forceDisconnect("Пульт отключён — аккумулятор разряжен");
        }

        if (this.controllerUUID != null && sl.getServer() != null) {
            ServerPlayer cp = sl.getServer().getPlayerList().getPlayer(this.controllerUUID);
            if (cp == null || cp.level() != sl) {
                this.forceDisconnect(null);
            } else if (cp.position().distanceTo(this.position()) >= DroneControlPacket.MAX_RANGE) {
                this.forceDisconnect("Пульт отключён — потеря сигнала");
            }
        }

        float effectiveThrottle = this.entityData.get(THROTTLE);
        this.entityData.set(IS_FLYING, effectiveThrottle > 0.01f && this.entityData.get(BATTERY_TICKS) > 0);
    }

    private boolean checkEntityImpact(ServerLevel sl) {
        double speed = Math.max(this.getDeltaMovement().length(), Math.hypot(this.prevHoriz, this.prevVelY));
        if (speed <= 0.16) {
            return false;
        }
        AABB sweepBox = this.getBoundingBox().expandTowards(this.getDeltaMovement()).inflate(0.28);
        List<Entity> hits = sl.getEntities(this, sweepBox, this::canDetonateOnEntity);
        if (hits.isEmpty()) {
            return false;
        }
        Entity closest = null;
        double bestDistSqr = Double.MAX_VALUE;
        Vec3 pos = this.position();
        for (Entity candidate : hits) {
            double d = candidate.getBoundingBox().distanceToSqr(pos);
            if (d < bestDistSqr) {
                bestDistSqr = d;
                closest = candidate;
            }
        }
        if (closest != null) {
            AABB targetBox = closest.getBoundingBox();
            double cx = Mth.clamp(pos.x, targetBox.minX, targetBox.maxX);
            double cy = Mth.clamp(pos.y, targetBox.minY, targetBox.maxY);
            double cz = Mth.clamp(pos.z, targetBox.minZ, targetBox.maxZ);
            this.setPos(cx, cy, cz);
        }
        return true;
    }

    private boolean canDetonateOnEntity(Entity target) {
        if (target == this || !target.isAlive() || target.isSpectator() || target instanceof FpvDroneEntity) {
            return false;
        }
        if (this.tickCount < 25 && this.controllerUUID != null && this.controllerUUID.equals(target.getUUID())) {
            return false;
        }
        return target instanceof LivingEntity || target.isPickable();
    }

    private void updatePowerCutoff() {
        if (this.entityData.get(BATTERY_TICKS) <= 0 || this.entityData.get(BATTERY_TEMP) >= 100.0f) {
            if (this.entityData.get(THROTTLE) != 0.0f) {
                this.entityData.set(THROTTLE, 0.0f);
            }
        }
    }

    private void updateTelemetry(float appliedThrottle) {
        float soc = Mth.clamp(this.entityData.get(BATTERY_TICKS) / 4800.0f, 0.0f, 1.0f);
        float ocv;
        if (soc >= 0.85f) {
            ocv = 24.4f + ((soc - 0.85f) / 0.15f) * 0.8f;
        } else if (soc >= 0.20f) {
            ocv = 22.2f + ((soc - 0.20f) / 0.65f) * 2.2f;
        } else {
            ocv = 19.2f + (soc / 0.20f) * 3.0f;
        }

        float effThrottle = Mth.clamp(appliedThrottle, 0.0f, 1.0f);
        float battTemp = this.entityData.get(BATTERY_TEMP);
        float amps = (effThrottle > 0.01f ? 1.6f : 0.4f) + (float) Math.pow(effThrottle, 1.35) * 64.0f;
        float rInt = 0.026f * (1.0f + 0.85f * (1.0f - soc) * (1.0f - soc));
        if (battTemp > 50.0f) {
            rInt *= (1.0f + 0.018f * (battTemp - 50.0f));
        }
        float thermalDrop = battTemp > 65.0f ? ((battTemp - 65.0f) / 35.0f) * 1.35f : 0.0f;
        float targetV = Mth.clamp(ocv - amps * rInt - thermalDrop, 17.5f, 25.2f);
        float currentV = this.entityData.get(BATTERY_VOLTAGE);
        this.entityData.set(BATTERY_VOLTAGE, Mth.lerp(0.22f, currentV, targetV));

        float rawKmh = (float) (this.getDeltaMovement().length() * 72.0);
        float prevKmh = this.entityData.get(SPEED_KMH);
        float nextKmh = (rawKmh < 0.3f && effThrottle <= 0.01f) ? 0.0f : Mth.lerp(0.35f, prevKmh, rawKmh);
        this.entityData.set(SPEED_KMH, nextKmh);
    }

    public boolean canAcceptControlFrom(ServerPlayer player) {
        return player != null
                && this.isAlive()
                && !this.dead
                && player.level() == this.level()
                && this.entityData.get(BATTERY_TICKS) > 0
                && this.entityData.get(BATTERY_TEMP) < 100.0f;
    }

    @Override
    public void checkDespawn() { }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) { return false; }

    @Override
    public boolean requiresCustomPersistence() { return true; }

    private void keepOwnChunkLoaded(ServerLevel sl) {
        if (this.dead || !this.isAlive()) return;
        ChunkPos current = new ChunkPos(Mth.floor(this.getX()) >> 4, Mth.floor(this.getZ()) >> 4);

        if (this.ticketLevel == sl && current.equals(this.ticketChunk)) {
            return;
        }

        if (this.ticketLevel != null && this.ticketChunk != null) {
            this.ticketLevel.getChunkSource().removeRegionTicket(DRONE_CHUNK_TICKET, this.ticketChunk, DRONE_TICKET_RADIUS, this.getUUID(), true);
        }

        sl.getChunkSource().addRegionTicket(DRONE_CHUNK_TICKET, current, DRONE_TICKET_RADIUS, this.getUUID(), true);
        this.ticketLevel = sl;
        this.ticketChunk = current;
    }

    public void pauseFpvSession() {
        UUID id = this.controllerUUID;
        this.controllerUUID = null;
        if (id != null && this.level().getServer() != null) {
            ServerPlayer cp = this.level().getServer().getPlayerList().getPlayer(id);
            if (cp != null) {
                FpvCameraAnchor.detach(cp);
            }
        }
    }

    private void stopChunkLoading() {
        if (this.ticketLevel != null && this.ticketChunk != null) {
            this.ticketLevel.getChunkSource().removeRegionTicket(DRONE_CHUNK_TICKET, this.ticketChunk, DRONE_TICKET_RADIUS, this.getUUID(), true);
        }
        this.ticketLevel = null;
        this.ticketChunk = null;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        FpvCameraAnchor.detachByDrone(this);
        stopChunkLoading();
        super.remove(reason);
    }

    public void setController(UUID uuid) {
        this.controllerUUID = uuid;
    }

    public void setRenderDistance(int rd) {
        this.renderDistance = Math.max(2, Math.min(32, rd));
    }

    public void forceDisconnect() {
        forceDisconnect(null);
    }

    public void forceDisconnect(@Nullable String reasonMessage) {
        if (this.controllerUUID == null) return;
        UUID id = this.controllerUUID;
        this.controllerUUID = null;
        this.entityData.set(THROTTLE, 0.0f);
        if (this.level().getServer() != null) {
            ServerPlayer cp = this.level().getServer().getPlayerList().getPlayer(id);
            if (cp != null) {
                FpvCameraAnchor.detach(cp);
                replaceBoundConnectedRemotes(cp);
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> cp), new DroneDisconnectPacket(this.getUUID()));
                if (reasonMessage != null && !reasonMessage.isEmpty()) {
                    cp.displayClientMessage(Component.literal(ChatFormatting.RED + reasonMessage), true);
                }
            }
        }
    }

    private void replaceBoundConnectedRemotes(ServerPlayer cp) {
        boolean changed = false;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack st = cp.getItemInHand(hand);
            if (isBoundConnectedRemote(st)) {
                cp.setItemInHand(hand, new ItemStack(ModItems.REMOTE_DISCONNECTED.get()));
                changed = true;
            }
        }
        Inventory inv = cp.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            if (isBoundConnectedRemote(inv.items.get(i))) {
                inv.items.set(i, new ItemStack(ModItems.REMOTE_DISCONNECTED.get()));
                changed = true;
            }
        }
        for (int i = 0; i < inv.offhand.size(); i++) {
            if (isBoundConnectedRemote(inv.offhand.get(i))) {
                inv.offhand.set(i, new ItemStack(ModItems.REMOTE_DISCONNECTED.get()));
                changed = true;
            }
        }
        if (changed) {
            cp.inventoryMenu.broadcastChanges();
        }
    }

    private boolean isBoundConnectedRemote(ItemStack st) {
        return st.getItem() == ModItems.REMOTE_CONNECTED.get()
                && st.hasTag()
                && st.getTag() != null
                && st.getTag().hasUUID("DroneUUID")
                && this.getUUID().equals(st.getTag().getUUID("DroneUUID"));
    }

    public void explodeDrone() {
        explodeDrone(false);
    }

    private void explodeDrone(boolean impact) {
        if (this.dead || this.level().isClientSide()) return;
        this.dead = true;

        ServerPlayer activePilot = null;
        if (this.controllerUUID != null && this.level().getServer() != null) {
            ServerPlayer cp = this.level().getServer().getPlayerList().getPlayer(this.controllerUUID);
            if (cp != null && cp.level() == this.level()) {
                activePilot = cp;
            }
        }

        if (activePilot != null) {
            this.controllerUUID = null;
            this.entityData.set(THROTTLE, 0.0f);
            Vec3 impactPos = this.position();
            FpvCameraAnchor.holdAtImpact(activePilot, impactPos, IMPACT_ANCHOR_HOLD_TICKS);
            ServerPlayer targetPilot = activePilot;
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> targetPilot),
                    new DroneImpactCamPacket(this.getUUID(), this.getId(), impactPos.x, impactPos.y, impactPos.z)
            );
            replaceBoundConnectedRemotes(activePilot);
        } else {
            this.forceDisconnect("Пульт отключён — дрон уничтожен");
        }

        int payload = this.entityData.get(PAYLOAD_TYPE);
        Vec3 movement = this.getDeltaMovement();
        if (payload == 2) {
            this.entityData.set(PAYLOAD_TYPE, 0);
            PayloadExplosion.detonate(this.level(), this, this.position(), 8.0f, movement, impact);
        } else {
            this.level().explode(this, null, null, this.getX(), this.getY(), this.getZ(), 4.0F, false, Level.ExplosionInteraction.NONE);
        }
        this.discard();
    }

    public void releaseRpgProjectile(ServerPlayer player) {
        if (this.level().isClientSide() || this.dead || this.entityData.get(PAYLOAD_TYPE) != 1 || player.level() != this.level()) {
            return;
        }
        Vec3 forward = rotateVec(this.orientation, 0.0f, 0.0f, 1.0f).normalize();
        Vec3 spawn = this.position().add(0.0, -0.18, 0.0).add(forward.scale(0.34));
        RpgProjectileEntity projectile = new RpgProjectileEntity(com.zzynes.sarcophag.DRONE.init.ModEntityTypes.RPG_PROJECTILE.get(), this.level(), player.getUUID());
        projectile.setPos(spawn.x, spawn.y, spawn.z);
        Vec3 inherited = this.getDeltaMovement().add(0.0, -0.018, 0.0);
        projectile.setDeltaMovement(inherited);
        projectile.setYRot(this.getYRot());
        projectile.setXRot(this.getXRot());
        this.level().addFreshEntity(projectile);
        this.entityData.set(PAYLOAD_TYPE, 0);
        this.level().broadcastEntityEvent(this, (byte) 9);
    }

    public void selfDestruct(ServerPlayer player) {
        if (this.dead || this.level().isClientSide() || !player.getUUID().equals(this.controllerUUID)) {
            return;
        }
        explodeDrone(false);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        int nextPayload = held.getItem() == ModItems.RPG_SHELL.get() ? 1 : held.getItem() == ModItems.CI4_CHARGE.get() ? 2 : -1;
        if (nextPayload < 0) {
            return super.mobInteract(player, hand);
        }
        if (player.distanceToSqr(this) > 36.0 || this.dead) {
            return InteractionResult.FAIL;
        }
        if (!this.level().isClientSide()) {
            int previous = this.entityData.get(PAYLOAD_TYPE);
            if (previous != nextPayload) {
                ItemStack oldStack = previous == 1 ? new ItemStack(ModItems.RPG_SHELL.get()) : previous == 2 ? new ItemStack(ModItems.CI4_CHARGE.get()) : ItemStack.EMPTY;
                if (!oldStack.isEmpty() && !player.getInventory().add(oldStack)) {
                    player.drop(oldStack, false);
                }
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                this.entityData.set(PAYLOAD_TYPE, nextPayload);
                player.displayClientMessage(Component.literal(nextPayload == 1 ? "Снаряд РПГ установлен" : "С4 установлен"), true);
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide());
    }

    public int getPayloadType() {
        return this.entityData.get(PAYLOAD_TYPE);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide() || this.dead) return false;
        if (this.invulnerableTime > 0) return false;
        this.invulnerableTime = 10;
        int hp = this.entityData.get(DRONE_HP) - Math.max(1, (int) Math.ceil(amount));
        if (hp <= 0) {
            this.entityData.set(DRONE_HP, 0);
            this.explodeDrone(false);
        } else {
            this.entityData.set(DRONE_HP, hp);
        }
        return true;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean canBeCollidedWith() { return true; }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public void push(Entity entity) { }

    private Vec3 rotateVec(Quaternionf q, float x, float y, float z) {
        float qx = q.x, qy = q.y, qz = q.z, qw = q.w;
        float tx = 2 * (qy * z - qz * y);
        float ty = 2 * (qz * x - qx * z);
        float tz = 2 * (qx * y - qy * x);
        float rx = x + qw * tx + (qy * tz - qz * ty);
        float ry = y + qw * ty + (qz * tx - qx * tz);
        float rz = z + qw * tz + (qx * ty - qy * tx);
        return new Vec3(rx, ry, rz);
    }

    public Quaternionf getInterpolatedOrientation(float partialTick) {
        if (!this.level().isClientSide()) return this.orientation;
        return new Quaternionf(renderPrevQuat).slerp(renderQuat, partialTick);
    }

    public Quaternionf getRenderOrientation(float partialTick) {
        if (this.killcamQuat != null) {
            return new Quaternionf(this.killcamQuat);
        }
        return getInterpolatedOrientation(partialTick);
    }

    public void setKillcamRenderState(boolean flying, @Nullable Quaternionf quat) {
        this.killcamFlying = flying;
        this.killcamQuat = quat != null ? new Quaternionf(quat) : null;
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isControlledByLocalInstance()) {
            this.move(MoverType.SELF, this.getDeltaMovement());
        }
    }

    public void setThrottle(float value) {
        if (this.entityData.get(BATTERY_TICKS) <= 0 || this.entityData.get(BATTERY_TEMP) >= 100.0f) {
            this.entityData.set(THROTTLE, 0.0f);
            return;
        }
        float current = this.entityData.get(THROTTLE);
        float target = Mth.clamp(value, 0f, MAX_THROTTLE);
        float newThrottle = Mth.lerp(THROTTLE_RAMP_SPEED, current, target);
        this.entityData.set(THROTTLE, newThrottle);
    }

    public void addPitchRate(float rate) { this.pitchRate += rate; }
    public void addRollRate(float rate) { this.rollRate += rate; }
    public void addYawRate(float rate) { this.yawRate += rate; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "drone_controller", 0, this::animController));
    }

    private <E extends FpvDroneEntity> PlayState animController(AnimationState<E> state) {
        if (this.killcamFlying || this.isFlying() || (!this.onGround() && this.getBatteryTicks() > 0) || this.entityData.get(THROTTLE) > 0.01f) {
            return state.setAndContinue(FLY_ANIM);
        }
        return state.setAndContinue(STOP_ANIM);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(BATTERY_TICKS, tag.getInt("BatteryTicks"));
        this.entityData.set(BATTERY_TEMP, tag.getFloat("BatteryTemp"));
        if (tag.contains("BatteryVoltage")) {
            this.entityData.set(BATTERY_VOLTAGE, tag.getFloat("BatteryVoltage"));
        }
        this.pitchRate = tag.getFloat("PitchRate");
        this.rollRate = tag.getFloat("RollRate");
        this.yawRate = tag.getFloat("YawRate");
        int hp = tag.getInt("DroneHp");
        this.entityData.set(DRONE_HP, hp <= 0 ? 5 : hp);
        this.entityData.set(PAYLOAD_TYPE, Math.max(0, Math.min(2, tag.getInt("PayloadType"))));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BatteryTicks", this.entityData.get(BATTERY_TICKS));
        tag.putFloat("BatteryTemp", this.entityData.get(BATTERY_TEMP));
        tag.putFloat("BatteryVoltage", this.entityData.get(BATTERY_VOLTAGE));
        tag.putFloat("PitchRate", this.pitchRate);
        tag.putFloat("RollRate", this.rollRate);
        tag.putFloat("YawRate", this.yawRate);
        tag.putInt("DroneHp", this.entityData.get(DRONE_HP));
        tag.putInt("PayloadType", this.entityData.get(PAYLOAD_TYPE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public int getDroneHp() { return Math.max(0, this.entityData.get(DRONE_HP)); }
    public float getThrottle() { return this.entityData.get(THROTTLE); }
    public int getBatteryTicks() { return this.entityData.get(BATTERY_TICKS); }
    public float getBatteryTemp() { return this.entityData.get(BATTERY_TEMP); }
    public float getBatteryVoltage() { return this.entityData.get(BATTERY_VOLTAGE); }
    public float getSpeedKmh() { return this.entityData.get(SPEED_KMH); }
    public boolean isFlying() { return this.entityData.get(IS_FLYING); }
    public Quaternionf getOrientation() { return this.orientation; }
    public UUID getControllerUUID() { return this.controllerUUID; }
}
