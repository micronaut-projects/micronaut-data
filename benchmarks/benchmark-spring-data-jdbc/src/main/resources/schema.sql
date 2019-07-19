drop table book if exists
create table book (id bigint not null auto_increment, pages integer not null, title varchar(255), primary key (id))
