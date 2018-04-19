package org.gosulang.gradle.tasks;

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
        Path javaHome = Paths.get(System.getProperty("java.home"));
        Path toolsJar = javaHome.resolve("lib").resolve("tools.jar");

        if(!Files.isRegularFile(toolsJar) && javaHome.toFile().getName().equalsIgnoreCase("jre")) {
            javaHome = javaHome.getParent();
            toolsJar = javaHome.resolve("lib").resolve("tools.jar");
        } else {
            throw new IllegalStateException("Could not find tools.jar");
        }
        return toolsJar.toFile();
    }
}
