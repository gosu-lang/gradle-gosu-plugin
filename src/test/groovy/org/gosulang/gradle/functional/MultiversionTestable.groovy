package org.gosulang.gradle.functional

trait MultiversionTestable {

    private final ClassLoader classLoader = this.class.classLoader
    private final URL _gradleVersionResource = classLoader.getResource("gradleVersion.txt")
    private final URL _testedVersionsResource = classLoader.getResource("testedVersions.txt")
    private final String DELIMITER = ','

    String getGradleVersion() {
        return getFirstLineFromResource(_gradleVersionResource)
    }

    String[] getTestedVersions() {
        return getFirstLineFromResource(_testedVersionsResource).split(DELIMITER).collect { it.trim() }
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    private String getFirstLineFromResource(URL url) {
        return new BufferedReader(new FileReader(url.file)).lines().findFirst().get()
    }

}