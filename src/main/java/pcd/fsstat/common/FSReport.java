package pcd.fsstat.common;

/**
 * Immutable report of file-system scanning results.
 *
 * @param totalFiles  non-negative total count of files scanned
 * @param bandCounts  array of file counts per size band; each element is non-negative
 * @param maxFileSize upper bound for the regular size bands (must be > 0)
 * @param numBands    number of equal-width bands within [0, maxFileSize]
 */
public record FSReport(long totalFiles, long[] bandCounts, long maxFileSize, int numBands) {

    public FSReport(long totalFiles, long[] bandCounts, long maxFileSize, int numBands) {
        if (bandCounts == null) throw new IllegalArgumentException("bandCounts must not be null");
        if (maxFileSize <= 0) throw new IllegalArgumentException("maxFileSize must be > 0");
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

    /**
     * Creates an immutable {@link FSReport} representing a single file of the specified size.
     *
     * @param fileSize    size of the file in bytes (must be >= 0)
     * @param maxFileSize upper bound for regular histogram bands
     * @param numBands    number of equal-width histogram bands
     * @return an immutable unit {@link FSReport} containing a single file observation
     */
    public static FSReport single(long fileSize, long maxFileSize, int numBands) {
        long[] counts = new long[numBands + 1];
        if (fileSize > maxFileSize) {
            counts[numBands] = 1;
        } else {
            long size = maxFileSize / numBands;
            if (maxFileSize % numBands != 0) {
                size++;
            }
            long bandSize = Math.max(1L, size);
            int band = (int) Math.min(fileSize / bandSize, numBands - 1);
            counts[band] = 1;
        }
        return new FSReport(1, counts, maxFileSize, numBands);
    }

    @Override
    public long[] bandCounts() {
        return bandCounts.clone();
    }

    @Override
    public String toString() {
        long bandSize = bandWidth();
        StringBuilder sb = new StringBuilder();
        sb.append("-- FSReport --\n");
        sb.append(String.format("Total files : %,d%n", totalFiles));
        sb.append("File-size distribution:\n");
        for (int i = 0; i < numBands; i++) {
            long start = i * bandSize;
            if (start > maxFileSize) {
                sb.append(String.format("[empty] bytes : %,d files%n", bandCounts[i]));
            } else {
                long end = (i == numBands - 1) ? maxFileSize : Math.min(maxFileSize, start + bandSize - 1);
                sb.append(String.format("[%,d - %,d] bytes : %,d files%n",
                        start, end, bandCounts[i]));
            }
        }
        sb.append(String.format("[> %,d]  bytes : %,d files%n",
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
