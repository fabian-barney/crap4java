package media.barney.crap4java.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

final class SourceFileFinder {

    private SourceFileFinder() {
    }

    private static final Path PRODUCTION_SOURCE_ROOT = Path.of("src", "main", "java");

    static List<Path> findAllJavaFilesUnderSourceRoots(Path projectRoot) throws IOException {
        if (!Files.exists(projectRoot)) {
            return List.of();
        }

        try (var stream = Files.walk(projectRoot)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> isUnderSourceTree(projectRoot, path))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    private static boolean isUnderSourceTree(Path projectRoot, Path file) {
        Path normalized = projectRoot.normalize().relativize(file.normalize());
        for (int index = 0; index <= normalized.getNameCount() - PRODUCTION_SOURCE_ROOT.getNameCount(); index++) {
            if (matchesProductionSourceRoot(normalized, index)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesProductionSourceRoot(Path relativePath, int startIndex) {
        for (int offset = 0; offset < PRODUCTION_SOURCE_ROOT.getNameCount(); offset++) {
            if (!PRODUCTION_SOURCE_ROOT.getName(offset).toString().equals(relativePath.getName(startIndex + offset).toString())) {
                return false;
            }
        }
        return true;
    }
}
