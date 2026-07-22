/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.runtime.support

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanResolutionContext
import io.micronaut.context.condition.ConditionContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class DataSourceConfigurationUtilsSpec extends Specification {

    void "configured datasource names are resolved from property entries"() {
        given:
        ApplicationContext beanContext = ApplicationContext.run()
        ConditionContext<?> conditionContext = Stub(ConditionContext) {
            getBeanContext() >> beanContext
            getPropertyEntries('datasources') >> ['default', 'mdb']
        }

        expect:
        DataSourceConfigurationUtils.resolveConfiguredDataSourceNames(conditionContext, 'datasources', TestConfiguration) == ['default', 'mdb']

        cleanup:
        beanContext.close()
    }

    void "configured datasource names fall back to configuration bean definitions"() {
        given:
        ApplicationContext beanContext = ApplicationContext.run()
        BeanDefinition<?> defaultDefinition = beanDefinition('default')
        BeanDefinition<?> mdbDefinition = beanDefinition('mdb')
        ConditionContext<?> conditionContext = Stub(ConditionContext) {
            getBeanContext() >> beanContext
            getPropertyEntries('datasources') >> []
            findBeanDefinitions(TestConfiguration) >> [defaultDefinition, mdbDefinition]
        }

        expect:
        DataSourceConfigurationUtils.resolveConfiguredDataSourceNames(conditionContext, 'datasources', TestConfiguration) == ['default', 'mdb']

        cleanup:
        beanContext.close()
    }

    void "configured datasource names are cached per bean context prefix and configuration type"() {
        given:
        ApplicationContext beanContext = ApplicationContext.run()
        int propertyEntryLookups = 0
        ConditionContext<?> conditionContext = Stub(ConditionContext) {
            getBeanContext() >> beanContext
            getPropertyEntries('datasources') >> {
                propertyEntryLookups++
                ['default']
            }
        }

        when:
        List<String> first = DataSourceConfigurationUtils.resolveConfiguredDataSourceNames(conditionContext, 'datasources', TestConfiguration)
        List<String> second = DataSourceConfigurationUtils.resolveConfiguredDataSourceNames(conditionContext, 'datasources', TestConfiguration)

        then:
        first == ['default']
        second == ['default']
        propertyEntryLookups == 1

        cleanup:
        beanContext.close()
    }

    void "datasource name is resolved from current bean resolution qualifier"() {
        given:
        BeanResolutionContext beanResolutionContext = Stub(BeanResolutionContext) {
            getCurrentQualifier() >> Qualifiers.byName('mdb')
        }
        ConditionContext<?> conditionContext = Stub(ConditionContext) {
            getBeanResolutionContext() >> beanResolutionContext
        }

        expect:
        DataSourceConfigurationUtils.resolveDataSourceName(conditionContext).get() == 'mdb'
    }

    void "datasource name is resolved from current path segment qualifier"() {
        given:
        BeanResolutionContext.Segment segment = Stub(BeanResolutionContext.Segment) {
            getDeclaringTypeQualifier() >> Qualifiers.byName('mdb')
        }
        BeanResolutionContext.Path path = Stub(BeanResolutionContext.Path) {
            currentSegment() >> Optional.of(segment)
        }
        BeanResolutionContext beanResolutionContext = Stub(BeanResolutionContext) {
            getCurrentQualifier() >> null
            getPath() >> path
        }
        ConditionContext<?> conditionContext = Stub(ConditionContext) {
            getBeanResolutionContext() >> beanResolutionContext
        }

        expect:
        DataSourceConfigurationUtils.resolveDataSourceName(conditionContext).get() == 'mdb'
    }

    void "datasource name falls back to component qualifier"() {
        given:
        BeanDefinition<?> beanDefinition = beanDefinition('mdb')
        ConditionContext<?> conditionContext = Stub(ConditionContext) {
            getBeanResolutionContext() >> null
            getComponent() >> beanDefinition
        }

        expect:
        DataSourceConfigurationUtils.resolveDataSourceName(conditionContext).get() == 'mdb'
    }

    void "datasource name is empty when there is no qualifier"() {
        given:
        ConditionContext<?> conditionContext = Stub(ConditionContext) {
            getBeanResolutionContext() >> null
            getComponent() >> null
        }

        expect:
        DataSourceConfigurationUtils.resolveDataSourceName(conditionContext).isEmpty()
    }

    private BeanDefinition<?> beanDefinition(String name) {
        Stub(BeanDefinition) {
            getDeclaredQualifier() >> Qualifiers.byName(name)
        }
    }

    private static final class TestConfiguration {
    }
}
