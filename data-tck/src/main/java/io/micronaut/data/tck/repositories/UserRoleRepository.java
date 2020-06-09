package io.micronaut.data.tck.repositories;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.jdbc.entities.Role;
import io.micronaut.data.tck.jdbc.entities.User;
import io.micronaut.data.tck.jdbc.entities.UserRole;
import io.micronaut.data.tck.jdbc.entities.UserRoleId;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

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
}
