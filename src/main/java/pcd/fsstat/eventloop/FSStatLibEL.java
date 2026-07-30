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
        // Asynchronously request file names contained in target directory
        return vertx.fileSystem().readDir(parameters.directory().toString())
                // Recover from read failures by returning an empty list
                .recover(err -> Future.succeededFuture(List.of()))
                // Chain processing when directory entry list becomes available
                .compose(children -> {
                    // Pre-allocate list to hold futures for each child entry
                    List<Future<FSReport>> childReports = new ArrayList<>(children.size());
                    // Iterate through each child path string returned by Vert.x
                    for (String child : children) {
                        // Dispatch asynchronous inspection for child path and accumulate future
                        childReports.add(processFSEntry(parameters.withDirectory(Path.of(child))));
                    }

                    // Check if directory is empty
                    if (childReports.isEmpty()) {
                        // Complete immediately with an empty report instance
                        return Future.succeededFuture(FSReport.empty(
                                parameters.maxFileSize(), parameters.numBands()));
                    }

                    // Join all child futures, waiting for every child subtree to finish scanning
                    return Future.all(childReports)
                            // Map composite results into a single merged report upon completion
                            .map(compositeFuture -> mergeReports(
                                    // Extract raw list of resolved FSReport objects
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
        // Asynchronously fetch file properties/metadata for target path
        return vertx.fileSystem().props(parameters.directory().toString())
                // Chain evaluation once metadata is fetched
                .compose(props -> {
                    // Branch execution if path points to a directory
                    if (props.isDirectory()) {
                        // Recursively trigger directory scanning pipeline
                        return scanDirectory(parameters);
                    }

                    // For regular files, return a succeeded future with a single-file report
                    return Future.succeededFuture(
                            FSReport.single(
                                    // Extract size in bytes from file properties
                                    props.size(),
                                    // Pass max file size limit
                                    parameters.maxFileSize(),
                                    // Pass histogram band count
                                    parameters.numBands())
                    );
                })
                // Fallback to empty report on any I/O error (e.g., broken symlink or access denied)
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
        // Iterate through untyped list items returned by composite future
        for (Object o : reports) {
            if (o instanceof FSReport r) {
                // Merge individual child report into accumulated total
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
