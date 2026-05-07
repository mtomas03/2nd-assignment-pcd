package pcd.eventloop;

import pcd.common.TestLibUtils;

/**
 * Demo entry-point for the Vert.x event-loop implementation.
 */
public class TestEL {

    public static void main(String[] args) {
        var parsed = TestLibUtils.parseArgs(args);
        FSStatLibEventLoop lib = new FSStatLibEventLoop();
        TestLibUtils.runAndPrint(parsed,
                () -> lib.getFSReport(parsed)
                        .toCompletionStage().toCompletableFuture().get(),
                lib::shutdown);
    }
}

