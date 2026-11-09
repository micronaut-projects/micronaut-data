package io.micronaut.data.search.opensearch

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.CursoredPageable
import io.micronaut.data.model.Sort
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

    void "an enum binds by its serialised name"() {
        expect:
        meetings.findByPriority(Meeting.Priority.HIGH)*.title() == ["Planning"]
        meetings.findByPriorityInList([Meeting.Priority.LOW])*.title() == ["Retro"]
    }

    void "cursored paging over equal sort keys returns every document once, with the generated String ids as the tiebreaker"() {
        given: "four more meetings of the same priority, their ids left to Micronaut Data"
        meetings.saveAll((1..4).collect { new Meeting(null, "Standup " + it, Date.from(START.toInstant()), START, Meeting.Priority.HIGH) })
        def sort = Sort.of(Sort.Order.asc("priority"))

        when:
        def first = meetings.findByPriority(Meeting.Priority.HIGH, CursoredPageable.from(2, sort))
        def second = meetings.findByPriority(Meeting.Priority.HIGH, first.nextPageable())
        def third = meetings.findByPriority(Meeting.Priority.HIGH, second.nextPageable())
        def after = meetings.findByPriority(Meeting.Priority.HIGH, third.nextPageable())
        def walked = first.content + second.content + third.content

        then: "the id breaks the ties, so no meeting is skipped or repeated"
        first.content.size() == 2
        second.content.size() == 2
        third.content.size() == 1
        walked*.title.toSorted() == ["Planning", "Standup 1", "Standup 2", "Standup 3", "Standup 4"]
        walked*.id == walked*.id.toSorted()
        after.content.isEmpty()

        when: "a backward page from the last one"
        def back = meetings.findByPriority(Meeting.Priority.HIGH, third.previousPageable())

        then:
        back.content*.id == second.content*.id
    }
}
