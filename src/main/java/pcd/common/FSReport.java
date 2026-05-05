package pcd.common;

/**
 * Immutable report of file-system scanning results.
 *
 * <p>An instance of FSReport encapsulates:
 * <ul>
 *   <li><b>totalFiles:</b> total count of files scanned</li>
 *   <li><b>bandCounts:</b> array of file counts per size band</li>
 *   <li><b>maxFileSize:</b> upper bound for the regular size bands</li>
 *   <li><b>numBands:</b> number of equal-width bands within [0, maxFileSize]</li>
 * </ul>
 *
 * <p><b>Band Layout:</b>
 * The array {@code bandCounts} has {@code numBands + 1} elements:
 * <ul>
 *   <li>bandCounts[0..numBands-1]: files in regular bands
 *     <ul>
 *       <li>band i represents the range [i*bandWidth, (i+1)*bandWidth)</li>
 *     </ul>
 *   </li>
 *   <li>bandCounts[numBands]: overflow bucket; files with size > maxFileSize</li>
 * </ul>
 *
 * <p><b>Immutability:</b>
 * This record is immutable. The {@code bandCounts} array is defensively cloned in
 * the constructor and the accessor to prevent external mutation.
 *
 * <p><b>Merging:</b>
 * The {@link #merge(FSReport)} method supports pure functional aggregation
 * of multiple reports without shared mutable state.
 *
 * @param totalFiles  non-negative total file count
 * @param bandCounts  array of length {@code numBands + 1}; each element is non-negative
 * @param maxFileSize upper bound for regular bands (must be ≥ 0)
 * @param numBands    number of regular bands (must be > 0)
 */
public record FSReport(long totalFiles, long[] bandCounts, long maxFileSize, int numBands) {

    public FSReport(long totalFiles, long[] bandCounts, long maxFileSize, int numBands) {
        if (bandCounts == null) throw new IllegalArgumentException("bandCounts must not be null");
        if (maxFileSize < 0) throw new IllegalArgumentException("maxFileSize must be >= 0");
        if (numBands <= 0) throw new IllegalArgumentException("numBands must be > 0");
        if (bandCounts.length != numBands + 1) {
            throw new IllegalArgumentException("bandCounts length must be numBands + 1");
        }
        this.totalFiles = totalFiles;
        this.bandCounts = bandCounts.clone();
        this.maxFileSize = maxFileSize;
        this.numBands = numBands;
    }

    /**
     * Creates an empty report with zero files in all bands.
     *
     * @param maxFileSize upper bound for bands
     * @param numBands    number of bands
     * @return a new FSReport with zero totals and zero band counts
     */
    public static FSReport empty(long maxFileSize, int numBands) {
        return new FSReport(0, new long[numBands + 1], maxFileSize, numBands);
    }

    @Override
    public long[] bandCounts() {
        return bandCounts.clone();
    }

    @Override
    public String toString() {
        long bandSize = bandWidth();
        StringBuilder sb = new StringBuilder();
        sb.append("=== FSReport ===\n");
        sb.append(String.format("  Total files : %,d%n", totalFiles));
        sb.append("  File-size distribution:\n");
        for (int i = 0; i < numBands; i++) {
            long start = i * bandSize;
            if (start > maxFileSize) {
                sb.append(String.format("    [empty] bytes : %,d files%n", bandCounts[i]));
            } else {
                long end = (i == numBands - 1) ? maxFileSize : Math.min(maxFileSize, start + bandSize - 1);
                sb.append(String.format("    [%,d – %,d] bytes : %,d files%n",
                        start, end, bandCounts[i]));
            }
        }
        sb.append(String.format("    [> %,d]  bytes : %,d files%n",
                maxFileSize, bandCounts[numBands]));
        return sb.toString();
    }

    /**
     * Merges this report with another, producing a new immutable report.
     *
     * <p>The merge operation aggregates:
     * <ul>
     *   <li>totalFiles: sum of both reports' totalFiles</li>
     *   <li>bandCounts: element-wise sum of both arrays</li>
     * </ul>
     *
     * @param other another FSReport with the same band configuration
     * @return a new FSReport with aggregated statistics
     * @throws IllegalArgumentException if band configurations (maxFileSize or numBands) differ
     */
    public FSReport merge(FSReport other) {
        if (this.maxFileSize != other.maxFileSize || this.numBands != other.numBands) {
            throw new IllegalArgumentException(
                    "Cannot merge reports with different band configurations");
        }
        long newTotal = this.totalFiles + other.totalFiles;
        long[] newCounts = new long[this.numBands + 1];
        for (int i = 0; i <= this.numBands; i++) {
            newCounts[i] = this.bandCounts[i] + other.bandCounts[i];
        }
        return new FSReport(newTotal, newCounts, this.maxFileSize, this.numBands);
    }

    private long bandWidth() {
        long size = maxFileSize / numBands;
        if (maxFileSize % numBands != 0) {
            size++;
        }
        return Math.max(1L, size);
    }
}
