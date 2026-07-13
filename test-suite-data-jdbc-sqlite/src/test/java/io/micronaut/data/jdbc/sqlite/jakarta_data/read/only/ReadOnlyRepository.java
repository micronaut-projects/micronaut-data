package io.micronaut.data.jdbc.sqlite.jakarta_data.read.only;

import jakarta.data.repository.DataRepository;
import jakarta.data.repository.Save;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

//FIXME - Are user defined repository interfaces like this allowed via the Specification?
// Currently failing in test environment
//   java.lang.IllegalArgumentException: @Repository ee.jakarta.tck.data.framework.readonly.NaturalNumbers does not specify an entity class.
//   To correct this, have the repository interface extend DataRepository or another built-in repository interface and supply the entity class as the first parameter.
@Deprecated //Not currently in use
public interface ReadOnlyRepository<T, K> extends DataRepository<T, K>{

    // WRITE - default method
    // Necessary for pre-population
    @Save
    <S extends T> List<S> saveAll(List<S> entities);

    // READ - default methods
    Optional<T> findById(K id);

    boolean existsById(K id);

    Stream<T> findAll();

    Stream<T> findByIdIn(List<K> ids);

    long count();

}
