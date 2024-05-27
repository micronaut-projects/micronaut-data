package io.micronaut.data.jdbc.h2;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import io.micronaut.data.tck.entities.Person;
import jakarta.persistence.criteria.JoinType;

@JdbcRepository(dialect = Dialect.H2)
@Join(value = "genres")
public interface NewAuthorRepository extends CrudRepository<NewAuthor, Long>, JpaSpecificationExecutor<NewAuthor> {

    class Specifications {

        public static QuerySpecification<Person> criteria() {
            return (root, query, criteriaBuilder) -> {
                jakarta.persistence.criteria.Join<NewAuthor, NewGenre> genreJoin = root.joinList("genres", JoinType.LEFT);
                return criteriaBuilder.or(genreJoin.get("name").in("Horror", "Thriller"));
            };
        }

    }
}
