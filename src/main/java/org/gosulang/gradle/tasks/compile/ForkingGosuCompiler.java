package org.gosulang.gradle.tasks.compile;

//import gw.lang.gosuc.simple.GosuCompiler;
//import gw.lang.gosuc.simple.ICompilerDriver;
//import gw.lang.gosuc.simple.IGosuCompiler;
//import gw.lang.gosuc.simple.SoutCompilerDriver;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.Serializable;

public class ForkingGosuCompiler implements Compiler<GosuCompileSpec>, Serializable {
  private static final Logger LOGGER = Logging.getLogger(ForkingGosuCompiler.class);

  @Override
  public WorkResult execute( GosuCompileSpec spec ) {
    LOGGER.info("ForkingGosuCompiler#execute");
    return ForkingCompiler.execute(spec);
  }

  // inner class defers loading of Gosu classes until we are
  // running in the compiler daemon and have them on the class path
  private static class ForkingCompiler {
    static WorkResult execute( GosuCompileSpec spec ) {
      LOGGER.info("ForkingGosuCompiler#execute -> ForkingCompiler#execute");
//      ICompilerDriver driver = new SoutCompilerDriver();
//      IGosuCompiler gosuc = new GosuCompiler();
      //((InfersGosuRuntime) spec).getGosuClasspath().call();
      return new SimpleWorkResult(false);
    }
  }

}
