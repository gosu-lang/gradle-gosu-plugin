package org.gosulang.gradle.functional

trait MultiversionTestable {

    private final ClassLoader classLoader = this.class.classLoader
    private final URL _gradleVersionResource = classLoader.getResource("gradleVersion.txt")
    private final URL _fullyTestedVersionsResource = classLoader.getResource("fullyTestedVersions.txt")
    private final URL _partiallyTestedVersionsResource = classLoader.getResource("partiallyTestedVersions.txt")
    private final URL _knownIncompatibleVersionsResource = classLoader.getResource("knownIncompatibleVersions.txt")
    private final String DELIMITER = ','

    String getGradleVersion() {
        return getFirstLineFromResource(_gradleVersionResource)
    }

    String[] getFullyTestedVersions() {
        return getFirstLineFromResource(_fullyTestedVersionsResource).split(DELIMITER).collect { it.trim() }
    }

    String[] getPartiallyTestedVersions() {
        return getFirstLineFromResource(_partiallyTestedVersionsResource).split(DELIMITER).collect { it.trim() }
    }

    String[] getKnownIncompatibleVersions() {
        return getFirstLineFromResource(_knownIncompatibleVersionsResource).split(DELIMITER).collect { it.trim() }
    }

    /**
     * @return map of gradleVersion -> knownBreak status
     */
    Map<String, Boolean> getCompatibilityMap() {
        Map<String, Boolean> map = new HashMap()
        getPartiallyTestedVersions().each { map.put(it, false) }
        getKnownIncompatibleVersions().each { map.put(it, true) }
        getFullyTestedVersions().each { map.put(it, false) }
        map.put(getGradleVersion(), false)
        return map
    }

    private static String getFirstLineFromResource(URL url) {
        return new BufferedReader(new FileReader(url.file)).lines().findFirst().get()
    }

}