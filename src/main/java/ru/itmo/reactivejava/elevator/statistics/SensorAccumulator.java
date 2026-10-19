package ru.itmo.reactivejava.elevator.statistics;

import ru.itmo.reactivejava.elevator.domain.AlarmCode;
import ru.itmo.reactivejava.elevator.domain.ElevatorTelemetry;

import java.util.DoubleSummaryStatistics;
import java.util.EnumMap;

final class SensorAccumulator {
    private final DoubleSummaryStatistics loads = new DoubleSummaryStatistics();
    private final EnumMap<AlarmCode, Long> alarms = new EnumMap<>(AlarmCode.class);

    void add(ElevatorTelemetry event, long delayNanos) {
        loads.accept(event.loadKg(delayNanos));
        for (AlarmCode alarm : event.activeAlarms()) {
            alarms.merge(alarm, 1L, Long::sum);
        }
    }

    void combine(SensorAccumulator other) {
        loads.combine(other.loads);
        other.alarms.forEach((alarm, count) -> alarms.merge(alarm, count, Long::sum));
    }

    SensorStatistics finish() {
        return SensorStatistics.from(loads, alarms);
    }
}
