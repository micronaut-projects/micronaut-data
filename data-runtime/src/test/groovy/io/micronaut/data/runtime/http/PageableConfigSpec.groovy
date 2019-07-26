package io.micronaut.data.runtime.http

import io.micronaut.context.ApplicationContext
import io.micronaut.data.runtime.config.DataConfiguration
import spock.lang.Specification

class PageableConfigSpec extends Specification {

    void 'test pageable config'() {
        given:
        def context = ApplicationContext.run(
                'micronaut.data.pageable.max-page-size': 30,
                'micronaut.data.pageable.sort-parameter-name': 's',
                'micronaut.data.pageable.page-parameter-name': 'index',
                'micronaut.data.pageable.size-parameter-name': 'max'
        )
        DataConfiguration.PageableConfiguration configuration = context.getBean(DataConfiguration.PageableConfiguration)

        expect:
        configuration.maxPageSize == 30
        configuration.sortParameterName == 's'
        configuration.pageParameterName == 'index'
        configuration.sizeParameterName == 'max'

        cleanup:
        context.close()
    }
}
