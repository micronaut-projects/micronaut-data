package example

import io.micronaut.data.annotation.Relation
import io.micronaut.serde.annotation.Serdeable

@Serdeable
class Child {

    private String firstName;
    private String gender;
    private int grade;
    @Relation(value = Relation.Kind.ONE_TO_MANY)
    private List<Pet> pets = new ArrayList<>();

    String getFirstName() {
        return firstName
    }

    void setFirstName(String firstName) {
        this.firstName = firstName
    }

    String getGender() {
        return gender
    }

    void setGender(String gender) {
        this.gender = gender
    }

    int getGrade() {
        return grade
    }

    void setGrade(int grade) {
        this.grade = grade
    }

    List<Pet> getPets() {
        return pets
    }

    void setPets(List<Pet> pets) {
        this.pets = pets
    }
}
