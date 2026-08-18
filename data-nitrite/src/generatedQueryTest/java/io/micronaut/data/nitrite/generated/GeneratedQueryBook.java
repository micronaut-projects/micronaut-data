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
package io.micronaut.data.nitrite.generated;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;

import java.io.Serializable;

/**
 * Entity behind the generated-query fallback suite. Carries one field per operand shape the
 * fallback parser has to handle: a string for equality and {@code LIKE}, a number for the
 * ordering and {@code BETWEEN} comparisons, a nullable field for {@code IS NULL}, and an
 * association for dotted field references, and an embedded value whose own field carries a
 * {@code @MappedProperty} rename, so a dotted {@code ORDER BY} path has to be resolved segment by
 * segment rather than passed through verbatim.
 */
@MappedEntity("generated_query_books")
public class GeneratedQueryBook {

  @Id
  @GeneratedValue
  private String id;

  private String title;

  private int pages;

  private @Nullable String genre;

  @Relation(Relation.Kind.MANY_TO_ONE)
  private @Nullable GeneratedQueryAuthor author;

  @Relation(Relation.Kind.EMBEDDED)
  private @Nullable Edition edition;

  /** Default constructor used by the data runtime. */
  public GeneratedQueryBook() {
  }

  /**
   * Creates a book.
   *
   * @param title  book title
   * @param pages  page count
   * @param genre  genre, or {@code null} to leave the field unset
   * @param author owning author, or {@code null} for an unattributed book
   */
  public GeneratedQueryBook(String title, int pages, @Nullable String genre, @Nullable GeneratedQueryAuthor author) {
    this.title = title;
    this.pages = pages;
    this.genre = genre;
    this.author = author;
  }

  /**
   * Creates a book with an edition.
   *
   * @param title   book title
   * @param pages   page count
   * @param genre   genre, or {@code null} to leave the field unset
   * @param author  owning author, or {@code null} for an unattributed book
   * @param edition embedded edition
   */
  public GeneratedQueryBook(String title, int pages, @Nullable String genre,
                            @Nullable GeneratedQueryAuthor author, @Nullable Edition edition) {
    this(title, pages, genre, author);
    this.edition = edition;
  }

  /**
   * Returns the book identifier.
   *
   * @return id
   */
  public String getId() {
    return id;
  }

  /**
   * Sets the book identifier.
   *
   * @param id new id
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * Returns the book title.
   *
   * @return title
   */
  public String getTitle() {
    return title;
  }

  /**
   * Sets the book title.
   *
   * @param title new title
   */
  public void setTitle(String title) {
    this.title = title;
  }

  /**
   * Returns the page count.
   *
   * @return pages
   */
  public int getPages() {
    return pages;
  }

  /**
   * Sets the page count.
   *
   * @param pages new page count
   */
  public void setPages(int pages) {
    this.pages = pages;
  }

  /**
   * Returns the genre, which may be unset.
   *
   * @return genre or {@code null}
   */
  public @Nullable String getGenre() {
    return genre;
  }

  /**
   * Sets the genre.
   *
   * @param genre new genre
   */
  public void setGenre(@Nullable String genre) {
    this.genre = genre;
  }

  /**
   * Returns the owning author, which may be unset.
   *
   * @return author or {@code null}
   */
  public @Nullable GeneratedQueryAuthor getAuthor() {
    return author;
  }

  /**
   * Sets the owning author.
   *
   * @param author new author
   */
  public void setAuthor(@Nullable GeneratedQueryAuthor author) {
    this.author = author;
  }

  /**
   * Returns the embedded edition, which may be unset.
   *
   * @return edition or {@code null}
   */
  public @Nullable Edition getEdition() {
    return edition;
  }

  /**
   * Sets the embedded edition.
   *
   * @param edition new edition
   */
  public void setEdition(@Nullable Edition edition) {
    this.edition = edition;
  }

  /**
   * Embedded value whose {@code label} is persisted under a different name, so a dotted sort path
   * only resolves if every segment is mapped rather than the path being used verbatim.
   */
  @Embeddable
  public static class Edition implements Serializable {

    @MappedProperty("label_text")
    private String label;

    /** Default constructor used by the data runtime. */
    public Edition() {
    }

    /**
     * Creates an edition.
     *
     * @param label edition label
     */
    public Edition(String label) {
      this.label = label;
    }

    /**
     * Returns the edition label.
     *
     * @return label
     */
    public String getLabel() {
      return label;
    }

    /**
     * Sets the edition label.
     *
     * @param label new label
     */
    public void setLabel(String label) {
      this.label = label;
    }
  }
}
