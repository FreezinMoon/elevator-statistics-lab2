package ru.itmo.reactivejava.elevator;

import ru.itmo.reactivejava.elevator.domain.Building;
import ru.itmo.reactivejava.elevator.domain.Elevator;
import ru.itmo.reactivejava.elevator.domain.ElevatorState;
import ru.itmo.reactivejava.elevator.domain.ElevatorTelemetry;
import ru.itmo.reactivejava.elevator.generator.BuildingGenerator;
import ru.itmo.reactivejava.elevator.generator.ElevatorGenerator;
import ru.itmo.reactivejava.elevator.generator.ElevatorTelemetryGenerator;
import ru.itmo.reactivejava.elevator.statistics.SensorReportWriter;
import ru.itmo.reactivejava.elevator.statistics.SensorStatistics;
import ru.itmo.reactivejava.elevator.statistics.SensorStatisticsCalculator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class App {
    public static void main(String[] args) throws IOException {
        List<Building> buildings = new BuildingGenerator(10_001L).generate(12);
        List<Elevator> elevators = new ElevatorGenerator(20_002L).generate(60, buildings);
        List<ElevatorTelemetry> events = new ElevatorTelemetryGenerator(30_003L)
                .generate(5_000, elevators, buildings);

        try (SensorStatisticsCalculator calculator = new SensorStatisticsCalculator(
                Math.max(2, Math.min(16, Runtime.getRuntime().availableProcessors())))) {
            Map<String, SensorStatistics> sequential = calculator.sequential(events, 0);
            Map<String, SensorStatistics> parallel = calculator.parallel(events, 0);
            Map<String, SensorStatistics> forkJoin = calculator.forkJoin(events, 0);
            if (!SensorStatisticsCalculator.sameStatistics(sequential, parallel)
                    || !SensorStatisticsCalculator.sameStatistics(sequential, forkJoin)) {
                throw new IllegalStateException("Statistics differ");
            }

            Path report = Path.of("target", "reports", "all-sensors.csv");
            SensorReportWriter.write(report, parallel);
            SensorReportWriter.writeByKind(Path.of("target", "reports"), events, parallel);
            long moving = events.stream().filter(event ->
                    event.state() == ElevatorState.MOVING_UP
                            || event.state() == ElevatorState.MOVING_DOWN).count();
            System.out.println("Событий: " + events.size());
            System.out.println("Из них во время движения: " + moving);
            System.out.println("Отдельных сенсоров: " + parallel.size());
            System.out.println("Все три результата совпали.");
            System.out.println("Отчёты по сенсорам: " + report.toAbsolutePath().getParent());
            System.out.println("Замеры времени запускайте через JMH, инструкция в README.md");
        }
    }
}
