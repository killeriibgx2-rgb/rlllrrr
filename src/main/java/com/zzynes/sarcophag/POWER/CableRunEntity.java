package com.zzynes.sarcophag.POWER;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;

// Один прогон кабеля: от крепления до крепления. Не тикает, физику сети считает отдельный сервис.
public class CableRunEntity extends Entity implements IEntityAdditionalSpawnData {

    private static final EntityDataAccessor<Byte> DATA_TIER = SynchedEntityData.defineId(CableRunEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_CORE = SynchedEntityData.defineId(CableRunEntity.class, EntityDataSerializers.BYTE);

    private CableRoute route = CableRoute.EMPTY;
    private Anchor start;
    private Anchor end;

    public CableRunEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void configure(CableTier tier, CableCore core, Anchor start, Anchor end, CableRoute route) {
        this.entityData.set(DATA_TIER, (byte) tier.ordinal());
        this.entityData.set(DATA_CORE, (byte) core.ordinal());
        this.start = start;
        this.end = end;
        this.route = route;
        Vec3 center = route.bounds().getCenter();
        this.setPos(center.x, center.y, center.z);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_TIER, (byte) 0);
        this.entityData.define(DATA_CORE, (byte) 0);
    }

    public CableTier tier() {
        return CableTier.byIndex(this.entityData.get(DATA_TIER));
    }

    public CableCore core() {
        return CableCore.byIndex(this.entityData.get(DATA_CORE));
    }

    public CableRoute route() {
        return route;
    }

    public Anchor start() {
        return start;
    }

    public Anchor end() {
        return end;
    }


    // Прогон держится, пока хотя бы одно крепление упирается в твёрдую грань.
    public boolean isSupported() {
        return anchored(start) || anchored(end);
    }

    private boolean anchored(Anchor anchor) {
        if (anchor == null || this.level() == null) {
            return false;
        }
        BlockState state = this.level().getBlockState(anchor.pos());
        return state.isFaceSturdy(this.level(), anchor.pos(), anchor.face());
    }

    public void dropAsItems() {
        if (this.level().isClientSide || route.isEmpty()) {
            return;
        }
        int count = Math.max(1, Math.min(8, (int) Math.round(route.length() / 8.0)));
        ItemStack stack = new ItemStack(itemFor(tier()));
        stack.setCount(count);
        ItemEntity drop = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), stack);
        this.level().addFreshEntity(drop);
    }

    public static Item itemFor(CableTier tier) {
        return switch (tier) {
            case CONTROL -> PowerRegistry.CABLE_CONTROL.get();
            case POWER -> PowerRegistry.CABLE_POWER.get();
            case MAIN -> PowerRegistry.CABLE_MAIN.get();
        };
    }

    // Разрезает прогон в точке и возвращает куски, которые нужно поставить заново.
    public List<CableRoute> cutAt(Vec3 point) {
        return route.cut(point);
    }

    @Override
    protected AABB makeBoundingBox() {
        if (route != null && !route.isEmpty()) {
            return route.bounds();
        }
        return super.makeBoundingBox();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0 * 128.0;
    }

    @Override
    public void tick() {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putByte("Tier", (byte) tier().ordinal());
        tag.putByte("Core", (byte) core().ordinal());
        tag.put("Route", route.save());
        if (start != null) {
            tag.put("Start", start.save());
        }
        if (end != null) {
            tag.put("End", end.save());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(DATA_TIER, tag.getByte("Tier"));
        this.entityData.set(DATA_CORE, tag.getByte("Core"));
        this.route = CableRoute.load(tag.getCompound("Route"));
        this.start = tag.contains("Start") ? Anchor.load(tag.getCompound("Start")) : null;
        this.end = tag.contains("End") ? Anchor.load(tag.getCompound("End")) : null;
        this.setBoundingBox(makeBoundingBox());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        buffer.writeByte(tier().ordinal());
        buffer.writeByte(core().ordinal());
        route.write(buffer);
        boolean hasStart = start != null;
        buffer.writeBoolean(hasStart);
        if (hasStart) {
            start.write(buffer);
        }
        boolean hasEnd = end != null;
        buffer.writeBoolean(hasEnd);
        if (hasEnd) {
            end.write(buffer);
        }
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        this.entityData.set(DATA_TIER, buffer.readByte());
        this.entityData.set(DATA_CORE, buffer.readByte());
        this.route = CableRoute.read(buffer);
        this.start = buffer.readBoolean() ? Anchor.read(buffer) : null;
        this.end = buffer.readBoolean() ? Anchor.read(buffer) : null;
        this.setBoundingBox(makeBoundingBox());
    }

    public List<Vec3> points() {
        return new ArrayList<>(route.points());
    }
}
