package crap4java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProcessCommandExecutorTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsExitCodeFromLaunchedProcess() throws Exception {
        int exit = new ProcessCommandExecutor().run(List.of("/bin/sh", "-c", "exit 7"), tempDir);

        assertEquals(7, exit);
    }
}
