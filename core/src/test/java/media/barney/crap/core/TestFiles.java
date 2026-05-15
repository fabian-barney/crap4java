package media.barney.crap.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

final class TestFiles {

    private TestFiles() {
    }

    static void bestEffortDeleteTree(Path path) {
        try {
            if (!Files.exists(path)) {
                return;
            }
            List<Path> entries;
            try (var paths = Files.walk(path)) {
                entries = paths.sorted(Comparator.reverseOrder()).toList();
            }
            for (Path entry : entries) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException ex) {
            // Best-effort cleanup must not hide the primary assertion failure.
        }
    }
}
