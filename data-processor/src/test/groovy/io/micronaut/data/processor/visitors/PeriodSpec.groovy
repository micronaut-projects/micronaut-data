package io.micronaut.data.processor.visitors

import io.micronaut.data.annotation.Query

class PeriodSpec extends AbstractDataSpec {

    void "test period query"() {
        given:
        def repository = buildRepository('test.IntervalInterface', """
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.jdbc.entities.IntervalEntity;
import java.time.Period;

@Repository
@io.micronaut.context.annotation.Executable
interface IntervalInterface extends GenericRepository<IntervalEntity, Long> {

    Integer countByPeriodLessThanEquals(Period period);

    List<IntervalEntity> findByPeriodBetweenOrderById(Period first, Period last);
}
"""
        )

        def executableMethod1 = repository.findPossibleMethods("countByPeriodLessThanEquals")
                .findFirst()
                .get()

        def executableMethod2 = repository.findPossibleMethods("findByPeriodBetweenOrderById")
                .findFirst()
                .get()

        when:
        def query1 = executableMethod1.stringValue(Query).orElse(null)
        def query2 = executableMethod2.stringValue(Query).orElse(null)

        then:
        query1 == "SELECT COUNT(intervalEntity_) FROM io.micronaut.data.tck.jdbc.entities.IntervalEntity AS intervalEntity_ WHERE (intervalEntity_.period <= :p1)"
        query2 == "SELECT intervalEntity_ FROM io.micronaut.data.tck.jdbc.entities.IntervalEntity AS intervalEntity_ WHERE ((intervalEntity_.period >= :p1 AND intervalEntity_.period <= :p2)) ORDER BY intervalEntity_.id ASC"
    }
}
