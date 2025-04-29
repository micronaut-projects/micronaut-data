package example;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Relation.Kind;

@MappedEntity("some_table")
public class EmbeddedEntity {

    @EmbeddedId
    private PrimaryKey primaryKey;

    private String col;

    public PrimaryKey getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(PrimaryKey primaryKey) {
        this.primaryKey = primaryKey;
    }

    public String getCol() {
        return col;
    }

    public void setCol(String col) {
        this.col = col;
    }

    @Embeddable
    public record PrimaryKey(
        int someColumn,
        @Relation(Kind.MANY_TO_ONE) OtherEntity otherEntity
    ) {}

    @MappedEntity("other_table")
    public static class OtherEntity {

        @Id
        @AutoPopulated
        @GeneratedValue
        private Long id;

        private String someColumn;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getSomeColumn() {
            return someColumn;
        }

        public void setSomeColumn(String someColumn) {
            this.someColumn = someColumn;
        }
    }
}
