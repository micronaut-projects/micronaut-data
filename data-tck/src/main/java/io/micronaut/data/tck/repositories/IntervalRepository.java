package io.micronaut.data.tck.repositories;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.tck.jdbc.entities.IntervalEntity;

import java.time.Duration;
import java.time.Period;
import java.util.List;

public interface IntervalRepository extends PageableRepository<IntervalEntity, Integer> {

    @Query("INSERT INTO interval_entity(duration, period) VALUES (:dur, :per)")
    void saveCustom(@Parameter("dur") Duration duration, @Parameter("per") Period period);

    @Query("SELECT * FROM interval_entity WHERE duration = :dur AND period = :per ORDER BY id ASC")
    List<IntervalEntity> findCustom(@Parameter("dur") Duration duration, @Parameter("per") Period period);

    @Query("UPDATE interval_entity SET duration = :dur, period = :per WHERE id = :id")
    void updateCustom(Integer id, @Parameter("dur") Duration duration, @Parameter("per") Period period);
}
