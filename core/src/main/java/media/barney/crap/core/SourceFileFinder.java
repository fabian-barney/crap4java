package media.barney.crap.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class SourceFileFinder {

    private SourceFileFinder() {
    }

    static List<Path> findAllJavaFilesUnderSourceRoots(Path projectRoot) throws IOException {
        return findAllJavaFilesUnderSourceRoots(projectRoot, List.of());
    }

    static List<Path> findAllJavaFilesUnderSourceRoots(Path projectRoot, List<Path> configuredSourceRoots)
            throws IOException {
        if (!Files.isDirectory(projectRoot)) {
            return List.of();
        }

        Set<Path> javaFiles = new LinkedHashSet<>();
        for (Path sourceRoot : productionSourceRoots(projectRoot, configuredSourceRoots)) {
            javaFiles.addAll(javaFilesUnder(sourceRoot));
        }
        List<Path> sorted = new ArrayList<>(javaFiles);
        sorted.sort(Comparator.naturalOrder());
        return sorted;
    }

    private static List<Path> javaFilesUnder(Path sourceRoot) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        try (var stream = Files.walk(sourceRoot)) {
            // Directory symlinks are not followed because FOLLOW_LINKS is not passed.
            // Symlinked files are kept as link paths when Files.isRegularFile follows them.
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(javaFiles::add);
        }
        return javaFiles;
    }

    private static List<Path> productionSourceRoots(Path projectRoot, List<Path> configuredSourceRoots)
            throws IOException {
        List<Path> sourceRoots = new ArrayList<>();
        sourceRoots.addAll(existingAbsoluteSourceRootsUnder(projectRoot, configuredSourceRoots));
        Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (ProductionSourceRoots.matchesAnyCustomSourceRoot(dir, configuredSourceRoots)) {
                    sourceRoots.add(dir.normalize());
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (!dir.equals(projectRoot) && ProductionSourceRoots.isSkippableDirectory(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (ProductionSourceRoots.usesDefaultSourceRoots(configuredSourceRoots)
                        && ProductionSourceRoots.isProductionSourceRoot(dir)) {
                    sourceRoots.add(dir.normalize());
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return sourceRoots.stream()
                .distinct()
                .sorted()
                .toList();
    }

    private static List<Path> existingAbsoluteSourceRootsUnder(Path projectRoot, List<Path> configuredSourceRoots) {
        Path normalizedProjectRoot = projectRoot.toAbsolutePath().normalize();
        return configuredSourceRoots.stream()
                .map(Path::normalize)
                .filter(Path::isAbsolute)
                .peek(sourceRoot -> validateSourceRootUnderProjectRoot(normalizedProjectRoot, sourceRoot))
                .filter(Files::isDirectory)
                .toList();
    }

    private static void validateSourceRootUnderProjectRoot(Path projectRoot, Path sourceRoot) {
        if (!sourceRoot.startsWith(projectRoot)) {
            throw new IllegalArgumentException("Absolute source root must be under the analyzed path: " + sourceRoot);
        }
    }
}

