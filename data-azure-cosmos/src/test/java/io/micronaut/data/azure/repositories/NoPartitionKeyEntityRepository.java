package io.micronaut.data.azure.repositories;

import com.azure.cosmos.models.PartitionKey;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.azure.entities.nopartitionkey.NoPartitionKeyEntity;
import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@CosmosRepository
public interface NoPartitionKeyEntityRepository extends CrudRepository<NoPartitionKeyEntity, String> {

    @Query(value = "SELECT * FROM c WHERE c.name = :name")
    List<NoPartitionKeyEntity> findAllByName(@Parameter("name") String name);

    @Query(value = "SELECT * FROM c WHERE c.name = :name AND c.grade = :grade")
    List<NoPartitionKeyEntity> findAllByNameAndGrade(@Parameter("name") String name, int grade);

    @Query(value = "SELECT * FROM c WHERE c.grade IN (:grades)")
    List<NoPartitionKeyEntity> findByGradeIn(List<Integer> grades);

    int findMaxGradeByIdIn(List<String> ids);

    int findSumGrade();

    int findAvgGradeByNameIn(List<String> names);

    int findMinGrade();

    List<NoPartitionKeyEntity> findByTagsArrayContains(String tag);

    void updateGrade(@Id String id, int grade);

    // This query should not update records because it is using partition key but
    // partition key is not defined on container/entity
    void updateGrade(@Id String id, int grade, PartitionKey partitionKey);

    void deleteByName(String name);

    // This query should not delete records because it is using partition key but
    // partition key is not defined on container/entity
    void deleteByGrade(int grade, PartitionKey partitionKey);

    String findNameById(String id);

    // This has to be custom query since we cannot make String[] as a result in Micronaut generated queries.
    @Query("SELECT DISTINCT VALUE f.tags FROM f WHERE f.id = :id")
    String[] getTagsById(String id);

    List<NoPartitionKeyEntity> findByNameIsNotEmpty();

    List<NoPartitionKeyEntity> findByCustomNameIsEmpty();

    List<NoPartitionKeyEntity> findByRatingIsNull();

    List<NoPartitionKeyEntity> findByCustomNameIsNull();
}
