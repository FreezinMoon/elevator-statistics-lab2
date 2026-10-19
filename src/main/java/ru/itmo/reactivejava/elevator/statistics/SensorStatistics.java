package ru.itmo.reactivejava.elevator.statistics;

import ru.itmo.reactivejava.elevator.domain.AlarmCode;

import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.EnumMap;
import java.util.Map;

public record SensorStatistics(
        long samples,
        double minLoadKg,
        double averageLoadKg,
        double maxLoadKg,
        double totalLoadKg,
        Map<AlarmCode, Long> alarmCounts
) {
    public SensorStatistics {
        EnumMap<AlarmCode, Long> copy = new EnumMap<>(AlarmCode.class);
        for (AlarmCode alarm : AlarmCode.values()) {
            copy.put(alarm, alarmCounts.getOrDefault(alarm, 0L));
        }
        alarmCounts = Collections.unmodifiableMap(copy);
    }

    public static SensorStatistics from(DoubleSummaryStatistics loads,
                                        Map<AlarmCode, Long> alarms) {
        return new SensorStatistics(loads.getCount(),
                loads.getCount() == 0 ? 0.0 : loads.getMin(),
                loads.getAverage(),
                loads.getCount() == 0 ? 0.0 : loads.getMax(),
                loads.getSum(), alarms);
    }

    public boolean approximatelyEquals(SensorStatistics other) {
        if (other == null || samples != other.samples || !alarmCounts.equals(other.alarmCounts)) {
            return false;
        }
        return close(minLoadKg, other.minLoadKg)
                && close(averageLoadKg, other.averageLoadKg)
                && close(maxLoadKg, other.maxLoadKg)
                && close(totalLoadKg, other.totalLoadKg);
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) <= 1e-9 * Math.max(1.0, Math.max(Math.abs(a), Math.abs(b)));
    }
}
