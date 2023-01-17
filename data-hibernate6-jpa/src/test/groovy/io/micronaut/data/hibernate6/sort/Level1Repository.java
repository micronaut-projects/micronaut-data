package io.micronaut.data.hibernate6.sort;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate6.jpa.repository.JpaRepository;
import io.micronaut.data.model.Sort;

import java.util.List;

@Repository
public interface Level1Repository extends JpaRepository<Level1, Long> {

    @Query("select distinct level1_ from Level1 level1_" +
            " left join fetch level1_.level2 level1_level2_" +
            " left join fetch level1_level2_.level3 level1_level2_level3_")
    @NonNull
    @Override
    List<Level1> findAll(@NonNull Sort sort);
}
