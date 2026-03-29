package media.barney.crap4java.core;

enum CoverageMode {
    GENERATE,
    USE_EXISTING;

    boolean shouldGenerateCoverage() {
        return this == GENERATE;
    }
}
