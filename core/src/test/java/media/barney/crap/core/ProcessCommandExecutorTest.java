package media.barney.crap.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessCommandExecutorTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsExitCodeFromLaunchedProcess() throws Exception {
        int exit = new ProcessCommandExecutor().run(exitCommand(7), tempDir);

        assertEquals(7, exit);
    }

    @Test
    void returnsCapturedProcessOutput() throws Exception {
        ByteArrayOutputStream streamedOutput = new ByteArrayOutputStream();
        ProcessCommandExecutor executor = new ProcessCommandExecutor(Duration.ofSeconds(5),
                new PrintStream(streamedOutput, true, StandardCharsets.UTF_8));

        CommandResult result = executor.runWithResult(outputCommand(), tempDir);

        assertEquals(3, result.exitCode());
        assertTrue(result.stdout().contains("stdout message"));
        assertTrue(result.stderr().contains("stderr message"));
        String streamed = streamedOutput.toString(StandardCharsets.UTF_8);
        assertTrue(streamed.contains("stdout message"));
        assertTrue(streamed.contains("stderr message"));
    }

    @Test
    void limitsCapturedProcessOutputToTail() throws Exception {
        ByteArrayOutputStream streamedOutput = new ByteArrayOutputStream();
        ProcessCommandExecutor executor = new ProcessCommandExecutor(Duration.ofSeconds(5),
                new PrintStream(streamedOutput, true, StandardCharsets.UTF_8));

        CommandResult result = executor.runWithResult(largeOutputCommand(), tempDir);

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("captured output truncated to last 65536 bytes"));
        assertFalse(result.stdout().contains("first-line"));
        assertTrue(result.stdout().contains("last-line"));
    }

    @Test
    void timesOutLongRunningProcesses() {
        ProcessCommandExecutor executor = new ProcessCommandExecutor(Duration.ofMillis(100));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> executor.run(sleepCommand(), tempDir));

        String message = Objects.requireNonNull(error.getMessage());
        assertTrue(message.contains("Command timed out"));
        assertTrue(message.contains(Duration.ofMillis(100).toString()));
        assertTrue(message.contains(String.join(" ", sleepCommand())));
    }

    private static List<String> exitCommand(int exitCode) {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return List.of("cmd", "/c", "exit " + exitCode);
        }
        return List.of("sh", "-c", "exit " + exitCode);
    }

    private static List<String> sleepCommand() {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return List.of("powershell", "-Command", "Start-Sleep -Seconds 5");
        }
        return List.of("sh", "-c", "sleep 5");
    }

    private static List<String> outputCommand() {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return List.of("cmd", "/c", "echo stdout message && echo stderr message 1>&2 && exit 3");
        }
        return List.of("sh", "-c", "printf 'stdout message\\n'; printf 'stderr message\\n' >&2; exit 3");
    }

    private static List<String> largeOutputCommand() {
        return List.of(
                javaExecutable(),
                "-cp",
                System.getProperty("java.class.path"),
                LargeOutput.class.getName()
        );
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    public static final class LargeOutput {
        public static void main(String[] args) {
            System.out.println("first-line");
            for (int index = 0; index < 70_000; index++) {
                System.out.print('x');
            }
            System.out.println();
            System.out.println("last-line");
        }
    }
}

