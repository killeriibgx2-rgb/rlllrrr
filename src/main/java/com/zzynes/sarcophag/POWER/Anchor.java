package com.zzynes.sarcophag.POWER;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

// Точка крепления: блок, грань и место на грани с шагом 1/16.
// Ставится туда, куда ткнули, а не в центр.
public final class Anchor {

    public static final double SURFACE = 0.0;
    private static final float SNAP = 16f;

    private final BlockPos pos;
    private final Direction face;
    private final float u;
    private final float v;

    public Anchor(BlockPos pos, Direction face, float u, float v) {
        this.pos = pos.immutable();
        this.face = face;
        this.u = Math.round(u * SNAP) / SNAP;
        this.v = Math.round(v * SNAP) / SNAP;
    }

    public static Anchor fromHit(BlockHitResult hit) {
        Vec3 rel = hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos()));
        Direction face = hit.getDirection();
        float u;
        float v;
        switch (face.getAxis()) {
            case X -> {
                u = (float) rel.z;
                v = (float) rel.y;
            }
            case Y -> {
                u = (float) rel.x;
                v = (float) rel.z;
            }
            default -> {
                u = (float) rel.x;
                v = (float) rel.y;
            }
        }
        return new Anchor(hit.getBlockPos(), face, u, v);
    }

    public BlockPos pos() {
        return pos;
    }

    public Direction face() {
        return face;
    }

    public Vec3 point() {
        Vec3 center = Vec3.atLowerCornerOf(pos).add(0.5, 0.5, 0.5);
        Direction.Axis axis = face.getAxis();
        Vec3 tangentA = axis == Direction.Axis.X ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
        Vec3 tangentB = axis == Direction.Axis.Y ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        return center
                .add(tangentA.scale(u - 0.5))
                .add(tangentB.scale(v - 0.5))
                .add(normal.scale(0.5 + SURFACE));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Pos", pos.asLong());
        tag.putByte("Face", (byte) face.get3DDataValue());
        tag.putFloat("U", u);
        tag.putFloat("V", v);
        return tag;
    }

    public static Anchor load(CompoundTag tag) {
        return new Anchor(
                BlockPos.of(tag.getLong("Pos")),
                Direction.from3DDataValue(tag.getByte("Face")),
                tag.contains("U") ? tag.getFloat("U") : 0.5f,
                tag.contains("V") ? tag.getFloat("V") : 0.5f);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeByte(face.get3DDataValue());
        buf.writeFloat(u);
        buf.writeFloat(v);
    }

    public static Anchor read(FriendlyByteBuf buf) {
        return new Anchor(buf.readBlockPos(), Direction.from3DDataValue(buf.readByte()), buf.readFloat(), buf.readFloat());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Anchor anchor)) {
            return false;
        }
        return pos.equals(anchor.pos) && face == anchor.face && Float.compare(u, anchor.u) == 0 && Float.compare(v, anchor.v) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pos, face, u, v);
    }
}
