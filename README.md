# Micronaut Data

[![Maven Central](https://img.shields.io/maven-central/v/io.micronaut.data/micronaut-data-model.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.micronaut.data%22%20AND%20a:%22micronaut-data-model%22)
[![](https://github.com/micronaut-projects/micronaut-data/workflows/Java%20CI/badge.svg)](https://github.com/micronaut-projects/micronaut-data/actions)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=micronaut-projects_micronaut-data&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=micronaut-projects_micronaut-data)
[![Revved up by Gradle Enterprise](https://img.shields.io/badge/Revved%20up%20by-Gradle%20Enterprise-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.micronaut.io/scans)

Micronaut Data is a database access toolkit that uses Ahead of Time (AoT) compilation to pre-compute queries for repository interfaces that are then executed by a thin, lightweight runtime layer.

Micronaut Data is inspired by [GORM](https://gorm.grails.org) and [Spring Data](https://spring.io/projects/spring-data), however improves on those solutions in the following ways:

* *Compilation Time model* - Both GORM and Spring Data maintain a runtime meta-model that uses reflection to model relationships between entities. This model consumes significant memory and memory requirements grow as your application size grows. The problem is worse when combined with Hibernate which maintains its own meta-model as you end up with duplicate meta-models. Micronaut Data instead moves this model into the compiler.
* *No query translation* - Both GORM and Spring Data use regular expressions and pattern matching in combination with runtime generated proxies to translate a method definition on a Java interface into a query at runtime. No such runtime translation exists in Micronaut Data and this work is carried out by the Micronaut compiler at compilation time.
* *No Reflection or Runtime Proxies* - Micronaut Data uses no reflection or runtime proxies, resulting in better performance, smaller stack traces and reduced memory consumption due to a complete lack of reflection caches (Note that the backing implementation, for example Hibernate, may use reflection).
* *Type Safety* - Micronaut Data will actively check at compile time that a repository method can be implemented and fail compilation if it cannot.

See also the [Micronaut Data Announcement](https://objectcomputing.com/news/2019/07/18/unleashing-predator-precomputed-data-repositories) for details about how and why Micronaut Data was built.

## Quick Start

To get started quickly with Micronaut Data JPA you can use [Micronaut Launch](https://micronaut.io/launch/) either via the web browser or `curl` to create a correctly configured application with a Gradle build:

```bash
$ curl https://launch.micronaut.io/demo.zip?features=data-jpa -o demo.zip
$ unzip demo.zip -d demo
```

Or for Micronaut Data JDBC:

```bash
$ curl https://launch.micronaut.io/demo.zip?features=data-jdbc -o demo.zip
$ unzip demo.zip -d demo
```  

Note that you can append `&build=maven` to the URL to switch to a Maven build. 


## Documentation

See the [Documentation](https://micronaut-projects.github.io/micronaut-data/latest/guide/) for more information. 

See the [Snapshot Documentation](https://micronaut-projects.github.io/micronaut-data/snapshot/guide/) for the current development docs.

## Snapshots and Releases

Snaphots are automatically published to [JFrog OSS](https://oss.jfrog.org/artifactory/oss-snapshot-local/) using [Github Actions](https://github.com/micronaut-projects/micronaut-data/actions).

See the documentation in the [Micronaut Docs](https://docs.micronaut.io/latest/guide/index.html#usingsnapshots) for how to configure your build to use snapshots.

Releases are published to Maven Central via [Github Actions](https://github.com/micronaut-projects/micronaut-data/actions).

A release is performed with the following steps:

* [Edit the version](https://github.com/micronaut-projects/micronaut-data/edit/master/gradle.properties) specified by `projectVersion` in `gradle.properties` to a semantic, unreleased version. Example `1.0.0`
* [Create a new release](https://github.com/micronaut-projects/micronaut-data/releases/new). The Git Tag should start with `v`. For example `v1.0.0`.
* [Monitor the Workflow](https://github.com/micronaut-projects/micronaut-data/actions?query=workflow%3ARelease) to check it passed successfully.
* Celebrate!
