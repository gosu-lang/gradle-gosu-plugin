package org.gosulang.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;

public class GosuPlugin implements Plugin<Project> {

  public void apply(Project project) {
    project.getPluginManager().apply(GosuBasePlugin.class);
    project.getPluginManager().apply(JavaPlugin.class);
  }

}
