drop table author_genre;
drop table author;
drop table genre;

create table author
(
    id   bigserial primary key,
    name text not null
);


create table genre
(
    id   bigserial primary key,
    name text not null
);

create table author_genre
(
    author_id bigint not null references author (id) on delete cascade,
    genre_id  bigint not null references genre (id) on delete cascade,
    unique (author_id, genre_id)
);

insert into author (name) values ('Stephen King');
insert into author (name) values ('William Shakespeare');
insert into author (name) values ('Dan Brown');
insert into `genre` (name) values ('Horror'), ('Thriller'), ('Comedy'), ('Mystery');
insert into `author_genre` (author_id, genre_id) values (1, 1), (1, 2);
insert into `author_genre` (author_id, genre_id) values (2, 3);
insert into `author_genre` (author_id, genre_id) values (3, 2), (3, 4);
