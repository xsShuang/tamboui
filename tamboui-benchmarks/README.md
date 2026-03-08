# TamboUI Performance Benchmarks

JMH (Java Microbenchmark Harness) benchmarks for measuring TamboUI performance.

## Running Benchmarks

### Quick Run (All Benchmarks)

```bash
./gradlew :tamboui-benchmarks:jmh
```

Results are saved to: `tamboui-benchmarks/build/reports/jmh/results.json`

### Run Specific Benchmark

```bash
# Run only buffer diff benchmarks
./gradlew :tamboui-benchmarks:jmh -Pjmh.includes='.*BufferDiff.*'

# Run only allocation profile benchmarks
./gradlew :tamboui-benchmarks:jmh -Pjmh.includes='.*AllocationProfile.*'
```

### Run with GC Profiler

To see allocation rates and GC statistics:

```bash
./gradlew :tamboui-benchmarks:jmh -Pjmh.profilers=gc
```

### Run with Specific Parameters

```bash
# Test only 80x24 terminal with 20% change rate
./gradlew :tamboui-benchmarks:jmh \
  -Pjmh.includes='BufferDiffBenchmark' \
  -Pjmh.params='terminalSize=80x24;changePercentage=20'
```

## Available Benchmarks

### BufferDiffBenchmark

Compares three approaches for buffer diffing:

1. **`diffWithAllocation`** - Allocates new `List<CellUpdate>` every frame (baseline)
2. **`diffWithListReuse`** - Reuses list but still allocates `CellUpdate` objects
3. **`diffWithDiffResult`** - Zero-allocation DoD approach with `DiffResult`

**Parameters:**
- `terminalSize`: Terminal dimensions (`80x24`, `160x50`, `240x80`)
- `changePercentage`: Percentage of cells changed per frame (`10`, `20`, `50`, `100`)

**Expected Results:**
- DoD approach should be 2-3x faster
- Zero allocations in steady state

### AllocationProfileBenchmark

Measures allocation rate and GC pressure under realistic workload.

**Metrics:**
- Throughput (ops/sec)
- Allocation rate (MB/sec)
- GC count
- GC time

**Expected Results:**
- Old approach: ~12 KB allocated per frame
- DoD approach: ~0 bytes allocated after warmup

## Understanding Results

### Throughput (ops/sec)

Higher is better. Measures how many diff operations can be performed per second.

### Average Time (ns)

Lower is better. Measures average time for a single diff operation.

### Allocation Rate (MB/sec)

Lower is better. Shows memory pressure on the GC.

**Example output with GC profiler:**

```
Benchmark                                  Mode  Cnt     Score   Error   Units
AllocationProfileBenchmark.allocatingDiff  thrpt   5  15000.0  ±100.0   ops/s
  ·gc.alloc.rate                           thrpt   5   720.5   ±10.2  MB/sec  ← High!
  ·gc.count                                thrpt   5    45.0            counts

AllocationProfileBenchmark.dodDiff         thrpt   5  45000.0  ±200.0   ops/s
  ·gc.alloc.rate                           thrpt   5     0.1    ±0.1  MB/sec  ← Zero!
  ·gc.count                                thrpt   5     1.0            counts
```

## Advanced Usage

### Build JMH JAR

```bash
./gradlew :tamboui-benchmarks:jmhJar
```

### Run JAR Directly

```bash
java -jar tamboui-benchmarks/build/libs/tamboui-benchmarks-jmh.jar \
  -prof gc \
  BufferDiffBenchmark
```

### Generate Flamegraphs

Requires [async-profiler](https://github.com/jvm-profiling-tools/async-profiler):

```bash
./gradlew :tamboui-benchmarks:jmh -Pjmh.profilers=async:libPath=/path/to/libasyncProfiler.so
```

### Export Results

```bash
# Export to CSV
./gradlew :tamboui-benchmarks:jmh -Pjmh.resultFormat=CSV

# Export to JSON
./gradlew :tamboui-benchmarks:jmh -Pjmh.resultFormat=JSON
```

## Benchmark Development

When adding new benchmarks:

1. Extend from `@State(Scope.Benchmark)` for shared state
2. Use `@Setup(Level.Trial)` for one-time setup
3. Use `@Setup(Level.Invocation)` for per-iteration setup
4. Always consume results with `Blackhole.consume()` to prevent DCE
5. Use `@Param` for parametric benchmarks

**Example:**

```java
@Benchmark
public void myBenchmark(Blackhole blackhole) {
    Object result = performOperation();
    blackhole.consume(result);  // Prevent dead code elimination
}
```

## Performance Targets

For the DoD optimization (80×24 terminal, 20% change rate):

- **Throughput:** 3x improvement over baseline
- **Allocation rate:** <1 MB/sec (vs ~700 MB/sec baseline)
- **GC overhead:** <1% (vs ~10% baseline)

## References

- [JMH Documentation](https://github.com/openjdk/jmh)
- [JMH Samples](https://github.com/openjdk/jmh/tree/master/jmh-samples/src/main/java/org/openjdk/jmh/samples)
- [Avoiding Benchmarking Pitfalls](https://www.oracle.com/technical-resources/articles/java/architect-benchmarking.html)
