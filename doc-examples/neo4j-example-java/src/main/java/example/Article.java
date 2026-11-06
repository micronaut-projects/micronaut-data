package example;

// tag::article[]
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.graph.annotation.Node;

@Node
@Index(columns = "author") // <1>
public class Article {

    @Id // <2>
    private final String slug;

    private String title;

    private final String author;

    @Version // <3>
    private Long version;

    public Article(String slug, String title, String author) {
        this.slug = slug;
        this.title = title;
        this.author = author;
    }
    // end::article[]

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
// tag::article[]
}
// end::article[]
