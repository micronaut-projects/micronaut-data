package io.micronaut.data.jdbc.h2

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource

@MicronautTest(transactional = false)
@H2DBProperties
@Property(name = "datasources.default.enabled", value = "false")
class H2DisabledDataSourceSpec extends Specification {

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
}
