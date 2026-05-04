package media.barney.crap.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CrapJavaGradlePluginTest {

    @TempDir
    Path tempDir;

    @Test
    void applyRegistersVerificationTaskForJavaProjects() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CrapJavaGradlePlugin.class);

        CrapJavaCheckTask checkTask = (CrapJavaCheckTask) project.getTasks().getByName("crap-java-check");
        CrapJavaExtension extension = project.getExtensions().getByType(CrapJavaExtension.class);

        assertEquals("verification", checkTask.getGroup());
        assertEquals("Runs the crap-java CRAP metric gate.", checkTask.getDescription());
        assertEquals(8.0, extension.getThreshold().get());
        assertFalse(extension.getAgent().get());
        assertEquals("none", extension.getFormat().get());
        assertFalse(extension.getFailuresOnly().get());
        assertFalse(extension.getOmitRedundancy().get());
        assertFalse(extension.getOutput().isPresent());
        assertTrue(extension.getJunit().get());
        assertTrue(extension.getJunitReport().get().getAsFile().toPath().normalize().toString()
                .replace('\\', '/')
                .endsWith("build/reports/crap-java/TEST-crap-java.xml"));
        assertEquals(8.0, checkTask.getThreshold().get());
        assertEquals("none", checkTask.getFormat().get());
        assertFalse(checkTask.getAgent().get());
        assertFalse(checkTask.getFailuresOnly().get());
        assertFalse(checkTask.getOmitRedundancy().get());
        assertFalse(checkTask.getOutput().isPresent());
        assertTrue(checkTask.getJunit().get());
        Set<String> dependencyNames = checkTask.getTaskDependencies().getDependencies(checkTask).stream()
                .map(Task::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("test", "jacocoTestReport"), dependencyNames);
        assertEquals(Map.of(".", "build/reports/jacoco/test/jacocoTestReport.xml"), checkTask.getModuleCoverageReports().get());
        assertTrue(checkTask.getJunitReport().get().getAsFile().toPath().normalize().toString()
                .replace('\\', '/')
                .endsWith("build/reports/crap-java/TEST-crap-java.xml"));
        assertTrue(checkTask.getJunitReportOutput().isPresent());
        assertNotNull(project.getTasks().findByName("jacocoTestReport"));
    }

    @Test
    void configuredExtensionThresholdFlowsToCheckTask() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CrapJavaGradlePlugin.class);
        project.getExtensions().getByType(CrapJavaExtension.class).getThreshold().set(6.0);

        CrapJavaCheckTask checkTask = (CrapJavaCheckTask) project.getTasks().getByName("crap-java-check");

        assertEquals(6.0, checkTask.getThreshold().get());
    }

    @Test
    void configuredExtensionReportControlsFlowToCheckTask() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CrapJavaGradlePlugin.class);
        CrapJavaExtension extension = project.getExtensions().getByType(CrapJavaExtension.class);
        Path output = tempDir.resolve("build/reports/crap-java/report.json");
        Path junitReport = tempDir.resolve("build/reports/crap-java/custom-junit.xml");
        extension.getFormat().set("json");
        extension.getAgent().set(true);
        extension.getFailuresOnly().set(false);
        extension.getOmitRedundancy().set(true);
        extension.getOutput().fileValue(output.toFile());
        extension.getJunit().set(false);
        extension.getJunitReport().fileValue(junitReport.toFile());

        CrapJavaCheckTask checkTask = (CrapJavaCheckTask) project.getTasks().getByName("crap-java-check");

        assertEquals("json", checkTask.getFormat().get());
        assertTrue(checkTask.getAgent().get());
        assertFalse(checkTask.getFailuresOnly().get());
        assertTrue(checkTask.getOmitRedundancy().get());
        assertEquals(output.normalize(), checkTask.getOutput().get().getAsFile().toPath().normalize());
        assertFalse(checkTask.getJunit().get());
        assertEquals(junitReport.normalize(), checkTask.getJunitReport().get().getAsFile().toPath().normalize());
        assertFalse(checkTask.getJunitReportOutput().isPresent());
    }

    @Test
    void directlyRegisteredCheckTaskHasReportControlDefaults() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        CrapJavaCheckTask checkTask = project.getTasks().register("custom-crap-java-check", CrapJavaCheckTask.class).get();

        assertEquals(8.0, checkTask.getThreshold().get());
        assertFalse(checkTask.getAgent().get());
        assertEquals("none", checkTask.getFormat().get());
        assertFalse(checkTask.getFailuresOnly().get());
        assertFalse(checkTask.getOmitRedundancy().get());
        assertFalse(checkTask.getOutput().isPresent());
        assertTrue(checkTask.getJunit().get());
        assertTrue(checkTask.getJunitReport().get().getAsFile().toPath().normalize().toString()
                .replace('\\', '/')
                .endsWith("build/reports/crap-java/custom-crap-java-check/TEST-crap-java.xml"));
        assertTrue(checkTask.getJunitReportOutput().isPresent());
    }

    @Test
    void agentExtensionComposesPrimaryDefaultsWhenControlsAreUnset() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CrapJavaGradlePlugin.class);
        project.getExtensions().getByType(CrapJavaExtension.class).getAgent().set(true);

        CrapJavaCheckTask checkTask = (CrapJavaCheckTask) project.getTasks().getByName("crap-java-check");
        CrapJavaExtension extension = project.getExtensions().getByType(CrapJavaExtension.class);

        assertEquals("toon", extension.getFormat().get());
        assertTrue(extension.getFailuresOnly().get());
        assertTrue(extension.getOmitRedundancy().get());
        assertEquals("toon", checkTask.getFormat().get());
        assertTrue(checkTask.getFailuresOnly().get());
        assertTrue(checkTask.getOmitRedundancy().get());
    }

    @Test
    void agentTaskComposesPrimaryDefaultsWhenControlsAreUnset() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CrapJavaGradlePlugin.class);
        CrapJavaCheckTask checkTask = (CrapJavaCheckTask) project.getTasks().getByName("crap-java-check");
        checkTask.getAgent().set(true);

        assertEquals("toon", checkTask.getFormat().get());
        assertTrue(checkTask.getFailuresOnly().get());
        assertTrue(checkTask.getOmitRedundancy().get());
    }

    @Test
    void taskAgentFalseOverridesExtensionAgentComposedDefaults() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CrapJavaGradlePlugin.class);
        project.getExtensions().getByType(CrapJavaExtension.class).getAgent().set(true);
        CrapJavaCheckTask checkTask = (CrapJavaCheckTask) project.getTasks().getByName("crap-java-check");
        checkTask.getAgent().set(false);

        assertEquals("none", checkTask.getFormat().get());
        assertFalse(checkTask.getFailuresOnly().get());
        assertFalse(checkTask.getOmitRedundancy().get());
    }

    @Test
    void configuredTaskReportControlsOverrideExtensionDefaults() {
        Project project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build();

        project.getPluginManager().apply("java");
        project.getPluginManager().apply(CrapJavaGradlePlugin.class);
        CrapJavaCheckTask checkTask = (CrapJavaCheckTask) project.getTasks().getByName("crap-java-check");
        Path output = tempDir.resolve("build/reports/crap-java/task-report.json");
        Path junitReport = tempDir.resolve("build/reports/crap-java/task-junit.xml");
        checkTask.getAgent().set(true);
        checkTask.getFormat().set("json");
        checkTask.getFailuresOnly().set(false);
        checkTask.getOmitRedundancy().set(true);
        checkTask.getOutput().fileValue(output.toFile());
        checkTask.getJunit().set(false);
        checkTask.getJunitReport().fileValue(junitReport.toFile());

        assertEquals("json", checkTask.getFormat().get());
        assertTrue(checkTask.getAgent().get());
        assertFalse(checkTask.getFailuresOnly().get());
        assertTrue(checkTask.getOmitRedundancy().get());
        assertEquals(output.normalize(), checkTask.getOutput().get().getAsFile().toPath().normalize());
        assertFalse(checkTask.getJunit().get());
        assertEquals(junitReport.normalize(), checkTask.getJunitReport().get().getAsFile().toPath().normalize());
        assertFalse(checkTask.getJunitReportOutput().isPresent());
    }

    @Test
    void runCheckAnalyzesConfiguredSourcesWithExistingCoverage() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Files.writeString(projectRoot.resolve("build.gradle"), "plugins { id 'java' }");
        Path source = projectRoot.resolve("src/main/java/demo/Sample.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package demo;

                class Sample {
                    int alpha() {
                        return 1;
                    }
                }
                """);
        Path jacocoXml = projectRoot.resolve("build/reports/jacoco/test/jacocoTestReport.xml");
        Files.createDirectories(jacocoXml.getParent());
        Files.writeString(jacocoXml, """
                <report name="demo">
                  <package name="demo">
                    <class name="demo/Sample" sourcefilename="Sample.java">
                      <method name="alpha" desc="()I" line="4">
                        <counter type="INSTRUCTION" missed="0" covered="1"/>
                      </method>
                    </class>
                  </package>
                </report>
                """);

        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getAnalysisSources().from(source);
        task.getCoverageReports().from(jacocoXml);
        task.getModuleCoverageReports().put(".", "build/reports/jacoco/test/jacocoTestReport.xml");
        task.getThreshold().set(8.0);
        task.getFormat().set("none");
        task.getAgent().set(false);
        task.getFailuresOnly().set(false);
        task.getOmitRedundancy().set(false);
        task.getJunit().set(true);
        Path junitReport = projectRoot.resolve("build/reports/crap-java/TEST-crap-java.xml");
        task.getJunitReport().fileValue(junitReport.toFile());

        task.runCheck();

        assertTrue(Files.exists(jacocoXml));
        assertTrue(Files.readString(junitReport).contains("<testsuites tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\" time=\"0\">"));
    }

    @Test
    void movedJunitReportDoesNotDeleteUnownedDefaultSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path defaultJunitReport = projectRoot.resolve("build/reports/crap-java/TEST-crap-java.xml");
        Path customJunitReport = projectRoot.resolve("custom-junit.xml");
        Files.createDirectories(defaultJunitReport.getParent());
        Files.writeString(defaultJunitReport, "user-managed");
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getJunitReport().fileValue(customJunitReport.toFile());

        task.runCheck();

        assertTrue(Files.exists(customJunitReport));
        assertEquals("user-managed", Files.readString(defaultJunitReport));
    }

    @Test
    void disabledJunitDoesNotDeleteUnownedDefaultSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path defaultJunitReport = projectRoot.resolve("build/reports/crap-java/TEST-crap-java.xml");
        Files.createDirectories(defaultJunitReport.getParent());
        Files.writeString(defaultJunitReport, "user-managed");
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getJunit().set(false);

        task.runCheck();

        assertEquals("user-managed", Files.readString(defaultJunitReport));
    }

    @Test
    void movedJunitReportDeletesOwnedRememberedDefaultSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Path defaultJunitReport = projectRoot.resolve("build/reports/crap-java/TEST-crap-java.xml");
        Path customJunitReport = projectRoot.resolve("custom-junit.xml");
        CrapJavaCheckTask firstTask = newCheckTask(projectRoot);
        firstTask.runCheck();
        assertTrue(Files.exists(defaultJunitReport));

        CrapJavaCheckTask secondTask = newCheckTask(projectRoot);
        secondTask.getJunitReport().fileValue(customJunitReport.toFile());

        secondTask.runCheck();

        assertTrue(Files.exists(customJunitReport));
        assertFalse(Files.exists(defaultJunitReport));
    }

    @Test
    void disabledJunitDeletesOwnedRememberedDefaultSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Path defaultJunitReport = projectRoot.resolve("build/reports/crap-java/TEST-crap-java.xml");
        CrapJavaCheckTask firstTask = newCheckTask(projectRoot);
        firstTask.runCheck();
        assertTrue(Files.exists(defaultJunitReport));

        CrapJavaCheckTask secondTask = newCheckTask(projectRoot);
        secondTask.getJunit().set(false);

        secondTask.runCheck();

        assertFalse(Files.exists(defaultJunitReport));
    }

    @Test
    void disabledJunitDeletesOwnedRememberedExternalSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path junitReport = projectRoot.resolve("outside-junit.xml");
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getJunitReport().fileValue(junitReport.toFile());
        task.runCheck();
        assertTrue(Files.exists(junitReport));

        task.getJunit().set(false);
        task.runCheck();

        assertFalse(Files.exists(junitReport));
    }

    @Test
    void disabledJunitKeepsRecreatedRememberedExternalSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path junitReport = projectRoot.resolve("outside-junit.xml");
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getJunitReport().fileValue(junitReport.toFile());
        task.runCheck();
        Files.writeString(junitReport, "unrelated");

        task.getJunit().set(false);
        task.runCheck();

        assertEquals("unrelated", Files.readString(junitReport));
    }

    @Test
    void disabledJunitKeepsRecreatedIdenticalRememberedExternalSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path junitReport = projectRoot.resolve("outside-junit.xml");
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getJunitReport().fileValue(junitReport.toFile());
        task.runCheck();
        String originalReport = Files.readString(junitReport);
        FileTime originalModified = Files.getLastModifiedTime(junitReport);
        Files.delete(junitReport);
        Files.writeString(junitReport, originalReport);
        Files.setLastModifiedTime(junitReport, FileTime.fromMillis(originalModified.toMillis() + 5_000));

        task.getJunit().set(false);
        task.runCheck();

        assertEquals(originalReport, Files.readString(junitReport));
    }

    @Test
    void movedOutputDoesNotDeleteCurrentJunitReportAtPreviousOutputPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path sharedReport = projectRoot.resolve("shared-report.xml");
        Files.writeString(sharedReport, "<testsuites tests=\"0\"/>");
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        rememberOwnedReport(
                projectRoot.resolve(".gradle/crap-java/root/crap-java-check/primary-output.path"),
                sharedReport
        );

        invokeCleanupStaleReports(task, null, sharedReport);

        assertTrue(Files.exists(sharedReport));
    }

    @Test
    void failedJunitPublishRemembersWrittenPrimaryOutput() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path output = projectRoot.resolve("primary.json");
        Path junitParentFile = projectRoot.resolve("junit-parent-file");
        Path badJunitReport = junitParentFile.resolve("TEST-crap-java.xml");
        Files.writeString(junitParentFile, "not a directory");
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getFormat().set("json");
        task.getOutput().fileValue(output.toFile());
        task.getJunitReport().fileValue(badJunitReport.toFile());

        assertThrows(Exception.class, task::runCheck);

        assertTrue(Files.exists(output));
        assertTrue(Files.exists(projectRoot.resolve(".gradle/crap-java/root/crap-java-check/primary-output.path")));
    }

    @Test
    void invalidReportPathDoesNotDeleteRememberedJunitSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path junitReport = projectRoot.resolve("outside-junit.xml");
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getJunitReport().fileValue(junitReport.toFile());
        task.runCheck();
        assertTrue(Files.exists(junitReport));

        task.getJunit().set(false);
        task.getOutput().fileValue(projectRoot.resolve(".gradle/crap-java/root/other-task/primary-output.path").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
        assertTrue(Files.exists(junitReport));
    }

    @Test
    void invalidFormatDoesNotDeleteRememberedOutput() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path output = projectRoot.resolve("outside-report.json");
        CrapJavaCheckTask firstTask = newCheckTask(projectRoot);
        firstTask.getFormat().set("json");
        firstTask.getOutput().fileValue(output.toFile());
        firstTask.runCheck();
        assertTrue(Files.exists(output));

        CrapJavaCheckTask secondTask = newCheckTask(projectRoot);
        secondTask.getFormat().set("invalid");

        GradleException exception = assertThrows(GradleException.class, secondTask::runCheck);

        assertTrue(exception.getMessage().contains("Unknown report format: invalid"));
        assertTrue(Files.exists(output));
    }

    @Test
    void invalidThresholdDoesNotDeleteRememberedOutput() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path output = projectRoot.resolve("outside-report.json");
        CrapJavaCheckTask firstTask = newCheckTask(projectRoot);
        firstTask.getFormat().set("json");
        firstTask.getOutput().fileValue(output.toFile());
        firstTask.runCheck();
        assertTrue(Files.exists(output));

        CrapJavaCheckTask secondTask = newCheckTask(projectRoot);
        secondTask.getThreshold().set(0.0);

        GradleException exception = assertThrows(GradleException.class, secondTask::runCheck);

        assertTrue(exception.getMessage().contains("Threshold must be a finite number greater than 0"));
        assertTrue(Files.exists(output));
    }

    @Test
    void failedReplacementOutputDoesNotDeleteRememberedOutput() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path oldOutput = projectRoot.resolve("outside-report.json");
        Path replacementOutput = projectRoot.resolve("replacement-report.json");
        CrapJavaCheckTask firstTask = newCheckTask(projectRoot);
        firstTask.getFormat().set("json");
        firstTask.getOutput().fileValue(oldOutput.toFile());
        firstTask.runCheck();
        assertTrue(Files.exists(oldOutput));
        Files.createDirectories(replacementOutput);

        CrapJavaCheckTask secondTask = newCheckTask(projectRoot);
        secondTask.getFormat().set("json");
        secondTask.getOutput().fileValue(replacementOutput.toFile());

        assertThrows(Exception.class, secondTask::runCheck);

        assertTrue(Files.exists(oldOutput));
    }

    @Test
    void failedMovedJunitReplacementDoesNotForgetRememberedOutput() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Path oldOutput = projectRoot.resolve("outside-report.json");
        Path replacementOutput = projectRoot.resolve("replacement-report.json");
        Path junitParentFile = projectRoot.resolve("junit-parent-file");
        Path badJunitReport = junitParentFile.resolve("TEST-crap-java.xml");
        CrapJavaCheckTask firstTask = newCheckTask(projectRoot);
        firstTask.getFormat().set("json");
        firstTask.getOutput().fileValue(oldOutput.toFile());
        firstTask.runCheck();
        assertTrue(Files.exists(oldOutput));
        Files.writeString(junitParentFile, "not a directory");

        CrapJavaCheckTask secondTask = newCheckTask(projectRoot);
        secondTask.getFormat().set("json");
        secondTask.getOutput().fileValue(replacementOutput.toFile());
        secondTask.getJunitReport().fileValue(badJunitReport.toFile());

        assertThrows(Exception.class, secondTask::runCheck);
        assertTrue(Files.exists(oldOutput));
        assertFalse(Files.exists(replacementOutput));

        CrapJavaCheckTask thirdTask = newCheckTask(projectRoot);
        thirdTask.getFormat().set("json");
        thirdTask.getOutput().fileValue(replacementOutput.toFile());

        thirdTask.runCheck();

        assertFalse(Files.exists(oldOutput));
        assertTrue(Files.exists(replacementOutput));
    }

    @Test
    void movedOutputDoesNotDeleteReportStillOwnedByAnotherTask() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Path sharedOutput = projectRoot.resolve("shared-report.json");
        Path movedOutput = projectRoot.resolve("moved-report.json");
        CrapJavaCheckTask firstTask = newCheckTask(projectRoot, "first-crap-java-check");
        firstTask.getFormat().set("json");
        firstTask.getOutput().fileValue(sharedOutput.toFile());
        firstTask.runCheck();
        CrapJavaCheckTask secondTask = newCheckTask(projectRoot, "second-crap-java-check");
        secondTask.getFormat().set("json");
        secondTask.getOutput().fileValue(sharedOutput.toFile());
        secondTask.runCheck();

        CrapJavaCheckTask movedFirstTask = newCheckTask(projectRoot, "first-crap-java-check");
        movedFirstTask.getFormat().set("json");
        movedFirstTask.getOutput().fileValue(movedOutput.toFile());

        movedFirstTask.runCheck();

        assertTrue(Files.exists(sharedOutput));
        assertTrue(Files.exists(movedOutput));
    }

    @Test
    void aliasedFutureReportPathCollisionDoesNotDeleteRememberedOutput() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path output = projectRoot.resolve("outside-report.json");
        CrapJavaCheckTask firstTask = newCheckTask(projectRoot);
        firstTask.getFormat().set("json");
        firstTask.getOutput().fileValue(output.toFile());
        firstTask.runCheck();
        assertTrue(Files.exists(output));

        Path reportDirectory = projectRoot.resolve("reports");
        Files.createDirectories(reportDirectory);
        Path reportAlias = createDirectorySymlinkOrSkip(projectRoot.resolve("reports-alias"), reportDirectory);
        CrapJavaCheckTask secondTask = newCheckTask(projectRoot);
        secondTask.getOutput().fileValue(reportDirectory.resolve("collision.xml").toFile());
        secondTask.getJunitReport().fileValue(reportAlias.resolve("collision.xml").toFile());

        GradleException exception = assertThrows(GradleException.class, secondTask::runCheck);

        assertTrue(exception.getMessage().contains("output and junitReport must not point to the same file"));
        assertTrue(Files.exists(output));
    }

    @Test
    void runCheckRejectsOtherTaskInternalStatePath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(projectRoot.resolve(".gradle/crap-java/root/other-task/primary-output.path").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsOtherTaskInternalJunitReportPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getJunitReport().fileValue(projectRoot.resolve(".gradle/crap-java/root/other-task/junit-report.path").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("junitReport must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsOtherTaskInternalOwnerPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(projectRoot.resolve(".gradle/crap-java/root/other-task/primary-output.owner").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsOtherTaskInternalLockPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(projectRoot.resolve(".gradle/crap-java/root/other-task/state.lock").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsRootReportPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path root = projectRoot.getRoot();
        assumeTrue(root != null, "Filesystem root is unavailable");
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(root.toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a filesystem root"));
    }

    @Test
    void runCheckHandlesCaseOnlyInternalExecutionMarkerPathByFileSystem() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path markerPath = projectRoot.resolve("build/tmp/crap-java/crap-java-check/EXECUTION.MARKER");
        Files.createDirectories(markerPath.getParent());
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(markerPath.toFile());

        if (isCaseInsensitiveFileSystem(projectRoot)) {
            GradleException exception = assertThrows(GradleException.class, task::runCheck);
            assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
        } else {
            task.runCheck();
            assertTrue(Files.exists(markerPath));
        }
    }

    @Test
    void runCheckHandlesCaseOnlyInternalStatePathByFileSystem() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path statePath = projectRoot.resolve(".gradle/crap-java/root/other-task/PRIMARY-OUTPUT.PATH");
        Files.createDirectories(statePath.getParent());
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(statePath.toFile());

        if (isCaseInsensitiveFileSystem(projectRoot)) {
            GradleException exception = assertThrows(GradleException.class, task::runCheck);
            assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
        } else {
            task.runCheck();
            assertTrue(Files.exists(statePath));
        }
    }

    @Test
    void runCheckRejectsSymlinkedInternalStatePath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path stateRoot = projectRoot.resolve(".gradle/crap-java/root/other-task");
        Files.createDirectories(stateRoot);
        Path stateAlias = createDirectorySymlinkOrSkip(projectRoot.resolve("state-alias"), stateRoot);
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(stateAlias.resolve("primary-output.path").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsSymlinkedInternalExecutionMarkerPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Path markerRoot = projectRoot.resolve("build/tmp/crap-java");
        Files.createDirectories(markerRoot.resolve("crap-java-check"));
        Path markerAlias = createDirectorySymlinkOrSkip(projectRoot.resolve("marker-alias"), markerRoot);
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(markerAlias.resolve("crap-java-check/execution.marker").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsSubprojectInternalExecutionMarkerPath() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Files.createDirectories(projectRoot.resolve("sub"));
        Project root = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Project subproject = ProjectBuilder.builder()
                .withName("sub")
                .withParent(root)
                .withProjectDir(projectRoot.resolve("sub").toFile())
                .build();
        CrapJavaCheckTask task = root.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(subproject.getLayout().getBuildDirectory().get().getAsFile().toPath()
                .resolve("tmp/crap-java/crap-java-check/execution.marker")
                .toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckRejectsSubprojectInternalStatePathInRootCache() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Files.createDirectories(projectRoot.resolve("sub"));
        Project root = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        ProjectBuilder.builder()
                .withName("sub")
                .withParent(root)
                .withProjectDir(projectRoot.resolve("sub").toFile())
                .build();
        CrapJavaCheckTask task = root.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getOutput().fileValue(projectRoot.resolve(".gradle/crap-java/sub/crap-java-check/state.lock").toFile());

        GradleException exception = assertThrows(GradleException.class, task::runCheck);

        assertTrue(exception.getMessage().contains("output must not point to a crap-java internal task file"));
    }

    @Test
    void runCheckAllowsNormalReportPathWithInternalFileName() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path output = projectRoot.resolve("reports/crap-java/primary-output.path");
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getFormat().set("json");
        task.getOutput().fileValue(output.toFile());

        task.runCheck();

        assertTrue(Files.exists(output));
    }

    @Test
    void rememberedStateUsesGradleProjectCacheDir() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Path projectCacheDir = projectRoot.resolve("custom-project-cache");
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        project.getGradle().getStartParameter().setProjectCacheDir(projectCacheDir.toFile());
        CrapJavaCheckTask task = project.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());

        task.runCheck();

        assertTrue(Files.exists(projectCacheDir.resolve("crap-java/root/crap-java-check/junit-report.path")));
        assertFalse(Files.exists(projectRoot.resolve(".gradle/crap-java/root/crap-java-check/junit-report.path")));
    }

    @Test
    void reportStateLockIsSharedAcrossTasksInProjectCache() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        CrapJavaCheckTask firstTask = newCheckTask(projectRoot, "first-crap-java-check");
        CrapJavaCheckTask secondTask = newCheckTask(projectRoot, "second-crap-java-check");
        Path expectedLockPath = projectRoot.resolve(".gradle/crap-java/state.lock").toAbsolutePath().normalize();

        assertEquals(expectedLockPath, stateLockPath(firstTask));
        assertEquals(expectedLockPath, stateLockPath(secondTask));
    }

    @Test
    void rememberedStateFallsBackToRootProjectGradleDirForSubprojects() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Path subprojectRoot = projectRoot.resolve("sub");
        Files.createDirectories(subprojectRoot);
        Project root = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Project subproject = ProjectBuilder.builder()
                .withName("sub")
                .withParent(root)
                .withProjectDir(subprojectRoot.toFile())
                .build();
        CrapJavaCheckTask task = subproject.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());

        task.runCheck();

        assertTrue(Files.exists(projectRoot.resolve(".gradle/crap-java/%3Asub/crap-java-check/junit-report.path")));
        assertFalse(Files.exists(subprojectRoot.resolve(".gradle/crap-java/%3Asub/crap-java-check/junit-report.path")));
    }

    @Test
    void rememberedStateEscapesProjectPathSeparators() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Path projectCacheDir = projectRoot.resolve("custom-project-cache");
        Project root = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        root.getGradle().getStartParameter().setProjectCacheDir(projectCacheDir.toFile());
        Files.createDirectories(projectRoot.resolve("a-b"));
        Files.createDirectories(projectRoot.resolve("a/b"));
        Files.createDirectories(projectRoot.resolve("root"));
        Project dashProject = ProjectBuilder.builder()
                .withName("a-b")
                .withParent(root)
                .withProjectDir(projectRoot.resolve("a-b").toFile())
                .build();
        Project nestedParent = ProjectBuilder.builder()
                .withName("a")
                .withParent(root)
                .withProjectDir(projectRoot.resolve("a").toFile())
                .build();
        Project nestedProject = ProjectBuilder.builder()
                .withName("b")
                .withParent(nestedParent)
                .withProjectDir(projectRoot.resolve("a/b").toFile())
                .build();
        Project rootNamedProject = ProjectBuilder.builder()
                .withName("root")
                .withParent(root)
                .withProjectDir(projectRoot.resolve("root").toFile())
                .build();
        CrapJavaCheckTask dashTask = dashProject.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        CrapJavaCheckTask nestedTask = nestedProject.getTasks().register("crap-java-check", CrapJavaCheckTask.class).get();
        CrapJavaCheckTask rootNamedTask = rootNamedProject.getTasks()
                .register("crap-java-check", CrapJavaCheckTask.class)
                .get();
        dashTask.getAnalysisRoot().fileValue(projectRoot.toFile());
        dashTask.getModuleCoverageReports().set(Map.of());
        nestedTask.getAnalysisRoot().fileValue(projectRoot.toFile());
        nestedTask.getModuleCoverageReports().set(Map.of());
        rootNamedTask.getAnalysisRoot().fileValue(projectRoot.toFile());
        rootNamedTask.getModuleCoverageReports().set(Map.of());

        dashTask.runCheck();
        nestedTask.runCheck();
        rootNamedTask.runCheck();

        assertTrue(Files.exists(projectCacheDir.resolve("crap-java/%3Aa-b/crap-java-check/junit-report.path")));
        assertTrue(Files.exists(projectCacheDir.resolve("crap-java/%3Aa%3Ab/crap-java-check/junit-report.path")));
        assertTrue(Files.exists(projectCacheDir.resolve("crap-java/%3Aroot/crap-java-check/junit-report.path")));
        assertFalse(Files.exists(projectCacheDir.resolve("crap-java/root/crap-java-check/junit-report.path")));
    }

    @Test
    void rememberedStatePreservesTrailingSpacesInStoredPath() throws Exception {
        assumeTrue(!isWindows(), "Windows does not support filenames ending with spaces");
        Path projectRoot = tempDir.toRealPath();
        Path statePath = projectRoot.resolve(".gradle/crap-java/root/crap-java-check/primary-output.path");
        Path storedPath = projectRoot.resolve("report.json ");
        Files.createDirectories(statePath.getParent());
        Files.writeString(statePath, storedPath + "\nlink\t1\t2\n");
        CrapJavaCheckTask task = newCheckTask(projectRoot);

        Path rememberedPath = rememberedReportPath(task, statePath);

        assertEquals(storedPath.toAbsolutePath().normalize(), rememberedPath);
    }

    @Test
    void disabledCustomTaskDoesNotDeleteUnownedDefaultSidecars() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path builtInJunit = projectRoot.resolve("build/reports/crap-java/TEST-crap-java.xml");
        Path customJunit = projectRoot.resolve("build/reports/crap-java/custom-crap-java-check/TEST-crap-java.xml");
        Files.createDirectories(builtInJunit.getParent());
        Files.createDirectories(customJunit.getParent());
        Files.writeString(builtInJunit, "<testsuites tests=\"1\"/>");
        Files.writeString(customJunit, "<testsuites tests=\"2\"/>");

        CrapJavaCheckTask task = project.getTasks().register("custom-crap-java-check", CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        task.getJunit().set(false);

        task.runCheck();

        assertTrue(Files.exists(builtInJunit));
        assertEquals("<testsuites tests=\"2\"/>", Files.readString(customJunit));
    }

    @Test
    void disabledCustomTaskOnlyDeletesOwnedDefaultSidecar() throws Exception {
        Path projectRoot = tempDir.toRealPath();
        assumeHardLinksAvailable(projectRoot);
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        Path builtInJunit = projectRoot.resolve("build/reports/crap-java/TEST-crap-java.xml");
        Path customJunit = projectRoot.resolve("build/reports/crap-java/custom-crap-java-check/TEST-crap-java.xml");
        Files.createDirectories(builtInJunit.getParent());
        Files.writeString(builtInJunit, "<testsuites tests=\"1\"/>");
        CrapJavaCheckTask firstTask = project.getTasks().register("custom-crap-java-check", CrapJavaCheckTask.class).get();
        firstTask.getAnalysisRoot().fileValue(projectRoot.toFile());
        firstTask.getModuleCoverageReports().set(Map.of());
        firstTask.runCheck();
        assertTrue(Files.exists(customJunit));

        Project secondProject = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask secondTask = secondProject.getTasks()
                .register("custom-crap-java-check", CrapJavaCheckTask.class)
                .get();
        secondTask.getAnalysisRoot().fileValue(projectRoot.toFile());
        secondTask.getModuleCoverageReports().set(Map.of());
        secondTask.getJunit().set(false);

        secondTask.runCheck();

        assertTrue(Files.exists(builtInJunit));
        assertFalse(Files.exists(customJunit));
    }

    @Test
    void rootModuleMatchesEverySourcePath() {
        assertTrue(CrapJavaCheckTask.matchesModulePath("app/src/main/java/demo/Sample.java", "."));
    }

    @Test
    void modulePathMatchesExactModuleRoot() {
        assertTrue(CrapJavaCheckTask.matchesModulePath("app", "app"));
    }

    @Test
    void modulePathMatchesNestedSourcePath() {
        assertTrue(CrapJavaCheckTask.matchesModulePath("app/src/main/java/demo/Sample.java", "app"));
    }

    @Test
    void modulePathDoesNotMatchPartialPathSegment() {
        assertFalse(CrapJavaCheckTask.matchesModulePath("application/src/main/java/demo/Sample.java", "app"));
    }

    private Path createDirectorySymlinkOrSkip(Path link, Path target) throws Exception {
        try {
            return Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            assumeTrue(false, "Directory symbolic links are unavailable: " + exception.getMessage());
            return link;
        }
    }

    private CrapJavaCheckTask newCheckTask(Path projectRoot) {
        return newCheckTask(projectRoot, "crap-java-check");
    }

    private CrapJavaCheckTask newCheckTask(Path projectRoot, String name) {
        Project project = ProjectBuilder.builder().withProjectDir(projectRoot.toFile()).build();
        CrapJavaCheckTask task = project.getTasks().register(name, CrapJavaCheckTask.class).get();
        task.getAnalysisRoot().fileValue(projectRoot.toFile());
        task.getModuleCoverageReports().set(Map.of());
        return task;
    }

    private void rememberOwnedReport(Path statePath, Path reportPath) throws Exception {
        Files.createDirectories(statePath.getParent());
        Path ownerPath = statePath.resolveSibling("primary-output.owner");
        createHardLinkOrSkip(ownerPath, reportPath);
        Files.writeString(statePath, reportPath + "\n" + ownership(reportPath) + "\n");
    }

    private void assumeHardLinksAvailable(Path directory) throws Exception {
        Path target = Files.createTempFile(directory, ".crap-java-hard-link-target-", ".tmp");
        Path link = target.resolveSibling(target.getFileName() + ".link");
        try {
            createHardLinkOrSkip(link, target);
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(target);
        }
    }

    private Path createHardLinkOrSkip(Path link, Path target) throws Exception {
        try {
            return Files.createLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            assumeTrue(false, "Hard links are unavailable: " + exception.getMessage());
            return link;
        }
    }

    private String ownership(Path reportPath) throws Exception {
        BasicFileAttributes attributes = Files.readAttributes(reportPath, BasicFileAttributes.class);
        return "link\t"
                + attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS) + "\t"
                + attributes.size();
    }

    private void invokeCleanupStaleReports(
            CrapJavaCheckTask task,
            Path currentOutputPath,
            Path currentJunitReportPath
    ) throws Exception {
        Method cleanup = CrapJavaCheckTask.class.getDeclaredMethod(
                "cleanupStaleReports",
                Path.class,
                Path.class
        );
        cleanup.setAccessible(true);
        cleanup.invoke(task, currentOutputPath, currentJunitReportPath);
    }

    private Path stateLockPath(CrapJavaCheckTask task) throws Exception {
        Method stateLockPath = CrapJavaCheckTask.class.getDeclaredMethod("stateLockPath");
        stateLockPath.setAccessible(true);
        return (Path) stateLockPath.invoke(task);
    }

    private Path rememberedReportPath(CrapJavaCheckTask task, Path statePath) throws Exception {
        Method rememberedReportPath = CrapJavaCheckTask.class.getDeclaredMethod("rememberedReportPath", Path.class);
        rememberedReportPath.setAccessible(true);
        Object rememberedReport = rememberedReportPath.invoke(task, statePath);
        assertNotNull(rememberedReport);
        Method path = rememberedReport.getClass().getDeclaredMethod("path");
        path.setAccessible(true);
        return (Path) path.invoke(rememberedReport);
    }

    private boolean isCaseInsensitiveFileSystem(Path directory) throws Exception {
        Path probe = Files.createTempFile(directory, ".crap-java-case-", ".tmp");
        try {
            Path variant = probe.resolveSibling(probe.getFileName().toString().toUpperCase(Locale.ROOT));
            return !probe.getFileName().toString().equals(variant.getFileName().toString()) && Files.exists(variant);
        } finally {
            Files.deleteIfExists(probe);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}

