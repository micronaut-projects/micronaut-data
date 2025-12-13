package io.micronaut.data.jdbc.oraclexe.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.repository.PageableRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource

class OracleJdbcFloatVectorEntitySpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    VectorFloatDocRepository vectorRepository = context.getBean(VectorFloatDocRepository)

    @Shared
    DataSource dataSource = context.getBean(DataSource)

    @Override
    List<String> packages() {
        // Ensure entity/repository in this package are scanned
        return [getClass().package.name]
    }

    void "test save, find and update single entity (using custom queries with io.micronaut.data.model.Vector)"() {
        given:
        float[] dv = [1f, 2.5f, -3.75f] as float[]
        FloatVector v1 = Vector.of(dv)

        when: "save via custom @Query using Vector parameter"
        vectorRepository.saveCustom(v1)
        def list = vectorRepository.findAll(io.micronaut.data.model.Sort.of(io.micronaut.data.model.Sort.Order.asc("id")))
        def e = list.find { it.embedding.toFloatArray().toList() == dv.toList() }

        then: "entity persisted and read conversion to Micronaut Vector works"
        e != null
        e.id != null
        e.embedding != null
        e.embedding.type == Float.TYPE
        e.embedding.toFloatArray().toList() == dv.toList()

        when: "update via custom @Query to a new vector"
        float [] dv2 = [3f, 0.0f, 7.25f] as float[]
        FloatVector v2 = Vector.of(dv2)
        vectorRepository.updateCustom(e.id, v2)
        def updated = vectorRepository.findById(e.id).orElse(null)

        then:
        updated != null
        updated.embedding.type == Float.TYPE
        updated.embedding.toFloatArray().toList() == dv2.toList()
    }


    void "test save, find and update multiple entities"() {
        given:
        FloatVector vA = Vector.of([1f, 2f, 3f] as float[])
        FloatVector vB = Vector.of([4f, 5f, 6f] as float[])

        when:
        vectorRepository.saveCustom(vA)
        vectorRepository.saveCustom(vB)
        def rows = vectorRepository.findAll(io.micronaut.data.model.Sort.of(io.micronaut.data.model.Sort.Order.asc("id")))

        then:
        def idA = rows.find { it.embedding.toFloatArray().toList() == [1f, 2f, 3f] }?.id
        def idB = rows.find { it.embedding.toFloatArray().toList() == [4f, 5f, 6f] }?.id
        idA != null
        idB != null
        idA != idB

        when: "update both"
        FloatVector vA2 = Vector.of([7f, 8f, 9f] as float[])
        FloatVector vB2 = Vector.of([0f, -1f, -2f] as float[])
        vectorRepository.updateCustom(idA, vA2)
        vectorRepository.updateCustom(idB, vB2)
        def rows2 = vectorRepository.findAll(io.micronaut.data.model.Sort.of(io.micronaut.data.model.Sort.Order.asc("id")))

        then:
        def updatedA = rows2.find { it.id == idA }?.embedding?.toFloatArray()?.toList()
        def updatedB = rows2.find { it.id == idB }?.embedding?.toFloatArray()?.toList()
        updatedA == [7f, 8f, 9f]
        updatedB == [0f, -1f, -2f]
    }

    void "test custom and async queries"() {
        given:
        FloatVector vec = Vector.of([10f, 11f, 12f] as float[])

        when:
        java.util.concurrent.Future<Integer> saveFut = vectorRepository.saveAsync(vec)

        then:
        saveFut.get(10, java.util.concurrent.TimeUnit.SECONDS) == 1

        when:
        def all = vectorRepository.findAll(io.micronaut.data.model.Sort.of(io.micronaut.data.model.Sort.Order.asc("id")))
        def last = all.last()
        java.util.concurrent.Future<java.util.List<VectorFloatDoc>> findFut = vectorRepository.findAsync(last.id)

        then:
        def found = findFut.get(10, java.util.concurrent.TimeUnit.SECONDS)
        found != null
        found.size() == 1
        found.get(0).embedding.toFloatArray().toList() == [10f, 11f, 12f]

        when:
        FloatVector vec2 = Vector.of([13f, 14f, 15f] as float[])
        java.util.concurrent.Future<Integer> updFut = vectorRepository.updateAsync(last.id, vec2)

        then:
        updFut.get(10, java.util.concurrent.TimeUnit.SECONDS) != null
        with(vectorRepository.findById(last.id)) {
            it.isPresent()
            it.get().embedding.toFloatArray().toList() == [13f, 14f, 15f]
        }
    }

    void "test paging over VectorFloatDoc"() {
        given:
        FloatVector v1 = Vector.of([1f, 2f, 3f] as float[])
        FloatVector v2 = Vector.of([4f, 5f, 6f] as float[])
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

    private void executeSilently(String sql) {
        java.sql.Connection c = null
        java.sql.Statement st = null
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

@MappedEntity("vector_float_doc")
class VectorFloatDoc {
    @Id
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE, ref = "VECTOR_DOC_SEQ")
    Long id
    FloatVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    FloatVector getEmbedding() { return embedding }
    void setEmbedding(FloatVector embedding) { this.embedding = embedding }
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface VectorFloatDocRepository extends PageableRepository<VectorFloatDoc, Long> {

    @Query("INSERT INTO vector_float_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_float_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") FloatVector vec)

    @Query("SELECT * FROM vector_float_doc WHERE id = :id")
    Optional<VectorFloatDoc> findById(Long id)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") FloatVector vec)

    @Query("INSERT INTO vector_float_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    java.util.concurrent.Future<Integer> saveAsync(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_float_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    java.util.concurrent.Future<Integer> saveAsync(@Parameter("vec") FloatVector vec)

    @Query("SELECT * FROM vector_float_doc WHERE id = :id")
    java.util.concurrent.Future<java.util.List<VectorFloatDoc>> findAsync(Long id)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    java.util.concurrent.Future<Integer> updateAsync(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    java.util.concurrent.Future<Integer> updateAsync(Long id, @Parameter("vec") FloatVector vec)

}
