
package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;

@MappedEntity
public class Book {
    @Id
    @GeneratedValue
    private Long id;
    private String title;
    private int pages;
    @TypeDef(type = DataType.INTEGER_ARRAY, converter = OracleIntArrayConverter.class)
    private int[] yearsReleased;
    @TypeDef(type = DataType.INTEGER_ARRAY, converter = OracleIntArrayConverter.class)
    private int[] yearsBestBook;

    public Book(String title, int pages) {
        this.title = title;
        this.pages = pages;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public int getPages() {
        return pages;
    }

    public int[] getYearsReleased() {
        return yearsReleased;
    }

    public void setYearsReleased(int[] yearsReleased) {
        this.yearsReleased = yearsReleased;
    }

    public int[] getYearsBestBook() {
        return yearsBestBook;
    }

    public void setYearsBestBook(int[] yearsBestBook) {
        this.yearsBestBook = yearsBestBook;
    }
}
