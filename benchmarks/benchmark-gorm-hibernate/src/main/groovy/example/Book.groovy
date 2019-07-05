package example

import grails.gorm.annotation.Entity

@Entity
class Book {

    Book(String title, int pages) {
        this.title = title
        this.pages = pages
    }

    Book() {
    }

    String title
    int pages
}
