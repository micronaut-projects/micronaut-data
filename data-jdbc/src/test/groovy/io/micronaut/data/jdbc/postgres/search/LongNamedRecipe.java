package io.micronaut.data.jdbc.postgres.search;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.Searchable;

@MappedEntity("seasonal_recipes_from_the_regional_kitchens")
@Searchable
public class LongNamedRecipe {
    @Id
    @GeneratedValue
    private Long id;
    @SearchField(fuzzy = true)
    private String preparationInstructionsText;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPreparationInstructionsText() {
        return preparationInstructionsText;
    }

    public void setPreparationInstructionsText(String preparationInstructionsText) {
        this.preparationInstructionsText = preparationInstructionsText;
    }
}
