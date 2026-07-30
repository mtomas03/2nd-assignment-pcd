package pcd.fsstat.common;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Thread-safe, mutable accumulator for building file-size distribution reports.
 *
 * <p>Accumulates multiple file-size observations concurrently across threads
 * before producing an immutable {@link FSReport} snapshot when the traversal completes.
 *
 * <p><b>Thread safety</b> is guaranteed by using {@link AtomicLong} and {@link AtomicLongArray}
 * for lock-free atomic operations. Concurrent invocations of {@link #addFile(long)} perform
 * indivisible read-modify-write operations powered by Compare-and-Swap instructions paired with
 * volatile memory semantics. This eliminates race conditions and lost updates while
 * guaranteeing immediate visibility of changes across threads.
 */
public class FSReportAccumulator {

    private final AtomicLong totalFiles;
    private final AtomicLongArray bandCounts; // length = numBands + 1
    private final long maxFileSize;
    private final int numBands;
    private final long bandSize;

    /**
     * Constructs a new accumulator with the given band parameters.
     *
     * @param maxFileSize upper bound for regular bands (must be >= 0)
     * @param numBands    number of equal-width bands within [0, maxFileSize] (must be > 0)
     * @throws IllegalArgumentException if maxFileSize < 0 or numBands <= 0
     */
    public FSReportAccumulator(long maxFileSize, int numBands) {
        if (maxFileSize < 0) throw new IllegalArgumentException("maxFileSize must be >= 0");
        if (numBands <= 0) throw new IllegalArgumentException("numBands must be > 0");
        this.maxFileSize = maxFileSize;
        this.numBands = numBands;
        long size = maxFileSize / numBands;
        if (maxFileSize % numBands != 0) {
            size++;
        }
        this.bandSize = Math.max(1L, size);
        this.totalFiles = new AtomicLong(0);
        this.bandCounts = new AtomicLongArray(numBands + 1);
    }

    /**
     * Records one file of the given size.
     *
     * @param size the file size in bytes (must be >= 0)
     */
    public void addFile(long size) {
        totalFiles.incrementAndGet();
        if (size > maxFileSize) {
            // overflow bucket
            bandCounts.incrementAndGet(numBands);
        } else {
            int band = (int) Math.min(size / bandSize, numBands - 1);
            bandCounts.incrementAndGet(band);
        }
    }

    /**
     * Produces an immutable FSReport of accumulated statistics.
     *
     * <p>Should be called only after all {@link #addFile(long)} operations are complete.
     * The resulting FSReport is immutable and safe to share across threads.
     *
     * @return a new immutable FSReport reflecting the current accumulated state
     */
    public FSReport toReport() {
        long[] counts = new long[numBands + 1];
        for (int i = 0; i <= numBands; i++) {
            counts[i] = bandCounts.get(i);
        }
        return new FSReport(totalFiles.get(), counts, maxFileSize, numBands);
    }
}
