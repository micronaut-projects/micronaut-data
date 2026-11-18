package io.micronaut.data.model.search

import spock.lang.Specification

class TextMatchOptionsSpec extends Specification {

    void "a boost that is not a finite number above 0 is rejected"() {
        when:
        TextMatchOptions.builder().boost("title", boost)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "The boost of title must be a finite number above 0, got " + boost

        where:
        boost << [0d, -1d, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY]
    }

    void "a boost needs a property path and the fuzziness cannot be negative"() {
        when:
        TextMatchOptions.builder().boost(null, 2d)

        then:
        thrown(NullPointerException)

        when:
        TextMatchOptions.builder().fuzziness(-1)

        then:
        thrown(IllegalArgumentException)
    }

    void "options without boosts or fuzziness are the defaults"() {
        expect:
        TextMatchOptions.builder().build().is(TextMatchOptions.NONE)
        TextMatchOptions.NONE.isDefault()
        !TextMatchOptions.builder().fuzziness(1).build().isDefault()
        !TextMatchOptions.builder().language("german").build().isDefault()
        TextMatchOptions.builder().language(" ").build().is(TextMatchOptions.NONE)
        TextMatchOptions.builder().language(null).build().language() == null
    }

    void "options are value objects keyed by property path"() {
        given:
        def options = TextMatchOptions.builder().boost("address.city", 3d).fuzziness(2).build()

        expect:
        options.boosts() == ["address.city": 3d]
        options.fuzziness() == 2
        options.boostFor("address.city") == 3d
        options.boostFor("city") == null
        options == TextMatchOptions.builder().boost("address.city", 3d).fuzziness(2).build()
        options.hashCode() == TextMatchOptions.builder().boost("address.city", 3d).fuzziness(2).build().hashCode()
        options != TextMatchOptions.builder().boost("address.city", 3d).build()
        options.toString() == "TextMatchOptions{boosts={address.city=3.0}, fuzziness=2, language=null}"
    }

    void "options with another language differ"() {
        given:
        def german = TextMatchOptions.builder().language("german").build()

        expect:
        german.language() == "german"
        german == TextMatchOptions.builder().language("german").build()
        german.hashCode() == TextMatchOptions.builder().language("german").build().hashCode()
        german != TextMatchOptions.builder().language("french").build()
        german.toString() == "TextMatchOptions{boosts={}, fuzziness=0, language=german}"
    }

    void "the options of a search query carry its boosts, edit distance and language"() {
        expect:
        TextMatchOptions.of(SearchQuery.builder("coffe").fuzzy(2).boost("title", 2d).build()) == TextMatchOptions.builder().boost("title", 2d).fuzziness(2).build()
        TextMatchOptions.of(SearchQuery.builder("kaffee").language("german").build()).language() == "german"
        TextMatchOptions.of(SearchQuery.of("coffee")).is(TextMatchOptions.NONE)
    }
}
