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
package io.micronaut.data.r2dbc.oracle.reservable

import io.micronaut.context.ApplicationContext
import io.micronaut.data.exceptions.DataIntegrityViolationException
import io.micronaut.data.r2dbc.oraclexe.OracleXETestPropertyProvider
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class OracleR2dbcReservableSpec extends Specification implements OracleXETestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    ReservableAccountRepository repository = context.getBean(ReservableAccountRepository)

    @Shared
    ReservableWalletRepository walletRepository = context.getBean(ReservableWalletRepository)

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    void cleanup() {
        repository.deleteAll()
        walletRepository.deleteAll()
    }

    void "test Oracle reservable column with generated reservation delta updates"() {
        given:
        def account = repository.save(new ReservableAccount(name: "primary", balance: 100L))

        when:
        repository.reserveDecrementBalance(account.id, 40L)
        def updated = repository.findById(account.id).orElseThrow()

        then:
        updated.balance == 60L

        when:
        repository.reserveIncrementBalance(account.id, 10L)
        updated = repository.findById(account.id).orElseThrow()

        then:
        updated.balance == 70L

        when:
        repository.reserveDecrementBalance(account.id, 100L)

        then:
        thrown(DataIntegrityViolationException)
    }

    void "test reservation updating multiple reservable columns"() {
        given:
        def wallet = walletRepository.save(new ReservableWallet(name: "wallet", amount: 10L, balance: 100L))

        when:
        def updatedRows = walletRepository.reserveIncrementAmountAndDecrementBalance(wallet.id, 40L, 40L)
        def updated = walletRepository.findById(wallet.id).orElseThrow()

        then:
        updatedRows == 1
        updated.amount == 50L
        updated.balance == 60L

        when:
        walletRepository.reserveDecrementAmountAndIncrementBalance(wallet.id, 20L, 20L)
        updated = walletRepository.findById(wallet.id).orElseThrow()

        then:
        updated.amount == 30L
        updated.balance == 80L

        when: "aliased delta parameters are declared in a different order than the operations"
        walletRepository.reserveDecrementBalanceAndIncrementAmount(wallet.id, 5L, 30L)
        updated = walletRepository.findById(wallet.id).orElseThrow()

        then: "each delta is applied to its own column"
        updated.amount == 35L
        updated.balance == 50L

        when:
        walletRepository.reserveDecrementAmountAndIncrementBalance(wallet.id, 5L, 30L)
        updated = walletRepository.findById(wallet.id).orElseThrow()

        then:
        updated.amount == 30L
        updated.balance == 80L

        when: "the balance check constraint fails"
        walletRepository.reserveIncrementAmountAndDecrementBalance(wallet.id, 100L, 100L)

        then: "neither column changes"
        thrown(DataIntegrityViolationException)
        walletRepository.findById(wallet.id).orElseThrow().amount == 30L
        walletRepository.findById(wallet.id).orElseThrow().balance == 80L

        when: "the amount check constraint fails"
        walletRepository.reserveDecrementAmountAndIncrementBalance(wallet.id, 50L, 50L)

        then: "neither column changes"
        thrown(DataIntegrityViolationException)
        walletRepository.findById(wallet.id).orElseThrow().amount == 30L
        walletRepository.findById(wallet.id).orElseThrow().balance == 80L
    }
}
