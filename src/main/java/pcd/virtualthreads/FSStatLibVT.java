package pcd.virtualthreads;

import pcd.common.FSReport;
import pcd.common.FSReportAccumulator;
import pcd.common.FSStatLib;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Virtual Threads implementation of {@link FSStatLib}.
 *
 * <p><b>Design Rationale:</b>
 * Virtual threads from Project Loom (Java 21+) enable cheap, lightweight concurrent
 * tasks that do NOT pin carrier OS threads during blocking I/O. This implementation
 * spawns one virtual thread per directory and uses simple, sequential-style code.
 *
 * <p><b>Concurrency Model:</b>
 * <ul>
 *   <li>Each VT recursively scans its assigned directory and any subdirectories.</li>
 *   <li>Blocking I/O (DirectoryStream, readAttributes) safely dismounts the carrier thread.</li>
 *   <li>Results are aggregated immutably via {@link pcd.common.FSReport#merge(FSReport)}.</li>
 *   <li><b>Zero shared mutable state:</b> each task builds a local accumulator on its
 *       virtual-thread stack; no cross-thread race conditions or pinning risks.</li>
 * </ul>
 *
 * <p><b>Executor Lifecycle:</b>
 * The implementation creates a {@code newVirtualThreadPerTaskExecutor} per call to
 * {@code getFSReport()} and shuts it down on completion. The caller receives a
 * CompletableFuture that completes asynchronously once all scanning is done.
 *
 * <p><b>Paradigm Adherence:</b>
 * This implementation prioritizes clean, sequential code over complex coordination logic.
 * Virtual-thread join points (CompletableFuture.join) are safe and natural; blocking
 * I/O is idiomatic. Stack-confined state avoids synchronization overhead and lock pinning.
 *
 * @see pcd.common.FSStatLib
 * @see pcd.common.FSReport
 */
public class FSStatLibVT implements FSStatLib {

    /**
     * Asynchronously scans a directory tree using virtual threads.
     *
     * @param directory   root of the file-system subtree to scan (must exist and be readable)
     * @param maxFileSize upper bound for file-size bands (must be >= 0)
     * @param numBands    number of equal-width size bands (must be > 0)
     * @return a CompletableFuture that:
     * - completes with an immutable {@link FSReport} when scanning finishes,
     * - completes exceptionally if an unrecoverable error occurs,
     * - is non-blocking from the caller's perspective
     * @throws IllegalArgumentException if {@code maxFileSize} or {@code numBands} are invalid
     * @see pcd.common.FSStatLib#getFSReport(java.nio.file.Path, long, int)
     */
    @Override
    public CompletableFuture<FSReport> getFSReport(Path directory, long maxFileSize, int numBands) {
        CompletableFuture<FSReport> result = new CompletableFuture<>();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        scanDirectoryAsync(directory, maxFileSize, numBands, executor)
                .whenComplete((report, err) -> {
                    try {
                        if (err != null) result.completeExceptionally(err);
                        else result.complete(report);
                    } finally {
                        executor.shutdown();
                    }
                });

        return result;
    }

    /**
     * Recursively scans a directory and its subdirectories on a virtual thread,
     * producing an aggregated immutable FSReport.
     *
     * <p><b>Execution Model:</b>
     * <ul>
     *   <li>Scheduled on the provided executor, which creates/reuses a virtual thread.</li>
     *   <li>Blocking I/O (file listing, attributes) safely happens on the VT (no carrier pinning).</li>
     *   <li>For each subdirectory, recursively schedules a child scan on the same executor.</li>
     *   <li>Joins all child futures (safe on VT; does not block carrier thread).</li>
     *   <li>Merges results immutably before returning.</li>
     * </ul>
     *
     * <p><b>Thread Safety:</b>
     * The local accumulator is confined to this VT's stack. Child futures are independent
     * tasks. No shared mutable state = no synchronization needed = no lock pinning.
     *
     * @param dir         directory to scan
     * @param maxFileSize upper bound for size bands
     * @param numBands    number of bands
     * @param executor    the virtual-thread-per-task executor for scheduling child scans
     * @return a CompletableFuture yielding the aggregated FSReport for this directory subtree
     */
    private CompletableFuture<FSReport> scanDirectoryAsync(Path dir,
                                                           long maxFileSize,
                                                           int numBands,
                                                           ExecutorService executor) {
        return CompletableFuture.supplyAsync(() -> {
            FSReportAccumulator localAcc = new FSReportAccumulator(maxFileSize, numBands);
            List<CompletableFuture<FSReport>> childFutures = new ArrayList<>();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path entry : stream) {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                        if (attrs.isDirectory()) {
                            // Recurse asynchronously; scheduling onto the same virtual-thread executor
                            childFutures.add(scanDirectoryAsync(entry, maxFileSize, numBands, executor));
                        } else {
                            localAcc.addFile(attrs.size());
                        }
                    } catch (IOException ignored) { /* skip inaccessible */ }
                }
            } catch (IOException ignored) { /* skip inaccessible directory */ }

            // Wait for children and merge results
            FSReport result = localAcc.toReport();
            for (CompletableFuture<FSReport> cf : childFutures) {
                FSReport child = cf.join();
                result = result.merge(child);
            }
            return result;
        }, executor);
    }
}
