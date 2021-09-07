create sequence BOOK_SEQ;

create type num_varray is varray(1000) of integer;
/
create table BOOK (
    id NUMBER(19) NOT NULL PRIMARY KEY,
    title VARCHAR2(200) NOT NULL,
    pages NUMBER(19) NOT NULL,
    years_released num_varray,
    years_best_book num_varray
);