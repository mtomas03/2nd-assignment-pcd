package pcd.virtualthreads;

import pcd.common.FSReport;
import pcd.common.FSReportAccumulator;
import pcd.common.ReportParameters;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Virtual Threads (VT) implementation.
 *
 * <p><b>Execution Model:</b>
 * <ul>
 *   <li>Each VT recursively scans its assigned directory and any subdirectories.</li>
 *   <li>Blocking I/O (DirectoryStream, readAttributes) safely dismounts the carrier thread.</li>
 *   <li>Results are aggregated immutably via {@link pcd.common.FSReport#merge(FSReport)}.</li>
 *   <li>Each task builds a local accumulator on its virtual-thread stack,
 *       then merges immutable reports.</li>
 * </ul>
 */
public class FSStatLibVT implements AutoCloseable {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Starts an asynchronous recursive scan using virtual threads.
     *
     * @param parameters scan parameters including root directory, max file size,
     *                   and number of bands
     * @return a non-null {@link Future} that eventually returns the final immutable
     *         {@link FSReport}
     * @throws NullPointerException if {@code parameters} is {@code null}
     */
    public Future<FSReport> getFSReport(ReportParameters parameters) {
        Objects.requireNonNull(parameters, "parameters must not be null");
        return scanDirectoryAsync(parameters);
    }

    /**
     * Recursively scans a directory and its subdirectories on a virtual thread,
     * producing an aggregated immutable FSReport.
     *
     * @param parameters scan parameters including root directory, max file size,
     *                   and number of bands
     * @return a {@link Future} yielding the aggregated FSReport for this directory subtree
     */
    private Future<FSReport> scanDirectoryAsync(ReportParameters parameters) {
        return this.executor.submit(() -> {
            FSReportAccumulator localAcc = new FSReportAccumulator(
                    parameters.maxFileSize(), parameters.numBands());
            List<Future<FSReport>> childFutures = new ArrayList<>();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(parameters.directory())) {
                for (Path entry : stream) {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                        if (attrs.isDirectory()) {
                            childFutures.add(scanDirectoryAsync(parameters.withDirectory(entry)));
                        } else {
                            localAcc.addFile(attrs.size());
                        }
                    } catch (IOException ignored) { /* skip inaccessible */ }
                }
            } catch (IOException ignored) { /* skip inaccessible directory */ }

            // Wait for children and merge results
            FSReport result = localAcc.toReport();
            for (Future<FSReport> childFuture : childFutures) {
                try {
                    result = result.merge(childFuture.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                } catch (ExecutionException e) {
                    throw new IOException("Subtree scan failed", e.getCause());
                }
            }
            return result;
        });
    }

    /**
     * Shuts down the internal executor.
     */
    @Override
    public void close() {
        this.executor.shutdown();
    }
}
