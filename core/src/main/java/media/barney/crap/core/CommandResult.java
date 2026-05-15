package media.barney.crap.core;

import java.util.Objects;

record CommandResult(int exitCode, String stdout, String stderr) {

    CommandResult {
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");
    }

    static CommandResult exitCode(int exitCode) {
        return new CommandResult(exitCode, "", "");
    }
}
