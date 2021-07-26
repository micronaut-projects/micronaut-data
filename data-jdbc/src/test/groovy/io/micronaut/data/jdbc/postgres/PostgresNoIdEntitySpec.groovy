package io.micronaut.data.jdbc.postgres

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.transaction.SynchronousTransactionManager
import io.micronaut.transaction.TransactionCallback
import io.micronaut.transaction.TransactionStatus
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject

class PostgresNoIdEntitySpec extends Specification implements PostgresTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    NoIdEntityRepository noIdEntityRepository = applicationContext.getBean(NoIdEntityRepository)

    @Shared
    @Inject
    SynchronousTransactionManager transactionManager = applicationContext.getBean(SynchronousTransactionManager)

    void setup() {
        transactionManager.executeWrite(new TransactionCallback() {
            @Override
            Object call(TransactionStatus status) throws Exception {
                status.connection.prepareStatement('''
drop sequence if exists "no_id_names_seq";
drop table if exists "no_id_names";
create sequence "no_id_names_seq" increment by 1;
create table "no_id_names"
(
  "id"                 bigint default nextval('no_id_names_seq'::regclass)    not null,
  "first_name"         varchar(255)                                    ,
  "last_name"          varchar(255)                                    
);
''').withCloseable { it.executeUpdate() }
                return null
            }

        })
    }

    void 'test no-id entity operations'() {
        when:
            noIdEntityRepository.save(new NoIdEntity(firstName: "Xyz", lastName: "Abc"))
            noIdEntityRepository.save(new NoIdEntity(firstName: "Qwe", lastName: "Jkl"))
            def all = noIdEntityRepository.listAll()
            def allPageable = noIdEntityRepository.listAll(Pageable.from(1, 1).order("firstName", Sort.Order.Direction.DESC))
        then:
            all.size() == 2
            all.find { it.firstName == "Xyz" }
            all.find { it.firstName == "Qwe" }
            allPageable.size() == 1
            allPageable.find { it.firstName == "Qwe" }
    }

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.NONE
    }
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface NoIdEntityRepository {

    NoIdEntity save(NoIdEntity entity)

    List<NoIdEntity> listAll()

    List<NoIdEntity> listAll(Pageable pageable)

}

@MappedEntity("no_id_names")
class NoIdEntity {

    String firstName
    String lastName
}
