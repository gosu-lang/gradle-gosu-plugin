package org.gosulang.gradle.functional

trait MultiversionTestable {

    private final String DELIMITER = ','

    private ClassLoader getClassLoader() {
        return this.class.classLoader
    }
    
    String getGradleVersion() {
        return getFirstLineFromResource(getClassLoader().getResource('gradleVersion.txt'))
    }

    String[] getTestedVersions() {
        return getFirstLineFromResource(getClassLoader().getResource('testedVersions.txt')).split(DELIMITER).collect { it.trim() }
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    private String getFirstLineFromResource(URL url) {
        return new BufferedReader(new FileReader(url.file)).lines().findFirst().get()
    }

}