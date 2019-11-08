# Micronaut Data

[![Maven Central](https://img.shields.io/maven-central/v/io.micronaut.data/micronaut-data-model.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.micronaut.data%22%20AND%20a:%22micronaut-data-model%22)
[![](https://github.com/micronaut-projects/micronaut-data/workflows/Java%20CI/badge.svg)](https://github.com/micronaut-projects/micronaut-data/actions)

Micronaut Data is a database access toolkit that uses Ahead of Time (AoT) compilation to pre-compute queries for repository interfaces that are then executed by a thin, lightweight runtime layer.

Micronaut Data is inspired by [GORM](https://gorm.grails.org) and [Spring Data](https://spring.io/projects/spring-data), however improves on those solutions in the following ways:

* *Compilation Time model* - Both GORM and Spring Data maintain a runtime meta-model that uses reflection to model relationships between entities. This model consumes significant memory and memory requirements grow as your application size grows. The problem is worse when combined with Hibernate which maintains its own meta-model as you end up with duplicate meta-models. Micronaut Data instead moves this model into the compiler.
* *No query translation* - Both GORM and Spring Data use regular expressions and pattern matching in combination with runtime generated proxies to translate a method definition on a Java interface into a query at runtime. No such runtime translation exists in Micronaut Data and this work is carried out by the Micronaut compiler at compilation time.
* *No Reflection or Runtime Proxies* - Micronaut Data uses no reflection or runtime proxies, resulting in better performance, smaller stack traces and reduced memory consumption due to a complete lack of reflection caches (Note that the backing implementation, for example Hibernate, may use reflection).
* *Type Safety* - Micronaut Data will actively check at compile time that a repository method can be implemented and fail compilation if it cannot.

See also the [Micronaut Data Announcement](https://objectcomputing.com/news/2019/07/18/unleashing-predator-precomputed-data-repositories) for details about how and why Micronaut Data was built.

## Documentation

See the [Documentation](https://micronaut-projects.github.io/micronaut-data/latest/guide/) for more information. 

See the [Snapshot Documentation](https://micronaut-projects.github.io/micronaut-data/snapshot/guide/) for the current development docs.

## Examples

Examples can be found in the [examples](https://github.com/micronaut-projects/micronaut-data/tree/master/examples) directory.

## Snapshots and Releases

Snaphots are automatically published to JFrog OSS using [Github Actions](https://github.com/micronaut-projects/micronaut-data/actions).

See the documentation in the [Micronaut Docs](https://docs.micronaut.io/latest/guide/index.html#usingsnapshots) for how to configure your build to use snapshots.

Releases are published to JCenter and Maven Central via [Github Actions](https://github.com/micronaut-projects/micronaut-data/actions).

A release is performed with the following steps:

* Change the version specified by `projectVersion` in `gradle.properties` to a semantic, unreleased version. Example `1.0.0`
* Commit the change. Example: `git commit -m "Release Version 1.0.0"`
* Tag the release where the tag starts with `v`. Example: `git tag v1.0.0`
* Push the commit and the tag: `git push && git push --tags`