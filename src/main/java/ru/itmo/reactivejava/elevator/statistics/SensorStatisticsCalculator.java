package ru.itmo.reactivejava.elevator.statistics;

import ru.itmo.reactivejava.elevator.domain.AlarmCode;
import ru.itmo.reactivejava.elevator.domain.ElevatorTelemetry;

import java.util.DoubleSummaryStatistics;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class SensorStatisticsCalculator implements AutoCloseable {
    private static final int CPU_CHUNK_SIZE = 256;
    private static final int WAITING_CHUNK_SIZE = 8;
    private final ForkJoinPool pool;

    public SensorStatisticsCalculator(int parallelism) {
        if (parallelism <= 0) throw new IllegalArgumentException("parallelism must be positive");
        pool = new ForkJoinPool(parallelism);
    }

    public Map<String, SensorStatistics> sequential(List<ElevatorTelemetry> events,
                                                     long delayNanos) {
        checkDelay(delayNanos);
        return events.stream().collect(Collectors.groupingBy(
                ElevatorTelemetry::sensorId, HashMap::new, sensorCollector(delayNanos)));
    }

    public ConcurrentMap<String, SensorStatistics> parallel(List<ElevatorTelemetry> events,
                                                             long delayNanos) {
        checkDelay(delayNanos);
        return events.parallelStream().collect(Collectors.groupingByConcurrent(
                ElevatorTelemetry::sensorId, ConcurrentHashMap::new,
                sensorCollector(delayNanos)));
    }

    public ConcurrentMap<String, SensorStatistics> forkJoin(List<ElevatorTelemetry> events,
                                                             long delayNanos) {
        checkDelay(delayNanos);
        Map<String, SensorAccumulator> partial =
                pool.invoke(new AccumulateTask(events, 0, events.size(), delayNanos));
        ConcurrentMap<String, SensorStatistics> result = new ConcurrentHashMap<>();
        partial.forEach((sensor, accumulator) -> result.put(sensor, accumulator.finish()));
        return result;
    }

    private static Collector<ElevatorTelemetry, ?, SensorStatistics> sensorCollector(
            long delayNanos) {
        return Collectors.teeing(
                Collectors.summarizingDouble(event -> event.loadKg(delayNanos)),
                Collectors.flatMapping(event -> event.activeAlarms().stream(),
                        Collectors.groupingBy(Function.identity(),
                                () -> new EnumMap<>(AlarmCode.class), Collectors.counting())),
                SensorStatistics::from);
    }

    private static void checkDelay(long delayNanos) {
        if (delayNanos < 0) throw new IllegalArgumentException("delayNanos must not be negative");
    }

    public static boolean sameStatistics(Map<String, SensorStatistics> first,
                                         Map<String, SensorStatistics> second) {
        if (!first.keySet().equals(second.keySet())) return false;
        for (String sensor : first.keySet()) {
            if (!first.get(sensor).approximatelyEquals(second.get(sensor))) return false;
        }
        return true;
    }

    @Override
    public void close() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(1, TimeUnit.MINUTES)) pool.shutdownNow();
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static final class AccumulateTask
            extends RecursiveTask<Map<String, SensorAccumulator>> {
        private final List<ElevatorTelemetry> events;
        private final int from;
        private final int to;
        private final long delayNanos;

        AccumulateTask(List<ElevatorTelemetry> events, int from, int to, long delayNanos) {
            this.events = events;
            this.from = from;
            this.to = to;
            this.delayNanos = delayNanos;
        }

        @Override
        protected Map<String, SensorAccumulator> compute() {
            int chunkSize = delayNanos == 0 ? CPU_CHUNK_SIZE : WAITING_CHUNK_SIZE;
            if (to - from <= chunkSize) {
                Map<String, SensorAccumulator> result = new HashMap<>();
                for (int i = from; i < to; i++) {
                    ElevatorTelemetry event = events.get(i);
                    result.computeIfAbsent(event.sensorId(), key -> new SensorAccumulator())
                            .add(event, delayNanos);
                }
                return result;
            }
            int middle = (from + to) >>> 1;
            AccumulateTask left = new AccumulateTask(events, from, middle, delayNanos);
            AccumulateTask right = new AccumulateTask(events, middle, to, delayNanos);
            left.fork();
            Map<String, SensorAccumulator> rightResult = right.compute();
            Map<String, SensorAccumulator> leftResult = left.join();
            leftResult.forEach((sensor, partial) ->
                    rightResult.merge(sensor, partial, (one, two) -> {
                        one.combine(two);
                        return one;
                    }));
            return rightResult;
        }
    }
}
