package media.barney.crap4java.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceFileFinderTest {

    @TempDir
    Path tempDir;

    @Test
    void findsAllJavaFilesUnderSrcOnly() throws Exception {
        Path src = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(src);
        Path inSrc = src.resolve("Sample.java");
        Files.writeString(inSrc, "class Sample {}\n");

        Path outOfSrc = tempDir.resolve("other/Elsewhere.java");
        Files.createDirectories(outOfSrc.getParent());
        Files.writeString(outOfSrc, "class Elsewhere {}\n");

        List<Path> files = SourceFileFinder.findAllJavaFilesUnderSrc(tempDir);

        assertEquals(List.of(inSrc), files);
    }
}
