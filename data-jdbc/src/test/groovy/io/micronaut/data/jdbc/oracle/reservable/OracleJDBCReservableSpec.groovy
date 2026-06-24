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
package io.micronaut.data.jdbc.oracle.reservable

import io.micronaut.context.ApplicationContext
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class OracleJDBCReservableSpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties + ["datasources.default.dialect-options.version": "26"])

    @Shared
    ReservableAccountRepository repository = context.getBean(ReservableAccountRepository)

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    void cleanup() {
        repository.deleteAll()
    }

    void "test Oracle reservable column with raw delta update"() {
        given:
        def account = repository.save(new ReservableAccount(name: "primary", balance: 100L))

        when:
        repository.reserve(account.id, -40L)
        def updated = repository.findById(account.id).orElseThrow()

        then:
        updated.balance == 60L

        when:
        repository.reserve(account.id, -100L)

        then:
        thrown(DataAccessException)
    }
}
