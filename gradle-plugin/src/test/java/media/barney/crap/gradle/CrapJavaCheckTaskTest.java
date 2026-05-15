package media.barney.crap.gradle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrapJavaCheckTaskTest {

    @Test
    void caseSensitivityFallbackTreatsWindowsAsInsensitive() {
        withOsName("Windows 11", () -> assertTrue(CrapJavaCheckTask.isLikelyCaseInsensitiveOs()));
    }

    @Test
    void caseSensitivityFallbackTreatsMacOsAsUnknown() {
        withOsName("Mac OS X", () -> assertFalse(CrapJavaCheckTask.isLikelyCaseInsensitiveOs()));
    }

    @Test
    void caseSensitivityFallbackDoesNotMatchDarwinAsWindows() {
        withOsName("Darwin", () -> assertFalse(CrapJavaCheckTask.isLikelyCaseInsensitiveOs()));
    }

    private static void withOsName(String osName, Runnable assertion) {
        String original = System.getProperty("os.name");
        try {
            System.setProperty("os.name", osName);
            assertion.run();
        } finally {
            if (original == null) {
                System.clearProperty("os.name");
            } else {
                System.setProperty("os.name", original);
            }
        }
    }
}
