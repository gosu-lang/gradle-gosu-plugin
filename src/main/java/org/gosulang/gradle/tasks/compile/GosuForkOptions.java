package org.gosulang.gradle.tasks.compile;

import org.gradle.api.tasks.compile.BaseForkOptions;

/**
 * Fork options for Gosu compilation. Only take effect if {@code GosuCompileOptions.fork}
 * is {@code true}.
 */
public class GosuForkOptions extends BaseForkOptions {
  private static final long serialVersionUID = 0;
}
