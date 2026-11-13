package io.micronaut.data.search.elasticsearch

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Runs the Painless comparisons of Jakarta Data queries against Elasticsearch, with documents missing the
 * compared properties: like SQL, a comparison with a missing value is unknown, so neither it nor its
 * negation matches the document.
 */
class ElasticsearchScriptComparisonSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(ElasticsearchTestContainer.properties())

    @Shared
    ReadingRepository readings = context.getBean(ReadingRepository)

    void setup() {
        readings.deleteAll()
        readings.saveAll([
                new Reading("r1", "Ab", 1, 1),
                new Reading("r2", "cd", 1, 2),
                new Reading("r3", null, null, 2),
                new Reading("r4", "EF", 3, null),
        ])
    }

    void cleanup() {
        readings.deleteAll()
    }

    void "a negated comparison between two properties excludes the documents missing either one"() {
        expect:
        readings.lowNotEqualToHigh()*.id() == ["r2"]
        readings.lowDifferentFromHigh()*.id() == ["r2"]
        readings.lowNotBelowHigh()*.id() == ["r1"]
    }

    void "the lower or upper case form of a property compares in ranges and equality"() {
        expect: "the bound is compared as given, so only a lower case bound matches a lower cased property"
        readings.lowerCodeAfter("b")*.id() == ["r2", "r4"]
        readings.lowerCodeAfter("B")*.id() == ["r1", "r2", "r4"]

        and: "a bound in UPPER is folded like the property"
        readings.codeIgnoringCase("ab")*.id() == ["r1"]
        readings.codeIgnoringCase("ef")*.id() == ["r4"]

        and: "a negated comparison excludes the documents without the property"
        readings.lowerCodeNotUpTo("cd")*.id() == ["r4"]
    }

    void "a case-folded range folds only the bounds wrapped in a case function, and its negation excludes missing values"() {
        expect:
        readings.lowerCodeBetween("a", "d")*.id() == ["r1", "r2"]
        readings.lowerCodeBetween("A", "D")*.id() == []
        readings.lowerCodeBetweenFolded("A", "D")*.id() == ["r1", "r2"]
        readings.lowerCodeNotBetween("a", "d")*.id() == ["r4"]
    }
}
