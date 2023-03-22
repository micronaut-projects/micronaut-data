package io.micronaut.data.r2dbc.h2

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.NonNull
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import reactor.core.publisher.Mono
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

@MicronautTest(transactional = false)
class H2EmbeddedCascadeSpec extends Specification implements H2TestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    TemplateRepository templateRepository = applicationContext.getBean(TemplateRepository)

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

            templateRepository.save(template).block()
            template = templateRepository.findById(template.id).blockOptional().get()
        then:
            template
            template.tags.size() == 1
    }

}

@R2dbcRepository(dialect = Dialect.H2)
interface TemplateRepository extends ReactorCrudRepository<Template, Long> {

    @Join("tags")
    @Override
    Mono<Template> findById(Long aLong);
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

