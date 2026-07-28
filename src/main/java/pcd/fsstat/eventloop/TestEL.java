package pcd.fsstat.eventloop;

import pcd.fsstat.common.TestLibUtils;

/**
 * Test entry-point for the event-loop implementation.
 */
public class TestEL {

    public static void main(String[] args) {
        var parsed = TestLibUtils.parseArgs(args);
        FSStatLibEL lib = new FSStatLibEL();
        TestLibUtils.runAndPrint(parsed,
                () -> lib.getFSReport(parsed)
                        .toCompletionStage().toCompletableFuture().get(),
                lib::shutdown);
    }
}

