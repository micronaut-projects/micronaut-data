package io.micronaut.data.jdbc.h2


import io.micronaut.data.tck.entities.Application
import io.micronaut.data.tck.entities.Template
import io.micronaut.data.tck.entities.Question
import io.micronaut.data.tck.entities.QuestionId
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
@H2DBProperties
class H2TemplateQuestionsSpec extends Specification {

    @Inject
    NewTemplateRepository templateRepository

    @Inject
    @Shared
    QuestionRepository questionRepository

    @Inject
    ApplicationRepository appRepository

    void "test join"() {
        given:
            def template = new Template()
            template.setEnabled(true)
            templateRepository.save(template)
            def questionId = new QuestionId();
            questionId.setNumber(1)
            questionId.setTemplate(template)
            def question = new Question()
            question.setId(questionId)
            question.setText("How are you?")
            questionRepository.save(question)

            def app = new Application()
            app.setTemplate(template)
            appRepository.save(app)
        when:
            def optTemplateLoadedById = templateRepository.findById(template.getId())
            def optTemplateLoadedByAppId = appRepository.findTemplateById(app.getId())
        then:
            optTemplateLoadedById.present == true
            optTemplateLoadedById.get().questions.size() > 0
            optTemplateLoadedByAppId.present == true
            optTemplateLoadedByAppId.get().questions.size() > 0
    }

}
