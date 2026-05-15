package media.barney.crap.core;

record CommandResult(int exitCode, String stdout, String stderr) {

    static CommandResult exitCode(int exitCode) {
        return new CommandResult(exitCode, "", "");
    }
}
