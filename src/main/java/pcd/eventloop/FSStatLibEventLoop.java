package pcd.eventloop;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import pcd.common.FSReport;
import pcd.common.FSReportAccumulator;
import pcd.common.ReportParameters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Vert.x event-loop implementation.
 *
 * <p><b>Execution Model:</b>
 * <ul>
 *   <li>Non-blocking file-system operations via Vert.x.</li>
 *   <li>Regular files become unit reports.</li>
 *   <li>Directories recurse asynchronously.</li>
 *   <li>Child futures aggregated and reports merged immutably.</li>
 * </ul>
 */
public class FSStatLibEventLoop {

    private final Vertx vertx;

    public FSStatLibEventLoop() {
        this(Vertx.vertx());
    }

    public FSStatLibEventLoop(Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx, "vertx must not be null");
    }

    /**
     * Starts a non-blocking asynchronous recursive scan.
     *
     * <p>This method returns immediately without performing any I/O.
     * The returned {@link Future} completes when all directory entries
     * and their statistics have been fully scanned and merged.
     *
     * <p>Errors in individual entries are silently skipped via recovery
     * handlers; only fatal I/O failures to the root directory propagate
     * as exceptional completion.
     *
     * @param parameters scan parameters including root directory, max file size,
     *                   and number of bands
     * @return a non-null Vert.x {@link Future} that eventually yields the
     *         aggregated {@link FSReport}
     * @throws NullPointerException if {@code parameters} is {@code null}
     */
    public Future<FSReport> getFSReport(ReportParameters parameters) {
        Objects.requireNonNull(parameters, "parameters must not be null");
        return traverseAsync(parameters);
    }

    /**
     * Recursively traverses {@code dir} using Vert.x file-system futures and
     * returns an immutable {@link FSReport} for the whole subtree.
     *
     * <p>Each call returns a {@code Future<FSReport>} that, when complete,
     * contains the aggregated statistics for that directory and all its
     * descendants. Regular files are converted into unit reports, subdirectories
     * recurse through {@link #processPath(ReportParameters)}, and the resulting
     * child reports are merged immutably via {@link FSReport#merge(FSReport)}.
     *
     * <p>Unreadable directories are treated as empty subtrees.
     *
     * @param parameters scan parameters including root directory, max file size,
     *                   and number of bands
     * @return a future completed with the aggregated report for {@code dir}
     */
    private Future<FSReport> traverseAsync(ReportParameters parameters) {
        return vertx.fileSystem().readDir(parameters.directory().toString())
                .recover(err -> Future.succeededFuture(List.of()))
                .compose(children -> {
                    List<Future<FSReport>> childReports = new ArrayList<>(children.size());
                    for (String child : children) {
                        childReports.add(processPath(parameters.withDirectory(Path.of(child))));
                    }

                    if (childReports.isEmpty()) {
                        return Future.succeededFuture(FSReport.empty(
                                parameters.maxFileSize(), parameters.numBands()));
                    }

                    // Collect all child reports and merge them into a single, immutable report
                    return Future.all(childReports)
                            .map(compositeFuture -> mergeReports(
                                    compositeFuture.list(),
                                    parameters.maxFileSize(),
                                    parameters.numBands()
                            ));
                });
    }

    /**
     * Classifies a single path and returns its report immutably.
     *
     * <p>If the path is a directory, the method recurses via
     * {@link #traverseAsync(ReportParameters)}. If the path is a regular file,
     * it is converted into a unit report containing exactly one counted file.
     * Any error while inspecting the path is recovered as an empty report so
     * that inaccessible entries do not abort the whole scan.
     *
     * @param parameters scan parameters including root directory, max file size,
     *                   and number of bands
     * @return a future completed with the report for the single path
     */
    private Future<FSReport> processPath(ReportParameters parameters) {
        return vertx.fileSystem().props(parameters.directory().toString())
                .compose(props -> {
                    if (props.isDirectory()) {
                        return traverseAsync(parameters);
                    }

                    // Unit report for a single file (immutable)
                    return Future.succeededFuture(
                            createUnitReport(
                                    props.size(),
                                    parameters.maxFileSize(),
                                    parameters.numBands())
                    );
                })
                .recover(err -> Future.succeededFuture(FSReport.empty(
                        parameters.maxFileSize(),
                        parameters.numBands()
                )));
    }

    /**
     * Merge a list of {@link FSReport} instances into a single immutable report.
     *
     * <p>This helper is used after {@code Future.all(...)} has completed so the
     * list contains only successfully completed child reports. Non-{@link FSReport}
     * entries are ignored defensively.
     *
     * @param reports     list of reports to merge
     * @param maxFileSize maximum file size used to build the histogram bands
     * @param numBands    number of regular size bands
     * @return the merged report
     */
    private FSReport mergeReports(List<?> reports, long maxFileSize, int numBands) {
        FSReport result = FSReport.empty(maxFileSize, numBands);
        for (Object o : reports) {
            if (o instanceof FSReport r) {
                result = result.merge(r);
            }
        }
        return result;
    }

    /**
     * Creates a unit report for a single file size.
     *
     * <p>The report is built through {@link FSReportAccumulator} and immediately
     * converted to its immutable snapshot form.
     *
     * @param fileSize    size of the file to count
     * @param maxFileSize maximum file size used to build the histogram bands
     * @param numBands    number of regular size bands
     * @return a report describing a single file
     */
    private FSReport createUnitReport(long fileSize, long maxFileSize, int numBands) {
        FSReportAccumulator acc = new FSReportAccumulator(maxFileSize, numBands);
        acc.addFile(fileSize);
        return acc.toReport();
    }

    /**
     * Closes the underlying Vert.x instance.
     *
     * <p>Call this method when the library is no longer needed to release the
     * event-loop and file-system resources owned by Vert.x.
     */
    public void shutdown() {
        vertx.close();
    }
}
