package media.barney.crapjava.core;

record MethodDescriptor(
        String name,
        int startLine,
        int endLine,
        int complexity
) {
}
