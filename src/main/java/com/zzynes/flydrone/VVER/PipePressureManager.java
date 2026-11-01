package com.zzynes.flydrone.VVER;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PipePressureManager {
    public static final double ATMOSPHERIC_PRESSURE_ATM = 1.0;
    private static final int MAX_PIPE_CELLS = 4096;
    private static final int MAX_ENDPOINTS = 256;
    private static final double PIPE_VOLUME = 1.0;
    private static final double GCN_VOLUME = 3.0;

    private PipePressureManager() {
    }

    public static boolean adjustFromPipe(ServerLevel level, BlockPos pos, double deltaAtm) {
        if (!PipePorts.isPipe(level.getBlockState(pos))) {
            return false;
        }
        Network network = scan(level, pos.immutable(), null);
        return adjustNetwork(level, network, deltaAtm);
    }

    public static boolean adjustFromEndpointBlock(ServerLevel level, BlockPos pos, double deltaAtm) {
        EndpointKey key = endpointKeyAtBlock(level, pos);
        if (key == null) {
            return false;
        }
        Network network = scan(level, null, key);
        return adjustNetwork(level, network, deltaAtm);
    }

    public static void reconcileSteamGenerator(ServerLevel level, SteamGeneratorBlockEntity blockEntity) {
        for (SteamGeneratorBlock.Circuit circuit : SteamGeneratorBlock.Circuit.values()) {
            EndpointKey key = new EndpointKey(
                    circuit == SteamGeneratorBlock.Circuit.PRIMARY ? EndpointKind.STEAM_PRIMARY : EndpointKind.STEAM_SECONDARY,
                    blockEntity.getBlockPos().asLong()
            );
            Network network = scan(level, null, key);
            if (!network.complete || !network.endpoints.contains(key)) {
                continue;
            }
            List<EndpointSnapshot> snapshots = readEndpoints(level, network);
            double pressure = networkPressure(level, network, snapshots);
            double target = pressure;
            if (network.openEnds > 0) {
                double retained = Math.pow(0.65, Math.min(4, network.openEnds));
                target = ATMOSPHERIC_PRESSURE_ATM + (pressure - ATMOSPHERIC_PRESSURE_ATM) * retained;
            }
            setNetworkPressure(level, network, snapshots, target);
        }
    }

    private static boolean adjustNetwork(ServerLevel level, Network network, double deltaAtm) {
        if (!network.complete || (network.pipes.isEmpty() && network.endpoints.isEmpty())) {
            return false;
        }
        List<EndpointSnapshot> snapshots = readEndpoints(level, network);
        double current = networkPressure(level, network, snapshots);
        double target = Mth.clamp(current + deltaAtm, 0.0, SteamGeneratorBlockEntity.MAX_PRESSURE_ATM);
        setNetworkPressure(level, network, snapshots, target);
        return true;
    }

    private static Network scan(ServerLevel level, @Nullable BlockPos startPipe, @Nullable EndpointKey startEndpoint) {
        Network network = new Network();
        ArrayDeque<BlockPos> pipeQueue = new ArrayDeque<>();
        ArrayDeque<EndpointKey> endpointQueue = new ArrayDeque<>();
        if (startPipe != null) {
            addPipe(network, pipeQueue, startPipe);
        }
        if (startEndpoint != null) {
            addEndpoint(network, endpointQueue, startEndpoint);
        }
        while (!pipeQueue.isEmpty() || !endpointQueue.isEmpty()) {
            if (network.pipes.size() > MAX_PIPE_CELLS || network.endpoints.size() > MAX_ENDPOINTS) {
                network.complete = false;
                break;
            }
            if (!pipeQueue.isEmpty()) {
                expandPipe(level, network, pipeQueue.removeFirst(), pipeQueue, endpointQueue);
            } else {
                expandEndpoint(level, network, endpointQueue.removeFirst(), pipeQueue, endpointQueue);
            }
        }
        return network;
    }

    private static void expandPipe(ServerLevel level, Network network, BlockPos pos, ArrayDeque<BlockPos> pipeQueue, ArrayDeque<EndpointKey> endpointQueue) {
        if (!level.isLoaded(pos)) {
            network.complete = false;
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!PipePorts.isPipe(state)) {
            network.complete = false;
            return;
        }
        for (Direction direction : Direction.values()) {
            if (!PipePorts.hasPipePort(state, direction)) {
                continue;
            }
            BlockPos neighbor = pos.relative(direction);
            if (!level.isLoaded(neighbor)) {
                network.complete = false;
                continue;
            }
            BlockState neighborState = level.getBlockState(neighbor);
            if (PipePorts.isPipe(neighborState) && PipePorts.hasPipePort(neighborState, direction.getOpposite())) {
                addPipe(network, pipeQueue, neighbor);
                continue;
            }
            EndpointPort endpoint = endpointPortAt(level, neighbor, neighborState, direction.getOpposite());
            if (endpoint != null) {
                addEndpoint(network, endpointQueue, endpoint.key());
            } else if (isOpenBoundary(level, neighbor, neighborState)) {
                network.openEnds++;
            }
        }
    }

    private static void expandEndpoint(ServerLevel level, Network network, EndpointKey key, ArrayDeque<BlockPos> pipeQueue, ArrayDeque<EndpointKey> endpointQueue) {
        List<EndpointPort> ports = endpointPorts(level, key);
        if (ports.isEmpty()) {
            network.complete = false;
            return;
        }
        for (EndpointPort port : ports) {
            BlockPos external = port.occupiedPos().relative(port.outward());
            if (!level.isLoaded(external)) {
                network.complete = false;
                continue;
            }
            BlockState externalState = level.getBlockState(external);
            if (PipePorts.isPipe(externalState) && PipePorts.hasPipePort(externalState, port.outward().getOpposite())) {
                addPipe(network, pipeQueue, external);
                continue;
            }
            EndpointPort other = endpointPortAt(level, external, externalState, port.outward().getOpposite());
            if (other != null && !other.key().equals(key)) {
                addEndpoint(network, endpointQueue, other.key());
            } else if (key.kind() != EndpointKind.REACTOR && isOpenBoundary(level, external, externalState)) {
                network.openEnds++;
            }
        }
    }

    private static void addPipe(Network network, ArrayDeque<BlockPos> queue, BlockPos pos) {
        BlockPos immutable = pos.immutable();
        if (network.pipes.add(immutable)) {
            queue.addLast(immutable);
        }
    }

    private static void addEndpoint(Network network, ArrayDeque<EndpointKey> queue, EndpointKey key) {
        if (network.endpoints.add(key)) {
            queue.addLast(key);
        }
    }

    @Nullable
    private static EndpointKey endpointKeyAtBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SteamGeneratorBlock) {
            SteamGeneratorBlock.Port port = SteamGeneratorBlock.portAt(pos, state);
            if (port == null) {
                return null;
            }
            BlockPos mainPos = SteamGeneratorBlock.mainPos(pos, state);
            return new EndpointKey(
                    port.circuit() == SteamGeneratorBlock.Circuit.PRIMARY ? EndpointKind.STEAM_PRIMARY : EndpointKind.STEAM_SECONDARY,
                    mainPos.asLong()
            );
        }
        if (state.getBlock() instanceof GcnBlock) {
            return new EndpointKey(EndpointKind.GCN, GcnBlock.fluidMainPos(pos, state).asLong());
        }
        if (state.getBlock() instanceof HullNozzleBlock) {
            return new EndpointKey(EndpointKind.REACTOR, pos.asLong());
        }
        return null;
    }

    @Nullable
    private static EndpointPort endpointPortAt(ServerLevel level, BlockPos pos, BlockState state, Direction expectedOutward) {
        if (state.getBlock() instanceof SteamGeneratorBlock) {
            SteamGeneratorBlock.Port port = SteamGeneratorBlock.portAt(pos, state);
            if (port == null || port.outward() != expectedOutward) {
                return null;
            }
            BlockPos mainPos = SteamGeneratorBlock.mainPos(pos, state);
            EndpointKey key = new EndpointKey(
                    port.circuit() == SteamGeneratorBlock.Circuit.PRIMARY ? EndpointKind.STEAM_PRIMARY : EndpointKind.STEAM_SECONDARY,
                    mainPos.asLong()
            );
            return new EndpointPort(key, pos.immutable(), expectedOutward);
        }
        if (state.getBlock() instanceof GcnBlock && GcnBlock.hasFluidPort(state, expectedOutward)) {
            EndpointKey key = new EndpointKey(EndpointKind.GCN, GcnBlock.fluidMainPos(pos, state).asLong());
            return new EndpointPort(key, pos.immutable(), expectedOutward);
        }
        if (state.getBlock() instanceof HullNozzleBlock && state.getValue(HullNozzleBlock.FACING) == expectedOutward) {
            return new EndpointPort(new EndpointKey(EndpointKind.REACTOR, pos.asLong()), pos.immutable(), expectedOutward);
        }
        return null;
    }

    private static List<EndpointPort> endpointPorts(ServerLevel level, EndpointKey key) {
        List<EndpointPort> ports = new ArrayList<>();
        BlockPos mainPos = BlockPos.of(key.mainPos());
        if (!level.isLoaded(mainPos)) {
            return ports;
        }
        BlockState state = level.getBlockState(mainPos);
        switch (key.kind()) {
            case STEAM_PRIMARY, STEAM_SECONDARY -> {
                if (!(state.getBlock() instanceof SteamGeneratorBlock) || state.getValue(SteamGeneratorBlock.PART) != SteamGeneratorBlock.MAIN_PART) {
                    return ports;
                }
                SteamGeneratorBlock.Circuit circuit = key.kind() == EndpointKind.STEAM_PRIMARY
                        ? SteamGeneratorBlock.Circuit.PRIMARY
                        : SteamGeneratorBlock.Circuit.SECONDARY;
                for (SteamGeneratorBlock.Port port : SteamGeneratorBlock.ports(mainPos, state, circuit)) {
                    ports.add(new EndpointPort(key, port.occupiedPos(), port.outward()));
                }
            }
            case GCN -> {
                if (!(state.getBlock() instanceof GcnBlock) || state.getValue(GcnBlock.PART) != GcnBlock.PART_MAIN) {
                    return ports;
                }
                Direction facing = GcnBlock.inletDirection(state);
                BlockPos frontPos = mainPos.relative(facing);
                ports.add(new EndpointPort(key, frontPos, facing));
                ports.add(new EndpointPort(key, frontPos, GcnBlock.outletDirection(state)));
            }
            case REACTOR -> {
                if (state.getBlock() instanceof HullNozzleBlock) {
                    ports.add(new EndpointPort(key, mainPos.immutable(), state.getValue(HullNozzleBlock.FACING)));
                }
            }
        }
        return ports;
    }

    private static boolean isOpenBoundary(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || PipePorts.isPipe(state)) {
            return true;
        }
        return state.canBeReplaced() || state.getCollisionShape(level, pos).isEmpty();
    }

    private static List<EndpointSnapshot> readEndpoints(ServerLevel level, Network network) {
        List<EndpointSnapshot> snapshots = new ArrayList<>();
        PipePressureData data = PipePressureData.get(level);
        ReactorVesselData reactorData = null;
        for (EndpointKey key : network.endpoints) {
            BlockPos mainPos = BlockPos.of(key.mainPos());
            switch (key.kind()) {
                case STEAM_PRIMARY -> {
                    SteamGeneratorBlockEntity blockEntity = steamGeneratorAt(level, mainPos);
                    double pressure = blockEntity != null ? blockEntity.getCircuitPressureAtm(SteamGeneratorBlock.Circuit.PRIMARY) : ATMOSPHERIC_PRESSURE_ATM;
                    snapshots.add(new EndpointSnapshot(key, pressure, SteamGeneratorBlockEntity.PRIMARY_VOLUME, blockEntity, null, null));
                }
                case STEAM_SECONDARY -> {
                    SteamGeneratorBlockEntity blockEntity = steamGeneratorAt(level, mainPos);
                    double pressure = blockEntity != null ? blockEntity.getCircuitPressureAtm(SteamGeneratorBlock.Circuit.SECONDARY) : ATMOSPHERIC_PRESSURE_ATM;
                    snapshots.add(new EndpointSnapshot(key, pressure, SteamGeneratorBlockEntity.SECONDARY_VOLUME, blockEntity, null, null));
                }
                case GCN -> snapshots.add(new EndpointSnapshot(key, data.getEndpointPressureAtm(mainPos), GCN_VOLUME, null, null, null));
                case REACTOR -> {
                    if (reactorData == null) {
                        reactorData = ReactorVesselData.get(level);
                    }
                    ReactorVesselData.Vessel vessel = reactorData.findByShell(mainPos);
                    double pressure = vessel != null ? vessel.pressure() : ATMOSPHERIC_PRESSURE_ATM;
                    double volume = vessel != null ? Math.max(10.0, vessel.capacity()) : 1.0;
                    snapshots.add(new EndpointSnapshot(key, pressure, volume, null, reactorData, vessel));
                }
            }
        }
        return snapshots;
    }

    @Nullable
    private static SteamGeneratorBlockEntity steamGeneratorAt(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof SteamGeneratorBlockEntity steamGenerator ? steamGenerator : null;
    }

    private static double networkPressure(ServerLevel level, Network network, List<EndpointSnapshot> snapshots) {
        PipePressureData data = PipePressureData.get(level);
        double weighted = 0.0;
        double volume = 0.0;
        for (BlockPos pos : network.pipes) {
            weighted += data.getPipePressureAtm(pos) * PIPE_VOLUME;
            volume += PIPE_VOLUME;
        }
        for (EndpointSnapshot snapshot : snapshots) {
            weighted += snapshot.pressure * snapshot.volume;
            volume += snapshot.volume;
        }
        return volume <= 0.0 ? ATMOSPHERIC_PRESSURE_ATM : Mth.clamp(weighted / volume, 0.0, SteamGeneratorBlockEntity.MAX_PRESSURE_ATM);
    }

    private static void setNetworkPressure(ServerLevel level, Network network, List<EndpointSnapshot> snapshots, double targetPressure) {
        double target = Mth.clamp(targetPressure, 0.0, SteamGeneratorBlockEntity.MAX_PRESSURE_ATM);
        PipePressureData data = PipePressureData.get(level);
        for (BlockPos pos : network.pipes) {
            data.setPipePressureAtm(pos, target);
        }
        for (EndpointSnapshot snapshot : snapshots) {
            BlockPos mainPos = BlockPos.of(snapshot.key.mainPos());
            switch (snapshot.key.kind()) {
                case STEAM_PRIMARY -> {
                    if (snapshot.steamGenerator != null) {
                        snapshot.steamGenerator.setCircuitPressureAtm(SteamGeneratorBlock.Circuit.PRIMARY, target);
                    }
                }
                case STEAM_SECONDARY -> {
                    if (snapshot.steamGenerator != null) {
                        snapshot.steamGenerator.setCircuitPressureAtm(SteamGeneratorBlock.Circuit.SECONDARY, target);
                    }
                }
                case GCN -> data.setEndpointPressureAtm(mainPos, target);
                case REACTOR -> {
                    if (snapshot.reactorData != null && snapshot.vessel != null) {
                        snapshot.reactorData.adjustPressure(snapshot.vessel, target - snapshot.vessel.pressure());
                    }
                }
            }
        }
    }

    private enum EndpointKind {
        STEAM_PRIMARY,
        STEAM_SECONDARY,
        GCN,
        REACTOR
    }

    private record EndpointKey(EndpointKind kind, long mainPos) {
    }

    private record EndpointPort(EndpointKey key, BlockPos occupiedPos, Direction outward) {
    }

    private static final class Network {
        private final Set<BlockPos> pipes = new LinkedHashSet<>();
        private final Set<EndpointKey> endpoints = new LinkedHashSet<>();
        private int openEnds;
        private boolean complete = true;
    }

    private static final class EndpointSnapshot {
        private final EndpointKey key;
        private final double pressure;
        private final double volume;
        @Nullable
        private final SteamGeneratorBlockEntity steamGenerator;
        @Nullable
        private final ReactorVesselData reactorData;
        @Nullable
        private final ReactorVesselData.Vessel vessel;

        private EndpointSnapshot(EndpointKey key, double pressure, double volume, @Nullable SteamGeneratorBlockEntity steamGenerator, @Nullable ReactorVesselData reactorData, @Nullable ReactorVesselData.Vessel vessel) {
            this.key = key;
            this.pressure = pressure;
            this.volume = volume;
            this.steamGenerator = steamGenerator;
            this.reactorData = reactorData;
            this.vessel = vessel;
        }
    }
}
