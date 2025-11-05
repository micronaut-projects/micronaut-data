grammar JDQL;

// Copied from https://github.com/eclipse-jnosql/jnosql/blob/58917799e77bca640f73fc8cecc1fb7815ea5ba5/jnosql-communication/jnosql-communication-query/antlr4/org/eclipse/jnosql/query/grammar/data/JDQL.g4

statement : select_statement | update_statement | delete_statement;

select_statement : select_clause? from_clause? where_clause? orderby_clause?;
update_statement : UPDATE entity_name set_clause where_clause?;
delete_statement : DELETE from_clause where_clause?;

from_clause : FROM entity_name;

where_clause : WHERE conditional_expression;

set_clause : SET update_item (COMMA update_item)*;
update_item : state_field_path_expression EQ (scalar_expression | NULL);

select_clause : SELECT select_list;
select_list
    : state_field_path_expression (COMMA state_field_path_expression)*
    | aggregate_expression
    ;
aggregate_expression : COUNT '(' THIS ')';

orderby_clause : ORDER BY orderby_item (COMMA orderby_item)*;
orderby_item : state_field_path_expression (ASC | DESC)?;

conditional_expression
    // highest to lowest precedence
    : LPAREN conditional_expression RPAREN
    | null_comparison_expression
    | in_expression
    | between_expression
    | like_expression
    | comparison_expression
    | NOT conditional_expression
    | conditional_expression AND conditional_expression
    | conditional_expression OR conditional_expression
    ;

comparison_expression : scalar_expression comparison_operator scalar_expression;
comparison_operator : EQ | GT | GTEQ | LT | LTEQ | NEQ;

between_expression : scalar_expression NOT? BETWEEN scalar_expression AND scalar_expression;
like_expression : scalar_expression NOT? LIKE (STRING | input_parameter);

in_expression : state_field_path_expression NOT? IN '(' in_item (',' in_item)* ')';
in_item : literal | enum_literal | input_parameter; // could simplify to just literal

null_comparison_expression : state_field_path_expression IS NOT? NULL;

scalar_expression
    // highest to lowest precedence
    : LPAREN scalar_expression RPAREN
    | primary_expression
    | scalar_expression MUL scalar_expression
    | scalar_expression DIV scalar_expression
    | scalar_expression PLUS scalar_expression
    | scalar_expression MINUS scalar_expression
    | scalar_expression CONCAT scalar_expression
    ;

primary_expression
    : function_expression
    | special_expression
    | state_field_path_expression
    | enum_literal
    | input_parameter
    | literal
    ;

function_expression
    : ('abs(' | 'ABS(') scalar_expression ')'
    | ('length(' | 'LENGTH(') scalar_expression ')'
    | ('lower(' | 'LOWER(') scalar_expression ')'
    | ('upper(' | 'UPPER(') scalar_expression ')'
    | ('left(' | 'LEFT(') scalar_expression ',' scalar_expression ')'
    | ('right(' | 'RIGHT(') scalar_expression ',' scalar_expression ')'
    ;

special_expression
    : LOCAL_DATE
    | LOCAL_DATETIME
    | LOCAL_TIME
    | TRUE
    | FALSE
    ;

state_field_path_expression : IDENTIFIER (DOT IDENTIFIER)* | FULLY_QUALIFIED_IDENTIFIER;

entity_name : IDENTIFIER; // no ambiguity

enum_literal : IDENTIFIER (DOT IDENTIFIER)* | FULLY_QUALIFIED_IDENTIFIER; // ambiguity with state_field_path_expression resolvable semantically

input_parameter : COLON IDENTIFIER | QUESTION INTEGER;

literal : STRING | INTEGER | DOUBLE | FLOAT;

// Tokens defined to be case-insensitive using character classes
SELECT          : [sS][eE][lL][eE][cC][tT];
UPDATE          : [uU][pP][dD][aA][tT][eE];
DELETE          : [dD][eE][lL][eE][tT][eE];
FROM            : [fF][rR][oO][mM];
WHERE           : [wW][hH][eE][rR][eE];
SET             : [sS][eE][tT];
ORDER           : [oO][rR][dD][eE][rR];
BY              : [bB][yY];
NOT             : [nN][oO][tT];
IN              : [iI][nN];
IS              : [iI][sS];
NULL            : [nN][uU][lL][lL];
COUNT           : [cC][oO][uU][nN][tT];
TRUE            : [tT][rR][uU][eE];
FALSE           : [fF][aA][lL][sS][eE];
ASC             : [aA][sS][cC];
DESC            : [dD][eE][sS][cC];
AND             : [aA][nN][dD];
OR              : [oO][rR];
LOCAL_DATE      : [lL][oO][cC][aA][lL] [dD][aA][tT][eE];
LOCAL_DATETIME  : [lL][oO][cC][aA][lL] [dD][aA][tT][eE][tT][iI][mM][eE];
LOCAL_TIME      : [lL][oO][cC][aA][lL] [tT][iI][mM][eE];
BETWEEN         : [bB][eE][tT][wW][eE][eE][nN];
LIKE            : [lL][iI][kK][eE];
THIS            : [tT][hH][iI][sS];
LOCAL           : [lL][oO][cC][aA][lL];
DATE            : [dD][aA][tT][eE];
DATETIME        : [dD][aA][tT][eE][tT][iI][mM][eE];
TIME            : [tT][iI][mM][eE];

// Operators
EQ              : '=';
GT              : '>';
LT              : '<';
NEQ             : '<>';
GTEQ            : '>=';
LTEQ            : '<=';
PLUS            : '+';
MINUS           : '-';
MUL             : '*';
DIV             : '/';
CONCAT          : '||';

// Special Characters
COMMA           : ',';
DOT             : '.';
LPAREN          : '(';
RPAREN          : ')';
COLON           : ':';
QUESTION        : '?';

// Identifier and literals
FULLY_QUALIFIED_IDENTIFIER : [a-zA-Z_][a-zA-Z0-9_]* (DOT [a-zA-Z_][a-zA-Z0-9_]*)+;
IDENTIFIER                 : [a-zA-Z_][a-zA-Z0-9_]*;
STRING                     : '\'' ( ~('\'' | '\\') | '\\' . | '\'\'' )* '\''  // single quoted strings with embedded single quotes handled
                          | '"' ( ~["\\] | '\\' . )* '"' ;  // double quoted strings
INTEGER                    : '-'?[0-9]+;
DOUBLE                     : '-'?[0-9]+'.'[0-9]*?'d' | '-'?'.'[0-9]+?'d';
FLOAT                     : '-'?[0-9]+'.'[0-9]*?'f' | '-'?'.'[0-9]+?'f';

// Whitespace and Comments
WS              : [ \t\r\n]+ -> skip ;
LINE_COMMENT    : '//' ~[\r\n]* -> skip;
BLOCK_COMMENT   : '/*' .*? '*/' -> skip;
