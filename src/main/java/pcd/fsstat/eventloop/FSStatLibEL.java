package pcd.fsstat.eventloop;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import pcd.fsstat.common.FSReport;
import pcd.fsstat.common.ReportParameters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Vert.x event-loop implementation.
 */
public class FSStatLibEL {

    private final Vertx vertx;

    public FSStatLibEL() {
        this(Vertx.vertx());
    }

    public FSStatLibEL(Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx, "vertx must not be null");
    }

    /**
     * Initiates an asynchronous file-system scan for the directory specified in the parameters.
     *
     * @param parameters the scan configuration specifying target directory, band sizes, and bounds
     * @return a {@link Future} that completes with the final aggregated immutable {@link FSReport}
     * @throws NullPointerException if {@code parameters} is null
     */
    public Future<FSReport> getFSReport(ReportParameters parameters) {
        Objects.requireNonNull(parameters, "parameters must not be null");
        return scanDirectory(parameters);
    }

    /**
     * Asynchronously reads a directory and triggers child path inspection tasks.
     *
     * <p>If reading the directory fails, the error is recovered
     * gracefully by treating the directory as empty.
     *
     * @param parameters configuration for the current directory path
     * @return a future resolving to the aggregated report of the directory and all its children
     */
    private Future<FSReport> scanDirectory(ReportParameters parameters) {
        // Asynchronous non-blocking request to read directory entries (delegated to internal worker pool)
        return vertx.fileSystem().readDir(parameters.directory().toString())
                // Recover from read failures by returning an empty list
                .recover(err -> Future.succeededFuture(List.of()))
                // CPS: execute processing logic on event loop thread once directory entries are available
                .compose(children -> {
                    List<Future<FSReport>> childReports = new ArrayList<>(children.size());

                    // Asynchronous inspection for each discovered child path
                    for (String child : children) {
                        childReports.add(processFSEntry(parameters.withDirectory(Path.of(child))));
                    }

                    // Immediate completion for empty directories
                    if (childReports.isEmpty()) {
                        return Future.succeededFuture(FSReport.empty(
                                parameters.maxFileSize(), parameters.numBands()));
                    }

                    // Non-blocking join of all child report futures into a single composite future
                    return Future.all(childReports)
                            // Merge completed child reports into a single aggregated report
                            .map(compositeFuture -> mergeReports(
                                    compositeFuture.list(),
                                    parameters.maxFileSize(),
                                    parameters.numBands()
                            ));
                });
    }

    /**
     * Inspects a file-system entry to determine whether it is a subdirectory or a regular file.
     *
     * <p>Recursively invokes directory traversal for subdirectories or creates a unit report for
     * regular files. I/O errors are caught and gracefully fallback to an empty report.
     *
     * @param parameters configuration for the path being evaluated
     * @return a future completing with the report for this specific path
     */
    private Future<FSReport> processFSEntry(ReportParameters parameters) {
        // Asynchronously fetch file properties for target path (delegated to internal worker pool)
        return vertx.fileSystem().props(parameters.directory().toString())
                // CPS: evaluate entry type once properties are obtained
                .compose(props -> {
                    if (props.isDirectory()) {
                        // Asynchronous recursive scan for subdirectories
                        return scanDirectory(parameters);
                    }

                    // Produce an immediate unit report for regular files
                    return Future.succeededFuture(
                            FSReport.single(
                                    props.size(),
                                    parameters.maxFileSize(),
                                    parameters.numBands())
                    );
                })
                // Fallback to empty report on any I/O error
                .recover(err -> Future.succeededFuture(FSReport.empty(
                        parameters.maxFileSize(),
                        parameters.numBands()
                )));
    }

    /**
     * Combines a list of completed child reports into a single aggregated report.
     *
     * @param reports     list of child reports returned by the composite future
     * @param maxFileSize upper bound for regular histogram bands
     * @param numBands    number of histogram bands
     * @return a new merged immutable {@link FSReport}
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
     * Closes the underlying Vert.x instance and releases associated asynchronous resources.
     */
    public void shutdown() {
        vertx.close();
    }
}
