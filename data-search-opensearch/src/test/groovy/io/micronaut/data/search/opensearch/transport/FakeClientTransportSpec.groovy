package io.micronaut.data.search.opensearch.transport

import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration.Refresh
import io.micronaut.data.search.document.runtime.AsyncSearchTransport
import io.micronaut.data.search.document.runtime.BulkResponses
import io.micronaut.data.search.document.runtime.SearchTransport
import io.micronaut.data.search.document.runtime.SearchTransport.BulkDocument
import io.micronaut.data.search.document.runtime.SearchTransport.SearchHit
import io.micronaut.data.search.document.runtime.SearchTransport.SearchHits
import jakarta.json.Json
import jakarta.json.JsonValue
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.ErrorResponse
import org.opensearch.client.opensearch._types.FieldValue
import org.opensearch.client.opensearch._types.OpenSearchException
import org.opensearch.client.opensearch._types.VersionType
import org.opensearch.client.opensearch.core.BulkRequest
import org.opensearch.client.opensearch.core.CountRequest
import org.opensearch.client.opensearch.core.DeleteByQueryRequest
import org.opensearch.client.opensearch.core.DeleteRequest
import org.opensearch.client.opensearch.core.GetRequest
import org.opensearch.client.opensearch.core.IndexRequest
import org.opensearch.client.opensearch.core.SearchRequest
import org.opensearch.client.opensearch.indices.CreateIndexRequest
import org.opensearch.client.opensearch.indices.ExistsRequest
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage

/**
 * Drives the blocking and the async OpenSearch transports through a {@link FakeOpenSearchTransport},
 * so the request translation and the response mapping run in CI without a server.
 */
class FakeClientTransportSpec extends Specification {

    static final String SHARDS = '"_shards":{"total":1,"successful":1,"failed":0}'

    static final String SEARCH_RESPONSE = """{"took":3,"timed_out":false,$SHARDS,
        "hits":{"total":{"value":2,"relation":"eq"},"max_score":1.5,"hits":[
            {"_index":"books","_id":"1","_score":1.5,"_version":3,"_source":{"title":"Kotlin"},
             "highlight":{"title":["<em>Kotlin</em>"]},"sort":["Kotlin",7]},
            {"_index":"books","_id":"2"}
        ]}}"""

    static final String SEARCH_RESPONSE_WITHOUT_TOTAL = """{"took":3,"timed_out":false,$SHARDS,
        "hits":{"hits":[{"_index":"books","_id":"1","_source":{"title":"Kotlin"}}]}}"""

    static final String SEARCH_BODY = '''{
        "query":{"match":{"title":"kotlin"}},
        "sort":[{"title":{"order":"desc"}}],
        "from":10,"size":5,"track_total_hits":true,
        "highlight":{"fields":[{"title":{}},{"author":{"number_of_fragments":2}}]},
        "version":true,"min_score":0.5,
        "search_after":["Kotlin",7,1.5,true]
    }'''

    static String writeResponse(String result, String id = "42") {
        """{"_index":"books","_id":"$id","_version":1,"result":"$result",$SHARDS,"_seq_no":0,"_primary_term":1}"""
    }

    static String bulkItem(String id, int status, String error = null) {
        """{"index":{"_index":"books","_id":"$id","status":$status${error == null ? ',"result":"created"' : ',"error":' + error}}}"""
    }

    static String bulkResponse(String... items) {
        """{"took":3,"errors":${items.any { it.contains('"error"') }},"items":[${items.join(',')}]}"""
    }

    static OpenSearchException conflict(int status, String reason) {
        new OpenSearchException(ErrorResponse.of { e -> e.status(status).error { c -> c.type("version_conflict_engine_exception").reason(reason) } })
    }

    @Unroll("#kind search")
    void "search translates every clause of the body and maps the hits"() {
        given:
        driver.fake.answer(SearchRequest, SEARCH_RESPONSE)

        when:
        SearchHits hits = driver.search("books", SEARCH_BODY)

        then: "the request carries every clause"
        SearchRequest request = driver.fake.requests[0] as SearchRequest
        request.index() == ["books"]
        request.query().isMatch()
        request.query().match().field() == "title"
        request.sort().size() == 1
        request.sort()[0].isField()
        request.sort()[0].field().field() == "title"
        request.sort()[0].field().order().jsonValue() == "desc"
        request.from() == 10
        request.size() == 5
        request.trackTotalHits().isEnabled()
        request.trackTotalHits().enabled()
        request.highlight().fields().keySet() == ["title", "author"] as Set
        request.highlight().fields()["author"].numberOfFragments() == 2
        request.version()
        request.minScore() == 0.5d
        request.searchAfter()*._get() == ["Kotlin", 7L, 1.5d, true]
        driver.fake.urls[0] == "POST /books/_search"

        and: "the hits carry id, score, source, highlight, sort values and version"
        hits.total == 2
        hits.maxScore == 1.5d
        hits.hits.size() == 2
        with(hits.hits[0]) {
            id == "1"
            score == 1.5d
            source == [title: "Kotlin"]
            highlights == [title: ["<em>Kotlin</em>"]]
            version == 3L
            sort == ["Kotlin", 7]
        }
        with(hits.hits[1]) {
            id == "2"
            score == null
            source == [:]
            highlights == [:]
            version == null
            sort == []
        }

        where:
        kind << KINDS
        driver = driver(kind)
    }

    @Unroll("#kind search without total")
    void "a knn clause with a similarity threshold becomes a radial search without k"() {
        expect:
        OpenSearchRequests.translateKnn([knn: [field: "embedding", query_vector: [1d, 0d], k: 10, num_candidates: 100, similarity: 0.5d]]) ==
            [knn: [embedding: [vector: [1d, 0d], max_distance: 0.5d]]]
        OpenSearchRequests.translateKnn([knn: [field: "embedding", query_vector: [1d, 0d], k: 10, num_candidates: 100]]) ==
            [knn: [embedding: [vector: [1d, 0d], k: 10]]]
    }

    void "a response without total or max score counts the hits"() {
        given:
        driver.fake.answer(SearchRequest, SEARCH_RESPONSE_WITHOUT_TOTAL)

        when:
        SearchHits hits = driver.search("books", '{"query":{"match_all":{}}}')

        then:
        hits.total == 1
        hits.maxScore == null
        hits.hits*.id == ["1"]
        !(driver.fake.requests[0] as SearchRequest).version()
        (driver.fake.requests[0] as SearchRequest).sort().isEmpty()
        (driver.fake.requests[0] as SearchRequest).trackTotalHits() == null
        (driver.fake.requests[0] as SearchRequest).highlight() == null

        where:
        kind << KINDS
        driver = driver(kind)
    }

    @Unroll("#kind count")
    void "count sends the query and reads the count"() {
        given:
        driver.fake.answer(CountRequest, """{"count":5,$SHARDS}""")

        when:
        long count = driver.count("books", '{"query":{"term":{"author":"Jones"}}}')

        then:
        count == 5
        (driver.fake.requests[0] as CountRequest).query().isTerm()
        (driver.fake.requests[0] as CountRequest).index() == ["books"]

        where:
        kind << KINDS
        driver = driver(kind)
    }

    @Unroll("#kind count without query")
    void "a body without a query counts everything"() {
        given:
        driver.fake.answer(CountRequest, """{"count":9,$SHARDS}""")

        expect:
        driver.count("books", '{}') == 9
        (driver.fake.requests[0] as CountRequest).query().isMatchAll()

        where:
        kind << KINDS
        driver = driver(kind)
    }

    @Unroll("#kind index")
    void "index sends the document with the refresh policy"() {
        given:
        driver.fake.answer(IndexRequest, writeResponse("created"))

        when:
        String id = driver.index("books", "42", [title: "Kotlin"], Refresh.WAIT_FOR)

        then:
        id == "42"
        IndexRequest request = driver.fake.requests[0] as IndexRequest
        request.id() == "42"
        request.document() == [title: "Kotlin"]
        request.version() == null
        request.versionType() == null
        request.refresh().jsonValue() == "wait_for"
        driver.fake.parameters[0] == [refresh: "wait_for"]

        where:
        kind << KINDS
        driver = driver(kind)
    }

    @Unroll("#kind index with version")
    void "index with a version sends an external version"() {
        given:
        driver.fake.answer(IndexRequest, writeResponse("updated", "generated"))

        when:
        String id = driver.index("books", null, [title: "Kotlin"], 7L, Refresh.TRUE)

        then:
        id == "generated"
        IndexRequest request = driver.fake.requests[0] as IndexRequest
        request.id() == null
        request.version() == 7L
        request.versionType() == VersionType.External
        request.refresh().jsonValue() == "true"
        driver.fake.urls[0] == "POST /books/_doc"

        where:
        kind << KINDS
        driver = driver(kind)
    }

    @Unroll("#kind index conflict")
    void "a version conflict on index is an OptimisticLockException"() {
        given:
        driver.fake.answer(IndexRequest, conflict(409, "current version [8] is higher"))

        when:
        driver.index("books", "42", [title: "Kotlin"], 7L, Refresh.NONE)

        then:
        OptimisticLockException e = thrown()
        e.message == "Document 42 was modified concurrently: current version [8] is higher"
        e.cause instanceof OpenSearchException

        where:
        kind << KINDS
        driver = driver(kind)
    }

    @Unroll("#kind index failure")
    void "another OpenSearch failure on index passes through"() {
        given:
        driver.fake.answer(IndexRequest, conflict(400, "bad request"))

        when:
        driver.index("books", "42", [title: "Kotlin"], Refresh.NONE)

        then:
        OpenSearchException e = thrown()
        e.status() == 400

        where:
        kind << KINDS
        driver = driver(kind)
    }

    @Unroll("#kind get")
    void "get and getHit read a found document"() {
        given:
        driver.fake.answer(GetRequest, '{"_index":"books","_id":"1","_version":2,"found":true,"_source":{"title":"Kotlin"}}')

        when:
        Map<String, Object> source = driver.get("books", "1")
        SearchHit hit = driver.getHit("books", "1")

        then:
        source == [title: "Kotlin"]
        hit.id == "1"
        hit.score == null
        hit.source == [title: "Kotlin"]
        hit.version == 2L
        hit.highlights == [:]
        hit.sort == []
        driver.fake.urls == ["GET /books/_doc/1", "GET /books/_doc/1"]

        where:
        kind << KINDS
        driver = driver(kind)
    }

    @Unroll("#kind get missing")
    void "get and getHit of a missing document are empty"() {
        given:
        driver.fake.answer(GetRequest, '{"_index":"books","_id":"9","found":false}')

        expect:
        driver.get("books", "9") == null
        driver.getHit("books", "9") == null

        where:
        kind << KINDS
        driver = driver(kind)
    }

    @Unroll("#kind delete #result")
    void "delete reports whether a document was deleted"() {
        given:
        driver.fake.answer(DeleteRequest, writeResponse(result, "1"))

        expect:
        driver.delete("books", "1", Refresh.NONE) == deleted
        (driver.fake.requests[0] as DeleteRequest).refresh().jsonValue() == "false"
        driver.fake.urls[0] == "DELETE /books/_doc/1"

        where:
        [kind, result] << [KINDS, ["deleted", "not_found"]].combinations()
        driver = driver(kind)
        deleted = result == "deleted"
    }

    @Unroll("#kind deleteByQuery #refresh")
    void "deleteByQuery sends true or false as refresh and reads the deleted count"() {
        given:
        driver.fake.answer(DeleteByQueryRequest, '{"took":5,"timed_out":false,"deleted":3,"batches":1,"version_conflicts":0,"noops":0,"total":3,"failures":[]}')

        expect:
        driver.deleteByQuery("books", '{"query":{"term":{"author":"Jones"}}}', refresh) == 3
        (driver.fake.requests[0] as DeleteByQueryRequest).refresh().jsonValue() == expected
        (driver.fake.requests[0] as DeleteByQueryRequest).query().isTerm()
        driver.fake.urls[0] == "POST /books/_delete_by_query"

        where:
        [kind, refresh] << [KINDS, [Refresh.NONE, Refresh.WAIT_FOR]].combinations()
        driver = driver(kind)
        expected = refresh == Refresh.NONE ? "false" : "true"
    }

    @Unroll("#kind deleteByQuery without count")
    void "a delete by query response without a deleted count deletes nothing"() {
        given:
        driver.fake.answer(DeleteByQueryRequest, '{"took":1,"timed_out":false}')

        expect:
        driver.deleteByQuery("books", '{}', Refresh.TRUE) == 0

        where:
        kind << KINDS
        driver = driver(kind)
    }

    @Unroll("#kind bulk")
    void "bulkIndex sends one index operation per document and collects the ids"() {
        given:
        driver.fake.answer(BulkRequest, bulkResponse(bulkItem("1", 201), bulkItem("2", 200)))

        when:
        List<String> ids = driver.bulkIndex("books", [new BulkDocument("1", [title: "Kotlin"]), new BulkDocument(null, [title: "Scala"], 4L)], Refresh.WAIT_FOR)

        then:
        ids == ["1", "2"]
        BulkRequest request = driver.fake.requests[0] as BulkRequest
        request.index() == "books"
        request.refresh().jsonValue() == "wait_for"
        request.operations().size() == 2
        request.operations().every { it.isIndex() }
        request.operations()[0].index().id() == "1"
        request.operations()[0].index().version() == null
        request.operations()[0].index().document() == [title: "Kotlin"]
        request.operations()[1].index().id() == null
        request.operations()[1].index().version() == 4L
        request.operations()[1].index().versionType() == VersionType.External
        driver.fake.urls[0] == "POST /books/_bulk"

        where:
        kind << KINDS
        driver = driver(kind)
    }

    @Unroll("#kind bulk item error")
    void "a body key the transport cannot forward fails instead of being dropped"() {
        when:
        driver.search("books", '{"query":{"match_all":{}},"aggs":{"authors":{"terms":{"field":"author"}}}}')

        then:
        DataAccessException e = thrown()
        e.message.contains("'aggs'")
        driver.fake.requests.isEmpty()

        where:
        kind << KINDS
        driver = driver(kind)
    }

    void "a failed bulk item is a DataAccessException"() {
        given:
        driver.fake.answer(BulkRequest, bulkResponse(bulkItem("1", 201), bulkItem("2", 400, '{"type":"mapper_parsing_exception","reason":"failed to parse"}')))

        when:
        driver.bulkIndex("books", [new BulkDocument("1", [:]), new BulkDocument("2", [:])], Refresh.NONE)

        then:
        DataAccessException e = thrown()
        !(e instanceof OptimisticLockException)
        e.message == "OpenSearch bulk item failed: failed to parse"

        where:
        kind << KINDS
        driver = driver(kind)
    }

    @Unroll("#kind bulk item conflict")
    void "a 409 bulk item is an OptimisticLockException"() {
        given:
        driver.fake.answer(BulkRequest, bulkResponse(bulkItem("2", 409, '{"type":"version_conflict_engine_exception","reason":"version conflict"}')))

        when:
        driver.bulkIndex("books", [new BulkDocument("2", [:], 1L)], Refresh.NONE)

        then:
        OptimisticLockException e = thrown()
        e.message == "Document 2 was modified concurrently: version conflict"

        where:
        kind << KINDS
        driver = driver(kind)
    }

    void "a 409 of the whole bulk request is an OptimisticLockException on the blocking transport"() {
        given:
        def driver = sync()
        driver.fake.answer(BulkRequest, conflict(409, "version conflict"))

        when:
        driver.bulkIndex("books", [new BulkDocument("2", [:], 1L)], Refresh.NONE)

        then:
        OptimisticLockException e = thrown()
        e.message == "Document null was modified concurrently: version conflict"
    }

    void "indexExists reads the boolean response of the blocking transport"() {
        given:
        def driver = sync()
        driver.fake.answer(ExistsRequest, exists)

        expect:
        driver.transport.indexExists("books") == exists
        driver.fake.urls[0] == "HEAD /books"

        where:
        exists << [true, false]
    }

    void "createIndex sends the mappings of the body"() {
        given:
        def driver = sync()
        driver.fake.answer(CreateIndexRequest, '{"acknowledged":true,"shards_acknowledged":true,"index":"books"}')

        when:
        driver.transport.createIndex("books", '''{
            "settings":{"number_of_shards":1},
            "mappings":{"properties":{"title":{"type":"text"},"year":{"type":"integer"}}}
        }''')

        then:
        CreateIndexRequest request = driver.fake.requests[0] as CreateIndexRequest
        request.index() == "books"
        request.mappings().properties().keySet() == ["title", "year"] as Set
        request.mappings().properties()["title"].isText()
        request.mappings().properties()["year"].isInteger()
        driver.fake.urls[0] == "PUT /books"
    }

    void "createIndex without mappings sends only the index name"() {
        given:
        def driver = sync()
        driver.fake.answer(CreateIndexRequest, '{"acknowledged":true,"shards_acknowledged":true,"index":"books"}')

        when:
        driver.transport.createIndex("books", '{}')

        then:
        (driver.fake.requests[0] as CreateIndexRequest).mappings() == null
    }

    @Unroll("#kind #operation fails with an IOException")
    void "an IOException of the client is a DataAccessException"() {
        given:
        driver.fake.failWith(new IOException("connection refused"))

        when:
        call(driver)

        then:
        DataAccessException e = thrown()
        e.message.startsWith("OpenSearch ")
        e.message.endsWith("failed: connection refused")
        e.cause instanceof IOException

        where:
        [kind, operation] << [KINDS, [
            "search", "count", "index", "versionedIndex", "get", "getHit", "bulkIndex", "delete", "deleteByQuery"
        ]].combinations()
        driver = driver(kind)
        call = OPERATIONS[operation]
    }

    void "an IOException of the blocking indices client is a DataAccessException"() {
        given:
        def driver = sync()
        driver.fake.failWith(new IOException("connection refused"))

        when:
        driver.transport.indexExists("books")

        then:
        DataAccessException e = thrown()
        e.message == "OpenSearch index exists failed: connection refused"

        when:
        driver.transport.createIndex("books", '{}')

        then:
        e = thrown()
        e.message == "OpenSearch create index failed: connection refused"
    }

    static final Map<String, Closure<?>> OPERATIONS = [
        search        : { Driver d -> d.search("books", '{}') },
        count         : { Driver d -> d.count("books", '{}') },
        index         : { Driver d -> d.index("books", "1", [:], Refresh.NONE) },
        versionedIndex: { Driver d -> d.index("books", "1", [:], 1L, Refresh.NONE) },
        get           : { Driver d -> d.get("books", "1") },
        getHit        : { Driver d -> d.getHit("books", "1") },
        bulkIndex     : { Driver d -> d.bulkIndex("books", [new BulkDocument("1", [:])], Refresh.NONE) },
        delete        : { Driver d -> d.delete("books", "1", Refresh.NONE) },
        deleteByQuery : { Driver d -> d.deleteByQuery("books", '{}', Refresh.NONE) },
    ]

    // ---- the static helpers ----

    void "fieldValue maps Java scalars to the client's field values"() {
        expect:
        OpenSearchRequests.fieldValue(value)._get() == expected
        OpenSearchRequests.fieldValue(value).isLong() == (expected instanceof Long)
        OpenSearchRequests.fieldValue(value).isDouble() == (expected instanceof Double)
        OpenSearchRequests.fieldValue(value).isBoolean() == (expected instanceof Boolean)
        OpenSearchRequests.fieldValue(value).isString() == (expected instanceof String)

        where:
        value               | expected
        "Kotlin"            | "Kotlin"
        7L                  | 7L
        7                   | 7L
        (short) 7           | 7L
        (byte) 7            | 7L
        1.5d                | 1.5d
        1.5f                | 1.5d
        new BigDecimal("2") | 2.0d
        true                | true
        false               | false
        null                | "null"
        new StringBuilder("x") | "x"
    }

    void "sortValues unwraps the field values"() {
        expect:
        OpenSearchRequests.sortValues(null) == []
        OpenSearchRequests.sortValues([]) == []
        OpenSearchRequests.sortValues([FieldValue.of("a"), FieldValue.of(1L), FieldValue.of(2.5d), FieldValue.of(true)]) == ["a", 1L, 2.5d, true]
    }

    void "plain turns JSON-P values into Java values"() {
        given:
        JsonValue json = Json.createObjectBuilder()
            .add("name", "Kotlin")
            .add("year", 2011)
            .add("rating", 4.5)
            .add("stable", true)
            .add("beta", false)
            .addNull("eol")
            .add("tags", Json.createArrayBuilder().add("jvm").add(1).add(Json.createObjectBuilder().add("nested", true)))
            .build()

        expect:
        OpenSearchRequests.plain(json) == [name: "Kotlin", year: 2011L, rating: 4.5d, stable: true, beta: false, eol: null, tags: ["jvm", 1L, [nested: true]]]
        OpenSearchRequests.plain(json)["year"] instanceof Long
        OpenSearchRequests.plain(json)["rating"] instanceof Double
        OpenSearchRequests.plain(JsonValue.NULL) == null
        OpenSearchRequests.plain(null) == null
        OpenSearchRequests.plain("already plain") == "already plain"
        OpenSearchRequests.plain(42) == 42
    }

    void "plain copies Java maps and lists holding JSON-P values"() {
        given:
        def mixed = [list: [Json.createValue("a"), Json.createValue(3)], map: [(1): Json.createValue(2.5), inner: [Json.createArrayBuilder().add(1).build()]]]

        expect:
        OpenSearchRequests.plain(mixed) == [list: ["a", 3L], map: ["1": 2.5d, inner: [[1L]]]]
    }

    void "parse reads a body into plain values"() {
        given:
        def mapper = new FakeOpenSearchTransport().mapper

        expect:
        OpenSearchRequests.parse(mapper, '{"query":{"match_all":{}},"size":3,"min_score":0.5,"version":true,"missing":null}') == [query: [match_all: [:]], size: 3, min_score: 0.5d, version: true, missing: null]
    }

    void "scalar reads a parsed scalar in the requested type"() {
        given:
        def mapper = new FakeOpenSearchTransport().mapper

        expect:
        OpenSearchRequests.scalar(mapper, null, Integer) == null
        OpenSearchRequests.scalar(mapper, 5, Integer) == 5
        OpenSearchRequests.scalar(mapper, 5L, Integer) == 5
        OpenSearchRequests.scalar(mapper, 5, Double) == 5.0d
        OpenSearchRequests.scalar(mapper, 2.5d, Double) == 2.5d
        OpenSearchRequests.scalar(mapper, 5, Long) == 5L
        OpenSearchRequests.scalar(mapper, "7", Integer) == 7
        OpenSearchRequests.scalar(mapper, true, Boolean) == true
        OpenSearchRequests.scalar(mapper, Json.createValue(7), Integer) == 7
        OpenSearchRequests.scalar(mapper, Json.createValue(7.5), Double) == 7.5d
        OpenSearchRequests.scalar(mapper, JsonValue.TRUE, Boolean) == true
        OpenSearchRequests.scalar(mapper, Json.createValue("x"), String) == "x"
    }

    void "highlightWithFieldMap merges the field list of the query builder into a map"() {
        expect:
        OpenSearchRequests.highlightWithFieldMap([pre_tags: ["<b>"], fields: [[title: [:]], [author: [number_of_fragments: 2]]]]) ==
            [pre_tags: ["<b>"], fields: [title: [:], author: [number_of_fragments: 2]]]
        OpenSearchRequests.highlightWithFieldMap([fields: [[title: [:]], "not a map"]]) == [fields: [title: [:]]]
        OpenSearchRequests.highlightWithFieldMap([fields: [title: [:]]]) == [fields: [title: [:]]]
        OpenSearchRequests.highlightWithFieldMap([pre_tags: ["<b>"]]) == [pre_tags: ["<b>"]]
        OpenSearchRequests.highlightWithFieldMap("fields") == "fields"
        OpenSearchRequests.highlightWithFieldMap(null) == null
    }

    void "the refresh policies map to the client's refresh values"() {
        expect:
        OpenSearchRequests.refresh(Refresh.NONE).jsonValue() == "false"
        OpenSearchRequests.refresh(Refresh.WAIT_FOR).jsonValue() == "wait_for"
        OpenSearchRequests.refresh(Refresh.TRUE).jsonValue() == "true"
        OpenSearchRequests.deleteByQueryRefresh(Refresh.NONE).jsonValue() == "false"
        OpenSearchRequests.deleteByQueryRefresh(Refresh.WAIT_FOR).jsonValue() == "true"
        OpenSearchRequests.deleteByQueryRefresh(Refresh.TRUE).jsonValue() == "true"
    }

    void "conflictOrFailure maps a 409 to an OptimisticLockException and passes other failures through"() {
        given:
        def stale = conflict(409, "stale")
        def other = conflict(500, "boom")
        def io = new IOException("io")

        expect:
        OpenSearchRequests.conflictOrFailure(stale, "1") instanceof OptimisticLockException
        OpenSearchRequests.conflictOrFailure(stale, "1").message == "Document 1 was modified concurrently: stale"
        OpenSearchRequests.conflictOrFailure(stale, "1").cause.is(stale)
        OpenSearchRequests.conflictOrFailure(new CompletionException(stale), "2") instanceof OptimisticLockException
        OpenSearchRequests.conflictOrFailure(new CompletionException(stale), "2").cause.is(stale)
        OpenSearchRequests.conflictOrFailure(other, "1").is(other)
        OpenSearchRequests.conflictOrFailure(new CompletionException(other), "1").is(other)
        OpenSearchRequests.conflictOrFailure(new CompletionException(io), "1") instanceof DataAccessException
        OpenSearchRequests.conflictOrFailure(new CompletionException(io), "1").message == "OpenSearch request failed: io"
        OpenSearchRequests.conflictOrFailure(new CompletionException(io), "1").cause.is(io)
        OpenSearchRequests.conflictOrFailure(io, "1").cause.is(io)

        and: "a completion exception without a cause is passed through"
        def bare = new CompletionException("bare", null)
        OpenSearchRequests.conflictOrFailure(bare, "1").is(bare)
    }

    void "BulkResponses.ids collects ids, fails on an item error and maps a 409 to an OptimisticLockException"() {
        given:
        def items = [[id: "1", status: 201], [id: "2", status: 200]]
        def failed = [[id: "1", status: 201], [id: "2", status: 400, error: "bad"]]
        def stale = [[id: "3", status: 409, error: "conflict"]]

        expect:
        BulkResponses.ids("OpenSearch", items, { it.error }, { it.id }, { it.status }) == ["1", "2"]

        when:
        BulkResponses.ids("OpenSearch", failed, { it.error }, { it.id }, { it.status })

        then:
        DataAccessException e = thrown()
        e.message == "OpenSearch bulk item failed: bad"

        when:
        BulkResponses.ids("OpenSearch", stale, { it.error }, { it.id }, { it.status })

        then:
        OptimisticLockException lock = thrown()
        lock.message == "Document 3 was modified concurrently: conflict"
    }

    // ---- drivers ----

    static final List<String> KINDS = ["blocking", "async"]

    static Driver driver(String kind) {
        kind == "blocking" ? sync() : async()
    }

    static SyncDriver sync() {
        def fake = new FakeOpenSearchTransport()
        new SyncDriver(fake: fake, transport: new OpenSearchSearchTransport(new OpenSearchClient(fake)))
    }

    static AsyncDriver async() {
        def fake = new FakeOpenSearchTransport()
        new AsyncDriver(fake: fake, transport: new OpenSearchAsyncSearchTransport(fake))
    }

    /**
     * Presents the blocking and the async transport through one blocking API, so the same features
     * drive both.
     */
    static abstract class Driver {
        FakeOpenSearchTransport fake

        abstract SearchHits search(String index, String body)

        abstract long count(String index, String body)

        abstract String index(String index, String id, Map<String, Object> source, Refresh refresh)

        abstract String index(String index, String id, Map<String, Object> source, Long version, Refresh refresh)

        abstract Map<String, Object> get(String index, String id)

        abstract SearchHit getHit(String index, String id)

        abstract List<String> bulkIndex(String index, List<BulkDocument> documents, Refresh refresh)

        abstract boolean delete(String index, String id, Refresh refresh)

        abstract long deleteByQuery(String index, String body, Refresh refresh)
    }

    static class SyncDriver extends Driver {
        SearchTransport transport

        SearchHits search(String index, String body) { transport.search(index, body) }

        long count(String index, String body) { transport.count(index, body) }

        String index(String index, String id, Map<String, Object> source, Refresh refresh) { transport.index(index, id, source, refresh) }

        String index(String index, String id, Map<String, Object> source, Long version, Refresh refresh) { transport.index(index, id, source, version, refresh) }

        Map<String, Object> get(String index, String id) { transport.get(index, id) }

        SearchHit getHit(String index, String id) { transport.getHit(index, id) }

        List<String> bulkIndex(String index, List<BulkDocument> documents, Refresh refresh) { transport.bulkIndex(index, documents, refresh) }

        boolean delete(String index, String id, Refresh refresh) { transport.delete(index, id, refresh) }

        long deleteByQuery(String index, String body, Refresh refresh) { transport.deleteByQuery(index, body, refresh) }

        String toString() { "blocking" }
    }

    static class AsyncDriver extends Driver {
        AsyncSearchTransport transport

        SearchHits search(String index, String body) { await(transport.search(index, body)) }

        long count(String index, String body) { await(transport.count(index, body)) }

        String index(String index, String id, Map<String, Object> source, Refresh refresh) { await(transport.index(index, id, source, refresh)) }

        String index(String index, String id, Map<String, Object> source, Long version, Refresh refresh) { await(transport.index(index, id, source, version, refresh)) }

        Map<String, Object> get(String index, String id) { await(transport.get(index, id)).orElse(null) }

        SearchHit getHit(String index, String id) { await(transport.getHit(index, id)).orElse(null) }

        List<String> bulkIndex(String index, List<BulkDocument> documents, Refresh refresh) { await(transport.bulkIndex(index, documents, refresh)) }

        boolean delete(String index, String id, Refresh refresh) { await(transport.delete(index, id, refresh)) }

        long deleteByQuery(String index, String body, Refresh refresh) { await(transport.deleteByQuery(index, body, refresh)) }

        /** Unwraps the failure of the stage, so the features assert the same exception as on the blocking transport. */
        private static <T> T await(CompletionStage<T> stage) {
            try {
                return stage.toCompletableFuture().join()
            } catch (CompletionException e) {
                throw e.cause ?: e
            }
        }

        String toString() { "async" }
    }
}
