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
package io.micronaut.data.runtime.convert

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.core.convert.DefaultMutableConversionService
import io.micronaut.core.convert.MutableConversionService
import spock.lang.Specification
import spock.lang.Unroll

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.*

class DataConversionServiceSpec extends Specification {

    static def DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd")
    static Date now = new Date()

    @Unroll
    def "test date conversion #obj to #targetType"() {
        given:
            BeanContext mockBeanContext = new DefaultBeanContext() {
                @Override
                MutableConversionService getConversionService() {
                    return new DefaultMutableConversionService()
                }
            }
            DataConversionService conversionService = new DataConversionServiceFactory().build(mockBeanContext)
        when:
            def expectedValue = conversionService.convert(obj, targetType)
        then:
            result == expectedValue.get()
        where:
            obj                                          || targetType     || result
            DATE_FORMAT.parse("1970-01-02")              || LocalDate      || LocalDate.parse("1970-01-02")
            DATE_FORMAT.parse("1970-01-02")              || LocalDateTime  || LocalDate.parse("1970-01-02").atStartOfDay()
            DATE_FORMAT.parse("1970-01-02")              || OffsetDateTime || LocalDate.parse("1970-01-02").atStartOfDay().atZone(ZoneId.systemDefault()).toOffsetDateTime()
            LocalDate.parse("1970-01-02")                || Date           || DATE_FORMAT.parse("1970-01-02")
            LocalDate.parse("1970-01-02").atStartOfDay() || Date           || DATE_FORMAT.parse("1970-01-02")
            new Date(now.getTime())                      || Instant        || Instant.ofEpochMilli(now.getTime())
            Instant.ofEpochMilli(now.getTime())          || Date           || new Date(now.getTime())

            new java.sql.Date(now.getTime())             || Instant        || Instant.ofEpochMilli(now.getTime())
            Instant.ofEpochMilli(now.getTime())          || java.sql.Date  || new Date(now.getTime())

            new java.sql.Date(now.getTime())             || LocalDate      || Instant.ofEpochMilli(now.getTime()).atZone(ZoneId.systemDefault()).toLocalDate()
            new java.sql.Date(now.getTime())             || LocalDateTime  || Instant.ofEpochMilli(now.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime()
            new java.sql.Date(now.getTime())             || OffsetDateTime || Instant.ofEpochMilli(now.getTime()).atZone(ZoneId.systemDefault()).toOffsetDateTime()

            LocalDate.parse("1970-01-02")
                    .atTime( LocalTime.of(2,0))
                    .atOffset(ZoneOffset.of("+02:00"))   || java.sql.Date  || new java.sql.Date(24 *60 * 60 * 1000)
            LocalDate.parse("1970-01-02")
                .atTime( LocalTime.of(2,0))
                .atOffset(ZoneOffset.of("+02:00"))       || Date           || new Date(24 *60 * 60 * 1000)
            LocalDate.parse("1970-01-02")
                .atTime( LocalTime.of(2,0))
                .atOffset(ZoneOffset.of("+02:00"))       || Long           || 24 *60 * 60 * 1000
            LocalDate.parse("1970-01-02")
                .atTime( LocalTime.of(2,0))
                .atOffset(ZoneOffset.of("+02:00"))       || Timestamp      || new Timestamp(24 *60 * 60 * 1000)
            LocalDate.parse("1970-01-02")
                .atTime( LocalTime.of(2,0))
                .atOffset(ZoneOffset.of("+02:00"))       || LocalDateTime  || LocalDate.parse("1970-01-02").atTime( LocalTime.of(2,0))


    }
}
