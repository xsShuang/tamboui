/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.buffer;

import java.util.Arrays;

/**
 * A reusable container for buffer diff results using a Data-Oriented Design (DoD) layout.
 * <p>
 * This class uses separate arrays for x coordinates, y coordinates, and cells to achieve:
 * <ul>
 *   <li>Zero allocations per frame (after initial sizing)</li>
 *   <li>Better hardware prefetching during linear iteration</li>
 *   <li>Reduced GC pressure (no intermediate objects)</li>
 * </ul>
 * <p>
 * Typical usage pattern:
 * <pre>{@code
 * DiffResult diff = new DiffResult(1920);  // Pre-size for 80x24 terminal
 * while (running) {
 *     diff.clear();
 *     previousBuffer.diff(currentBuffer, diff);
 *     backend.draw(diff);
 * }
 * }</pre>
 *
 * @see Buffer#diff(Buffer, DiffResult)
 */
public final class DiffResult {

    private int[] xs;
    private int[] ys;
    private Cell[] cells;
    private int count;

    /**
     * Creates a new diff result with the default initial capacity (256 updates).
     */
    public DiffResult() {
        this(256);
    }

    /**
     * Creates a new diff result with the specified initial capacity.
     * <p>
     * For best performance, size this to match your expected maximum number of
     * cell updates per frame. A typical 80x24 terminal has 1,920 cells, so a
     * capacity of 1,920 ensures no reallocation even if every cell changes.
     *
     * @param initialCapacity the initial capacity for the parallel arrays
     */
    public DiffResult(int initialCapacity) {
        this.xs = new int[initialCapacity];
        this.ys = new int[initialCapacity];
        this.cells = new Cell[initialCapacity];
        this.count = 0;
    }

    /**
     * Clears this diff result, resetting the count to zero.
     */
    public void clear() {
        Arrays.fill(cells, 0, count, null);
        this.count = 0;
    }

    /**
     * Adds a cell update to this diff result.
     * <p>
     * If the internal arrays are full, they will be grown automatically (rare after warmup).
     *
     * @param x the x coordinate of the cell
     * @param y the y coordinate of the cell
     * @param cell the new cell value
     */
    public void add(int x, int y, Cell cell) {
        ensureCapacity(count + 1);
        xs[count] = x;
        ys[count] = y;
        cells[count] = cell;
        count++;
    }

    /**
     * Returns the number of cell updates in this result.
     *
     * @return the count of updates
     */
    public int size() {
        return count;
    }

    /**
     * Returns whether this diff result is empty.
     *
     * @return true if there are no updates
     */
    public boolean isEmpty() {
        return count == 0;
    }

    /**
     * Returns the x coordinate of the update at the given index.
     *
     * @param index the index (0 to size()-1)
     * @return the x coordinate
     */
    public int getX(int index) {
        return xs[index];
    }

    /**
     * Returns the y coordinate of the update at the given index.
     *
     * @param index the index (0 to size()-1)
     * @return the y coordinate
     */
    public int getY(int index) {
        return ys[index];
    }

    /**
     * Returns the cell at the given index.
     *
     * @param index the index (0 to size()-1)
     * @return the cell
     */
    public Cell getCell(int index) {
        return cells[index];
    }

    /**
     * Ensures the internal arrays can hold at least the specified capacity.
     * <p>
     * If the current capacity is insufficient, the arrays are grown by 50%.
     *
     * @param minCapacity the minimum required capacity
     */
    private void ensureCapacity(int minCapacity) {
        int currentCapacity = xs.length;
        if (minCapacity > currentCapacity) {
            int newCapacity = Math.max(minCapacity, currentCapacity + (currentCapacity >> 1));
            xs = Arrays.copyOf(xs, newCapacity);
            ys = Arrays.copyOf(ys, newCapacity);
            cells = Arrays.copyOf(cells, newCapacity);
        }
    }

    /**
     * Returns the current capacity of the internal arrays.
     *
     * @return the capacity
     */
    public int capacity() {
        return xs.length;
    }

    @Override
    public String toString() {
        return String.format("DiffResult[count=%d, capacity=%d]", count, xs.length);
    }
}
