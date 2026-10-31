package com.zzynes.flydrone.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BshuCollision {
    private static final int SUBDIVISIONS = 8;
    private static final int PIXELS_PER_SUBCELL = 16 / SUBDIVISIONS;
    private static final List<Obb> OBBS = List.of(
            obb(4.33476251, 2.00000000, -0.20272430, 0.76604444, 0.00000000, -0.64278761, 0.00000000, 1.00000000, 0.00000000, 0.64278761, 0.00000000, 0.76604444, 2.50000000, 2.00000000, 0.12500000),
            obb(-4.33476251, 2.00000000, -0.20272430, 0.76604444, 0.00000000, 0.64278761, 0.00000000, 1.00000000, 0.00000000, -0.64278761, 0.00000000, 0.76604444, 2.50000000, 2.00000000, 0.12500000),
            obb(3.91792348, 0.75000000, -1.82439903, 0.76604444, 0.00000000, -0.64278761, 0.00000000, 1.00000000, 0.00000000, 0.64278761, 0.00000000, 0.76604444, 1.93750000, 0.75000000, 0.14687500),
            obb(3.55465598, 0.42075313, -2.25732472, 0.76604444, 0.00000000, -0.64278761, 0.64278761, 0.00000000, 0.76604444, 0.00000000, -1.00000000, 0.00000000, 1.93750000, 0.41875000, 0.12500000),
            obb(3.65377244, 0.14808344, -2.13303431, 0.76604444, 0.00000000, -0.64278761, 0.00000000, 1.00000000, 0.00000000, 0.64278761, 0.00000000, 0.76604444, 1.93750000, 0.14766969, 0.28125000),
            obb(0.07315250, 0.68818916, -0.98386518, 1.00000000, 0.00000000, 0.00000000, 0.00000000, 0.60876143, 0.79335334, 0.00000000, -0.79335334, 0.60876143, 2.60312500, 0.39688000, 0.12500000),
            obb(0.00000000, 0.75000000, -0.60780531, 1.00000000, 0.00000000, 0.00000000, 0.00000000, 1.00000000, 0.00000000, 0.00000000, 0.00000000, 1.00000000, 2.53125000, 0.75000000, 0.14219469),
            obb(0.17627750, 0.42075313, -1.06232688, 1.00000000, 0.00000000, 0.00000000, 0.00000000, 0.00000000, -1.00000000, 0.00000000, -1.00000000, 0.00000000, 2.50000000, 0.31250000, 0.12500000),
            obb(0.14502750, 0.14642563, -0.93732688, 1.00000000, 0.00000000, 0.00000000, 0.00000000, 0.00000000, 1.00000000, 0.00000000, -1.00000000, 0.00000000, 2.53125000, 0.18750000, 0.14766938),
            obb(-3.76206947, 0.14642594, -2.08313266, 0.76604444, 0.00000000, 0.64278761, -0.64278761, 0.00000000, 0.76604444, 0.00000000, -1.00000000, 0.00000000, 1.93750000, 0.16483031, 0.14766969),
            obb(-3.71855439, 0.70510750, -2.08637499, 0.76604444, 0.00000000, 0.64278761, -0.49240388, 0.64278761, 0.58682409, -0.41317591, -0.76604444, 0.49240388, 1.96875000, 0.39688000, 0.12500000),
            obb(-3.76810948, 0.42075313, -2.22178357, 0.76604444, 0.00000000, 0.64278761, 0.64278761, 0.00000000, -0.76604444, 0.00000000, -1.00000000, 0.00000000, 1.84375000, 0.31250000, 0.12500000),
            obb(-3.93708237, 0.75000000, -1.84047512, 0.76604444, 0.00000000, 0.64278761, 0.00000000, 1.00000000, 0.00000000, -0.64278761, 0.00000000, 0.76604444, 1.96250969, 0.75000000, 0.14687500),
            obb(4.07063684, 4.85348029, -0.51749750, 0.76604444, 0.00000000, -0.64278761, -0.27165378, 0.90630779, -0.32374437, 0.58256342, 0.42261826, 0.69427204, 2.50000000, 1.00000000, 0.12500000),
            obb(0.00752812, 4.85348029, 0.96135299, 1.00000000, 0.00000000, 0.00000000, 0.00000000, 0.90630779, -0.42261826, 0.00000000, 0.42261826, 0.90630779, 2.50000000, 1.00000000, 0.12500000),
            obb(-4.05558059, 4.85348029, -0.51749750, 0.76604444, 0.00000000, 0.64278761, 0.27165378, 0.90630779, -0.32374437, -0.58256342, 0.42261826, 0.69427204, 2.50000000, 1.00000000, 0.12500000),
            obb(-2.34694751, 4.91298682, 1.02758437, 0.92597618, -0.01493889, 0.37728629, 0.15737860, 0.92355360, -0.34968662, -0.34322018, 0.38317827, 0.85753969, 0.46875000, 1.00000000, 0.16877938),
            obb(2.36200376, 4.91298682, 1.02758437, 0.92597618, 0.01493889, -0.37728629, -0.15737860, 0.92355360, -0.34968662, 0.34322018, 0.38317827, 0.85753969, 0.46875000, 1.00000000, 0.16877938),
            obb(2.54206466, 2.02215319, 1.39464071, 0.92597618, 0.01493889, -0.37728629, -0.01613103, 0.99986989, 0.00000000, 0.37723720, 0.00608602, 0.92609668, 0.46875000, 2.03125000, 0.13752906),
            obb(-2.52700904, 2.02215319, 1.39464071, 0.92597618, -0.01493889, 0.37728629, 0.01613103, 0.99986989, 0.00000000, -0.37723720, 0.00608602, 0.92609668, 0.46875000, 2.03125000, 0.13752906),
            obb(3.61795017, 0.67852953, -2.23051042, 0.76604444, 0.00000000, -0.64278761, 0.55667040, 0.50000000, 0.66341395, 0.32139380, -0.86602540, 0.38302222, 1.90625000, 0.48205906, 0.12500000),
            obb(0.00000000, 2.00000000, 1.37500000, 1.00000000, 0.00000000, 0.00000000, 0.00000000, 1.00000000, 0.00000000, 0.00000000, 0.00000000, 1.00000000, 2.50000000, 2.00000000, 0.12500000)
    );
    private static final List<PartDef> PARTS;
    private static final Map<BlockPos, Integer> PART_INDEX = new HashMap<>();
    private static final VoxelShape[][] SHAPES;
    private static final int MASTER_PART_INDEX;
    private static boolean removingStructure;

    static {
        Set<SubPos> occupied = new HashSet<>();
        for (Obb obb : OBBS) {
            addObbCells(obb, occupied);
        }
        Map<BlockPos, boolean[][][]> cells = new HashMap<>();
        for (SubPos pos : occupied) {
            BlockPos cell = new BlockPos(
                    Math.floorDiv(pos.x(), SUBDIVISIONS),
                    Math.floorDiv(pos.y(), SUBDIVISIONS),
                    Math.floorDiv(pos.z(), SUBDIVISIONS)
            );
            boolean[][][] grid = cells.computeIfAbsent(cell, ignored -> new boolean[SUBDIVISIONS][SUBDIVISIONS][SUBDIVISIONS]);
            grid[Math.floorMod(pos.x(), SUBDIVISIONS)][Math.floorMod(pos.y(), SUBDIVISIONS)][Math.floorMod(pos.z(), SUBDIVISIONS)] = true;
        }
        List<BlockPos> offsets = new ArrayList<>(cells.keySet());
        offsets.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY())
                .thenComparingInt((BlockPos pos) -> pos.getZ())
                .thenComparingInt((BlockPos pos) -> pos.getX()));
        List<PartDef> parts = new ArrayList<>();
        for (BlockPos offset : offsets) {
            int index = parts.size();
            PART_INDEX.put(offset, index);
            parts.add(new PartDef(index, offset, List.copyOf(mergeBoxes(cells.get(offset)))));
        }
        PARTS = List.copyOf(parts);
        MASTER_PART_INDEX = PART_INDEX.getOrDefault(BlockPos.ZERO, -1);
        VoxelShape[][] shapes = new VoxelShape[PARTS.size()][4];
        for (PartDef part : PARTS) {
            for (Direction facing : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                shapes[part.index()][facingIndex(facing)] = buildShape(part, facing);
            }
        }
        SHAPES = shapes;
    }

    private BshuCollision() {
    }

    public static int partCount() {
        return PARTS.size();
    }

    public static VoxelShape masterShape(Direction facing) {
        return partShape(MASTER_PART_INDEX, facing);
    }

    public static VoxelShape partShape(int partIndex, Direction facing) {
        if (partIndex < 0 || partIndex >= SHAPES.length) {
            return Shapes.empty();
        }
        return SHAPES[partIndex][facingIndex(facing)];
    }

    public static BlockPos worldOffset(int partIndex, Direction facing) {
        if (partIndex < 0 || partIndex >= PARTS.size()) {
            return BlockPos.ZERO;
        }
        BlockPos offset = PARTS.get(partIndex).offset();
        return rotateOffset(offset, facing);
    }

    public static boolean isMasterPart(int partIndex) {
        return partIndex == MASTER_PART_INDEX;
    }

    @Nullable
    public static BlockState expectedPartState(int partIndex, Direction facing) {
        Block block = VverRegistry.BSHU_PART_BLOCK.get();
        if (block instanceof BshuPartBlock partBlock && partIndex >= 0 && partIndex < PARTS.size()) {
            return partBlock.stateFor(partIndex, facing);
        }
        return null;
    }

    public static boolean isExpectedPart(BlockState state, int partIndex, Direction facing) {
        return state != null
                && state.getBlock() instanceof BshuPartBlock partBlock
                && partBlock.partIndex(state) == partIndex
                && partBlock.facing(state) == horizontal(facing);
    }

    @Nullable
    public static BlockPos masterPosForPart(BlockPos partPos, BlockState state) {
        if (state == null || !(state.getBlock() instanceof BshuPartBlock partBlock)) {
            return null;
        }
        int partIndex = partBlock.partIndex(state);
        if (partIndex < 0 || partIndex >= PARTS.size()) {
            return null;
        }
        Direction facing = partBlock.facing(state);
        BlockPos offset = worldOffset(partIndex, facing);
        return partPos.offset(-offset.getX(), -offset.getY(), -offset.getZ());
    }

    public static boolean canPlace(Level level, BlockPos masterPos, Direction facing, BlockPlaceContext context) {
        Direction useFacing = horizontal(facing);
        for (PartDef part : PARTS) {
            if (part.index() == MASTER_PART_INDEX) {
                continue;
            }
            BlockPos pos = offsetPos(masterPos, part.index(), useFacing);
            if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (isExpectedPart(state, part.index(), useFacing)) {
                continue;
            }
            if (!state.canBeReplaced(context) || !state.getFluidState().isEmpty()) {
                return false;
            }
            BlockState expected = expectedPartState(part.index(), useFacing);
            if (expected == null || !level.isUnobstructed(expected, pos, CollisionContext.empty())) {
                return false;
            }
        }
        return true;
    }

    public static boolean placeParts(Level level, BlockPos masterPos, Direction facing) {
        if (level.isClientSide) {
            return false;
        }
        Direction useFacing = horizontal(facing);
        boolean complete = true;
        for (PartDef part : PARTS) {
            if (part.index() == MASTER_PART_INDEX) {
                continue;
            }
            BlockPos pos = offsetPos(masterPos, part.index(), useFacing);
            if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) {
                complete = false;
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (isExpectedPart(state, part.index(), useFacing)) {
                continue;
            }
            BlockState expected = expectedPartState(part.index(), useFacing);
            if (expected != null
                    && state.canBeReplaced()
                    && state.getFluidState().isEmpty()
                    && level.isUnobstructed(expected, pos, CollisionContext.empty())) {
                level.setBlock(pos, expected, 3);
            } else {
                complete = false;
            }
        }
        return complete;
    }

    public static void removeStructure(Level level, BlockPos masterPos, Direction facing, @Nullable BlockPos skipPos) {
        if (level.isClientSide || removingStructure) {
            return;
        }
        Direction useFacing = horizontal(facing);
        removingStructure = true;
        try {
            if (!masterPos.equals(skipPos)
                    && level.getBlockState(masterPos).is(VverRegistry.BSHU_BLOCK.get())) {
                level.setBlock(masterPos, Blocks.AIR.defaultBlockState(), 35);
            }
            for (PartDef part : PARTS) {
                if (part.index() == MASTER_PART_INDEX) {
                    continue;
                }
                BlockPos pos = offsetPos(masterPos, part.index(), useFacing);
                if (pos.equals(skipPos)) {
                    continue;
                }
                if (isExpectedPart(level.getBlockState(pos), part.index(), useFacing)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 35);
                }
            }
        } finally {
            removingStructure = false;
        }
    }

    private static BlockPos offsetPos(BlockPos masterPos, int partIndex, Direction facing) {
        BlockPos offset = worldOffset(partIndex, facing);
        return masterPos.offset(offset.getX(), offset.getY(), offset.getZ());
    }

    private static BlockPos rotateOffset(BlockPos offset, Direction facing) {
        int dx = offset.getX();
        int dy = offset.getY();
        int dz = offset.getZ();
        return switch (horizontal(facing)) {
            case SOUTH -> new BlockPos(-dx, dy, -dz);
            case WEST -> new BlockPos(dz, dy, -dx);
            case EAST -> new BlockPos(-dz, dy, dx);
            default -> offset;
        };
    }

    private static Direction horizontal(Direction facing) {
        return facing != null && facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
    }

    private static int facingIndex(Direction facing) {
        return switch (horizontal(facing)) {
            case SOUTH -> 1;
            case WEST -> 2;
            case EAST -> 3;
            default -> 0;
        };
    }

    private static VoxelShape buildShape(PartDef part, Direction facing) {
        VoxelShape shape = Shapes.empty();
        Direction useFacing = horizontal(facing);
        for (BoxDef box : part.boxes()) {
            int x0 = box.minX() * PIXELS_PER_SUBCELL;
            int y0 = box.minY() * PIXELS_PER_SUBCELL;
            int z0 = box.minZ() * PIXELS_PER_SUBCELL;
            int x1 = box.maxX() * PIXELS_PER_SUBCELL;
            int y1 = box.maxY() * PIXELS_PER_SUBCELL;
            int z1 = box.maxZ() * PIXELS_PER_SUBCELL;
            int rx0 = x0;
            int rz0 = z0;
            int rx1 = x1;
            int rz1 = z1;
            switch (useFacing) {
                case SOUTH -> {
                    rx0 = 16 - x1;
                    rx1 = 16 - x0;
                    rz0 = 16 - z1;
                    rz1 = 16 - z0;
                }
                case WEST -> {
                    rx0 = z0;
                    rx1 = z1;
                    rz0 = 16 - x1;
                    rz1 = 16 - x0;
                }
                case EAST -> {
                    rx0 = 16 - z1;
                    rx1 = 16 - z0;
                    rz0 = x0;
                    rz1 = x1;
                }
                default -> {
                }
            }
            shape = Shapes.or(shape, Shapes.box(rx0 / 16.0, y0 / 16.0, rz0 / 16.0, rx1 / 16.0, y1 / 16.0, rz1 / 16.0));
        }
        return shape;
    }

    private static void addObbCells(Obb obb, Set<SubPos> occupied) {
        double[] lo = new double[3];
        double[] hi = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            double extent = 0.0;
            for (int i = 0; i < 3; i++) {
                extent += Math.abs(obb.axes()[i][axis] * obb.half()[i]);
            }
            lo[axis] = obb.center()[axis] - extent;
            hi[axis] = obb.center()[axis] + extent;
        }
        int minX = (int) Math.floor((lo[0] + 0.5) * SUBDIVISIONS);
        int maxX = (int) Math.ceil((hi[0] + 0.5) * SUBDIVISIONS) - 1;
        int minY = Math.max(0, (int) Math.floor(lo[1] * SUBDIVISIONS));
        int maxY = (int) Math.ceil(hi[1] * SUBDIVISIONS) - 1;
        int minZ = (int) Math.floor((lo[2] + 0.5) * SUBDIVISIONS);
        int maxZ = (int) Math.ceil((hi[2] + 0.5) * SUBDIVISIONS) - 1;
        for (int ix = minX; ix <= maxX; ix++) {
            double x = (ix + 0.5) / SUBDIVISIONS - 0.5;
            for (int iy = minY; iy <= maxY; iy++) {
                double y = (iy + 0.5) / SUBDIVISIONS;
                for (int iz = minZ; iz <= maxZ; iz++) {
                    double z = (iz + 0.5) / SUBDIVISIONS - 0.5;
                    if (intersects(obb, x, y, z)) {
                        occupied.add(new SubPos(ix, iy, iz));
                    }
                }
            }
        }
    }

    private static boolean intersects(Obb obb, double x, double y, double z) {
        double dx = x - obb.center()[0];
        double dy = y - obb.center()[1];
        double dz = z - obb.center()[2];
        double cellRadius = 0.5 / SUBDIVISIONS;
        for (int i = 0; i < 3; i++) {
            double[] axis = obb.axes()[i];
            double projection = Math.abs(dx * axis[0] + dy * axis[1] + dz * axis[2]);
            double radius = cellRadius * (Math.abs(axis[0]) + Math.abs(axis[1]) + Math.abs(axis[2]));
            if (projection > obb.half()[i] + radius + 1.0e-9) {
                return false;
            }
        }
        return true;
    }

    private static List<BoxDef> mergeBoxes(boolean[][][] source) {
        boolean[][][] unused = new boolean[SUBDIVISIONS][SUBDIVISIONS][SUBDIVISIONS];
        for (int x = 0; x < SUBDIVISIONS; x++) {
            for (int y = 0; y < SUBDIVISIONS; y++) {
                System.arraycopy(source[x][y], 0, unused[x][y], 0, SUBDIVISIONS);
            }
        }
        List<BoxDef> boxes = new ArrayList<>();
        for (int y = 0; y < SUBDIVISIONS; y++) {
            for (int z = 0; z < SUBDIVISIONS; z++) {
                for (int x = 0; x < SUBDIVISIONS; x++) {
                    if (!unused[x][y][z]) {
                        continue;
                    }
                    int x1 = x;
                    int y1 = y;
                    int z1 = z;
                    boolean grew = true;
                    while (grew) {
                        grew = false;
                        while (x1 + 1 < SUBDIVISIONS && canExpandX(unused, x1 + 1, y, y1, z, z1)) {
                            x1++;
                            grew = true;
                        }
                        while (y1 + 1 < SUBDIVISIONS && canExpandY(unused, y1 + 1, x, x1, z, z1)) {
                            y1++;
                            grew = true;
                        }
                        while (z1 + 1 < SUBDIVISIONS && canExpandZ(unused, z1 + 1, x, x1, y, y1)) {
                            z1++;
                            grew = true;
                        }
                    }
                    boxes.add(new BoxDef(x, y, z, x1 + 1, y1 + 1, z1 + 1));
                    for (int xx = x; xx <= x1; xx++) {
                        for (int yy = y; yy <= y1; yy++) {
                            for (int zz = z; zz <= z1; zz++) {
                                unused[xx][yy][zz] = false;
                            }
                        }
                    }
                }
            }
        }
        return boxes;
    }

    private static boolean canExpandX(boolean[][][] grid, int x, int y0, int y1, int z0, int z1) {
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                if (!grid[x][y][z]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean canExpandY(boolean[][][] grid, int y, int x0, int x1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!grid[x][y][z]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean canExpandZ(boolean[][][] grid, int z, int x0, int x1, int y0, int y1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                if (!grid[x][y][z]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Obb obb(double... values) {
        return new Obb(
                new double[]{values[0], values[1], values[2]},
                new double[][]{
                        {values[3], values[4], values[5]},
                        {values[6], values[7], values[8]},
                        {values[9], values[10], values[11]}
                },
                new double[]{values[12], values[13], values[14]}
        );
    }

    private record Obb(double[] center, double[][] axes, double[] half) {
    }

    private record SubPos(int x, int y, int z) {
    }

    private record BoxDef(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    private record PartDef(int index, BlockPos offset, List<BoxDef> boxes) {
    }
}
