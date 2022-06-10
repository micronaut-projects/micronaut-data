
package example

import javax.persistence.*

@Entity
class Manufacturer {

    @Id
    @GeneratedValue
    Long id
    String name
}
