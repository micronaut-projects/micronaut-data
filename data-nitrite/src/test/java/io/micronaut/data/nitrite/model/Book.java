package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * Simple book entity used by the Nitrite documentation examples.
 */
@MappedEntity("books")
public class Book {

  @Id
  @GeneratedValue
  private String id;

  private String title;

  /** Default constructor used by the data runtime. */
  public Book() {}

  /**
   * Creates a book with the supplied title.
   *
   * @param title title to set
   */
  public Book(String title) {
    this.title = title;
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
}
