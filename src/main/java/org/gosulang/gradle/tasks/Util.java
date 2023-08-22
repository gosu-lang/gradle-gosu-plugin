package org.gosulang.gradle.tasks;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginExtension;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Util {

    private Util() {}

    /**
     * Takes the place of {@link org.gradle.internal.jvm.Jvm#findToolsJar(File)}
     * @return the location of tools.jar
     */

    public static File findToolsJar() {
       Path toolsJar = null;
        if(JavaVersion.current().isJava11Compatible()) {
            return null;
        } else {
            Path javaHome = Paths.get(System.getProperty("java.home"));
            toolsJar = javaHome.resolve("lib").resolve("tools.jar");

            if(!Files.isRegularFile(toolsJar) && javaHome.toFile().getName().equalsIgnoreCase("jre")) {
                javaHome = javaHome.getParent();
                toolsJar = javaHome.resolve("lib").resolve("tools.jar");
                if(!Files.isRegularFile(toolsJar))
                    throw new IllegalStateException("Could not find tools.jar");
            }
        }
        return  toolsJar.toFile();
    }

    public static JavaPluginExtension javaPluginExtension(Project project) {
        return extensionOf(project, JavaPluginExtension.class);
    }

    private static <T> T extensionOf(ExtensionAware extensionAware, Class<T> type) {
        return extensionAware.getExtensions().getByType(type);
    }
}
