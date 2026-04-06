package media.barney.crap.core;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class ProcessCommandExecutor implements CommandExecutor {

    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(5);

    private final Duration timeout;

    ProcessCommandExecutor() {
        this(Duration.ofMinutes(10));
    }

    ProcessCommandExecutor(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public int run(List<String> command, Path directory) throws Exception {
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .inheritIO()
                .start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            if (!process.waitFor(TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(
                        "Command timed out after " + timeout + " and could not be terminated within "
                                + TERMINATION_TIMEOUT + ": " + String.join(" ", command));
            }
            throw new IllegalStateException("Command timed out after " + timeout + ": " + String.join(" ", command));
        }
        return process.exitValue();
    }
}

