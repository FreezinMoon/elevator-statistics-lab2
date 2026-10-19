package ru.itmo.reactivejava.elevator.jmh;

import org.openjdk.jmh.annotations.*;
import ru.itmo.reactivejava.elevator.statistics.SensorStatistics;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 700, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
public class NoDelayBenchmark {
    @State(Scope.Thread)
    public static class Data {
        @Param({"1", "2", "4", "8", "16", "32", "64", "128", "256", "512", "1024", "2048",
                "4096", "5000", "8192", "16384", "32768", "50000", "65536", "131072", "250000"})
        public int size;
        BenchmarkData input;

        @Setup(Level.Trial)
        public void setup() {
            input = new BenchmarkData(size);
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            input.close();
        }
    }

    @Benchmark
    public Map<String, SensorStatistics> sequential(Data data) {
        return data.input.calculator.sequential(data.input.events, 0);
    }

    @Benchmark
    public Map<String, SensorStatistics> parallel(Data data) {
        return data.input.calculator.parallel(data.input.events, 0);
    }

    @Benchmark
    public Map<String, SensorStatistics> forkJoin(Data data) {
        return data.input.calculator.forkJoin(data.input.events, 0);
    }
}
