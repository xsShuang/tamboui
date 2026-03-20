/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.benchmarks;

import java.io.IOException;
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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.buffer.DiffResult;
import dev.tamboui.layout.Position;
import dev.tamboui.layout.Rect;
import dev.tamboui.layout.Size;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.AbstractBackend;

/**
 * Benchmark measuring the backend draw() path: DiffResult → ANSI escape sequences.
 * Uses a null backend that counts output bytes without actual I/O.
 * <p>
 * This isolates the cost of cursor positioning, style encoding, and ANSI output
 * generation — the part that follows diff calculation in the rendering pipeline.
 * <p>
 * Run with: ./gradlew :tamboui-benchmarks:jmh -Pjmh.includes='.*backendDraw.*'
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Benchmark)
public class BackendDrawBenchmark {

    @Param({"borderedPanel", "fullRedraw", "scattered5pct"})
    private String scenario;

    private DiffResult diffResult;
    private CountingBackend backend;
    private int diffSize;

    @Setup(Level.Trial)
    public void setup() {
        int width = 160, height = 50;
        Rect area = Rect.of(width, height);

        Buffer prev = Buffer.empty(area);
        Buffer curr = Buffer.empty(area);
        diffResult = new DiffResult(area.area());

        if ("borderedPanel".equals(scenario)) {
            // Bordered panel with text — simulates typical TUI app
            renderBorderedPanel(prev, width, height, "Hello world - this is line ");
            renderBorderedPanel(curr, width, height, "Updated content for line ");

        } else if ("fullRedraw".equals(scenario)) {
            // Full screen redraw — first frame or RGB-style animation
            for (int y = 0; y < height; y++) {
                StringBuilder sb = new StringBuilder(width);
                for (int x = 0; x < width; x++) {
                    sb.append((char) ('a' + ((x + y) % 26)));
                }
                curr.setString(0, y, sb.toString(), Style.EMPTY);
            }

        } else if ("scattered5pct".equals(scenario)) {
            // Scattered changes — cursor blink, status updates
            String line = buildRepeat("a", width);
            for (int y = 0; y < height; y++) {
                prev.setString(0, y, line, Style.EMPTY);
                curr.setString(0, y, line, Style.EMPTY);
            }
            java.util.Random rand = new java.util.Random(42);
            int totalCells = width * height;
            int numChanges = totalCells * 5 / 100;
            java.util.List<Integer> indices = new java.util.ArrayList<>(totalCells);
            for (int i = 0; i < totalCells; i++) indices.add(i);
            java.util.Collections.shuffle(indices, rand);
            for (int i = 0; i < numChanges; i++) {
                int idx = indices.get(i);
                curr.setString(idx % width, idx / width, "X", Style.EMPTY.fg(Color.RED));
            }
        }

        prev.diff(curr, diffResult);
        diffSize = diffResult.size();
        backend = new CountingBackend(width, height);

        System.out.println("\n=== BackendDraw: " + scenario + " (" + diffSize + " diffs) ===\n");
    }

    @Benchmark
    public void backendDraw(Blackhole blackhole) throws IOException {
        backend.reset();
        backend.draw(diffResult);
        blackhole.consume(backend.bytesWritten);
        blackhole.consume(backend.writeCount);
    }

    private void renderBorderedPanel(Buffer buffer, int width, int height, String linePrefix) {
        Style borderStyle = Style.EMPTY.fg(Color.WHITE);
        Cell hBorder = new Cell("─", borderStyle);
        Cell vBorder = new Cell("│", borderStyle);

        buffer.set(0, 0, new Cell("┌", borderStyle));
        buffer.set(width - 1, 0, new Cell("┐", borderStyle));
        buffer.set(0, height - 1, new Cell("└", borderStyle));
        buffer.set(width - 1, height - 1, new Cell("┘", borderStyle));
        for (int x = 1; x < width - 1; x++) {
            buffer.set(x, 0, hBorder);
            buffer.set(x, height - 1, hBorder);
        }
        for (int y = 1; y < height - 1; y++) {
            buffer.set(0, y, vBorder);
            buffer.set(width - 1, y, vBorder);
        }

        int innerWidth = width - 2;
        for (int y = 1; y < height - 1; y++) {
            String text = linePrefix + y;
            if (text.length() < innerWidth) {
                text = text + buildRepeat(" ", innerWidth - text.length());
            } else {
                text = text.substring(0, innerWidth);
            }
            buffer.setString(1, y, text, Style.EMPTY);
        }
    }

    private static String buildRepeat(String s, int count) {
        StringBuilder sb = new StringBuilder(s.length() * count);
        for (int i = 0; i < count; i++) sb.append(s);
        return sb.toString();
    }

    /**
     * Minimal backend that counts bytes written without real I/O.
     */
    static class CountingBackend extends AbstractBackend {
        int bytesWritten;
        int writeCount;
        private final int width;
        private final int height;

        CountingBackend(int width, int height) {
            this.width = width;
            this.height = height;
        }

        void reset() {
            bytesWritten = 0;
            writeCount = 0;
        }

        @Override
        public void writeRaw(String data) {
            bytesWritten += data.length();
            writeCount++;
        }

        @Override
        public void writeRaw(byte[] data) {
            bytesWritten += data.length;
            writeCount++;
        }

        @Override
        public void flush() { }

        @Override
        public Size size() { return new Size(width, height); }

        @Override
        public void clear() { }

        @Override
        public void showCursor() { }

        @Override
        public void hideCursor() { }

        @Override
        public Position getCursorPosition() { return new Position(0, 0); }

        @Override
        public void enableRawMode() { }

        @Override
        public void disableRawMode() { }

        @Override
        public void enterAlternateScreen() { }

        @Override
        public void leaveAlternateScreen() { }

        @Override
        public int read(int timeoutMs) { return -1; }

        @Override
        public int peek(int timeoutMs) { return -1; }

        @Override
        public void onResize(Runnable handler) { }

        @Override
        public void close() { }
    }
}
