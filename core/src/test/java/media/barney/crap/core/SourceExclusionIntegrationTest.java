package media.barney.crap.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceExclusionIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultGeneratedPathExclusionsPreventThresholdFailures() throws Exception {
        Path source = writeGeneratedSource();
        Path jacoco = writeZeroCoverageReport();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.runWithExistingCoverage(
                List.of(new Main.ResolvedCoverageModule(tempDir, jacoco, List.of(source))),
                tempDir,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                "json",
                false,
                false,
                false,
                null,
                null,
                1.0,
                SourceExclusionOptions.defaults()
        );

        String report = out.toString(StandardCharsets.UTF_8);
        assertEquals(0, exit);
        assertTrue(report.contains("\"excludedFiles\": 1"));
        assertFalse(report.contains("\"method\": \"alpha\""));
    }

    @Test
    void disabledDefaultsAllowGeneratedSourcesToFailThresholds() throws Exception {
        Path source = writeGeneratedSource();
        Path jacoco = writeZeroCoverageReport();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int exit = Main.runWithExistingCoverage(
                List.of(new Main.ResolvedCoverageModule(tempDir, jacoco, List.of(source))),
                tempDir,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                "none",
                false,
                false,
                false,
                null,
                null,
                1.0,
                new SourceExclusionOptions(List.of(), List.of(), List.of(), false)
        );

        assertEquals(2, exit);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("CRAP threshold exceeded"));
    }

    @Test
    void agentPrimaryReportOmitsExclusionAuditWhileJunitSidecarKeepsIt() throws Exception {
        Path source = writeGeneratedSource();
        Path jacoco = writeZeroCoverageReport();
        Path junit = tempDir.resolve("target/crap-java/TEST-crap-java.xml");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int exit = Main.runWithExistingCoverage(
                List.of(new Main.ResolvedCoverageModule(tempDir, jacoco, List.of(source))),
                tempDir,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                "json",
                true,
                true,
                true,
                null,
                junit,
                1.0,
                SourceExclusionOptions.defaults()
        );

        assertEquals(0, exit);
        assertFalse(out.toString(StandardCharsets.UTF_8).contains("\"exclusions\""));
        assertTrue(Files.readString(junit).contains("exclusion.excludedFiles"));
    }

    private Path writeGeneratedSource() throws Exception {
        Path source = tempDir.resolve("src/main/java/demo/generated/Sample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package demo.generated;

                class Sample {
                    int alpha(boolean a) {
                        if (a) {
                            return 1;
                        }
                        return 0;
                    }
                }
                """);
        return source;
    }

    private Path writeZeroCoverageReport() throws Exception {
        Path jacoco = tempDir.resolve("jacoco.xml");
        Files.writeString(jacoco, """
                <report>
                  <package name="demo/generated">
                    <class name="demo/generated/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="(Z)I" line="4">
                        <counter type="INSTRUCTION" missed="4" covered="0"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);
        return jacoco;
    }
}
