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
package io.micronaut.data.processor.sql

import io.micronaut.data.processor.visitors.AbstractDataSpec

class ReservationMethodMatcherSpec extends AbstractDataSpec {

    void "test reservation method validates return type and ID parameter"() {
        when:
        buildReservationRepository('InvalidReservationReturnRepository', 'String reserveIncrementBalance(@Id Long id, Long balance);')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('only support void or number based return types')

        when:
        buildReservationRepository('MissingReservationIdRepository', 'long reserveIncrementBalance(Long id, Long balance);')

        then:
        e = thrown(RuntimeException)
        e.message.contains('require exactly one @Id parameter')

        when:
        buildReservationRepository('MismatchedReservationIdRepository', 'long reserveIncrementBalance(@Id String id, Long balance);')

        then:
        e = thrown(RuntimeException)
        e.message.contains('does not match ID type of entity')
    }

    void "test reservation method validates grammar and delta parameters"() {
        when:
        buildReservationRepository('InvalidReservationNameRepository', 'long reserveAddBalance(@Id Long id, Long balance);')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Invalid reservation method name')

        when:
        buildReservationRepository('DuplicateReservationTargetRepository', 'long reserveIncrementBalanceAndDecrementBalance(@Id Long id, Long balance, Long secondBalance);')

        then:
        e = thrown(RuntimeException)
        e.message.contains('is declared more than once')

        when:
        buildReservationRepository('NonNumericReservationDeltaRepository', 'long reserveIncrementBalance(@Id Long id, String balance);')

        then:
        e = thrown(RuntimeException)
        e.message.contains('Reservation delta parameter [balance] must be numeric')

        when:
        buildReservationRepository('MissingReservationDeltaRepository', 'long reserveIncrementBalance(@Id Long id, Long amount);')

        then:
        e = thrown(RuntimeException)
        e.message.contains('requires a matching delta parameter')

        when:
        buildReservationRepository('ExtraReservationDeltaRepository', 'long reserveIncrementBalance(@Id Long id, Long balance, Long amount);')

        then:
        e = thrown(RuntimeException)
        e.message.contains('require one delta parameter for each reservation property')
    }

    private void buildReservationRepository(String repositoryName, String method) {
        buildRepository("test.$repositoryName", """
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Reservable;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;

@JdbcRepository(dialect = Dialect.ORACLE)
interface $repositoryName extends GenericRepository<Account, Long> {
    $method
}

@MappedEntity
class Account {
    @Id private Long id;
    @Reservable private Long balance;
    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    Long getBalance() { return balance; }
    void setBalance(Long balance) { this.balance = balance; }
}
""")
    }
}
