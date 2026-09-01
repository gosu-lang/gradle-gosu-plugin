package org.gosulang.gradle.functional

trait MultiversionTestable {

    private final String DELIMITER = ','

    String getGosuVersion() {
        return System.getProperty('test.gosuVersion')
    }

    String getGradleVersion() {
        return System.getProperty('test.gradleVersion')
    }

    String[] getTestedVersions() {
        return System.getProperty('test.testedVersions').split(DELIMITER).collect { it.trim() }
    }

}