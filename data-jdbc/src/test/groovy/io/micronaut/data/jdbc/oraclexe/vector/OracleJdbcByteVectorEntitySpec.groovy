package io.micronaut.data.jdbc.oraclexe.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import io.micronaut.data.model.Sort
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.ByteVector
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.repository.PageableRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.Statement
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class OracleJdbcByteVectorEntitySpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    VectorByteDocRepository vectorRepository = context.getBean(VectorByteDocRepository)

    @Shared
    DataSource dataSource = context.getBean(DataSource)

    @Override
    List<String> packages() {
        // Ensure entity/repository in this package are scanned
        return [getClass().package.name]
    }

    void "test save, find and update single entity (using custom queries with io.micronaut.data.model.Vector)"() {
        given:
        byte[] dv = [1, 2, -3] as byte[]
        ByteVector v1 = Vector.of(dv)

        when: "save via custom @Query using Vector parameter"
        vectorRepository.saveCustom(v1)
        def list = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))
        def e = list.find { it.embedding.toByteArray().toList() == dv.toList() }

        then: "entity persisted and read conversion to Micronaut Vector works"
        e != null
        e.id != null
        e.embedding != null
        e.embedding.type == Byte.TYPE
        e.embedding.toByteArray().toList() == dv.toList()

        when: "update via custom @Query to a new vector"
        byte [] dv2 = [3, 0, 7] as byte[]
        ByteVector v2 = Vector.of(dv2)
        vectorRepository.updateCustom(e.id, v2)
        def updated = vectorRepository.findById(e.id).orElse(null)

        then:
        updated != null
        updated.embedding.type == Byte.TYPE
        updated.embedding.toByteArray().toList() == dv2.toList()
    }


    void "test save, find and update multiple entities"() {
        given:
        ByteVector vA = Vector.of([1, 2, 3] as byte[])
        ByteVector vB = Vector.of([4, 5, 6] as byte[])

        when:
        vectorRepository.saveCustom(vA)
        vectorRepository.saveCustom(vB)
        def rows = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))

        then:
        def idA = rows.find { it.embedding.toByteArray().toList() == [1, 2, 3] }?.id
        def idB = rows.find { it.embedding.toByteArray().toList() == [4, 5, 6] }?.id
        idA != null
        idB != null
        idA != idB

        when: "update both"
        ByteVector vA2 = Vector.of([7, 8, 9] as byte[])
        ByteVector vB2 = Vector.of([0, -1, -2] as byte[])
        vectorRepository.updateCustom(idA, vA2)
        vectorRepository.updateCustom(idB, vB2)
        def rows2 = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))

        then:
        def updatedA = rows2.find { it.id == idA }?.embedding?.toByteArray()?.toList()
        def updatedB = rows2.find { it.id == idB }?.embedding?.toByteArray()?.toList()
        updatedA == [7, 8, 9]
        updatedB == [0, -1, -2]
    }

    void "test custom and async queries"() {
        given:
        ByteVector vec = Vector.of([10, 11, 12] as byte[])

        when:
        Future<Integer> saveFut = vectorRepository.saveAsync(vec)

        then:
        saveFut.get(10, TimeUnit.SECONDS) == 1

        when:
        def all = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))
        def last = all.last()
        Future<List<VectorByteDoc>> findFut = vectorRepository.findAsync(last.id)

        then:
        def found = findFut.get(10, TimeUnit.SECONDS)
        found != null
        found.size() == 1
        found.get(0).embedding.toByteArray().toList() == [10, 11, 12]

        when:
        ByteVector vec2 = Vector.of([13, 14, 15] as byte[])
        Future<Integer> updFut = vectorRepository.updateAsync(last.id, vec2)

        then:
        updFut.get(10, TimeUnit.SECONDS) != null
        with(vectorRepository.findById(last.id)) {
            it.isPresent()
            it.get().embedding.toByteArray().toList() == [13, 14, 15]
        }
    }

    void "test paging over VectorByteDoc"() {
        given:
        ByteVector v1 = Vector.of([1, 2, 3] as byte[])
        ByteVector v2 = Vector.of([4, 5, 6] as byte[])
        vectorRepository.saveCustom(v1)
        vectorRepository.saveCustom(v2)

        when:
        def p0 = vectorRepository.findAll(io.micronaut.data.model.Pageable.from(0, 1))
        def p1 = vectorRepository.findAll(io.micronaut.data.model.Pageable.from(1, 1))

        then:
        p0 != null
        p1 != null
        p0.getContent().size() == 1
        p1.getContent().size() == 1
        p0.getTotalSize() >= 2
    }

    void "test derived vector near and within search results"() {
        given:
        vectorRepository.deleteAll()
        vectorRepository.save(new VectorByteDoc(embedding: Vector.of([1, 0, 0] as byte[])))
        vectorRepository.save(new VectorByteDoc(embedding: Vector.of([0, 1, 0] as byte[])))

        when:
        SearchResults<VectorByteDoc> nearResults = vectorRepository.searchByEmbeddingNear(Vector.of([1, 0, 0] as byte[]), 2d)

        then:
        nearResults != null
        nearResults.results().size() >= 1
        nearResults.results().get(0).score().value() <= 2d

        when:
        SearchResults<VectorByteDoc> withinResults = vectorRepository.searchByEmbeddingWithin(Vector.of([1, 0, 0] as byte[]), 0d, 0.2d)

        then:
        withinResults != null
        withinResults.results() != null

        when:
        SearchResults<VectorByteDoc> betweenResults = vectorRepository.searchByEmbeddingBetween(Vector.of([1, 0, 0] as byte[]), 0d, 0.2d)

        then:
        betweenResults != null
        betweenResults.results() != null
        betweenResults.results().every { it.score().value() >= 0d && it.score().value() <= 0.2d }
    }

    private void executeSilently(String sql) {
        Connection c = null
        Statement st = null
        try {
            c = dataSource.getConnection()
            st = c.createStatement()
            st.execute(sql)
        } catch (Throwable ignored) {
            // ignore if already exists or unsupported in current XE version
        } finally {
            try { st?.close() } catch (ignored) {}
            try { c?.close() } catch (ignored) {}
        }
    }
}

@MappedEntity("vector_byte_doc")
class VectorByteDoc {
    @Id
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE, ref = "VECTOR_DOC_SEQ")
    Long id
    ByteVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    ByteVector getEmbedding() { return embedding }
    void setEmbedding(ByteVector embedding) { this.embedding = embedding }
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface VectorByteDocRepository extends PageableRepository<VectorByteDoc, Long> {

    @Query("INSERT INTO vector_byte_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_byte_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") ByteVector vec)

    @Query("SELECT * FROM vector_byte_doc WHERE id = :id")
    Optional<VectorByteDoc> findById(Long id)

    @Query("UPDATE vector_byte_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_byte_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") ByteVector vec)

    @Query("INSERT INTO vector_byte_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    Future<Integer> saveAsync(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_byte_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    Future<Integer> saveAsync(@Parameter("vec") ByteVector vec)

    @Query("SELECT * FROM vector_byte_doc WHERE id = :id")
    Future<List<VectorByteDoc>> findAsync(Long id)

    SearchResults<VectorByteDoc> searchByEmbeddingNear(Vector vector, Double maxDistance)

    SearchResults<VectorByteDoc> searchByEmbeddingWithin(Vector vector, Double minDistance, Double maxDistance)

    SearchResults<VectorByteDoc> searchByEmbeddingBetween(Vector vector, Double minDistance, Double maxDistance)

    @Query("UPDATE vector_byte_doc SET embedding = :vec WHERE id = :id")
    Future<Integer> updateAsync(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_byte_doc SET embedding = :vec WHERE id = :id")
    Future<Integer> updateAsync(Long id, @Parameter("vec") ByteVector vec)

}
