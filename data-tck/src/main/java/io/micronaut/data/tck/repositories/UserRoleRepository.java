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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.jdbc.entities.Role;
import io.micronaut.data.tck.jdbc.entities.User;
import io.micronaut.data.tck.jdbc.entities.UserRole;
import io.micronaut.data.tck.jdbc.entities.UserRoleId;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface UserRoleRepository extends GenericRepository<UserRole, UserRoleId> {

    @NonNull
    UserRole save(@Valid @NotNull @NonNull UserRole entity);

    default UserRole save(User user, Role role) {
        return save(new UserRole(new UserRoleId(user, role)));
    }

    void deleteById(@NonNull @NotNull UserRoleId id);

    default void delete(User user, Role role) {
        deleteById(new UserRoleId(user, role));
    }

    int count();

    @Join("role")
    Iterable<Role> findRoleByUser(User user);

    void deleteAll();
}
