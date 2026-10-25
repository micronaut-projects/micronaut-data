package io.micronaut.data.search.elasticsearch

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient
import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.ElasticsearchException
import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.Refresh
import co.elastic.clients.elasticsearch._types.VersionType
import co.elastic.clients.elasticsearch.core.BulkRequest
import co.elastic.clients.elasticsearch.core.CountRequest
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest
import co.elastic.clients.elasticsearch.core.DeleteRequest
import co.elastic.clients.elasticsearch.core.GetRequest
import co.elastic.clients.elasticsearch.core.IndexRequest
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import co.elastic.clients.elasticsearch.indices.ExistsRequest
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration
import io.micronaut.data.search.document.runtime.AsyncSearchTransport
import io.micronaut.data.search.document.runtime.BulkResponses
import io.micronaut.data.search.document.runtime.SearchTransport
import io.micronaut.data.search.document.runtime.SearchTransport.BulkDocument
import io.micronaut.data.search.elasticsearch.transport.ElasticsearchAsyncSearchTransport
import io.micronaut.data.search.elasticsearch.transport.ElasticsearchSearchTransport
import org.jspecify.annotations.Nullable
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException

import static io.micronaut.data.search.document.conf.DocumentSearchConfiguration.Refresh.NONE
import static io.micronaut.data.search.document.conf.DocumentSearchConfiguration.Refresh.TRUE
import static io.micronaut.data.search.document.conf.DocumentSearchConfiguration.Refresh.WAIT_FOR

/**
 * Drives the blocking and the async Elasticsearch transports through the real Elasticsearch Java
 * client over a {@link FakeElasticsearchTransport}, so request shapes, response mapping and the
 * error paths are verified without a server.
 */
class FakeClientTransportSpec extends Specification {

    static final String INDEX = "books"
    static final String SHARDS = '"_shards":{"total":1,"successful":1,"failed":0}'

    @Shared
    FakeElasticsearchTransport fake = new FakeElasticsearchTransport()

    @Shared
    ElasticsearchSearchTransport sync = new ElasticsearchSearchTransport(new ElasticsearchClient(fake))

    @Shared
    ElasticsearchAsyncSearchTransport async = new ElasticsearchAsyncSearchTransport(new ElasticsearchAsyncClient(fake))

    /** The async transport behind the blocking SPI, so the data-driven features run both. */
    @Shared
    SearchTransport asyncAsBlocking = new BlockingOverAsync(async)

    void setup() {
        fake.reset()
    }

    // --- search ---------------------------------------------------------------------------------

    void "search maps hits, highlights, sort values, versions, total and max score (#name)"() {
        given:
        fake.respond("es/search", """{"took":3,"timed_out":false,$SHARDS,"hits":{"total":{"value":42,"relation":"eq"},"max_score":1.5,"hits":[
            {"_index":"$INDEX","_id":"1","_score":1.5,"_version":7,"_source":{"title":"Kotlin in Action","pages":360},
             "highlight":{"title":["<em>Kotlin</em> in Action"]},"sort":[1700000000000,"kotlin"]},
            {"_index":"$INDEX","_id":"2","_score":null,"sort":[]}
        ]}}""")

        when:
        def hits = transport.search(INDEX, '{"query":{"match":{"title":"kotlin"}},"from":10,"size":5}')

        then:
        hits.total() == 42L
        hits.maxScore() == 1.5d
        hits.hits().size() == 2
        with(hits.hits()[0]) {
            id() == "1"
            score() == 1.5d
            version() == 7L
            source() == [title: "Kotlin in Action", pages: 360]
            highlights() == [title: ["<em>Kotlin</em> in Action"]]
            sort() == [1700000000000L, "kotlin"]
        }
        with(hits.hits()[1]) {
            id() == "2"
            score() == null
            version() == null
            source() == [:]
            highlights() == [:]
            sort() == []
        }

        and: "the body is passed through to the client"
        fake.endpoints == ["es/search"]
        with(fake.request() as SearchRequest) {
            index() == [INDEX]
            from() == 10
            size() == 5
            query().isMatch()
            query().match().field() == "title"
            query().match().query().stringValue() == "kotlin"
        }

        where:
        name    | transport
        "sync"  | sync
        "async" | asyncAsBlocking
    }

    void "a search response without total and max score counts the hits (#name)"() {
        given:
        fake.respond("es/search", """{"took":1,"timed_out":false,$SHARDS,"hits":{"hits":[
            {"_index":"$INDEX","_id":"a","_source":{"title":"A"}},
            {"_index":"$INDEX","_id":"b","_source":{"title":"B"}},
            {"_index":"$INDEX","_id":"c","_source":{"title":"C"}}
        ]}}""")

        when:
        def hits = transport.search(INDEX, '{"query":{"match_all":{}}}')

        then:
        hits.total() == 3L
        hits.maxScore() == null
        hits.hits()*.id() == ["a", "b", "c"]
        hits.hits().every { it.score() == null && it.sort().isEmpty() && it.highlights().isEmpty() }

        where:
        name    | transport
        "sync"  | sync
        "async" | asyncAsBlocking
    }

    // --- count ----------------------------------------------------------------------------------

    void "count returns the count of the query (#name)"() {
        given:
        fake.respond("es/count", """{"count":7,$SHARDS}""")

        when:
        long count = transport.count(INDEX, '{"query":{"term":{"author":"Subramaniam"}}}')

        then:
        count == 7L
        fake.endpoints == ["es/count"]
        with(fake.request() as CountRequest) {
            index() == [INDEX]
            query().isTerm()
            query().term().field() == "author"
        }

        where:
        name    | transport
        "sync"  | sync
        "async" | asyncAsBlocking
    }

    // --- index ----------------------------------------------------------------------------------

    void "index without a version lets the store assign the id and does not set versioning (#name)"() {
        given:
        fake.respond("es/index", writeResponse("generated-id", "created", 1))

        when:
        String documentId = transport.index(INDEX, null, [title: "Programming Kotlin"], NONE)

        then:
        documentId == "generated-id"
        fake.endpoints == ["es/index"]
        with(fake.request() as IndexRequest) {
            index() == INDEX
            id() == null
            document() == [title: "Programming Kotlin"]
            version() == null
            versionType() == null
            refresh() == Refresh.False
        }

        where:
        name    | transport
        "sync"  | sync
        "async" | asyncAsBlocking
    }

    void "index with a version writes with external versioning and the refresh policy #policy (#name)"() {
        given:
        fake.respond("es/index", writeResponse("k1", "updated", 3))

        when:
        String documentId = transport.index(INDEX, "k1", [title: "Programming Kotlin"], 3L, policy)

        then:
        documentId == "k1"
        with(fake.request() as IndexRequest) {
            index() == INDEX
            id() == "k1"
            document() == [title: "Programming Kotlin"]
            version() == 3L
            versionType() == VersionType.External
            refresh() == expected
        }

        where:
        name    | transport       | policy   | expected
        "sync"  | sync            | NONE     | Refresh.False
        "sync"  | sync            | WAIT_FOR | Refresh.WaitFor
        "sync"  | sync            | TRUE     | Refresh.True
        "async" | asyncAsBlocking | NONE     | Refresh.False
        "async" | asyncAsBlocking | WAIT_FOR | Refresh.WaitFor
        "async" | asyncAsBlocking | TRUE     | Refresh.True
    }

    void "a version conflict on index is an OptimisticLockException (#name, wrapped=#wrapped)"() {
        given:
        def conflict = FakeElasticsearchTransport.error("es/index", 409, "version_conflict_engine_exception", "current version [4] is higher")
        fake.fail("es/index", conflict)
        fake.wrapAsyncFailures = wrapped

        when:
        transport.index(INDEX, "k1", [title: "Stale"], 3L, NONE)

        then:
        OptimisticLockException e = thrown()
        e.message == "Document k1 was modified concurrently: current version [4] is higher"
        e.cause.is(conflict)

        where:
        name    | transport       | wrapped
        "sync"  | sync            | false
        "async" | asyncAsBlocking | false
        "async" | asyncAsBlocking | true
    }

    void "another Elasticsearch error on index passes through unchanged (#name, wrapped=#wrapped)"() {
        given:
        def failure = FakeElasticsearchTransport.error("es/index", 400, "mapper_parsing_exception", "failed to parse")
        fake.fail("es/index", failure)
        fake.wrapAsyncFailures = wrapped

        when:
        transport.index(INDEX, "k1", [title: "Bad"], NONE)

        then:
        ElasticsearchException e = thrown()
        e.is(failure)
        e.status() == 400

        where:
        name    | transport       | wrapped
        "sync"  | sync            | false
        "async" | asyncAsBlocking | false
        "async" | asyncAsBlocking | true
    }

    void "a runtime failure on index passes through unchanged (#name)"() {
        given:
        def failure = new IllegalStateException("client broke")
        fake.fail("es/index", failure)

        when:
        transport.index(INDEX, "k1", [title: "Bad"], NONE)

        then:
        IllegalStateException e = thrown()
        e.is(failure)

        where:
        name    | transport
        "sync"  | sync
        "async" | asyncAsBlocking
    }

    void "an I/O failure on async index is a DataAccessException (wrapped=#wrapped)"() {
        given:
        def failure = new IOException("connection reset")
        fake.fail("es/index", failure)
        fake.wrapAsyncFailures = wrapped

        when:
        asyncAsBlocking.index(INDEX, "k1", [title: "Lost"], NONE)

        then:
        DataAccessException e = thrown()
        !(e instanceof OptimisticLockException)
        e.message == "Elasticsearch request failed: connection reset"
        e.cause.is(failure)

        where:
        wrapped << [false, true]
    }

    // --- get ------------------------------------------------------------------------------------

    void "get returns the source of a found document and null otherwise (#name)"() {
        given:
        fake.respond("es/get") { GetRequest request ->
            request.id() == "k1"
                ? """{"_index":"$INDEX","_id":"k1","_version":5,"_seq_no":4,"_primary_term":1,"found":true,"_source":{"title":"Programming Kotlin","pages":460}}"""
                : """{"_index":"$INDEX","_id":"${request.id()}","found":false}"""
        }

        expect:
        transport.get(INDEX, "k1") == [title: "Programming Kotlin", pages: 460]
        transport.get(INDEX, "missing") == null
        fake.endpoints == ["es/get", "es/get"]
        (fake.request(0) as GetRequest).index() == INDEX
        (fake.request(1) as GetRequest).id() == "missing"

        where:
        name    | transport
        "sync"  | sync
        "async" | asyncAsBlocking
    }

    void "getHit returns the source with its version and null when not found (#name)"() {
        given:
        fake.respond("es/get") { GetRequest request ->
            request.id() == "k1"
                ? """{"_index":"$INDEX","_id":"k1","_version":5,"_seq_no":4,"_primary_term":1,"found":true,"_source":{"title":"Programming Kotlin"}}"""
                : """{"_index":"$INDEX","_id":"${request.id()}","found":false}"""
        }

        when:
        def hit = transport.getHit(INDEX, "k1")

        then:
        hit.id() == "k1"
        hit.score() == null
        hit.version() == 5L
        hit.source() == [title: "Programming Kotlin"]
        hit.highlights() == [:]
        hit.sort() == []

        and:
        transport.getHit(INDEX, "missing") == null

        where:
        name    | transport
        "sync"  | sync
        "async" | asyncAsBlocking
    }

    // --- delete ---------------------------------------------------------------------------------

    void "delete reports whether a document was deleted and carries the refresh policy (#name)"() {
        given:
        fake.respond("es/delete") { DeleteRequest request ->
            writeResponse(request.id(), request.id() == "k1" ? "deleted" : "not_found", 2)
        }

        expect:
        transport.delete(INDEX, "k1", WAIT_FOR)
        !transport.delete(INDEX, "missing", NONE)
        fake.endpoints == ["es/delete", "es/delete"]
        with(fake.request(0) as DeleteRequest) {
            index() == INDEX
            id() == "k1"
            refresh() == Refresh.WaitFor
        }
        (fake.request(1) as DeleteRequest).refresh() == Refresh.False

        where:
        name    | transport
        "sync"  | sync
        "async" | asyncAsBlocking
    }

    void "deleteByQuery returns the deleted count and refreshes unless the policy is NONE (#name, #policy)"() {
        given:
        fake.respond("es/delete_by_query", '{"took":5,"timed_out":false,"total":3,"deleted":3,"batches":1,"version_conflicts":0,"noops":0,"retries":{"bulk":0,"search":0},"throttled_millis":0,"requests_per_second":-1.0,"throttled_until_millis":0,"failures":[]}')

        when:
        long deleted = transport.deleteByQuery(INDEX, '{"query":{"match_all":{}}}', policy)

        then:
        deleted == 3L
        fake.endpoints == ["es/delete_by_query"]
        with(fake.request() as DeleteByQueryRequest) {
            index() == [INDEX]
            query().isMatchAll()
            refresh() == expected
        }

        where:
        name    | transport       | policy   | expected
        "sync"  | sync            | NONE     | false
        "sync"  | sync            | WAIT_FOR | true
        "sync"  | sync            | TRUE     | true
        "async" | asyncAsBlocking | NONE     | false
        "async" | asyncAsBlocking | WAIT_FOR | true
        "async" | asyncAsBlocking | TRUE     | true
    }

    void "deleteByQuery without a deleted count reports zero (#name)"() {
        given:
        fake.respond("es/delete_by_query", '{"took":1,"timed_out":false}')

        expect:
        transport.deleteByQuery(INDEX, '{"query":{"match_all":{}}}', NONE) == 0L

        where:
        name    | transport
        "sync"  | sync
        "async" | asyncAsBlocking
    }

    // --- bulk -----------------------------------------------------------------------------------

    void "bulkIndex sends one index operation per document and returns the ids in order (#name)"() {
        given:
        fake.respond("es/bulk", """{"took":4,"errors":false,"items":[
            ${bulkItem("k1", 201, "created")},
            ${bulkItem("generated", 201, "created")},
            ${bulkItem("k3", 200, "updated")}
        ]}""")

        when:
        List<String> ids = transport.bulkIndex(INDEX, [
            new BulkDocument("k1", [title: "One"], 1L),
            new BulkDocument(null, [title: "Two"]),
            new BulkDocument("k3", [title: "Three"], null),
        ], WAIT_FOR)

        then:
        ids == ["k1", "generated", "k3"]
        fake.endpoints == ["es/bulk"]
        with(fake.request() as BulkRequest) {
            index() == INDEX
            refresh() == Refresh.WaitFor
            operations().size() == 3
            operations().every { it.isIndex() }
            with(operations()[0].index()) {
                id() == "k1"
                document() == [title: "One"]
                version() == 1L
                versionType() == VersionType.External
            }
            with(operations()[1].index()) {
                id() == null
                document() == [title: "Two"]
                version() == null
                versionType() == null
            }
            with(operations()[2].index()) {
                id() == "k3"
                document() == [title: "Three"]
                version() == null
                versionType() == null
            }
        }

        where:
        name    | transport
        "sync"  | sync
        "async" | asyncAsBlocking
    }

    void "a failed bulk item is a DataAccessException (#name)"() {
        given:
        fake.respond("es/bulk", """{"took":4,"errors":true,"items":[
            ${bulkItem("k1", 201, "created")},
            ${bulkError("k2", 400, "mapper_parsing_exception", "failed to parse field [pages]")}
        ]}""")

        when:
        transport.bulkIndex(INDEX, [new BulkDocument("k1", [title: "One"]), new BulkDocument("k2", [pages: "many"])], NONE)

        then:
        DataAccessException e = thrown()
        !(e instanceof OptimisticLockException)
        e.message == "Elasticsearch bulk item failed: failed to parse field [pages]"

        where:
        name    | transport
        "sync"  | sync
        "async" | asyncAsBlocking
    }

    void "a conflicting bulk item is an OptimisticLockException (#name)"() {
        given:
        fake.respond("es/bulk", """{"took":4,"errors":true,"items":[
            ${bulkError("k1", 409, "version_conflict_engine_exception", "current version [5] is higher")},
            ${bulkItem("k2", 201, "created")}
        ]}""")

        when:
        transport.bulkIndex(INDEX, [new BulkDocument("k1", [title: "One"], 2L), new BulkDocument("k2", [title: "Two"])], NONE)

        then:
        OptimisticLockException e = thrown()
        e.message == "Document k1 was modified concurrently: current version [5] is higher"

        where:
        name    | transport
        "sync"  | sync
        "async" | asyncAsBlocking
    }

    void "a rejected bulk request is mapped like a single index: #status -> #expected.simpleName"() {
        given:
        def failure = FakeElasticsearchTransport.error("es/bulk", status, "some_exception", "reason")
        fake.fail("es/bulk", failure)

        when:
        sync.bulkIndex(INDEX, [new BulkDocument("k1", [title: "One"], 2L)], NONE)

        then:
        Throwable e = thrown()
        expected.isInstance(e)
        status == 409 ? e.message == "Document null was modified concurrently: reason" && e.cause.is(failure) : e.is(failure)

        where:
        status | expected
        409    | OptimisticLockException
        500    | ElasticsearchException
    }

    // --- indices (blocking transport only) -----------------------------------------------------

    void "indexExists reads the boolean endpoint"() {
        given:
        fake.respond("es/indices.exists") { ExistsRequest request -> request.index() == [INDEX] }

        expect:
        sync.indexExists(INDEX)
        !sync.indexExists("other")
        fake.endpoints == ["es/indices.exists", "es/indices.exists"]
    }

    void "createIndex passes the mappings and settings through"() {
        given:
        fake.respond("es/indices.create", """{"acknowledged":true,"shards_acknowledged":true,"index":"$INDEX"}""")

        when:
        sync.createIndex(INDEX, '{"settings":{"number_of_shards":1},"mappings":{"properties":{"title":{"type":"text"}}}}')

        then:
        fake.endpoints == ["es/indices.create"]
        with(fake.request() as CreateIndexRequest) {
            index() == INDEX
            settings().numberOfShards() == "1"
            mappings().properties()["title"].isText()
        }
    }

    // --- I/O failures ---------------------------------------------------------------------------

    void "an I/O failure of the blocking #method is a DataAccessException"() {
        given:
        def failure = new IOException("connection refused")
        fake.fail(endpoint, failure)

        when:
        invoke(sync)

        then:
        DataAccessException e = thrown()
        e.message == "Elasticsearch $operation failed: connection refused"
        e.cause.is(failure)

        where:
        method          | endpoint             | operation         | invoke
        "search"        | "es/search"          | "search"          | { SearchTransport t -> t.search(INDEX, '{"query":{"match_all":{}}}') }
        "count"         | "es/count"           | "count"           | { SearchTransport t -> t.count(INDEX, '{"query":{"match_all":{}}}') }
        "index"         | "es/index"           | "index"           | { SearchTransport t -> t.index(INDEX, "k1", [title: "One"], NONE) }
        "bulkIndex"     | "es/bulk"            | "bulk"            | { SearchTransport t -> t.bulkIndex(INDEX, [new BulkDocument("k1", [title: "One"])], NONE) }
        "get"           | "es/get"             | "get"             | { SearchTransport t -> t.get(INDEX, "k1") }
        "getHit"        | "es/get"             | "get"             | { SearchTransport t -> t.getHit(INDEX, "k1") }
        "delete"        | "es/delete"          | "delete"          | { SearchTransport t -> t.delete(INDEX, "k1", NONE) }
        "deleteByQuery" | "es/delete_by_query" | "delete by query" | { SearchTransport t -> t.deleteByQuery(INDEX, '{"query":{"match_all":{}}}', NONE) }
        "indexExists"   | "es/indices.exists"  | "index exists"    | { SearchTransport t -> t.indexExists(INDEX) }
        "createIndex"   | "es/indices.create"  | "create index"    | { SearchTransport t -> t.createIndex(INDEX, '{"mappings":{}}') }
    }

    void "an I/O failure of the async #method fails the stage with the cause"() {
        given:
        def failure = new IOException("connection refused")
        fake.fail(endpoint, failure)

        when:
        invoke(async).toCompletableFuture().get()

        then:
        ExecutionException e = thrown()
        e.cause.is(failure)

        where:
        method          | endpoint             | invoke
        "search"        | "es/search"          | { AsyncSearchTransport t -> t.search(INDEX, '{"query":{"match_all":{}}}') }
        "count"         | "es/count"           | { AsyncSearchTransport t -> t.count(INDEX, '{"query":{"match_all":{}}}') }
        "bulkIndex"     | "es/bulk"            | { AsyncSearchTransport t -> t.bulkIndex(INDEX, [new BulkDocument("k1", [title: "One"])], NONE) }
        "get"           | "es/get"             | { AsyncSearchTransport t -> t.get(INDEX, "k1") }
        "getHit"        | "es/get"             | { AsyncSearchTransport t -> t.getHit(INDEX, "k1") }
        "delete"        | "es/delete"          | { AsyncSearchTransport t -> t.delete(INDEX, "k1", NONE) }
        "deleteByQuery" | "es/delete_by_query" | { AsyncSearchTransport t -> t.deleteByQuery(INDEX, '{"query":{"match_all":{}}}', NONE) }
    }

    // --- shared helpers -------------------------------------------------------------------------

    void "BulkResponses.ids collects the ids and fails on the first rejected item"() {
        given:
        def ok = [[id: "a", error: null, status: 201], [id: "b", error: null, status: 200]]
        def rejected = [[id: "a", error: null, status: 201], [id: "b", error: "bad document", status: 400], [id: "c", error: null, status: 201]]
        def conflicting = [[id: "a", error: "stale", status: 409]]
        def ids = { List<Map> items -> BulkResponses.ids("Store", items, { it.error }, { it.id }, { it.status as int }) }

        expect:
        ids(ok) == ["a", "b"]

        when:
        ids(rejected)

        then:
        DataAccessException e = thrown()
        !(e instanceof OptimisticLockException)
        e.message == "Store bulk item failed: bad document"

        when:
        ids(conflicting)

        then:
        OptimisticLockException conflict = thrown()
        conflict.message == "Document a was modified concurrently: stale"
    }

    void "conflictOrFailure unwraps a CompletionException and keeps the cause"() {
        given:
        def conflict = FakeElasticsearchTransport.error("es/index", 409, "version_conflict_engine_exception", "stale")
        def io = new IOException("lost")
        def runtime = new IllegalArgumentException("bad")

        expect:
        with(ElasticsearchAsyncSearchTransport.conflictOrFailure(new CompletionException(conflict), "k1")) {
            it instanceof OptimisticLockException
            message == "Document k1 was modified concurrently: stale"
            cause.is(conflict)
        }
        with(ElasticsearchAsyncSearchTransport.conflictOrFailure(new CompletionException(io), null)) {
            it instanceof DataAccessException
            message == "Elasticsearch request failed: lost"
            cause.is(io)
        }
        ElasticsearchAsyncSearchTransport.conflictOrFailure(new CompletionException(runtime), null).is(runtime)
        ElasticsearchAsyncSearchTransport.conflictOrFailure(runtime, null).is(runtime)

        and: "a CompletionException without a cause is the failure itself"
        def bare = new CompletionException("no cause", null)
        ElasticsearchAsyncSearchTransport.conflictOrFailure(bare, null).is(bare)
    }

    void "the refresh policy and the sort values map to the client types"() {
        expect:
        ElasticsearchAsyncSearchTransport.refresh(NONE) == Refresh.False
        ElasticsearchAsyncSearchTransport.refresh(WAIT_FOR) == Refresh.WaitFor
        ElasticsearchAsyncSearchTransport.refresh(TRUE) == Refresh.True
        ElasticsearchAsyncSearchTransport.sortValues(null) == []
        ElasticsearchAsyncSearchTransport.sortValues([]) == []
        ElasticsearchAsyncSearchTransport.sortValues([FieldValue.of(3L), FieldValue.of("x"), FieldValue.of(1.5d), FieldValue.of(true)]) == [3L, "x", 1.5d, true]
    }

    void "the fake transport exposes its mapper and closes"() {
        given:
        def transport = new FakeElasticsearchTransport()

        when:
        transport.close()

        then:
        transport.closed
        transport.jsonpMapper().is(transport.mapper)
        transport.options() != null
        new ElasticsearchClient(transport)._jsonpMapper().is(transport.mapper)
    }

    // --- canned responses -----------------------------------------------------------------------

    private static String writeResponse(String id, String result, long version) {
        return """{"_index":"$INDEX","_id":"$id","_version":$version,"result":"$result",$SHARDS,"_seq_no":${version - 1},"_primary_term":1}"""
    }

    private static String bulkItem(String id, int status, String result) {
        return """{"index":{"_index":"$INDEX","_id":"$id","_version":1,"result":"$result","status":$status,$SHARDS,"_seq_no":0,"_primary_term":1}}"""
    }

    private static String bulkError(String id, int status, String type, String reason) {
        return """{"index":{"_index":"$INDEX","_id":"$id","status":$status,"error":{"type":"$type","reason":"$reason"}}}"""
    }

    /**
     * Runs the async transport behind the blocking SPI, rethrowing the failure of a stage as is.
     */
    static class BlockingOverAsync implements SearchTransport {

        private final AsyncSearchTransport delegate

        BlockingOverAsync(AsyncSearchTransport delegate) {
            this.delegate = delegate
        }

        private static <T> T await(CompletionStage<T> stage) {
            try {
                return stage.toCompletableFuture().get()
            } catch (ExecutionException e) {
                throw e.cause
            }
        }

        @Override
        SearchHits search(String index, String body) {
            return await(delegate.search(index, body))
        }

        @Override
        long count(String index, String body) {
            return await(delegate.count(index, body))
        }

        @Override
        String index(String index, @Nullable String id, Map<String, Object> source, DocumentSearchConfiguration.Refresh refresh) {
            return await(delegate.index(index, id, source, refresh))
        }

        @Override
        String index(String index, @Nullable String id, Map<String, Object> source, @Nullable Long version, DocumentSearchConfiguration.Refresh refresh) {
            return await(delegate.index(index, id, source, version, refresh))
        }

        @Override
        List<String> bulkIndex(String index, List<BulkDocument> documents, DocumentSearchConfiguration.Refresh refresh) {
            return await(delegate.bulkIndex(index, documents, refresh))
        }

        @Override
        Map<String, Object> get(String index, String id) {
            return await(delegate.get(index, id)).orElse(null)
        }

        @Override
        SearchHit getHit(String index, String id) {
            return await(delegate.getHit(index, id)).orElse(null)
        }

        @Override
        boolean delete(String index, String id, DocumentSearchConfiguration.Refresh refresh) {
            return await(delegate.delete(index, id, refresh))
        }

        @Override
        long deleteByQuery(String index, String body, DocumentSearchConfiguration.Refresh refresh) {
            return await(delegate.deleteByQuery(index, body, refresh))
        }

        @Override
        boolean indexExists(String index) {
            throw new UnsupportedOperationException("The async transport has no index management")
        }

        @Override
        void createIndex(String index, String body) {
            throw new UnsupportedOperationException("The async transport has no index management")
        }
    }
}
