package org.gosulang.gradle.tasks.compile;

import org.gradle.api.internal.tasks.compile.AbstractJavaCompileSpecFactory;
import org.gradle.api.tasks.compile.CompileOptions;

public class DefaultGosuCompileSpecFactory extends AbstractJavaCompileSpecFactory<DefaultGosuCompileSpec> {

  DefaultGosuCompileSpecFactory(CompileOptions compileOptions) {
    super(compileOptions);
  }

  @Override
  protected DefaultGosuCompileSpec getCommandLineSpec() {
    return null;
  }

  @Override
  protected DefaultGosuCompileSpec getForkingSpec() {
    return null;
  }

  @Override
  protected DefaultGosuCompileSpec getDefaultSpec() {
    return new DefaultGosuCompileSpec();
  }
}
