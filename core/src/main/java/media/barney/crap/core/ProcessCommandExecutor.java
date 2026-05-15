package media.barney.crap.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class ProcessCommandExecutor implements CommandExecutor {

    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(5);
    private static final int CAPTURE_LIMIT_BYTES = 64 * 1024;

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
        OutputPipe stdout = pipe(process.getInputStream(), "stdout");
        OutputPipe stderr = pipe(process.getErrorStream(), "stderr");
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

    private OutputPipe pipe(InputStream input, String label) {
        BoundedOutputCapture capture = new BoundedOutputCapture();
        Thread thread = new Thread(() -> {
            try (input) {
                copyOutput(input, capture);
            } catch (Exception ex) {
                processOutput.println("Failed to read process output: " + ex.getMessage());
            }
        }, "crap-java-process-output-" + label);
        thread.setDaemon(true);
        thread.start();
        return new OutputPipe(thread, capture);
    }

    private void copyOutput(InputStream input, BoundedOutputCapture capture) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            processOutput.write(buffer, 0, read);
            capture.write(buffer, 0, read);
        }
        processOutput.flush();
    }

    private record OutputPipe(Thread thread, BoundedOutputCapture capture) {
        private void join() throws InterruptedException {
            thread.join();
        }

        private String output() {
            return capture.toString(StandardCharsets.UTF_8);
        }
    }

    private static final class BoundedOutputCapture {
        private final byte[] tail = new byte[CAPTURE_LIMIT_BYTES];
        private int nextIndex;
        private int length;
        private long totalBytes;

        private void write(byte[] buffer, int offset, int count) {
            int keptCount = Math.min(count, tail.length);
            int keptOffset = offset + count - keptCount;
            copy(buffer, keptOffset, keptCount);
            length = Math.min(length + keptCount, tail.length);
            totalBytes += count;
        }

        private void copy(byte[] buffer, int offset, int count) {
            int firstCount = Math.min(count, tail.length - nextIndex);
            System.arraycopy(buffer, offset, tail, nextIndex, firstCount);
            int secondCount = count - firstCount;
            if (secondCount > 0) {
                System.arraycopy(buffer, offset + firstCount, tail, 0, secondCount);
            }
            nextIndex = (nextIndex + count) % tail.length;
        }

        private String toString(java.nio.charset.Charset charset) {
            String text = new String(bytes(), charset);
            if (totalBytes <= CAPTURE_LIMIT_BYTES) {
                return text;
            }
            return "[captured output truncated to last " + CAPTURE_LIMIT_BYTES + " bytes]" + System.lineSeparator()
                    + text;
        }

        private byte[] bytes() {
            byte[] output = new byte[length];
            int startIndex = length == tail.length ? nextIndex : 0;
            for (int index = 0; index < length; index++) {
                output[index] = tail[(startIndex + index) % tail.length];
            }
            return output;
        }
    }
}

