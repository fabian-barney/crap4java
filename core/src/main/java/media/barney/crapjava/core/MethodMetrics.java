package media.barney.crapjava.core;

import org.jspecify.annotations.Nullable;

record MethodMetrics(
        String methodName,
        String className,
        int complexity,
        @Nullable Double coveragePercent,
        @Nullable Double crapScore
) {
}
