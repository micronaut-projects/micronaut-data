package io.micronaut.data.processor.visitors

class CustomIdSpec extends AbstractDataSpec {

    void 'test compile repo with custom ID'() {
        when:
        def repo = buildRepository('test.MyInterface', """

import io.micronaut.data.tck.entities.Task;

@Repository
interface MyInterface extends CrudRepository<Task, Long> {

}
""")

        then:
        repo != null
    }
}
