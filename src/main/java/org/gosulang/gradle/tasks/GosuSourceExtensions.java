package org.gosulang.gradle.tasks;

/**
 * Canonical list of Gosu source file extensions for use within the Gradle plugin.
 *
 * <p>Mirrors {@code gw.lang.reflect.gs.GosuClassTypeLoader.ALL_EXTS} in gosu-core-api,
 * which is the authoritative source of truth. A direct reference is not possible here
 * because gosu-core-api is not on the Gradle daemon classpath at task-execution time.
 * Keep this constant in sync with that field when new extensions are added to Gosu.
 */
public final class GosuSourceExtensions {

  /** All Gosu source file extensions. Mirrors {@code GosuClassTypeLoader.ALL_EXTS}. */
  public static final String[] ALL_EXTS = { ".gs", ".gsx", ".gst", ".gsp", ".gr", ".grs" };

  private GosuSourceExtensions() {}

  /**
   * Checks the filename against a list of all known Gosu file extensions
   *
   * @return {@code true} if the given filename ends with any Gosu source extension.
   * */
  public static boolean isGosuSourceFile( String filename ) {
    for( String ext : ALL_EXTS ) {
      if( filename.endsWith( ext ) ) {
        return true;
      }
    }
    return false;
  }

  /**
   * Strips the Gosu file extension from a dot-separated path string used when computing FQCNs.
   * For example, {@code "com.example.MyRule.gr"} becomes {@code "com.example.MyRule"}.
   *
   * @return the input less its Gosu file extension; otherwise return input unchanged if it does not end with a known Gosu extension.
   */
  public static String stripExtension( String fqcnWithExtension ) {
    for( String ext : ALL_EXTS ) {
      if( fqcnWithExtension.endsWith( ext ) ) {
        return fqcnWithExtension.substring( 0, fqcnWithExtension.length() - ext.length() );
      }
    }
    return fqcnWithExtension;
  }
}
