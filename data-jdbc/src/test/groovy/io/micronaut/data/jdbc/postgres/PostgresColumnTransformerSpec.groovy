package io.micronaut.data.jdbc.postgres

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.sql.ColumnTransformer
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.TestResourcesDatabaseTestPropertyProvider
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.GenericRepository
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.time.Duration

import static io.micronaut.data.annotation.Relation.Kind.MANY_TO_ONE

/**
 * Test for https://github.com/micronaut-projects/micronaut-data/issues/3338 .
 */
class PostgresColumnTransformerSpec extends Specification implements PostgresTestPropertyProvider {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run(properties)

    void setup() {
        def dataSource = DelegatingDataSource.unwrapDataSource(applicationContext.getBean(DataSource.class, Qualifiers.byName("default")))
        def connection = dataSource.connection
        connection.prepareStatement("CREATE CAST (varchar AS interval) WITH INOUT AS IMPLICIT;").execute()
    }

    void cleanup() {
        def dataSource = DelegatingDataSource.unwrapDataSource(applicationContext.getBean(DataSource.class, Qualifiers.byName("default")))
        def connection = dataSource.connection
        connection.prepareStatement("DROP CAST IF EXISTS (varchar AS interval);").execute()
    }

    void 'load entity with transformer column'() {
        when:
        def riderRepository = applicationContext.getBean(RiderRepository)
        def savedRider = riderRepository.save(new Rider(name: 'Peter', finishTime: Duration.ofHours(3).plusMinutes(45)))
        def optRider = riderRepository.findById(savedRider.id)
        then:
        optRider.present
        optRider.get().finishTime == savedRider.finishTime
        cleanup:
        riderRepository.deleteAll()
    }

    void 'load joined entity with transformer column'() {
        when:
        def riderRepository = applicationContext.getBean(RiderRepository)
        def reportRepository = applicationContext.getBean(ReportRepository)
        def savedRider = riderRepository.save(new Rider(name: 'Peter', finishTime: Duration.ofHours(3).plusMinutes(45)))
        def savedReport = reportRepository.save(new Report(rider: savedRider, message: 'Hello, World'))
        def optReport = reportRepository.findById(savedReport.getId())
        then:
        optReport.present
        savedRider.finishTime == optReport.get().rider.finishTime
        cleanup:
        reportRepository.deleteAll()
        riderRepository.deleteAll()
    }

    @Override
    List<String> packages() {
        List.of("io.micronaut.data.jdbc.postgres")
    }
}

@MappedEntity
class Rider {
    @Id
    @GeneratedValue
    Integer id

    String name

    @ColumnTransformer(read = "to_char(@.finish_time, 'PTHH24HMIM')")
    Duration finishTime
}

@MappedEntity
class Report {

    @Id
    @GeneratedValue
    Integer id

    @Relation(value = MANY_TO_ONE)
    @MappedProperty(value = "rider")
    Rider rider

    private String message
}

@JdbcRepository(dialect = Dialect.POSTGRES)
@Join(value = "rider")
interface ReportRepository extends GenericRepository<Report, Integer> {

    Optional<Report> findById(Integer id)

    Report save(Report entity)

    void deleteAll()
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface RiderRepository extends GenericRepository<Rider, Integer> {

    Optional<Rider> findById(Integer id)

    Rider save(Rider entity)

    void deleteAll()
}
