package ru.itmo.reactivejava.elevator.jmh;

import ru.itmo.reactivejava.elevator.domain.ElevatorTelemetry;
import ru.itmo.reactivejava.elevator.generator.BuildingGenerator;
import ru.itmo.reactivejava.elevator.generator.ElevatorGenerator;
import ru.itmo.reactivejava.elevator.generator.ElevatorTelemetryGenerator;
import ru.itmo.reactivejava.elevator.statistics.SensorStatisticsCalculator;

import java.util.List;

final class BenchmarkData {
    final List<ElevatorTelemetry> events;
    final SensorStatisticsCalculator calculator;

    BenchmarkData(int size) {
        var buildings = new BuildingGenerator(10_001L).generate(12);
        var elevators = new ElevatorGenerator(20_002L).generate(60, buildings);
        events = new ElevatorTelemetryGenerator(30_003L).generate(size, elevators, buildings);
        calculator = new SensorStatisticsCalculator(
                Math.max(2, Math.min(16, Runtime.getRuntime().availableProcessors())));
    }

    void close() {
        calculator.close();
    }
}
