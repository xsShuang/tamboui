/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.benchmarks;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import dev.tamboui.buffer.Cell;
import dev.tamboui.buffer.DiffResult;
import dev.tamboui.style.Style;

/**
 * Benchmark measuring only the iteration/consumption cost of DiffResult.
 * <p>
 * Pre-fills a DiffResult during setup and measures the cost of reading
 * x, y, cell values - isolating the array layout impact from diff cost.
 * <p>
 * Run with: ./gradlew :tamboui-benchmarks:jmh -Pjmh.includes='.*DiffResultIteration.*'
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Benchmark)
public class DiffResultIterationBenchmark {

    @Param({"1920", "8000", "16080", "32000"})
    private int diffSize;

    private DiffResult diffResult;

    @Setup(Level.Trial)
    public void setup() {
        diffResult = new DiffResult(diffSize);
        Random rand = new Random(42);
        Cell[] sampleCells = {
            new Cell("a", Style.EMPTY),
            new Cell("b", Style.EMPTY),
            new Cell("x", Style.EMPTY),
            new Cell("Z", Style.EMPTY),
        };
        for (int i = 0; i < diffSize; i++) {
            int x = rand.nextInt(320);
            int y = rand.nextInt(100);
            diffResult.add(x, y, sampleCells[i & 3]);
        }
    }

    @Benchmark
    public void iterateAndConsume(Blackhole blackhole) {
        int size = diffResult.size();
        for (int i = 0; i < size; i++) {
            int x = diffResult.getX(i);
            int y = diffResult.getY(i);
            Cell cell = diffResult.getCell(i);
            blackhole.consume(x);
            blackhole.consume(y);
            blackhole.consume(cell);
        }
    }
}
