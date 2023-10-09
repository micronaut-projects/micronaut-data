insert into products(id, code, name) values(1, 'p101', 'Apple MacBook Pro') ON CONFLICT DO NOTHING;
insert into products(id, code, name) values(2, 'p102', 'Sony TV') ON CONFLICT DO NOTHING;
