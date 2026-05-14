package pcd.ass02.reactive;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import pcd.ass02.common.FSReport;
import pcd.ass02.common.FSReportAccumulator;
import pcd.ass02.common.ReportParameters;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Reactive Programming implementation.
 *
 * <p><b>Execution Model:</b>
 * <ul>
 *   <li>Returns a cold {@link Flowable}; the filesystem traversal
 *       starts only when a subscriber subscribes to the stream.</li>
 *   <li>Recursively traverses the directory tree. Concurrency is managed at
 *       each directory level via {@code flatMap(..., MAX_CONCURRENCY)} to
 *       control the breadth of the scan during stream expansion.</li>
 *   <li>All blocking filesystem operations (directory listing
 *       and file metadata reads) are offloaded to {@link Schedulers#io()}.</li>
 *   <li>Emits intermediate {@link FSReport} updates
 *       by accumulating results with {@code scan(...)} and sampling
 *       the stream every 100ms to maintain UI responsiveness.</li>
 *   <li>Ensures the final, precise report is always delivered as the last item
 *       by merging the sampled progress with the absolute
 *       last element of the accumulation.</li>
 *   <li>Filters consecutive identical reports using
 *       {@code distinctUntilChanged()} to avoid redundant UI cycles.</li>
 *   <li>Implements local error recovery. Unreadable files
 *       are treated as having size 0, and inaccessible directories are silently
 *       skipped, allowing the scan to complete despite localized I/O errors.</li>
 * </ul>
 */
public class FSStatLibRx {

    private static final int MAX_CONCURRENCY = Runtime.getRuntime().availableProcessors();

    /**
     * Generates a cold {@link Flowable} that performs a recursive scan of the specified
     * directory, emitting periodic progress updates and a guaranteed final report.
     *
     * @param parameters configuration object containing the root directory,
     *                   file size thresholds, and histogram binning details.
     * @return a cold {@link Flowable} emitting sampled {@link FSReport} snapshots
     *         followed by the definitive final report.
     * @throws NullPointerException if {@code parameters} is null.
     */
    public Flowable<FSReport> getFSReport(ReportParameters parameters) {
        Objects.requireNonNull(parameters);
        FSReport seed = FSReport.empty(parameters.maxFileSize(), parameters.numBands());

        return scanPath(parameters.directory())
                .subscribeOn(Schedulers.io())
                .map(size -> createUnitReport(size, parameters.maxFileSize(), parameters.numBands()))
                .scan(seed, FSReport::merge)
                .publish(shared -> {
                    Flowable<FSReport> progress = shared
                            .sample(100, TimeUnit.MILLISECONDS, Schedulers.computation());

                    Flowable<FSReport> finalReport = shared.takeLast(1);

                    return Flowable.merge(progress, finalReport);
                })
                .distinctUntilChanged();
    }

    private Flowable<Long> scanPath(Path path) {
        return Flowable.fromCallable(() -> Files.isDirectory(path))
                .flatMap(isDir -> {
                    if (isDir) {
                        return Flowable.using(
                                () -> Files.newDirectoryStream(path),
                                ds -> Flowable.fromIterable(ds)
                                        .flatMap(this::scanPath, MAX_CONCURRENCY),
                                DirectoryStream::close
                        ).onErrorResumeNext(e -> Flowable.empty());
                    } else {
                        return Flowable.fromCallable(() -> Files.size(path))
                                .onErrorReturnItem(0L);
                    }
                })
                .onErrorResumeNext(e -> Flowable.empty());
    }

    private FSReport createUnitReport(long fileSize, long maxFileSize, int numBands) {
        FSReportAccumulator acc = new FSReportAccumulator(maxFileSize, numBands);
        acc.addFile(fileSize);
        return acc.toReport();
    }
}
