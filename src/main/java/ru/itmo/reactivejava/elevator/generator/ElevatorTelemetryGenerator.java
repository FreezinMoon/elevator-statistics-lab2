package ru.itmo.reactivejava.elevator.generator;

import ru.itmo.reactivejava.elevator.domain.*;

import java.time.LocalDateTime;
import java.util.*;

public final class ElevatorTelemetryGenerator {
    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 8, 0);
    private final SplittableRandom random;

    public ElevatorTelemetryGenerator(long seed) {
        random = new SplittableRandom(seed);
    }

    public List<ElevatorTelemetry> generate(int count, List<Elevator> elevators,
                                            List<Building> buildings) {
        if (count < 0 || (count > 0 && elevators.isEmpty())) {
            throw new IllegalArgumentException("invalid number of events or no elevators");
        }
        Objects.requireNonNull(elevators);
        Objects.requireNonNull(buildings);

        Map<String, Building> buildingsById = new HashMap<>();
        for (Building building : buildings) {
            if (buildingsById.putIfAbsent(building.id(), building) != null) {
                throw new IllegalArgumentException("duplicate building: " + building.id());
            }
        }

        PriorityQueue<Source> queue = new PriorityQueue<>(
                Comparator.comparing(source -> source.nextTime));
        for (Elevator elevator : elevators) {
            Building building = buildingsById.get(elevator.buildingId());
            if (building == null) {
                throw new IllegalArgumentException("building not found: " + elevator.buildingId());
            }
            Source source = new Source(elevator, building,
                    random.nextInt(building.lowestFloor(), building.highestFloor() + 1),
                    START.plusNanos(random.nextLong(2_000_000_000L)));
            beginRide(source);
            queue.add(source);
        }

        List<ElevatorTelemetry> events = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Source source = queue.remove();
            boolean moving = source.remainingSamples > 0;
            ElevatorState state = moving
                    ? (source.direction > 0 ? ElevatorState.MOVING_UP : ElevatorState.MOVING_DOWN)
                    : stationaryState();
            double progress = moving
                    ? (source.totalSamples - source.remainingSamples + 1.0)
                            / (source.totalSamples + 1.0) : 0.0;
            int passengers = state == ElevatorState.MAINTENANCE
                    ? 0 : random.nextInt(source.elevator.maxPassengers() + 1);
            boolean overload = passengers > 0 && random.nextDouble() < 0.015;
            SensorKind kind = sensorKind(moving);
            double speed = moving
                    ? random.nextDouble(0.1, source.elevator.ratedSpeedMetersPerSecond()) : 0.0;

            events.add(new ElevatorTelemetry(index + 1L,
                    source.elevator.id() + "-" + kind, kind, source.nextTime,
                    state, new FloorPosition(source.floor, progress), alarms(state, overload),
                    passengers, loadKg(source.elevator, passengers, overload), speed,
                    source.elevator, source.building));

            if (moving) {
                source.remainingSamples--;
                if (source.remainingSamples == 0) {
                    source.floor += source.direction;
                    source.nextTime = source.nextTime.plusNanos(
                            random.nextLong(3_000_000_000L, 20_000_000_000L));
                } else {
                    source.nextTime = source.nextTime.plusNanos(
                            random.nextLong(20_000_000L, 120_000_000L));
                }
            } else if (random.nextDouble() < 0.15) {
                source.nextTime = source.nextTime.plusNanos(
                        random.nextLong(3_000_000_000L, 20_000_000_000L));
            } else {
                beginRide(source);
                source.nextTime = source.nextTime.plusNanos(
                        random.nextLong(1_000_000_000L, 6_000_000_000L));
            }
            queue.add(source);
        }
        return List.copyOf(events);
    }

    private void beginRide(Source source) {
        if (source.floor == source.building.lowestFloor()) source.direction = 1;
        else if (source.floor == source.building.highestFloor()) source.direction = -1;
        else source.direction = random.nextBoolean() ? 1 : -1;
        source.totalSamples = random.nextInt(8, 25);
        source.remainingSamples = source.totalSamples;
    }

    private ElevatorState stationaryState() {
        double chance = random.nextDouble();
        if (chance < 0.50) return ElevatorState.IDLE;
        if (chance < 0.94) return ElevatorState.DOORS_OPEN;
        if (chance < 0.99) return ElevatorState.MAINTENANCE;
        return ElevatorState.EMERGENCY;
    }

    private SensorKind sensorKind(boolean moving) {
        int choice = random.nextInt(100);
        if (moving) {
            if (choice < 60) return SensorKind.POSITION;
            if (choice < 85) return SensorKind.LOAD;
            return SensorKind.MOTOR;
        }
        if (choice < 60) return SensorKind.DOOR;
        if (choice < 85) return SensorKind.LOAD;
        return SensorKind.POSITION;
    }

    private double loadKg(Elevator elevator, int passengers, boolean overload) {
        if (overload) return elevator.maxLoadKg() * random.nextDouble(1.01, 1.13);
        if (passengers == 0) return 0.0;
        return Math.min(passengers * random.nextDouble(55.0, 95.0),
                elevator.maxLoadKg() * 0.98);
    }

    private EnumSet<AlarmCode> alarms(ElevatorState state, boolean overload) {
        EnumSet<AlarmCode> result = EnumSet.noneOf(AlarmCode.class);
        if (overload) result.add(AlarmCode.OVERLOAD);
        if (state == ElevatorState.DOORS_OPEN && random.nextDouble() < 0.02)
            result.add(AlarmCode.DOOR_BLOCKED);
        if ((state == ElevatorState.MOVING_UP || state == ElevatorState.MOVING_DOWN)
                && random.nextDouble() < 0.006) result.add(AlarmCode.MOTOR_OVERHEAT);
        if (random.nextDouble() < 0.002) result.add(AlarmCode.SENSOR_FAILURE);
        if (state == ElevatorState.EMERGENCY) result.add(AlarmCode.EMERGENCY_STOP);
        return result;
    }

    private static final class Source {
        final Elevator elevator;
        final Building building;
        int floor;
        LocalDateTime nextTime;
        int direction;
        int totalSamples;
        int remainingSamples;

        Source(Elevator elevator, Building building, int floor, LocalDateTime nextTime) {
            this.elevator = elevator;
            this.building = building;
            this.floor = floor;
            this.nextTime = nextTime;
        }
    }
}
