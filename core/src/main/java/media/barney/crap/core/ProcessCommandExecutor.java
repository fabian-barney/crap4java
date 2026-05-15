package media.barney.crap.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

final class ProcessCommandExecutor implements CommandExecutor {

    private static final int CAPTURE_LIMIT_BYTES = 64 * 1024;
    private static final Duration DEFAULT_OUTPUT_PIPE_JOIN_GRACE = Duration.ofSeconds(5);

    private final Duration timeout;
    private final PrintStream processOutput;
    private final Charset outputCharset;
    private final Duration outputPipeJoinGrace;

    ProcessCommandExecutor() {
        this(Duration.ofMinutes(10), System.err);
    }

    ProcessCommandExecutor(Duration timeout) {
        this(timeout, System.err);
    }

    ProcessCommandExecutor(Duration timeout, PrintStream processOutput) {
        this(timeout, processOutput, Charset.defaultCharset());
    }

    ProcessCommandExecutor(Duration timeout, PrintStream processOutput, Charset outputCharset) {
        this(timeout, processOutput, outputCharset, DEFAULT_OUTPUT_PIPE_JOIN_GRACE);
    }

    ProcessCommandExecutor(
            Duration timeout,
            PrintStream processOutput,
            Charset outputCharset,
            Duration outputPipeJoinGrace
    ) {
        this.timeout = timeout;
        this.processOutput = processOutput;
        this.outputCharset = outputCharset;
        this.outputPipeJoinGrace = outputPipeJoinGrace;
    }

    @Override
    public int run(List<String> command, Path directory) throws Exception {
        return runWithResult(command, directory).exitCode();
    }

    @Override
    public CommandResult runWithResult(List<String> command, Path directory) throws Exception {
        ProcessTimeout.validate(timeout);
        validateOutputPipeJoinGrace();
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .start();
        process.getOutputStream().close();
        OutputPipe stdout = pipe(process.getInputStream(), "stdout");
        OutputPipe stderr = pipe(process.getErrorStream(), "stderr");
        int exit = ProcessTimeout.waitForOrTerminate(process, command, timeout, "Command");
        stdout.join(outputPipeJoinGrace);
        stderr.join(outputPipeJoinGrace);
        return new CommandResult(exit, stdout.output(), stderr.output());
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
        return new OutputPipe(thread, capture, outputCharset);
    }

    private void copyOutput(InputStream input, BoundedOutputCapture capture) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            processOutput.write(buffer, 0, read);
            processOutput.flush();
            capture.write(buffer, 0, read);
        }
    }

    private void validateOutputPipeJoinGrace() {
        if (outputPipeJoinGrace.isZero() || outputPipeJoinGrace.isNegative()) {
            throw new IllegalArgumentException("Process output pipe join grace must be positive");
        }
    }

    private record OutputPipe(Thread thread, BoundedOutputCapture capture, Charset outputCharset) {
        private void join(Duration grace) throws InterruptedException {
            thread.join(joinMillis(grace));
            if (thread.isAlive()) {
                thread.interrupt();
            }
        }

        private static long joinMillis(Duration grace) {
            long millis = grace.toMillis();
            return millis > 0 ? millis : 1L;
        }

        private String output() {
            return capture.toString(outputCharset);
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

