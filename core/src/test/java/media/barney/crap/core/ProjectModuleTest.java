package media.barney.crap.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectModuleTest {

    @TempDir
    Path tempDir;

    @Test
    void gradleFallbackLauncherUsesGradleBatOnWindows() {
        assertGradleFallbackLauncher("Windows 11", "gradle.bat");
    }

    @Test
    void gradleFallbackLauncherUsesGradleOnOtherPlatforms() {
        assertGradleFallbackLauncher("Linux", "gradle");
    }

    private void assertGradleFallbackLauncher(String osName, String expectedLauncher) {
        String originalOsName = System.getProperty("os.name");
        try {
            System.setProperty("os.name", osName);
            ProjectModule module = new ProjectModule(tempDir, tempDir, BuildTool.GRADLE);

            assertEquals(expectedLauncher, module.coverageCommand().get(0));
        } finally {
            if (originalOsName == null) {
                System.clearProperty("os.name");
            } else {
                System.setProperty("os.name", originalOsName);
            }
        }
    }
}
