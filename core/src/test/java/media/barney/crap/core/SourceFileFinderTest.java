package media.barney.crap.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceFileFinderTest {

    @TempDir
    Path tempDir;

    @Test
    void findsAllJavaFilesUnderProductionSourceRootsOnly() throws Exception {
        Path rootSrc = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(rootSrc);
        Path inRootSrc = rootSrc.resolve("Sample.java");
        Files.writeString(inRootSrc, "class Sample {}\n");

        Path nestedModuleSrc = tempDir.resolve("module-a/src/main/java/demo");
        Files.createDirectories(nestedModuleSrc);
        Path inNestedModuleSrc = nestedModuleSrc.resolve("NestedSample.java");
        Files.writeString(inNestedModuleSrc, "class NestedSample {}\n");

        Path skippedBuildSrc = tempDir.resolve("build/src/main/java/demo");
        Files.createDirectories(skippedBuildSrc);
        Path skippedBuildFile = skippedBuildSrc.resolve("Generated.java");
        Files.writeString(skippedBuildFile, "class Generated {}\n");

        Path generatedSrc = tempDir.resolve("build/generated/src/demo");
        Files.createDirectories(generatedSrc);
        Path generated = generatedSrc.resolve("Generated.java");
        Files.writeString(generated, "class Generated {}\n");

        Path outOfSrc = tempDir.resolve("other/Elsewhere.java");
        Files.createDirectories(outOfSrc.getParent());
        Files.writeString(outOfSrc, "class Elsewhere {}\n");

        List<Path> files = SourceFileFinder.findAllJavaFilesUnderSourceRoots(tempDir);
        List<Path> expected = new ArrayList<>(List.of(inRootSrc, inNestedModuleSrc));
        expected.sort(Path::compareTo);

        assertEquals(expected, files);
    }

    @Test
    void acceptsADirectoryThatIsAlreadyAProductionSourceRoot() throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(sourceRoot);
        Path source = sourceRoot.resolve("Sample.java");
        Files.writeString(source, "class Sample {}\n");

        List<Path> files = SourceFileFinder.findAllJavaFilesUnderSourceRoots(tempDir.resolve("src/main/java"));

        assertEquals(List.of(source), files);
    }

    @Test
    void supportsConfiguredRelativeSourceRoots() throws Exception {
        Path customSourceRoot = tempDir.resolve("src/java/demo");
        Files.createDirectories(customSourceRoot);
        Path customSource = customSourceRoot.resolve("Sample.java");
        Files.writeString(customSource, "class Sample {}\n");

        Path defaultSourceRoot = tempDir.resolve("src/main/java/demo");
        Files.createDirectories(defaultSourceRoot);
        Files.writeString(defaultSourceRoot.resolve("DefaultSample.java"), "class DefaultSample {}\n");

        Path nestedModuleSourceRoot = tempDir.resolve("module-a/src/java/demo");
        Files.createDirectories(nestedModuleSourceRoot);
        Path nestedModuleSource = nestedModuleSourceRoot.resolve("NestedSample.java");
        Files.writeString(nestedModuleSource, "class NestedSample {}\n");

        List<Path> files = SourceFileFinder.findAllJavaFilesUnderSourceRoots(tempDir, List.of(Path.of("src/java")));

        assertEquals(List.of(nestedModuleSource, customSource), files);
    }

    @Test
    void supportsConfiguredAbsoluteSourceRootsOutsideSearchedRoot() throws Exception {
        Path projectRoot = tempDir.resolve("project");
        Files.createDirectories(projectRoot);

        Path externalSourceRoot = tempDir.resolve("external-src/demo");
        Files.createDirectories(externalSourceRoot);
        Path externalSource = externalSourceRoot.resolve("ExternalSample.java");
        Files.writeString(externalSource, "class ExternalSample {}\n");

        List<Path> files = SourceFileFinder.findAllJavaFilesUnderSourceRoots(
                projectRoot,
                List.of(tempDir.resolve("external-src"))
        );

        assertEquals(List.of(externalSource), files);
    }
}

