package crap4java;

record MethodDescriptor(
        String name,
        int startLine,
        int endLine,
        int complexity
) {
}
