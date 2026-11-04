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
package io.micronaut.data.runtime.mapper

import io.micronaut.core.annotation.Introspected
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.exceptions.ConversionErrorException
import spock.lang.Specification

class BeanIntrospectionMapperConversionErrorSpec extends Specification {

    void "a failed conversion reports the cause"() {
        given:
            BeanIntrospectionMapper<Map<String, Object>, CountedThing> mapper = new BeanIntrospectionMapper<Map<String, Object>, CountedThing>() {
                @Override
                Object read(Map<String, Object> object, String name) {
                    return object.get(name)
                }

                @Override
                ConversionService getConversionService() {
                    return ConversionService.SHARED
                }
            }
        when:
            mapper.map([name: "a", count: "not a number"], CountedThing)
        then:
            def e = thrown(ConversionErrorException)
            e.argument.name == "count"
            e.conversionError.cause instanceof NumberFormatException
    }
}

@Introspected
class CountedThing {
    String name
    Integer count
}
