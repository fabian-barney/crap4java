package media.barney.crap.core;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

final class ProcessCommandExecutor implements CommandExecutor {

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
        ProcessTimeout.validate(timeout);
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .start();
        process.getOutputStream().close();
        Thread stdout = pipe(process.getInputStream());
        Thread stderr = pipe(process.getErrorStream());
        int exit = ProcessTimeout.waitForOrTerminate(process, command, timeout, "Command");
        stdout.join();
        stderr.join();
        return exit;
    }

    private Thread pipe(InputStream input) {
        Thread thread = new Thread(() -> {
            try (input) {
                input.transferTo(processOutput);
            } catch (Exception ex) {
                processOutput.println("Failed to read process output: " + ex.getMessage());
            }
        }, "crap-java-process-output");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}

