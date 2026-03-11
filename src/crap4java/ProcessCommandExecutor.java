package crap4java;

import java.nio.file.Path;
import java.util.List;

final class ProcessCommandExecutor implements CommandExecutor {

    @Override
    public int run(List<String> command, Path directory) throws Exception {
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .inheritIO()
                .start();
        return process.waitFor();
    }
}
