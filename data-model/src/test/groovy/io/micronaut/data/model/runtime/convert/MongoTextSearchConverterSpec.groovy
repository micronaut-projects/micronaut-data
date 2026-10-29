package io.micronaut.data.model.runtime.convert

import io.micronaut.data.model.search.MatchMode
import spock.lang.Specification
import spock.lang.Unroll

class MongoTextSearchConverterSpec extends Specification {

    @Unroll
    void "renders '#query' as #mode"() {
        expect:
        MongoTextSearchConverter.render(query, mode) == expected

        where:
        query                      | mode             | expected
        "coffee"                   | MatchMode.ANY    | 'coffee'
        "bake coffee -cake"        | MatchMode.ANY    | 'bake coffee -cake'
        '"ken burns" baseball'     | MatchMode.ANY    | '"ken burns" baseball'
        'tea OR coffee -decaf'     | MatchMode.ANY    | 'tea coffee -decaf'
        "bake coffee -cake"        | MatchMode.ALL    | '"bake" "coffee" -"cake"'
        '"ken burns" baseball'     | MatchMode.ALL    | '"ken burns" "baseball"'
        "the fat rats"             | MatchMode.PHRASE | '"the fat rats"'
        '"quoted"'                 | MatchMode.PHRASE | '"quoted"'
        ""                         | MatchMode.ANY    | ''
        null                       | MatchMode.ALL    | ''
    }

    void "OR cannot be combined with SearchAll"() {
        when:
        MongoTextSearchConverter.render("tea OR coffee", MatchMode.ALL)

        then:
        thrown(UnsupportedOperationException)
    }

    void "converters exist per supported mode"() {
        expect:
        MongoTextSearchConverter.converterFor(MatchMode.ANY) == MongoTextSearchConverter.Any.name
        MongoTextSearchConverter.converterFor(MatchMode.ALL) == MongoTextSearchConverter.All.name
        MongoTextSearchConverter.converterFor(MatchMode.PHRASE) == MongoTextSearchConverter.Phrase.name

        when:
        MongoTextSearchConverter.converterFor(MatchMode.PREFIX)

        then:
        thrown(IllegalArgumentException)
    }
}
