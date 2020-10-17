package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.entities.Children;
import io.micronaut.data.hibernate.entities.ChildrenId;
import io.micronaut.data.jpa.repository.JpaRepository;

@Repository
public interface ChildrenRepository extends JpaRepository<Children, ChildrenId> {

    //int findMaxIdNumberByIdParentId(int parentId);

    @Query(nativeQuery = true, value = "SELECT max(c.number) FROM children c WHERE c.parent_id = :parentId")
    Integer getMaxNumber(int parentId);
}