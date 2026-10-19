package ru.itmo.reactivejava.elevator;

import ru.itmo.reactivejava.elevator.domain.ElevatorState;
import ru.itmo.reactivejava.elevator.domain.ElevatorTelemetry;
import ru.itmo.reactivejava.elevator.generator.BuildingGenerator;
import ru.itmo.reactivejava.elevator.generator.ElevatorGenerator;
import ru.itmo.reactivejava.elevator.generator.ElevatorTelemetryGenerator;
import ru.itmo.reactivejava.elevator.statistics.SensorStatistics;
import ru.itmo.reactivejava.elevator.statistics.SensorStatisticsCalculator;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StatisticsSelfCheck {
    public static void main(String[] args) {
        var buildings = new BuildingGenerator(10_001L).generate(12);
        var elevators = new ElevatorGenerator(20_002L).generate(60, buildings);
        var generator = new ElevatorTelemetryGenerator(30_003L);
        List<ElevatorTelemetry> events = generator.generate(50_000, elevators, buildings);
        check(events.size() == 50_000, "wrong event count");

        long moving = 0;
        long closeMovementPairs = 0;
        long slowStationaryPairs = 0;
        Map<String, ElevatorTelemetry> lastByElevator = new HashMap<>();
        for (int i = 0; i < events.size(); i++) {
            ElevatorTelemetry event = events.get(i);
            check(event.eventId() == i + 1L, "event IDs not unique");
            if (i > 0) {
                check(!event.capturedAt().isBefore(events.get(i - 1).capturedAt()),
                        "events out of order");
            }
            ElevatorTelemetry previous = lastByElevator.put(event.elevator().id(), event);
            if (event.state() == ElevatorState.MOVING_UP
                    || event.state() == ElevatorState.MOVING_DOWN) {
                moving++;
                if (previous != null && previous.state() == event.state()
                        && Duration.between(previous.capturedAt(), event.capturedAt()).toMillis() < 150) {
                    closeMovementPairs++;
                }
            } else if (previous != null
                    && Duration.between(previous.capturedAt(), event.capturedAt()).toSeconds() >= 1) {
                slowStationaryPairs++;
            }
        }
        check(moving > 40_000 && closeMovementPairs > 25_000 && slowStationaryPairs > 100,
                "no movement bursts or stationary gaps");

        try (SensorStatisticsCalculator calculator = new SensorStatisticsCalculator(4)) {
            Map<String, SensorStatistics> sequential = calculator.sequential(events, 0);
            Map<String, SensorStatistics> parallel = calculator.parallel(events, 0);
            Map<String, SensorStatistics> forkJoin = calculator.forkJoin(events, 0);
            check(parallel instanceof ConcurrentHashMap, "parallel result not concurrent");
            check(forkJoin instanceof ConcurrentHashMap, "forkJoin result not concurrent");
            check(SensorStatisticsCalculator.sameStatistics(sequential, parallel), "parallel mismatch");
            check(SensorStatisticsCalculator.sameStatistics(sequential, forkJoin), "forkJoin mismatch");
            check(sequential.size() > 60, "events were not separated by sensor");
            check(sequential.values().stream().mapToLong(SensorStatistics::samples).sum()
                    == events.size(), "samples lost");

            List<ElevatorTelemetry> small = events.subList(0, 128);
            check(SensorStatisticsCalculator.sameStatistics(
                    calculator.sequential(small, 50_000), calculator.parallel(small, 50_000)),
                    "delayed parallel mismatch");
            check(SensorStatisticsCalculator.sameStatistics(
                    calculator.sequential(small, 50_000), calculator.forkJoin(small, 50_000)),
                    "delayed forkJoin mismatch");
        }
        System.out.println("OK: bursts, timestamps, sensor groups and all three calculations");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
