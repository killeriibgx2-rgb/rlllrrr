package com.zzynes.sarcophag.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

// обход корпуса реактора: ищем замкнутую оболочку, считаем объём и патрубки
public final class ReactorVesselScanner {
    // больше - это уже не корпус, а склад металла, оболочку не считаем
    private static final int MAX_SHELL_CELLS = 16384;
    // отсечка от гигантских построек, иначе скан съедает весь тик
    private static final long MAX_SCAN_VOLUME = 524288L;
    private static final double CORE_CELL_VOLUME = 0.35;

    private ReactorVesselScanner() {
    }

    public static Result scan(ServerLevel level, BlockPos anchor) {
        return scan(level, anchor, List.of());
    }

    public static Result scan(ServerLevel level, BlockPos anchor, Collection<BlockPos> preferredCoreBases) {
        Result result = new Result();
        if (level == null || anchor == null || !level.isLoaded(anchor)) {
            result.complete = false;
            return result;
        }
        BlockPos hullSeed = findHullSeed(level, anchor, preferredCoreBases);
        if (hullSeed == null) {
            return result;
        }
        result.hullPresent = true;
        collectHullComponent(level, hullSeed, result.shell, result);
        if (result.shell.isEmpty()) {
            result.hullPresent = false;
            return result;
        }
        updateHullBounds(result);
        int minX = result.hullMinX - 2;
        int minY = result.hullMinY - 2;
        int minZ = result.hullMinZ - 2;
        int maxX = result.hullMaxX + 2;
        int maxY = result.hullMaxY + 2;
        int maxZ = result.hullMaxZ + 2;
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > MAX_SCAN_VOLUME) {
            result.complete = false;
            result.anchor = hullSeed;
            return result;
        }
        Set<BlockPos> exterior = floodExterior(level, result, minX, minY, minZ, maxX, maxY, maxZ);
        List<Set<BlockPos>> enclosed = findEnclosedComponents(level, result, exterior, minX, minY, minZ, maxX, maxY, maxZ);
        Set<BlockPos> cavity = chooseCavity(level, enclosed, preferredCoreBases);
        if (cavity != null) {
            fillEnclosed(level, result, cavity, exterior);
        } else {
            fillOpenBasin(level, result, exterior, preferredCoreBases);
        }
        finish(level, result);
        return result;
    }

    private static BlockPos findHullSeed(ServerLevel level, BlockPos anchor, Collection<BlockPos> preferredCoreBases) {
        if (isHull(level, anchor)) {
            return anchor.immutable();
        }
        List<BlockPos> seeds = new ArrayList<>();
        BlockPos anchorBase = BshuBlockEntity.componentBase(level, anchor);
        if (anchorBase != null) {
            seeds.add(anchorBase);
        }
        if (preferredCoreBases != null) {
            for (BlockPos preferred : preferredCoreBases) {
                if (preferred == null || !level.isLoaded(preferred)) {
                    continue;
                }
                BlockPos base = BshuBlockEntity.componentBase(level, preferred);
                if (base != null && !seeds.contains(base)) {
                    seeds.add(base);
                }
            }
        }
        if (seeds.isEmpty()) {
            seeds.add(anchor.immutable());
        }
        int[] radii = new int[]{3, 6, 10, 16, 24};
        for (int radius : radii) {
            for (BlockPos seed : seeds) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != radius) {
                                continue;
                            }
                            BlockPos candidate = seed.offset(dx, dy, dz);
                            if (isHull(level, candidate)) {
                                return candidate.immutable();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static void collectHullComponent(ServerLevel level, BlockPos seed, Set<BlockPos> output, Result result) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = seed.immutable();
        if (output.add(start)) {
            queue.add(start);
        }
        while (!queue.isEmpty() && output.size() < MAX_SHELL_CELLS) {
            BlockPos current = queue.remove();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = current.offset(dx, dy, dz);
                        if (!level.isLoaded(next)) {
                            result.complete = false;
                            continue;
                        }
                        if (isHull(level, next) && output.add(next.immutable())) {
                            queue.add(next.immutable());
                            if (output.size() >= MAX_SHELL_CELLS) {
                                result.complete = false;
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private static void expandShellFromBoundary(ServerLevel level, Result result) {
        List<BlockPos> seeds = new ArrayList<>();
        for (BlockPos pos : result.cavity) {
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (isHull(level, next) && !result.shell.contains(next)) {
                    seeds.add(next.immutable());
                }
            }
        }
        for (BlockPos pos : result.coreCells) {
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (isHull(level, next) && !result.shell.contains(next)) {
                    seeds.add(next.immutable());
                }
            }
        }
        for (BlockPos seed : seeds) {
            collectHullComponent(level, seed, result.shell, result);
        }
        updateHullBounds(result);
    }

    private static void updateHullBounds(Result result) {
        result.hullMinX = Integer.MAX_VALUE;
        result.hullMinY = Integer.MAX_VALUE;
        result.hullMinZ = Integer.MAX_VALUE;
        result.hullMaxX = Integer.MIN_VALUE;
        result.hullMaxY = Integer.MIN_VALUE;
        result.hullMaxZ = Integer.MIN_VALUE;
        for (BlockPos pos : result.shell) {
            result.hullMinX = Math.min(result.hullMinX, pos.getX());
            result.hullMinY = Math.min(result.hullMinY, pos.getY());
            result.hullMinZ = Math.min(result.hullMinZ, pos.getZ());
            result.hullMaxX = Math.max(result.hullMaxX, pos.getX());
            result.hullMaxY = Math.max(result.hullMaxY, pos.getY());
            result.hullMaxZ = Math.max(result.hullMaxZ, pos.getZ());
        }
    }

    private static Set<BlockPos> floodExterior(ServerLevel level, Result result, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Set<BlockPos> exterior = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (x != minX && x != maxX && y != minY && y != maxY && z != minZ && z != maxZ) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.isLoaded(pos)) {
                        result.complete = false;
                        continue;
                    }
                    if (isPassable(level, pos) && exterior.add(pos)) {
                        queue.add(pos);
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!inside(next, minX, minY, minZ, maxX, maxY, maxZ)) {
                    continue;
                }
                if (!level.isLoaded(next)) {
                    result.complete = false;
                    continue;
                }
                if (isPassable(level, next) && exterior.add(next.immutable())) {
                    queue.add(next.immutable());
                }
            }
        }
        return exterior;
    }

    private static List<Set<BlockPos>> findEnclosedComponents(ServerLevel level, Result result, Set<BlockPos> exterior, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        List<Set<BlockPos>> components = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>(exterior);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.isLoaded(pos)) {
                        result.complete = false;
                        continue;
                    }
                    if (visited.contains(pos) || !isPassable(level, pos)) {
                        continue;
                    }
                    Set<BlockPos> component = new HashSet<>();
                    Queue<BlockPos> queue = new ArrayDeque<>();
                    visited.add(pos);
                    component.add(pos);
                    queue.add(pos);
                    while (!queue.isEmpty()) {
                        BlockPos current = queue.remove();
                        for (Direction direction : Direction.values()) {
                            BlockPos next = current.relative(direction);
                            if (!inside(next, minX, minY, minZ, maxX, maxY, maxZ) || visited.contains(next)) {
                                continue;
                            }
                            if (!level.isLoaded(next)) {
                                result.complete = false;
                                continue;
                            }
                            if (isPassable(level, next)) {
                                BlockPos immutable = next.immutable();
                                visited.add(immutable);
                                component.add(immutable);
                                queue.add(immutable);
                            }
                        }
                    }
                    components.add(component);
                }
            }
        }
        return components;
    }

    private static Set<BlockPos> chooseCavity(ServerLevel level, List<Set<BlockPos>> components, Collection<BlockPos> preferredCoreBases) {
        Set<BlockPos> preferredCells = new HashSet<>();
        if (preferredCoreBases != null) {
            for (BlockPos preferred : preferredCoreBases) {
                if (preferred == null || !level.isLoaded(preferred)) {
                    continue;
                }
                BlockPos base = BshuBlockEntity.componentBase(level, preferred);
                if (base == null) {
                    continue;
                }
                for (int i = 0; i < 3; i++) {
                    BlockPos cell = base.above(i);
                    if (isCore(level, cell)) {
                        preferredCells.add(cell.immutable());
                    }
                }
            }
        }
        Set<BlockPos> best = null;
        int bestPreferred = -1;
        int bestCoreNeighbors = -1;
        int bestSize = -1;
        for (Set<BlockPos> component : components) {
            Set<BlockPos> coreNeighbors = new HashSet<>();
            int preferred = 0;
            for (BlockPos pos : component) {
                for (Direction direction : Direction.values()) {
                    BlockPos next = pos.relative(direction);
                    if (isCore(level, next)) {
                        coreNeighbors.add(next.immutable());
                        if (preferredCells.contains(next)) {
                            preferred++;
                        }
                    }
                }
            }
            boolean better = best == null
                    || preferred > bestPreferred
                    || (preferred == bestPreferred && coreNeighbors.size() > bestCoreNeighbors)
                    || (preferred == bestPreferred && coreNeighbors.size() == bestCoreNeighbors && component.size() > bestSize);
            if (better) {
                best = component;
                bestPreferred = preferred;
                bestCoreNeighbors = coreNeighbors.size();
                bestSize = component.size();
            }
        }
        return best;
    }

    private static void fillEnclosed(ServerLevel level, Result result, Set<BlockPos> cavity, Set<BlockPos> exterior) {
        result.cavity.addAll(cavity);
        collectCoreCells(level, result);
        expandShellFromBoundary(level, result);
        boolean sealed = true;
        for (BlockPos pos : result.cavity) {
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!level.isLoaded(next)) {
                    result.complete = false;
                    sealed = false;
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (isHull(state) || isCore(state) || isPassable(level, next)) {
                    continue;
                }
                if (touchesExterior(next, exterior)) {
                    addLeak(result, next.immutable(), direction);
                    sealed = false;
                }
            }
        }
        result.sealed = sealed && result.leakPositions.isEmpty();
    }

    private static void fillOpenBasin(ServerLevel level, Result result, Set<BlockPos> exterior, Collection<BlockPos> preferredCoreBases) {
        if (!fillTrappedBasin(level, result, preferredCoreBases)) {
            fillOpenBasinFallback(level, result, exterior, preferredCoreBases);
        }
    }

    private static boolean fillTrappedBasin(ServerLevel level, Result result, Collection<BlockPos> preferredCoreBases) {
        int graphMinX = result.hullMinX - 2;
        int graphMinY = result.hullMinY - 2;
        int graphMinZ = result.hullMinZ - 2;
        int graphMaxX = result.hullMaxX + 2;
        int graphMaxY = result.hullMaxY + 2;
        int graphMaxZ = result.hullMaxZ + 2;
        Map<BlockPos, Integer> escapeLevel = new HashMap<>();
        PriorityQueue<EscapeEntry> priority = new PriorityQueue<>(Comparator.comparingInt(EscapeEntry::level));
        for (int x = graphMinX; x <= graphMaxX; x++) {
            for (int y = graphMinY; y <= graphMaxY; y++) {
                for (int z = graphMinZ; z <= graphMaxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    boolean graphBoundary = x == graphMinX || x == graphMaxX || y == graphMinY || y == graphMaxY || z == graphMinZ || z == graphMaxZ;
                    boolean outsideHull = !inside(pos, result.hullMinX, result.hullMinY, result.hullMinZ, result.hullMaxX, result.hullMaxY, result.hullMaxZ);
                    if (!graphBoundary && !outsideHull) {
                        continue;
                    }
                    if (!level.isLoaded(pos)) {
                        result.complete = false;
                        continue;
                    }
                    if (isPassable(level, pos) || isCore(level, pos)) {
                        int value = pos.getY() + 1;
                        escapeLevel.put(pos, value);
                        priority.add(new EscapeEntry(value, pos));
                    }
                }
            }
        }
        while (!priority.isEmpty()) {
            EscapeEntry entry = priority.remove();
            if (escapeLevel.getOrDefault(entry.pos(), Integer.MAX_VALUE) != entry.level()) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = entry.pos().relative(direction);
                if (!inside(next, graphMinX, graphMinY, graphMinZ, graphMaxX, graphMaxY, graphMaxZ)) {
                    continue;
                }
                if (!level.isLoaded(next)) {
                    result.complete = false;
                    continue;
                }
                if (!isPassable(level, next) && !isCore(level, next)) {
                    continue;
                }
                int nextLevel = Math.max(entry.level(), next.getY() + 1);
                if (nextLevel < escapeLevel.getOrDefault(next, Integer.MAX_VALUE)) {
                    BlockPos immutable = next.immutable();
                    escapeLevel.put(immutable, nextLevel);
                    priority.add(new EscapeEntry(nextLevel, immutable));
                }
            }
        }
        Set<BlockPos> preferredCells = new HashSet<>();
        if (preferredCoreBases != null) {
            for (BlockPos preferred : preferredCoreBases) {
                if (preferred == null || !level.isLoaded(preferred)) {
                    continue;
                }
                BlockPos base = BshuBlockEntity.componentBase(level, preferred);
                if (base != null) {
                    for (int i = 0; i < 3; i++) {
                        preferredCells.add(base.above(i).immutable());
                    }
                }
            }
        }
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> bestPassable = null;
        Set<BlockPos> bestCore = null;
        int bestPreferred = -1;
        int bestSupported = -1;
        int bestSize = -1;
        for (BlockPos start : escapeLevel.keySet()) {
            if (!inside(start, result.hullMinX, result.hullMinY, result.hullMinZ, result.hullMaxX, result.hullMaxY, result.hullMaxZ)
                    || visited.contains(start)
                    || escapeLevel.getOrDefault(start, Integer.MAX_VALUE) <= start.getY() + 1) {
                continue;
            }
            Set<BlockPos> passable = new HashSet<>();
            Set<BlockPos> core = new HashSet<>();
            Queue<BlockPos> queue = new ArrayDeque<>();
            visited.add(start);
            queue.add(start);
            if (isCore(level, start)) {
                core.add(start);
            } else {
                passable.add(start);
            }
            while (!queue.isEmpty()) {
                BlockPos current = queue.remove();
                for (Direction direction : Direction.values()) {
                    BlockPos next = current.relative(direction);
                    if (!inside(next, result.hullMinX, result.hullMinY, result.hullMinZ, result.hullMaxX, result.hullMaxY, result.hullMaxZ)
                            || visited.contains(next)
                            || !escapeLevel.containsKey(next)
                            || escapeLevel.getOrDefault(next, Integer.MAX_VALUE) <= next.getY() + 1) {
                        continue;
                    }
                    BlockPos immutable = next.immutable();
                    visited.add(immutable);
                    queue.add(immutable);
                    if (isCore(level, immutable)) {
                        core.add(immutable);
                    } else {
                        passable.add(immutable);
                    }
                }
            }
            int preferred = 0;
            for (BlockPos cell : core) {
                if (preferredCells.contains(cell)) {
                    preferred++;
                }
            }
            int supported = 0;
            for (BlockPos cell : passable) {
                if (isHull(level, cell.below())) {
                    supported++;
                }
            }
            int size = passable.size() + core.size();
            boolean better = bestPassable == null
                    || preferred > bestPreferred
                    || (preferred == bestPreferred && supported > bestSupported)
                    || (preferred == bestPreferred && supported == bestSupported && size > bestSize);
            if (better) {
                bestPassable = passable;
                bestCore = core;
                bestPreferred = preferred;
                bestSupported = supported;
                bestSize = size;
            }
        }
        if (bestPassable == null || bestCore == null || (bestPassable.isEmpty() && bestCore.isEmpty())) {
            return false;
        }
        result.cavity.addAll(bestPassable);
        result.coreCells.addAll(bestCore);
        expandShellFromBoundary(level, result);
        Set<BlockPos> basin = new HashSet<>(bestPassable);
        basin.addAll(bestCore);
        int spillLevel = Integer.MAX_VALUE;
        for (BlockPos pos : basin) {
            spillLevel = Math.min(spillLevel, escapeLevel.getOrDefault(pos, Integer.MAX_VALUE));
        }
        boolean foundLeak = false;
        Queue<BlockPos> spillQueue = new ArrayDeque<>(basin);
        Set<BlockPos> spillVisited = new HashSet<>(basin);
        while (!spillQueue.isEmpty() && !foundLeak) {
            BlockPos current = spillQueue.remove();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!inside(next, result.hullMinX, result.hullMinY, result.hullMinZ, result.hullMaxX, result.hullMaxY, result.hullMaxZ)) {
                    if (isPassable(level, next) || isCore(level, next)) {
                        addLeak(result, next.immutable(), direction);
                        foundLeak = true;
                        break;
                    }
                    continue;
                }
                if (!inside(next, graphMinX, graphMinY, graphMinZ, graphMaxX, graphMaxY, graphMaxZ)
                        || spillVisited.contains(next)
                        || (!isPassable(level, next) && !isCore(level, next))) {
                    continue;
                }
                if (next.getY() < spillLevel && escapeLevel.getOrDefault(next, Integer.MAX_VALUE) <= spillLevel) {
                    BlockPos immutable = next.immutable();
                    spillVisited.add(immutable);
                    spillQueue.add(immutable);
                }
            }
        }
        if (!foundLeak) {
            for (BlockPos pos : basin) {
                for (Direction direction : Direction.values()) {
                    BlockPos next = pos.relative(direction);
                    if (!basin.contains(next) && (isPassable(level, next) || isCore(level, next))) {
                        addLeak(result, next.immutable(), direction);
                        foundLeak = true;
                        break;
                    }
                }
                if (foundLeak) {
                    break;
                }
            }
        }
        result.sealed = false;
        return true;
    }

    private record EscapeEntry(int level, BlockPos pos) {
    }

    private static void fillOpenBasinFallback(ServerLevel level, Result result, Set<BlockPos> exterior, Collection<BlockPos> preferredCoreBases) {
        int minX = result.hullMinX;
        int minY = result.hullMinY;
        int minZ = result.hullMinZ;
        int maxX = result.hullMaxX;
        int maxY = result.hullMaxY;
        int maxZ = result.hullMaxZ;
        Set<BlockPos> preferredCells = new HashSet<>();
        if (preferredCoreBases != null) {
            for (BlockPos preferred : preferredCoreBases) {
                if (preferred == null || !level.isLoaded(preferred)) {
                    continue;
                }
                BlockPos base = BshuBlockEntity.componentBase(level, preferred);
                if (base != null) {
                    for (int i = 0; i < 3; i++) {
                        preferredCells.add(base.above(i).immutable());
                    }
                }
            }
        }
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> bestPassable = null;
        Set<BlockPos> bestCore = null;
        int bestPreferred = -1;
        int bestSupported = -1;
        int bestSize = -1;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.isLoaded(pos)) {
                        result.complete = false;
                        continue;
                    }
                    if (visited.contains(pos) || (!isPassable(level, pos) && !isCore(level, pos))) {
                        continue;
                    }
                    Set<BlockPos> passable = new HashSet<>();
                    Set<BlockPos> core = new HashSet<>();
                    Queue<BlockPos> queue = new ArrayDeque<>();
                    visited.add(pos);
                    queue.add(pos);
                    if (isCore(level, pos)) {
                        core.add(pos);
                    } else {
                        passable.add(pos);
                    }
                    while (!queue.isEmpty()) {
                        BlockPos current = queue.remove();
                        for (Direction direction : Direction.values()) {
                            BlockPos next = current.relative(direction);
                            if (!inside(next, minX, minY, minZ, maxX, maxY, maxZ) || visited.contains(next)) {
                                continue;
                            }
                            if (!level.isLoaded(next)) {
                                result.complete = false;
                                continue;
                            }
                            if (isCore(level, next)) {
                                BlockPos immutable = next.immutable();
                                visited.add(immutable);
                                core.add(immutable);
                                queue.add(immutable);
                            } else if (isPassable(level, next)) {
                                BlockPos immutable = next.immutable();
                                visited.add(immutable);
                                passable.add(immutable);
                                queue.add(immutable);
                            }
                        }
                    }
                    int preferred = 0;
                    for (BlockPos cell : core) {
                        if (preferredCells.contains(cell)) {
                            preferred++;
                        }
                    }
                    int supported = 0;
                    for (BlockPos cell : passable) {
                        if (isHull(level, cell.below())) {
                            supported++;
                        }
                    }
                    int size = passable.size() + core.size();
                    boolean better = bestPassable == null
                            || preferred > bestPreferred
                            || (preferred == bestPreferred && supported > bestSupported)
                            || (preferred == bestPreferred && supported == bestSupported && size > bestSize);
                    if (better) {
                        bestPassable = passable;
                        bestCore = core;
                        bestPreferred = preferred;
                        bestSupported = supported;
                        bestSize = size;
                    }
                }
            }
        }
        if (bestPassable != null) {
            result.cavity.addAll(bestPassable);
        }
        if (bestCore != null) {
            result.coreCells.addAll(bestCore);
        }
        expandShellFromBoundary(level, result);
        Set<BlockPos> interior = new HashSet<>(result.cavity);
        interior.addAll(result.coreCells);
        for (BlockPos pos : interior) {
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!inside(next, result.hullMinX, result.hullMinY, result.hullMinZ, result.hullMaxX, result.hullMaxY, result.hullMaxZ)) {
                    addLeak(result, next.immutable(), direction);
                }
            }
        }
        result.sealed = false;
    }

    private static void collectCoreCells(ServerLevel level, Result result) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos pos : result.cavity) {
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (isCore(level, next) && result.coreCells.add(next.immutable())) {
                    queue.add(next.immutable());
                }
            }
        }
        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (isCore(level, next) && result.coreCells.add(next.immutable())) {
                    queue.add(next.immutable());
                }
            }
        }
    }

    private static void computeNozzlePerformance(ServerLevel level, Result result) {
        int cavityMinY = Integer.MAX_VALUE;
        int cavityMaxY = Integer.MIN_VALUE;
        for (BlockPos pos : result.cavity) {
            cavityMinY = Math.min(cavityMinY, pos.getY());
            cavityMaxY = Math.max(cavityMaxY, pos.getY());
        }
        for (BlockPos pos : result.coreCells) {
            cavityMinY = Math.min(cavityMinY, pos.getY());
            cavityMaxY = Math.max(cavityMaxY, pos.getY());
        }
        if (cavityMinY == Integer.MAX_VALUE) {
            cavityMinY = result.hullMinY;
            cavityMaxY = result.hullMaxY;
        }
        List<NozzleScore> scores = new ArrayList<>();
        for (BlockPos pos : result.shell) {
            BlockState state = state(level, pos);
            if (state == null || !(state.getBlock() instanceof HullNozzleBlock)) {
                continue;
            }
            Direction facing = state.getValue(HullNozzleBlock.FACING);
            BlockPos inside = pos.relative(facing.getOpposite());
            double access = result.cavity.contains(inside) || result.coreCells.contains(inside) ? 1.0 : 0.0;
            if (access <= 0.0) {
                for (Direction direction : Direction.values()) {
                    BlockPos neighbor = pos.relative(direction);
                    if (result.cavity.contains(neighbor) || result.coreCells.contains(neighbor)) {
                        access = 0.35;
                        break;
                    }
                }
            }
            if (access <= 0.0) {
                continue;
            }
            double orientation = facing.getAxis().isHorizontal() ? 1.0 : facing == Direction.UP ? 0.78 : 0.68;
            double height = cavityMaxY <= cavityMinY ? 0.5 : (double) (pos.getY() - cavityMinY) / (double) (cavityMaxY - cavityMinY);
            height = Math.max(0.0, Math.min(1.0, height));
            double inlet = access * orientation * (1.0 - 0.72 * height);
            double outlet = access * orientation * (0.28 + 0.72 * height);
            scores.add(new NozzleScore(inlet, outlet, height));
            result.bestInletPotential = Math.max(result.bestInletPotential, inlet);
            result.bestOutletPotential = Math.max(result.bestOutletPotential, outlet);
        }
        result.connectedNozzleCount = scores.size();
        if (scores.isEmpty()) {
            result.nozzlePerformance = 0.0;
            return;
        }
        if (scores.size() == 1) {
            NozzleScore only = scores.get(0);
            result.nozzlePerformance = Math.max(only.inlet(), only.outlet()) * 15.0;
            return;
        }
        double bestPair = 0.0;
        for (int i = 0; i < scores.size(); i++) {
            for (int j = 0; j < scores.size(); j++) {
                if (i == j) {
                    continue;
                }
                NozzleScore inlet = scores.get(i);
                NozzleScore outlet = scores.get(j);
                double separation = Math.abs(outlet.height() - inlet.height());
                double pair = Math.sqrt(Math.max(0.0, inlet.inlet() * outlet.outlet())) * (0.45 + 0.55 * separation);
                bestPair = Math.max(bestPair, pair);
            }
        }
        double countFactor = 0.78 + 0.11 * Math.min(2, scores.size() - 2);
        result.nozzlePerformance = Math.max(0.0, Math.min(100.0, bestPair * countFactor * 100.0));
    }

    private record NozzleScore(double inlet, double outlet, double height) {
    }

    private static void finish(ServerLevel level, Result result) {
        for (BlockPos pos : result.shell) {
            BlockState state = state(level, pos);
            if (state != null && state.getBlock() instanceof HullBlock hull) {
                switch (hull.hullType()) {
                    case WALL -> result.wallCount++;
                    case BOTTOM -> result.bottomCount++;
                    case LID -> result.lidCount++;
                    case NOZZLE -> result.nozzleCount++;
                }
            }
        }
        Map<Integer, Integer> layerCells = new HashMap<>();
        for (BlockPos pos : result.cavity) {
            result.layerCapacity.merge(pos.getY(), 1.0, Double::sum);
            layerCells.merge(pos.getY(), 1, Integer::sum);
            result.capacity += 1.0;
        }
        for (BlockPos pos : result.coreCells) {
            result.layerCapacity.merge(pos.getY(), CORE_CELL_VOLUME, Double::sum);
            layerCells.merge(pos.getY(), 1, Integer::sum);
            result.capacity += CORE_CELL_VOLUME;
            result.coreMinY = Math.min(result.coreMinY, pos.getY());
            result.coreTopY = Math.max(result.coreTopY, pos.getY());
            BlockState state = state(level, pos);
            if (state != null) {
                if (state.is(VverRegistry.TVS_BLOCK.get()) && state.getValue(TvsBlock.PART) == 0) {
                    result.coreBases.add(pos.immutable());
                } else if (state.is(VverRegistry.SUZ_BLOCK.get()) && state.getValue(SuzBlock.PART) == 0) {
                    result.coreBases.add(pos.immutable());
                }
            }
        }
        for (int count : layerCells.values()) {
            result.maxLayerArea = Math.max(result.maxLayerArea, count);
        }
        result.lowestLeakY = Integer.MAX_VALUE;
        for (BlockPos leak : result.leakPositions) {
            result.lowestLeakY = Math.min(result.lowestLeakY, leak.getY());
        }
        if (result.sealed) {
            result.retainedCapacity = result.capacity;
        } else if (result.lowestLeakY == Integer.MAX_VALUE) {
            result.retainedCapacity = result.capacity;
        } else {
            double retained = 0.0;
            for (Map.Entry<Integer, Double> entry : result.layerCapacity.entrySet()) {
                if (entry.getKey() < result.lowestLeakY) {
                    retained += entry.getValue();
                }
            }
            result.retainedCapacity = retained;
        }
        result.anchor = chooseAnchor(result);
        computeBoundaryMetrics(level, result);
        result.minCoreClearance = computeCoreClearance(result);
        int cavityMaxY = Integer.MIN_VALUE;
        for (BlockPos pos : result.cavity) {
            cavityMaxY = Math.max(cavityMaxY, pos.getY());
        }
        result.headroom = result.coreCells.isEmpty() || cavityMaxY == Integer.MIN_VALUE ? 0 : Math.max(0, cavityMaxY - result.coreTopY);
        computeNozzlePerformance(level, result);
        result.pressureLimit = computePressureLimit(result);
    }

    private static BlockPos chooseAnchor(Result result) {
        BlockPos best = null;
        for (BlockPos pos : result.cavity) {
            if (best == null || comparePos(pos, best) < 0) {
                best = pos;
            }
        }
        if (best != null) {
            return best.immutable();
        }
        for (BlockPos pos : result.shell) {
            if (best == null || comparePos(pos, best) < 0) {
                best = pos;
            }
        }
        return best == null ? BlockPos.ZERO : best.immutable();
    }

    private static int comparePos(BlockPos a, BlockPos b) {
        int y = Integer.compare(a.getY(), b.getY());
        if (y != 0) {
            return y;
        }
        int z = Integer.compare(a.getZ(), b.getZ());
        if (z != 0) {
            return z;
        }
        return Integer.compare(a.getX(), b.getX());
    }

    private static void computeBoundaryMetrics(ServerLevel level, Result result) {
        int bottomBoundary = 0;
        int properBottom = 0;
        int lidBoundary = 0;
        int properLid = 0;
        int sideBoundary = 0;
        int samples = 0;
        int thicknessSum = 0;
        result.minThickness = Integer.MAX_VALUE;
        for (BlockPos pos : result.cavity) {
            for (Direction direction : Direction.values()) {
                BlockPos hullPos = pos.relative(direction);
                BlockState state = state(level, hullPos);
                if (state == null || !(state.getBlock() instanceof HullBlock hull)) {
                    continue;
                }
                if (direction == Direction.DOWN) {
                    bottomBoundary++;
                    if (hull.hullType() == HullType.BOTTOM) {
                        properBottom++;
                    }
                } else if (direction == Direction.UP) {
                    lidBoundary++;
                    if (hull.hullType() == HullType.LID) {
                        properLid++;
                    }
                } else {
                    sideBoundary++;
                }
                int thickness = 0;
                BlockPos cursor = hullPos;
                while (thickness < 4 && isHull(level, cursor)) {
                    thickness++;
                    cursor = cursor.relative(direction);
                }
                result.minThickness = Math.min(result.minThickness, thickness);
                thicknessSum += thickness;
                samples++;
            }
        }
        result.averageThickness = samples == 0 ? 0.0 : (double) thicknessSum / samples;
        if (result.minThickness == Integer.MAX_VALUE) {
            result.minThickness = 0;
        }
        boolean bottomOk = bottomBoundary == 0 || properBottom * 2 >= bottomBoundary;
        boolean lidOk = lidBoundary == 0 || properLid * 2 >= lidBoundary;
        result.roleComplete = bottomOk && lidOk && sideBoundary > 0;
        result.properBottomFraction = bottomBoundary == 0 ? 1.0 : (double) properBottom / bottomBoundary;
        result.properLidFraction = lidBoundary == 0 ? 1.0 : (double) properLid / lidBoundary;
    }

    private static int computeCoreClearance(Result result) {
        if (result.coreCells.isEmpty()) {
            return -1;
        }
        for (BlockPos core : result.coreCells) {
            for (Direction direction : Direction.values()) {
                if (result.shell.contains(core.relative(direction))) {
                    return 0;
                }
            }
        }
        Map<BlockPos, Integer> distances = new HashMap<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos cavity : result.cavity) {
            boolean nearCore = false;
            for (Direction direction : Direction.values()) {
                if (result.coreCells.contains(cavity.relative(direction))) {
                    nearCore = true;
                    break;
                }
            }
            if (nearCore) {
                distances.put(cavity, 1);
                queue.add(cavity);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();
            int distance = distances.getOrDefault(current, 1);
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (result.shell.contains(next)) {
                    return distance;
                }
                if (result.cavity.contains(next) && !distances.containsKey(next)) {
                    distances.put(next, distance + 1);
                    queue.add(next);
                }
            }
        }
        return -1;
    }

    private static double computePressureLimit(Result result) {
        double limit = 70.0;
        if (result.properBottomFraction < 0.5) {
            limit -= 5.0;
        }
        if (result.properLidFraction < 0.5) {
            limit -= 5.0;
        }
        if (result.nozzleCount > 4) {
            limit -= Math.min(5.0, result.nozzleCount - 4.0);
        }
        int spanX = result.hullMaxX - result.hullMinX + 1;
        int spanZ = result.hullMaxZ - result.hullMinZ + 1;
        int span = Math.max(spanX, spanZ);
        if (span > 24) {
            limit -= 20.0;
        } else if (span > 16) {
            limit -= 10.0;
        }
        double radius = Math.sqrt(Math.max(0, result.maxLayerArea) / Math.PI);
        if (radius > 7.0) {
            limit -= 5.0;
        }
        if (result.minThickness >= 2) {
            limit += 10.0;
        } else if (result.averageThickness >= 1.5) {
            limit += 5.0;
        }
        return Math.max(25.0, Math.min(70.0, limit));
    }

    private static boolean touchesExterior(BlockPos pos, Set<BlockPos> exterior) {
        for (Direction direction : Direction.values()) {
            if (exterior.contains(pos.relative(direction))) {
                return true;
            }
        }
        return false;
    }

    private static void addLeak(Result result, BlockPos pos, Direction direction) {
        if (result.leakPositions.add(pos)) {
            result.leakDirections.put(pos, direction);
        }
    }

    private static boolean inside(BlockPos pos, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private static BlockState state(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) ? level.getBlockState(pos) : null;
    }

    private static boolean isHull(ServerLevel level, BlockPos pos) {
        BlockState state = state(level, pos);
        return state != null && isHull(state);
    }

    private static boolean isHull(BlockState state) {
        return state.getBlock() instanceof HullBlock;
    }

    private static boolean isCore(ServerLevel level, BlockPos pos) {
        BlockState state = state(level, pos);
        return state != null && isCore(state);
    }

    private static boolean isCore(BlockState state) {
        return state.is(VverRegistry.TVS_BLOCK.get()) || state.is(VverRegistry.SUZ_BLOCK.get());
    }

    private static boolean isPassable(ServerLevel level, BlockPos pos) {
        BlockState state = state(level, pos);
        if (state == null || isHull(state) || isCore(state)) {
            return false;
        }
        return state.isAir() || !state.getFluidState().isEmpty() || state.getCollisionShape(level, pos).isEmpty();
    }

    public static final class Result {
        public final Set<BlockPos> shell = new HashSet<>();
        public final Set<BlockPos> cavity = new HashSet<>();
        public final Set<BlockPos> coreCells = new HashSet<>();
        public final Set<BlockPos> coreBases = new HashSet<>();
        public final Set<BlockPos> leakPositions = new HashSet<>();
        public final Map<BlockPos, Direction> leakDirections = new HashMap<>();
        public final Map<Integer, Double> layerCapacity = new HashMap<>();
        public boolean hullPresent;
        public boolean sealed;
        public boolean complete = true;
        public boolean roleComplete;
        public BlockPos anchor = BlockPos.ZERO;
        public double capacity;
        public double retainedCapacity;
        public double pressureLimit = 70.0;
        public double averageThickness;
        public double properBottomFraction = 1.0;
        public double properLidFraction = 1.0;
        public double nozzlePerformance;
        public double bestInletPotential;
        public double bestOutletPotential;
        public int wallCount;
        public int bottomCount;
        public int lidCount;
        public int nozzleCount;
        public int connectedNozzleCount;
        public int hullMinX;
        public int hullMinY;
        public int hullMinZ;
        public int hullMaxX;
        public int hullMaxY;
        public int hullMaxZ;
        public int coreMinY = Integer.MAX_VALUE;
        public int coreTopY = Integer.MIN_VALUE;
        public int lowestLeakY = Integer.MAX_VALUE;
        public int minThickness;
        public int maxLayerArea;
        public int minCoreClearance = -1;
        public int headroom;
    }
}
