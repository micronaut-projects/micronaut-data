package io.micronaut.data.search.elasticsearch.memory

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.model.CursoredPageable
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.runtime.query.internal.DefaultPagedQuery
import io.micronaut.data.runtime.search.SearchIndexOperations
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration
import io.micronaut.data.search.document.query.SearchJson
import io.micronaut.data.search.document.runtime.BulkIndexException
import io.micronaut.data.search.document.runtime.DocumentSearchRepositoryOperations
import io.micronaut.data.search.document.runtime.SearchQueryExecutor
import io.micronaut.data.search.elasticsearch.Meeting
import io.micronaut.data.search.elasticsearch.MeetingRepository
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Runs the document search repository operations against an in-memory transport, checking the
 * requests they send and how they map the hits back to entities.
 */
@MicronautTest(transactional = false)
@Property(name = "spec.name", value = "DocumentSearchOperationsSpec")
@Property(name = "micronaut.data.search.index-prefix", value = "test_")
@Property(name = "micronaut.data.search.bulk-size", value = "2")
class DocumentSearchOperationsSpec extends Specification {

    @Inject
    InMemorySearchTransport transport

    @Inject
    NoteRepository notes

    @Inject
    TagRepository tags

    @Inject
    CounterRepository counters

    @Inject
    MeetingRepository meetings

    @Inject
    JsonMapper jsonMapper

    @Inject
    DocumentSearchRepositoryOperations operations

    @Inject
    ApplicationContext context

    private static jakarta.json.JsonObject json(String text) {
        jakarta.json.Json.createReader(new StringReader(text)).withCloseable { it.readObject() }
    }

    void setup() {
        transport.clear()
    }

    void "the index names carry the configured prefix and the indexes are created at startup"() {
        expect:
        operations.getIndexName(Note) == "test_notes"
        operations.transport.is(transport)
        transport.createdIndexes.containsKey("test_notes")
    }

    void "creating an index that a concurrent create made in the meantime leaves it alone"() {
        given:
        def indexOperations = context.getBean(SearchIndexOperations)
        def mapping = transport.createdIndexes.remove("test_notes")
        transport.createFailure = new DataAccessException("resource_already_exists_exception")
        transport.createRacing = true

        when:
        indexOperations.create(Note)

        then:
        noExceptionThrown()
        indexOperations.exists(Note)

        cleanup:
        transport.createdIndexes.put("test_notes", mapping)
    }

    void "a failed index creation is reported when the index still does not exist"() {
        given:
        def indexOperations = context.getBean(SearchIndexOperations)
        def mapping = transport.createdIndexes.remove("test_notes")
        transport.createFailure = new DataAccessException("Elasticsearch create index failed: mapper_parsing_exception")

        when:
        indexOperations.create(Note)

        then:
        def e = thrown(DataAccessException)
        e.message.contains("mapper_parsing_exception")
        !indexOperations.exists(Note)

        cleanup:
        transport.createdIndexes.put("test_notes", mapping)
    }

    void "saving generates String ids, kept in the document source, and the entities read back by id"() {
        when:
        def saved = notes.saveAll([new Note("Coffee", "Roasting beans", 120), new Note("Bread", "Sourdough", 0)])
        def single = notes.save(new Note("Tea", "Green tea", 40))

        then: "the ids are generated before the write, so the source holds them for the cursored paging tiebreaker"
        (saved + single)*.id.every { UUID.fromString(it) }
        (saved + single)*.id.toSet().size() == 3
        (saved + single).every { transport.indexes["test_notes"][it.id].id == it.id }
        transport.refreshes.every { it == DocumentSearchConfiguration.Refresh.WAIT_FOR }
        operations.findOne(Note, saved[0].id).title == "Coffee"
        operations.findOne(Note, "missing") == null
    }

    void "an assigned id is kept and an update replaces the document"() {
        given:
        def note = new Note("Tea", "Green tea", 40)
        note.id = "tea"

        when:
        notes.save(note)
        note.pages = 41
        notes.update(note)
        notes.updateAll([note])

        then:
        transport.indexes["test_notes"]["tea"].pages == 41
        transport.indexes["test_notes"].size() == 1
    }

    void "equal entities in one batch each get their own generated id"() {
        when:
        def saved = tags.saveAll([new Tag(null, "beverages"), new Tag(null, "beverages"), new Tag(null, "beverages")])

        then:
        saved*.id().every { it != null }
        saved*.id().toSet().size() == 3
        transport.indexes["test_tags"].size() == 3
    }

    void "saveAll sends the batch in bulk requests of the configured size and keeps the order of the ids"() {
        when:
        def saved = notes.saveAll((1..5).collect { new Note("Note " + it, "Body", it) })

        then:
        transport.bulkSizes == [2, 2, 1]
        saved*.title == (1..5).collect { "Note " + it }
        transport.indexes["test_notes"].keySet().toList() == saved*.id
    }

    void "a partly failed saveAll gives the written entities their ids and sends no further request"() {
        given:
        notes.save(new Note("Tea", "Green tea", 40).tap { id = "tea" })
        def coffee = new Note("Coffee", "Roasting beans", 120)
        def cocoa = new Note("Cocoa", "Beans", 80)
        def bread = new Note("Bread", "Sourdough", 60)
        def duplicate = new Note("Other", "Black tea", 10).tap { id = "tea" }
        def cake = new Note("Cake", "Sponge", 10)

        when:
        notes.saveAll([coffee, cocoa, bread, duplicate, cake])

        then:
        BulkIndexException e = thrown()
        e.message.startsWith("Indexing document 1 of 2 failed, the 1 before it were written and the rest were not sent: ")
        e.ids == [bread.id, null]
        transport.bulkSizes == [2, 2]
        transport.indexes["test_notes"].keySet() == ["tea", coffee.id, cocoa.id, bread.id] as Set
        !transport.indexes["test_notes"].containsKey(cake.id)
        transport.indexes["test_notes"]["tea"].title == "Tea"
    }

    void "a @Transactional repository method fails at its first call naming the missing TransactionOperations"() {
        when:
        notes.countByBody("Sourdough")

        then:
        Exception e = thrown()
        e.message.contains("TransactionOperations")
        transport.bodies.isEmpty()
    }

    void "the bulk size must be positive"() {
        when:
        new DocumentSearchConfiguration().bulkSize = 0

        then:
        IllegalArgumentException e = thrown()
        e.message == "The bulk size must be at least 1, was 0"
    }

    void "saving an entity with the id of an existing document fails instead of replacing it"() {
        given:
        def note = new Note("Tea", "Green tea", 40)
        note.id = "tea"
        notes.save(note)

        when:
        notes.save(new Note("Other", "Black tea", 10).tap { id = "tea" })

        then:
        thrown(io.micronaut.data.exceptions.DataAccessException)
        transport.indexes["test_notes"]["tea"].title == "Tea"
    }

    void "a sort on a single result decides which hit comes first"() {
        given:
        notes.saveAll([new Note("Coffee", "Roasting beans", 120), new Note("Bread", "Sourdough", 80)])

        when:
        notes.findFirstByPagesGreaterThan(1, Sort.of(Sort.Order.desc("title")))

        then:
        transport.lastBody().contains('"sort":[{"title.keyword":{"order":"desc"}}]')
        transport.lastBody().contains('"size":1')
    }

    void "an update without an id fails"() {
        when:
        notes.update(new Note("No id", "None", 1))

        then:
        def e = thrown(DataAccessException)
        e.message.contains("without an id")
    }

    void "UUID ids are generated by the operations and other ids must be assigned"() {
        when:
        def tag = tags.save(new Tag(null, "beverages"))

        then:
        tag.id() != null
        transport.indexes["test_tags"].containsKey(tag.id().toString())
        operations.findOne(Tag, tag.id()).name() == "beverages"

        when:
        counters.save(new Counter(null, 1))

        then:
        def e = thrown(DataAccessException)
        e.message.contains("String and UUID ids only")

        when:
        def counter = counters.save(new Counter(7L, 3))

        then:
        counter.id() == 7L
        operations.findOne(Counter, 7L).value() == 3
    }

    void "derived finders send the query and map the hits"() {
        given:
        notes.saveAll([new Note("Coffee", "Roasting beans", 120), new Note("Bread", "Sourdough", 0)])

        when:
        def found = notes.findByTitle("Coffee")

        then:
        found*.title == ["Coffee", "Bread"]
        transport.lastBody().contains('"term"')

        when:
        def one = notes.getByTitle("Coffee")

        then:
        one.title == "Coffee"
        transport.lastBody().contains('"size":1')

        expect:
        notes.countByTitle("Coffee") == 2
        notes.existsByTitle("Coffee")
        notes.findByBody("Sourdough").count() == 2
        notes.count() == 2
        notes.findAll().size() == 2
    }

    void "a @Query method returning a number counts the documents its body's query matches"() {
        given:
        notes.saveAll([new Note("Coffee", "Roasting beans", 120), new Note("Bread", "Sourdough", 0)])

        expect:
        notes.countMatchingTitle("Coffee") == 2L
        jsonMapper.readValue(transport.lastBody(), Map) == [query: [match: [title: "Coffee"]]]
        notes.countMatchingTitleBoxed("Coffee") == 2L
        transport.bodies.every { !it.contains('"size"') }
    }

    void "a backslash before a colon in a @Query body keeps the colon literal"() {
        when:
        notes.findTitledAtHalfPastTen("Coffee")

        then:
        jsonMapper.readValue(transport.lastBody(), Map).query.bool.filter == [match_phrase: [body: "at 10:30"]]
        jsonMapper.readValue(transport.lastBody(), Map).query.bool.must == [match: [title: "Coffee"]]
    }

    void "the SearchQueryExecutor binds parameters as JSON values that cannot change the body"() {
        given:
        def executor = context.getBean(SearchQueryExecutor)
        notes.saveAll([new Note("Coffee", "Roasting beans", 120), new Note("Bread", "Sourdough", 0)])
        def injection = 'x"}}, "size": 10000, "query": {"match_all": {}}, "x": {"y": "'
        def body = '{"query": {"bool": {"must": {"match": {"title": ":title"}}, "filter": {"terms": {"pages": ":pages"}}}}}'

        when:
        def results = executor.search(Note, body, [title: injection, pages: [120, 0]])
        def sent = jsonMapper.readValue(transport.lastBody(), Map)

        then: "the value stays one string, a list becomes an array and the total is exact"
        results.results().size() == 2
        sent.query.bool.must == [match: [title: injection]]
        sent.query.bool.filter == [terms: [pages: [120, 0]]]
        sent.track_total_hits == true
        !sent.containsKey("size")

        when:
        def found = executor.find(Note, body, [title: "Coffee", pages: [120]])

        then:
        found*.title == ["Coffee", "Bread"]
        jsonMapper.readValue(transport.lastBody(), Map).query.bool.must == [match: [title: "Coffee"]]
        !jsonMapper.readValue(transport.lastBody(), Map).containsKey("track_total_hits")

        when:
        def count = executor.count(Note, body, [title: "Coffee", pages: [120]])

        then:
        count == 2L
        jsonMapper.readValue(transport.lastBody(), Map).query.bool.must == [match: [title: "Coffee"]]

        when: "a body that sets track_total_hits keeps it"
        executor.search(Note, '{"query": {"match_all": {}}, "track_total_hits": false, "size": 1}')

        then:
        jsonMapper.readValue(transport.lastBody(), Map).track_total_hits == false
        jsonMapper.readValue(transport.lastBody(), Map).size == 1

        when: "a parameter the body does not reference"
        executor.find(Note, '{"query": {"match": {"title": ":title"}}}', [titel: "Coffee"])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('":titel"')
    }

    void "a single result finder returns null without hits"() {
        expect:
        notes.getByTitle("Nothing") == null
        !notes.existsByTitle("Nothing")
    }

    void "search results carry the scores and the total"() {
        given:
        notes.saveAll([new Note("Coffee", "Roasting beans", 120), new Note("Bread", "Sourdough", 0)])

        when:
        def results = notes.searchByTitleSearch("coffee")

        then:
        results.results()*.entity()*.title == ["Coffee", "Bread"]
        results.results()*.score()*.value() == [1.5d, 0d]
        transport.lastBody().contains('"simple_query_string"')

        when:
        def hits = notes.search("coffee", Pageable.from(0, 1))

        then:
        hits.results().size() == 2
        transport.lastBody().contains('"from":0')
        transport.lastBody().contains('"size":1')
        notes.countHits("coffee") == 2
    }

    void "projections map the hit source to the DTO"() {
        given:
        notes.save(new Note("Coffee", "Roasting beans", 120))

        expect:
        notes.findByBodySearch("beans") == [new NoteSummary("Coffee", 120)]
    }

    void "pages send the window, the sort and ask for the exact total"() {
        given:
        notes.saveAll([new Note("Coffee", "Roasting beans", 120), new Note("Bread", "Sourdough", 80)])

        when:
        def page = notes.findByPagesGreaterThan(10, Pageable.from(0, 10, Sort.of(Sort.Order.desc("title"), Sort.Order.asc("pages"))))

        then:
        page.totalSize == 2
        page.content.size() == 2
        transport.lastBody().contains('"track_total_hits":true')
        transport.lastBody().contains('"sort":[{"title.keyword":{"order":"desc"}},{"pages":{"order":"asc"}}]')

        when:
        def all = notes.findAll(Pageable.from(0, 1, Sort.of(Sort.Order.desc("_score"))))

        then:
        all.totalSize == 2
        transport.lastBody().contains('"sort":[{"_score":{"order":"desc"}}]')

        when:
        def unpaged = notes.findByPagesLessThan(500, Pageable.UNPAGED)

        then:
        unpaged.size() == 2
        !transport.lastBody().contains('"from"')
    }

    void "sorting on an unknown property fails"() {
        when:
        notes.findByPagesLessThan(500, Pageable.from(0, 10, Sort.of(Sort.Order.asc("missing"))))

        then:
        def e = thrown(DataAccessException)
        e.message.contains("unknown property")
    }

    void "a first cursored page sorts with the id as tiebreaker and starts without search_after"() {
        given:
        notes.saveAll([new Note("Coffee", "Roasting beans", 120), new Note("Bread", "Sourdough", 80)])

        when:
        def results = notes.findByPagesLessThan(500, CursoredPageable.from(10, Sort.of(Sort.Order.asc("pages"))))

        then:
        results.size() == 2
        transport.lastBody().contains('"sort":[{"pages":{"order":"asc"}}')
        transport.lastBody().contains('"size":10')
        !transport.lastBody().contains('"search_after"')
    }

    void "a cursored page keeps the method's OrderBy ahead of the pageable sort and the id tiebreaker"() {
        given:
        notes.saveAll([new Note("Coffee", "Roasting beans", 120), new Note("Bread", "Sourdough", 80)])

        when:
        notes.findByPagesLessThanOrderByTitleDesc(500, CursoredPageable.from(10, Sort.of(Sort.Order.asc("pages"))))

        then:
        transport.lastBody().contains('"sort":[{"title.keyword":{"order":"desc"}},{"pages":{"order":"asc"}},{"id":{"order":"asc"}}]')

        when: "the pageable sorts on the OrderBy field too"
        notes.findByPagesLessThanOrderByTitleDesc(500, CursoredPageable.from(10, Sort.of(Sort.Order.desc("title"))))

        then:
        transport.lastBody().contains('"sort":[{"title.keyword":{"order":"desc"}},{"id":{"order":"asc"}}]')
    }

    void "deletes remove documents by id and by query"() {
        given:
        def saved = notes.saveAll([new Note("Coffee", "Roasting beans", 120), new Note("Bread", "Sourdough", 80), new Note("Tea", "Green", 40)])

        when:
        notes.delete(saved[0])
        notes.deleteAll([saved[1]])

        then:
        transport.indexes["test_notes"].keySet() == [saved[2].id] as Set

        when:
        notes.delete(new Note("No id", "None", 1))

        then:
        transport.indexes["test_notes"].size() == 1

        expect:
        notes.deleteByTitle("Tea") == 1
        transport.lastBody().startsWith('{"query":')
        transport.indexes["test_notes"].isEmpty()
    }

    void "derived operators render their queries with the bound values"() {
        when:
        notes."$method"(*args)

        then:
        json(transport.lastBody()) == json(expected)

        where:
        method                                                | args                     | expected
        "findByTitleStartsWith"                               | ["Co*"]                  | '{"query":{"wildcard":{"title.keyword":{"value":"Co\\\\**"}}}}'
        "findByTitleStartsWithIgnoreCase"                     | ["co"]                   | '{"query":{"wildcard":{"title.keyword":{"value":"co*","case_insensitive":true}}}}'
        "findByTitleEndsWith"                                 | ["fee"]                  | '{"query":{"wildcard":{"title.keyword":{"value":"*fee"}}}}'
        "findByTitleContains"                                 | ["ff"]                   | '{"query":{"wildcard":{"title.keyword":{"value":"*ff*"}}}}'
        "findByTitleLike"                                     | ["C_f%"]                 | '{"query":{"wildcard":{"title.keyword":{"value":"C?f*"}}}}'
        "findByTitleIgnoreCase"                               | ["coffee"]               | '{"query":{"term":{"title.keyword":{"value":"coffee","case_insensitive":true}}}}'
        "findByTitleRegex"                                    | ["Co.*"]                 | '{"query":{"regexp":{"title.keyword":"Co.*"}}}'
        "findByPagesGreaterThanEqualsAndPagesLessThanEquals"  | [10, 20]                 | '{"query":{"bool":{"must":[{"range":{"pages":{"gte":10}}},{"range":{"pages":{"lte":20}}}]}}}'
        "findByTitleIsEmpty"                                  | []                       | '{"query":{"bool":{"should":[{"bool":{"must_not":{"exists":{"field":"title"}}}},{"term":{"title.keyword":""}}],"minimum_should_match":1}}}'
        "findByTitleIsNotEmpty"                               | []                       | '{"query":{"bool":{"must_not":{"bool":{"should":[{"bool":{"must_not":{"exists":{"field":"title"}}}},{"term":{"title.keyword":""}}],"minimum_should_match":1}}}}}'
        "findByTitleOrBody"                                   | ["Coffee", "Beans"]      | '{"query":{"bool":{"should":[{"term":{"title.keyword":"Coffee"}},{"term":{"body.keyword":"Beans"}}],"minimum_should_match":1}}}'
        "findByTitleNotInList"                                | [["Coffee", "Tea"]]      | '{"query":{"bool":{"must_not":{"terms":{"title.keyword":["Coffee","Tea"]}}}}}'
        "findByTitleInList"                                   | [["Coffee", "Tea"] as String[]] | '{"query":{"terms":{"title.keyword":["Coffee","Tea"]}}}'
        "findByTitleNotEqual"                                 | ["Coffee"]               | '{"query":{"bool":{"must_not":{"term":{"title.keyword":"Coffee"}}}}}'
    }

    void "dates, temporals and enums are bound in the form the documents store them"() {
        given:
        def start = ZonedDateTime.of(2026, 11, 5, 14, 21, 0, 0, ZoneId.of("Europe/Paris"))
        def created = Date.from(start.toInstant())
        def saved = meetings.save(new Meeting(null, "Planning", created, start, Meeting.Priority.HIGH))
        def document = transport.indexes["test_meetings"][saved.id()]

        when:
        meetings.findByCreatedAtAfter(created)

        then:
        query().range.createdAt.gt == document.createdAt

        when:
        meetings.findByStartsAtBefore(start)

        then:
        query().range.startsAt.lt == document.startsAt

        when:
        meetings.findByPriority(Meeting.Priority.HIGH)

        then:
        document.priority == "urgent"
        query().term.priority == "urgent"

        when:
        meetings.findByPriorityInList([Meeting.Priority.HIGH, Meeting.Priority.LOW])

        then:
        query().terms.priority == ["urgent", "routine"]

        when: "a named parameter of a @Query body"
        meetings.startingBefore(start)

        then:
        query().range.startsAt.lt == document.startsAt
    }

    private Map<String, Object> query() {
        return jsonMapper.readValue(transport.lastBody(), Map).query as Map<String, Object>
    }

    void "the index mapping renders keyword, autocomplete and analyzer fields"() {
        when:
        def mapping = transport.createdIndexes["test_recipes"]

        then:
        mapping.contains('"cuisine":{"type":"keyword"}')
        mapping.contains('"name":{"type":"search_as_you_type","analyzer":"standard","fields":{"keyword":{')
        mapping.contains('"steps":{"type":"text","analyzer":"whitespace"')
        mapping.contains('"ignore_above":8191')
        mapping.contains('"reference":{"type":"keyword"}')
    }

    void "paged queries without a predicate match every document"() {
        given:
        notes.saveAll([new Note("Coffee", "Roasting beans", 120), new Note("Bread", "Sourdough", 80)])
        def method = context.getBeanDefinition(NoteRepository).getRequiredMethod("findAll")
        def query = new DefaultPagedQuery<Note>(method, Note, Pageable.from(0, 5))

        expect:
        operations.findAll(query).size() == 2
        transport.lastBody() == '{"query":{"match_all":{}},"from":0,"size":5}'
        operations.count(query) == 2
        operations.findStream(query).count() == 2
        operations.findPage(query).totalSize == 2
        transport.lastBody().contains('"track_total_hits":true')
    }

    void "the JSON writer escapes strings and writes enums, nulls and other values"() {
        given:
        def uuid = UUID.fromString("00000000-0000-0000-0000-000000000001")

        expect:
        SearchJson.write([text: 'a"b\\c\nd\re\tf\u0001', refresh: DocumentSearchConfiguration.Refresh.TRUE, none: null, id: uuid, flag: true, list: [1, 2.5]]) ==
            '{"text":"a\\"b\\\\c\\nd\\re\\tf\\u0001","refresh":"TRUE","none":null,"id":"00000000-0000-0000-0000-000000000001","flag":true,"list":[1,2.5]}'
        SearchJson.write([nan: Double.NaN, inf: Float.POSITIVE_INFINITY, finite: 1.5d]) == '{"nan":null,"inf":null,"finite":1.5}'
    }

    void "the configuration holds the refresh policy and the index creation switch"() {
        given:
        def configuration = new DocumentSearchConfiguration()

        when:
        configuration.refresh = DocumentSearchConfiguration.Refresh.NONE
        configuration.createIndexes = false
        configuration.indexPrefix = null

        then:
        configuration.refresh == DocumentSearchConfiguration.Refresh.NONE
        !configuration.createIndexes
        configuration.indexPrefix == null
    }
}
