package crap4java;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

final class SourceFileFinder {

    private SourceFileFinder() {
    }

    static List<Path> findAllJavaFilesUnderSrc(Path projectRoot) throws IOException {
        Path src = projectRoot.resolve("src");
        if (!Files.exists(src)) {
            return List.of();
        }

        try (var stream = Files.walk(src)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }
}
