package media.barney.crap.core;

import java.util.List;
import org.jspecify.annotations.Nullable;

record CliArguments(
        CliMode mode,
        BuildToolSelection buildToolSelection,
        ReportFormat reportFormat,
        double threshold,
        boolean agent,
        boolean failuresOnly,
        boolean omitRedundancy,
        @Nullable String outputPath,
        @Nullable String junitReportPath,
        List<String> sourceRoots,
        List<String> fileArgs,
        SourceExclusionOptions exclusionOptions
) {
    CliArguments(CliMode mode,
                 BuildToolSelection buildToolSelection,
                 ReportFormat reportFormat,
                 double threshold,
                 boolean agent,
                 boolean failuresOnly,
                 boolean omitRedundancy,
                 @Nullable String outputPath,
                 @Nullable String junitReportPath,
                 List<String> fileArgs) {
        this(
                mode,
                buildToolSelection,
                reportFormat,
                threshold,
                agent,
                failuresOnly,
                omitRedundancy,
                outputPath,
                junitReportPath,
                List.of(),
                fileArgs,
                SourceExclusionOptions.defaults()
        );
    }
}

