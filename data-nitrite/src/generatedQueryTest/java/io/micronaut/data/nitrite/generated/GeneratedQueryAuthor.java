package io.micronaut.data.nitrite.generated;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

/**
 * Owning side of the association exercised by {@link GeneratedQueryBookRepository}, so the suite
 * can assert that a dotted reference such as {@code book_.author.id} in a generated predicate
 * resolves to the persisted document path.
 */
@MappedEntity("generated_query_authors")
public class GeneratedQueryAuthor {

  @Id
  @GeneratedValue
  private String id;

  private String name;

  /** Default constructor used by the data runtime. */
  public GeneratedQueryAuthor() {
  }

  /**
   * Creates an author with the supplied name.
   *
   * @param name author name
   */
  public GeneratedQueryAuthor(String name) {
    this.name = name;
  }

  /**
   * Returns the author identifier.
   *
   * @return id
   */
  public String getId() {
    return id;
  }

  /**
   * Sets the author identifier.
   *
   * @param id new id
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * Returns the author name.
   *
   * @return name
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the author name.
   *
   * @param name new name
   */
  public void setName(String name) {
    this.name = name;
  }
}
