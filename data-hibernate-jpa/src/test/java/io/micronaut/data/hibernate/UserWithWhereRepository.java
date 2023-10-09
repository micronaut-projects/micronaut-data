package io.micronaut.data.hibernate;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.entities.UserWithWhere;
import io.micronaut.data.model.Sort;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserWithWhereRepository extends CrudRepository<UserWithWhere, UUID> {

    List<UserWithWhere> findAllByIdInList(List<UUID> ids, @NonNull Sort sort);
}
