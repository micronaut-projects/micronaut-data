package io.micronaut.data.cosmos.common

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration
import io.micronaut.data.cosmos.config.StorageUpdatePolicy
import io.micronaut.test.support.TestPropertyProvider
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MissingEndpointConfigSpec extends Specification implements TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Override
    Map<String, String> getProperties() {
        return [
                'azure.cosmos.default-gateway-mode'                        : 'true',
                'azure.cosmos.endpoint-discovery-enabled'                  : 'false',
                'azure.cosmos.database.throughput-settings.request-units'  : '1200',
                'azure.cosmos.database.throughput-settings.auto-scale'     : 'false',
                'azure.cosmos.database.database-name'                      : 'customdb',
                'azure.cosmos.database.packages'                           : 'io.micronaut.data.azure.entities',
                'spec.name'                                                : getClass().getSimpleName()
        ]
    }

    def "test configuration"() {
        given:
            def config = context.getBean(CosmosDatabaseConfiguration)

        expect:
            config.databaseName == 'customdb'
            !config.throughput.autoScale
            config.throughput.requestUnits == 1200
            config.updatePolicy == StorageUpdatePolicy.NONE
            !config.containers || config.containers.size() == 0

        when:
            context.getBean(CosmosDatabaseInitializer)
        then:
            thrown(NoSuchBeanException)
    }


}
