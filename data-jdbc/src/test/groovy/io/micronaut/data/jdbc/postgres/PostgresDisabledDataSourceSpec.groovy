package io.micronaut.data.jdbc.postgres

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource

@MicronautTest(transactional = false)
class PostgresDisabledDataSourceSpec extends Specification implements PostgresTestPropertyProvider {

    @Inject
    @Shared
    ApplicationContext applicationContext


    void 'test disabled data source'() {
        when:
        applicationContext.getBean(DataSource)
        then:
        def exception = thrown(NoSuchBeanException)
        exception.message.contains('is disabled since bean property [enabled] value is not equal to [true]')
    }

    @Override
    boolean dataSourceEnabled(String dataSourceName) {
        if (dataSourceName == 'default') {
            return false
        }
        return PostgresTestPropertyProvider.class.dataSourceEnabled(dataSourceName)
    }
}
