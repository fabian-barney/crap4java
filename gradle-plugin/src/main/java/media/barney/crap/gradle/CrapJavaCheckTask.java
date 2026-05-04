package media.barney.crap.gradle;

import media.barney.crap.core.Main;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class CrapJavaCheckTask extends DefaultTask {

    private static final String LINK_OWNERSHIP = "link";

    private final Provider<RegularFile> defaultJunitReport;
    private final Provider<RegularFile> executionMarker;
    private final RegularFileProperty junitReportState;
    private final RegularFileProperty outputState;
    private final Provider<String> absentString;
    private final Provider<RegularFile> absentRegularFile;

    public CrapJavaCheckTask() {
        absentString = getProject().getProviders().provider(() -> (String) null);
        absentRegularFile = getProject().getProviders().provider(() -> (RegularFile) null);
        defaultJunitReport = getProject().getProviders()
                .provider(this::defaultJunitReportRelativePath)
                .flatMap(path -> getProject().getLayout().getBuildDirectory().file(path));
        executionMarker = getProject().getLayout().getBuildDirectory()
                .file("tmp/crap-java/" + getName() + "/execution.marker");
        junitReportState = getProject().getObjects().fileProperty();
        junitReportState.fileValue(localStateFile("junit-report.path"));
        outputState = getProject().getObjects().fileProperty();
        outputState.fileValue(localStateFile("primary-output.path"));
        getThreshold().convention(Main.DEFAULT_THRESHOLD);
        getAgent().convention(false);
        getFormat().convention(getAgent().map(agent -> agent ? "toon" : "none"));
        getFailuresOnly().convention(getAgent());
        getOmitRedundancy().convention(getAgent());
        getJunit().convention(true);
        getJunitReport().convention(defaultJunitReport);
    }

    @Internal
    public abstract DirectoryProperty getAnalysisRoot();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getAnalysisSources();

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getCoverageReports();

    @Input
    public abstract MapProperty<String, String> getModuleCoverageReports();

    @Input
    public abstract Property<Double> getThreshold();

    @Input
    public abstract Property<String> getFormat();

    @Input
    public abstract Property<Boolean> getAgent();

    @Input
    public abstract Property<Boolean> getFailuresOnly();

    @Input
    public abstract Property<Boolean> getOmitRedundancy();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getOutput();

    @Input
    public abstract Property<Boolean> getJunit();

    @Internal
    public abstract RegularFileProperty getJunitReport();

    @Input
    @Optional
    public Provider<String> getDisabledJunitReportPathInput() {
        return getJunit().flatMap(enabled -> enabled
                ? absentString
                : getJunitReport().map(file -> file.getAsFile().toPath().toAbsolutePath().normalize().toString()));
    }

    @OutputFile
    @Optional
    public Provider<RegularFile> getJunitReportOutput() {
        return getJunit().flatMap(enabled -> enabled
                ? getJunitReport()
                : absentRegularFile);
    }

    @OutputFile
    public Provider<RegularFile> getExecutionMarkerOutput() {
        return executionMarker;
    }

    @TaskAction
    void runCheck() throws Exception {
        List<Path> sourceFiles = getAnalysisSources().getFiles().stream()
                .map(file -> file.toPath().toAbsolutePath().normalize())
                .sorted()
                .toList();
        Path analysisRoot = getAnalysisRoot().get().getAsFile().toPath().toAbsolutePath().normalize();
        Path configuredOutputPath = outputPath();
        Path configuredJunitReportPath = junitReportPath();
        validateReportPaths(configuredOutputPath, configuredJunitReportPath);
        if (sourceFiles.isEmpty()) {
            try (var out = GradleLoggingPrintStreams.standardOut(getLogger());
                 var err = GradleLoggingPrintStreams.standardErr(getLogger())) {
                int exit = Main.runWithExistingCoverage(
                        List.of(),
                        analysisRoot,
                        out,
                        err,
                        getFormat().get(),
                        getFailuresOnly().get(),
                        getOmitRedundancy().get(),
                        configuredOutputPath,
                        configuredJunitReportPath,
                        getThreshold().get()
                );
                cleanupStaleReports(configuredOutputPath, configuredJunitReportPath);
                rememberOutputPath(configuredOutputPath);
                rememberJunitReportPath(configuredJunitReportPath);
                if (exit != 0) {
                    throw new GradleException("crap-java-check failed with exit " + exit);
                }
                writeExecutionMarker();
            }
            return;
        }
        List<Main.ResolvedCoverageModule> modules = resolvedModules(sourceFiles);
        try (var out = GradleLoggingPrintStreams.standardOut(getLogger());
             var err = GradleLoggingPrintStreams.standardErr(getLogger())) {
            int exit = Main.runWithExistingCoverage(
                    modules,
                    analysisRoot,
                    out,
                    err,
                    getFormat().get(),
                    getFailuresOnly().get(),
                    getOmitRedundancy().get(),
                    configuredOutputPath,
                    configuredJunitReportPath,
                    getThreshold().get()
            );
            cleanupStaleReports(configuredOutputPath, configuredJunitReportPath);
            rememberOutputPath(configuredOutputPath);
            rememberJunitReportPath(configuredJunitReportPath);
            if (exit != 0) {
                throw new GradleException("crap-java-check failed with exit " + exit);
            }
            writeExecutionMarker();
        }
    }

    private void validateReportPaths(Path outputPath, Path junitReportPath) {
        if (outputPath != null && outputPath.equals(junitReportPath)) {
            throw new GradleException("output and junitReport must not point to the same file");
        }
        validateReportPathDoesNotUseInternalFile("output", outputPath);
        validateReportPathDoesNotUseInternalFile("junitReport", junitReportPath);
    }

    private void validateReportPathDoesNotUseInternalFile(String propertyName, Path reportPath) {
        if (reportPath == null) {
            return;
        }
        if (isInternalTaskFile(reportPath)) {
            throw new GradleException(propertyName + " must not point to a crap-java internal task file: "
                    + reportPath);
        }
    }

    private boolean isInternalTaskFile(Path reportPath) {
        return isExecutionMarkerPath(reportPath) || isRememberedPathStateFile(reportPath);
    }

    private boolean isExecutionMarkerPath(Path reportPath) {
        return "execution.marker".equals(reportPath.getFileName().toString())
                && normalizedPath(reportPath).contains("/tmp/crap-java/");
    }

    private boolean isRememberedPathStateFile(Path reportPath) {
        return hasRememberedStateFileName(reportPath) && normalizedPath(reportPath).contains("/crap-java/");
    }

    private String normalizedPath(Path path) {
        return path.normalize().toString().replace('\\', '/');
    }

    private boolean hasRememberedStateFileName(Path reportPath) {
        String fileName = reportPath.getFileName().toString();
        return "primary-output.path".equals(fileName) || "junit-report.path".equals(fileName);
    }

    private void cleanupStaleReports(Path currentOutputPath, Path currentJunitReportPath) throws Exception {
        deleteMovedOutput(currentOutputPath);
        deleteMovedJunitReport(currentJunitReportPath);
        deleteDisabledJunitReport();
    }

    private void writeExecutionMarker() throws Exception {
        Path markerPath = executionMarkerPath();
        Files.createDirectories(markerPath.getParent());
        Files.writeString(markerPath, "ok\n");
    }

    private Path executionMarkerPath() {
        return getExecutionMarkerOutput().get().getAsFile().toPath().toAbsolutePath().normalize();
    }

    private void deleteMovedOutput(Path currentPath) throws Exception {
        RememberedReport rememberedReport = rememberedOutputPath();
        deleteRememberedOutputIfMoved(rememberedReport, currentPath);
        deleteOutputStateIfUnset(currentPath);
    }

    private void deleteRememberedOutputIfMoved(RememberedReport rememberedReport, Path currentPath) throws Exception {
        if (rememberedReport == null || rememberedReport.path().equals(currentPath)) {
            return;
        }
        deleteRememberedReport(rememberedReport);
    }

    private void deleteOutputStateIfUnset(Path currentPath) throws Exception {
        if (currentPath == null) {
            deleteReportState(outputStatePath());
        }
    }

    private void deleteMovedJunitReport(Path currentPath) throws Exception {
        if (currentPath == null) {
            return;
        }
        RememberedReport rememberedReport = rememberedJunitReportPath();
        if (rememberedReport != null && !rememberedReport.path().equals(currentPath)) {
            deleteRememberedReport(rememberedReport);
        }
    }

    private Path outputPath() {
        if (!getOutput().isPresent()) {
            return null;
        }
        return getOutput().get().getAsFile().toPath().toAbsolutePath().normalize();
    }

    private Path junitReportPath() {
        if (!getJunit().get()) {
            return null;
        }
        return getJunitReport().get().getAsFile().toPath().toAbsolutePath().normalize();
    }

    private void deleteDisabledJunitReport() throws Exception {
        if (getJunit().get()) {
            return;
        }
        deleteRememberedReport(rememberedJunitReportPath());
        deleteReportState(junitReportStatePath());
    }

    private void deleteRememberedReport(RememberedReport rememberedReport) throws Exception {
        if (!isOwnedRememberedReport(rememberedReport)) {
            return;
        }
        Files.deleteIfExists(rememberedReport.path());
    }

    private boolean isOwnedRememberedReport(RememberedReport rememberedReport) throws Exception {
        if (rememberedReport == null) {
            return false;
        }
        if (!Files.isRegularFile(rememberedReport.path())) {
            return false;
        }
        return rememberedReport.ownership().startsWith(LINK_OWNERSHIP + "\t")
                && Files.exists(rememberedReport.ownerLink())
                && Files.isSameFile(rememberedReport.path(), rememberedReport.ownerLink())
                && rememberedReport.ownership().equals(ownership(rememberedReport.path()));
    }

    private String defaultJunitReportRelativePath() {
        if ("crap-java-check".equals(getName())) {
            return "reports/crap-java/TEST-crap-java.xml";
        }
        return "reports/crap-java/" + getName() + "/TEST-crap-java.xml";
    }

    private void rememberOutputPath(Path path) throws Exception {
        if (path == null) {
            Files.deleteIfExists(outputStatePath());
            return;
        }
        rememberReportPath(outputStatePath(), path);
    }

    private RememberedReport rememberedOutputPath() throws Exception {
        return rememberedReportPath(outputStatePath());
    }

    private Path outputStatePath() {
        return outputState.get().getAsFile()
                .toPath()
                .toAbsolutePath()
                .normalize();
    }

    private void rememberJunitReportPath(Path path) throws Exception {
        if (path == null) {
            return;
        }
        rememberReportPath(junitReportStatePath(), path);
    }

    private void rememberReportPath(Path statePath, Path reportPath) throws Exception {
        Files.createDirectories(statePath.getParent());
        Path ownerLink = ownerLinkPath(statePath);
        Files.deleteIfExists(ownerLink);
        String ownership = ownership(reportPath, ownerLink);
        if (ownership.isBlank()) {
            Files.deleteIfExists(statePath);
            return;
        }
        Files.writeString(statePath, reportPath + "\n" + ownership + "\n");
    }

    private String ownership(Path reportPath, Path ownerLink) throws Exception {
        try {
            Files.createLink(ownerLink, reportPath);
            return ownership(reportPath);
        } catch (IOException | SecurityException | UnsupportedOperationException exception) {
            return "";
        }
    }

    private String ownership(Path reportPath) throws Exception {
        BasicFileAttributes attributes = Files.readAttributes(reportPath, BasicFileAttributes.class);
        return LINK_OWNERSHIP + "\t"
                + attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS) + "\t"
                + attributes.size();
    }

    private void deleteReportState(Path statePath) throws Exception {
        Files.deleteIfExists(ownerLinkPath(statePath));
        Files.deleteIfExists(statePath);
    }

    private Path ownerLinkPath(Path statePath) {
        String fileName = statePath.getFileName().toString();
        String ownerFileName = fileName.endsWith(".path")
                ? fileName.substring(0, fileName.length() - ".path".length()) + ".owner"
                : fileName + ".owner";
        return statePath.resolveSibling(ownerFileName);
    }

    private RememberedReport rememberedJunitReportPath() throws Exception {
        return rememberedReportPath(junitReportStatePath());
    }

    private RememberedReport rememberedReportPath(Path statePath) throws Exception {
        if (!Files.isRegularFile(statePath)) {
            return null;
        }
        return parseRememberedReport(statePath, Files.readAllLines(statePath));
    }

    private RememberedReport parseRememberedReport(Path statePath, List<String> lines) {
        if (!hasRememberedReport(lines)) {
            return null;
        }
        return new RememberedReport(
                Path.of(lines.get(0).trim()).toAbsolutePath().normalize(),
                lines.get(1),
                ownerLinkPath(statePath)
        );
    }

    private boolean hasRememberedReport(List<String> lines) {
        return lines.size() >= 2 && !lines.get(0).isBlank() && !lines.get(1).isBlank();
    }

    private Path junitReportStatePath() {
        return junitReportState.get().getAsFile()
                .toPath()
                .toAbsolutePath()
                .normalize();
    }

    private File localStateFile(String fileName) {
        return projectCacheRoot()
                .resolve("crap-java")
                .resolve(projectStateName())
                .resolve(getName())
                .resolve(fileName)
                .toFile();
    }

    private Path projectCacheRoot() {
        File projectCacheDir = getProject().getGradle().getStartParameter().getProjectCacheDir();
        if (projectCacheDir != null) {
            return projectCacheDir.toPath().toAbsolutePath().normalize();
        }
        return getProject().getProjectDir().toPath().resolve(".gradle").toAbsolutePath().normalize();
    }

    private String projectStateName() {
        String projectPath = getProject().getPath();
        if (":".equals(projectPath)) {
            return "root";
        }
        return projectPath.substring(1).replace(':', '-');
    }

    private record RememberedReport(Path path, String ownership, Path ownerLink) {
    }

    private List<Main.ResolvedCoverageModule> resolvedModules(List<Path> sourceFiles) {
        Path analysisRoot = getAnalysisRoot().get().getAsFile().toPath().toAbsolutePath().normalize();
        Map<String, String> configuredModules = new LinkedHashMap<>(getModuleCoverageReports().get());
        List<String> orderedModulePaths = configuredModules.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        List<String> matchingModulePaths = orderedModulePaths.stream()
                .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
                .toList();

        Map<String, List<Path>> sourceFilesByModule = new LinkedHashMap<>();
        for (String modulePath : orderedModulePaths) {
            sourceFilesByModule.put(modulePath, new ArrayList<>());
        }
        for (Path sourceFile : sourceFiles) {
            String modulePath = matchingModulePath(analysisRoot, sourceFile, matchingModulePaths);
            sourceFilesByModule.get(modulePath).add(sourceFile);
        }

        List<Main.ResolvedCoverageModule> modules = new ArrayList<>();
        for (String modulePath : orderedModulePaths) {
            List<Path> moduleSources = sourceFilesByModule.get(modulePath);
            if (moduleSources.isEmpty()) {
                continue;
            }
            String coverageReport = configuredModules.get(modulePath);
            modules.add(new Main.ResolvedCoverageModule(
                    resolveModuleRoot(analysisRoot, modulePath),
                    resolveRelativePath(analysisRoot, coverageReport),
                    moduleSources
            ));
        }
        return modules;
    }

    private String matchingModulePath(Path analysisRoot, Path sourceFile, List<String> matchingModulePaths) {
        String relativeSourcePath = normalizeRelativePath(analysisRoot.relativize(sourceFile));
        return matchingModulePaths.stream()
                .filter(modulePath -> matchesModulePath(relativeSourcePath, modulePath))
                .findFirst()
                .orElseThrow(() -> new GradleException("No configured Gradle module matches " + relativeSourcePath));
    }

    static boolean matchesModulePath(String relativeSourcePath, String modulePath) {
        if (".".equals(modulePath)) {
            return true;
        }
        return relativeSourcePath.equals(modulePath) || relativeSourcePath.startsWith(modulePath + "/");
    }

    private static Path resolveModuleRoot(Path analysisRoot, String modulePath) {
        if (".".equals(modulePath)) {
            return analysisRoot;
        }
        return analysisRoot.resolve(modulePath).normalize();
    }

    private static Path resolveRelativePath(Path analysisRoot, String relativePath) {
        if (".".equals(relativePath)) {
            return analysisRoot;
        }
        return analysisRoot.resolve(relativePath).normalize();
    }

    private static String normalizeRelativePath(Path path) {
        return path.normalize().toString().replace('\\', '/');
    }
}

