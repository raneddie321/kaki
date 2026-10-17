package com.snakebrawl.myapp.game;

/** Uniform grid of snake segments, rebuilt every step, for fast proximity queries. */
final class SegGrid {
    static final float CELL = 96f;

    private final int cols;
    private final int[] cellStart;
    private final int[] cellCount;
    private final int[] cellFill;
    private int[] items = new int[4096];
    private int[] cellOf = new int[4096];

    SegGrid(float worldSize) {
        cols = (int) Math.ceil(worldSize / CELL) + 1;
        cellStart = new int[cols * cols + 1];
        cellCount = new int[cols * cols];
        cellFill = new int[cols * cols];
    }

    private int cellIndex(float x, float y) {
        int cx = (int) (x / CELL), cy = (int) (y / CELL);
        if (cx < 0) cx = 0;
        else if (cx >= cols) cx = cols - 1;
        if (cy < 0) cy = 0;
        else if (cy >= cols) cy = cols - 1;
        return cy * cols + cx;
    }

    void build(Snake[] snakes, int n) {
        int total = 0;
        for (int i = 0; i < n; i++) if (snakes[i].alive) total += snakes[i].segs;
        if (total > items.length) {
            items = new int[total * 2];
            cellOf = new int[total * 2];
        }
        java.util.Arrays.fill(cellCount, 0);
        int k = 0;
        for (int i = 0; i < n; i++) {
            Snake s = snakes[i];
            if (!s.alive) continue;
            for (int j = 0; j < s.segs; j++) {
                int c = cellIndex(s.sx[j], s.sy[j]);
                cellOf[k++] = c;
                cellCount[c]++;
            }
        }
        int sum = 0;
        for (int c = 0; c < cellCount.length; c++) {
            cellStart[c] = sum;
            cellFill[c] = sum;
            sum += cellCount[c];
        }
        cellStart[cellCount.length] = sum;
        k = 0;
        for (int i = 0; i < n; i++) {
            Snake s = snakes[i];
            if (!s.alive) continue;
            for (int j = 0; j < s.segs; j++) {
                int c = cellOf[k++];
                items[cellFill[c]++] = i * Snake.MAX_SEG + j;
            }
        }
    }

    /** Collects packed (snake * MAX_SEG + segment) ids in cells overlapping the circle. */
    int query(float x, float y, float r, int[] out) {
        int x0 = (int) ((x - r) / CELL), x1 = (int) ((x + r) / CELL);
        int y0 = (int) ((y - r) / CELL), y1 = (int) ((y + r) / CELL);
        if (x0 < 0) x0 = 0;
        if (y0 < 0) y0 = 0;
        if (x1 >= cols) x1 = cols - 1;
        if (y1 >= cols) y1 = cols - 1;
        int n = 0;
        for (int cy = y0; cy <= y1; cy++) {
            for (int cx = x0; cx <= x1; cx++) {
                int c = cy * cols + cx;
                int end = cellStart[c] + cellCount[c];
                for (int p = cellStart[c]; p < end && n < out.length; p++) out[n++] = items[p];
            }
        }
        return n;
    }
}
