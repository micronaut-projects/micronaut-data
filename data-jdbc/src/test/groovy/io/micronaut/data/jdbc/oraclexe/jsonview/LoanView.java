package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.serde.annotation.Serdeable;

@JsonView(entity = Loan.class)
@Serdeable
public record LoanView(
    @Id
    Long id,
    @Relation(Relation.Kind.MANY_TO_ONE)
    LoanedBook book
) {
    /**
     * A book nested as a single object can't take DELETE without a foreign key (ORA-42693), so it has only UPDATE.
     */
    @Serdeable
    @JsonSubView(entity = Book.class, operations = JsonView.Operation.UPDATE)
    public record LoanedBook(@Id @MappedProperty("BOOK-KEY") Long id, String title) {
    }
}
