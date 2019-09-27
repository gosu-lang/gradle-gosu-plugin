package org.gosulang.gradle.unit

import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.gosulang.gradle.tasks.Util
import org.gradle.api.JavaVersion
import org.junit.rules.ExpectedException;

import java.nio.file.Path
import java.nio.file.Paths;

import static org.junit.Assert.*

class UtilsTest {

    @Test
    public void testFindToolJar(){
      if(JavaVersion.current().java8)
            assertTrue(Util.findToolsJar().isFile())
        if(JavaVersion.current().isJava11Compatible())
            assertNull(Util.findToolsJar())
    }


    @Test()
    public void testExceptionWhenToolsJarUnavilableOnJava8(){
       String systemJavaHome =  System.getProperty("java.home");
       System.setProperty("java.home", "/tmp/jre");
       try {
           if(JavaVersion.current().java8) {
               Util.findToolsJar()
              assertFalse("Something wrong", true)
           }
       } catch(IllegalStateException ex) {
           assertEquals(ex.getMessage(), "Could not find tools.jar")
        }
        finally {
            System.setProperty("java.home", systemJavaHome);
        }
    }
}
