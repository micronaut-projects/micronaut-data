package io.micronaut.data.hibernate.jakarta_data.read.only;

import jakarta.data.repository.Delete;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Repository;

import java.util.List;
import java.util.Set;

/**
 * Do not add methods or inheritance to this interface.
 * Its purpose is to test that without inheriting from a built-in repository,
 * the lifecycle methods with the same entity class are what identifies the
 * primary entity class to use for the count and exist methods.
 */
@Repository
public interface CustomRepository {

    @Insert
    void add(List<NaturalNumber> list);

    long countByIdIn(Set<Long> ids);

    boolean existsByIdIn(Set<Long> ids);

    @Delete
    void remove(List<NaturalNumber> list);
}
