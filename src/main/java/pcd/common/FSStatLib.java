package pcd.common;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for asynchronous file-system scanning implementations.
 *
 * <p><b>Non-Blocking Semantics:</b>
 * All implementations adhere to non-blocking semantics:
 * <ul>
 *   <li>The method {@link #getFSReport(java.nio.file.Path, long, int)} returns immediately
 *       (never blocks the caller's thread)</li>
 *   <li>The returned {@link CompletableFuture} completes asynchronously when scanning finishes</li>
 *   <li>The caller can attach callbacks, zip with other futures, or block (via get/join)</li>
 * </ul>
 *
 * <p><b>Paradigm Differences:</b>
 * Although the interface is identical, each implementation embodies distinct concurrency
 * principles and should not be mixed or reused across implementations.
 */
public interface FSStatLib {

    /**
     * Asynchronously scans a directory tree and produces a file-size distribution report.
     *
     * <p><b>Scanning Behavior:</b>
     * Recursively traverses the directory rooted at {@code directory}, including all
     * subdirectories. For each regular file, records its size. Inaccessible files or
     * directories are skipped silently.
     *
     * <p><b>Non-Blocking Contract:</b>
     * This method returns immediately without blocking the caller. The actual scanning
     * happens asynchronously.
     *
     * <p><b>Error Handling:</b>
     * If an unrecoverable error occurs during scanning (e.g., bad directory path,
     * access denied), the returned future completes exceptionally.
     *
     * @param directory   root of the file-system subtree to scan.
     *                    Must exist and be readable; must be a directory.
     * @param maxFileSize upper bound for regular size bands. Files with size <= maxFileSize
     *                    are distributed across numBands equal-width bands;
     *                    files > maxFileSize go to an overflow bucket.
     *                    Must be >= 0.
     * @param numBands    number of equal-width bands within [0, maxFileSize].
     *                    Determines the granularity of the size distribution.
     *                    Must be > 0.
     * @return a CompletableFuture that:
     * <ul>
     *   <li>Is never null</li>
     *   <li>Completes successfully with an immutable FSReport when scanning finishes</li>
     *   <li>Completes exceptionally if a fatal error occurs</li>
     *   <li>Is safe to share across threads</li>
     * </ul>
     * @throws IllegalArgumentException if maxFileSize < 0 or numBands <= 0
     * @throws NullPointerException     if directory is null
     * @see pcd.common.FSReport
     */
    CompletableFuture<FSReport> getFSReport(Path directory, long maxFileSize, int numBands);
}
