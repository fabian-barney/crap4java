package crap4java;

import java.util.ArrayList;
import java.util.List;

final class CliArgumentsParser {

    private CliArgumentsParser() {
    }

    static CliArguments parse(String[] args) {
        if (args.length == 0) {
            return new CliArguments(CliMode.ALL_SRC, List.of());
        }

        if (containsFlag(args, "--help")) {
            return new CliArguments(CliMode.HELP, List.of());
        }

        boolean changed = containsFlag(args, "--changed");
        List<String> values = nonFlagArgs(args);
        ensureChangedIsNotCombined(changed, values);
        if (changed) {
            return new CliArguments(CliMode.CHANGED_SRC, List.of());
        }
        return new CliArguments(CliMode.EXPLICIT_FILES, List.copyOf(values));
    }

    private static boolean containsFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> nonFlagArgs(String[] args) {
        List<String> values = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                continue;
            }
            values.add(arg);
        }
        return values;
    }

    private static void ensureChangedIsNotCombined(boolean changed, List<String> values) {
        if (changed && !values.isEmpty()) {
            throw new IllegalArgumentException("--changed cannot be combined with file arguments");
        }
    }
}
