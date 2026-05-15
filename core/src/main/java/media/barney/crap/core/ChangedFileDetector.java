package media.barney.crap.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

final class ChangedFileDetector {

    private static final Duration GIT_STATUS_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(5);
    private static final List<String> GIT_COMMAND = List.of("git");

    private ChangedFileDetector() {
    }

    static List<Path> changedJavaFiles(Path projectRoot) throws IOException, InterruptedException {
        return changedJavaFiles(projectRoot, GIT_COMMAND, GIT_STATUS_TIMEOUT);
    }

    static List<Path> changedJavaFiles(Path projectRoot,
                                       List<String> gitCommand,
                                       Duration timeout) throws IOException, InterruptedException {
        List<String> command = gitStatusCommand(projectRoot, gitCommand);
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        process.getOutputStream().close();
        CompletableFuture<byte[]> outputFuture = readOutput(process);
        int exit = waitFor(process, command, timeout);
        byte[] outputBytes = outputBytes(outputFuture);
        String output = new String(outputBytes, StandardCharsets.UTF_8);
        if (exit != 0) {
            throw new IllegalStateException("git status failed: " + output);
        }

        List<Path> files = new ArrayList<>();
        List<String> entries = nullDelimitedEntries(output);
        for (int index = 0; index < entries.size(); index++) {
            StatusEntry entry = parseStatusEntry(entries.get(index));
            if (entry == null) {
                continue;
            }
            if (entry.hasOriginalPathToken()) {
                index++;
            }
            Path file = entry.toPath(projectRoot);
            if (file != null) {
                files.add(file);
            }
        }
        files.sort(Path::compareTo);
        return files;
    }

    private static List<String> gitStatusCommand(Path projectRoot, List<String> gitCommand) {
        List<String> command = new ArrayList<>(gitCommand);
        command.add("-C");
        command.add(projectRoot.toString());
        command.add("status");
        command.add("--porcelain=v1");
        command.add("-z");
        command.add("--untracked-files=all");
        return command;
    }

    private static CompletableFuture<byte[]> readOutput(Process process) {
        return CompletableFuture.supplyAsync(() -> {
            try (var input = process.getInputStream()) {
                return input.readAllBytes();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
    }

    private static int waitFor(Process process,
                               List<String> command,
                               Duration timeout) throws InterruptedException {
        if (process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            return process.exitValue();
        }
        process.destroyForcibly();
        if (!process.waitFor(TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException(
                    "git status timed out after " + timeout + " and could not be terminated within "
                            + TERMINATION_TIMEOUT + ": " + String.join(" ", command));
        }
        throw new IllegalStateException("git status timed out after " + timeout + ": " + String.join(" ", command));
    }

    private static byte[] outputBytes(CompletableFuture<byte[]> outputFuture) throws IOException, InterruptedException {
        try {
            return outputFuture.get();
        } catch (ExecutionException ex) {
            throw outputException(ex.getCause());
        }
    }

    private static IOException outputException(@Nullable Throwable cause) {
        if (cause instanceof UncheckedIOException io) {
            return uncheckedIoCause(io);
        }
        return wrappedOutputException(cause);
    }

    private static IOException uncheckedIoCause(UncheckedIOException error) {
        @Nullable IOException cause = error.getCause();
        return cause == null ? new IOException("Unable to read git status output", error) : cause;
    }

    private static IOException wrappedOutputException(@Nullable Throwable cause) {
        return cause == null
                ? new IOException("Unable to read git status output")
                : new IOException("Unable to read git status output", cause);
    }

    static List<Path> changedJavaFilesUnderSourceRoots(Path projectRoot) throws IOException, InterruptedException {
        return changedJavaFiles(projectRoot).stream()
                .filter(ProductionSourceRoots::isUnderProductionSourceRoot)
                .toList();
    }

    private static List<String> nullDelimitedEntries(String output) {
        List<String> entries = new ArrayList<>();
        int start = 0;
        while (start < output.length()) {
            int separator = output.indexOf('\0', start);
            if (separator < 0) {
                break;
            }
            entries.add(output.substring(start, separator));
            start = separator + 1;
        }
        return entries;
    }

    private static @Nullable StatusEntry parseStatusEntry(String entry) {
        if (entry.length() < 4 || entry.charAt(2) != ' ') {
            return null;
        }
        String status = entry.substring(0, 2);
        String path = entry.substring(3);
        return new StatusEntry(status, path, hasOriginalPathToken(status));
    }

    private static boolean hasOriginalPathToken(String status) {
        return status.indexOf('R') >= 0 || status.indexOf('C') >= 0;
    }

    private static boolean isJavaPath(String path) {
        return path.endsWith(".java");
    }

    private record StatusEntry(String status, String path, boolean hasOriginalPathToken) {

        private @Nullable Path toPath(Path root) {
            if (!isCandidate() || !isJavaPath(path)) {
                return null;
            }
            return root.resolve(path).normalize();
        }

        private boolean isCandidate() {
            if ("!!".equals(status) || status.indexOf('D') >= 0) {
                return false;
            }
            return "??".equals(status) || status.charAt(0) != ' ' || status.charAt(1) != ' ';
        }
    }
}

