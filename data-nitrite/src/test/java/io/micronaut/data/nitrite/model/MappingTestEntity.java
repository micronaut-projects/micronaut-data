/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Transient;

/**
 * Test entity exercising edge-case dispatch strategies in the entity mapper.
 *
 * <ul>
 *   <li>{@code transientField} — covered by {@link Transient}: excluded from the document.</li>
 *   <li>{@code tag} — a non-java.*, non-entity POJO: classified SERDE.</li>
 *   <li>{@code bookRef} — a non-{@code @Relation} reference to a registered entity ({@link Book}):
 *       classified ENTITY_ID_REF; only the Book's ID is stored in the document.</li>
 * </ul>
 */
@MappedEntity("mapping_test_entity")
public class MappingTestEntity {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    @Transient
    private String transientField;

    private CustomTag tag;

    private Book bookRef;

    /** Default constructor. */
    public MappingTestEntity() {
    }

    /**
     * Returns the entity id.
     *
     * @return the id
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the entity id.
     *
     * @param id the id
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the transient field (never stored).
     *
     * @return the transient field
     */
    public String getTransientField() {
        return transientField;
    }

    /**
     * Sets the transient field.
     *
     * @param transientField the value
     */
    public void setTransientField(String transientField) {
        this.transientField = transientField;
    }

    /**
     * Returns the custom tag (SERDE strategy).
     *
     * @return the tag
     */
    public CustomTag getTag() {
        return tag;
    }

    /**
     * Sets the custom tag.
     *
     * @param tag the tag
     */
    public void setTag(CustomTag tag) {
        this.tag = tag;
    }

    /**
     * Returns the book reference (ENTITY_ID_REF strategy).
     *
     * @return the book ref
     */
    public Book getBookRef() {
        return bookRef;
    }

    /**
     * Sets the book reference.
     *
     * @param bookRef the book
     */
    public void setBookRef(Book bookRef) {
        this.bookRef = bookRef;
    }
}
