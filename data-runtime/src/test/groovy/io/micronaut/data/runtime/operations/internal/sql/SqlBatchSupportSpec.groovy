package io.micronaut.data.runtime.operations.internal.sql

import io.micronaut.core.type.Argument
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.runtime.InsertBatchOperation
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.runtime.RuntimePersistentProperty
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CompletionStage

class SqlBatchSupportSpec extends Specification {

    void "mysql dialect stays conservative for generated identities by default"() {
        expect:
        !SqlBatchSupport.isSupportsBatchInsert(entityWithGeneratedId(), Dialect.MYSQL)
    }

    void "mariadb can batch generated-id inserts when generated keys are not required"() {
        expect:
        SqlBatchSupport.isSupportsBatchInsert(entityWithGeneratedId(), Dialect.MYSQL, "MariaDB", false)
    }

    void "mariadb generated-id inserts stay conservative when generated keys are required"() {
        expect:
        !SqlBatchSupport.isSupportsBatchInsert(entityWithGeneratedId(), Dialect.MYSQL, "MariaDB", true)
    }

    @Unroll
    void "generated keys are required for #scenario"() {
        expect:
        SqlBatchSupport.requiresBatchGeneratedKeys(entity(cascadesPersist, postPersist), operation(resultArgument)) == required

        where:
        scenario                         | cascadesPersist | postPersist | resultArgument                                            || required
        "entity lists"                   | false           | false       | Argument.listOf(String)                                   || true
        "completion stage entity lists"  | false           | false       | Argument.of(CompletionStage, Argument.listOf(String))     || true
        "void returns"                   | false           | false       | Argument.of(Void)                                          || false
        "numeric returns"                | false           | false       | Argument.of(Long)                                         || false
        "post persist listeners"         | false           | true        | Argument.of(Long)                                         || true
        "cascade persist associations"   | true            | false       | Argument.of(Void)                                          || true
    }

    private InsertBatchOperation<?> operation(Argument<?> resultArgument) {
        Stub(InsertBatchOperation) {
            getResultArgument() >> resultArgument
        }
    }

    private RuntimePersistentEntity<?> entityWithGeneratedId() {
        entity(false, false)
    }

    private RuntimePersistentEntity<?> entity(boolean cascadesPersistAssociations, boolean postPersist) {
        Stub(RuntimePersistentEntity) {
            hasIdentity() >> true
            getIdentity() >> Stub(RuntimePersistentProperty) {
                isGenerated() >> true
            }
            cascadesPersist() >> cascadesPersistAssociations
            hasPostPersistEventListeners() >> postPersist
        }
    }
}
