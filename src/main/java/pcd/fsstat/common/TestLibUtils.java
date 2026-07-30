package pcd.fsstat.common;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Utility class for parsing CLI arguments and formatting report outputs.
 */
public final class TestLibUtils {

    private TestLibUtils() {}

    /**
     * Parses common CLI arguments.
     *
     * @param args command-line arguments: root directory, optional maxFileSize, and optional numBands
     * @return populated {@link ReportParameters} configuration object
     * @throws IllegalArgumentException if mandatory arguments are missing
     */
    public static ReportParameters parseArgs(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: <TestLib> <directory> [maxFileSize] [numBands]");
        }
        Path directory = Paths.get(args[0]);
        long maxFileSize = args.length > 1 ? Long.parseLong(args[1]) : 1000L;
        int numBands = args.length > 2 ? Integer.parseInt(args[2]) : 5;

        System.out.printf("Directory   : %s%n", directory.toAbsolutePath());
        System.out.printf("maxFileSize : %,d bytes%n", maxFileSize);
        System.out.printf("numBands    : %d%n%n", numBands);

        return new ReportParameters(directory, maxFileSize, numBands);
    }

    /**
     * Prints the completed {@link FSReport} statistics and the total elapsed scanning time.
     *
     * @param report    the generated report snapshot
     * @param elapsedMs scanning duration in milliseconds
     */
    public static void printReport(FSReport report, long elapsedMs) {
        Objects.requireNonNull(report, "report must not be null");
        System.out.println(report);
        System.out.printf("Elapsed: %d ms%n", elapsedMs);
    }
}
