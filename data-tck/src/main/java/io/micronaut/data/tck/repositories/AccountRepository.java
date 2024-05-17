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
package io.micronaut.data.tck.repositories;

import io.micronaut.data.annotation.WithTenantId;
import io.micronaut.data.annotation.WithoutTenantId;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Account;

import java.util.List;

public interface AccountRepository extends CrudRepository<Account, Long> {

    @WithoutTenantId
    List<Account> findAll$withAllTenants();

    @WithTenantId("bar")
    List<Account> findAll$withTenantBar();

    @WithTenantId("foo")
    List<Account> findAll$withTenantFoo();

    @WithTenantId("#{this.barTenant()}")
    List<Account> findAll$withTenantExpression();

    default String barTenant() {
        return "bar";
    }
}
