# Python Docs Disabled Test Inventory

This file tracks the Python documentation examples under `doc-examples/*-python` that are present but
disabled, or intentionally adapted, because the direct port of the Java example currently fails. Use it as the
bug-fixing task list for the Python compiler.

The Python tests only run with `./gradlew pythonCheck -Ppython-ci` (or `-p doc-examples/<name>-python test -Ppython-ci`).

## Migration Rules

- Do not define local copies of Micronaut annotation helpers or custom annotation shims in docs snippets.
- Do not add Java-style getters or setters to Python docs models. Use `@dataclass` models with idiomatic attributes.
- Repository query method names stay camelCase (`findByTitle`): Micronaut Data parses the method name and the
  Python compiler does not convert `snake_case` to `camelCase`. Service and test methods use `snake_case`.
- Python has no method overloading: Java overloads of the same repository method are ported with distinct names
  (`findAllByPagesGreaterThan(pageCount, pageable)`, `findAll(pageable: CursoredPageable)`, `store(title, pages)`)
  as the Kotlin and Groovy examples do.
- Java classes are imported (`from micronaut.data.model import Pageable`, `from java.util import Optional`,
  `from io.reactivex.rxjava3.core import Maybe, Single`); no `java.type(...)` is used (see "java.type usages" below).
- Java members whose names are Python keywords are called through the trailing-underscore alias (`Pageable.from_`,
  `criteria_builder.and_` / `or_` / `not_`, `root.get("id").in_`); the criteria specification combinators are plain
  Python functions. Logging uses the `logging` module (`LOG = logging.getLogger(__name__)`).
- JPA entities (`hibernate-example-python`, `hibernate-reactive-example-python`) are plain `@Entity` dataclasses; the
  example builds pass `-Amicronaut.introspection.allowReflection=example.*` to the Python compiler
  (`micronautBuild.python.compilerArgs`) so that the generated classes carry the annotations Hibernate reads reflectively.
- The Azure Cosmos tests start the Cosmos emulator (Testcontainers) through the Java `support.CosmosEmulatorConfigurer`
  (`@ContextConfigurer`), the Python counterpart of the `TestPropertyProvider` base class of the Java tests.

## Active `@Disabled` Tests

| Test | Reason |
| --- | --- |
| `jdbc-example-python`, `mongo-example-python`, `azure-cosmos-example-python`: `PersonRepositorySpec.testDelete`, `PersonRepositorySpec.testUpdate` | A Python lambda passed to `deleteAll(spec)` / `updateAll(spec)` is ambiguous between the inherited `CrudRepository.deleteAll(Iterable)` / `updateAll(Iterable)` overload and the `PredicateSpecification` / `UpdateSpecification` one (`TypeError: invalid instantiation of foreign object`): the runtime only selects functional-interface overloads by arity for the `java.util.function` interfaces, and `Iterable` counts as a functional interface for the host interop. `PythonInterop.fn(PredicateSpecification, spec)` works but is not what the samples should show. `findOne`, `findAll`, `count` (no same-arity overload) work. |

## Adapted Snippet Targets

| Target | Adaptation |
| --- | --- |
| `PersonRepository` (jdbc, mongo, cosmos), `ProductRepository` (hibernate), `FamilyRepository` (cosmos) | A Python repository extending `JpaSpecificationExecutor` compiles now, but every call passing a lambda is ambiguous between its same-arity overloads (`findOne(PredicateSpecification)` / `findOne(QuerySpecification)`, same error as above), so the specification methods are declared on the repository (Micronaut Data matches them by parameter type) and `Specifications` is a set of module level functions. |
| `BookRepository.saveAll` (r2dbc) | Overriding the inherited generic `<S extends Book> Publisher<S> saveAll(Iterable<S>)` is not possible: a `list[Book]` hint generates `Publisher<Book> saveAll(Iterable<Book>)` (`name clash: ... have the same erasure, yet neither overrides the other`) and the PEP 695 form `def saveAll[S: Book](self, entities: list[S]) -> Publisher[S]` is rejected by the Micronaut Data visitor (`Unsupported return type for a save method: python.S`). Only `save` carries the `@Transactional("MANDATORY")` in the Python sample. |
| `UserRepository.listAll` / `queryAll` (hibernate) | A method body consisting of a docstring followed by `...` is not treated as a declaration placeholder: the generated stub bridges to the Python method, which returns `None` (`AttributeError: 'NoneType' object has no attribute 'iterator'`). The Javadoc of the Java example is a `#` comment in the Python sample. |
| `PersonRepository.typesafe` tag (jdbc) | Java only (static metamodel), like the Kotlin and Groovy examples. |

## `java.type` usages

None. Every Java class is imported (`from micronaut.data.model import Pageable`, `from java.util import Map` with `Map.Entry`
for the nested class, `from org.bson.types import ObjectId`, ...); Python classes are passed directly as runtime type
arguments (`entityStream(result_set, Book)`, `beanContext.getBeanDefinition(BookRepository)`).
