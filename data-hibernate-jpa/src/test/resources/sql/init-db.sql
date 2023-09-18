create table products
(
    id   int          not null,
    code varchar(255) not null,
    name varchar(255) not null,
    primary key (id),
    unique (code)
);
