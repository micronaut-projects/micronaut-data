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
- Java classes are imported (`from micronaut.data.model import Pageable`, `from java.util import Optional`); packages
  under `io.` other than `io.micronaut` (`io.reactivex.rxjava3.core`) need the `try: from io.reactivex... except ImportError:
  from reactivex...` form until the compiler resolves them at runtime. `java.type(...)` is only used where the imported
  form does not work (see "java.type usages" below).
- Java members whose names are Python keywords are called through the trailing-underscore alias (`Pageable.from_`,
  `criteria_builder.and_` / `or_` / `not_`, `root.get("id").in_`); the criteria specification combinators are plain
  Python functions. Logging uses the `logging` module (`LOG = logging.getLogger(__name__)`).

## Active `@Disabled` Tests

| Test | Reason |
| --- | --- |
| `hibernate-example-python`: `BookRepositorySpec`, `ProductRepositorySpec`, `UserRepositorySpec` | Hibernate reads the JPA annotations reflectively from the Java class; the Java stub generated for a Python dataclass does not carry `@Entity`/`@Id`/... (`Unknown entity type 'example.Book' ('Book' is not annotated '@Entity')`). The sources compile and the repository queries are generated. |
| `hibernate-reactive-example-python`: `BookRepositorySpec` | Same as above. |
| `jdbc-example-python`: `BookRepositorySpec.testOneToManyCustomQuery` | The generated `equals`/`hashCode` of a Python dataclass include every property, so a bidirectional association (`Book.reviews` <-> `Review.book`) recurses (`StackOverflowError`) when Micronaut Data tracks the cascaded entities in a `HashSet`. |
| `jdbc-example-python`: `UserRepositorySpec` | A Python method overriding an inherited Java interface method with the same signature (`deleteById(Integer)` with a custom `@Query`) is dropped from the generated bean definition; the inherited method is used instead. `SaleRepository.findById` was renamed to `getById` for the same reason. |
| `mongo-example-python`: `SaleRepositorySpec` | `@MappedProperty(converter=...)` on a Python attribute makes the compiler overflow (`MappedPropertyMapper` re-enters the class element registry while the class element is being built); the member is commented out in `Sale.py`. Same for `Book.itemPrice` in `azure-cosmos-example-python`. |
| `r2dbc-example-python`: `BookControllerTest` | The Reactor transaction context is not propagated into the publishers returned by Python lambdas inside `operations.withTransaction(...)`: the `@Transactional(MANDATORY)` `BookRepository.save` fails with `NoTransactionException`. Reactive return values reach Python as plain `Publisher` objects, wrap them with `Mono.from_(...)` / `Flux.from_(...)` (see `UpsertTest`). |
| `azure-cosmos-example-python`: `BookRepositorySpec`, `PersonRepositorySpec` | Not a Python compiler gap: not verified locally, the tests need the Azure Cosmos emulator (Testcontainers). |
| `jdbc-example-python`, `mongo-example-python`: `PersonRepositorySpec.testFind` (also affects the class-disabled `azure-cosmos-example-python` `PersonRepositorySpec.testFind` and `hibernate-example-python` `ProductRepositorySpec.testFindByNameSpecification`) | Keyword alias on a foreign object: the trailing-underscore alias (`criteria_builder.and_(...)`, `or_`, `not_`, `root.get("id").in_(...)`) is only rewritten for imported Java classes and `java.type` names, not for a lambda parameter (`AttributeError: foreign object has no attribute 'and_'`). The specification combinators keep the alias form so that the samples read as intended. |

## Adapted Snippet Targets

| Target | Adaptation |
| --- | --- |
| `PersonRepository` (jdbc, mongo, cosmos), `ProductRepository` (hibernate), `FamilyRepository` (cosmos) | `JpaSpecificationExecutor` cannot be implemented by a Python class: the generic methods (`<R> R findOne(CriteriaQueryBuilder<R>)`) are not bridged (`is not abstract and does not override abstract method`). The specification methods are declared on the repository (Micronaut Data matches them by parameter type), `Specifications` is a set of module level functions. |
| `Child` (cosmos) | Dataclass inheritance (`Child extends GenderAware`) is not supported by the stub generator (`no suitable constructor found for GenderAware()`); the `gender` attribute is declared on `Child`. |
| `PersonRepository.typesafe` tag (jdbc) | Java only (static metamodel), like the Kotlin and Groovy examples. |
| `ContactView`, `AddressSubView`, `StudentView`, `StudentScheduleSubView`, `TeacherSubView` (jdbc, Oracle JSON views) | Not ported: `@JsonView(entity=Contact)` referencing a Python entity fails in the JSON view visitor (`Json View property id doesn't exist in the defined entity class Contact`); the Kotlin and Groovy examples do not have them either. |

## `java.type` usages

None. Every Java class is imported (`from micronaut.data.model import Pageable`, `from java.util import Map` with `Map.Entry`
for the nested class, `from org.bson.types import ObjectId`, ...); Python classes are passed directly as runtime type
arguments (`entityStream(result_set, Book)`, `beanContext.getBeanDefinition(BookRepository)`).
