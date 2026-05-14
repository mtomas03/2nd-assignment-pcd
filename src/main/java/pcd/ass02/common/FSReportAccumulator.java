package pcd.ass02.common;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Thread-safe, mutable accumulator for building file-size distribution reports.
 *
 * <p><b>Purpose:</b>
 * Accumulates file-size observations from concurrent callbacks/threads and produces
 * an immutable {@link FSReport} snapshot when complete. Used internally by scanning
 * implementations (Vert.x, RxJava, Virtual Threads).
 *
 * <p><b>Thread Safety:</b>
 * Multiple threads/callbacks can safely call {@link #addFile(long)} concurrently
 * without external synchronization, thanks to atomic field operations:
 * <ul>
 *   <li>{@link java.util.concurrent.atomic.AtomicLong} for totalFiles</li>
 *   <li>{@link java.util.concurrent.atomic.AtomicLongArray} for bandCounts</li>
 * </ul>
 * No lock pinning or blocking synchronization is used, making it suitable for
 * virtual-thread and reactive contexts.
 *
 * <p><b>Band Sizing:</b>
 * The range [0, maxFileSize] is divided into numBands equal-width bands. Each file's
 * size determines which band it falls into; files with size > maxFileSize are
 * counted in the overflow bucket (band numBands).
 *
 * <p><b>Usage Pattern:</b>
 * <ol>
 *   <li>Construct with band parameters</li>
 *   <li>Call {@link #addFile(long)} for each observed file size</li>
 *   <li>Once all additions complete, call {@link #toReport()} to get immutable snapshot</li>
 * </ol>
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
     * @param maxFileSize upper bound for regular bands (must be ≥ 0)
     * @param numBands    number of equal-width bands within [0, maxFileSize] (must be > 0)
     * @throws IllegalArgumentException if maxFileSize < 0 or numBands ≤ 0
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
     * <p>Atomically updates:
     * <ul>
     *   <li>totalFiles: incremented by 1</li>
     *   <li>bandCounts: the appropriate band is incremented</li>
     * </ul>
     * If the file size exceeds maxFileSize, it is counted in the overflow bucket.
     *
     * <p>This method is fully thread-safe and lock-free. It is safe to call
     * concurrently from multiple threads/callbacks.
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
     * Produces an immutable snapshot of accumulated statistics.
     *
     * <p>Should be called only after all {@link #addFile(long)} operations are complete
     * (or at least, once all concurrent callers have finished). The resulting FSReport
     * is immutable and safe to share across threads.
     *
     * <p>Multiple calls to toReport() will produce equivalent but distinct FSReport instances
     * (new arrays are created each time).
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
