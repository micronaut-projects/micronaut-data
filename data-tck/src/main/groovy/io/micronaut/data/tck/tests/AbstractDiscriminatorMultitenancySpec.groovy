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
package io.micronaut.data.tck.tests

import groovy.transform.EqualsAndHashCode
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.core.annotation.Introspected
import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.annotation.Connectable
import io.micronaut.data.tck.entities.Account
import io.micronaut.data.tck.repositories.AccountRepository
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.transaction.Transactional
import spock.lang.Specification

abstract class AbstractDiscriminatorMultitenancySpec extends Specification {

    abstract Map<String, String> getExtraProperties()

    Map<String, String> getDataSourceProperties() {
        return [:]
    }

    def "test discriminator multitenancy"() {
        setup:
            EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, getExtraProperties() + getProperties() + [
                    'spec.name'                                               : 'discriminator-multitenancy',
                    'micronaut.multitenancy.tenantresolver.httpheader.enabled': 'true',
                    'datasource.default.schema-generate'                      : 'create-drop'
            ], Environment.TEST)
            def context = embeddedServer.applicationContext
            FooAccountClient fooAccountClient = context.getBean(FooAccountClient)
            BarAccountClient barAccountClient = context.getBean(BarAccountClient)
            fooAccountClient.deleteAll()
            barAccountClient.deleteAll()
        when: 'An account created in FOO tenant'
            AccountDto fooAccount = fooAccountClient.save("The Stand")
        then: 'The account exists in FOO tenant'
            fooAccount.id
        when:
            fooAccount = fooAccountClient.findOne(fooAccount.getId()).orElse(null)
        then:
            fooAccount
            fooAccount.name == "The Stand"
            fooAccount.tenancy == "foo"
        and: 'There is one account'
            fooAccountClient.findAll().size() == 1
        and: 'There is no accounts in BAR tenant'
            barAccountClient.findAll().size() == 0

        when: "Update the tenancy"
            fooAccountClient.updateTenancy(fooAccount.getId(), "bar")
        then:
            fooAccountClient.findAll().size() == 0
            barAccountClient.findAll().size() == 1
            fooAccountClient.findOne(fooAccount.getId()).isEmpty()
            barAccountClient.findOne(fooAccount.getId()).isPresent()

        when: "Update the tenancy"
            barAccountClient.updateTenancy(fooAccount.getId(), "foo")
        then:
            fooAccountClient.findAll().size() == 1
            barAccountClient.findAll().size() == 0
            fooAccountClient.findOne(fooAccount.getId()).isPresent()
            barAccountClient.findOne(fooAccount.getId()).isEmpty()

        when:
            AccountDto barAccount = barAccountClient.save("The Bar")
            def allAccounts = barAccountClient.findAllTenants()
        then:
            barAccount.tenancy == "bar"
            allAccounts.size() == 2
            allAccounts.find { it.id == barAccount.id }.tenancy == "bar"
            allAccounts.find { it.id == fooAccount.id }.tenancy == "foo"
            allAccounts == fooAccountClient.findAllTenants()

        when:
            def barAccounts = barAccountClient.findAllBarTenants()
        then:
            barAccounts.size() == 1
            barAccounts[0].id == barAccount.id
            barAccounts[0].tenancy == "bar"
            barAccounts == fooAccountClient.findAllBarTenants()

        when:
            def fooAccounts = barAccountClient.findAllFooTenants()
        then:
            fooAccounts.size() == 1
            fooAccounts[0].id == fooAccount.id
            fooAccounts[0].tenancy == "foo"
            fooAccounts == fooAccountClient.findAllFooTenants()

        when:
            def exp = barAccountClient.findTenantExpression()
        then:
            exp.size() == 1
            exp[0].tenancy == "bar"
            exp == fooAccountClient.findTenantExpression()

        when: 'Delete all BARs'
            barAccountClient.deleteAll()
        then: "FOOs aren't deletes"
            fooAccountClient.findAll().size() == 1

        when: 'Delete all FOOs'
            fooAccountClient.deleteAll()
        then: "All FOOs are deleted"
            fooAccountClient.findAll().size() == 0
        cleanup:
            embeddedServer?.stop()
    }

}

@Requires(property = "spec.name", value = "discriminator-multitenancy")
@ExecuteOn(TaskExecutors.IO)
@Controller("/accounts")
class AccountController {

    private final AccountRepository accountRepository

    AccountController(ApplicationContext beanContext) {
        def className = beanContext.getProperty("accountRepositoryClass", String).get()
        this.accountRepository = beanContext.getBean(Class.forName(className)) as AccountRepository
    }

    @Post
    AccountDto save(String name) {
        def newAccount = new Account()
        newAccount.name = name
        def account = accountRepository.save(newAccount)
        return new AccountDto(account)
    }

    @Put("/{id}/tenancy")
    void updateTenancy(Long id, String tenancy) {
        def account = accountRepository.findById(id).orElseThrow()
        account.tenancy = tenancy
        accountRepository.update(account)
    }

    @Get("/{id}")
    Optional<AccountDto> findOne(Long id) {
        return accountRepository.findById(id).map(AccountDto::new)
    }

    @Get
    List<AccountDto> findAll() {
        return findAll0()
    }

    @Get("/alltenants")
    List<AccountDto> findAllTenants() {
        return accountRepository.findAll$withAllTenants().stream().map(AccountDto::new).toList()
    }

    @Get("/foo")
    List<AccountDto> findAllFooTenants() {
        return accountRepository.findAll$withTenantFoo().stream().map(AccountDto::new).toList()
    }

    @Get("/bar")
    List<AccountDto> findAllBarTenants() {
        return accountRepository.findAll$withTenantBar().stream().map(AccountDto::new).toList()
    }

    @Get("/expression")
    List<AccountDto> findTenantExpression() {
        return accountRepository.findAll$withTenantExpression().stream().map(AccountDto::new).toList()
    }

    @Connectable
    protected List<AccountDto> findAll0() {
        findAll1()
    }

    @Connectable(propagation = ConnectionDefinition.Propagation.MANDATORY)
    protected List<AccountDto> findAll1() {
        accountRepository.findAll().stream().map(AccountDto::new).toList()
    }

    @Delete
    void deleteAll() {
        deleteAll0()
    }

    @Transactional
    protected deleteAll0() {
        deleteAll1()
    }

    @Transactional(Transactional.TxType.MANDATORY)
    protected deleteAll1() {
        accountRepository.deleteAll()
    }

}

@Introspected
@EqualsAndHashCode
class AccountDto {
    Long id
    String name
    String tenancy

    AccountDto() {
    }

    AccountDto(Account account) {
        id = account.id
        name = account.name
        tenancy = account.tenancy
    }

}

@Requires(property = "spec.name", value = "discriminator-multitenancy")
@Client("/accounts")
interface AccountClient {

    @Post
    AccountDto save(String name);

    @Put("/{id}/tenancy")
    void updateTenancy(Long id, String tenancy)

    @Get("/{id}")
    Optional<AccountDto> findOne(Long id);

    @Get
    List<AccountDto> findAll();

    @Get("/alltenants")
    List<AccountDto> findAllTenants();

    @Get("/foo")
    List<AccountDto> findAllFooTenants();

    @Get("/bar")
    List<AccountDto> findAllBarTenants();

    @Get("/expression")
    List<AccountDto> findTenantExpression();

    @Delete
    void deleteAll();
}


@Requires(property = "spec.name", value = "discriminator-multitenancy")
@Header(name = "tenantId", value = "foo")
@Client("/accounts")
interface FooAccountClient extends AccountClient {
}

@Requires(property = "spec.name", value = "discriminator-multitenancy")
@Header(name = "tenantId", value = "bar")
@Client("/accounts")
interface BarAccountClient extends AccountClient {
}
