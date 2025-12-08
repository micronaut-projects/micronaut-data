package io.micronaut.data.r2dbc.oraclexe.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.Vector
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.r2dbc.oraclexe.OracleXETestPropertyProvider
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * R2DBC specs for Oracle VECTOR support covering FloatVector, IntVector and ByteVector.
 * Mirrors the JDBC vector entity specs.
 */
class OracleR2dbcVectorEntitySpec extends Specification implements OracleXETestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties + ["spec.name": "OracleR2dbcVectorEntitySpec"])

    @Shared
    ConnectionFactory connectionFactory = context.getBean(ConnectionFactory)

    @Shared
    VectorFloatDocRepository vectorFloatDocRepository = context.getBean(VectorFloatDocRepository)

    @Shared
    VectorIntDocRepository vectorIntDocRepository = context.getBean(VectorIntDocRepository)

    @Shared
    VectorByteDocRepository vectorByteDocRepository = context.getBean(VectorByteDocRepository)

    @Shared
    VectorDoubleDocRepository vectorDoubleDocRepository = context.getBean(VectorDoubleDocRepository)

    def setupSpec() {
        // Create sequence and tables if not exists (ignore errors if they already exist)
        executeSilently "CREATE SEQUENCE VECTOR_DOC_SEQ"
        // default FLOAT64 (double[])
        executeSilently "CREATE TABLE vector_double_doc (id NUMBER PRIMARY KEY, embedding VECTOR(3))"
        // FLOAT32
        executeSilently "CREATE TABLE vector_float_doc (id NUMBER PRIMARY KEY, embedding VECTOR(3, FLOAT32))"
        // INT8 for IntVector
        executeSilently "CREATE TABLE vector_int_doc (id NUMBER PRIMARY KEY, embedding VECTOR(3, INT8))"
        // INT8 also used for ByteVector (Oracle maps INT8 vectors to byte[])
        executeSilently "CREATE TABLE vector_byte_doc (id NUMBER PRIMARY KEY, embedding VECTOR(3, INT8))"
    }

    def cleanup() {
        // Clean tables between tests
        executeSilently "DELETE FROM vector_double_doc"
        executeSilently "DELETE FROM vector_float_doc"
        executeSilently "DELETE FROM vector_int_doc"
        executeSilently "DELETE FROM vector_byte_doc"
    }

    // FLOAT64/default
    void "R2DBC DoubleVector - default CRUD and custom @Query"() {
        given:
        def repo = vectorDoubleDocRepository
        double[] dv = [1d, 2.5d, -3.75d] as double[]
        Vector.DoubleVector v1 = Vector.of(dv)

        when:
        def saved = repo.save(new VectorDoubleDoc(embedding: v1))

        then:
        saved?.id != null

        when:
        def fetched = repo.findById(saved.id).orElse(null)

        then:
        fetched != null
        fetched.embedding.type == Double.TYPE
        fetched.embedding.toDoubleArray().toList() == dv.toList()

        when:
        double[] dv2 = [3d, 0.0d, 7.25d] as double[]
        Vector.DoubleVector v2 = Vector.of(dv2)
        fetched.embedding = v2
        def updated = repo.update(fetched)

        then:
        updated != null
        updated.embedding.type == Double.TYPE
        updated.embedding.toDoubleArray().toList() == dv2.toList()

        when: "custom @Query insert and update"
        double[] dvx = [2d, 4d, 6d] as double[]
        Vector.DoubleVector vx = Vector.of(dvx)
        repo.saveCustom(vx)
        def all = repo.findAll()
        def e = all.find { it.embedding?.toDoubleArray()?.toList() == dvx.toList() }

        then:
        e != null
        e.id != null

        when:
        double[] dvy = [-1d, 0.5d, 10d] as double[]
        Vector.DoubleVector vy = Vector.of(dvy)
        repo.updateCustom(e.id, vy)
        def after = repo.findById(e.id).orElse(null)

        then:
        after != null
        after.embedding.type == Double.TYPE
        after.embedding.toDoubleArray().toList() == dvy.toList()
    }

    // FLOAT32
    void "R2DBC FloatVector - default CRUD and custom @Query"() {
        given:
        def repo = vectorFloatDocRepository
        Vector.FloatVector v1 = Vector.of([1f, 2.5f, -3.75f] as float[])

        when:
        def saved = repo.save(new VectorFloatDoc(embedding: v1))

        then:
        saved?.id != null

        when:
        def fetched = repo.findById(saved.id).orElse(null)

        then:
        fetched != null
        fetched.embedding.type == Float.TYPE
        fetched.embedding.toFloatArray().toList() == [1f, 2.5f, -3.75f]

        when:
        Vector.FloatVector v2 = Vector.of([3f, 0f, 7.25f] as float[])
        fetched.embedding = v2
        def updated = repo.update(fetched)

        then:
        updated != null
        updated.embedding.type == Float.TYPE
        updated.embedding.toFloatArray().toList() == [3f, 0f, 7.25f]

        when: "custom @Query insert and update"
        Vector.FloatVector vx = Vector.of([10f, 11f, 12f] as float[])
        repo.saveCustom(vx)
        def all = repo.findAll()
        def e = all.find { it.embedding?.toFloatArray()?.toList() == [10f, 11f, 12f] }

        then:
        e != null

        when:
        Vector.FloatVector vy = Vector.of([13f, 14f, 15f] as float[])
        repo.updateCustom(e.id, vy)
        def after = repo.findById(e.id).orElse(null)

        then:
        after != null
        after.embedding.toFloatArray().toList() == [13f, 14f, 15f]
    }

    // INT8 -> IntVector (maps to int[])
    void "R2DBC IntVector - default CRUD and custom @Query"() {
        given:
        def repo = vectorIntDocRepository
        Vector.IntVector v1 = Vector.of([1, -2, 127] as int[])

        when:
        def saved = repo.save(new VectorIntDoc(embedding: v1))

        then:
        saved?.id != null

        when:
        def fetched = repo.findById(saved.id).orElse(null)

        then:
        fetched != null
        fetched.embedding.type == Integer.TYPE
        fetched.embedding.toIntegerArray().toList() == [1, -2, 127]

        when:
        Vector.IntVector v2 = Vector.of([0, 5, -7] as int[])
        fetched.embedding = v2
        def updated = repo.update(fetched)

        then:
        updated != null
        updated.embedding.type == Integer.TYPE
        updated.embedding.toIntegerArray().toList() == [0, 5, -7]

        when: "custom @Query insert and update"
        Vector.IntVector vx = Vector.of([10, 11, 12] as int[])
        repo.saveCustom(vx)
        def all = repo.findAll()
        def e = all.find { it.embedding?.toIntegerArray()?.toList() == [10, 11, 12] }

        then:
        e != null

        when:
        Vector.IntVector vy = Vector.of([13, 14, 15] as int[])
        repo.updateCustom(e.id, vy)
        def after = repo.findById(e.id).orElse(null)

        then:
        after != null
        after.embedding.toIntegerArray().toList() == [13, 14, 15]
    }

    // INT8 -> ByteVector (maps to byte[])
    void "R2DBC ByteVector - default CRUD and custom @Query"() {
        given:
        def repo = vectorByteDocRepository
        Vector.ByteVector v1 = Vector.of([1, 2, -3] as byte[])

        when:
        def saved = repo.save(new VectorByteDoc(embedding: v1))

        then:
        saved?.id != null

        when:
        def fetched = repo.findById(saved.id).orElse(null)

        then:
        fetched != null
        fetched.embedding.type == Byte.TYPE
        fetched.embedding.toByteArray().toList() == [1, 2, -3]

        when:
        Vector.ByteVector v2 = Vector.of([3, 0, 7] as byte[])
        fetched.embedding = v2
        def updated = repo.update(fetched)

        then:
        updated != null
        updated.embedding.type == Byte.TYPE
        updated.embedding.toByteArray().toList() == [3, 0, 7]

        when: "custom @Query insert and update"
        Vector.ByteVector vx = Vector.of([10, 11, 12] as byte[])
        repo.saveCustom(vx)
        def all = repo.findAll()
        def e = all.find { it.embedding?.toByteArray()?.toList() == [10, 11, 12] }

        then:
        e != null

        when:
        Vector.ByteVector vy = Vector.of([13, 14, 15] as byte[])
        repo.updateCustom(e.id, vy)
        def after = repo.findById(e.id).orElse(null)

        then:
        after != null
        after.embedding.toByteArray().toList() == [13, 14, 15]
    }

    private void executeSilently(String sql) {
        try {
            Mono.from(connectionFactory.create())
                .flatMapMany { Connection c ->
                    Flux.from(c.createStatement(sql).execute())
                        .flatMap { r -> r.getRowsUpdated() }
                        .onErrorResume { t -> Mono.empty() }
                        .concatWith(Mono.from(c.close()))
                }
                .collectList()
                .block()
        } catch (Throwable ignored) {
            // ignore if statement is unsupported or already exists
        }
    }
}


@MappedEntity("vector_double_doc")
class VectorDoubleDoc {
    @Id
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE, ref = "VECTOR_DOC_SEQ")
    Long id
    Vector.DoubleVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    Vector.DoubleVector getEmbedding() { return embedding }
    void setEmbedding(Vector.DoubleVector embedding) { this.embedding = embedding }
}

@MappedEntity("vector_float_doc")
class VectorFloatDoc {
    @Id
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE, ref = "VECTOR_DOC_SEQ")
    Long id
    Vector.FloatVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    Vector.FloatVector getEmbedding() { return embedding }
    void setEmbedding(Vector.FloatVector embedding) { this.embedding = embedding }
}

@MappedEntity("vector_int_doc")
class VectorIntDoc {
    @Id
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE, ref = "VECTOR_DOC_SEQ")
    Long id
    Vector.IntVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    Vector.IntVector getEmbedding() { return embedding }
    void setEmbedding(Vector.IntVector embedding) { this.embedding = embedding }
}

@MappedEntity("vector_byte_doc")
class VectorByteDoc {
    @Id
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE, ref = "VECTOR_DOC_SEQ")
    Long id
    Vector.ByteVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    Vector.ByteVector getEmbedding() { return embedding }
    void setEmbedding(Vector.ByteVector embedding) { this.embedding = embedding }
}

// Repositories (R2DBC, Oracle)

@Requires(property = "spec.name", value = "OracleR2dbcVectorEntitySpec")
@R2dbcRepository(dialect = Dialect.ORACLE)
interface VectorFloatDocRepository extends CrudRepository<VectorFloatDoc, Long> {

    @Query("INSERT INTO vector_float_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_float_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") Vector.FloatVector vec)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector.FloatVector vec)

    @Query("SELECT * FROM vector_float_doc")
    List<VectorFloatDoc> findAll()
}

@Requires(property = "spec.name", value = "OracleR2dbcVectorEntitySpec")
@R2dbcRepository(dialect = Dialect.ORACLE)
interface VectorIntDocRepository extends CrudRepository<VectorIntDoc, Long> {

    @Query("INSERT INTO vector_int_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_int_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") Vector.IntVector vec)

    @Query("UPDATE vector_int_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_int_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector.IntVector vec)

    @Query("SELECT * FROM vector_int_doc")
    List<VectorIntDoc> findAll()
}

@Requires(property = "spec.name", value = "OracleR2dbcVectorEntitySpec")
@R2dbcRepository(dialect = Dialect.ORACLE)
interface VectorByteDocRepository extends CrudRepository<VectorByteDoc, Long> {

    @Query("INSERT INTO vector_byte_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_byte_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") Vector.ByteVector vec)

    @Query("UPDATE vector_byte_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_byte_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector.ByteVector vec)

    @Query("SELECT * FROM vector_byte_doc")
    List<VectorByteDoc> findAll()
}

@Requires(property = "spec.name", value = "OracleR2dbcVectorEntitySpec")
@R2dbcRepository(dialect = Dialect.ORACLE)
interface VectorDoubleDocRepository extends CrudRepository<VectorDoubleDoc, Long> {

    @Query("INSERT INTO vector_double_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_double_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") Vector.DoubleVector vec)

    @Query("UPDATE vector_double_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_double_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector.DoubleVector vec)

    @Query("SELECT * FROM vector_double_doc")
    List<VectorDoubleDoc> findAll()
}
