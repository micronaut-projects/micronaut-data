package example

import grails.gorm.services.Service

@Service(Book)
interface BookRepository {

    Book findByTitle(String title)

}