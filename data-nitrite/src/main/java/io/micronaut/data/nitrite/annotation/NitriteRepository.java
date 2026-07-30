package io.micronaut.data.nitrite.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.document.annotation.DocumentProcessorRequired;
import io.micronaut.data.nitrite.model.query.builder.NitriteQueryBuilder;
import io.micronaut.data.nitrite.operations.NitriteRepositoryOperations;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stereotype repository annotation for NitriteDB.
 *
 * <p>This annotation configures the Micronaut Data annotation processor to generate repository
 * implementations that use NitriteDB as the backing store.
 *
 * <p>Note: {@code implicitQueries=true} enables generation of standard {@link
 * io.micronaut.data.repository.CrudRepository} methods (for example {@code deleteById}). Query
 * builders must treat {@link io.micronaut.data.model.query.builder.QueryResult}
 * {@code additionalRequiredParameters} as a compile-time binding contract (method-parameter
 * bindings), not as a generic metadata channel.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * {@literal @}NitriteRepository
 * public interface PersonRepository extends CrudRepository<Person, String> {
 *     List<Person> findByAgeGreaterThan(int age);
 *     Optional<Person> findByName(String name);
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
@RepositoryConfiguration(
    queryBuilder = NitriteQueryBuilder.class,
    operations = NitriteRepositoryOperations.class
)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Repository
@DocumentProcessorRequired
public @interface NitriteRepository {

  /**
   * The datasource name.
   * @return The datasource name (for multi-datasource configurations).
   */
  @AliasFor(annotation = Repository.class, member = "value")
  String value() default "";
}
