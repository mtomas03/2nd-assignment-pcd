package pcd.fsstat.virtualthreads;

import pcd.fsstat.common.FSReport;
import pcd.fsstat.common.TestLibUtils;

/**
 * Test entry-point for the Virtual Threads implementation.
 */
public class TestVT {

    public static void main(String[] args) {
        var parsed = TestLibUtils.parseArgs(args);

        try (var lib = new FSStatLibVT()) {
            long start = System.currentTimeMillis();
            FSReport report = lib.getFSReport(parsed).get();
            long elapsed = System.currentTimeMillis() - start;

            TestLibUtils.printReport(report, elapsed);
        } catch (Exception e) {
            System.err.println("Scan failed: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
}
