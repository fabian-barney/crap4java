package media.barney.crap.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

record ReportOptions(
        ReportFormat format,
        boolean failuresOnly,
        boolean omitRedundancy,
        @Nullable Path outputPath,
        @Nullable Path junitReportPath
) {
    ReportOptions {
        outputPath = normalize(outputPath);
        junitReportPath = normalize(junitReportPath);
        if (outputPath != null && junitReportPath != null && sameReportTarget(outputPath, junitReportPath)) {
            throw new IllegalArgumentException("output and junitReport must not point to the same file");
        }
    }

    static ReportOptions textWithOptionalJunit(@Nullable Path junitReportPath) {
        return new ReportOptions(ReportFormat.TEXT, false, false, null, junitReportPath);
    }

    private static @Nullable Path normalize(@Nullable Path path) {
        if (path == null) {
            return null;
        }
        return path.toAbsolutePath().normalize();
    }

    private static boolean sameReportTarget(Path first, Path second) {
        if (first.equals(second)) {
            return true;
        }
        try {
            return sameExistingFile(first, second) || sameParentAndFileName(first, second);
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean sameExistingFile(Path first, Path second) throws IOException {
        return Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second);
    }

    private static boolean sameParentAndFileName(Path first, Path second) throws IOException {
        Path firstParent = first.getParent();
        Path secondParent = second.getParent();
        return first.getFileName().equals(second.getFileName())
                && firstParent != null
                && secondParent != null
                && Files.exists(firstParent)
                && Files.exists(secondParent)
                && Files.isSameFile(firstParent, secondParent);
    }
}
