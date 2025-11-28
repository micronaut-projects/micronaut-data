package io.micronaut.data.jdbc.oraclexe;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.jdbc.entities.IntervalEntity;
import io.micronaut.data.tck.repositories.IntervalRepository;

import java.time.Duration;
import java.time.Period;
import java.util.List;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface OracleXEIntervalRepository extends IntervalRepository {

    @Override
    @Query("INSERT INTO interval_entity(duration, period, id) VALUES (:dur, :per, INTERVAL_ENTITY_SEQ.nextval)")
    void saveCustom(@Parameter("dur") Duration duration, @Parameter("per") Period period);

    List<IntervalEntity> findByDurationBetweenOrderById(Duration first, Duration last);

    List<IntervalEntity> findByDurationGreaterThanEqualsOrderById(Duration duration);

    Integer countByDurationLessThan(Duration duration);

    List<IntervalEntity> findByPeriodBetweenOrderById(Period first, Period last);

    List<IntervalEntity> findByPeriodGreaterThanOrderById(Period period);

    Integer countByPeriodLessThanEquals(Period period);
}
