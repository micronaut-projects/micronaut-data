package io.micronaut.data.hibernate;

import org.jspecify.annotations.NonNull;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.entities.Audit;
import io.micronaut.data.hibernate.entities.UserWithWhere;
import io.micronaut.data.hibernate.entities.UserWithWhereSummary;
import io.micronaut.data.model.Sort;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserWithWhereRepository extends CrudRepository<UserWithWhere, UUID> {

    List<UserWithWhere> findAllByIdInList(List<UUID> ids, @NonNull Sort sort);

    @Query(value = "UPDATE users SET email = :email, deleted = :deleted WHERE id = :id RETURNING *", nativeQuery = true)
    UserWithWhere updateReturningCustom(String email, boolean deleted, UUID id);

    @Query(value = "UPDATE users SET email = :email WHERE id = :id RETURNING email", nativeQuery = true)
    String updateAndReturnEmail(String email, UUID id);

    Audit findAuditById(UUID id);

    @Query(value = "SELECT id AS \"id\", email AS \"email\", deleted AS \"deleted\" FROM users WHERE id = :id", nativeQuery = true)
    UserWithWhereSummary findSummaryById(UUID id);

    @Query(value = "UPDATE users SET email = :email WHERE id = :id RETURNING id AS \"id\", email AS \"email\", deleted AS \"deleted\"", nativeQuery = true)
    UserWithWhereSummary updateReturningSummary(String email, UUID id);

    void updateEmailById(UUID id, String email);
}
