package pcd.common;

import java.nio.file.Path;

public record ReportParameters(Path directory, long maxFileSize, int numBands) {
    public ReportParameters withDirectory(Path directory) {
        return new ReportParameters(directory, maxFileSize, numBands);
    }
}
