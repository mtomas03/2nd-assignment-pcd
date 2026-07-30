package pcd.fsstat.eventloop;

import pcd.fsstat.common.FSReport;
import pcd.fsstat.common.TestLibUtils;

/**
 * Test entry-point for the event-loop implementation.
 */
public class TestEL {

    public static void main(String[] args) {
        var parsed = TestLibUtils.parseArgs(args);
        FSStatLibEL lib = new FSStatLibEL();

        try {
            long start = System.currentTimeMillis();
            FSReport report = lib.getFSReport(parsed)
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get();
            long elapsed = System.currentTimeMillis() - start;

            TestLibUtils.printReport(report, elapsed);
        } catch (Exception e) {
            System.err.println("Scan failed: " + e.getMessage());
            e.printStackTrace(System.err);
        } finally {
            lib.shutdown();
        }
    }
}
