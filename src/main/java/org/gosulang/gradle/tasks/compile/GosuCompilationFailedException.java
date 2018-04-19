package org.gosulang.gradle.tasks.compile;

/**
 * Copied from {@link org.gradle.api.internal.tasks.compile.CompilationFailedException}
 */
public class GosuCompilationFailedException extends RuntimeException {
    public GosuCompilationFailedException() {
        super("Compilation failed; see the compiler error output for details.");
    }

    public GosuCompilationFailedException(int exitCode) {
        super(String.format("Compilation failed with exit code %d; see the compiler error output for details.", exitCode));
    }

    public GosuCompilationFailedException(Throwable cause) {
        super(cause);
    }

}
