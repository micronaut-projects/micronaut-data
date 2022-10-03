package io.micronaut.data.azure.entities;

import io.micronaut.data.cosmos.annotation.CosmosContainerDef;
import io.micronaut.data.document.tck.entities.Author;
import io.micronaut.data.document.tck.entities.Book;

@CosmosContainerDef(name = "books", throughputAutoScale = false, throughputRequestUnits = 400, mappedEntities = {Book.class, Author.class}, partitionKeyPath = "/id")
public class BooksContainer {
}
