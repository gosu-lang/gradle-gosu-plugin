# Change Log

## [v0.3.4](https://github.com/gosu-lang/gradle-gosu-plugin/tree/v0.3.4) (2017-06-28)
[Full Changelog](https://github.com/gosu-lang/gradle-gosu-plugin/compare/v0.3.3...v0.3.4)

**Fixed bugs:**

- Gosudoc breaking with Gradle 2.12~3.1 [\#33](https://github.com/gosu-lang/gradle-gosu-plugin/issues/33)

## [v0.3.3](https://github.com/gosu-lang/gradle-gosu-plugin/tree/v0.3.3) (2017-06-28)
[Full Changelog](https://github.com/gosu-lang/gradle-gosu-plugin/compare/v0.3.2...v0.3.3)

## [v0.3.2](https://github.com/gosu-lang/gradle-gosu-plugin/tree/v0.3.2) (2017-06-21)
[Full Changelog](https://github.com/gosu-lang/gradle-gosu-plugin/compare/v0.3.1...v0.3.2)

**Implemented enhancements:**

- Upgrade to Gradle 3.5 [\#30](https://github.com/gosu-lang/gradle-gosu-plugin/issues/30)

**Closed issues:**

- Drop support for Gradle 2.11 and earlier [\#32](https://github.com/gosu-lang/gradle-gosu-plugin/issues/32)

## [v0.3.1](https://github.com/gosu-lang/gradle-gosu-plugin/tree/v0.3.1) (2017-05-23)
[Full Changelog](https://github.com/gosu-lang/gradle-gosu-plugin/compare/v0.3.0...v0.3.1)

**Implemented enhancements:**

- Enable task caching [\#31](https://github.com/gosu-lang/gradle-gosu-plugin/issues/31)

## [v0.3.0](https://github.com/gosu-lang/gradle-gosu-plugin/tree/v0.3.0) (2017-03-23)
[Full Changelog](https://github.com/gosu-lang/gradle-gosu-plugin/compare/v0.2.2...v0.3.0)

**Implemented enhancements:**

- Use Gradle 3.4.1 [\#28](https://github.com/gosu-lang/gradle-gosu-plugin/issues/28)
- Include tools.jar on CommandLineCompiler's classpath [\#27](https://github.com/gosu-lang/gradle-gosu-plugin/issues/27)

## [v0.2.2](https://github.com/gosu-lang/gradle-gosu-plugin/tree/v0.2.2) (2016-12-06)
[Full Changelog](https://github.com/gosu-lang/gradle-gosu-plugin/compare/v0.2.1...v0.2.2)

**Implemented enhancements:**

- Remove deprecated constructor [\#26](https://github.com/gosu-lang/gradle-gosu-plugin/issues/26)
- Add configurable threshold for errors and warnings [\#25](https://github.com/gosu-lang/gradle-gosu-plugin/issues/25)

## [v0.2.1](https://github.com/gosu-lang/gradle-gosu-plugin/tree/v0.2.1) (2016-11-18)
[Full Changelog](https://github.com/gosu-lang/gradle-gosu-plugin/compare/v0.2.0...v0.2.1)

**Fixed bugs:**

- JAVA\_TOOL\_OPTIONS, sent to stderr, causes task failure [\#23](https://github.com/gosu-lang/gradle-gosu-plugin/issues/23)
- tools.jar might be missing from gosudoc classpath [\#22](https://github.com/gosu-lang/gradle-gosu-plugin/issues/22)
- Gosu fails under Gradle 2.12 [\#21](https://github.com/gosu-lang/gradle-gosu-plugin/issues/21)
- .gsp not included in default inclusion filter [\#20](https://github.com/gosu-lang/gradle-gosu-plugin/issues/20)

## [v0.2.0](https://github.com/gosu-lang/gradle-gosu-plugin/tree/v0.2.0) (2016-09-27)
[Full Changelog](https://github.com/gosu-lang/gradle-gosu-plugin/compare/v0.1.3...v0.2.0)

**Implemented enhancements:**

- Enable non-standard Gosu class file extensions [\#19](https://github.com/gosu-lang/gradle-gosu-plugin/issues/19)
- Publish hybrid java -\> gosu example [\#9](https://github.com/gosu-lang/gradle-gosu-plugin/issues/9)

## [v0.1.3](https://github.com/gosu-lang/gradle-gosu-plugin/tree/v0.1.3) (2016-01-14)
[Full Changelog](https://github.com/gosu-lang/gradle-gosu-plugin/compare/v0.1.2-alpha...v0.1.3)

**Implemented enhancements:**

- Add module/project name to compilation result message [\#18](https://github.com/gosu-lang/gradle-gosu-plugin/issues/18)
- Infer gosu classpath at execution time, not configuration time [\#17](https://github.com/gosu-lang/gradle-gosu-plugin/issues/17)
- Add an optional 'orderClasspath' property to GosuCompile task [\#13](https://github.com/gosu-lang/gradle-gosu-plugin/issues/13)
- Log error context [\#12](https://github.com/gosu-lang/gradle-gosu-plugin/issues/12)

**Fixed bugs:**

- File-level exclusions not working [\#15](https://github.com/gosu-lang/gradle-gosu-plugin/issues/15)
- Log errors under default \(quiet\) mode [\#11](https://github.com/gosu-lang/gradle-gosu-plugin/issues/11)
- Clarify classpath vs. gosuClasspath in DefaultGosuCompileSpec and AntGosuCompile [\#10](https://github.com/gosu-lang/gradle-gosu-plugin/issues/10)
- Support older versions of Gradle [\#8](https://github.com/gosu-lang/gradle-gosu-plugin/issues/8)

**Closed issues:**

- Upgrade to Gradle 2.9 [\#16](https://github.com/gosu-lang/gradle-gosu-plugin/issues/16)
- Add GosuDoc support [\#7](https://github.com/gosu-lang/gradle-gosu-plugin/issues/7)

## [v0.1.2-alpha](https://github.com/gosu-lang/gradle-gosu-plugin/tree/v0.1.2-alpha) (2015-11-06)
[Full Changelog](https://github.com/gosu-lang/gradle-gosu-plugin/compare/v0.1.1-alpha...v0.1.2-alpha)

**Implemented enhancements:**

- Gosu 1.9 support [\#6](https://github.com/gosu-lang/gradle-gosu-plugin/issues/6)
- Add failOnError flag [\#4](https://github.com/gosu-lang/gradle-gosu-plugin/issues/4)
- Add support for checked arithmetic [\#3](https://github.com/gosu-lang/gradle-gosu-plugin/issues/3)

**Fixed bugs:**

- Fix classpath pollution [\#5](https://github.com/gosu-lang/gradle-gosu-plugin/issues/5)

## [v0.1.1-alpha](https://github.com/gosu-lang/gradle-gosu-plugin/tree/v0.1.1-alpha) (2015-09-21)
[Full Changelog](https://github.com/gosu-lang/gradle-gosu-plugin/compare/v0.1-alpha...v0.1.1-alpha)

**Implemented enhancements:**

- Gosu 1.8 support [\#2](https://github.com/gosu-lang/gradle-gosu-plugin/issues/2)

**Fixed bugs:**

- sourceSets not configurable [\#1](https://github.com/gosu-lang/gradle-gosu-plugin/issues/1)

## [v0.1-alpha](https://github.com/gosu-lang/gradle-gosu-plugin/tree/v0.1-alpha) (2015-09-03)


\* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*