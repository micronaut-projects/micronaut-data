package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity("my_main_entity")
public class MyMainEntity {

    @Id
    private Long id;

    @GeneratedValue
    private String example;

    private String value;

    @Relation(value = Relation.Kind.EMBEDDED)
    private MyPart part = new MyPart();

    public MyMainEntity() {
    }

    public MyMainEntity(Long id, String example, String value, MyPart part) {
        this.id = id;
        this.example = example;
        this.value = value;
        this.part = part;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getExample() {
        return example;
    }

    public void setExample(String example) {
        this.example = example;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public MyPart getPart() {
        return part;
    }

    public void setPart(MyPart part) {
        this.part = part;
    }
}
