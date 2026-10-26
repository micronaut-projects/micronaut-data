package io.micronaut.data.neo4j.operations

import io.micronaut.data.exceptions.DataAccessException
import org.neo4j.driver.Values
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.Period

/**
 * Checks how driver values are decoded, independently of a database.
 */
class Neo4jValuesSpec extends Specification {

    @Unroll
    void "a duration with #description is read as #expected"() {
        expect:
        Neo4jValues.decode(Values.isoDuration(months, days, seconds, nanos)) == expected

        where:
        description             | months | days | seconds | nanos      | expected
        'only seconds'          | 0      | 0    | 5400    | 0          | Duration.ofMinutes(90)
        'seconds and nanos'     | 0      | 0    | 1       | 500_000    | Duration.ofSeconds(1, 500_000)
        'only nanos'            | 0      | 0    | 0       | 42         | Duration.ofNanos(42)
        'nothing'               | 0      | 0    | 0       | 0          | Duration.ZERO
        'only months'           | 14     | 0    | 0       | 0          | Period.ofMonths(14)
        'only days'             | 0      | 3    | 0       | 0          | Period.ofDays(3)
        'months and days'       | 1      | 2    | 0       | 0          | Period.of(0, 1, 2)
        'a negative time'       | 0      | 0    | -60     | 0          | Duration.ofMinutes(-1)
    }

    @Unroll
    void "a duration with #description is rejected"() {
        when:
        Neo4jValues.decode(Values.isoDuration(months, days, seconds, nanos))

        then:
        def e = thrown(DataAccessException)
        e.message.startsWith('Cannot read the Neo4j duration [')
        e.message.contains('both a calendar part (months or days) and a time part')

        where:
        description          | months | days | seconds | nanos
        'days and hours'     | 0      | 1    | 7200    | 0
        'months and seconds' | 1      | 0    | 1       | 0
        'days and nanos'     | 0      | 1    | 0       | 1
    }

    void "a duration nested in a list or a map is converted too"() {
        expect:
        Neo4jValues.decode(Values.value([Values.isoDuration(0, 0, 60, 0)])) == [Duration.ofMinutes(1)]
        Neo4jValues.decode(Values.value([age: Values.isoDuration(12, 0, 0, 0)])) == [age: Period.ofMonths(12)]
    }
}
