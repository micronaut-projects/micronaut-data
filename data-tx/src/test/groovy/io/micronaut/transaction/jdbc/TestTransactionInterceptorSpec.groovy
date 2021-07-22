package io.micronaut.transaction.jdbc

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification
import spock.lang.Stepwise

import jakarta.inject.Inject
import java.sql.Connection

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
@Stepwise
class TestTransactionInterceptorSpec extends Specification {

    @Inject
    Connection connection

    void setup() {
        connection.prepareStatement("drop table book if exists").execute()
        connection.prepareStatement("create table book (id bigint not null auto_increment, pages integer not null, title varchar(255), primary key (id))").execute()
    }

    void "test transaction connection"() {

        when:
        def ps1 = connection.prepareStatement("insert into book (pages, title) values(100, 'The Stand')")
        ps1.execute()
        def ps2 = connection.prepareStatement("select * from book")
        def rs = ps2.executeQuery()
        rs.next()

        then:
        rs.getInt("pages") == 100

        cleanup:
        ps2.close()
        ps1.close()
        rs.close()
    }

    void "test changes rolled back"() {
        when:
        def ps2 = connection.prepareStatement("select * from book")
        def rs = ps2.executeQuery()


        then:
        !rs.next()
    }
}
