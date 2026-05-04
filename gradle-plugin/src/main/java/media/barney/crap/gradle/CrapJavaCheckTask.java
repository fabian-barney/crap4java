package media.barney.crap.gradle;

import media.barney.crap.core.Main;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
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
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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
        validateReportOptions(configuredOutputPath, configuredJunitReportPath);
        List<Main.ResolvedCoverageModule> modules = sourceFiles.isEmpty() ? List.of() : resolvedModules(sourceFiles);
        int exit = runWithReportStateLock(
                modules,
                analysisRoot,
                configuredOutputPath,
                configuredJunitReportPath
        );
        if (exit != 0) {
            throw new GradleException("crap-java-check failed with exit " + exit);
        }
        writeExecutionMarker();
    }

    private int runWithReportStateLock(
            List<Main.ResolvedCoverageModule> modules,
            Path analysisRoot,
            Path configuredOutputPath,
            Path configuredJunitReportPath
    ) throws Exception {
        Path lockPath = stateLockPath();
        Files.createDirectories(lockPath.getParent());
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            return runAndRememberReports(
                    modules,
                    analysisRoot,
                    configuredOutputPath,
                    configuredJunitReportPath
            );
        }
    }

    private int runAndRememberReports(
            List<Main.ResolvedCoverageModule> modules,
            Path analysisRoot,
            Path configuredOutputPath,
            Path configuredJunitReportPath
    ) throws Exception {
        ReportSnapshot outputBefore = reportSnapshot(configuredOutputPath);
        ReportSnapshot junitBefore = reportSnapshot(configuredJunitReportPath);
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
            rememberReportState(configuredOutputPath, configuredJunitReportPath);
            return exit;
        } catch (Exception exception) {
            rememberChangedReportState(
                    configuredOutputPath,
                    configuredJunitReportPath,
                    outputBefore,
                    junitBefore
            );
            throw exception;
        }
    }

    private void validateReportOptions(Path outputPath, Path junitReportPath) throws IOException {
        validateReportFormat(getFormat().get());
        validateThreshold(getThreshold().get());
        validateReportPaths(outputPath, junitReportPath);
    }

    private void validateReportFormat(String format) {
        if (format == null) {
            throw new GradleException("Unknown report format: null");
        }
        switch (format.toLowerCase(Locale.ROOT)) {
            case "toon", "json", "text", "junit", "none" -> { return; }
            default -> throw new GradleException("Unknown report format: " + format);
        }
    }

    private void validateThreshold(double threshold) {
        if (!Double.isFinite(threshold) || Double.compare(threshold, 0.0) <= 0) {
            throw new GradleException("Threshold must be a finite number greater than 0");
        }
    }

    private void validateReportPaths(Path outputPath, Path junitReportPath) throws IOException {
        if (outputPath != null && junitReportPath != null && sameReportTarget(outputPath, junitReportPath)) {
            throw new GradleException("output and junitReport must not point to the same file");
        }
        validateReportPathDoesNotUseInternalFile("output", outputPath);
        validateReportPathDoesNotUseInternalFile("junitReport", junitReportPath);
    }

    private void validateReportPathDoesNotUseInternalFile(String propertyName, Path reportPath) {
        if (reportPath == null) {
            return;
        }
        if (reportPath.getFileName() == null) {
            throw new GradleException(propertyName + " must not point to a filesystem root");
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
        return isInternalFileName(reportPath, "execution.marker")
                && internalExecutionMarkerRoots().stream()
                .anyMatch(internalRoot -> isUnderInternalRoot(reportPath, internalRoot));
    }

    private boolean isRememberedPathStateFile(Path reportPath) {
        return hasRememberedStateFileName(reportPath)
                && internalRememberedStateRoots().stream()
                .anyMatch(internalRoot -> isUnderInternalRoot(reportPath, internalRoot));
    }

    private List<Path> internalExecutionMarkerRoots() {
        return getProject().getRootProject().getAllprojects().stream()
                .map(project -> project.getLayout().getBuildDirectory().dir("tmp/crap-java").get())
                .map(directory -> directory.getAsFile().toPath().toAbsolutePath().normalize())
                .toList();
    }

    private List<Path> internalRememberedStateRoots() {
        return getProject().getRootProject().getAllprojects().stream()
                .map(project -> projectCacheRoot(project).resolve("crap-java").resolve(projectStateName(project)))
                .toList();
    }

    private boolean hasRememberedStateFileName(Path reportPath) {
        return isInternalFileName(reportPath, "primary-output.path")
                || isInternalFileName(reportPath, "junit-report.path")
                || isInternalFileName(reportPath, "primary-output.owner")
                || isInternalFileName(reportPath, "junit-report.owner")
                || isInternalFileName(reportPath, "state.lock");
    }

    private boolean isUnderInternalRoot(Path reportPath, Path internalRoot) {
        return reportPath.startsWith(internalRoot) || realPathStartsWith(reportPath, internalRoot);
    }

    private boolean realPathStartsWith(Path reportPath, Path internalRoot) {
        Path realReportPath = realPathForComparison(reportPath);
        Path realInternalRoot = realPathForComparison(internalRoot);
        return realReportPath != null && realInternalRoot != null && realReportPath.startsWith(realInternalRoot);
    }

    private Path realPathForComparison(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        try {
            if (Files.exists(normalized)) {
                return normalized.toRealPath();
            }
            Path existing = nearestExistingPath(normalized);
            if (existing != null) {
                return existing.toRealPath().resolve(existing.relativize(normalized)).normalize();
            }
        } catch (IOException | SecurityException exception) {
            return null;
        }
        return null;
    }

    private Path nearestExistingPath(Path path) {
        Path current = path;
        while (current != null) {
            if (Files.exists(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private boolean isInternalFileName(Path reportPath, String internalFileName) {
        String fileName = reportPath.getFileName().toString();
        return fileName.equals(internalFileName)
                || sameCaseInsensitiveFileName(fileName, internalFileName, reportPath.getParent());
    }

    private boolean sameCaseInsensitiveFileName(String fileName, String internalFileName, Path parent) {
        return fileName.equalsIgnoreCase(internalFileName) && isCaseInsensitive(parent);
    }

    private boolean isCaseInsensitive(Path path) {
        Path directory = nearestExistingDirectory(path);
        return directory == null ? isLikelyCaseInsensitiveOs() : directoryIsCaseInsensitive(directory);
    }

    private boolean directoryIsCaseInsensitive(Path directory) {
        try {
            Path probe = Files.createTempFile(directory, ".crap-java-case-", ".tmp");
            try {
                return caseVariantExists(probe);
            } finally {
                Files.deleteIfExists(probe);
            }
        } catch (IOException | SecurityException exception) {
            return isLikelyCaseInsensitiveOs();
        }
    }

    private Path nearestExistingDirectory(Path path) {
        Path start = path == null ? Path.of(".").toAbsolutePath().normalize() : path.toAbsolutePath().normalize();
        return ancestors(start).filter(Files::isDirectory).findFirst().orElse(null);
    }

    private Stream<Path> ancestors(Path path) {
        return Stream.iterate(path, Objects::nonNull, Path::getParent);
    }

    private boolean caseVariantExists(Path probe) {
        Path variant = probe.resolveSibling(probe.getFileName().toString().toUpperCase(Locale.ROOT));
        return !probe.getFileName().toString().equals(variant.getFileName().toString()) && Files.exists(variant);
    }

    private boolean isLikelyCaseInsensitiveOs() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") || os.contains("mac");
    }

    private void cleanupStaleReports(Path currentOutputPath, Path currentJunitReportPath) throws Exception {
        deleteMovedOutput(currentOutputPath, currentJunitReportPath);
        deleteMovedJunitReport(currentJunitReportPath, currentOutputPath);
        deleteDisabledJunitReport(currentOutputPath);
    }

    private void rememberReportState(Path currentOutputPath, Path currentJunitReportPath) throws Exception {
        rememberOutputPath(currentOutputPath);
        rememberJunitReportPath(currentJunitReportPath);
    }

    private void rememberChangedReportState(
            Path currentOutputPath,
            Path currentJunitReportPath,
            ReportSnapshot outputBefore,
            ReportSnapshot junitBefore
    ) throws Exception {
        if (reportChanged(currentOutputPath, outputBefore)) {
            rememberOutputPath(currentOutputPath);
        }
        if (reportChanged(currentJunitReportPath, junitBefore)) {
            rememberJunitReportPath(currentJunitReportPath);
        }
    }

    private boolean reportChanged(Path reportPath, ReportSnapshot before) throws IOException {
        return reportPath != null && !reportSnapshot(reportPath).equals(before);
    }

    private ReportSnapshot reportSnapshot(Path reportPath) throws IOException {
        if (reportPath == null || !Files.isRegularFile(reportPath)) {
            return ReportSnapshot.missing();
        }
        BasicFileAttributes attributes = Files.readAttributes(reportPath, BasicFileAttributes.class);
        return new ReportSnapshot(
                true,
                attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS),
                attributes.size()
        );
    }

    private Path stateLockPath() {
        return outputStatePath().resolveSibling("state.lock");
    }

    private void writeExecutionMarker() throws Exception {
        Path markerPath = executionMarkerPath();
        Files.createDirectories(markerPath.getParent());
        Files.writeString(markerPath, "ok\n");
    }

    private Path executionMarkerPath() {
        return getExecutionMarkerOutput().get().getAsFile().toPath().toAbsolutePath().normalize();
    }

    private void deleteMovedOutput(Path currentPath, Path otherCurrentPath) throws Exception {
        RememberedReport rememberedReport = rememberedOutputPath();
        deleteRememberedOutputIfMoved(rememberedReport, currentPath, otherCurrentPath);
        deleteOutputStateIfUnset(currentPath);
    }

    private void deleteRememberedOutputIfMoved(
            RememberedReport rememberedReport,
            Path currentPath,
            Path otherCurrentPath
    ) throws Exception {
        if (shouldKeepRememberedReport(rememberedReport, currentPath, otherCurrentPath)) {
            return;
        }
        deleteRememberedReport(rememberedReport);
    }

    private boolean shouldKeepRememberedReport(
            RememberedReport rememberedReport,
            Path currentPath,
            Path otherCurrentPath
    ) throws IOException {
        return rememberedReport == null
                || isCurrentRememberedPath(rememberedReport, currentPath)
                || isCurrentRememberedPath(rememberedReport, otherCurrentPath);
    }

    private boolean isCurrentRememberedPath(RememberedReport rememberedReport, Path currentPath) throws IOException {
        return currentPath != null && sameReportTarget(rememberedReport.path(), currentPath);
    }

    private void deleteOutputStateIfUnset(Path currentPath) throws Exception {
        if (currentPath == null) {
            deleteReportState(outputStatePath());
        }
    }

    private void deleteMovedJunitReport(Path currentPath, Path otherCurrentPath) throws Exception {
        if (currentPath == null) {
            return;
        }
        RememberedReport rememberedReport = rememberedJunitReportPath();
        if (!shouldKeepRememberedReport(rememberedReport, currentPath, otherCurrentPath)) {
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

    private void deleteDisabledJunitReport(Path currentOutputPath) throws Exception {
        if (getJunit().get()) {
            return;
        }
        RememberedReport rememberedReport = rememberedJunitReportPath();
        if (!shouldKeepRememberedReport(rememberedReport, currentOutputPath, null)) {
            deleteRememberedReport(rememberedReport);
        }
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

    private Path defaultJunitReportPath() {
        return defaultJunitReport.get().getAsFile().toPath().toAbsolutePath().normalize();
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
            getLogger().warn(
                    "crap-java could not remember ownership for {}; stale cleanup for that report path is disabled.",
                    reportPath);
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
        return projectCacheRoot(getProject())
                .resolve("crap-java")
                .resolve(projectStateName(getProject()))
                .resolve(getName())
                .resolve(fileName)
                .toFile();
    }

    private Path projectCacheRoot(Project project) {
        File projectCacheDir = project.getGradle().getStartParameter().getProjectCacheDir();
        if (projectCacheDir != null) {
            return projectCacheDir.toPath().toAbsolutePath().normalize();
        }
        return project.getRootProject().getProjectDir().toPath().resolve(".gradle").toAbsolutePath().normalize();
    }

    private String projectStateName(Project project) {
        String projectPath = project.getPath();
        if (":".equals(projectPath)) {
            return "root";
        }
        return projectPath.substring(1)
                .replace("%", "%25")
                .replace(":", "%3A");
    }

    private boolean sameReportTarget(Path first, Path second) throws IOException {
        if (first.equals(second)) {
            return true;
        }
        return sameExistingFile(first, second) || sameParentAndFileName(first, second);
    }

    private boolean sameExistingFile(Path first, Path second) throws IOException {
        return Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second);
    }

    private boolean sameParentAndFileName(Path first, Path second) throws IOException {
        Path firstParent = first.getParent();
        Path secondParent = second.getParent();
        return sameParent(firstParent, secondParent) && sameFileName(first, second, firstParent);
    }

    private boolean sameParent(Path firstParent, Path secondParent) throws IOException {
        return (firstParent == null || secondParent == null)
                ? firstParent == secondParent
                : sameNonNullParent(firstParent, secondParent);
    }

    private boolean sameNonNullParent(Path firstParent, Path secondParent) throws IOException {
        return firstParent.equals(secondParent)
                || sameAliasedParent(firstParent, secondParent);
    }

    private boolean sameAliasedParent(Path firstParent, Path secondParent) throws IOException {
        return sameExistingFile(firstParent, secondParent)
                || sameRealPath(firstParent, secondParent)
                || sameCaseInsensitivePath(firstParent, secondParent);
    }

    private boolean sameRealPath(Path first, Path second) {
        Path firstRealPath = realPathForComparison(first);
        Path secondRealPath = realPathForComparison(second);
        return firstRealPath != null && firstRealPath.equals(secondRealPath);
    }

    private boolean sameCaseInsensitivePath(Path first, Path second) {
        return first.toString().equalsIgnoreCase(second.toString()) && isCaseInsensitive(first);
    }

    private boolean sameFileName(Path first, Path second, Path parent) {
        String firstName = first.getFileName().toString();
        String secondName = second.getFileName().toString();
        return firstName.equals(secondName) || sameCaseInsensitiveFileName(firstName, secondName, parent);
    }

    private record RememberedReport(Path path, String ownership, Path ownerLink) {
    }

    private record ReportSnapshot(boolean exists, long modifiedNanos, long size) {

        private static ReportSnapshot missing() {
            return new ReportSnapshot(false, 0, 0);
        }
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

