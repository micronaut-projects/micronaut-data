# Micronaut Predator

[![Maven Central](https://img.shields.io/maven-central/v/io.micronaut.data/micronaut-predator-model.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.micronaut.data%22%20AND%20a:%22micronaut-predator-model%22)
[![Build Status](https://travis-ci.org/micronaut-projects/micronaut-predator.svg?branch=master)](https://travis-ci.org/micronaut-projects/micronaut-predator)

Micronaut Predator (short for **Pre**computed **Dat**a **R**epositories) is a database access toolkit that uses Ahead of Time (AoT) compilation to pre-compute queries for repository interfaces that are then executed by a thin, lightweight runtime layer.

Predator is inspired by https://gorm.grails.org[GORM] and https://spring.io/projects/spring-data[Spring Data], however improves on those solutions in the following ways:

* *No runtime model* - Both GORM and Spring Data maintain a runtime meta-model that uses reflection to model relationships between entities. This model consumes significant memory and memory requirements grow as your application size grows. The problem is worse when combined with Hibernate which maintains its own meta-model as you end up with duplicate meta-models.
* *No query translation* - Both GORM and Spring Data use regular expressions and pattern matching in combination with runtime generated proxies to translate a method definition on a Java interface into a query at runtime. No such runtime translation exists in Predator and this work is carried out by the Micronaut compiler at compilation time.
* *No Reflection or Runtime Proxies* - Predator uses no reflection or runtime proxies, resulting in better performance, smaller stack traces and reduced memory consumption due to a complete lack of reflection caches (Note that the backing implementation, for example Hibernate, may use reflection).
* *Type Safety* - Predator will actively check at compile time that a repository method can be implemented and fail compilation if it cannot.

## Documentation

<!--- See the [Documentation](https://micronaut-projects.github.io/micronaut-grpc/latest/guide) for more information. -->

See the [Snapshot Documentation](https://micronaut-projects.github.io/micronaut-predator/snapshot/guide) for the current development docs.

## Examples

Examples can be found in the [examples](https://github.com/micronaut-projects/micronaut-predator/tree/master/examples) directory.