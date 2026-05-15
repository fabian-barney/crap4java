package media.barney.crap.core;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class ProcessCommandExecutor implements CommandExecutor {

    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(5);

    private final Duration timeout;
    private final PrintStream processOutput;

    ProcessCommandExecutor() {
        this(Duration.ofMinutes(10), System.err);
    }

    ProcessCommandExecutor(Duration timeout) {
        this(timeout, System.err);
    }

    ProcessCommandExecutor(Duration timeout, PrintStream processOutput) {
        this.timeout = timeout;
        this.processOutput = processOutput;
    }

    @Override
    public int run(List<String> command, Path directory) throws Exception {
        return runWithResult(command, directory).exitCode();
    }

    @Override
    public CommandResult runWithResult(List<String> command, Path directory) throws Exception {
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .start();
        process.getOutputStream().close();
        OutputPipe stdout = pipe(process.getInputStream());
        OutputPipe stderr = pipe(process.getErrorStream());
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            if (!process.waitFor(TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(
                        "Command timed out after " + timeout + " and could not be terminated within "
                                + TERMINATION_TIMEOUT + ": " + String.join(" ", command));
            }
            throw new IllegalStateException("Command timed out after " + timeout + ": " + String.join(" ", command));
        }
        stdout.join();
        stderr.join();
        return new CommandResult(process.exitValue(), stdout.output(), stderr.output());
    }

    private OutputPipe pipe(InputStream input) {
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        Thread thread = new Thread(() -> {
            try (input) {
                copyOutput(input, capture);
            } catch (Exception ex) {
                processOutput.println("Failed to read process output: " + ex.getMessage());
            }
        }, "crap-java-process-output");
        thread.setDaemon(true);
        thread.start();
        return new OutputPipe(thread, capture);
    }

    private void copyOutput(InputStream input, ByteArrayOutputStream capture) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            processOutput.write(buffer, 0, read);
            capture.write(buffer, 0, read);
        }
        processOutput.flush();
    }

    private record OutputPipe(Thread thread, ByteArrayOutputStream capture) {
        private void join() throws InterruptedException {
            thread.join();
        }

        private String output() {
            return capture.toString(StandardCharsets.UTF_8);
        }
    }
}

