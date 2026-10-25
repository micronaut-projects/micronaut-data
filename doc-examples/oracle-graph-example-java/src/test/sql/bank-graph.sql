CREATE TABLE accounts (id NUMBER PRIMARY KEY, name VARCHAR2(100));
CREATE TABLE transfers (id NUMBER PRIMARY KEY, src NUMBER REFERENCES accounts (id), dst NUMBER REFERENCES accounts (id), amount NUMBER(10, 2));
INSERT INTO accounts VALUES (1, 'Alice');
INSERT INTO accounts VALUES (2, 'Bob');
INSERT INTO accounts VALUES (3, 'Carol');
INSERT INTO accounts VALUES (4, 'Dave');
INSERT INTO transfers VALUES (1, 1, 2, 500);
INSERT INTO transfers VALUES (2, 2, 3, 1500);
INSERT INTO transfers VALUES (3, 1, 3, 50);
-- tag::graph[]
CREATE PROPERTY GRAPH bank_graph
  VERTEX TABLES (accounts KEY (id) LABEL Account PROPERTIES (id, name))
  EDGE TABLES (transfers KEY (id)
    SOURCE KEY (src) REFERENCES accounts (id)
    DESTINATION KEY (dst) REFERENCES accounts (id)
    LABEL TRANSFERS PROPERTIES (id, amount));
-- end::graph[]
