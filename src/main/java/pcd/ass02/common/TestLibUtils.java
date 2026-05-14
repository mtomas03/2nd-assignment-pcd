package pcd.ass02.common;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Small utility to parse CLI args and run demo mains with minimal duplication.
 */
public final class TestLibUtils {

    private TestLibUtils() {}

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /**
     * Parse common CLI arguments used by the demo mains.
     */
    public static ReportParameters parseArgs(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: <TestLib> <directory> [maxFileSize] [numBands]");
        }
        Path directory = Paths.get(args[0]);
        long maxFileSize = args.length > 1 ? Long.parseLong(args[1]) : 1_048_576L;
        int numBands = args.length > 2 ? Integer.parseInt(args[2]) : 8;

        System.out.printf("Directory   : %s%n", directory.toAbsolutePath());
        System.out.printf("maxFileSize : %,d bytes%n", maxFileSize);
        System.out.printf("numBands    : %d%n%n", numBands);

        return new ReportParameters(directory, maxFileSize, numBands);
    }

    /**
     * Run the supplied blocking supplier, print report and elapsed time, and run cleanup.
     */
    public static void runAndPrint(
            ReportParameters parsed, ThrowingSupplier<FSReport> supplier, Runnable cleanup) {
        Objects.requireNonNull(parsed);
        long start = System.currentTimeMillis();
        try {
            FSReport report = supplier.get();
            long elapsed = System.currentTimeMillis() - start;
            System.out.println(report);
            System.out.printf("  Elapsed: %d ms%n", elapsed);
        } catch (Throwable t) {
            System.err.println("Scan failed: " + t.getMessage());
            t.printStackTrace(System.err);
        } finally {
            if (cleanup != null) {
                try { cleanup.run(); } catch (Exception ignored) {}
            }
        }
    }
}

