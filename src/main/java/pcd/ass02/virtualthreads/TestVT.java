package pcd.ass02.virtualthreads;

import pcd.ass02.common.TestLibUtils;

/**
 * Test entry-point for the Virtual Threads implementation.
 */
public class TestVT {

    public static void main(String[] args) {
        var parsed = TestLibUtils.parseArgs(args);

        try (var lib = new FSStatLibVT()) {
            TestLibUtils.runAndPrint(parsed,
                    () -> lib.getFSReport(parsed).get(),
                    null);
        }
    }
}

