package ru.itmo.reactivejava.elevator.statistics;

import ru.itmo.reactivejava.elevator.domain.AlarmCode;
import ru.itmo.reactivejava.elevator.domain.ElevatorTelemetry;
import ru.itmo.reactivejava.elevator.domain.SensorKind;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.List;

public final class SensorReportWriter {
    private SensorReportWriter() {
    }

    public static void write(Path path, Map<String, SensorStatistics> statistics)
            throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("sensor_id,samples,min_kg,avg_kg,max_kg,total_kg");
            for (AlarmCode alarm : AlarmCode.values()) writer.write("," + alarm);
            writer.newLine();
            for (String sensor : statistics.keySet().stream().sorted().toList()) {
                SensorStatistics value = statistics.get(sensor);
                writer.write(String.format(Locale.ROOT, "%s,%d,%.3f,%.3f,%.3f,%.3f",
                        sensor, value.samples(), value.minLoadKg(), value.averageLoadKg(),
                        value.maxLoadKg(), value.totalLoadKg()));
                for (AlarmCode alarm : AlarmCode.values()) {
                    writer.write("," + value.alarmCounts().get(alarm));
                }
                writer.newLine();
            }
        }
    }

    public static void writeByKind(Path directory, List<ElevatorTelemetry> events,
                                   Map<String, SensorStatistics> statistics) throws IOException {
        Map<String, SensorKind> kindsBySensor = new HashMap<>();
        for (ElevatorTelemetry event : events) {
            SensorKind previous = kindsBySensor.putIfAbsent(event.sensorId(), event.sensorKind());
            if (previous != null && previous != event.sensorKind()) {
                throw new IllegalArgumentException("sensor kind changed: " + event.sensorId());
            }
        }
        Map<SensorKind, Map<String, SensorStatistics>> parts = new EnumMap<>(SensorKind.class);
        for (SensorKind kind : SensorKind.values()) parts.put(kind, new HashMap<>());
        for (Map.Entry<String, SensorStatistics> entry : statistics.entrySet()) {
            SensorKind kind = kindsBySensor.get(entry.getKey());
            if (kind == null) throw new IllegalArgumentException("unknown sensor: " + entry.getKey());
            parts.get(kind).put(entry.getKey(), entry.getValue());
        }
        for (SensorKind kind : SensorKind.values()) {
            write(directory.resolve(kind.name().toLowerCase(Locale.ROOT) + ".csv"),
                    parts.get(kind));
        }
    }
}
