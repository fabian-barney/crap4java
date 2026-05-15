package media.barney.crap.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectModuleTest {

    @TempDir
    Path tempDir;

    @Test
    void gradleFallbackLauncherMatchesTheCurrentPlatform() {
        ProjectModule module = new ProjectModule(tempDir, tempDir, BuildTool.GRADLE);

        assertEquals(gradleFallbackLauncher(), module.coverageCommand().get(0));
    }

    private static String gradleFallbackLauncher() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")
                ? "gradle.bat"
                : "gradle";
    }
}
