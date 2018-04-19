package org.gosulang.gradle.tasks.compile;

import org.gradle.api.tasks.WorkResult;

/**
 * Copied from {@link org.gradle.language.base.internal.compile.Compiler}
 * @param <T> an implementation of GosuCompileSpec
 */
public interface GosuCompiler<T extends GosuCompileSpec> {
    WorkResult execute(T var1);
}