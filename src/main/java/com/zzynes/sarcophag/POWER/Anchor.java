package com.zzynes.sarcophag.POWER;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

// Точка крепления: блок и грань. Кабель цепляется за центр клетки грани,
// поэтому вся геометрия остаётся блочной.
public final class Anchor {

    public static final double SURFACE = 0.02;
    private static final float CELL = 0.5f;

    private final BlockPos pos;
    private final Direction face;

    public Anchor(BlockPos pos, Direction face) {
        this.pos = pos.immutable();
        this.face = face;
    }

    public static Anchor fromHit(BlockHitResult hit) {
        return new Anchor(hit.getBlockPos(), hit.getDirection());
    }

    public static Direction.Axis tangentA(Direction.Axis planeAxis) {
        return planeAxis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    public static Direction.Axis tangentB(Direction.Axis planeAxis) {
        return planeAxis == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
    }

    public BlockPos pos() {
        return pos;
    }

    public Direction face() {
        return face;
    }

    public Vec3 point() {
        Vec3 center = Vec3.atLowerCornerOf(pos).add(0.5, 0.5, 0.5);
        Vec3 tangentA = axisVector(tangentA(face.getAxis()));
        Vec3 tangentB = axisVector(tangentB(face.getAxis()));
        Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        return center
                .add(tangentA.scale(CELL - 0.5))
                .add(tangentB.scale(CELL - 0.5))
                .add(normal.scale(0.5 + SURFACE));
    }

    private static Vec3 axisVector(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vec3(1, 0, 0);
            case Y -> new Vec3(0, 1, 0);
            case Z -> new Vec3(0, 0, 1);
        };
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Pos", pos.asLong());
        tag.putByte("Face", (byte) face.get3DDataValue());
        return tag;
    }

    public static Anchor load(CompoundTag tag) {
        return new Anchor(BlockPos.of(tag.getLong("Pos")), Direction.from3DDataValue(tag.getByte("Face")));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeByte(face.get3DDataValue());
    }

    public static Anchor read(FriendlyByteBuf buf) {
        return new Anchor(buf.readBlockPos(), Direction.from3DDataValue(buf.readByte()));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Anchor anchor)) {
            return false;
        }
        return pos.equals(anchor.pos) && face == anchor.face;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pos, face);
    }
}
