package io.micronaut.data.jdbc.h2

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.NonNull
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany

@MicronautTest
@H2DBProperties
class H2EmbeddedCascadeSpec extends Specification implements H2TestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    TemplateRepository templateRepository = applicationContext.getBean(TemplateRepository)

    @Shared
    @Inject
    TagRepository tagRepository = applicationContext.getBean(TagRepository)

    void "test embedded cascade"() {
        when:
            Template template = new Template()
            template.name = "Template test"

            Tag tag = new Tag()
            TagPK tagPK = new TagPK()
            tagPK.tag = "New tag"
            tagPK.template = template
            tag.id = tagPK

            template.tags << tag

            templateRepository.save(template)
            template = templateRepository.findById(template.id).get()
        then:
            template
            template.tags.size() == 1
    }

}

@JdbcRepository(dialect = Dialect.H2)
interface TemplateRepository extends CrudRepository<Template, Long> {

    @Join("tags")
    @Override
    Optional<Template> findById(Long aLong);
}

@JdbcRepository(dialect = Dialect.H2)
interface TagRepository extends CrudRepository<Tag, TagPK> {
}

@MappedEntity
class Template {

    @Id
    @GeneratedValue
    Long id

    @NonNull
    String name

    @OneToMany(mappedBy = "id.template", cascade = CascadeType.ALL)
    Set<Tag> tags = [] as Set

}

@MappedEntity
class Tag {

    @EmbeddedId
    TagPK id

}

@Embeddable
class TagPK implements Serializable {

    @NonNull
    @Column(name = "tag")
    String tag

    @ManyToOne
    @JoinColumn(name = "template_id")
    @Column(name = "template_id")
    Template template

    @Override
    boolean equals(o) {
        if (this.is(o)) {
            return true
        }
        if (getClass() != o.class) {
            return false
        }

        TagPK that = (TagPK) o

        if (tag != that.tag) {
            return false
        }
        return template == that.template
    }

    @Override
    int hashCode() {
        int result
        result = tag.hashCode()
        result = 31 * result + (template != null ? template.hashCode() : 0)
        return result
    }
}

