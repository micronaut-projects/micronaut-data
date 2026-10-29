package io.micronaut.data.age.features;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.age.annotation.AgeRepository;
import io.micronaut.data.repository.CrudRepository;

@Requires(property = "spec.name", value = "AgeDataSourceGraphsSpec")
@AgeRepository("reportingDb")
public interface ReportingAccountRepository extends CrudRepository<Account, Long> {
}
