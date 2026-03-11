package crap4java;

record MethodMetrics(
        String methodName,
        String className,
        int complexity,
        Double coveragePercent,
        Double crapScore
) {
}
