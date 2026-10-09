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
package io.micronaut.data.processor.python

import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

import static io.micronaut.data.processor.visitors.TestUtils.getDataInterceptor
import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingIndexes
import static io.micronaut.data.processor.visitors.TestUtils.getParameterPropertyPaths
import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class PythonReservationSpec extends AbstractPythonTypeElementSpec {

    void "Python reservation #snake has the same SQL and bindings as #camel"() {
        given:
        def definition = repository("""
    @abstractmethod
    def ${camel}(self, id: Annotated[int, Id], balance: int, credit: int) -> int: ...
    @abstractmethod
    def ${snake}(self, id: Annotated[int, Id], balance: int, credit: int) -> int: ...
""")
        def camelMethod = definition.executableMethods.find { it.methodName == camel }
        def snakeMethod = definition.executableMethods.find { it.methodName == snake }

        expect:
        camelMethod != null
        snakeMethod != null
        getQuery(snakeMethod) == getQuery(camelMethod)
        getParameterBindingIndexes(snakeMethod) == getParameterBindingIndexes(camelMethod)
        getParameterPropertyPaths(snakeMethod) == getParameterPropertyPaths(camelMethod)
        getDataInterceptor(snakeMethod) == getDataInterceptor(camelMethod)

        where:
        camel                                         | snake
        "reserveIncrementBalanceAndDecrementCredit"   | "reserve_increment_balance_and_decrement_credit"
        "reserveDecrementBalanceAndIncrementCredit"   | "reserve_decrement_balance_and_increment_credit"
    }

    void "Python reservation binds native underscore properties by name or alias"() {
        given:
        def definition = repository("""
    @abstractmethod
    def reserve_increment_available_balance_and_decrement_reserved_balance(self, id: Annotated[int, Id], ${parameters}) -> int: ...
""")
        def method = definition.executableMethods.find { it.methodName == "reserve_increment_available_balance_and_decrement_reserved_balance" }

        expect:
        method != null
        getQuery(method) == 'UPDATE "ACCOUNT" SET "AVAILABLE_BALANCE"=("AVAILABLE_BALANCE" + ?),"RESERVED_BALANCE"=("RESERVED_BALANCE" - ?) WHERE ("ID" = ?)'
        getParameterBindingIndexes(method) == ['2', '1', '0'] as String[]
        getParameterPropertyPaths(method) == ['available_balance', 'reserved_balance', 'id'] as String[]

        where:
        parameters << [
            "reserved_balance: int, available_balance: int",
            'debit: Annotated[int, Parameter("reserved_balance")], credit: Annotated[int, Parameter("available_balance")]'
        ]
    }

    void "Python reservation resolves literal grammar words before splitting deltas"() {
        given:
        def definition = repository('''
    @abstractmethod
    def reserve_increment_available_balance_and_decrement_reserved_balance(self, id: Annotated[int, Id], available_balance_and_decrement_reserved_balance: int) -> int: ...
    @abstractmethod
    def reserve_increment_available_balance__and_decrement_reserved_balance(self, id: Annotated[int, Id], available_balance: int, reserved_balance: int) -> int: ...
''', 'ORACLE', '''
    available_balance_and_decrement_reserved_balance: Annotated[int, Reservable]
''')
        def literal = definition.executableMethods.find { it.methodName == "reserve_increment_available_balance_and_decrement_reserved_balance" }
        def deltas = definition.executableMethods.find { it.methodName == "reserve_increment_available_balance__and_decrement_reserved_balance" }

        expect:
        getQuery(literal) == 'UPDATE "ACCOUNT" SET "AVAILABLE_BALANCE_AND_DECREMENT_RESERVED_BALANCE"=("AVAILABLE_BALANCE_AND_DECREMENT_RESERVED_BALANCE" + ?) WHERE ("ID" = ?)'
        getParameterPropertyPaths(literal) == ['available_balance_and_decrement_reserved_balance', 'id'] as String[]
        getQuery(deltas) == 'UPDATE "ACCOUNT" SET "AVAILABLE_BALANCE"=("AVAILABLE_BALANCE" + ?),"RESERVED_BALANCE"=("RESERVED_BALANCE" - ?) WHERE ("ID" = ?)'
    }

    void "Python reservation preserves native property casing"() {
        given:
        def definition = buildBeanDefinition("python", "AccountRepository\$Intercepted", '''
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, MappedEntity, Reservable
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import GenericRepository

@MappedEntity("account")
@dataclass
class Account:
    id: Annotated[int, Id]
    Balance: Annotated[int, Reservable]

@JdbcRepository(dialect="ORACLE")
class AccountRepository(GenericRepository[Account, int], ABC):
    @abstractmethod
    def reserve_increment_Balance(self, id: Annotated[int, Id], Balance: int) -> int: ...
''')
        def method = definition.executableMethods.find { it.methodName == "reserve_increment_Balance" }

        expect:
        method != null
        getParameterPropertyPaths(method) == ['Balance', 'id'] as String[]
        getParameterBindingIndexes(method) == ['1', '0'] as String[]
    }

    void "Python reservation preserves validation for #methodName"() {
        when:
        repository("""
    @abstractmethod
    def ${methodName}(self${parameters}) -> ${returnType}: ...
""", dialect)

        then:
        def failure = thrown(RuntimeException)
        failure.message.contains(message)

        where:
        methodName                                                | parameters                                                          | returnType | dialect  | message
        "reserve_increment_balance"                               | ", id: Annotated[int, Id], balance: int"                             | "int"      | "H2"     | "Reservation methods require the Oracle dialect"
        "reserve_increment_balance"                               | ", id: int, balance: int"                                            | "int"      | "ORACLE" | "require exactly one @Id parameter"
        "reserve_increment_balance"                               | ", id: Annotated[str, Id], balance: int"                             | "int"      | "ORACLE" | "does not match ID type of entity"
        "reserve_increment_balance"                               | ", id: Annotated[int, Id], balance: int"                             | "str"      | "ORACLE" | "only support void or number based return types"
        "reserve_increment_balance_and_decrement_balance"         | ", id: Annotated[int, Id], balance: int, other: int"                 | "int"      | "ORACLE" | "is declared more than once"
        "reserve_increment_unreserved_balance"                    | ", id: Annotated[int, Id], unreserved_balance: int"                  | "int"      | "ORACLE" | "must be annotated with @Reservable"
        "reserve_increment_balance"                               | ", id: Annotated[int, Id], balance: str"                             | "int"      | "ORACLE" | "Reservation delta parameter [balance] must be numeric"
        "reserve_increment_available_balance"                     | ", id: Annotated[int, Id], availableBalance: int"                    | "int"      | "ORACLE" | "requires a matching delta parameter named [available_balance]"
        "reserve_increment_balance"                               | ", id: Annotated[int, Id], balance: int, other: int"                 | "int"      | "ORACLE" | "require one delta parameter for each reservation property"
        "reserve_increment_balance"                               | ', id: Annotated[int, Id], balance: int, other: Annotated[int, Parameter("balance")]' | "int" | "ORACLE" | "matches more than one delta parameter"
        "reserve_add_balance"                                     | ", id: Annotated[int, Id], balance: int"                             | "int"      | "ORACLE" | "Unsupported Python snake_case derived query"
        "reserve_increment_balance_and_"                          | ", id: Annotated[int, Id], balance: int"                             | "int"      | "ORACLE" | "Unsupported Python snake_case derived query"
    }

    void "Python reservation resolves native embedded property paths"() {
        given:
        def definition = buildBeanDefinition("python", "AccountRepository\$Intercepted", '''
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Embeddable, Id, MappedEntity, Relation, Reservable
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import GenericRepository

@Embeddable
@dataclass
class Limits:
    available_balance: Annotated[int, Reservable]

@MappedEntity("account")
@dataclass
class Account:
    id: Annotated[int, Id]
    limits: Annotated[Limits, Relation(Relation.Kind.EMBEDDED)]

@JdbcRepository(dialect="ORACLE")
class AccountRepository(GenericRepository[Account, int], ABC):
    @abstractmethod
    def reserve_increment_limits_available_balance(self, id: Annotated[int, Id], limits_available_balance: int) -> int: ...
    @abstractmethod
    def reserve_increment_limits__available_balance(self, id: Annotated[int, Id], limits__available_balance: int) -> int: ...
''')
        def implicit = definition.executableMethods.find { it.methodName == "reserve_increment_limits_available_balance" }
        def explicit = definition.executableMethods.find { it.methodName == "reserve_increment_limits__available_balance" }

        expect:
        implicit != null
        explicit != null
        getQuery(implicit) == getQuery(explicit)
        getQuery(explicit) == 'UPDATE "ACCOUNT" SET "AVAILABLE_BALANCE"=("AVAILABLE_BALANCE" + ?) WHERE ("ID" = ?)'
        getParameterPropertyPaths(explicit) == ['limits.available_balance', 'id'] as String[]
        getParameterBindingIndexes(explicit) == ['1', '0'] as String[]
    }

    private def repository(String methods, String dialect = "ORACLE", String extraFields = "") {
        buildBeanDefinition("python", "AccountRepository\$Intercepted", """
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.context.annotation import Parameter
from micronaut.data.annotation import Id, MappedEntity, Reservable
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import GenericRepository

@MappedEntity("account")
@dataclass
class Account:
    id: Annotated[int, Id]
    balance: Annotated[int, Reservable]
    credit: Annotated[int, Reservable]
    available_balance: Annotated[int, Reservable]
    reserved_balance: Annotated[int, Reservable]
    unreserved_balance: int
${extraFields}

@JdbcRepository(dialect="${dialect}")
class AccountRepository(GenericRepository[Account, int], ABC):
${methods}
""")
    }
}
