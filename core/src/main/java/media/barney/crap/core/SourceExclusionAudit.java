package media.barney.crap.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record SourceExclusionAudit(
        int candidateFiles,
        int analyzedFiles,
        List<ExclusionCount> excludedFiles,
        List<ExclusionCount> excludedClasses
) {
    SourceExclusionAudit {
        excludedFiles = List.copyOf(excludedFiles);
        excludedClasses = List.copyOf(excludedClasses);
    }

    static SourceExclusionAudit empty() {
        return new SourceExclusionAudit(0, 0, List.of(), List.of());
    }

    static Builder builder() {
        return new Builder();
    }

    int excludedFileCount() {
        return excludedFiles.stream().mapToInt(ExclusionCount::count).sum();
    }

    int excludedClassCount() {
        return excludedClasses.stream().mapToInt(ExclusionCount::count).sum();
    }

    record ExclusionCount(String reason, int count) {
    }

    static final class Builder {
        private int candidateFiles;
        private int analyzedFiles;
        private final Map<String, Integer> excludedFiles = new LinkedHashMap<>();
        private final Map<String, Integer> excludedClasses = new LinkedHashMap<>();

        void recordCandidateFile() {
            candidateFiles++;
        }

        void recordAnalyzedFile() {
            analyzedFiles++;
        }

        void recordExcludedFile(String reason) {
            increment(excludedFiles, reason);
        }

        void recordExcludedClass(String reason) {
            increment(excludedClasses, reason);
        }

        SourceExclusionAudit build() {
            return new SourceExclusionAudit(
                    candidateFiles,
                    analyzedFiles,
                    counts(excludedFiles),
                    counts(excludedClasses)
            );
        }

        private static void increment(Map<String, Integer> counts, String reason) {
            counts.merge(reason, 1, Integer::sum);
        }

        private static List<ExclusionCount> counts(Map<String, Integer> counts) {
            List<ExclusionCount> result = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                result.add(new ExclusionCount(entry.getKey(), entry.getValue()));
            }
            return result;
        }
    }
}
