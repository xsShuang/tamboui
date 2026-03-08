/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.terminal;

import java.io.IOException;

import dev.tamboui.buffer.Cell;
import dev.tamboui.buffer.DiffResult;
import dev.tamboui.error.RuntimeIOException;
import dev.tamboui.layout.Position;

/**
 * Base class for terminal backends that produce ANSI output.
 * <p>
 * Provides final implementation of {@link #draw(DiffResult)} and
 * {@link #setCursorPosition(Position)} so that all concrete backends
 * share a single, consistent rendering path through {@link AnsiCellWriter}.
 * <p>
 * Subclasses must implement the raw I/O primitives ({@link #writeRaw(String)},
 * {@link #flush()}, etc.) but cannot override the drawing or cursor-positioning
 * logic.
 *
 * @see AnsiCellWriter
 */
public abstract class AbstractBackend implements Backend {

    /**
     * Creates a new abstract backend.
     */
    protected AbstractBackend() {
    }

    /**
     * Draws the given cell updates to the terminal using Data-Oriented Design.
     * <p>
     * Iterates over the parallel arrays in {@link DiffResult}, positions the cursor
     * for each cell, and writes styled content using {@link AnsiCellWriter}.
     * This method achieves zero allocations after the initial {@link DiffResult} warmup.
     * <p>
     * Output is sent via {@link #writeRaw(String)}.
     *
     * @param diff the diff result containing cell updates in Structure-of-Arrays format
     * @throws IOException if drawing fails
     */
    @Override
    public final void draw(DiffResult diff) throws IOException {
        try (AnsiCellWriter cellWriter = new AnsiCellWriter(s -> {
            try {
                writeRaw(s);
            } catch (IOException e) {
                throw new RuntimeIOException("Failed to write cell data", e);
            }
        })) {
            // Linear scan over parallel arrays - cache-friendly access pattern
            for (int i = 0; i < diff.size(); i++) {
                Cell cell = diff.getCell(i);
                if (cell.isContinuation()) {
                    continue;
                }
                int x = diff.getX(i);
                int y = diff.getY(i);
                // ANSI uses 1-based coordinates
                writeRaw("\u001b[" + (y + 1) + ";" + (x + 1) + "H");
                cellWriter.writeCell(cell);
            }
        }
    }

    /**
     * Sets the cursor to the given position and flushes.
     *
     * @param position the position to set the cursor to
     * @throws IOException if the operation fails
     */
    @Override
    public final void setCursorPosition(Position position) throws IOException {
        // ANSI uses 1-based coordinates
        writeRaw("\u001b[" + (position.y() + 1) + ";" + (position.x() + 1) + "H");
        flush();
    }
}
