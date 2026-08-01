package pcd.fsstat.reactive;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import pcd.fsstat.common.FSReport;
import pcd.fsstat.common.ReportParameters;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Reactive Programming implementation.
 */
public class FSStatLibRx {

    /**
     * Generates a cold {@link Flowable} that performs a recursive scan of the specified
     * directory, emitting periodic progress updates and a guaranteed final report.
     *
     * @param parameters the scan configuration containing target directory, band sizes, and bounds
     * @return a {@link Flowable} emitting periodic accumulated {@link FSReport} updates and a guaranteed final report
     * @throws NullPointerException if {@code parameters} is null
     */
    public Flowable<FSReport> getFSReport(ReportParameters parameters) {
        Objects.requireNonNull(parameters, "parameters must not be null");
        FSReport emptyReport = FSReport.empty(parameters.maxFileSize(), parameters.numBands());

        return scanDirectory(parameters.directory())
                // Delegate blocking file-system I/O operations to the dedicated I/O scheduler
                .subscribeOn(Schedulers.io())

                // Map each discovered file size into a single-file FSReport
                .map(size -> FSReport.single(size, parameters.maxFileSize(), parameters.numBands()))

                // Accumulate file reports
                .scan(emptyReport, FSReport::merge)

                // UI throttling and the final completion snapshot share a single traversal
                .publish(shared -> {
                    // Branch 1: Sample accumulated progress at 100ms intervals for smooth UI updates
                    Flowable<FSReport> progress = shared
                            .sample(100, TimeUnit.MILLISECONDS, Schedulers.computation());

                    // Branch 2: Ensure the final, complete report is captured upon stream completion
                    Flowable<FSReport> finalReport = shared.takeLast(1);

                    // Merge periodic progress snapshots with the guaranteed final report
                    return Flowable.merge(progress, finalReport);
                })

                // Filter out duplicate consecutive emissions
                .distinctUntilChanged();
    }

    /**
     * Recursively traverses the file system from the given path, emitting file sizes as a reactive stream.
     *
     * <p>Uses {@link Flowable#using} to guarantee that opened {@link DirectoryStream} instances are
     * safely closed when the stream terminates, completes, or encounters an error.
     *
     * @param path the root path to inspect
     * @return a {@link Flowable} emitting sizes in bytes for all discovered regular files
     */
    private Flowable<Long> scanDirectory(Path path) {
        // Asynchronously check whether the current path is a directory or a regular file (executed on subscription)
        return Flowable.fromCallable(() -> Files.isDirectory(path))
                .flatMap(isDir -> {
                    if (isDir) {
                        // Reactive try-with-resources pattern to safely manage the OS directory handle
                        return Flowable.using(
                                        // 1. Resource factory: Open the OS DirectoryStream
                                        () -> Files.newDirectoryStream(path),

                                        // 2. Observable factory: Recursively scan each directory entry
                                        ds -> Flowable.fromIterable(ds)
                                                .flatMap(this::scanDirectory),

                                        // 3. Cleanup: Ensure DirectoryStream closure on completion, error, or disposal
                                        DirectoryStream::close
                                )
                                // Ignore unreadable directories and resume with an empty stream
                                .onErrorResumeNext(e -> Flowable.empty());
                    } else {
                        // Asynchronously retrieve the size of regular files
                        return Flowable.fromCallable(() -> Files.size(path))
                                // Fallback to 0 bytes on I/O error to prevent stream termination
                                .onErrorReturnItem(0L);
                    }
                })
                // Catch-all exceptions related to the first-level path inspection
                .onErrorResumeNext(e -> Flowable.empty());
    }
}
