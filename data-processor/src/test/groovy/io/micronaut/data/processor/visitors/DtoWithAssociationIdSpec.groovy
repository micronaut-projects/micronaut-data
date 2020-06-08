package io.micronaut.data.processor.visitors

import io.micronaut.data.annotation.Query
import io.micronaut.inject.BeanDefinition

class DtoWithAssociationIdSpec extends AbstractDataSpec {
    void "test DTO with an association id doesn't fail to compile"() {
        when:
        BeanDefinition beanDefinition = buildRepository('test.FaceInterface', """
import io.micronaut.data.model.entities.Person;
import io.micronaut.core.annotation.Introspected;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import io.micronaut.data.annotation.Join;

@Entity
class Nose {

    @GeneratedValue
    @Id
    private Long id;

    @OneToOne
    private Face face;

    public Face getFace() {
        return face;
    }

    public void setFace(Face face) {
        this.face = face;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}

@Entity
class Face {

    @GeneratedValue
    @Id
    private Long id;
    private String name;

    public Face(String name) {
        this.name = name;
    }
    @OneToOne(mappedBy = "face")
    private Nose nose;

    public String getName() {
        return name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Nose getNose() {
        return nose;
    }

    public void setNose(Nose nose) {
        this.nose = nose;
    }
}

@Repository
interface FaceInterface extends GenericRepository<Face, Long> {
    FaceDTO get(Long id);
}

@Introspected
class FaceDTO {

    private Long id;
    private String name;
    private Long noseId;
    public Long getNoseId() {
        return noseId;
    }

    public void setNoseId(Long noseId) {
        this.noseId = noseId;
    }
   
    public FaceDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
""")
        then:
        beanDefinition != null

        when:
        Map<String, Object> signature = ["id": Long]
        def method = beanDefinition.getRequiredMethod("get", signature.values() as Class[])
        String query = method.synthesize(Query)value()

        then:
        "SELECT face_nose_.id AS noseId,face_.id AS id,face_.name AS name FROM test.Face AS face_ JOIN FETCH face_.nose face_nose_ WHERE (face_.id = :p1)" == query

    }
}
