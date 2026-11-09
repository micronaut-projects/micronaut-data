package io.micronaut.data.search.opensearch

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Runs derived finders over a date, a temporal and an enum with custom serialised names against
 * OpenSearch, whose documents hold them in the form the JSON mapper writes.
 */
class OpenSearchBoundValuesSpec extends Specification {

    // a region zone id is written with a [Europe/Paris] suffix, which the stores do not detect as a date
    static final ZonedDateTime START = ZonedDateTime.of(2026, 11, 5, 14, 21, 0, 0, ZoneOffset.ofHours(1))

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(OpenSearchTestContainer.properties())

    @Shared
    MeetingRepository meetings = context.getBean(MeetingRepository)

    void setup() {
        meetings.deleteAll()
        meetings.saveAll([
                new Meeting(null, "Planning", Date.from(START.toInstant()), START, Meeting.Priority.HIGH),
                new Meeting(null, "Retro", Date.from(START.plusDays(2).toInstant()), START.plusDays(2), Meeting.Priority.LOW),
        ])
    }

    void cleanup() {
        meetings.deleteAll()
    }

    void "a date and a zoned date time bind as the documents store them"() {
        expect:
        meetings.findByCreatedAtAfter(Date.from(START.plusDays(1).toInstant()))*.title() == ["Retro"]
        meetings.findByStartsAtBefore(START.plusDays(1))*.title() == ["Planning"]
        meetings.findByStartsAtBefore(START.plusDays(1)).first().startsAt().toInstant() == START.toInstant()
    }

    void "a named parameter of a @Query body binds as the documents store it"() {
        expect:
        meetings.startingBefore(START.plusDays(1))*.title() == ["Planning"]
    }

    void "an enum binds by its serialised name"() {
        expect:
        meetings.findByPriority(Meeting.Priority.HIGH)*.title() == ["Planning"]
        meetings.findByPriorityInList([Meeting.Priority.LOW])*.title() == ["Retro"]
    }
}
