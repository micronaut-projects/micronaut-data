/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.runtime.http

import io.micronaut.context.ApplicationContext
import io.micronaut.data.runtime.config.DataConfiguration
import spock.lang.Specification

class PageableConfigSpec extends Specification {

    void 'test pageable config'() {
        given:
        def context = ApplicationContext.run(
                'micronaut.data.pageable.max-page-size': 30,
                'micronaut.data.pageable.default-page-size': 10,
                'micronaut.data.pageable.sort-parameter-name': 's',
                'micronaut.data.pageable.page-parameter-name': 'index',
                'micronaut.data.pageable.size-parameter-name': 'max'
        )
        DataConfiguration.PageableConfiguration configuration = context.getBean(DataConfiguration.PageableConfiguration)

        expect:
        configuration.maxPageSize == 30
        configuration.defaultPageSize == 10
        configuration.sortParameterName == 's'
        configuration.pageParameterName == 'index'
        configuration.sizeParameterName == 'max'

        cleanup:
        context.close()
    }

    void 'defaultPageSize is equal to maxPageSize if not set '(){
        given:
           def configuration = new DataConfiguration.PageableConfiguration()
           configuration.maxPageSize = 50

        expect:
            configuration.defaultPageSize == 50
    }
}
