grammar JQ;

// Adapted from the Jakarta Query 1.0 core grammar:
// https://jakarta.ee/specifications/query/1.0/jakarta-query-1.0-m1

statement : (select_statement | update_statement | delete_statement) EOF;

select_statement : select_clause? from_clause? where_clause? orderby_clause?;
update_statement : update_clause set_clause where_clause?;
delete_statement : delete_clause where_clause?;

from_clause : FROM entity_name (AS? identification_variable)?;

where_clause : WHERE conditional_expression;

update_clause : UPDATE entity_name (AS? identification_variable)?;
set_clause : SET update_item (COMMA update_item)*;
update_item : simple_path_expression EQ new_value;
new_value
    : scalar_expression
    | NULL
    ;

delete_clause : DELETE FROM entity_name (AS? identification_variable)?;

select_clause : SELECT DISTINCT? select_item (COMMA select_item)*;
select_item : select_expression (AS? result_variable)?;
select_expression
    : aggregate_expression
    | scalar_expression
    ;

aggregate_expression
    : aggregate_function LPAREN DISTINCT? aggregate_argument RPAREN
    | COUNT LPAREN DISTINCT? aggregate_argument RPAREN
    ;
aggregate_function : AVG | MAX | MIN | SUM;
aggregate_argument
    : THIS
    | id_expression
    | simple_path_expression
    ;

orderby_clause : ORDER BY orderby_item (COMMA orderby_item)*;
orderby_item : orderby_expression (ASC | DESC)? (NULLS (FIRST | LAST))?;
orderby_expression
    : simple_path_expression
    | id_expression
    ;

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
like_expression : scalar_expression NOT? LIKE escaped_pattern;
escaped_pattern
    : literal_pattern (ESCAPE escape_character)?
    | input_parameter
    ;

in_expression : scalar_expression NOT? IN in_item_list;
in_item_list : input_parameter | in_item_list_many ;
in_item_list_many : LPAREN in_item (COMMA in_item)* RPAREN ;
in_item : literal | enum_literal | input_parameter;

null_comparison_expression : scalar_expression IS NOT? NULL;

scalar_expression
    // highest to lowest precedence
    : LPAREN scalar_expression RPAREN
    | primary_expression
    | (PLUS | MINUS) scalar_expression
    | scalar_expression (MUL | DIV) scalar_expression
    | scalar_expression (PLUS | MINUS) scalar_expression
    | scalar_expression CONCAT scalar_expression
    ;

primary_expression
    : function_expression
    | special_expression
    | id_expression
    | simple_path_expression
    | enum_literal
    | input_parameter
    | literal
    ;

id_expression : IDENTIFIER LPAREN THIS RPAREN;

function_expression : IDENTIFIER LPAREN scalar_expression (COMMA scalar_expression)* RPAREN;

special_expression
    : special_boolean_expression
    | special_datetime_expression
    ;

special_boolean_expression
    : TRUE
    | FALSE
    ;

special_datetime_expression
    : LOCAL_DATE
    | LOCAL_TIME
    | LOCAL_DATETIME
    ;

simple_path_expression : IDENTIFIER (DOT IDENTIFIER)*;

entity_name : IDENTIFIER; // no ambiguity

identification_variable : IDENTIFIER;

result_variable : IDENTIFIER;

enum_literal : IDENTIFIER (DOT IDENTIFIER)*; // ambiguity with simple_path_expression resolvable semantically

input_parameter : COLON parameter_name | QUESTION INTEGER;
parameter_name
    : IDENTIFIER
    | COUNT
    | AVG
    | MAX
    | MIN
    | SUM
    ;

literal : string_literal | numeric_literal;
numeric_literal : INTEGER | DOUBLE;
string_literal : STRING;
literal_pattern : STRING;
escape_character : STRING;

// Tokens defined to be case-insensitive using character classes
SELECT          : [sS][eE][lL][eE][cC][tT];
DISTINCT        : [dD][iI][sS][tT][iI][nN][cC][tT];
UPDATE          : [uU][pP][dD][aA][tT][eE];
DELETE          : [dD][eE][lL][eE][tT][eE];
FROM            : [fF][rR][oO][mM];
WHERE           : [wW][hH][eE][rR][eE];
SET             : [sS][eE][tT];
AS              : [aA][sS];
ORDER           : [oO][rR][dD][eE][rR];
BY              : [bB][yY];
NOT             : [nN][oO][tT];
IN              : [iI][nN];
IS              : [iI][sS];
NULL            : [nN][uU][lL][lL];
NULLS           : [nN][uU][lL][lL][sS];
COUNT           : [cC][oO][uU][nN][tT];
AVG             : [aA][vV][gG];
MAX             : [mM][aA][xX];
MIN             : [mM][iI][nN];
SUM             : [sS][uU][mM];
TRUE            : [tT][rR][uU][eE];
FALSE           : [fF][aA][lL][sS][eE];
ASC             : [aA][sS][cC];
DESC            : [dD][eE][sS][cC];
FIRST           : [fF][iI][rR][sS][tT];
LAST            : [lL][aA][sS][tT];
AND             : [aA][nN][dD];
OR              : [oO][rR];
LOCAL_DATETIME  : [lL][oO][cC][aA][lL] SPACES [dD][aA][tT][eE][tT][iI][mM][eE];
LOCAL_DATE      : [lL][oO][cC][aA][lL] SPACES [dD][aA][tT][eE];
LOCAL_TIME      : [lL][oO][cC][aA][lL] SPACES [tT][iI][mM][eE];
BETWEEN         : [bB][eE][tT][wW][eE][eE][nN];
LIKE            : [lL][iI][kK][eE];
ESCAPE          : [eE][sS][cC][aA][pP][eE];
THIS            : [tT][hH][iI][sS];

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
IDENTIFIER                 : [a-zA-Z_][a-zA-Z0-9_]*;
DOUBLE                     : DIGITS DOT DIGITS? EXPONENT? FLOAT_SUFFIX?
                           | DOT DIGITS EXPONENT? FLOAT_SUFFIX?
                           | DIGITS EXPONENT FLOAT_SUFFIX?
                           | DIGITS FLOAT_SUFFIX
                           ;
INTEGER                    : DIGITS INTEGER_SUFFIX?;
STRING                     : '\'' ( ~'\'' | '\'\'' )* '\''
                           | '"' ( ~["\\] | '\\' . )* '"'
                           ;

fragment DIGITS           : [0-9] ([_]? [0-9])*;
fragment SPACES           : [ \t\r\n]+;
fragment EXPONENT         : [eE] [+-]? DIGITS;
fragment FLOAT_SUFFIX     : [fFdD];
fragment INTEGER_SUFFIX   : [lL];

// Whitespace and Comments
WS              : [ \t\r\n]+ -> skip ;
LINE_COMMENT    : '//' ~[\r\n]* -> skip;
BLOCK_COMMENT   : '/*' .*? '*/' -> skip;
