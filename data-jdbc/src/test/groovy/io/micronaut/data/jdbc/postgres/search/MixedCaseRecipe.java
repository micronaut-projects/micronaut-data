package io.micronaut.data.jdbc.postgres.search;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.Searchable;

/**
 * A searchable entity whose unquoted mixed-case index name PostgreSQL stores in lower case.
 */
@MappedEntity(value = "mixed_case_recipe", escape = false)
@Searchable(name = "IdxMixedCaseRecipe")
public class MixedCaseRecipe {
    @Id
    @GeneratedValue
    private Long id;
    @SearchField
    private String instructions;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }
}
