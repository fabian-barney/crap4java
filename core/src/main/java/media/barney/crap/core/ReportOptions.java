package media.barney.crap.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
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
        return sameParent(firstParent, secondParent) && sameFileName(first, second, firstParent);
    }

    private static boolean sameParent(@Nullable Path firstParent, @Nullable Path secondParent) throws IOException {
        if (firstParent == null || secondParent == null) {
            return firstParent == secondParent;
        }
        return firstParent.equals(secondParent)
                || (Files.exists(firstParent) && Files.exists(secondParent) && Files.isSameFile(firstParent, secondParent));
    }

    private static boolean sameFileName(Path first, Path second, @Nullable Path parent) {
        String firstName = first.getFileName().toString();
        String secondName = second.getFileName().toString();
        return firstName.equals(secondName)
                || (firstName.equalsIgnoreCase(secondName) && isCaseInsensitive(parent));
    }

    private static boolean isCaseInsensitive(@Nullable Path path) {
        Path directory = nearestExistingDirectory(path);
        if (directory == null) {
            return isLikelyCaseInsensitiveOs();
        }
        try {
            Path probe = Files.createTempFile(directory, ".crap-java-case-", ".tmp");
            try {
                Path variant = probe.resolveSibling(probe.getFileName().toString().toUpperCase(Locale.ROOT));
                return !probe.getFileName().toString().equals(variant.getFileName().toString()) && Files.exists(variant);
            } finally {
                Files.deleteIfExists(probe);
            }
        } catch (IOException | SecurityException exception) {
            return isLikelyCaseInsensitiveOs();
        }
    }

    private static @Nullable Path nearestExistingDirectory(@Nullable Path path) {
        Path current = path == null ? Path.of(".").toAbsolutePath().normalize() : path.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private static boolean isLikelyCaseInsensitiveOs() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") || os.contains("mac");
    }
}
