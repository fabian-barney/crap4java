package media.barney.crap.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class TestJavaCommand {

    private TestJavaCommand() {
    }

    static List<String> command(Class<?> mainClass) {
        return List.of(
                executable(),
                "-cp",
                System.getProperty("java.class.path"),
                mainClass.getName()
        );
    }

    private static String executable() {
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }
}
