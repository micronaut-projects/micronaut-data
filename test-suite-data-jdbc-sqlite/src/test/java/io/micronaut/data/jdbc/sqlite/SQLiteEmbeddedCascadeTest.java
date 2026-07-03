package io.micronaut.data.jdbc.sqlite;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SQLiteEmbeddedCascadeTest {

    @Test
    void testEmbeddedCascade() {
        try (ApplicationContext applicationContext = ApplicationContext.run(createProperties())) {
            TemplateRepository templateRepository = applicationContext.getBean(TemplateRepository.class);

            Template template = new Template();
            template.setName("Template test");

            Tag tag = new Tag();
            TagPk tagPk = new TagPk();
            tagPk.setTag("New tag");
            tagPk.setTemplate(template);
            tag.setId(tagPk);

            template.getTags().add(tag);

            Template saved = templateRepository.save(template);
            Template loaded = templateRepository.findById(saved.getId()).orElseThrow();

            assertNotNull(loaded);
            assertEquals(1, loaded.getTags().size());
        }
    }

    private static Map<String, Object> createProperties() {
        try {
            var databaseFile = Files.createTempFile("sqliteembeddedcascade", ".sqlite").toFile();
            databaseFile.deleteOnExit();
            Map<String, Object> properties = new HashMap<>();
            properties.put("datasources.default.url", "jdbc:sqlite:" + databaseFile.getAbsolutePath());
            properties.put("datasources.default.schema-generate", "CREATE");
            properties.put("datasources.default.dialect", "SQLITE");
            properties.put("datasources.default.db-type", "sqlite");
            properties.put("datasources.default.username", "");
            properties.put("datasources.default.password", "");
            properties.put("datasources.default.packages", "io.micronaut.data.jdbc.sqlite,io.micronaut.data.tck.entities,io.micronaut.data.tck.jdbc.entities");
            properties.put("datasources.default.driverClassName", "org.sqlite.JDBC");
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create SQLite test database", e);
        }
    }
}

@JdbcRepository(dialect = Dialect.SQLITE)
interface TemplateRepository extends CrudRepository<Template, Long> {

    @Join("tags")
    @Override
    Optional<Template> findById(Long id);
}

@JdbcRepository(dialect = Dialect.SQLITE)
interface TagRepository extends CrudRepository<Tag, TagPk> {
}

@MappedEntity
class Template {

    @Id
    @GeneratedValue
    private Long id;

    @NonNull
    private String name;

    @OneToMany(mappedBy = "id.template", cascade = CascadeType.ALL)
    private Set<Tag> tags = new HashSet<>();

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    Set<Tag> getTags() {
        return tags;
    }

    void setTags(Set<Tag> tags) {
        this.tags = tags;
    }
}

@MappedEntity
class Tag {

    @EmbeddedId
    private TagPk id;

    TagPk getId() {
        return id;
    }

    void setId(TagPk id) {
        this.id = id;
    }
}

@Embeddable
class TagPk implements Serializable {

    @NonNull
    @Column(name = "tag")
    private String tag;

    @ManyToOne
    @JoinColumn(name = "template_id")
    @Column(name = "template_id")
    private Template template;

    String getTag() {
        return tag;
    }

    void setTag(String tag) {
        this.tag = tag;
    }

    Template getTemplate() {
        return template;
    }

    void setTemplate(Template template) {
        this.template = template;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TagPk tagPk)) {
            return false;
        }
        return Objects.equals(tag, tagPk.tag) && Objects.equals(template, tagPk.template);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tag, template);
    }
}
