package io.micronaut.data.hibernate6;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate6.entities.Children;
import io.micronaut.data.hibernate6.entities.ChildrenId;
import io.micronaut.data.hibernate6.jpa.repository.JpaRepository;

@Repository
public interface ChildrenRepository extends JpaRepository<Children, ChildrenId> {

    //int findMaxIdNumberByIdParentId(int parentId);

    @Query(nativeQuery = true, value = "SELECT max(c.number) FROM children c WHERE c.parent_id = :parentId")
    Integer getMaxNumber(int parentId);
}
