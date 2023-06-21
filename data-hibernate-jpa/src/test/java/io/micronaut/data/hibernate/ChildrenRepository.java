package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.entities.Children;
import io.micronaut.data.hibernate.entities.ChildrenId;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.jpa.repository.intercept.LoadInterceptor;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@Repository
public interface ChildrenRepository extends JpaRepository<Children, @NotNull ChildrenId> {
    //int findMaxIdNumberByIdParentId(int parentId);

    @Query(nativeQuery = true, value = "SELECT max(c.number) FROM children c WHERE c.parent_id = :parentId")
    Integer getMaxNumber(int parentId);

    @DataMethod(interceptor = LoadInterceptor.class)
    @Override
    <S extends Children> S load(@NotNull Object id);
}
