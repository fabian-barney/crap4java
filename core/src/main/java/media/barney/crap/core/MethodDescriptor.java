package media.barney.crap.core;

import java.util.List;

record MethodDescriptor(
        String className,
        String name,
        int startLine,
        int endLine,
        int complexity,
        List<String> classAnnotations
) {
    MethodDescriptor {
        classAnnotations = List.copyOf(classAnnotations);
    }

    MethodDescriptor(String className, String name, int startLine, int endLine, int complexity) {
        this(className, name, startLine, endLine, complexity, List.of());
    }
}

