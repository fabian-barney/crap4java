package media.barney.crap.core;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

final class CoverageRunner {

    private final CommandExecutor executor;

    CoverageRunner(CommandExecutor executor) {
        this.executor = executor;
    }

    void generateCoverage(ProjectModule module) throws Exception {
        Path moduleRootRealPath = module.moduleRoot().toRealPath();
        for (Path staleCoveragePath : module.staleCoveragePaths()) {
            deleteIfExists(module.moduleRoot(), moduleRootRealPath, staleCoveragePath);
        }

        List<String> command = module.coverageCommand();
        CommandResult result = executor.runWithResult(command, module.executionRoot());
        if (result.exitCode() != 0) {
            throw new IllegalStateException(failureMessage(module, command, result));
        }
    }

    private String failureMessage(ProjectModule module, List<String> command, CommandResult result) {
        StringBuilder message = new StringBuilder()
                .append("Coverage command ")
                .append(command)
                .append(" failed in ")
                .append(module.executionRoot().toAbsolutePath().normalize())
                .append(" with exit ")
                .append(result.exitCode());
        appendOutput(message, "stderr", result.stderr());
        appendOutput(message, "stdout", result.stdout());
        return message.toString();
    }

    private void appendOutput(StringBuilder message, String label, String output) {
        String trimmed = output.stripTrailing();
        if (!trimmed.isEmpty()) {
            message.append(System.lineSeparator())
                    .append(label)
                    .append(':')
                    .append(System.lineSeparator())
                    .append(trimmed);
        }
    }

    private void deleteIfExists(Path moduleRoot, Path moduleRootRealPath, Path path) throws IOException {
        Path normalizedPath = path.normalize();
        if (!normalizedPath.startsWith(moduleRoot.normalize())) {
            throw new IllegalStateException("Refusing to delete stale coverage outside module root: " + normalizedPath);
        }
        if (!Files.exists(normalizedPath, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        deleteRecursively(moduleRootRealPath, normalizedPath);
    }

    private void deleteRecursively(Path moduleRootRealPath, Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (isLinkLike(attributes)) {
            Files.deleteIfExists(path);
            return;
        }

        Path realPath = path.toRealPath();
        if (!realPath.startsWith(moduleRootRealPath)) {
            throw new IllegalStateException("Refusing to delete stale coverage outside module root: " + path);
        }

        if (attributes.isDirectory()) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) {
                    deleteRecursively(moduleRootRealPath, entry);
                }
            }
        }

        Files.deleteIfExists(path);
    }

    private boolean isLinkLike(BasicFileAttributes attributes) {
        return attributes.isSymbolicLink() || attributes.isOther();
    }
}

