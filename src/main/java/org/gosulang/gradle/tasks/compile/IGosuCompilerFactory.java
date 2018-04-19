package org.gosulang.gradle.tasks.compile;

/**
 * Copied from {@link org.gradle.language.base.internal.compile.CompilerFactory}
 * @param <T> an implementation of GosuCompileSpec
 */
public interface IGosuCompilerFactory<T extends GosuCompileSpec> {
    GosuCompiler<T> newCompiler(T spec);
}
