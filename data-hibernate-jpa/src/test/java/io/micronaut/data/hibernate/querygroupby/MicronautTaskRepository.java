package io.micronaut.data.hibernate.querygroupby;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.entities.MicronautTask;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
@Repository
public interface MicronautTaskRepository extends CrudRepository<MicronautTask, Long> {
    @Query("select count(*), year(t.dueDate) from MicronautTask t group by year(t.dueDate)")
    Iterable<TasksPerYear> countByDueYear();

    @Query("select count(*), year(t.dueDate) from MicronautTask t group by year(t.dueDate)")
    List<List<Integer>> countByDueYearReturnList();
}
