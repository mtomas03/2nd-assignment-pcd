package pcd;

import pcd.common.FSReport;
import pcd.common.FSStatLib;
import pcd.eventloop.FSStatLibEventLoop;
import pcd.reactive.FSStatLibRx;
import pcd.virtualthreads.FSStatLibVT;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

/**
 * Entry point for the FSStat file-system scanning library.
 *
 * <p>Demonstrates all three paradigm-specific implementations of the FSStatLib interface:
 * <ol>
 *   <li>Event-loop based asynchronous programming (Vert.x)</li>
 *   <li>Reactive programming (RxJava 3)</li>
 *   <li>Virtual Threads (Java 21 Project Loom)</li>
 * </ol>
 *
 * <p>Each implementation scans a directory tree recursively and produces a file-size
 * distribution report. Performance and paradigm adherence are demonstrated via
 * sequential execution and timing measurements.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: FSStat <directory> [maxFileSize] [numBands]");
            System.exit(1);
        }
        Path directory = Paths.get(args[0]);
        long maxFileSize = args.length > 1 ? Long.parseLong(args[1]) : 1_048_576L;
        int numBands = args.length > 2 ? Integer.parseInt(args[2]) : 8;

        System.out.printf("Directory   : %s%n", directory.toAbsolutePath());
        System.out.printf("maxFileSize : %,d bytes%n", maxFileSize);
        System.out.printf("numBands    : %d%n%n", numBands);

        System.out.println("Version 1 - Event-loop");
        FSStatLibEventLoop elLib = new FSStatLibEventLoop();
        runAndPrint(elLib, directory, maxFileSize, numBands);
        elLib.shutdown();

        System.out.println("Version 2 - Reactive");
        FSStatLib rxLib = new FSStatLibRx();
        runAndPrint(rxLib, directory, maxFileSize, numBands);

        System.out.println("Version 3 - Virtual Threads");
        FSStatLib vtLib = new FSStatLibVT();
        runAndPrint(vtLib, directory, maxFileSize, numBands);
    }

    private static void runAndPrint(FSStatLib lib,
                                    Path directory,
                                    long maxFileSize,
                                    int numBands) throws Exception {
        long start = System.currentTimeMillis();

        // Call is non-blocking: we join here only to measure elapsed time
        CompletableFuture<FSReport> future = lib.getFSReport(directory, maxFileSize, numBands);
        FSReport report = future.get();

        long elapsed = System.currentTimeMillis() - start;
        System.out.println(report);
        System.out.printf("  Elapsed: %d ms%n%n", elapsed);
    }
}
