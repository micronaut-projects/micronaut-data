package io.micronaut.data.age.tck;

import io.micronaut.data.graph.tck.repositories.ColleagueRepository;
import io.micronaut.data.age.annotation.AgeRepository;

@AgeRepository
public interface AgeColleagueRepository extends ColleagueRepository {
}
