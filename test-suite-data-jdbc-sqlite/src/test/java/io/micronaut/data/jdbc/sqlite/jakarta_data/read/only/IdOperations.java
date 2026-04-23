package io.micronaut.data.jdbc.sqlite.jakarta_data.read.only;

import jakarta.data.Limit;
import jakarta.data.repository.Query;

import java.util.List;
/**
 * This interface contains common operations for the NaturalNumbers and AsciiCharacters repositories.
 */
public interface IdOperations {
    long countByIdBetween(long minimum, long maximum);

    boolean existsById(long id);
    @Query("SELECT id WHERE id >= :inclusiveMin ORDER BY id ASC")
    List<Long> withIdEqualOrAbove(long inclusiveMin, Limit limit);
}
