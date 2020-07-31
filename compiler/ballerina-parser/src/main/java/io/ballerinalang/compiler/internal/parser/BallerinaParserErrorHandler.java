/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerinalang.compiler.internal.parser;

import io.ballerinalang.compiler.internal.parser.tree.STToken;
import io.ballerinalang.compiler.syntax.tree.SyntaxKind;
import io.ballerinalang.compiler.syntax.tree.SyntaxKind2;

import java.util.ArrayDeque;

/**
 * <p>
 * Responsible for recovering from a parser error.
 *
 * When an unexpected token is reached, error handler will try inserting/removing a token from the current head, and see
 * how far the parser can successfully progress. After fixing the current head and trying to progress, if it encounters
 * more errors, then it will try to fix those as well. All possible combinations of insertions and deletions will be
 * tried out for such errors. Once all possible paths are discovered, pick the optimal combination that leads to the
 * best recovery. Finally, apply the best solution and continue the parsing.
 * </p>
 * e.g.:
 * If the best combination of fixes was <code>[insert, insert, remove, remove]</code>, then apply only the first
 * fix and continue.
 * <ul>
 * <li>
 * If the fix was a ‘remove’ - then consume the token stream once, and continue from the same rule again.
 * </li>
 * <li>
 * If the fix was an ‘insert’ - then insert the missing node, and continue from the next rule, without consuming the
 * token stream.
 * </li>
 * </ul>
 *
 * @since 1.2.0
 */
public class BallerinaParserErrorHandler extends AbstractParserErrorHandler {

    /**
     * FUNC_DEF_OR_FUNC_TYPE --> When a func-def and func-type-desc are possible.
     * e.g: start of a module level construct that starts with 'function' keyword.
     */
    private static final int[] FUNC_TYPE_OR_DEF_OPTIONAL_RETURNS =
            { ParserRuleContext2.RETURNS_KEYWORD, ParserRuleContext2.FUNC_BODY_OR_TYPE_DESC_RHS };

    private static final int[] FUNC_BODY_OR_TYPE_DESC_RHS =
            { ParserRuleContext2.FUNC_BODY, ParserRuleContext2.MODULE_LEVEL_AMBIGUOUS_FUNC_TYPE_DESC_RHS };

    /**
     * FUNC_DEF --> When only function definitions are possible. eg: resource function.
     */
    private static final int[] FUNC_DEF_OPTIONAL_RETURNS =
            { ParserRuleContext2.RETURNS_KEYWORD, ParserRuleContext2.FUNC_BODY };

    private static final int[] FUNC_BODY =
            { ParserRuleContext2.FUNC_BODY_BLOCK, ParserRuleContext2.EXTERNAL_FUNC_BODY };

    private static final int[] OBJECT_FUNC_BODY =
            { ParserRuleContext2.SEMICOLON, ParserRuleContext2.EXTERNAL_FUNC_BODY };

    private static final int[] EXTERNAL_FUNC_BODY_OPTIONAL_ANNOTS =
            { ParserRuleContext2.EXTERNAL_KEYWORD, ParserRuleContext2.ANNOTATIONS };

    /**
     * ANNON_FUNC--> When a anonymous function is possible.
     */
    private static final int[] ANNON_FUNC_OPTIONAL_RETURNS =
            { ParserRuleContext2.RETURNS_KEYWORD, ParserRuleContext2.ANON_FUNC_BODY };

    private static final int[] ANON_FUNC_BODY =
            { ParserRuleContext2.FUNC_BODY_BLOCK, ParserRuleContext2.EXPLICIT_ANON_FUNC_EXPR_BODY_START };

    /**
     * FUNC_TYPE --> When a only function type is possible.
     */
    private static final int[] FUNC_TYPE_OPTIONAL_RETURNS =
            { ParserRuleContext2.RETURNS_KEYWORD, ParserRuleContext2.FUNC_TYPE_DESC_END };

    private static final int[] FUNC_TYPE_OR_ANON_FUNC_OPTIONAL_RETURNS =
            { ParserRuleContext2.RETURNS_KEYWORD, ParserRuleContext2.FUNC_TYPE_DESC_RHS_OR_ANON_FUNC_BODY };

    private static final int[] FUNC_TYPE_DESC_RHS_OR_ANON_FUNC_BODY =
            { ParserRuleContext2.ANON_FUNC_BODY, ParserRuleContext2.STMT_LEVEL_AMBIGUOUS_FUNC_TYPE_DESC_RHS };

    private static final int[] WORKER_NAME_RHS =
            { ParserRuleContext2.RETURNS_KEYWORD, ParserRuleContext2.BLOCK_STMT };

    // We add named-worker-decl also as a statement. This is because we let having a named-worker
    // in all places a statement can be added during parsing, but then validates it based on the
    // context after the parsing the node is complete. This is to provide better error messages.
    private static final int[] STATEMENTS = { ParserRuleContext2.CLOSE_BRACE,
            ParserRuleContext2.ASSIGNMENT_STMT, ParserRuleContext2.VAR_DECL_STMT, ParserRuleContext2.IF_BLOCK,
            ParserRuleContext2.WHILE_BLOCK, ParserRuleContext2.CALL_STMT, ParserRuleContext2.PANIC_STMT,
            ParserRuleContext2.CONTINUE_STATEMENT, ParserRuleContext2.BREAK_STATEMENT, ParserRuleContext2.RETURN_STMT,
            ParserRuleContext2.COMPOUND_ASSIGNMENT_STMT, ParserRuleContext2.LOCAL_TYPE_DEFINITION_STMT,
            ParserRuleContext2.EXPRESSION_STATEMENT, ParserRuleContext2.LOCK_STMT, ParserRuleContext2.BLOCK_STMT,
            ParserRuleContext2.NAMED_WORKER_DECL, ParserRuleContext2.FORK_STMT, ParserRuleContext2.FOREACH_STMT,
            ParserRuleContext2.XML_NAMESPACE_DECLARATION, ParserRuleContext2.TRANSACTION_STMT,
            ParserRuleContext2.RETRY_STMT, ParserRuleContext2.ROLLBACK_STMT, ParserRuleContext2.MATCH_STMT };

    private static final int[] VAR_DECL_RHS =
            { ParserRuleContext2.ASSIGN_OP, ParserRuleContext2.SEMICOLON };

    private static final int[] TOP_LEVEL_NODE = { ParserRuleContext2.EOF, ParserRuleContext2.DOC_STRING,
            ParserRuleContext2.ANNOTATIONS, ParserRuleContext2.TOP_LEVEL_NODE_WITHOUT_METADATA };

    private static final int[] TOP_LEVEL_NODE_WITHOUT_METADATA = { ParserRuleContext2.EOF,
            ParserRuleContext2.PUBLIC_KEYWORD, ParserRuleContext2.TOP_LEVEL_NODE_WITHOUT_MODIFIER };

    private static final int[] TOP_LEVEL_NODE_WITHOUT_MODIFIER =
            { ParserRuleContext2.EOF, ParserRuleContext2.VAR_DECL_STMT, ParserRuleContext2.FUNC_DEF_OR_FUNC_TYPE,
                    ParserRuleContext2.MODULE_TYPE_DEFINITION, ParserRuleContext2.SERVICE_DECL,
                    ParserRuleContext2.LISTENER_DECL, ParserRuleContext2.CONSTANT_DECL, ParserRuleContext2.ANNOTATION_DECL,
                    ParserRuleContext2.VAR_DECL_STMT, ParserRuleContext2.XML_NAMESPACE_DECLARATION,
                    ParserRuleContext2.MODULE_ENUM_DECLARATION, ParserRuleContext2.IMPORT_DECL };

    private static final int[] TYPE_OR_VAR_NAME =
            { ParserRuleContext2.VARIABLE_NAME, ParserRuleContext2.TYPE_DESC_IN_TYPE_BINDING_PATTERN };

    private static final int[] ASSIGNMENT_OR_VAR_DECL_SECOND_TOKEN =
            { ParserRuleContext2.ASSIGN_OP, ParserRuleContext2.VARIABLE_NAME };

    private static final int[] FIELD_DESCRIPTOR_RHS =
            { ParserRuleContext2.SEMICOLON, ParserRuleContext2.QUESTION_MARK, ParserRuleContext2.ASSIGN_OP };

    private static final int[] FIELD_OR_REST_DESCIPTOR_RHS =
            { ParserRuleContext2.ELLIPSIS, ParserRuleContext2.VARIABLE_NAME };

    private static final int[] RECORD_BODY_START =
            { ParserRuleContext2.CLOSED_RECORD_BODY_START, ParserRuleContext2.OPEN_BRACE };

    private static final int[] RECORD_BODY_END =
            { ParserRuleContext2.CLOSED_RECORD_BODY_END, ParserRuleContext2.CLOSE_BRACE };

    // Give object the higher priority over records, since record body is a subset of object body.
    // Array, optional and union type descriptors are not added to the list since they are left recursive.
    private static final int[] TYPE_DESCRIPTORS = { ParserRuleContext2.SIMPLE_TYPE_DESCRIPTOR,
            ParserRuleContext2.OBJECT_TYPE_DESCRIPTOR, ParserRuleContext2.RECORD_TYPE_DESCRIPTOR,
            ParserRuleContext2.NIL_TYPE_DESCRIPTOR, ParserRuleContext2.PARAMETERIZED_TYPE,
            ParserRuleContext2.ERROR_KEYWORD, ParserRuleContext2.STREAM_KEYWORD, ParserRuleContext2.TABLE_KEYWORD,
            ParserRuleContext2.FUNC_TYPE_DESC, ParserRuleContext2.PARENTHESISED_TYPE_DESC_START,
            ParserRuleContext2.CONSTANT_EXPRESSION, ParserRuleContext2.TUPLE_TYPE_DESC_START };

    private static final int[] RECORD_FIELD_OR_RECORD_END =
            { ParserRuleContext2.RECORD_BODY_END, ParserRuleContext2.RECORD_FIELD };

    private static final int[] RECORD_FIELD_START =
            { ParserRuleContext2.ANNOTATIONS, ParserRuleContext2.ASTERISK, ParserRuleContext2.TYPE_DESC_IN_RECORD_FIELD };

    private static final int[] RECORD_FIELD_WITHOUT_METADATA =
            { ParserRuleContext2.ASTERISK, ParserRuleContext2.TYPE_DESC_IN_RECORD_FIELD };

    private static final int[] ARG_START_OR_ARG_LIST_END =
            { ParserRuleContext2.ARG_LIST_END, ParserRuleContext2.ARG_START };

    private static final int[] ARG_START =
            { ParserRuleContext2.VARIABLE_NAME, ParserRuleContext2.ELLIPSIS, ParserRuleContext2.EXPRESSION };

    private static final int[] ARG_END = { ParserRuleContext2.CLOSE_PARENTHESIS, ParserRuleContext2.COMMA };

    private static final int[] NAMED_OR_POSITIONAL_ARG_RHS =
            { ParserRuleContext2.ARG_END, ParserRuleContext2.ASSIGN_OP };

    private static final int[] OBJECT_FIELD_RHS =
            { ParserRuleContext2.SEMICOLON, ParserRuleContext2.ASSIGN_OP };

    private static final int[] OBJECT_MEMBER_START =
            { ParserRuleContext2.DOC_STRING, ParserRuleContext2.ANNOTATIONS, ParserRuleContext2.ASTERISK,
                    ParserRuleContext2.OBJECT_FUNC_OR_FIELD, ParserRuleContext2.CLOSE_BRACE };

    private static final int[] OBJECT_MEMBER_WITHOUT_METADATA =
            { ParserRuleContext2.ASTERISK, ParserRuleContext2.OBJECT_FUNC_OR_FIELD, ParserRuleContext2.CLOSE_BRACE };

    private static final int[] OBJECT_FUNC_OR_FIELD = { ParserRuleContext2.PUBLIC_KEYWORD,
            ParserRuleContext2.PRIVATE_KEYWORD, ParserRuleContext2.OBJECT_FUNC_OR_FIELD_WITHOUT_VISIBILITY };

    private static final int[] OBJECT_FUNC_OR_FIELD_WITHOUT_VISIBILITY =
            { ParserRuleContext2.TYPE_DESC_BEFORE_IDENTIFIER, ParserRuleContext2.OBJECT_METHOD_START };

    private static final int[] OBJECT_METHOD_START =
            { ParserRuleContext2.REMOTE_KEYWORD, ParserRuleContext2.FUNCTION_KEYWORD };

    private static final int[] OBJECT_TYPE_DESCRIPTOR_START =
            { ParserRuleContext2.OBJECT_TYPE_QUALIFIER, ParserRuleContext2.OBJECT_KEYWORD };

    private static final int[] ELSE_BODY = { ParserRuleContext2.IF_BLOCK, ParserRuleContext2.OPEN_BRACE };

    private static final int[] ELSE_BLOCK =
            { ParserRuleContext2.ELSE_KEYWORD, ParserRuleContext2.STATEMENT };

    private static final int[] CALL_STATEMENT =
            { ParserRuleContext2.CHECKING_KEYWORD, ParserRuleContext2.VARIABLE_NAME };

    private static final int[] IMPORT_PREFIX_DECL =
            { ParserRuleContext2.AS_KEYWORD, ParserRuleContext2.SEMICOLON };

    private static final int[] IMPORT_VERSION =
            { ParserRuleContext2.VERSION_KEYWORD, ParserRuleContext2.AS_KEYWORD, ParserRuleContext2.SEMICOLON };

    private static final int[] IMPORT_DECL_RHS = { ParserRuleContext2.SLASH, ParserRuleContext2.DOT,
            ParserRuleContext2.VERSION_KEYWORD, ParserRuleContext2.AS_KEYWORD, ParserRuleContext2.SEMICOLON };

    private static final int[] AFTER_IMPORT_MODULE_NAME = { ParserRuleContext2.DOT,
            ParserRuleContext2.VERSION_KEYWORD, ParserRuleContext2.AS_KEYWORD, ParserRuleContext2.SEMICOLON };

    private static final int[] MAJOR_MINOR_VERSION_END =
            { ParserRuleContext2.DOT, ParserRuleContext2.AS_KEYWORD, ParserRuleContext2.SEMICOLON };

    private static final int[] RETURN_RHS = { ParserRuleContext2.SEMICOLON, ParserRuleContext2.EXPRESSION };

    private static final int[] EXPRESSION_START = { ParserRuleContext2.BASIC_LITERAL,
            ParserRuleContext2.NIL_LITERAL, ParserRuleContext2.VARIABLE_REF, ParserRuleContext2.ACCESS_EXPRESSION,
            ParserRuleContext2.TYPEOF_EXPRESSION, ParserRuleContext2.TRAP_KEYWORD, ParserRuleContext2.UNARY_EXPRESSION,
            ParserRuleContext2.CHECKING_KEYWORD, ParserRuleContext2.LIST_CONSTRUCTOR, ParserRuleContext2.TYPE_CAST,
            ParserRuleContext2.OPEN_PARENTHESIS, ParserRuleContext2.TABLE_CONSTRUCTOR_OR_QUERY_EXPRESSION,
            ParserRuleContext2.LET_EXPRESSION, ParserRuleContext2.TEMPLATE_START, ParserRuleContext2.XML_KEYWORD,
            ParserRuleContext2.STRING_KEYWORD, ParserRuleContext2.BASE64_KEYWORD, ParserRuleContext2.BASE64_KEYWORD,
            ParserRuleContext2.ANON_FUNC_EXPRESSION, ParserRuleContext2.ERROR_KEYWORD, ParserRuleContext2.NEW_KEYWORD,
            ParserRuleContext2.START_KEYWORD, ParserRuleContext2.FLUSH_KEYWORD, ParserRuleContext2.LEFT_ARROW_TOKEN,
            ParserRuleContext2.WAIT_KEYWORD, ParserRuleContext2.COMMIT_KEYWORD, ParserRuleContext2.TRANSACTIONAL_KEYWORD,
            ParserRuleContext2.SERVICE_CONSTRUCTOR_EXPRESSION, ParserRuleContext2.FAIL_KEYWORD };

    private static final int[] FIRST_MAPPING_FIELD_START =
            { ParserRuleContext2.MAPPING_FIELD, ParserRuleContext2.CLOSE_BRACE };

    private static final int[] MAPPING_FIELD_START = { ParserRuleContext2.SPECIFIC_FIELD,
            ParserRuleContext2.COMPUTED_FIELD_NAME, ParserRuleContext2.ELLIPSIS, ParserRuleContext2.READONLY_KEYWORD };

    private static final int[] SPECIFIC_FIELD =
            { ParserRuleContext2.MAPPING_FIELD_NAME, ParserRuleContext2.STRING_LITERAL };

    private static final int[] SPECIFIC_FIELD_RHS =
            { ParserRuleContext2.COLON, ParserRuleContext2.MAPPING_FIELD_END };

    private static final int[] MAPPING_FIELD_END =
            { ParserRuleContext2.CLOSE_BRACE, ParserRuleContext2.COMMA };

    private static final int[] OPTIONAL_SERVICE_NAME =
            { ParserRuleContext2.SERVICE_NAME, ParserRuleContext2.ON_KEYWORD };

    private static final int[] RESOURCE_DEF_START =
            { ParserRuleContext2.RESOURCE_KEYWORD, ParserRuleContext2.FUNC_DEF, ParserRuleContext2.CLOSE_BRACE };

    private static final int[] CONST_DECL_RHS =
            { ParserRuleContext2.TYPE_NAME_OR_VAR_NAME, ParserRuleContext2.ASSIGN_OP };

    private static final int[] ARRAY_LENGTH =
            { ParserRuleContext2.CLOSE_BRACKET, ParserRuleContext2.DECIMAL_INTEGER_LITERAL,
                    ParserRuleContext2.HEX_INTEGER_LITERAL, ParserRuleContext2.ASTERISK, ParserRuleContext2.VARIABLE_REF };

    private static final int[] PARAM_LIST =
            { ParserRuleContext2.CLOSE_PARENTHESIS, ParserRuleContext2.REQUIRED_PARAM };

    private static final int[] PARAMETER_START =
            { ParserRuleContext2.ANNOTATIONS, ParserRuleContext2.PUBLIC_KEYWORD, ParserRuleContext2.TYPE_DESC_IN_PARAM };

    private static final int[] PARAMETER_WITHOUT_ANNOTS =
            { ParserRuleContext2.PUBLIC_KEYWORD, ParserRuleContext2.TYPE_DESC_IN_PARAM };

    private static final int[] REQUIRED_PARAM_NAME_RHS =
            { ParserRuleContext2.PARAM_END, ParserRuleContext2.ASSIGN_OP };

    private static final int[] PARAM_END =
            { ParserRuleContext2.COMMA, ParserRuleContext2.CLOSE_PARENTHESIS };

    private static final int[] STMT_START_WITH_EXPR_RHS = { ParserRuleContext2.ASSIGN_OP,
            ParserRuleContext2.RIGHT_ARROW, ParserRuleContext2.COMPOUND_BINARY_OPERATOR, ParserRuleContext2.SEMICOLON };

    private static final int[] EXPR_STMT_RHS = { ParserRuleContext2.SEMICOLON, ParserRuleContext2.ASSIGN_OP,
            ParserRuleContext2.RIGHT_ARROW, ParserRuleContext2.COMPOUND_BINARY_OPERATOR };

    private static final int[] EXPRESSION_STATEMENT_START =
            { ParserRuleContext2.VARIABLE_REF, ParserRuleContext2.CHECKING_KEYWORD, ParserRuleContext2.OPEN_PARENTHESIS,
                    ParserRuleContext2.START_KEYWORD, ParserRuleContext2.FLUSH_KEYWORD };

    private static final int[] ANNOT_DECL_OPTIONAL_TYPE =
            { ParserRuleContext2.TYPE_DESC_BEFORE_IDENTIFIER, ParserRuleContext2.ANNOTATION_TAG };

    private static final int[] CONST_DECL_TYPE =
            { ParserRuleContext2.TYPE_DESC_BEFORE_IDENTIFIER, ParserRuleContext2.VARIABLE_NAME };

    private static final int[] ANNOT_DECL_RHS =
            { ParserRuleContext2.ANNOTATION_TAG, ParserRuleContext2.ON_KEYWORD, ParserRuleContext2.SEMICOLON };

    private static final int[] ANNOT_OPTIONAL_ATTACH_POINTS =
            { ParserRuleContext2.ON_KEYWORD, ParserRuleContext2.SEMICOLON };

    private static final int[] ATTACH_POINT =
            { ParserRuleContext2.SOURCE_KEYWORD, ParserRuleContext2.ATTACH_POINT_IDENT };

    private static final int[] ATTACH_POINT_IDENT = { ParserRuleContext2.SINGLE_KEYWORD_ATTACH_POINT_IDENT,
            ParserRuleContext2.OBJECT_IDENT, ParserRuleContext2.RESOURCE_IDENT, ParserRuleContext2.RECORD_IDENT };

    private static final int[] ATTACH_POINT_END =
            { ParserRuleContext2.COMMA, ParserRuleContext2.SEMICOLON };

    private static final int[] XML_NAMESPACE_PREFIX_DECL =
            { ParserRuleContext2.AS_KEYWORD, ParserRuleContext2.SEMICOLON };

    private static final int[] CONSTANT_EXPRESSION =
            { ParserRuleContext2.BASIC_LITERAL, ParserRuleContext2.VARIABLE_REF, ParserRuleContext2.PLUS_TOKEN,
                    ParserRuleContext2.MINUS_TOKEN, ParserRuleContext2.NIL_LITERAL };

    private static final int[] LIST_CONSTRUCTOR_RHS =
            { ParserRuleContext2.CLOSE_BRACKET, ParserRuleContext2.EXPRESSION };

    private static final int[] TYPE_CAST_PARAM =
            { ParserRuleContext2.TYPE_DESC_IN_ANGLE_BRACKETS, ParserRuleContext2.ANNOTATIONS };

    private static final int[] TYPE_CAST_PARAM_RHS =
            { ParserRuleContext2.TYPE_DESC_IN_ANGLE_BRACKETS, ParserRuleContext2.GT };

    private static final int[] TABLE_KEYWORD_RHS =
            { ParserRuleContext2.KEY_SPECIFIER, ParserRuleContext2.TABLE_CONSTRUCTOR };

    private static final int[] ROW_LIST_RHS =
            { ParserRuleContext2.CLOSE_BRACKET, ParserRuleContext2.MAPPING_CONSTRUCTOR };

    private static final int[] TABLE_ROW_END =
            { ParserRuleContext2.COMMA, ParserRuleContext2.CLOSE_BRACKET };

    private static final int[] KEY_SPECIFIER_RHS =
            { ParserRuleContext2.CLOSE_PARENTHESIS, ParserRuleContext2.VARIABLE_NAME };

    private static final int[] TABLE_KEY_RHS =
            { ParserRuleContext2.COMMA, ParserRuleContext2.CLOSE_PARENTHESIS };

    private static final int[] ERROR_TYPE_PARAMS =
            { ParserRuleContext2.INFERRED_TYPE_DESC, ParserRuleContext2.TYPE_DESC_IN_ANGLE_BRACKETS };

    private static final int[] LET_VAR_DECL_START =
            { ParserRuleContext2.TYPE_DESC_IN_TYPE_BINDING_PATTERN, ParserRuleContext2.ANNOTATIONS };

    private static final int[] STREAM_TYPE_FIRST_PARAM_RHS =
            { ParserRuleContext2.COMMA, ParserRuleContext2.GT };

    private static final int[] TEMPLATE_MEMBER = { ParserRuleContext2.TEMPLATE_STRING,
            ParserRuleContext2.INTERPOLATION_START_TOKEN, ParserRuleContext2.TEMPLATE_END };

    private static final int[] TEMPLATE_STRING_RHS =
            { ParserRuleContext2.INTERPOLATION_START_TOKEN, ParserRuleContext2.TEMPLATE_END };

    private static final int[] KEY_CONSTRAINTS_RHS =
            { ParserRuleContext2.OPEN_PARENTHESIS, ParserRuleContext2.LT };

    private static final int[] FUNCTION_KEYWORD_RHS =
            { ParserRuleContext2.FUNC_NAME, ParserRuleContext2.OPEN_PARENTHESIS };

    private static final int[] TYPEDESC_RHS = { ParserRuleContext2.END_OF_TYPE_DESC,
            ParserRuleContext2.ARRAY_TYPE_DESCRIPTOR, ParserRuleContext2.OPTIONAL_TYPE_DESCRIPTOR, ParserRuleContext2.PIPE,
            ParserRuleContext2.BITWISE_AND_OPERATOR };

    private static final int[] TABLE_TYPE_DESC_RHS =
            { ParserRuleContext2.KEY_KEYWORD, ParserRuleContext2.TYPEDESC_RHS };

    private static final int[] NEW_KEYWORD_RHS =
            { ParserRuleContext2.TYPE_DESC_IN_NEW_EXPR, ParserRuleContext2.EXPRESSION_RHS };

    private static final int[] TABLE_CONSTRUCTOR_OR_QUERY_START =
            { ParserRuleContext2.TABLE_KEYWORD, ParserRuleContext2.STREAM_KEYWORD, ParserRuleContext2.QUERY_EXPRESSION };

    private static final int[] TABLE_CONSTRUCTOR_OR_QUERY_RHS =
            { ParserRuleContext2.TABLE_CONSTRUCTOR, ParserRuleContext2.QUERY_EXPRESSION };

    private static final int[] QUERY_EXPRESSION_RHS =
            { ParserRuleContext2.SELECT_CLAUSE, ParserRuleContext2.WHERE_CLAUSE, ParserRuleContext2.FROM_CLAUSE,
                    ParserRuleContext2.LET_CLAUSE, ParserRuleContext2.ORDER_BY_CLAUSE, ParserRuleContext2.DO_CLAUSE,
                    ParserRuleContext2.QUERY_EXPRESSION_END };

    private static final int[] BRACED_EXPR_OR_ANON_FUNC_PARAM_RHS =
            { ParserRuleContext2.CLOSE_PARENTHESIS, ParserRuleContext2.COMMA };

    private static final int[] ANNOTATION_REF_RHS =
            { ParserRuleContext2.OPEN_PARENTHESIS, ParserRuleContext2.ANNOTATION_END };

    private static final int[] INFER_PARAM_END_OR_PARENTHESIS_END =
            { ParserRuleContext2.CLOSE_PARENTHESIS, ParserRuleContext2.EXPR_FUNC_BODY_START };

    private static final int[] OPTIONAL_PEER_WORKER =
            { ParserRuleContext2.PEER_WORKER_NAME, ParserRuleContext2.EXPRESSION_RHS };

    private static final int[] TYPE_DESC_IN_TUPLE_RHS =
            { ParserRuleContext2.CLOSE_BRACKET, ParserRuleContext2.COMMA, ParserRuleContext2.ELLIPSIS };

    private static final int[] LIST_CONSTRUCTOR_MEMBER_END =
            { ParserRuleContext2.CLOSE_BRACKET, ParserRuleContext2.COMMA };

    private static final int[] NIL_OR_PARENTHESISED_TYPE_DESC_RHS =
            { ParserRuleContext2.CLOSE_PARENTHESIS, ParserRuleContext2.TYPE_DESCRIPTOR };

    private static final int[] BINDING_PATTERN =
            { ParserRuleContext2.BINDING_PATTERN_STARTING_IDENTIFIER, ParserRuleContext2.LIST_BINDING_PATTERN,
                    ParserRuleContext2.MAPPING_BINDING_PATTERN, ParserRuleContext2.FUNCTIONAL_BINDING_PATTERN };

    private static final int[] LIST_BINDING_PATTERN_CONTENTS =
            { ParserRuleContext2.REST_BINDING_PATTERN, ParserRuleContext2.BINDING_PATTERN };

    private static final int[] LIST_BINDING_PATTERN_MEMBER_END =
            { ParserRuleContext2.COMMA, ParserRuleContext2.CLOSE_BRACKET };

    private static final int[] MAPPING_BINDING_PATTERN_MEMBER =
            { ParserRuleContext2.REST_BINDING_PATTERN, ParserRuleContext2.FIELD_BINDING_PATTERN };

    private static final int[] MAPPING_BINDING_PATTERN_END =
            { ParserRuleContext2.COMMA, ParserRuleContext2.CLOSE_BRACE };

    private static final int[] FIELD_BINDING_PATTERN_END =
            { ParserRuleContext2.COMMA, ParserRuleContext2.COLON, ParserRuleContext2.CLOSE_BRACE };

    private static final int[] ARG_BINDING_PATTERN = { ParserRuleContext2.BINDING_PATTERN,
            ParserRuleContext2.NAMED_ARG_BINDING_PATTERN, ParserRuleContext2.REST_BINDING_PATTERN };

    private static final int[] ARG_BINDING_PATTERN_END =
            { ParserRuleContext2.COMMA, ParserRuleContext2.CLOSE_PARENTHESIS };

    private static final int[] ARG_BINDING_PATTERN_START_IDENT =
            { ParserRuleContext2.NAMED_ARG_BINDING_PATTERN, ParserRuleContext2.BINDING_PATTERN_STARTING_IDENTIFIER };

    private static final int[] REMOTE_CALL_OR_ASYNC_SEND_RHS =
            { ParserRuleContext2.WORKER_NAME_OR_METHOD_NAME, ParserRuleContext2.DEFAULT_WORKER_NAME_IN_ASYNC_SEND };

    private static final int[] REMOTE_CALL_OR_ASYNC_SEND_END =
            { ParserRuleContext2.ARG_LIST_START, ParserRuleContext2.SEMICOLON };

    private static final int[] RECEIVE_WORKERS =
            { ParserRuleContext2.PEER_WORKER_NAME, ParserRuleContext2.MULTI_RECEIVE_WORKERS };

    private static final int[] RECEIVE_FIELD =
            { ParserRuleContext2.PEER_WORKER_NAME, ParserRuleContext2.RECEIVE_FIELD_NAME };

    private static final int[] RECEIVE_FIELD_END =
            { ParserRuleContext2.CLOSE_BRACE, ParserRuleContext2.COMMA };

    private static final int[] WAIT_KEYWORD_RHS =
            { ParserRuleContext2.MULTI_WAIT_FIELDS, ParserRuleContext2.ALTERNATE_WAIT_EXPRS };

    private static final int[] WAIT_FIELD_NAME_RHS =
            { ParserRuleContext2.COLON, ParserRuleContext2.WAIT_FIELD_END };

    private static final int[] WAIT_FIELD_END =
            { ParserRuleContext2.CLOSE_BRACE, ParserRuleContext2.COMMA };

    private static final int[] WAIT_FUTURE_EXPR_END =
            { ParserRuleContext2.ALTERNATE_WAIT_EXPR_LIST_END, ParserRuleContext2.PIPE };

    private static final int[] ENUM_MEMBER_START =
            { ParserRuleContext2.DOC_STRING, ParserRuleContext2.ANNOTATIONS, ParserRuleContext2.ENUM_MEMBER_NAME };

    private static final int[] ENUM_MEMBER_RHS =
            { ParserRuleContext2.ASSIGN_OP, ParserRuleContext2.ENUM_MEMBER_END };

    private static final int[] ENUM_MEMBER_END =
            { ParserRuleContext2.COMMA, ParserRuleContext2.CLOSE_BRACE };

    private static final int[] MEMBER_ACCESS_KEY_EXPR_END =
            { ParserRuleContext2.COMMA, ParserRuleContext2.CLOSE_BRACKET };

    private static final int[] ROLLBACK_RHS =
            { ParserRuleContext2.SEMICOLON, ParserRuleContext2.EXPRESSION };

    private static final int[] RETRY_KEYWORD_RHS =
            { ParserRuleContext2.LT, ParserRuleContext2.RETRY_TYPE_PARAM_RHS };

    private static final int[] RETRY_TYPE_PARAM_RHS =
            { ParserRuleContext2.ARG_LIST_START, ParserRuleContext2.RETRY_BODY };

    private static final int[] RETRY_BODY =
            { ParserRuleContext2.BLOCK_STMT, ParserRuleContext2.TRANSACTION_STMT };

    private static final int[] LIST_BP_OR_TUPLE_TYPE_MEMBER =
            { ParserRuleContext2.TYPE_DESCRIPTOR, ParserRuleContext2.LIST_BINDING_PATTERN_MEMBER };

    private static final int[] LIST_BP_OR_TUPLE_TYPE_DESC_RHS =
            { ParserRuleContext2.ASSIGN_OP, ParserRuleContext2.VARIABLE_NAME };

    private static final int[] BRACKETED_LIST_MEMBER_END =
            { ParserRuleContext2.COMMA, ParserRuleContext2.CLOSE_BRACKET };

    private static final int[] BRACKETED_LIST_MEMBER =
            // array length is also an expression
            { ParserRuleContext2.EXPRESSION, ParserRuleContext2.BINDING_PATTERN };

    private static final int[] LIST_BINDING_MEMBER_OR_ARRAY_LENGTH =
            { ParserRuleContext2.ARRAY_LENGTH, ParserRuleContext2.BINDING_PATTERN };

    private static final int[] BRACKETED_LIST_RHS = { ParserRuleContext2.ASSIGN_OP,
            ParserRuleContext2.VARIABLE_NAME, ParserRuleContext2.BINDING_PATTERN, ParserRuleContext2.EXPRESSION_RHS };

    private static final int[] XML_NAVIGATE_EXPR =
            { ParserRuleContext2.XML_FILTER_EXPR, ParserRuleContext2.XML_STEP_EXPR };

    private static final int[] XML_NAME_PATTERN_RHS = { ParserRuleContext2.GT, ParserRuleContext2.PIPE };

    private static final int[] XML_ATOMIC_NAME_PATTERN_START =
            { ParserRuleContext2.ASTERISK, ParserRuleContext2.XML_ATOMIC_NAME_IDENTIFIER };

    private static final int[] XML_ATOMIC_NAME_IDENTIFIER_RHS =
            { ParserRuleContext2.ASTERISK, ParserRuleContext2.IDENTIFIER };

    private static final int[] XML_STEP_START = { ParserRuleContext2.SLASH_ASTERISK_TOKEN,
            ParserRuleContext2.DOUBLE_SLASH_DOUBLE_ASTERISK_LT_TOKEN, ParserRuleContext2.SLASH_LT_TOKEN };

    private static final int[] MATCH_PATTERN_RHS =
            { ParserRuleContext2.PIPE, ParserRuleContext2.MATCH_PATTERN_END };

    private static final int[] OPTIONAL_MATCH_GUARD =
            { ParserRuleContext2.IF_KEYWORD, ParserRuleContext2.RIGHT_DOUBLE_ARROW };

    private static final int[] MATCH_PATTERN_START = { ParserRuleContext2.CONSTANT_EXPRESSION,
            ParserRuleContext2.VAR_KEYWORD, ParserRuleContext2.LIST_MATCH_PATTERN,
            ParserRuleContext2.MAPPING_MATCH_PATTERN, ParserRuleContext2.FUNCTIONAL_MATCH_PATTERN };

    private static final int[] LIST_MATCH_PATTERNS_START =
            { ParserRuleContext2.LIST_MATCH_PATTERN_MEMBER, ParserRuleContext2.CLOSE_BRACKET };

    private static final int[] LIST_MATCH_PATTERN_MEMBER =
            { ParserRuleContext2.MATCH_PATTERN_START, ParserRuleContext2.REST_MATCH_PATTERN };

    private static final int[] LIST_MATCH_PATTERN_MEMBER_RHS =
            { ParserRuleContext2.COMMA, ParserRuleContext2.CLOSE_BRACKET };

    private static final int[] FIELD_MATCH_PATTERNS_START =
            { ParserRuleContext2.FIELD_MATCH_PATTERN_MEMBER, ParserRuleContext2.CLOSE_BRACE };

    private static final int[] FIELD_MATCH_PATTERN_MEMBER =
            { ParserRuleContext2.VARIABLE_NAME, ParserRuleContext2.REST_MATCH_PATTERN };

    private static final int[] FIELD_MATCH_PATTERN_MEMBER_RHS =
            { ParserRuleContext2.COMMA, ParserRuleContext2.CLOSE_BRACE };

    private static final int[] FUNC_MATCH_PATTERN_OR_CONST_PATTERN =
            { ParserRuleContext2.OPEN_PARENTHESIS, ParserRuleContext2.MATCH_PATTERN_END };

    private static final int[] FUNCTIONAL_MATCH_PATTERN_START =
            { ParserRuleContext2.ERROR_KEYWORD, ParserRuleContext2.TYPE_REFERENCE };

    private static final int[] ARG_LIST_MATCH_PATTERN_START =
            { ParserRuleContext2.ARG_MATCH_PATTERN, ParserRuleContext2.CLOSE_PARENTHESIS };

    private static final int[] ARG_MATCH_PATTERN = { ParserRuleContext2.MATCH_PATTERN,
            ParserRuleContext2.NAMED_ARG_MATCH_PATTERN, ParserRuleContext2.REST_MATCH_PATTERN };

    private static final int[] ARG_MATCH_PATTERN_RHS =
            { ParserRuleContext2.COMMA, ParserRuleContext2.CLOSE_PARENTHESIS };

    private static final int[] NAMED_ARG_MATCH_PATTERN_RHS =
            { ParserRuleContext2.NAMED_ARG_MATCH_PATTERN, ParserRuleContext2.REST_MATCH_PATTERN };

    private static final int[] ORDER_KEY_LIST_END =
            { ParserRuleContext2.COMMA, ParserRuleContext2.ORDER_CLAUSE_END };

    private static final int[] ORDER_DIRECTION_RHS =
            { ParserRuleContext2.COMMA, ParserRuleContext2.EXPRESSION, ParserRuleContext2.ORDER_CLAUSE_END };

    private static final int[] LIST_BP_OR_LIST_CONSTRUCTOR_MEMBER =
            { ParserRuleContext2.LIST_BINDING_PATTERN_MEMBER, ParserRuleContext2.LIST_CONSTRUCTOR_FIRST_MEMBER};

    private static final int[] TUPLE_TYPE_DESC_OR_LIST_CONST_MEMBER =
            { ParserRuleContext2.TYPE_DESCRIPTOR, ParserRuleContext2.LIST_CONSTRUCTOR_FIRST_MEMBER};

    public BallerinaParserErrorHandler(AbstractTokenReader tokenReader) {
        super(tokenReader);
    }

    @Override
    protected boolean isProductionWithAlternatives(int currentCtx) {
        switch (currentCtx) {
            case ParserRuleContext2.TOP_LEVEL_NODE:
            case ParserRuleContext2.TOP_LEVEL_NODE_WITHOUT_MODIFIER:
            case ParserRuleContext2.TOP_LEVEL_NODE_WITHOUT_METADATA:
            case ParserRuleContext2.STATEMENT:
            case ParserRuleContext2.STATEMENT_WITHOUT_ANNOTS:
            case ParserRuleContext2.FUNC_BODY_OR_TYPE_DESC_RHS:
            case ParserRuleContext2.VAR_DECL_STMT_RHS:
            case ParserRuleContext2.EXPRESSION_RHS:
            case ParserRuleContext2.PARAMETER_NAME_RHS:
            case ParserRuleContext2.ASSIGNMENT_OR_VAR_DECL_STMT:
            case ParserRuleContext2.AFTER_PARAMETER_TYPE:
            case ParserRuleContext2.FIELD_DESCRIPTOR_RHS:
            case ParserRuleContext2.RECORD_BODY_START:
            case ParserRuleContext2.RECORD_BODY_END:
            case ParserRuleContext2.TYPE_DESCRIPTOR:
            case ParserRuleContext2.NAMED_OR_POSITIONAL_ARG_RHS:
            case ParserRuleContext2.OBJECT_FIELD_RHS:
            case ParserRuleContext2.OBJECT_FUNC_OR_FIELD_WITHOUT_VISIBILITY:
            case ParserRuleContext2.OBJECT_MEMBER:
            case ParserRuleContext2.OBJECT_TYPE_QUALIFIER:
            case ParserRuleContext2.ELSE_BODY:
            case ParserRuleContext2.IMPORT_DECL_RHS:
            case ParserRuleContext2.IMPORT_SUB_VERSION:
            case ParserRuleContext2.VERSION_NUMBER:
            case ParserRuleContext2.IMPORT_VERSION_DECL:
            case ParserRuleContext2.IMPORT_PREFIX_DECL:
            case ParserRuleContext2.MAPPING_FIELD:
            case ParserRuleContext2.FIRST_MAPPING_FIELD:
            case ParserRuleContext2.SPECIFIC_FIELD_RHS:
            case ParserRuleContext2.RESOURCE_DEF:
            case ParserRuleContext2.PARAMETER_WITHOUT_ANNOTS:
            case ParserRuleContext2.PARAMETER_START:
            case ParserRuleContext2.STMT_START_WITH_EXPR_RHS:
            case ParserRuleContext2.EXPR_STMT_RHS:
            case ParserRuleContext2.RECORD_FIELD_OR_RECORD_END:
            case ParserRuleContext2.CONST_DECL_TYPE:
            case ParserRuleContext2.CONST_DECL_RHS:
            case ParserRuleContext2.ANNOT_OPTIONAL_ATTACH_POINTS:
            case ParserRuleContext2.XML_NAMESPACE_PREFIX_DECL:
            case ParserRuleContext2.ANNOT_DECL_OPTIONAL_TYPE:
            case ParserRuleContext2.ANNOT_DECL_RHS:
            case ParserRuleContext2.TABLE_KEYWORD_RHS:
            case ParserRuleContext2.ARRAY_LENGTH:
            case ParserRuleContext2.TYPEDESC_RHS:
            case ParserRuleContext2.ERROR_TYPE_PARAMS:
            case ParserRuleContext2.STREAM_TYPE_FIRST_PARAM_RHS:
            case ParserRuleContext2.KEY_CONSTRAINTS_RHS:
            case ParserRuleContext2.TABLE_TYPE_DESC_RHS:
            case ParserRuleContext2.FUNC_BODY:
            case ParserRuleContext2.FUNC_OPTIONAL_RETURNS:
            case ParserRuleContext2.TERMINAL_EXPRESSION:
            case ParserRuleContext2.TABLE_CONSTRUCTOR_OR_QUERY_START:
            case ParserRuleContext2.TABLE_CONSTRUCTOR_OR_QUERY_RHS:
            case ParserRuleContext2.QUERY_PIPELINE_RHS:
            case ParserRuleContext2.ANON_FUNC_BODY:
            case ParserRuleContext2.BINDING_PATTERN:
            case ParserRuleContext2.LIST_BINDING_PATTERN_MEMBER:
            case ParserRuleContext2.LIST_BINDING_PATTERN_MEMBER_END:
            case ParserRuleContext2.MAPPING_BINDING_PATTERN_MEMBER:
            case ParserRuleContext2.MAPPING_BINDING_PATTERN_END:
            case ParserRuleContext2.FIELD_BINDING_PATTERN_END:
            case ParserRuleContext2.ARG_BINDING_PATTERN:
            case ParserRuleContext2.ARG_BINDING_PATTERN_END:
            case ParserRuleContext2.ARG_BINDING_PATTERN_START_IDENT:
            case ParserRuleContext2.REMOTE_CALL_OR_ASYNC_SEND_RHS:
            case ParserRuleContext2.REMOTE_CALL_OR_ASYNC_SEND_END:
            case ParserRuleContext2.RECEIVE_FIELD_END:
            case ParserRuleContext2.RECEIVE_WORKERS:
            case ParserRuleContext2.WAIT_FIELD_NAME:
            case ParserRuleContext2.WAIT_FIELD_NAME_RHS:
            case ParserRuleContext2.WAIT_FIELD_END:
            case ParserRuleContext2.WAIT_FUTURE_EXPR_END:
            case ParserRuleContext2.MAPPING_FIELD_END:
            case ParserRuleContext2.ENUM_MEMBER_START:
            case ParserRuleContext2.ENUM_MEMBER_RHS:
            case ParserRuleContext2.STMT_START_BRACKETED_LIST_MEMBER:
            case ParserRuleContext2.STMT_START_BRACKETED_LIST_RHS:
            case ParserRuleContext2.ENUM_MEMBER_END:
            case ParserRuleContext2.BINDING_PATTERN_OR_EXPR_RHS:
            case ParserRuleContext2.BRACKETED_LIST_RHS:
            case ParserRuleContext2.BRACKETED_LIST_MEMBER:
            case ParserRuleContext2.BRACKETED_LIST_MEMBER_END:
            case ParserRuleContext2.AMBIGUOUS_STMT:
            case ParserRuleContext2.TYPED_BINDING_PATTERN_TYPE_RHS:
            case ParserRuleContext2.TYPE_DESC_IN_TUPLE_RHS:
            case ParserRuleContext2.LIST_BINDING_MEMBER_OR_ARRAY_LENGTH:
            case ParserRuleContext2.FUNC_TYPE_DESC_RHS_OR_ANON_FUNC_BODY:
            case ParserRuleContext2.OPTIONAL_MATCH_GUARD:
            case ParserRuleContext2.MATCH_PATTERN_RHS:
            case ParserRuleContext2.MATCH_PATTERN_START:
            case ParserRuleContext2.LIST_MATCH_PATTERNS_START:
            case ParserRuleContext2.LIST_MATCH_PATTERN_MEMBER:
            case ParserRuleContext2.LIST_MATCH_PATTERN_MEMBER_RHS:
            case ParserRuleContext2.FIELD_MATCH_PATTERNS_START:
            case ParserRuleContext2.FIELD_MATCH_PATTERN_MEMBER:
            case ParserRuleContext2.FIELD_MATCH_PATTERN_MEMBER_RHS:
            case ParserRuleContext2.FUNC_MATCH_PATTERN_OR_CONST_PATTERN:
            case ParserRuleContext2.FUNCTIONAL_MATCH_PATTERN_START:
            case ParserRuleContext2.ARG_LIST_MATCH_PATTERN_START:
            case ParserRuleContext2.ARG_MATCH_PATTERN:
            case ParserRuleContext2.ARG_MATCH_PATTERN_RHS:
            case ParserRuleContext2.NAMED_ARG_MATCH_PATTERN_RHS:
            case ParserRuleContext2.EXTERNAL_FUNC_BODY_OPTIONAL_ANNOTS:
            case ParserRuleContext2.ORDER_KEY_LIST_END:
            case ParserRuleContext2.ORDER_DIRECTION_RHS:
            case ParserRuleContext2.LIST_BP_OR_LIST_CONSTRUCTOR_MEMBER:
            case ParserRuleContext2.TUPLE_TYPE_DESC_OR_LIST_CONST_MEMBER:
                return true;
            default:
                return false;
        }
    }

    private boolean isEndOfObjectTypeNode(int nextLookahead) {
//        STToken nextToken = this.tokenReader.peek(nextLookahead);
//        switch (nextToken.kind) {
//            case CLOSE_BRACE_TOKEN:
//            case EOF_TOKEN:
//            case CLOSE_BRACE_PIPE_TOKEN:
//            case TYPE_KEYWORD:
//            case SERVICE_KEYWORD:
//                return true;
//            default:
//                STToken nextNextToken = this.tokenReader.peek(nextLookahead + 1);
//                switch (nextNextToken.kind) {
//                    case CLOSE_BRACE_TOKEN:
//                    case EOF_TOKEN:
//                    case CLOSE_BRACE_PIPE_TOKEN:
//                    case TYPE_KEYWORD:
//                    case SERVICE_KEYWORD:
//                        return true;
//                    default:
//                        return false;
//                }
//        }
        return false;
    }

    /**
     * Search for a solution.
     * Terminals are directly matched and Non-terminals which have alternative productions are seekInAlternativesPaths()
     *
     * @param currentCtx Current context
     * @param lookahead Position of the next token to consider, relative to the position of the original error.
     * @param currentDepth Amount of distance traveled so far.
     * @return Recovery result
     */
    @Override
    protected Result seekMatch(int currentCtx, int lookahead, int currentDepth, boolean isEntryPoint) {
        boolean hasMatch;
        boolean skipRule;
        int matchingRulesCount = 0;

        while (currentDepth < LOOKAHEAD_LIMIT) {
            hasMatch = true;
            skipRule = false;
            STToken nextToken = this.tokenReader.peek(lookahead);

            switch (currentCtx) {
                case ParserRuleContext2.EOF:
                    hasMatch = nextToken.kind == SyntaxKind2.EOF_TOKEN;
                    break;
                case ParserRuleContext2.FUNC_NAME:
                case ParserRuleContext2.VARIABLE_NAME:
                case ParserRuleContext2.TYPE_NAME:
                case ParserRuleContext2.IMPORT_ORG_OR_MODULE_NAME:
                case ParserRuleContext2.IMPORT_MODULE_NAME:
                case ParserRuleContext2.IMPORT_PREFIX:
                case ParserRuleContext2.MAPPING_FIELD_NAME:
                case ParserRuleContext2.SERVICE_NAME:
                case ParserRuleContext2.QUALIFIED_IDENTIFIER:
                case ParserRuleContext2.IDENTIFIER:
                case ParserRuleContext2.ANNOTATION_TAG:
                case ParserRuleContext2.NAMESPACE_PREFIX:
                case ParserRuleContext2.WORKER_NAME:
                case ParserRuleContext2.IMPLICIT_ANON_FUNC_PARAM:
                case ParserRuleContext2.WORKER_NAME_OR_METHOD_NAME:
                case ParserRuleContext2.RECEIVE_FIELD_NAME:
                case ParserRuleContext2.WAIT_FIELD_NAME:
                case ParserRuleContext2.FIELD_BINDING_PATTERN_NAME:
                case ParserRuleContext2.XML_ATOMIC_NAME_IDENTIFIER:
                    hasMatch = nextToken.kind == SyntaxKind2.IDENTIFIER_TOKEN;
                    break;
                case ParserRuleContext2.OPEN_PARENTHESIS:
                case ParserRuleContext2.PARENTHESISED_TYPE_DESC_START:
                    hasMatch = nextToken.kind == SyntaxKind2.OPEN_PAREN_TOKEN;
                    break;
                case ParserRuleContext2.CLOSE_PARENTHESIS:
                    hasMatch = nextToken.kind == SyntaxKind2.CLOSE_PAREN_TOKEN;
                    break;
                case ParserRuleContext2.SIMPLE_TYPE_DESCRIPTOR:
                    hasMatch = BallerinaParser.isSimpleType(nextToken.kind) ||
                            nextToken.kind == SyntaxKind2.IDENTIFIER_TOKEN;
                    break;
                case ParserRuleContext2.OPEN_BRACE:
                    hasMatch = nextToken.kind == SyntaxKind2.OPEN_BRACE_TOKEN;
                    break;
                case ParserRuleContext2.CLOSE_BRACE:
                    hasMatch = nextToken.kind == SyntaxKind2.CLOSE_BRACE_TOKEN;
                    break;
                case ParserRuleContext2.ASSIGN_OP:
                    hasMatch = nextToken.kind == SyntaxKind2.EQUAL_TOKEN;
                    break;
                case ParserRuleContext2.SEMICOLON:
                    hasMatch = nextToken.kind == SyntaxKind2.SEMICOLON_TOKEN;
                    break;
                case ParserRuleContext2.BINARY_OPERATOR:
                    hasMatch = isBinaryOperator(nextToken);
                    break;
                case ParserRuleContext2.COMMA:
                    hasMatch = nextToken.kind == SyntaxKind2.COMMA_TOKEN;
                    break;
                case ParserRuleContext2.CLOSED_RECORD_BODY_END:
                    hasMatch = nextToken.kind == SyntaxKind2.CLOSE_BRACE_PIPE_TOKEN;
                    break;
                case ParserRuleContext2.CLOSED_RECORD_BODY_START:
                    hasMatch = nextToken.kind == SyntaxKind2.OPEN_BRACE_PIPE_TOKEN;
                    break;
                case ParserRuleContext2.ELLIPSIS:
                    hasMatch = nextToken.kind == SyntaxKind2.ELLIPSIS_TOKEN;
                    break;
                case ParserRuleContext2.QUESTION_MARK:
                    hasMatch = nextToken.kind == SyntaxKind2.QUESTION_MARK_TOKEN;
                    break;
                case ParserRuleContext2.ARG_LIST_START:
                    hasMatch = nextToken.kind == SyntaxKind2.OPEN_PAREN_TOKEN;
                    break;
                case ParserRuleContext2.ARG_LIST_END:
                    hasMatch = nextToken.kind == SyntaxKind2.CLOSE_PAREN_TOKEN;
                    break;
                case ParserRuleContext2.OBJECT_TYPE_QUALIFIER:
                    hasMatch = nextToken.kind == SyntaxKind2.ABSTRACT_KEYWORD ||
                            nextToken.kind == SyntaxKind2.CLIENT_KEYWORD ||
                            nextToken.kind == SyntaxKind2.READONLY_KEYWORD;
                    break;
                case ParserRuleContext2.OPEN_BRACKET:
                case ParserRuleContext2.TUPLE_TYPE_DESC_START:
                    hasMatch = nextToken.kind == SyntaxKind2.OPEN_BRACKET_TOKEN;
                    break;
                case ParserRuleContext2.CLOSE_BRACKET:
                    hasMatch = nextToken.kind == SyntaxKind2.CLOSE_BRACKET_TOKEN;
                    break;
                case ParserRuleContext2.DOT:
                    hasMatch = nextToken.kind == SyntaxKind2.DOT_TOKEN;
                    break;
                case ParserRuleContext2.BOOLEAN_LITERAL:
                    hasMatch = nextToken.kind == SyntaxKind2.TRUE_KEYWORD || nextToken.kind == SyntaxKind2.FALSE_KEYWORD;
                    break;
                case ParserRuleContext2.DECIMAL_INTEGER_LITERAL:
                case ParserRuleContext2.MAJOR_VERSION:
                case ParserRuleContext2.MINOR_VERSION:
                case ParserRuleContext2.PATCH_VERSION:
                    hasMatch = nextToken.kind == SyntaxKind2.DECIMAL_INTEGER_LITERAL;
                    break;
                case ParserRuleContext2.SLASH:
                    hasMatch = nextToken.kind == SyntaxKind2.SLASH_TOKEN;
                    break;
                case ParserRuleContext2.BASIC_LITERAL:
                    hasMatch = isBasicLiteral(nextToken.kind);
                    break;
                case ParserRuleContext2.COLON:
                    hasMatch = nextToken.kind == SyntaxKind2.COLON_TOKEN;
                    break;
                case ParserRuleContext2.STRING_LITERAL:
                    hasMatch = nextToken.kind == SyntaxKind2.STRING_LITERAL;
                    break;
                case ParserRuleContext2.UNARY_OPERATOR:
                    hasMatch = isUnaryOperator(nextToken);
                    break;
                case ParserRuleContext2.HEX_INTEGER_LITERAL:
                    hasMatch = nextToken.kind == SyntaxKind2.HEX_INTEGER_LITERAL;
                    break;
                case ParserRuleContext2.AT:
                    hasMatch = nextToken.kind == SyntaxKind2.AT_TOKEN;
                    break;
                case ParserRuleContext2.RIGHT_ARROW:
                    hasMatch = nextToken.kind == SyntaxKind2.RIGHT_ARROW_TOKEN;
                    break;
                case ParserRuleContext2.PARAMETERIZED_TYPE:
                    hasMatch = isParameterizedTypeToken(nextToken.kind);
                    break;
                case ParserRuleContext2.LT:
                    hasMatch = nextToken.kind == SyntaxKind2.LT_TOKEN;
                    break;
                case ParserRuleContext2.GT:
                    hasMatch = nextToken.kind == SyntaxKind2.GT_TOKEN;
                    break;
                case ParserRuleContext2.FIELD_IDENT:
                    hasMatch = nextToken.kind == SyntaxKind2.FIELD_KEYWORD;
                    break;
                case ParserRuleContext2.FUNCTION_IDENT:
                    hasMatch = nextToken.kind == SyntaxKind2.FUNCTION_KEYWORD;
                    break;
                case ParserRuleContext2.IDENT_AFTER_OBJECT_IDENT:
                    hasMatch = nextToken.kind == SyntaxKind2.TYPE_KEYWORD ||
                            nextToken.kind == SyntaxKind2.FUNCTION_KEYWORD || nextToken.kind == SyntaxKind2.FIELD_KEYWORD;
                    break;
                case ParserRuleContext2.SINGLE_KEYWORD_ATTACH_POINT_IDENT:
                    hasMatch = isSingleKeywordAttachPointIdent(nextToken.kind);
                    break;
                case ParserRuleContext2.OBJECT_IDENT:
                    hasMatch = nextToken.kind == SyntaxKind2.OBJECT_KEYWORD;
                    break;
                case ParserRuleContext2.RECORD_IDENT:
                    hasMatch = nextToken.kind == SyntaxKind2.RECORD_KEYWORD;
                    break;
                case ParserRuleContext2.RESOURCE_IDENT:
                    hasMatch = nextToken.kind == SyntaxKind2.RESOURCE_KEYWORD;
                    break;
                case ParserRuleContext2.DECIMAL_FLOATING_POINT_LITERAL:
                    hasMatch = nextToken.kind == SyntaxKind2.DECIMAL_FLOATING_POINT_LITERAL;
                    break;
                case ParserRuleContext2.HEX_FLOATING_POINT_LITERAL:
                    hasMatch = nextToken.kind == SyntaxKind2.HEX_FLOATING_POINT_LITERAL;
                    break;
                case ParserRuleContext2.PIPE:
                    hasMatch = nextToken.kind == SyntaxKind2.PIPE_TOKEN;
                    break;
                case ParserRuleContext2.TEMPLATE_START:
                case ParserRuleContext2.TEMPLATE_END:
                    hasMatch = nextToken.kind == SyntaxKind2.BACKTICK_TOKEN;
                    break;
                case ParserRuleContext2.ASTERISK:
                case ParserRuleContext2.INFERRED_TYPE_DESC:
                    hasMatch = nextToken.kind == SyntaxKind2.ASTERISK_TOKEN;
                    break;
                case ParserRuleContext2.BITWISE_AND_OPERATOR:
                    hasMatch = nextToken.kind == SyntaxKind2.BITWISE_AND_TOKEN;
                    break;
                case ParserRuleContext2.EXPR_FUNC_BODY_START:
                case ParserRuleContext2.RIGHT_DOUBLE_ARROW:
                    hasMatch = nextToken.kind == SyntaxKind2.RIGHT_DOUBLE_ARROW_TOKEN;
                    break;
                case ParserRuleContext2.PLUS_TOKEN:
                    hasMatch = nextToken.kind == SyntaxKind2.PLUS_TOKEN;
                    break;
                case ParserRuleContext2.MINUS_TOKEN:
                    hasMatch = nextToken.kind == SyntaxKind2.MINUS_TOKEN;
                    break;
                case ParserRuleContext2.SIGNED_INT_OR_FLOAT_RHS:
                    hasMatch = BallerinaParser.isIntOrFloat(nextToken);
                    break;
                case ParserRuleContext2.SYNC_SEND_TOKEN:
                    hasMatch = nextToken.kind == SyntaxKind2.SYNC_SEND_TOKEN;
                    break;
                case ParserRuleContext2.PEER_WORKER_NAME:
                    hasMatch = nextToken.kind == SyntaxKind2.DEFAULT_KEYWORD ||
                            nextToken.kind == SyntaxKind2.IDENTIFIER_TOKEN;
                    break;
                case ParserRuleContext2.LEFT_ARROW_TOKEN:
                    hasMatch = nextToken.kind == SyntaxKind2.LEFT_ARROW_TOKEN;
                    break;
                case ParserRuleContext2.ANNOT_CHAINING_TOKEN:
                    hasMatch = nextToken.kind == SyntaxKind2.ANNOT_CHAINING_TOKEN;
                    break;
                case ParserRuleContext2.OPTIONAL_CHAINING_TOKEN:
                    hasMatch = nextToken.kind == SyntaxKind2.OPTIONAL_CHAINING_TOKEN;
                    break;
                case ParserRuleContext2.TRANSACTIONAL_KEYWORD:
                    hasMatch = nextToken.kind == SyntaxKind2.TRANSACTIONAL_KEYWORD;
                    break;
                case ParserRuleContext2.MODULE_ENUM_NAME:
                case ParserRuleContext2.ENUM_MEMBER_NAME:
                case ParserRuleContext2.NAMED_ARG_BINDING_PATTERN:
                    hasMatch = nextToken.kind == SyntaxKind2.IDENTIFIER_TOKEN;
                    break;
                case ParserRuleContext2.UNION_OR_INTERSECTION_TOKEN:
                    hasMatch =
                            nextToken.kind == SyntaxKind2.PIPE_TOKEN || nextToken.kind == SyntaxKind2.BITWISE_AND_TOKEN;
                    break;
                case ParserRuleContext2.DOT_LT_TOKEN:
                    hasMatch = nextToken.kind == SyntaxKind2.DOT_LT_TOKEN;
                    break;
                case ParserRuleContext2.SLASH_LT_TOKEN:
                    hasMatch = nextToken.kind == SyntaxKind2.SLASH_LT_TOKEN;
                    break;
                case ParserRuleContext2.DOUBLE_SLASH_DOUBLE_ASTERISK_LT_TOKEN:
                    hasMatch = nextToken.kind == SyntaxKind2.SLASH_ASTERISK_TOKEN;
                    break;
                case ParserRuleContext2.SLASH_ASTERISK_TOKEN:
                    hasMatch = nextToken.kind == SyntaxKind2.SLASH_ASTERISK_TOKEN;
                    break;
                case ParserRuleContext2.KEY_KEYWORD:
                    hasMatch = BallerinaParser.isKeyKeyword(nextToken);
                    break;
                case ParserRuleContext2.VAR_KEYWORD:
                    hasMatch = nextToken.kind == SyntaxKind2.VAR_KEYWORD;
                    break;
                case ParserRuleContext2.FUNCTIONAL_BINDING_PATTERN:
                    hasMatch =
                            nextToken.kind == SyntaxKind2.IDENTIFIER_TOKEN || nextToken.kind == SyntaxKind2.ERROR_KEYWORD;
                    break;

                // start a context, so that we know where to fall back, and continue
                // having the qualified-identifier as the next rule.
                case ParserRuleContext2.VARIABLE_REF:
                case ParserRuleContext2.TYPE_REFERENCE:
                case ParserRuleContext2.ANNOT_REFERENCE:
                case ParserRuleContext2.FIELD_ACCESS_IDENTIFIER:

                    // Contexts that expect a type
                case ParserRuleContext2.TYPE_DESC_IN_ANNOTATION_DECL:
                case ParserRuleContext2.TYPE_DESC_BEFORE_IDENTIFIER:
                case ParserRuleContext2.TYPE_DESC_IN_RECORD_FIELD:
                case ParserRuleContext2.TYPE_DESC_IN_PARAM:
                case ParserRuleContext2.TYPE_DESC_IN_TYPE_BINDING_PATTERN:
                case ParserRuleContext2.TYPE_DESC_IN_TYPE_DEF:
                case ParserRuleContext2.TYPE_DESC_IN_ANGLE_BRACKETS:
                case ParserRuleContext2.TYPE_DESC_IN_RETURN_TYPE_DESC:
                case ParserRuleContext2.TYPE_DESC_IN_EXPRESSION:
                case ParserRuleContext2.TYPE_DESC_IN_STREAM_TYPE_DESC:
                case ParserRuleContext2.TYPE_DESC_IN_PARENTHESIS:
                case ParserRuleContext2.TYPE_DESC_IN_NEW_EXPR:
                default:
                    if (isKeyword(currentCtx)) {
                        int expectedToken = getExpectedKeywordKind(currentCtx);
                        hasMatch = nextToken.kind == expectedToken;
                        break;
                    }

                    if (hasAlternativePaths(currentCtx)) {
                        return seekMatchInAlternativePaths(currentCtx, lookahead, currentDepth, matchingRulesCount,
                                isEntryPoint);
                    }

                    // Stay at the same place
                    skipRule = true;
                    hasMatch = true;
                    break;
            }

            if (!hasMatch) {
                return fixAndContinue(currentCtx, lookahead, currentDepth, matchingRulesCount, isEntryPoint);
            }

            currentCtx = getNextRule(currentCtx, lookahead + 1);
            if (!skipRule) {
                // Try the next token with the next rule
                currentDepth++;
                matchingRulesCount++;
                lookahead++;
                isEntryPoint = false;
            }
        }

        Result result = new Result(new ArrayDeque<>(), matchingRulesCount);
        result.solution = new Solution(Action.KEEP, currentCtx, SyntaxKind2.NONE, String.valueOf(currentCtx));
        return result;
    }

    /**
     * @param currentCtx
     * @return
     */
    private boolean isKeyword(int currentCtx) {
        switch (currentCtx) {
            case ParserRuleContext2.EOF:
            case ParserRuleContext2.PUBLIC_KEYWORD:
            case ParserRuleContext2.PRIVATE_KEYWORD:
            case ParserRuleContext2.REMOTE_KEYWORD:
            case ParserRuleContext2.FUNCTION_KEYWORD:
            case ParserRuleContext2.NEW_KEYWORD:
            case ParserRuleContext2.SELECT_KEYWORD:
            case ParserRuleContext2.WHERE_KEYWORD:
            case ParserRuleContext2.FROM_KEYWORD:
            case ParserRuleContext2.ORDER_KEYWORD:
            case ParserRuleContext2.BY_KEYWORD:
            case ParserRuleContext2.ASCENDING_KEYWORD:
            case ParserRuleContext2.DESCENDING_KEYWORD:
            case ParserRuleContext2.START_KEYWORD:
            case ParserRuleContext2.FLUSH_KEYWORD:
            case ParserRuleContext2.DEFAULT_KEYWORD:
            case ParserRuleContext2.DEFAULT_WORKER_NAME_IN_ASYNC_SEND:
            case ParserRuleContext2.WAIT_KEYWORD:
            case ParserRuleContext2.CHECKING_KEYWORD:
            case ParserRuleContext2.FAIL_KEYWORD:
            case ParserRuleContext2.DO_KEYWORD:
            case ParserRuleContext2.TRANSACTION_KEYWORD:
            case ParserRuleContext2.COMMIT_KEYWORD:
            case ParserRuleContext2.RETRY_KEYWORD:
            case ParserRuleContext2.ROLLBACK_KEYWORD:
            case ParserRuleContext2.ENUM_KEYWORD:
            case ParserRuleContext2.MATCH_KEYWORD:
            case ParserRuleContext2.RETURNS_KEYWORD:
            case ParserRuleContext2.EXTERNAL_KEYWORD:
            case ParserRuleContext2.RECORD_KEYWORD:
            case ParserRuleContext2.TYPE_KEYWORD:
            case ParserRuleContext2.OBJECT_KEYWORD:
            case ParserRuleContext2.ABSTRACT_KEYWORD:
            case ParserRuleContext2.CLIENT_KEYWORD:
            case ParserRuleContext2.IF_KEYWORD:
            case ParserRuleContext2.ELSE_KEYWORD:
            case ParserRuleContext2.WHILE_KEYWORD:
            case ParserRuleContext2.PANIC_KEYWORD:
            case ParserRuleContext2.AS_KEYWORD:
            case ParserRuleContext2.LOCK_KEYWORD:
            case ParserRuleContext2.IMPORT_KEYWORD:
            case ParserRuleContext2.VERSION_KEYWORD:
            case ParserRuleContext2.CONTINUE_KEYWORD:
            case ParserRuleContext2.BREAK_KEYWORD:
            case ParserRuleContext2.RETURN_KEYWORD:
            case ParserRuleContext2.SERVICE_KEYWORD:
            case ParserRuleContext2.ON_KEYWORD:
            case ParserRuleContext2.RESOURCE_KEYWORD:
            case ParserRuleContext2.LISTENER_KEYWORD:
            case ParserRuleContext2.CONST_KEYWORD:
            case ParserRuleContext2.FINAL_KEYWORD:
            case ParserRuleContext2.TYPEOF_KEYWORD:
            case ParserRuleContext2.IS_KEYWORD:
            case ParserRuleContext2.NULL_KEYWORD:
            case ParserRuleContext2.ANNOTATION_KEYWORD:
            case ParserRuleContext2.SOURCE_KEYWORD:
            case ParserRuleContext2.XMLNS_KEYWORD:
            case ParserRuleContext2.WORKER_KEYWORD:
            case ParserRuleContext2.FORK_KEYWORD:
            case ParserRuleContext2.TRAP_KEYWORD:
            case ParserRuleContext2.FOREACH_KEYWORD:
            case ParserRuleContext2.IN_KEYWORD:
            case ParserRuleContext2.TABLE_KEYWORD:
            case ParserRuleContext2.KEY_KEYWORD:
            case ParserRuleContext2.ERROR_KEYWORD:
            case ParserRuleContext2.LET_KEYWORD:
            case ParserRuleContext2.STREAM_KEYWORD:
            case ParserRuleContext2.XML_KEYWORD:
            case ParserRuleContext2.STRING_KEYWORD:
            case ParserRuleContext2.BASE16_KEYWORD:
            case ParserRuleContext2.BASE64_KEYWORD:
            case ParserRuleContext2.DISTINCT_KEYWORD:
                return true;
            default:
                return false;
        }
    }

    private boolean hasAlternativePaths(int currentCtx) {
        switch (currentCtx) {
            case ParserRuleContext2.TOP_LEVEL_NODE:
            case ParserRuleContext2.TOP_LEVEL_NODE_WITHOUT_MODIFIER:
            case ParserRuleContext2.TOP_LEVEL_NODE_WITHOUT_METADATA:
            case ParserRuleContext2.FUNC_OPTIONAL_RETURNS:
            case ParserRuleContext2.FUNC_BODY_OR_TYPE_DESC_RHS:
            case ParserRuleContext2.ANON_FUNC_BODY:
            case ParserRuleContext2.FUNC_BODY:
            case ParserRuleContext2.OBJECT_FUNC_BODY:
            case ParserRuleContext2.EXPRESSION:
            case ParserRuleContext2.TERMINAL_EXPRESSION:
            case ParserRuleContext2.VAR_DECL_STMT_RHS:
            case ParserRuleContext2.EXPRESSION_RHS:
            case ParserRuleContext2.VARIABLE_REF_RHS:
            case ParserRuleContext2.STATEMENT:
            case ParserRuleContext2.STATEMENT_WITHOUT_ANNOTS:
            case ParserRuleContext2.PARAM_LIST:
            case ParserRuleContext2.REQUIRED_PARAM_NAME_RHS:
            case ParserRuleContext2.TYPE_NAME_OR_VAR_NAME:
            case ParserRuleContext2.ASSIGNMENT_OR_VAR_DECL_STMT_RHS:
            case ParserRuleContext2.FIELD_DESCRIPTOR_RHS:
            case ParserRuleContext2.FIELD_OR_REST_DESCIPTOR_RHS:
            case ParserRuleContext2.RECORD_BODY_END:
            case ParserRuleContext2.RECORD_BODY_START:
            case ParserRuleContext2.TYPE_DESCRIPTOR:
            case ParserRuleContext2.RECORD_FIELD_OR_RECORD_END:
            case ParserRuleContext2.RECORD_FIELD_START:
            case ParserRuleContext2.RECORD_FIELD_WITHOUT_METADATA:
            case ParserRuleContext2.ARG_START:
            case ParserRuleContext2.ARG_START_OR_ARG_LIST_END:
            case ParserRuleContext2.NAMED_OR_POSITIONAL_ARG_RHS:
            case ParserRuleContext2.ARG_END:
            case ParserRuleContext2.OBJECT_MEMBER_START:
            case ParserRuleContext2.OBJECT_MEMBER_WITHOUT_METADATA:
            case ParserRuleContext2.OBJECT_FIELD_RHS:
            case ParserRuleContext2.OBJECT_METHOD_START:
            case ParserRuleContext2.OBJECT_FUNC_OR_FIELD:
            case ParserRuleContext2.OBJECT_FUNC_OR_FIELD_WITHOUT_VISIBILITY:
            case ParserRuleContext2.OBJECT_TYPE_DESCRIPTOR_START:
            case ParserRuleContext2.ELSE_BLOCK:
            case ParserRuleContext2.ELSE_BODY:
            case ParserRuleContext2.CALL_STMT_START:
            case ParserRuleContext2.IMPORT_PREFIX_DECL:
            case ParserRuleContext2.IMPORT_VERSION_DECL:
            case ParserRuleContext2.IMPORT_DECL_RHS:
            case ParserRuleContext2.AFTER_IMPORT_MODULE_NAME:
            case ParserRuleContext2.MAJOR_MINOR_VERSION_END:
            case ParserRuleContext2.RETURN_STMT_RHS:
            case ParserRuleContext2.ACCESS_EXPRESSION:
            case ParserRuleContext2.FIRST_MAPPING_FIELD:
            case ParserRuleContext2.MAPPING_FIELD:
            case ParserRuleContext2.SPECIFIC_FIELD:
            case ParserRuleContext2.SPECIFIC_FIELD_RHS:
            case ParserRuleContext2.MAPPING_FIELD_END:
            case ParserRuleContext2.OPTIONAL_SERVICE_NAME:
            case ParserRuleContext2.RESOURCE_DEF:
            case ParserRuleContext2.CONST_DECL_TYPE:
            case ParserRuleContext2.CONST_DECL_RHS:
            case ParserRuleContext2.ARRAY_LENGTH:
            case ParserRuleContext2.PARAMETER_START:
            case ParserRuleContext2.PARAMETER_WITHOUT_ANNOTS:
            case ParserRuleContext2.STMT_START_WITH_EXPR_RHS:
            case ParserRuleContext2.EXPR_STMT_RHS:
            case ParserRuleContext2.EXPRESSION_STATEMENT_START:
            case ParserRuleContext2.ANNOT_DECL_OPTIONAL_TYPE:
            case ParserRuleContext2.ANNOT_DECL_RHS:
            case ParserRuleContext2.ANNOT_OPTIONAL_ATTACH_POINTS:
            case ParserRuleContext2.ATTACH_POINT:
            case ParserRuleContext2.ATTACH_POINT_IDENT:
            case ParserRuleContext2.ATTACH_POINT_END:
            case ParserRuleContext2.XML_NAMESPACE_PREFIX_DECL:
            case ParserRuleContext2.CONSTANT_EXPRESSION_START:
            case ParserRuleContext2.TYPEDESC_RHS:
            case ParserRuleContext2.LIST_CONSTRUCTOR_FIRST_MEMBER:
            case ParserRuleContext2.TYPE_CAST_PARAM:
            case ParserRuleContext2.TYPE_CAST_PARAM_RHS:
            case ParserRuleContext2.TABLE_KEYWORD_RHS:
            case ParserRuleContext2.ROW_LIST_RHS:
            case ParserRuleContext2.TABLE_ROW_END:
            case ParserRuleContext2.KEY_SPECIFIER_RHS:
            case ParserRuleContext2.TABLE_KEY_RHS:
            case ParserRuleContext2.ERROR_TYPE_PARAMS:
            case ParserRuleContext2.LET_VAR_DECL_START:
            case ParserRuleContext2.ORDER_KEY_LIST_END:
            case ParserRuleContext2.ORDER_DIRECTION_RHS:
            case ParserRuleContext2.STREAM_TYPE_FIRST_PARAM_RHS:
            case ParserRuleContext2.TEMPLATE_MEMBER:
            case ParserRuleContext2.TEMPLATE_STRING_RHS:
            case ParserRuleContext2.FUNCTION_KEYWORD_RHS:
            case ParserRuleContext2.WORKER_NAME_RHS:
            case ParserRuleContext2.BINDING_PATTERN:
            case ParserRuleContext2.LIST_BINDING_PATTERN_MEMBER_END:
            case ParserRuleContext2.FIELD_BINDING_PATTERN_END:
            case ParserRuleContext2.LIST_BINDING_PATTERN_MEMBER:
            case ParserRuleContext2.MAPPING_BINDING_PATTERN_END:
            case ParserRuleContext2.MAPPING_BINDING_PATTERN_MEMBER:
            case ParserRuleContext2.KEY_CONSTRAINTS_RHS:
            case ParserRuleContext2.TABLE_TYPE_DESC_RHS:
            case ParserRuleContext2.NEW_KEYWORD_RHS:
            case ParserRuleContext2.TABLE_CONSTRUCTOR_OR_QUERY_START:
            case ParserRuleContext2.TABLE_CONSTRUCTOR_OR_QUERY_RHS:
            case ParserRuleContext2.QUERY_PIPELINE_RHS:
            case ParserRuleContext2.BRACED_EXPR_OR_ANON_FUNC_PARAM_RHS:
            case ParserRuleContext2.ANON_FUNC_PARAM_RHS:
            case ParserRuleContext2.PARAM_END:
            case ParserRuleContext2.ANNOTATION_REF_RHS:
            case ParserRuleContext2.INFER_PARAM_END_OR_PARENTHESIS_END:
            case ParserRuleContext2.TYPE_DESC_IN_TUPLE_RHS:
            case ParserRuleContext2.LIST_CONSTRUCTOR_MEMBER_END:
            case ParserRuleContext2.NIL_OR_PARENTHESISED_TYPE_DESC_RHS:
            case ParserRuleContext2.REMOTE_CALL_OR_ASYNC_SEND_RHS:
            case ParserRuleContext2.REMOTE_CALL_OR_ASYNC_SEND_END:
            case ParserRuleContext2.RECEIVE_WORKERS:
            case ParserRuleContext2.RECEIVE_FIELD:
            case ParserRuleContext2.RECEIVE_FIELD_END:
            case ParserRuleContext2.WAIT_KEYWORD_RHS:
            case ParserRuleContext2.WAIT_FIELD_NAME_RHS:
            case ParserRuleContext2.WAIT_FIELD_END:
            case ParserRuleContext2.WAIT_FUTURE_EXPR_END:
            case ParserRuleContext2.OPTIONAL_PEER_WORKER:
            case ParserRuleContext2.ENUM_MEMBER_START:
            case ParserRuleContext2.ENUM_MEMBER_RHS:
            case ParserRuleContext2.ENUM_MEMBER_END:
            case ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR_END:
            case ParserRuleContext2.ROLLBACK_RHS:
            case ParserRuleContext2.RETRY_KEYWORD_RHS:
            case ParserRuleContext2.RETRY_TYPE_PARAM_RHS:
            case ParserRuleContext2.RETRY_BODY:
            case ParserRuleContext2.STMT_START_BRACKETED_LIST_MEMBER:
            case ParserRuleContext2.STMT_START_BRACKETED_LIST_RHS:
            case ParserRuleContext2.BINDING_PATTERN_OR_EXPR_RHS:
            case ParserRuleContext2.BRACKETED_LIST_RHS:
            case ParserRuleContext2.BRACKETED_LIST_MEMBER:
            case ParserRuleContext2.BRACKETED_LIST_MEMBER_END:
            case ParserRuleContext2.AMBIGUOUS_STMT:
            case ParserRuleContext2.LIST_BINDING_MEMBER_OR_ARRAY_LENGTH:
            case ParserRuleContext2.XML_NAVIGATE_EXPR:
            case ParserRuleContext2.XML_NAME_PATTERN_RHS:
            case ParserRuleContext2.XML_ATOMIC_NAME_PATTERN_START:
            case ParserRuleContext2.XML_ATOMIC_NAME_IDENTIFIER_RHS:
            case ParserRuleContext2.XML_STEP_START:
            case ParserRuleContext2.FUNC_TYPE_DESC_RHS_OR_ANON_FUNC_BODY:
            case ParserRuleContext2.OPTIONAL_MATCH_GUARD:
            case ParserRuleContext2.MATCH_PATTERN_RHS:
            case ParserRuleContext2.MATCH_PATTERN_START:
            case ParserRuleContext2.LIST_MATCH_PATTERNS_START:
            case ParserRuleContext2.LIST_MATCH_PATTERN_MEMBER:
            case ParserRuleContext2.LIST_MATCH_PATTERN_MEMBER_RHS:
            case ParserRuleContext2.ARG_BINDING_PATTERN:
            case ParserRuleContext2.ARG_BINDING_PATTERN_END:
            case ParserRuleContext2.ARG_BINDING_PATTERN_START_IDENT:
            case ParserRuleContext2.FIELD_MATCH_PATTERNS_START:
            case ParserRuleContext2.FIELD_MATCH_PATTERN_MEMBER:
            case ParserRuleContext2.FIELD_MATCH_PATTERN_MEMBER_RHS:
            case ParserRuleContext2.FUNC_MATCH_PATTERN_OR_CONST_PATTERN:
            case ParserRuleContext2.FUNCTIONAL_MATCH_PATTERN_START:
            case ParserRuleContext2.ARG_LIST_MATCH_PATTERN_START:
            case ParserRuleContext2.ARG_MATCH_PATTERN:
            case ParserRuleContext2.ARG_MATCH_PATTERN_RHS:
            case ParserRuleContext2.NAMED_ARG_MATCH_PATTERN_RHS:
            case ParserRuleContext2.EXTERNAL_FUNC_BODY_OPTIONAL_ANNOTS:
            case ParserRuleContext2.LIST_BP_OR_LIST_CONSTRUCTOR_MEMBER:
            case ParserRuleContext2.TUPLE_TYPE_DESC_OR_LIST_CONST_MEMBER:
                return true;
            default:
                return false;
        }
    }

    private Result seekMatchInAlternativePaths(int currentCtx, int lookahead, int currentDepth,
                                               int matchingRulesCount, boolean isEntryPoint) {
        int[] alternativeRules;
        switch (currentCtx) {
            case ParserRuleContext2.TOP_LEVEL_NODE:
                alternativeRules = TOP_LEVEL_NODE;
                break;
            case ParserRuleContext2.TOP_LEVEL_NODE_WITHOUT_MODIFIER:
                alternativeRules = TOP_LEVEL_NODE_WITHOUT_MODIFIER;
                break;
            case ParserRuleContext2.TOP_LEVEL_NODE_WITHOUT_METADATA:
                alternativeRules = TOP_LEVEL_NODE_WITHOUT_METADATA;
                break;
            case ParserRuleContext2.FUNC_OPTIONAL_RETURNS:
                int parentCtx = getParentContext();
                int[] alternatives;
                if (parentCtx == ParserRuleContext2.FUNC_DEF) {
                    alternatives = FUNC_DEF_OPTIONAL_RETURNS;
                } else if (parentCtx == ParserRuleContext2.ANON_FUNC_EXPRESSION) {
                    alternatives = ANNON_FUNC_OPTIONAL_RETURNS;
                } else if (parentCtx == ParserRuleContext2.FUNC_TYPE_DESC) {
                    alternatives = FUNC_TYPE_OPTIONAL_RETURNS;
                } else if (parentCtx == ParserRuleContext2.FUNC_TYPE_DESC_OR_ANON_FUNC) {
                    alternatives = FUNC_TYPE_OR_ANON_FUNC_OPTIONAL_RETURNS;
                } else {
                    alternatives = FUNC_TYPE_OR_DEF_OPTIONAL_RETURNS;
                }

                alternativeRules = alternatives;
                break;
            case ParserRuleContext2.FUNC_BODY_OR_TYPE_DESC_RHS:
                alternativeRules = FUNC_BODY_OR_TYPE_DESC_RHS;
                break;
            case ParserRuleContext2.FUNC_TYPE_DESC_RHS_OR_ANON_FUNC_BODY:
                alternativeRules = FUNC_TYPE_DESC_RHS_OR_ANON_FUNC_BODY;
                break;
            case ParserRuleContext2.ANON_FUNC_BODY:
                alternativeRules = ANON_FUNC_BODY;
                break;
            case ParserRuleContext2.FUNC_BODY:
            case ParserRuleContext2.OBJECT_FUNC_BODY:
                if (getGrandParentContext() == ParserRuleContext2.OBJECT_MEMBER) {
                    alternativeRules = OBJECT_FUNC_BODY;
                } else {
                    alternativeRules = FUNC_BODY;
                }
                break;
            case ParserRuleContext2.PARAM_LIST:
                alternativeRules = PARAM_LIST;
                break;
            case ParserRuleContext2.REQUIRED_PARAM_NAME_RHS:
                alternativeRules = REQUIRED_PARAM_NAME_RHS;
                break;
            case ParserRuleContext2.FIELD_DESCRIPTOR_RHS:
                alternativeRules = FIELD_DESCRIPTOR_RHS;
                break;
            case ParserRuleContext2.FIELD_OR_REST_DESCIPTOR_RHS:
                alternativeRules = FIELD_OR_REST_DESCIPTOR_RHS;
                break;
            case ParserRuleContext2.RECORD_BODY_END:
                alternativeRules = RECORD_BODY_END;
                break;
            case ParserRuleContext2.RECORD_BODY_START:
                alternativeRules = RECORD_BODY_START;
                break;
            case ParserRuleContext2.TYPE_DESCRIPTOR:
                alternativeRules = TYPE_DESCRIPTORS;
                break;
            case ParserRuleContext2.RECORD_FIELD_OR_RECORD_END:
                alternativeRules = RECORD_FIELD_OR_RECORD_END;
                break;
            case ParserRuleContext2.RECORD_FIELD_START:
                alternativeRules = RECORD_FIELD_START;
                break;
            case ParserRuleContext2.RECORD_FIELD_WITHOUT_METADATA:
                alternativeRules = RECORD_FIELD_WITHOUT_METADATA;
                break;
            case ParserRuleContext2.OBJECT_MEMBER_START:
                alternativeRules = OBJECT_MEMBER_START;
                break;
            case ParserRuleContext2.OBJECT_MEMBER_WITHOUT_METADATA:
                alternativeRules = OBJECT_MEMBER_WITHOUT_METADATA;
                break;
            case ParserRuleContext2.OBJECT_FIELD_RHS:
                alternativeRules = OBJECT_FIELD_RHS;
                break;
            case ParserRuleContext2.OBJECT_METHOD_START:
                alternativeRules = OBJECT_METHOD_START;
                break;
            case ParserRuleContext2.OBJECT_FUNC_OR_FIELD:
                alternativeRules = OBJECT_FUNC_OR_FIELD;
                break;
            case ParserRuleContext2.OBJECT_FUNC_OR_FIELD_WITHOUT_VISIBILITY:
                alternativeRules = OBJECT_FUNC_OR_FIELD_WITHOUT_VISIBILITY;
                break;
            case ParserRuleContext2.OBJECT_TYPE_DESCRIPTOR_START:
                alternativeRules = OBJECT_TYPE_DESCRIPTOR_START;
                break;
            case ParserRuleContext2.IMPORT_PREFIX_DECL:
                alternativeRules = IMPORT_PREFIX_DECL;
                break;
            case ParserRuleContext2.IMPORT_VERSION_DECL:
                alternativeRules = IMPORT_VERSION;
                break;
            case ParserRuleContext2.IMPORT_DECL_RHS:
                alternativeRules = IMPORT_DECL_RHS;
                break;
            case ParserRuleContext2.AFTER_IMPORT_MODULE_NAME:
                alternativeRules = AFTER_IMPORT_MODULE_NAME;
                break;
            case ParserRuleContext2.MAJOR_MINOR_VERSION_END:
                alternativeRules = MAJOR_MINOR_VERSION_END;
                break;
            case ParserRuleContext2.OPTIONAL_SERVICE_NAME:
                alternativeRules = OPTIONAL_SERVICE_NAME;
                break;
            case ParserRuleContext2.RESOURCE_DEF:
                alternativeRules = RESOURCE_DEF_START;
                break;
            case ParserRuleContext2.CONST_DECL_TYPE:
                alternativeRules = CONST_DECL_TYPE;
                break;
            case ParserRuleContext2.CONST_DECL_RHS:
                alternativeRules = CONST_DECL_RHS;
                break;
            case ParserRuleContext2.PARAMETER_START:
                alternativeRules = PARAMETER_START;
                break;
            case ParserRuleContext2.PARAMETER_WITHOUT_ANNOTS:
                alternativeRules = PARAMETER_WITHOUT_ANNOTS;
                break;
            case ParserRuleContext2.ANNOT_DECL_OPTIONAL_TYPE:
                alternativeRules = ANNOT_DECL_OPTIONAL_TYPE;
                break;
            case ParserRuleContext2.ANNOT_DECL_RHS:
                alternativeRules = ANNOT_DECL_RHS;
                break;
            case ParserRuleContext2.ANNOT_OPTIONAL_ATTACH_POINTS:
                alternativeRules = ANNOT_OPTIONAL_ATTACH_POINTS;
                break;
            case ParserRuleContext2.ATTACH_POINT:
                alternativeRules = ATTACH_POINT;
                break;
            case ParserRuleContext2.ATTACH_POINT_IDENT:
                alternativeRules = ATTACH_POINT_IDENT;
                break;
            case ParserRuleContext2.ATTACH_POINT_END:
                alternativeRules = ATTACH_POINT_END;
                break;
            case ParserRuleContext2.XML_NAMESPACE_PREFIX_DECL:
                alternativeRules = XML_NAMESPACE_PREFIX_DECL;
                break;
            case ParserRuleContext2.ENUM_MEMBER_START:
                alternativeRules = ENUM_MEMBER_START;
                break;
            case ParserRuleContext2.ENUM_MEMBER_RHS:
                alternativeRules = ENUM_MEMBER_RHS;
                break;
            case ParserRuleContext2.ENUM_MEMBER_END:
                alternativeRules = ENUM_MEMBER_END;
                break;
            case ParserRuleContext2.EXTERNAL_FUNC_BODY_OPTIONAL_ANNOTS:
                alternativeRules = EXTERNAL_FUNC_BODY_OPTIONAL_ANNOTS;
                break;
            case ParserRuleContext2.LIST_BP_OR_LIST_CONSTRUCTOR_MEMBER:
                alternativeRules = LIST_BP_OR_LIST_CONSTRUCTOR_MEMBER;
                break;
            case ParserRuleContext2.TUPLE_TYPE_DESC_OR_LIST_CONST_MEMBER:
                alternativeRules = TUPLE_TYPE_DESC_OR_LIST_CONST_MEMBER;
                break;
            default:
                return seekMatchInStmtRelatedAlternativePaths(currentCtx, lookahead, currentDepth, matchingRulesCount,
                        isEntryPoint);
        }

        return seekInAlternativesPaths(lookahead, currentDepth, matchingRulesCount, alternativeRules, isEntryPoint);
    }

    private Result seekMatchInStmtRelatedAlternativePaths(int currentCtx, int lookahead, int currentDepth,
                                                          int matchingRulesCount, boolean isEntryPoint) {
        int[] alternativeRules;
        switch (currentCtx) {
            case ParserRuleContext2.VAR_DECL_STMT_RHS:
                alternativeRules = VAR_DECL_RHS;
                break;
            case ParserRuleContext2.STATEMENT:
            case ParserRuleContext2.STATEMENT_WITHOUT_ANNOTS:
                return seekInStatements(currentCtx, lookahead, currentDepth, matchingRulesCount, isEntryPoint);
            case ParserRuleContext2.TYPE_NAME_OR_VAR_NAME:
                alternativeRules = TYPE_OR_VAR_NAME;
                break;
            case ParserRuleContext2.ASSIGNMENT_OR_VAR_DECL_STMT_RHS:
                alternativeRules = ASSIGNMENT_OR_VAR_DECL_SECOND_TOKEN;
                break;
            case ParserRuleContext2.ELSE_BLOCK:
                alternativeRules = ELSE_BLOCK;
                break;
            case ParserRuleContext2.ELSE_BODY:
                alternativeRules = ELSE_BODY;
                break;
            case ParserRuleContext2.CALL_STMT_START:
                alternativeRules = CALL_STATEMENT;
                break;
            case ParserRuleContext2.RETURN_STMT_RHS:
                alternativeRules = RETURN_RHS;
                break;
            case ParserRuleContext2.ARRAY_LENGTH:
                alternativeRules = ARRAY_LENGTH;
                break;
            case ParserRuleContext2.STMT_START_WITH_EXPR_RHS:
                alternativeRules = STMT_START_WITH_EXPR_RHS;
                break;
            case ParserRuleContext2.EXPR_STMT_RHS:
                alternativeRules = EXPR_STMT_RHS;
                break;
            case ParserRuleContext2.EXPRESSION_STATEMENT_START:
                alternativeRules = EXPRESSION_STATEMENT_START;
                break;
            case ParserRuleContext2.TYPEDESC_RHS:
                alternativeRules = TYPEDESC_RHS;
                break;
            case ParserRuleContext2.ERROR_TYPE_PARAMS:
                alternativeRules = ERROR_TYPE_PARAMS;
                break;
            case ParserRuleContext2.STREAM_TYPE_FIRST_PARAM_RHS:
                alternativeRules = STREAM_TYPE_FIRST_PARAM_RHS;
                break;
            case ParserRuleContext2.FUNCTION_KEYWORD_RHS:
                alternativeRules = FUNCTION_KEYWORD_RHS;
                break;
            case ParserRuleContext2.WORKER_NAME_RHS:
                alternativeRules = WORKER_NAME_RHS;
                break;
            case ParserRuleContext2.BINDING_PATTERN:
                alternativeRules = BINDING_PATTERN;
                break;
            case ParserRuleContext2.LIST_BINDING_PATTERN_MEMBER_END:
                alternativeRules = LIST_BINDING_PATTERN_MEMBER_END;
                break;
            case ParserRuleContext2.LIST_BINDING_PATTERN_MEMBER:
                alternativeRules = LIST_BINDING_PATTERN_CONTENTS;
                break;
            case ParserRuleContext2.MAPPING_BINDING_PATTERN_END:
                alternativeRules = MAPPING_BINDING_PATTERN_END;
                break;
            case ParserRuleContext2.FIELD_BINDING_PATTERN_END:
                alternativeRules = FIELD_BINDING_PATTERN_END;
                break;
            case ParserRuleContext2.MAPPING_BINDING_PATTERN_MEMBER:
                alternativeRules = MAPPING_BINDING_PATTERN_MEMBER;
                break;
            case ParserRuleContext2.ARG_BINDING_PATTERN:
                alternativeRules = ARG_BINDING_PATTERN;
                break;
            case ParserRuleContext2.ARG_BINDING_PATTERN_END:
                alternativeRules = ARG_BINDING_PATTERN_END;
                break;
            case ParserRuleContext2.ARG_BINDING_PATTERN_START_IDENT:
                alternativeRules = ARG_BINDING_PATTERN_START_IDENT;
                break;
            case ParserRuleContext2.KEY_CONSTRAINTS_RHS:
                alternativeRules = KEY_CONSTRAINTS_RHS;
                break;
            case ParserRuleContext2.TABLE_TYPE_DESC_RHS:
                alternativeRules = TABLE_TYPE_DESC_RHS;
                break;
            case ParserRuleContext2.TYPE_DESC_IN_TUPLE_RHS:
                alternativeRules = TYPE_DESC_IN_TUPLE_RHS;
                break;
            case ParserRuleContext2.LIST_CONSTRUCTOR_MEMBER_END:
                alternativeRules = LIST_CONSTRUCTOR_MEMBER_END;
                break;
            case ParserRuleContext2.NIL_OR_PARENTHESISED_TYPE_DESC_RHS:
                alternativeRules = NIL_OR_PARENTHESISED_TYPE_DESC_RHS;
                break;
            case ParserRuleContext2.REMOTE_CALL_OR_ASYNC_SEND_RHS:
                alternativeRules = REMOTE_CALL_OR_ASYNC_SEND_RHS;
                break;
            case ParserRuleContext2.REMOTE_CALL_OR_ASYNC_SEND_END:
                alternativeRules = REMOTE_CALL_OR_ASYNC_SEND_END;
                break;
            case ParserRuleContext2.RECEIVE_WORKERS:
                alternativeRules = RECEIVE_WORKERS;
                break;
            case ParserRuleContext2.RECEIVE_FIELD:
                alternativeRules = RECEIVE_FIELD;
                break;
            case ParserRuleContext2.RECEIVE_FIELD_END:
                alternativeRules = RECEIVE_FIELD_END;
                break;
            case ParserRuleContext2.WAIT_KEYWORD_RHS:
                alternativeRules = WAIT_KEYWORD_RHS;
                break;
            case ParserRuleContext2.WAIT_FIELD_NAME_RHS:
                alternativeRules = WAIT_FIELD_NAME_RHS;
                break;
            case ParserRuleContext2.WAIT_FIELD_END:
                alternativeRules = WAIT_FIELD_END;
                break;
            case ParserRuleContext2.WAIT_FUTURE_EXPR_END:
                alternativeRules = WAIT_FUTURE_EXPR_END;
                break;
            case ParserRuleContext2.OPTIONAL_PEER_WORKER:
                alternativeRules = OPTIONAL_PEER_WORKER;
                break;
            case ParserRuleContext2.ROLLBACK_RHS:
                alternativeRules = ROLLBACK_RHS;
                break;
            case ParserRuleContext2.RETRY_KEYWORD_RHS:
                alternativeRules = RETRY_KEYWORD_RHS;
                break;
            case ParserRuleContext2.RETRY_TYPE_PARAM_RHS:
                alternativeRules = RETRY_TYPE_PARAM_RHS;
                break;
            case ParserRuleContext2.RETRY_BODY:
                alternativeRules = RETRY_BODY;
                break;
            case ParserRuleContext2.STMT_START_BRACKETED_LIST_MEMBER:
                alternativeRules = LIST_BP_OR_TUPLE_TYPE_MEMBER;
                break;
            case ParserRuleContext2.STMT_START_BRACKETED_LIST_RHS:
                alternativeRules = LIST_BP_OR_TUPLE_TYPE_DESC_RHS;
                break;
            case ParserRuleContext2.BRACKETED_LIST_MEMBER_END:
                alternativeRules = BRACKETED_LIST_MEMBER_END;
                break;
            case ParserRuleContext2.BRACKETED_LIST_MEMBER:
                alternativeRules = BRACKETED_LIST_MEMBER;
                break;
            case ParserRuleContext2.BRACKETED_LIST_RHS:
            case ParserRuleContext2.BINDING_PATTERN_OR_EXPR_RHS:
                alternativeRules = BRACKETED_LIST_RHS;
                break;
            case ParserRuleContext2.LIST_BINDING_MEMBER_OR_ARRAY_LENGTH:
                alternativeRules = LIST_BINDING_MEMBER_OR_ARRAY_LENGTH;
                break;
            case ParserRuleContext2.MATCH_PATTERN_RHS:
                alternativeRules = MATCH_PATTERN_RHS;
                break;
            case ParserRuleContext2.MATCH_PATTERN_START:
                alternativeRules = MATCH_PATTERN_START;
                break;
            case ParserRuleContext2.LIST_MATCH_PATTERNS_START:
                alternativeRules = LIST_MATCH_PATTERNS_START;
                break;
            case ParserRuleContext2.LIST_MATCH_PATTERN_MEMBER:
                alternativeRules = LIST_MATCH_PATTERN_MEMBER;
                break;
            case ParserRuleContext2.LIST_MATCH_PATTERN_MEMBER_RHS:
                alternativeRules = LIST_MATCH_PATTERN_MEMBER_RHS;
                break;
            case ParserRuleContext2.FIELD_MATCH_PATTERNS_START:
                alternativeRules = FIELD_MATCH_PATTERNS_START;
                break;
            case ParserRuleContext2.FIELD_MATCH_PATTERN_MEMBER:
                alternativeRules = FIELD_MATCH_PATTERN_MEMBER;
                break;
            case ParserRuleContext2.FIELD_MATCH_PATTERN_MEMBER_RHS:
                alternativeRules = FIELD_MATCH_PATTERN_MEMBER_RHS;
                break;
            case ParserRuleContext2.FUNC_MATCH_PATTERN_OR_CONST_PATTERN:
                alternativeRules = FUNC_MATCH_PATTERN_OR_CONST_PATTERN;
                break;
            case ParserRuleContext2.FUNCTIONAL_MATCH_PATTERN_START:
                alternativeRules = FUNCTIONAL_MATCH_PATTERN_START;
                break;
            case ParserRuleContext2.ARG_LIST_MATCH_PATTERN_START:
                alternativeRules = ARG_LIST_MATCH_PATTERN_START;
                break;
            case ParserRuleContext2.ARG_MATCH_PATTERN:
                alternativeRules = ARG_MATCH_PATTERN;
                break;
            case ParserRuleContext2.ARG_MATCH_PATTERN_RHS:
                alternativeRules = ARG_MATCH_PATTERN_RHS;
                break;
            case ParserRuleContext2.NAMED_ARG_MATCH_PATTERN_RHS:
                alternativeRules = NAMED_ARG_MATCH_PATTERN_RHS;
                break;
            default:
                return seekMatchInExprRelatedAlternativePaths(currentCtx, lookahead, currentDepth, matchingRulesCount,
                        isEntryPoint);
        }

        return seekInAlternativesPaths(lookahead, currentDepth, matchingRulesCount, alternativeRules, isEntryPoint);
    }

    private Result seekMatchInExprRelatedAlternativePaths(int currentCtx, int lookahead, int currentDepth,
                                                          int matchingRulesCount, boolean isEntryPoint) {
        int[] alternativeRules;
        switch (currentCtx) {
            case ParserRuleContext2.EXPRESSION:
            case ParserRuleContext2.TERMINAL_EXPRESSION:
                alternativeRules = EXPRESSION_START;
                break;
            case ParserRuleContext2.ARG_START:
                alternativeRules = ARG_START;
                break;
            case ParserRuleContext2.ARG_START_OR_ARG_LIST_END:
                alternativeRules = ARG_START_OR_ARG_LIST_END;
                break;
            case ParserRuleContext2.NAMED_OR_POSITIONAL_ARG_RHS:
                alternativeRules = NAMED_OR_POSITIONAL_ARG_RHS;
                break;
            case ParserRuleContext2.ARG_END:
                alternativeRules = ARG_END;
                break;
            case ParserRuleContext2.ACCESS_EXPRESSION:
                return seekInAccessExpression(currentCtx, lookahead, currentDepth, matchingRulesCount, isEntryPoint);
            case ParserRuleContext2.FIRST_MAPPING_FIELD:
                alternativeRules = FIRST_MAPPING_FIELD_START;
                break;
            case ParserRuleContext2.MAPPING_FIELD:
                alternativeRules = MAPPING_FIELD_START;
                break;
            case ParserRuleContext2.SPECIFIC_FIELD:
                alternativeRules = SPECIFIC_FIELD;
                break;
            case ParserRuleContext2.SPECIFIC_FIELD_RHS:
                alternativeRules = SPECIFIC_FIELD_RHS;
                break;
            case ParserRuleContext2.MAPPING_FIELD_END:
                alternativeRules = MAPPING_FIELD_END;
                break;
            case ParserRuleContext2.LET_VAR_DECL_START:
                alternativeRules = LET_VAR_DECL_START;
                break;
            case ParserRuleContext2.ORDER_KEY_LIST_END:
                alternativeRules = ORDER_KEY_LIST_END;
                break;
            case ParserRuleContext2.ORDER_DIRECTION_RHS:
                alternativeRules = ORDER_DIRECTION_RHS;
                break;
            case ParserRuleContext2.TEMPLATE_MEMBER:
                alternativeRules = TEMPLATE_MEMBER;
                break;
            case ParserRuleContext2.TEMPLATE_STRING_RHS:
                alternativeRules = TEMPLATE_STRING_RHS;
                break;
            case ParserRuleContext2.CONSTANT_EXPRESSION_START:
                alternativeRules = CONSTANT_EXPRESSION;
                break;
            case ParserRuleContext2.LIST_CONSTRUCTOR_FIRST_MEMBER:
                alternativeRules = LIST_CONSTRUCTOR_RHS;
                break;
            case ParserRuleContext2.TYPE_CAST_PARAM:
                alternativeRules = TYPE_CAST_PARAM;
                break;
            case ParserRuleContext2.TYPE_CAST_PARAM_RHS:
                alternativeRules = TYPE_CAST_PARAM_RHS;
                break;
            case ParserRuleContext2.TABLE_KEYWORD_RHS:
                alternativeRules = TABLE_KEYWORD_RHS;
                break;
            case ParserRuleContext2.ROW_LIST_RHS:
                alternativeRules = ROW_LIST_RHS;
                break;
            case ParserRuleContext2.TABLE_ROW_END:
                alternativeRules = TABLE_ROW_END;
                break;
            case ParserRuleContext2.KEY_SPECIFIER_RHS:
                alternativeRules = KEY_SPECIFIER_RHS;
                break;
            case ParserRuleContext2.TABLE_KEY_RHS:
                alternativeRules = TABLE_KEY_RHS;
                break;
            case ParserRuleContext2.NEW_KEYWORD_RHS:
                alternativeRules = NEW_KEYWORD_RHS;
                break;
            case ParserRuleContext2.TABLE_CONSTRUCTOR_OR_QUERY_START:
                alternativeRules = TABLE_CONSTRUCTOR_OR_QUERY_START;
                break;
            case ParserRuleContext2.TABLE_CONSTRUCTOR_OR_QUERY_RHS:
                alternativeRules = TABLE_CONSTRUCTOR_OR_QUERY_RHS;
                break;
            case ParserRuleContext2.QUERY_PIPELINE_RHS:
                alternativeRules = QUERY_EXPRESSION_RHS;
                break;
            case ParserRuleContext2.BRACED_EXPR_OR_ANON_FUNC_PARAM_RHS:
            case ParserRuleContext2.ANON_FUNC_PARAM_RHS:
                alternativeRules = BRACED_EXPR_OR_ANON_FUNC_PARAM_RHS;
                break;
            case ParserRuleContext2.PARAM_END:
                alternativeRules = PARAM_END;
                break;
            case ParserRuleContext2.ANNOTATION_REF_RHS:
                alternativeRules = ANNOTATION_REF_RHS;
                break;
            case ParserRuleContext2.INFER_PARAM_END_OR_PARENTHESIS_END:
                alternativeRules = INFER_PARAM_END_OR_PARENTHESIS_END;
                break;
            case ParserRuleContext2.XML_NAVIGATE_EXPR:
                alternativeRules = XML_NAVIGATE_EXPR;
                break;
            case ParserRuleContext2.XML_NAME_PATTERN_RHS:
                alternativeRules = XML_NAME_PATTERN_RHS;
                break;
            case ParserRuleContext2.XML_ATOMIC_NAME_PATTERN_START:
                alternativeRules = XML_ATOMIC_NAME_PATTERN_START;
                break;
            case ParserRuleContext2.XML_ATOMIC_NAME_IDENTIFIER_RHS:
                alternativeRules = XML_ATOMIC_NAME_IDENTIFIER_RHS;
                break;
            case ParserRuleContext2.XML_STEP_START:
                alternativeRules = XML_STEP_START;
                break;
            case ParserRuleContext2.OPTIONAL_MATCH_GUARD:
                alternativeRules = OPTIONAL_MATCH_GUARD;
                break;
            case ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR_END:
                alternativeRules = MEMBER_ACCESS_KEY_EXPR_END;
                break;
            case ParserRuleContext2.EXPRESSION_RHS:
                return seekMatchInExpressionRhs(lookahead, currentDepth, matchingRulesCount, isEntryPoint, false);
            case ParserRuleContext2.VARIABLE_REF_RHS:
                return seekMatchInExpressionRhs(lookahead, currentDepth, matchingRulesCount, isEntryPoint, true);
            default:
                throw new IllegalStateException(String.valueOf(currentCtx));
        }

        return seekInAlternativesPaths(lookahead, currentDepth, matchingRulesCount, alternativeRules, isEntryPoint);
    }

    /**
     * Search for matching token sequences within different kinds of statements and returns the most optimal solution.
     *
     * @param currentCtx Current context
     * @param lookahead Position of the next token to consider, relative to the position of the original error
     * @param currentDepth Amount of distance traveled so far
     * @param currentMatches Matching tokens found so far
     * @param fixes Fixes made so far
     * @return Recovery result
     */
    private Result seekInStatements(int currentCtx, int lookahead, int currentDepth, int currentMatches,
                                    boolean isEntryPoint) {
        STToken nextToken = this.tokenReader.peek(lookahead);
        if (nextToken.kind == SyntaxKind2.SEMICOLON_TOKEN) {
            // Semicolon at the start of a statement is a special case. This is equivalent to an empty
            // statement. So assume the fix for this is a REMOVE operation and continue from the next token.
            Result result = seekMatchInSubTree(ParserRuleContext2.STATEMENT, lookahead + 1, currentDepth, isEntryPoint);
            result.fixes.push(new Solution(Action.REMOVE, currentCtx, nextToken.kind, nextToken.toString()));
            return getFinalResult(currentMatches, result);
        }

        return seekInAlternativesPaths(lookahead, currentDepth, currentMatches, STATEMENTS, isEntryPoint);
    }

    /**
     * Search for matching token sequences within access expressions and returns the most optimal solution.
     * Access expression can be one of: method-call, field-access, member-access.
     *
     * @param currentCtx Current context
     * @param lookahead Position of the next token to consider, relative to the position of the original error
     * @param currentDepth Amount of distance traveled so far
     * @param currentMatches Matching tokens found so far
     * @param fixes Fixes made so far
     * @param isEntryPoint
     * @return Recovery result
     */
    private Result seekInAccessExpression(int currentCtx, int lookahead, int currentDepth,
                                          int currentMatches, boolean isEntryPoint) {
        // TODO: Remove this method
        STToken nextToken = this.tokenReader.peek(lookahead);
        currentDepth++;
        if (nextToken.kind != SyntaxKind2.IDENTIFIER_TOKEN) {
            return fixAndContinue(currentCtx, lookahead, currentDepth, currentMatches, isEntryPoint);
        }

        int nextContext;
        STToken nextNextToken = this.tokenReader.peek(lookahead + 1);
//        switch (nextNextToken.kind) {
//            case OPEN_PAREN_TOKEN:
//                nextContext = ParserRuleContext2.OPEN_PARENTHESIS;
//                break;
//            case DOT_TOKEN:
//                nextContext = ParserRuleContext2.DOT;
//                break;
//            case OPEN_BRACKET_TOKEN:
//                nextContext = ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR;
//                break;
//            default:
//                nextContext = getNextRuleForExpr();
//                break;
//        }
        nextContext = getNextRuleForExpr();

        currentMatches++;
        lookahead++;
        Result result = seekMatch(nextContext, lookahead, currentDepth, isEntryPoint);
        return getFinalResult(currentMatches, result);
    }

    /**
     * Search for a match in rhs of an expression. RHS of an expression can be the end
     * of the expression or the rhs of a binary expression.
     *
     * @param lookahead Position of the next token to consider, relative to the position of the original error
     * @param currentDepth Amount of distance traveled so far
     * @param currentMatches Matching tokens found so far
     * @param isEntryPoint
     * @param allowFuncCall Whether function call is allowed or not
     * @return Recovery result
     */
    private Result seekMatchInExpressionRhs(int lookahead, int currentDepth, int currentMatches, boolean isEntryPoint,
                                            boolean allowFuncCall) {
        int parentCtx = getParentContext();
        int[] alternatives = null;
        switch (parentCtx) {
            case ParserRuleContext2.ARG_LIST:
                alternatives = new int[] { ParserRuleContext2.BINARY_OPERATOR, ParserRuleContext2.DOT,
                        ParserRuleContext2.ANNOT_CHAINING_TOKEN, ParserRuleContext2.OPTIONAL_CHAINING_TOKEN,
                        ParserRuleContext2.CONDITIONAL_EXPRESSION, ParserRuleContext2.XML_NAVIGATE_EXPR,
                        ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR, ParserRuleContext2.COMMA,
                        ParserRuleContext2.ARG_LIST_END };
                break;
            case ParserRuleContext2.MAPPING_CONSTRUCTOR:
            case ParserRuleContext2.MULTI_WAIT_FIELDS:
            case ParserRuleContext2.MAPPING_BP_OR_MAPPING_CONSTRUCTOR:
                alternatives = new int[] { ParserRuleContext2.CLOSE_BRACE, ParserRuleContext2.COMMA,
                        ParserRuleContext2.BINARY_OPERATOR, ParserRuleContext2.DOT,
                        ParserRuleContext2.ANNOT_CHAINING_TOKEN, ParserRuleContext2.OPTIONAL_CHAINING_TOKEN,
                        ParserRuleContext2.CONDITIONAL_EXPRESSION, ParserRuleContext2.XML_NAVIGATE_EXPR,
                        ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR };
                break;
            case ParserRuleContext2.COMPUTED_FIELD_NAME:
                // Here we give high priority to the comma. Therefore order of the below array matters.
                alternatives = new int[] { ParserRuleContext2.CLOSE_BRACKET,
                        ParserRuleContext2.BINARY_OPERATOR, ParserRuleContext2.DOT,
                        ParserRuleContext2.ANNOT_CHAINING_TOKEN, ParserRuleContext2.OPTIONAL_CHAINING_TOKEN,
                        ParserRuleContext2.CONDITIONAL_EXPRESSION, ParserRuleContext2.XML_NAVIGATE_EXPR,
                        ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR, ParserRuleContext2.OPEN_BRACKET };
                break;
            case ParserRuleContext2.LISTENERS_LIST:
                alternatives = new int[] { ParserRuleContext2.COMMA, ParserRuleContext2.BINARY_OPERATOR,
                        ParserRuleContext2.DOT, ParserRuleContext2.ANNOT_CHAINING_TOKEN,
                        ParserRuleContext2.OPTIONAL_CHAINING_TOKEN, ParserRuleContext2.CONDITIONAL_EXPRESSION,
                        ParserRuleContext2.XML_NAVIGATE_EXPR, ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR,
                        ParserRuleContext2.OPEN_BRACE };
                break;
            case ParserRuleContext2.LIST_CONSTRUCTOR:
            case ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR:
            case ParserRuleContext2.BRACKETED_LIST:
            case ParserRuleContext2.STMT_START_BRACKETED_LIST:
                alternatives = new int[] { ParserRuleContext2.COMMA, ParserRuleContext2.BINARY_OPERATOR,
                        ParserRuleContext2.DOT, ParserRuleContext2.ANNOT_CHAINING_TOKEN,
                        ParserRuleContext2.OPTIONAL_CHAINING_TOKEN, ParserRuleContext2.CONDITIONAL_EXPRESSION,
                        ParserRuleContext2.XML_NAVIGATE_EXPR, ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR,
                        ParserRuleContext2.CLOSE_BRACKET };
                break;
            case ParserRuleContext2.LET_EXPR_LET_VAR_DECL:
                alternatives = new int[] { ParserRuleContext2.COMMA, ParserRuleContext2.BINARY_OPERATOR,
                        ParserRuleContext2.DOT, ParserRuleContext2.ANNOT_CHAINING_TOKEN,
                        ParserRuleContext2.OPTIONAL_CHAINING_TOKEN, ParserRuleContext2.CONDITIONAL_EXPRESSION,
                        ParserRuleContext2.XML_NAVIGATE_EXPR, ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR,
                        ParserRuleContext2.IN_KEYWORD };
                break;
            case ParserRuleContext2.LET_CLAUSE_LET_VAR_DECL:
                alternatives = new int[] { ParserRuleContext2.COMMA, ParserRuleContext2.BINARY_OPERATOR,
                        ParserRuleContext2.DOT, ParserRuleContext2.ANNOT_CHAINING_TOKEN,
                        ParserRuleContext2.OPTIONAL_CHAINING_TOKEN, ParserRuleContext2.CONDITIONAL_EXPRESSION,
                        ParserRuleContext2.XML_NAVIGATE_EXPR, ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR,
                        ParserRuleContext2.LET_CLAUSE_END };
                break;
            case ParserRuleContext2.ORDER_KEY:
                alternatives = new int[] {ParserRuleContext2.ASCENDING_KEYWORD,
                        ParserRuleContext2.DESCENDING_KEYWORD, ParserRuleContext2.ORDER_KEY_LIST_END,
                        ParserRuleContext2.BINARY_OPERATOR, ParserRuleContext2.DOT,
                        ParserRuleContext2.ANNOT_CHAINING_TOKEN, ParserRuleContext2.OPTIONAL_CHAINING_TOKEN,
                        ParserRuleContext2.CONDITIONAL_EXPRESSION, ParserRuleContext2.XML_NAVIGATE_EXPR,
                        ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR };
                break;
            case ParserRuleContext2.QUERY_EXPRESSION:
                alternatives = new int[] { ParserRuleContext2.BINARY_OPERATOR, ParserRuleContext2.DOT,
                        ParserRuleContext2.ANNOT_CHAINING_TOKEN, ParserRuleContext2.OPTIONAL_CHAINING_TOKEN,
                        ParserRuleContext2.CONDITIONAL_EXPRESSION, ParserRuleContext2.XML_NAVIGATE_EXPR,
                        ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR, ParserRuleContext2.QUERY_PIPELINE_RHS };
                break;
            default:
                if (isParameter(parentCtx)) {
                    alternatives = new int[] { ParserRuleContext2.CLOSE_PARENTHESIS,
                            ParserRuleContext2.BINARY_OPERATOR, ParserRuleContext2.DOT,
                            ParserRuleContext2.ANNOT_CHAINING_TOKEN, ParserRuleContext2.OPTIONAL_CHAINING_TOKEN,
                            ParserRuleContext2.CONDITIONAL_EXPRESSION, ParserRuleContext2.XML_NAVIGATE_EXPR,
                            ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR, ParserRuleContext2.COMMA };
                }
                break;
        }

        if (alternatives != null) {
            if (allowFuncCall) {
                alternatives = modifyAlternativesWithArgListStart(alternatives);
            }
            return seekInAlternativesPaths(lookahead, currentDepth, currentMatches, alternatives, isEntryPoint);
        }

        int nextContext;
        if (parentCtx == ParserRuleContext2.IF_BLOCK || parentCtx == ParserRuleContext2.WHILE_BLOCK ||
                parentCtx == ParserRuleContext2.FOREACH_STMT) {
            nextContext = ParserRuleContext2.BLOCK_STMT;
        } else if (isStatement(parentCtx) || parentCtx == ParserRuleContext2.RECORD_FIELD ||
                parentCtx == ParserRuleContext2.OBJECT_MEMBER || parentCtx == ParserRuleContext2.LISTENER_DECL ||
                parentCtx == ParserRuleContext2.CONSTANT_DECL) {
            nextContext = ParserRuleContext2.SEMICOLON;
        } else if (parentCtx == ParserRuleContext2.ANNOTATIONS) {
            nextContext = ParserRuleContext2.ANNOTATION_END;
        } else if (parentCtx == ParserRuleContext2.ARRAY_TYPE_DESCRIPTOR) {
            nextContext = ParserRuleContext2.CLOSE_BRACKET;
        } else if (parentCtx == ParserRuleContext2.INTERPOLATION) {
            nextContext = ParserRuleContext2.CLOSE_BRACE;
        } else if (parentCtx == ParserRuleContext2.BRACED_EXPR_OR_ANON_FUNC_PARAMS) {
            nextContext = ParserRuleContext2.CLOSE_PARENTHESIS;
        } else if (parentCtx == ParserRuleContext2.FUNC_DEF) {
            // expression bodied func in module level
            nextContext = ParserRuleContext2.SEMICOLON;
        } else if (parentCtx == ParserRuleContext2.ALTERNATE_WAIT_EXPRS) {
            nextContext = ParserRuleContext2.ALTERNATE_WAIT_EXPR_LIST_END;
        } else if (parentCtx == ParserRuleContext2.CONDITIONAL_EXPRESSION) {
            nextContext = ParserRuleContext2.COLON;
        } else if (parentCtx == ParserRuleContext2.ENUM_MEMBER_LIST) {
            nextContext = ParserRuleContext2.ENUM_MEMBER_END;
        } else if (parentCtx == ParserRuleContext2.MATCH_STMT) {
            nextContext = ParserRuleContext2.MATCH_BODY;
        } else if (parentCtx == ParserRuleContext2.MATCH_BODY) {
            nextContext = ParserRuleContext2.RIGHT_DOUBLE_ARROW;
        } else {
            throw new IllegalStateException(String.valueOf(parentCtx));
        }

        alternatives = new int[] { ParserRuleContext2.BINARY_OPERATOR, ParserRuleContext2.IS_KEYWORD,
                ParserRuleContext2.DOT, ParserRuleContext2.ANNOT_CHAINING_TOKEN,
                ParserRuleContext2.OPTIONAL_CHAINING_TOKEN, ParserRuleContext2.CONDITIONAL_EXPRESSION,
                ParserRuleContext2.XML_NAVIGATE_EXPR, ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR,
                ParserRuleContext2.RIGHT_ARROW, ParserRuleContext2.SYNC_SEND_TOKEN, nextContext };

        if (allowFuncCall) {
            alternatives = modifyAlternativesWithArgListStart(alternatives);
        }
        return seekInAlternativesPaths(lookahead, currentDepth, currentMatches, alternatives, isEntryPoint);
    }

    private int[] modifyAlternativesWithArgListStart(int[] alternatives) {
        int[] newAlternatives = new int[alternatives.length + 1];
        System.arraycopy(alternatives, 0, newAlternatives, 0, alternatives.length);
        newAlternatives[alternatives.length] = ParserRuleContext2.ARG_LIST_START;
        return newAlternatives;
    }

    /**
     * Get the next parser rule/context given the current parser context.
     *
     * @param currentCtx Current parser context
     * @param nextLookahead Position of the next token to consider, relative to the position of the original error
     * @return Next parser context
     */
    @Override
    protected int getNextRule(int currentCtx, int nextLookahead) {
        // If this is a production, then push the context to the stack.
        // We can do this within the same switch-case ParserRuleContext2.that follows after this one.
        // But doing it separately for the sake of readability/maintainability.
        startContextIfRequired(currentCtx);

        int parentCtx;
        STToken nextToken;
        switch (currentCtx) {
            case ParserRuleContext2.EOF:
                return ParserRuleContext2.EOF;
            case ParserRuleContext2.COMP_UNIT:
                return ParserRuleContext2.TOP_LEVEL_NODE;
            case ParserRuleContext2.FUNC_DEF:
            case ParserRuleContext2.FUNC_DEF_OR_FUNC_TYPE:
            case ParserRuleContext2.FUNC_TYPE_DESC:
            case ParserRuleContext2.ANON_FUNC_EXPRESSION:
                return ParserRuleContext2.FUNCTION_KEYWORD;
            case ParserRuleContext2.EXTERNAL_FUNC_BODY:
                return ParserRuleContext2.ASSIGN_OP;
            case ParserRuleContext2.FUNC_BODY_BLOCK:
                return ParserRuleContext2.OPEN_BRACE;
            case ParserRuleContext2.STATEMENT:
            case ParserRuleContext2.STATEMENT_WITHOUT_ANNOTS:
                // We reach here only if an end of a block is reached.
                endContext(); // end statement
                return ParserRuleContext2.CLOSE_BRACE;
            case ParserRuleContext2.ASSIGN_OP:
                return getNextRuleForEqualOp();
            case ParserRuleContext2.COMPOUND_BINARY_OPERATOR:
                return ParserRuleContext2.ASSIGN_OP;
            case ParserRuleContext2.CLOSE_BRACE:
                return getNextRuleForCloseBrace(nextLookahead);
            case ParserRuleContext2.CLOSE_PARENTHESIS:
                return getNextRuleForCloseParenthsis();
            case ParserRuleContext2.EXPRESSION:
            case ParserRuleContext2.BASIC_LITERAL:
            case ParserRuleContext2.TERMINAL_EXPRESSION:
                return getNextRuleForExpr();
            case ParserRuleContext2.FUNC_NAME:
                return ParserRuleContext2.OPEN_PARENTHESIS;
            case ParserRuleContext2.OPEN_BRACE:
                return getNextRuleForOpenBrace(nextLookahead);
            case ParserRuleContext2.OPEN_PARENTHESIS:
                return getNextRuleForOpenParenthesis();
            case ParserRuleContext2.SEMICOLON:
                return getNextRuleForSemicolon(nextLookahead);
            case ParserRuleContext2.SIMPLE_TYPE_DESCRIPTOR:
                return ParserRuleContext2.TYPEDESC_RHS;
            case ParserRuleContext2.VARIABLE_NAME:
            case ParserRuleContext2.PARAMETER_NAME_RHS:
                return getNextRuleForVarName();
            case ParserRuleContext2.TOP_LEVEL_NODE_WITHOUT_MODIFIER:
                return ParserRuleContext2.FUNC_DEF_OR_FUNC_TYPE;
            case ParserRuleContext2.REQUIRED_PARAM:
            case ParserRuleContext2.DEFAULTABLE_PARAM:
            case ParserRuleContext2.REST_PARAM:
                return ParserRuleContext2.TYPE_DESC_IN_PARAM;
            case ParserRuleContext2.ASSIGNMENT_STMT:
                return ParserRuleContext2.VARIABLE_NAME;
            case ParserRuleContext2.COMPOUND_ASSIGNMENT_STMT:
                return ParserRuleContext2.VARIABLE_NAME;
            case ParserRuleContext2.VAR_DECL_STMT:
                return ParserRuleContext2.TYPE_DESC_IN_TYPE_BINDING_PATTERN;
            case ParserRuleContext2.EXPRESSION_RHS:
                return ParserRuleContext2.BINARY_OPERATOR;
            case ParserRuleContext2.BINARY_OPERATOR:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.COMMA:
                return getNextRuleForComma();
            case ParserRuleContext2.AFTER_PARAMETER_TYPE:
                return getNextRuleForParamType();
            case ParserRuleContext2.MODULE_TYPE_DEFINITION:
                return ParserRuleContext2.TYPE_KEYWORD;
            case ParserRuleContext2.CLOSED_RECORD_BODY_END:
                endContext();
                nextToken = this.tokenReader.peek(nextLookahead);
                if (nextToken.kind == SyntaxKind2.EOF_TOKEN) {
                    return ParserRuleContext2.EOF;
                }
                return ParserRuleContext2.TYPEDESC_RHS;
            case ParserRuleContext2.CLOSED_RECORD_BODY_START:
                return ParserRuleContext2.RECORD_FIELD_OR_RECORD_END;
            case ParserRuleContext2.ELLIPSIS:
                parentCtx = getParentContext();
                switch (parentCtx) {
                    case ParserRuleContext2.MAPPING_CONSTRUCTOR:
                    case ParserRuleContext2.ARG_LIST:
                        return ParserRuleContext2.EXPRESSION;
                    case ParserRuleContext2.TYPE_DESC_IN_TUPLE:
                    case ParserRuleContext2.STMT_START_BRACKETED_LIST:
                    case ParserRuleContext2.BRACKETED_LIST:
                        return ParserRuleContext2.CLOSE_BRACKET;
                    case ParserRuleContext2.REST_MATCH_PATTERN:
                        return ParserRuleContext2.VAR_KEYWORD;
                    default:
                        return ParserRuleContext2.VARIABLE_NAME;
                }
            case ParserRuleContext2.QUESTION_MARK:
                return getNextRuleForQuestionMark();
            case ParserRuleContext2.RECORD_TYPE_DESCRIPTOR:
                return ParserRuleContext2.RECORD_KEYWORD;
            case ParserRuleContext2.ASTERISK:
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext2.ARRAY_TYPE_DESCRIPTOR) {
                    return ParserRuleContext2.CLOSE_BRACKET;
                } else if (parentCtx == ParserRuleContext2.XML_ATOMIC_NAME_PATTERN) {
                    endContext();
                    return ParserRuleContext2.XML_NAME_PATTERN_RHS;
                }
                return ParserRuleContext2.TYPE_REFERENCE;
            case ParserRuleContext2.TYPE_NAME:
                return ParserRuleContext2.TYPE_DESC_IN_TYPE_DEF;
            case ParserRuleContext2.OBJECT_TYPE_DESCRIPTOR:
                return ParserRuleContext2.OBJECT_TYPE_DESCRIPTOR_START;
            case ParserRuleContext2.OBJECT_TYPE_QUALIFIER:
                return ParserRuleContext2.OBJECT_KEYWORD;
            case ParserRuleContext2.OPEN_BRACKET:
                return getNextRuleForOpenBracket();
            case ParserRuleContext2.CLOSE_BRACKET:
                return getNextRuleForCloseBracket();
            case ParserRuleContext2.DOT:
                return getNextRuleForDot();
            case ParserRuleContext2.BLOCK_STMT:
                return ParserRuleContext2.OPEN_BRACE;
            case ParserRuleContext2.IF_BLOCK:
                return ParserRuleContext2.IF_KEYWORD;
            case ParserRuleContext2.WHILE_BLOCK:
                return ParserRuleContext2.WHILE_KEYWORD;
            case ParserRuleContext2.CALL_STMT:
                return ParserRuleContext2.CALL_STMT_START;
            case ParserRuleContext2.PANIC_STMT:
                return ParserRuleContext2.PANIC_KEYWORD;
            case ParserRuleContext2.FUNC_CALL:
                // TODO: check this again
                return ParserRuleContext2.IMPORT_PREFIX;
            case ParserRuleContext2.IMPORT_PREFIX:
            case ParserRuleContext2.NAMESPACE_PREFIX:
                return ParserRuleContext2.SEMICOLON;
            case ParserRuleContext2.VERSION_NUMBER:
            case ParserRuleContext2.VERSION_KEYWORD:
                return ParserRuleContext2.MAJOR_VERSION;
            case ParserRuleContext2.SLASH:
                return ParserRuleContext2.IMPORT_MODULE_NAME;
            case ParserRuleContext2.IMPORT_ORG_OR_MODULE_NAME:
                return ParserRuleContext2.IMPORT_DECL_RHS;
            case ParserRuleContext2.IMPORT_MODULE_NAME:
                return ParserRuleContext2.AFTER_IMPORT_MODULE_NAME;
            case ParserRuleContext2.MAJOR_VERSION:
            case ParserRuleContext2.MINOR_VERSION:
            case ParserRuleContext2.IMPORT_SUB_VERSION:
                return ParserRuleContext2.MAJOR_MINOR_VERSION_END;
            case ParserRuleContext2.PATCH_VERSION:
                return ParserRuleContext2.IMPORT_PREFIX_DECL;
            case ParserRuleContext2.IMPORT_DECL:
                return ParserRuleContext2.IMPORT_KEYWORD;
            case ParserRuleContext2.CONTINUE_STATEMENT:
                return ParserRuleContext2.CONTINUE_KEYWORD;
            case ParserRuleContext2.BREAK_STATEMENT:
                return ParserRuleContext2.BREAK_KEYWORD;
            case ParserRuleContext2.RETURN_STMT:
                return ParserRuleContext2.RETURN_KEYWORD;
            case ParserRuleContext2.ACCESS_EXPRESSION:
                return ParserRuleContext2.VARIABLE_REF;
            case ParserRuleContext2.MAPPING_FIELD_NAME:
                return ParserRuleContext2.SPECIFIC_FIELD_RHS;
            case ParserRuleContext2.COLON:
                return getNextRuleForColon();
            case ParserRuleContext2.STRING_LITERAL:
                // We assume string literal is specifically used only in the mapping constructor key.
                return ParserRuleContext2.COLON;
            case ParserRuleContext2.COMPUTED_FIELD_NAME:
                return ParserRuleContext2.OPEN_BRACKET;
            case ParserRuleContext2.LISTENERS_LIST:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.SERVICE_DECL:
                return ParserRuleContext2.SERVICE_KEYWORD;
            case ParserRuleContext2.SERVICE_NAME:
                return ParserRuleContext2.ON_KEYWORD;
            case ParserRuleContext2.LISTENER_DECL:
                return ParserRuleContext2.LISTENER_KEYWORD;
            case ParserRuleContext2.CONSTANT_DECL:
                return ParserRuleContext2.CONST_KEYWORD;
            case ParserRuleContext2.CONST_DECL_TYPE:
                return ParserRuleContext2.CONST_DECL_RHS;
            case ParserRuleContext2.NIL_TYPE_DESCRIPTOR:
                return ParserRuleContext2.OPEN_PARENTHESIS;
            case ParserRuleContext2.TYPEOF_EXPRESSION:
                return ParserRuleContext2.TYPEOF_KEYWORD;
            case ParserRuleContext2.OPTIONAL_TYPE_DESCRIPTOR:
                return ParserRuleContext2.QUESTION_MARK;
            case ParserRuleContext2.UNARY_EXPRESSION:
                return ParserRuleContext2.UNARY_OPERATOR;
            case ParserRuleContext2.UNARY_OPERATOR:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.ARRAY_TYPE_DESCRIPTOR:
                return ParserRuleContext2.OPEN_BRACKET;
            case ParserRuleContext2.ARRAY_LENGTH:
                return ParserRuleContext2.CLOSE_BRACKET;
            case ParserRuleContext2.AT:
                return ParserRuleContext2.ANNOT_REFERENCE;
            case ParserRuleContext2.DOC_STRING:
                return ParserRuleContext2.ANNOTATIONS;
            case ParserRuleContext2.ANNOTATIONS:
                return ParserRuleContext2.AT;
            case ParserRuleContext2.MAPPING_CONSTRUCTOR:
                return ParserRuleContext2.OPEN_BRACE;
            case ParserRuleContext2.VARIABLE_REF:
            case ParserRuleContext2.TYPE_REFERENCE:
            case ParserRuleContext2.ANNOT_REFERENCE:
            case ParserRuleContext2.FIELD_ACCESS_IDENTIFIER:
                return ParserRuleContext2.QUALIFIED_IDENTIFIER;
            case ParserRuleContext2.QUALIFIED_IDENTIFIER:
            case ParserRuleContext2.XML_ATOMIC_NAME_IDENTIFIER:
                nextToken = this.tokenReader.peek(nextLookahead);
                if (nextToken.kind == SyntaxKind2.COLON_TOKEN) {
                    return ParserRuleContext2.COLON;
                }
                // Else this is a simple identifier. Hence fall through.
            case ParserRuleContext2.IDENTIFIER:
                return getNextRuleForIdentifier();
            case ParserRuleContext2.NIL_LITERAL:
                return ParserRuleContext2.OPEN_PARENTHESIS;
            case ParserRuleContext2.LOCAL_TYPE_DEFINITION_STMT:
                return ParserRuleContext2.TYPE_KEYWORD;
            case ParserRuleContext2.RIGHT_ARROW:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.DECIMAL_INTEGER_LITERAL:
            case ParserRuleContext2.HEX_INTEGER_LITERAL:
            case ParserRuleContext2.TYPE_NAME_OR_VAR_NAME:
                return getNextRuleForDecimalIntegerLiteral();
            case ParserRuleContext2.EXPRESSION_STATEMENT:
                return ParserRuleContext2.EXPRESSION_STATEMENT_START;
            case ParserRuleContext2.MAP_KEYWORD:
            case ParserRuleContext2.FUTURE_KEYWORD:
            case ParserRuleContext2.LOCK_STMT:
                return ParserRuleContext2.LOCK_KEYWORD;
            case ParserRuleContext2.LOCK_KEYWORD:
                return ParserRuleContext2.BLOCK_STMT;
            case ParserRuleContext2.RECORD_FIELD:
                return ParserRuleContext2.RECORD_FIELD_START;
            case ParserRuleContext2.ANNOTATION_TAG:
                return ParserRuleContext2.ANNOT_OPTIONAL_ATTACH_POINTS;
            case ParserRuleContext2.ANNOT_ATTACH_POINTS_LIST:
                return ParserRuleContext2.ATTACH_POINT;
            case ParserRuleContext2.FIELD_IDENT:
            case ParserRuleContext2.FUNCTION_IDENT:
            case ParserRuleContext2.IDENT_AFTER_OBJECT_IDENT:
            case ParserRuleContext2.SINGLE_KEYWORD_ATTACH_POINT_IDENT:
            case ParserRuleContext2.ATTACH_POINT:
                return ParserRuleContext2.ATTACH_POINT_END;
            case ParserRuleContext2.RECORD_FIELD_OR_RECORD_END:
                return ParserRuleContext2.RECORD_BODY_END;
            case ParserRuleContext2.OBJECT_IDENT:
                return ParserRuleContext2.IDENT_AFTER_OBJECT_IDENT;
            case ParserRuleContext2.RECORD_IDENT:
                return ParserRuleContext2.FIELD_IDENT;
            case ParserRuleContext2.RESOURCE_IDENT:
                return ParserRuleContext2.FUNCTION_IDENT;
            case ParserRuleContext2.ANNOTATION_DECL:
                return ParserRuleContext2.ANNOTATION_KEYWORD;
            case ParserRuleContext2.XML_NAMESPACE_DECLARATION:
                return ParserRuleContext2.XMLNS_KEYWORD;
            case ParserRuleContext2.CONSTANT_EXPRESSION:
                return ParserRuleContext2.CONSTANT_EXPRESSION_START;
            case ParserRuleContext2.XML_NAMESPACE_PREFIX_DECL:
                return ParserRuleContext2.SEMICOLON;
            case ParserRuleContext2.NAMED_WORKER_DECL:
                return ParserRuleContext2.WORKER_KEYWORD;
            case ParserRuleContext2.WORKER_NAME:
                return ParserRuleContext2.WORKER_NAME_RHS;
            case ParserRuleContext2.FORK_STMT:
                return ParserRuleContext2.FORK_KEYWORD;
            case ParserRuleContext2.SERVICE_CONSTRUCTOR_EXPRESSION:
                return ParserRuleContext2.SERVICE_KEYWORD;
            case ParserRuleContext2.XML_FILTER_EXPR:
                return ParserRuleContext2.DOT_LT_TOKEN;
            case ParserRuleContext2.DOT_LT_TOKEN:
                return ParserRuleContext2.XML_NAME_PATTERN;
            case ParserRuleContext2.XML_NAME_PATTERN:
                return ParserRuleContext2.XML_ATOMIC_NAME_PATTERN;
            case ParserRuleContext2.XML_ATOMIC_NAME_PATTERN:
                return ParserRuleContext2.XML_ATOMIC_NAME_PATTERN_START;
            case ParserRuleContext2.XML_STEP_EXPR:
                return ParserRuleContext2.XML_STEP_START;
            case ParserRuleContext2.SLASH_ASTERISK_TOKEN:
                return ParserRuleContext2.EXPRESSION_RHS;
            case ParserRuleContext2.DOUBLE_SLASH_DOUBLE_ASTERISK_LT_TOKEN:
            case ParserRuleContext2.SLASH_LT_TOKEN:
                return ParserRuleContext2.XML_NAME_PATTERN;
            default:
                return getNextRuleInternal(currentCtx, nextLookahead);

        }
    }

    private int getNextRuleInternal(int currentCtx, int nextLookahead) {
        int parentCtx;
        switch (currentCtx) {
            case ParserRuleContext2.LIST_CONSTRUCTOR:
                return ParserRuleContext2.OPEN_BRACKET;
            case ParserRuleContext2.FOREACH_STMT:
                return ParserRuleContext2.FOREACH_KEYWORD;
            case ParserRuleContext2.TYPE_CAST:
                return ParserRuleContext2.LT;
            case ParserRuleContext2.PIPE:
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext2.ALTERNATE_WAIT_EXPRS) {
                    return ParserRuleContext2.EXPRESSION;
                } else if (parentCtx == ParserRuleContext2.XML_NAME_PATTERN) {
                    return ParserRuleContext2.XML_ATOMIC_NAME_PATTERN;
                } else if (parentCtx == ParserRuleContext2.MATCH_PATTERN) {
                    return ParserRuleContext2.MATCH_PATTERN_START;
                }
                return ParserRuleContext2.TYPE_DESCRIPTOR;
            case ParserRuleContext2.TABLE_CONSTRUCTOR:
                return ParserRuleContext2.OPEN_BRACKET;
            case ParserRuleContext2.KEY_SPECIFIER:
                return ParserRuleContext2.KEY_KEYWORD;
            case ParserRuleContext2.ERROR_TYPE_PARAM_START:
                return ParserRuleContext2.ERROR_TYPE_PARAMS;
            case ParserRuleContext2.LET_EXPRESSION:
                return ParserRuleContext2.LET_KEYWORD;
            case ParserRuleContext2.LET_EXPR_LET_VAR_DECL:
            case ParserRuleContext2.LET_CLAUSE_LET_VAR_DECL:
                return ParserRuleContext2.LET_VAR_DECL_START;
            case ParserRuleContext2.ORDER_KEY:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.END_OF_TYPE_DESC:
                return getNextRuleForTypeDescriptor();
            case ParserRuleContext2.TYPED_BINDING_PATTERN:
                return ParserRuleContext2.TYPE_DESCRIPTOR;
            case ParserRuleContext2.BINDING_PATTERN_STARTING_IDENTIFIER:
                return ParserRuleContext2.VARIABLE_NAME;
            case ParserRuleContext2.REST_BINDING_PATTERN:
                return ParserRuleContext2.ELLIPSIS;
            case ParserRuleContext2.LIST_BINDING_PATTERN:
                return ParserRuleContext2.OPEN_BRACKET;
            case ParserRuleContext2.MAPPING_BINDING_PATTERN:
                return ParserRuleContext2.OPEN_BRACE;
            case ParserRuleContext2.FIELD_BINDING_PATTERN:
                return ParserRuleContext2.FIELD_BINDING_PATTERN_NAME;
            case ParserRuleContext2.FIELD_BINDING_PATTERN_NAME:
                return ParserRuleContext2.FIELD_BINDING_PATTERN_END;
            case ParserRuleContext2.PARAMETERIZED_TYPE:
                return ParserRuleContext2.LT;
            case ParserRuleContext2.LT:
                return getNextRuleForLt();
            case ParserRuleContext2.GT:
                return getNextRuleForGt(nextLookahead);
            case ParserRuleContext2.TEMPLATE_END:
                return ParserRuleContext2.EXPRESSION_RHS;
            case ParserRuleContext2.TEMPLATE_START:
                return ParserRuleContext2.TEMPLATE_BODY;
            case ParserRuleContext2.TEMPLATE_BODY:
                return ParserRuleContext2.TEMPLATE_MEMBER;
            case ParserRuleContext2.TEMPLATE_STRING:
                return ParserRuleContext2.TEMPLATE_STRING_RHS;
            case ParserRuleContext2.INTERPOLATION_START_TOKEN:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.ARG_LIST_START:
                return ParserRuleContext2.ARG_LIST;
            case ParserRuleContext2.ARG_LIST_END:
            case ParserRuleContext2.QUERY_EXPRESSION_END:
                endContext();
                return ParserRuleContext2.EXPRESSION_RHS;
            case ParserRuleContext2.ARG_LIST:
                return ParserRuleContext2.ARG_START_OR_ARG_LIST_END;
            case ParserRuleContext2.TYPE_DESC_IN_ANNOTATION_DECL:
            case ParserRuleContext2.TYPE_DESC_BEFORE_IDENTIFIER:
            case ParserRuleContext2.TYPE_DESC_IN_RECORD_FIELD:
            case ParserRuleContext2.TYPE_DESC_IN_PARAM:
            case ParserRuleContext2.TYPE_DESC_IN_TYPE_BINDING_PATTERN:
            case ParserRuleContext2.TYPE_DESC_IN_TYPE_DEF:
            case ParserRuleContext2.TYPE_DESC_IN_ANGLE_BRACKETS:
            case ParserRuleContext2.TYPE_DESC_IN_RETURN_TYPE_DESC:
            case ParserRuleContext2.TYPE_DESC_IN_EXPRESSION:
            case ParserRuleContext2.TYPE_DESC_IN_STREAM_TYPE_DESC:
            case ParserRuleContext2.TYPE_DESC_IN_PARENTHESIS:
            case ParserRuleContext2.TYPE_DESC_IN_NEW_EXPR:
            case ParserRuleContext2.TYPE_DESC_IN_TUPLE:
                return ParserRuleContext2.TYPE_DESCRIPTOR;
            case ParserRuleContext2.VAR_DECL_STARTED_WITH_DENTIFIER:
                // We come here trying to recover statement started with identifier,
                // and trying to match it against a var-decl. Since this wasn't a var-decl
                // originally, a context for type hasn't started yet. Therefore start a
                // a context manually here.
                startContext(ParserRuleContext2.TYPE_DESC_IN_TYPE_BINDING_PATTERN);
                return ParserRuleContext2.TYPEDESC_RHS;
            case ParserRuleContext2.INFERRED_TYPE_DESC:
                return ParserRuleContext2.GT;
            case ParserRuleContext2.ROW_TYPE_PARAM:
                return ParserRuleContext2.LT;
            case ParserRuleContext2.PARENTHESISED_TYPE_DESC_START:
                return ParserRuleContext2.TYPE_DESC_IN_PARENTHESIS;
            case ParserRuleContext2.SELECT_CLAUSE:
                return ParserRuleContext2.SELECT_KEYWORD;
            case ParserRuleContext2.WHERE_CLAUSE:
                return ParserRuleContext2.WHERE_KEYWORD;
            case ParserRuleContext2.FROM_CLAUSE:
                return ParserRuleContext2.FROM_KEYWORD;
            case ParserRuleContext2.LET_CLAUSE:
                return ParserRuleContext2.LET_KEYWORD;
            case ParserRuleContext2.ORDER_BY_CLAUSE:
                return ParserRuleContext2.ORDER_KEYWORD;
            case ParserRuleContext2.QUERY_EXPRESSION:
                return ParserRuleContext2.FROM_CLAUSE;
            case ParserRuleContext2.TABLE_CONSTRUCTOR_OR_QUERY_EXPRESSION:
                return ParserRuleContext2.TABLE_CONSTRUCTOR_OR_QUERY_START;
            case ParserRuleContext2.BITWISE_AND_OPERATOR:
                return ParserRuleContext2.TYPE_DESCRIPTOR;
            case ParserRuleContext2.EXPR_FUNC_BODY_START:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.MODULE_LEVEL_AMBIGUOUS_FUNC_TYPE_DESC_RHS:
                endContext();
                // We come here trying to recover module-var-decl/object-member started with function,
                // keyword and trying to match it against a var-decl. Since this wasn't a var-decl
                // originally, a context for type hasn't started yet. Therefore start a
                // a context manually here.
                startContext(ParserRuleContext2.VAR_DECL_STMT);
                startContext(ParserRuleContext2.TYPE_DESC_IN_TYPE_BINDING_PATTERN);
                return ParserRuleContext2.TYPEDESC_RHS;
            case ParserRuleContext2.STMT_LEVEL_AMBIGUOUS_FUNC_TYPE_DESC_RHS:
                endContext();
                // We come here trying to recover statement started with function-keyword,
                // and trying to match it against a var-decl. Since this wasn't a var-decl
                // originally, a context for type hasn't started yet. Therefore switch to
                // var-decl context
                switchContext(ParserRuleContext2.VAR_DECL_STMT);
                startContext(ParserRuleContext2.TYPE_DESC_IN_TYPE_BINDING_PATTERN);
                return ParserRuleContext2.TYPEDESC_RHS;
            case ParserRuleContext2.FUNC_TYPE_DESC_END:
                endContext();
                return ParserRuleContext2.TYPEDESC_RHS;
            case ParserRuleContext2.IMPLICIT_ANON_FUNC_PARAM:
                return ParserRuleContext2.BRACED_EXPR_OR_ANON_FUNC_PARAM_RHS;
            case ParserRuleContext2.EXPLICIT_ANON_FUNC_EXPR_BODY_START:
                endContext(); // end explicit anon-func
                return ParserRuleContext2.EXPR_FUNC_BODY_START;
            case ParserRuleContext2.OBJECT_MEMBER:
                return ParserRuleContext2.OBJECT_MEMBER_START;
            case ParserRuleContext2.ANNOTATION_END:
                return getNextRuleForAnnotationEnd(nextLookahead);
            case ParserRuleContext2.PLUS_TOKEN:
            case ParserRuleContext2.MINUS_TOKEN:
                return ParserRuleContext2.SIGNED_INT_OR_FLOAT_RHS;
            case ParserRuleContext2.SIGNED_INT_OR_FLOAT_RHS:
                return getNextRuleForExpr();
            case ParserRuleContext2.TUPLE_TYPE_DESC_START:
                return ParserRuleContext2.TYPE_DESC_IN_TUPLE;
            case ParserRuleContext2.TYPE_DESC_IN_TUPLE_RHS:
                return ParserRuleContext2.OPEN_BRACKET;
            case ParserRuleContext2.WORKER_NAME_OR_METHOD_NAME:
                return ParserRuleContext2.WORKER_NAME_OR_METHOD_NAME;
            case ParserRuleContext2.DEFAULT_WORKER_NAME_IN_ASYNC_SEND:
                return ParserRuleContext2.SEMICOLON;
            case ParserRuleContext2.SYNC_SEND_TOKEN:
                return ParserRuleContext2.PEER_WORKER_NAME;
            case ParserRuleContext2.LEFT_ARROW_TOKEN:
                return ParserRuleContext2.RECEIVE_WORKERS;
            case ParserRuleContext2.MULTI_RECEIVE_WORKERS:
                return ParserRuleContext2.OPEN_BRACE;
            case ParserRuleContext2.RECEIVE_FIELD_NAME:
                return ParserRuleContext2.COLON;

            case ParserRuleContext2.WAIT_FIELD_NAME:
                return ParserRuleContext2.WAIT_FIELD_NAME_RHS;
            case ParserRuleContext2.ALTERNATE_WAIT_EXPR_LIST_END:
                return getNextRuleForWaitExprListEnd();
            case ParserRuleContext2.MULTI_WAIT_FIELDS:
                return ParserRuleContext2.OPEN_BRACE;
            case ParserRuleContext2.ALTERNATE_WAIT_EXPRS:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.ANNOT_CHAINING_TOKEN:
                return ParserRuleContext2.FIELD_ACCESS_IDENTIFIER;
            case ParserRuleContext2.DO_CLAUSE:
                return ParserRuleContext2.DO_KEYWORD;
            case ParserRuleContext2.LET_CLAUSE_END:
            case ParserRuleContext2.ORDER_CLAUSE_END:
                endContext();
                return ParserRuleContext2.QUERY_PIPELINE_RHS;
            case ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR:
                return ParserRuleContext2.OPEN_BRACKET;
            case ParserRuleContext2.OPTIONAL_CHAINING_TOKEN:
                return ParserRuleContext2.FIELD_ACCESS_IDENTIFIER;
            case ParserRuleContext2.CONDITIONAL_EXPRESSION:
                return ParserRuleContext2.QUESTION_MARK;
            case ParserRuleContext2.TRANSACTION_STMT:
                return ParserRuleContext2.TRANSACTION_KEYWORD;
            case ParserRuleContext2.RETRY_STMT:
                return ParserRuleContext2.RETRY_KEYWORD;
            case ParserRuleContext2.ROLLBACK_STMT:
                return ParserRuleContext2.ROLLBACK_KEYWORD;

            case ParserRuleContext2.MODULE_ENUM_DECLARATION:
                return ParserRuleContext2.ENUM_KEYWORD;
            case ParserRuleContext2.MODULE_ENUM_NAME:
                return ParserRuleContext2.OPEN_BRACE;
            case ParserRuleContext2.ENUM_MEMBER_LIST:
                return ParserRuleContext2.ENUM_MEMBER_START;
            case ParserRuleContext2.ENUM_MEMBER_NAME:
                return ParserRuleContext2.ENUM_MEMBER_RHS;
            case ParserRuleContext2.TYPED_BINDING_PATTERN_TYPE_RHS:
                return ParserRuleContext2.BINDING_PATTERN;
            case ParserRuleContext2.UNION_OR_INTERSECTION_TOKEN:
                return ParserRuleContext2.TYPE_DESCRIPTOR;
            case ParserRuleContext2.MATCH_STMT:
                return ParserRuleContext2.MATCH_KEYWORD;
            case ParserRuleContext2.MATCH_BODY:
                return ParserRuleContext2.OPEN_BRACE;
            case ParserRuleContext2.MATCH_PATTERN:
                return ParserRuleContext2.MATCH_PATTERN_START;
            case ParserRuleContext2.MATCH_PATTERN_END:
                endContext(); // End match pattern context
                return getNextRuleForMatchPattern();
            case ParserRuleContext2.RIGHT_DOUBLE_ARROW:
                // Assumption: RIGHT_DOUBLE_ARROW is only occurs in match clauses
                // in expr-func-body, it is used by a different alias.
                return ParserRuleContext2.BLOCK_STMT;
            case ParserRuleContext2.LIST_MATCH_PATTERN:
                return ParserRuleContext2.OPEN_BRACKET;
            case ParserRuleContext2.REST_MATCH_PATTERN:
                return ParserRuleContext2.ELLIPSIS;
            case ParserRuleContext2.FUNCTIONAL_BINDING_PATTERN:
                return ParserRuleContext2.OPEN_PARENTHESIS;
            case ParserRuleContext2.NAMED_ARG_BINDING_PATTERN:
                return ParserRuleContext2.ASSIGN_OP;
            case ParserRuleContext2.MAPPING_MATCH_PATTERN:
                return ParserRuleContext2.OPEN_BRACE;
            case ParserRuleContext2.FUNCTIONAL_MATCH_PATTERN:
                return ParserRuleContext2.FUNCTIONAL_MATCH_PATTERN_START;
            case ParserRuleContext2.NAMED_ARG_MATCH_PATTERN:
                return ParserRuleContext2.IDENTIFIER;
            default:
                return getNextRuleForKeywords(currentCtx, nextLookahead);
        }
    }

    private int getNextRuleForKeywords(int currentCtx, int nextLookahead) {
        int parentCtx;
        switch (currentCtx) {
            case ParserRuleContext2.PUBLIC_KEYWORD:
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext2.OBJECT_TYPE_DESCRIPTOR ||
                        parentCtx == ParserRuleContext2.OBJECT_MEMBER) {
                    return ParserRuleContext2.OBJECT_FUNC_OR_FIELD_WITHOUT_VISIBILITY;
                } else if (isParameter(parentCtx)) {
                    return ParserRuleContext2.TYPE_DESC_IN_PARAM;
                }
                return ParserRuleContext2.TOP_LEVEL_NODE_WITHOUT_MODIFIER;
            case ParserRuleContext2.PRIVATE_KEYWORD:
                return ParserRuleContext2.OBJECT_FUNC_OR_FIELD_WITHOUT_VISIBILITY;
            case ParserRuleContext2.ON_KEYWORD:
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext2.ANNOTATION_DECL) {
                    return ParserRuleContext2.ANNOT_ATTACH_POINTS_LIST;
                }
                return ParserRuleContext2.LISTENERS_LIST;
            case ParserRuleContext2.RESOURCE_KEYWORD:
                return ParserRuleContext2.FUNC_DEF;
            case ParserRuleContext2.SERVICE_KEYWORD:
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext2.SERVICE_CONSTRUCTOR_EXPRESSION) {
                    return ParserRuleContext2.LISTENERS_LIST;
                }
                return ParserRuleContext2.OPTIONAL_SERVICE_NAME;
            case ParserRuleContext2.LISTENER_KEYWORD:
                return ParserRuleContext2.TYPE_DESC_BEFORE_IDENTIFIER;
            case ParserRuleContext2.FINAL_KEYWORD:
                // Assume the final keyword is only used in var-decl.
                return ParserRuleContext2.TYPE_DESC_IN_TYPE_BINDING_PATTERN;
            case ParserRuleContext2.CONST_KEYWORD:
                return ParserRuleContext2.CONST_DECL_TYPE;
            case ParserRuleContext2.TYPEOF_KEYWORD:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.IS_KEYWORD:
                return ParserRuleContext2.TYPE_DESC_IN_EXPRESSION;
            case ParserRuleContext2.NULL_KEYWORD:
                return ParserRuleContext2.EXPRESSION_RHS;
            case ParserRuleContext2.ANNOTATION_KEYWORD:
                return ParserRuleContext2.ANNOT_DECL_OPTIONAL_TYPE;
            case ParserRuleContext2.SOURCE_KEYWORD:
                return ParserRuleContext2.ATTACH_POINT_IDENT;
            case ParserRuleContext2.XMLNS_KEYWORD:
                return ParserRuleContext2.CONSTANT_EXPRESSION;
            case ParserRuleContext2.WORKER_KEYWORD:
                return ParserRuleContext2.WORKER_NAME;
            case ParserRuleContext2.IF_KEYWORD:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.ELSE_KEYWORD:
                return ParserRuleContext2.ELSE_BODY;
            case ParserRuleContext2.WHILE_KEYWORD:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.CHECKING_KEYWORD:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.FAIL_KEYWORD:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.PANIC_KEYWORD:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.IMPORT_KEYWORD:
                return ParserRuleContext2.IMPORT_ORG_OR_MODULE_NAME;
            case ParserRuleContext2.AS_KEYWORD:
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext2.IMPORT_DECL) {
                    return ParserRuleContext2.IMPORT_PREFIX;
                } else if (parentCtx == ParserRuleContext2.XML_NAMESPACE_DECLARATION) {
                    return ParserRuleContext2.NAMESPACE_PREFIX;
                }
                throw new IllegalStateException();
            case ParserRuleContext2.CONTINUE_KEYWORD:
            case ParserRuleContext2.BREAK_KEYWORD:
                return ParserRuleContext2.SEMICOLON;
            case ParserRuleContext2.RETURN_KEYWORD:
                return ParserRuleContext2.RETURN_STMT_RHS;
            case ParserRuleContext2.EXTERNAL_KEYWORD:
                return ParserRuleContext2.SEMICOLON;
            case ParserRuleContext2.FUNCTION_KEYWORD:
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext2.ANON_FUNC_EXPRESSION ||
                        parentCtx == ParserRuleContext2.FUNC_TYPE_DESC) {
                    return ParserRuleContext2.OPEN_PARENTHESIS;
                }
                return ParserRuleContext2.FUNCTION_KEYWORD_RHS;
            case ParserRuleContext2.RETURNS_KEYWORD:
                return ParserRuleContext2.TYPE_DESC_IN_RETURN_TYPE_DESC;
            case ParserRuleContext2.RECORD_KEYWORD:
                return ParserRuleContext2.RECORD_BODY_START;
            case ParserRuleContext2.TYPE_KEYWORD:
                return ParserRuleContext2.TYPE_NAME;
            case ParserRuleContext2.OBJECT_KEYWORD:
                return ParserRuleContext2.OPEN_BRACE;
            case ParserRuleContext2.REMOTE_KEYWORD:
                return ParserRuleContext2.FUNCTION_KEYWORD;
            case ParserRuleContext2.ABSTRACT_KEYWORD:
            case ParserRuleContext2.CLIENT_KEYWORD:
                return ParserRuleContext2.OBJECT_KEYWORD;
            case ParserRuleContext2.FORK_KEYWORD:
                return ParserRuleContext2.OPEN_BRACE;
            case ParserRuleContext2.TRAP_KEYWORD:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.FOREACH_KEYWORD:
                return ParserRuleContext2.TYPE_DESC_IN_TYPE_BINDING_PATTERN;
            case ParserRuleContext2.IN_KEYWORD:
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext2.LET_EXPR_LET_VAR_DECL) {
                    endContext(); // end let-expr-let-var-decl
                }
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.KEY_KEYWORD:
                if (isInTypeDescContext()) {
                    return ParserRuleContext2.KEY_CONSTRAINTS_RHS;
                }
                return ParserRuleContext2.OPEN_PARENTHESIS;
            case ParserRuleContext2.ERROR_KEYWORD:
                if (isInTypeDescContext()) {
                    return ParserRuleContext2.ERROR_TYPE_PARAM_START;
                }
                if (getParentContext() == ParserRuleContext2.FUNCTIONAL_MATCH_PATTERN) {
                    return ParserRuleContext2.OPEN_PARENTHESIS;
                }
                return ParserRuleContext2.ARG_LIST_START;
            case ParserRuleContext2.LET_KEYWORD:
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext2.QUERY_EXPRESSION) {
                    return ParserRuleContext2.LET_CLAUSE_LET_VAR_DECL;
                } else if (parentCtx == ParserRuleContext2.LET_CLAUSE_LET_VAR_DECL) {
                    endContext(); // end let-clause-let-var-decl
                    return ParserRuleContext2.LET_CLAUSE_LET_VAR_DECL;
                }
                return ParserRuleContext2.LET_EXPR_LET_VAR_DECL;
            case ParserRuleContext2.TABLE_KEYWORD:
                if (isInTypeDescContext()) {
                    return ParserRuleContext2.ROW_TYPE_PARAM;
                }
                return ParserRuleContext2.TABLE_KEYWORD_RHS;
            case ParserRuleContext2.STREAM_KEYWORD:
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext2.TABLE_CONSTRUCTOR_OR_QUERY_EXPRESSION) {
                    return ParserRuleContext2.QUERY_EXPRESSION;
                }
                return ParserRuleContext2.LT;
            case ParserRuleContext2.NEW_KEYWORD:
                return ParserRuleContext2.NEW_KEYWORD_RHS;
            case ParserRuleContext2.XML_KEYWORD:
            case ParserRuleContext2.STRING_KEYWORD:
            case ParserRuleContext2.BASE16_KEYWORD:
            case ParserRuleContext2.BASE64_KEYWORD:
                return ParserRuleContext2.TEMPLATE_START;
            case ParserRuleContext2.SELECT_KEYWORD:
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext2.QUERY_EXPRESSION) {
                    endContext(); // end query-expression
                }
                if (parentCtx == ParserRuleContext2.LET_CLAUSE_LET_VAR_DECL) {
                    endContext(); // end let-clause-let-var-decl
                    endContext(); // end query-expression
                }
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.WHERE_KEYWORD:
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext2.LET_CLAUSE_LET_VAR_DECL) {
                    endContext(); // end let-clause-let-var-decl
                }
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.ORDER_KEYWORD:
                return ParserRuleContext2.BY_KEYWORD;
            case ParserRuleContext2.BY_KEYWORD:
                return ParserRuleContext2.ORDER_KEY;
            case ParserRuleContext2.ASCENDING_KEYWORD:
            case ParserRuleContext2.DESCENDING_KEYWORD:
                return ParserRuleContext2.ORDER_DIRECTION_RHS;
            case ParserRuleContext2.FROM_KEYWORD:
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext2.LET_CLAUSE_LET_VAR_DECL) {
                    endContext(); // end let-clause-let-var-decl
                }
                return ParserRuleContext2.TYPE_DESC_IN_TYPE_BINDING_PATTERN;
            case ParserRuleContext2.START_KEYWORD:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.FLUSH_KEYWORD:
                return ParserRuleContext2.OPTIONAL_PEER_WORKER;
            case ParserRuleContext2.PEER_WORKER_NAME:
            case ParserRuleContext2.DEFAULT_KEYWORD:
                if (getParentContext() == ParserRuleContext2.MULTI_RECEIVE_WORKERS) {
                    return ParserRuleContext2.RECEIVE_FIELD_END;
                }
                return ParserRuleContext2.EXPRESSION_RHS;
            case ParserRuleContext2.WAIT_KEYWORD:
                return ParserRuleContext2.WAIT_KEYWORD_RHS;
            case ParserRuleContext2.DO_KEYWORD:
                return ParserRuleContext2.OPEN_BRACE;
            case ParserRuleContext2.TRANSACTION_KEYWORD:
                return ParserRuleContext2.BLOCK_STMT;
            case ParserRuleContext2.COMMIT_KEYWORD:
                return ParserRuleContext2.EXPRESSION_RHS;
            case ParserRuleContext2.ROLLBACK_KEYWORD:
                return ParserRuleContext2.ROLLBACK_RHS;
            case ParserRuleContext2.RETRY_KEYWORD:
                return ParserRuleContext2.RETRY_KEYWORD_RHS;
            case ParserRuleContext2.TRANSACTIONAL_KEYWORD:
                return ParserRuleContext2.EXPRESSION_RHS;
            case ParserRuleContext2.ENUM_KEYWORD:
                return ParserRuleContext2.MODULE_ENUM_NAME;
            case ParserRuleContext2.MATCH_KEYWORD:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.READONLY_KEYWORD:
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext2.MAPPING_CONSTRUCTOR ||
                        parentCtx == ParserRuleContext2.MAPPING_FIELD) {
                    return ParserRuleContext2.SPECIFIC_FIELD;
                }
                throw new IllegalStateException("cannot find the next rule for: " + currentCtx);
            case ParserRuleContext2.DISTINCT_KEYWORD:
                return ParserRuleContext2.TYPE_DESCRIPTOR;
            case ParserRuleContext2.VAR_KEYWORD:
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext2.REST_MATCH_PATTERN) {
                    return ParserRuleContext2.VARIABLE_NAME;
                }
                return ParserRuleContext2.BINDING_PATTERN;
            default:
                throw new IllegalStateException("cannot find the next rule for: " + currentCtx);
        }
    }

    private void startContextIfRequired(int currentCtx) {
        switch (currentCtx) {
            case ParserRuleContext2.COMP_UNIT:
            case ParserRuleContext2.FUNC_DEF_OR_FUNC_TYPE:
            case ParserRuleContext2.ANON_FUNC_EXPRESSION:
            case ParserRuleContext2.FUNC_DEF:
            case ParserRuleContext2.FUNC_TYPE_DESC:
            case ParserRuleContext2.EXTERNAL_FUNC_BODY:
            case ParserRuleContext2.FUNC_BODY_BLOCK:
            case ParserRuleContext2.STATEMENT:
            case ParserRuleContext2.STATEMENT_WITHOUT_ANNOTS:
            case ParserRuleContext2.VAR_DECL_STMT:
            case ParserRuleContext2.ASSIGNMENT_STMT:
            case ParserRuleContext2.REQUIRED_PARAM:
            case ParserRuleContext2.DEFAULTABLE_PARAM:
            case ParserRuleContext2.REST_PARAM:
            case ParserRuleContext2.MODULE_TYPE_DEFINITION:
            case ParserRuleContext2.RECORD_FIELD:
            case ParserRuleContext2.RECORD_TYPE_DESCRIPTOR:
            case ParserRuleContext2.OBJECT_TYPE_DESCRIPTOR:
            case ParserRuleContext2.ARG_LIST:
            case ParserRuleContext2.OBJECT_FUNC_OR_FIELD:
            case ParserRuleContext2.IF_BLOCK:
            case ParserRuleContext2.BLOCK_STMT:
            case ParserRuleContext2.WHILE_BLOCK:
            case ParserRuleContext2.PANIC_STMT:
            case ParserRuleContext2.CALL_STMT:
            case ParserRuleContext2.IMPORT_DECL:
            case ParserRuleContext2.CONTINUE_STATEMENT:
            case ParserRuleContext2.BREAK_STATEMENT:
            case ParserRuleContext2.RETURN_STMT:
            case ParserRuleContext2.COMPUTED_FIELD_NAME:
            case ParserRuleContext2.LISTENERS_LIST:
            case ParserRuleContext2.SERVICE_DECL:
            case ParserRuleContext2.LISTENER_DECL:
            case ParserRuleContext2.CONSTANT_DECL:
            case ParserRuleContext2.NIL_TYPE_DESCRIPTOR:
            case ParserRuleContext2.COMPOUND_ASSIGNMENT_STMT:
            case ParserRuleContext2.OPTIONAL_TYPE_DESCRIPTOR:
            case ParserRuleContext2.ARRAY_TYPE_DESCRIPTOR:
            case ParserRuleContext2.ANNOTATIONS:
            case ParserRuleContext2.VARIABLE_REF:
            case ParserRuleContext2.TYPE_REFERENCE:
            case ParserRuleContext2.ANNOT_REFERENCE:
            case ParserRuleContext2.FIELD_ACCESS_IDENTIFIER:
            case ParserRuleContext2.MAPPING_CONSTRUCTOR:
            case ParserRuleContext2.LOCAL_TYPE_DEFINITION_STMT:
            case ParserRuleContext2.EXPRESSION_STATEMENT:
            case ParserRuleContext2.NIL_LITERAL:
            case ParserRuleContext2.LOCK_STMT:
            case ParserRuleContext2.ANNOTATION_DECL:
            case ParserRuleContext2.ANNOT_ATTACH_POINTS_LIST:
            case ParserRuleContext2.XML_NAMESPACE_DECLARATION:
            case ParserRuleContext2.CONSTANT_EXPRESSION:
            case ParserRuleContext2.NAMED_WORKER_DECL:
            case ParserRuleContext2.FORK_STMT:
            case ParserRuleContext2.FOREACH_STMT:
            case ParserRuleContext2.LIST_CONSTRUCTOR:
            case ParserRuleContext2.TYPE_CAST:
            case ParserRuleContext2.KEY_SPECIFIER:
            case ParserRuleContext2.LET_EXPR_LET_VAR_DECL:
            case ParserRuleContext2.LET_CLAUSE_LET_VAR_DECL:
            case ParserRuleContext2.ORDER_KEY:
            case ParserRuleContext2.ROW_TYPE_PARAM:
            case ParserRuleContext2.TABLE_CONSTRUCTOR_OR_QUERY_EXPRESSION:
            case ParserRuleContext2.OBJECT_MEMBER:
            case ParserRuleContext2.LIST_BINDING_PATTERN:
            case ParserRuleContext2.MAPPING_BINDING_PATTERN:
            case ParserRuleContext2.REST_BINDING_PATTERN:
            case ParserRuleContext2.TYPED_BINDING_PATTERN:
            case ParserRuleContext2.BINDING_PATTERN_STARTING_IDENTIFIER:
            case ParserRuleContext2.MULTI_RECEIVE_WORKERS:
            case ParserRuleContext2.MULTI_WAIT_FIELDS:
            case ParserRuleContext2.ALTERNATE_WAIT_EXPRS:
            case ParserRuleContext2.DO_CLAUSE:
            case ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR:
            case ParserRuleContext2.CONDITIONAL_EXPRESSION:
            case ParserRuleContext2.TRANSACTION_STMT:
            case ParserRuleContext2.RETRY_STMT:
            case ParserRuleContext2.ROLLBACK_STMT:
            case ParserRuleContext2.MODULE_ENUM_DECLARATION:
            case ParserRuleContext2.ENUM_MEMBER_LIST:
            case ParserRuleContext2.SERVICE_CONSTRUCTOR_EXPRESSION:
            case ParserRuleContext2.XML_NAME_PATTERN:
            case ParserRuleContext2.XML_ATOMIC_NAME_PATTERN:
            case ParserRuleContext2.MATCH_STMT:
            case ParserRuleContext2.MATCH_BODY:
            case ParserRuleContext2.MATCH_PATTERN:
            case ParserRuleContext2.LIST_MATCH_PATTERN:
            case ParserRuleContext2.REST_MATCH_PATTERN:
            case ParserRuleContext2.FUNCTIONAL_BINDING_PATTERN:
            case ParserRuleContext2.MAPPING_MATCH_PATTERN:
            case ParserRuleContext2.FUNCTIONAL_MATCH_PATTERN:
            case ParserRuleContext2.NAMED_ARG_MATCH_PATTERN:

                // Contexts that expect a type
            case ParserRuleContext2.TYPE_DESC_IN_ANNOTATION_DECL:
            case ParserRuleContext2.TYPE_DESC_BEFORE_IDENTIFIER:
            case ParserRuleContext2.TYPE_DESC_IN_RECORD_FIELD:
            case ParserRuleContext2.TYPE_DESC_IN_PARAM:
            case ParserRuleContext2.TYPE_DESC_IN_TYPE_BINDING_PATTERN:
            case ParserRuleContext2.TYPE_DESC_IN_TYPE_DEF:
            case ParserRuleContext2.TYPE_DESC_IN_ANGLE_BRACKETS:
            case ParserRuleContext2.TYPE_DESC_IN_RETURN_TYPE_DESC:
            case ParserRuleContext2.TYPE_DESC_IN_EXPRESSION:
            case ParserRuleContext2.TYPE_DESC_IN_STREAM_TYPE_DESC:
            case ParserRuleContext2.TYPE_DESC_IN_PARENTHESIS:
            case ParserRuleContext2.TYPE_DESC_IN_NEW_EXPR:
            case ParserRuleContext2.TYPE_DESC_IN_TUPLE:
                startContext(currentCtx);
                break;
            default:
                break;
        }

        switch (currentCtx) {
            case ParserRuleContext2.TABLE_CONSTRUCTOR:
            case ParserRuleContext2.QUERY_EXPRESSION:
                switchContext(currentCtx);
                break;
            default:
                break;
        }
    }

    private int getNextRuleForCloseParenthsis() {
        int parentCtx;
        parentCtx = getParentContext();
        if (parentCtx == ParserRuleContext2.PARAM_LIST) {
            endContext(); // end parameters
            return ParserRuleContext2.FUNC_OPTIONAL_RETURNS;
        } else if (isParameter(parentCtx)) {
            endContext(); // end parameters
            endContext(); // end parameter
            return ParserRuleContext2.FUNC_OPTIONAL_RETURNS;
        } else if (parentCtx == ParserRuleContext2.NIL_TYPE_DESCRIPTOR) {
            endContext();
            // After parsing nil type descriptor all the other parsing is same as next rule of simple type
            return ParserRuleContext2.TYPEDESC_RHS;
        } else if (parentCtx == ParserRuleContext2.NIL_LITERAL) {
            endContext();
            return getNextRuleForExpr();
        } else if (parentCtx == ParserRuleContext2.KEY_SPECIFIER) {
            endContext(); // end key-specifier
            if (isInTypeDescContext()) {
                return ParserRuleContext2.TYPEDESC_RHS;
            }
            return ParserRuleContext2.TABLE_CONSTRUCTOR_OR_QUERY_RHS;
        } else if (isInTypeDescContext()) {
            return ParserRuleContext2.TYPEDESC_RHS;
        } else if (parentCtx == ParserRuleContext2.BRACED_EXPR_OR_ANON_FUNC_PARAMS) {
            endContext(); // end infered-param/parenthesised-expr context
            return ParserRuleContext2.INFER_PARAM_END_OR_PARENTHESIS_END;
        } else if (parentCtx == ParserRuleContext2.FUNCTIONAL_MATCH_PATTERN) {
            endContext();
            return getNextRuleForMatchPattern();
        } else if (parentCtx == ParserRuleContext2.NAMED_ARG_MATCH_PATTERN) {
            endContext(); // end named arg math pattern context
            endContext(); // end functional match pattern context
            return getNextRuleForMatchPattern();
        } else if (parentCtx == ParserRuleContext2.FUNCTIONAL_BINDING_PATTERN) {
            endContext(); // end functional-binding-pattern
            return getNextRuleForBindingPattern();
        }
        return ParserRuleContext2.EXPRESSION_RHS;
    }

    private int getNextRuleForOpenParenthesis() {
        int parentCtx = getParentContext();
        if (parentCtx == ParserRuleContext2.EXPRESSION_STATEMENT) {
            return ParserRuleContext2.EXPRESSION_STATEMENT_START;
        } else if (isStatement(parentCtx) || isExpressionContext(parentCtx) ||
                parentCtx == ParserRuleContext2.ARRAY_TYPE_DESCRIPTOR) {
            return ParserRuleContext2.EXPRESSION;
        } else if (parentCtx == ParserRuleContext2.FUNC_DEF_OR_FUNC_TYPE ||
                parentCtx == ParserRuleContext2.FUNC_TYPE_DESC || parentCtx == ParserRuleContext2.FUNC_DEF ||
                parentCtx == ParserRuleContext2.ANON_FUNC_EXPRESSION ||
                parentCtx == ParserRuleContext2.FUNC_TYPE_DESC_OR_ANON_FUNC) {
            // TODO: find a better way
            startContext(ParserRuleContext2.PARAM_LIST);
            return ParserRuleContext2.PARAM_LIST;
        } else if (parentCtx == ParserRuleContext2.NIL_TYPE_DESCRIPTOR || parentCtx == ParserRuleContext2.NIL_LITERAL) {
            return ParserRuleContext2.CLOSE_PARENTHESIS;
        } else if (parentCtx == ParserRuleContext2.KEY_SPECIFIER) {
            return ParserRuleContext2.KEY_SPECIFIER_RHS;
        } else if (isInTypeDescContext()) {
            // if the parent context is table type desc then we are in key specifier context.hence start context
            startContext(ParserRuleContext2.KEY_SPECIFIER);
            return ParserRuleContext2.KEY_SPECIFIER_RHS;
        } else if (isParameter(parentCtx)) {
            return ParserRuleContext2.EXPRESSION;
        } else if (parentCtx == ParserRuleContext2.FUNCTIONAL_MATCH_PATTERN) {
            return ParserRuleContext2.ARG_LIST_MATCH_PATTERN_START;
        } else if (isInMatchPatternCtx(parentCtx)) {
            // This is a special case ParserRuleContext2.which occurs because of FUNC_MATCH_PATTERN_OR_CONST_PATTERN context,
            // If this is the case we are in a functional match pattern but the context is not started, hence
            // start the context.
            startContext(ParserRuleContext2.FUNCTIONAL_MATCH_PATTERN);
            return ParserRuleContext2.ARG_LIST_MATCH_PATTERN_START;
        } else if (parentCtx == ParserRuleContext2.FUNCTIONAL_BINDING_PATTERN) {
            return ParserRuleContext2.ARG_BINDING_PATTERN;
        }
        return ParserRuleContext2.EXPRESSION;
    }

    private boolean isInMatchPatternCtx(int context) {
        switch (context) {
            case ParserRuleContext2.MATCH_PATTERN:
            case ParserRuleContext2.LIST_MATCH_PATTERN:
            case ParserRuleContext2.MAPPING_MATCH_PATTERN:
            case ParserRuleContext2.FUNCTIONAL_MATCH_PATTERN:
            case ParserRuleContext2.NAMED_ARG_MATCH_PATTERN:
                return true;
            default:
                return false;
        }
    }

    private int getNextRuleForOpenBrace(int nextLookahead) {
        int parentCtx = getParentContext();
        if (parentCtx == ParserRuleContext2.LISTENERS_LIST) {
            endContext();
        }

        switch (parentCtx) {
            case ParserRuleContext2.OBJECT_TYPE_DESCRIPTOR:
                return ParserRuleContext2.OBJECT_MEMBER;
            case ParserRuleContext2.RECORD_TYPE_DESCRIPTOR:
                return ParserRuleContext2.RECORD_FIELD;
            case ParserRuleContext2.MAPPING_CONSTRUCTOR:
                return ParserRuleContext2.FIRST_MAPPING_FIELD;
            case ParserRuleContext2.FORK_STMT:
                return ParserRuleContext2.NAMED_WORKER_DECL;
            case ParserRuleContext2.MULTI_RECEIVE_WORKERS:
                return ParserRuleContext2.RECEIVE_FIELD;
            case ParserRuleContext2.MULTI_WAIT_FIELDS:
                return ParserRuleContext2.WAIT_FIELD_NAME;
            case ParserRuleContext2.MODULE_ENUM_DECLARATION:
                return ParserRuleContext2.ENUM_MEMBER_LIST;
            case ParserRuleContext2.MAPPING_BINDING_PATTERN:
                return ParserRuleContext2.MAPPING_BINDING_PATTERN_MEMBER;
            case ParserRuleContext2.MAPPING_MATCH_PATTERN:
                return ParserRuleContext2.FIELD_MATCH_PATTERNS_START;
            default:
                return ParserRuleContext2.STATEMENT;
        }
    }

    private boolean isExpressionContext(int ctx) {
        switch (ctx) {
            case ParserRuleContext2.LISTENERS_LIST:
            case ParserRuleContext2.MAPPING_CONSTRUCTOR:
            case ParserRuleContext2.COMPUTED_FIELD_NAME:
            case ParserRuleContext2.LIST_CONSTRUCTOR:
            case ParserRuleContext2.INTERPOLATION:
            case ParserRuleContext2.ARG_LIST:
            case ParserRuleContext2.LET_EXPR_LET_VAR_DECL:
            case ParserRuleContext2.LET_CLAUSE_LET_VAR_DECL:
            case ParserRuleContext2.TABLE_CONSTRUCTOR:
            case ParserRuleContext2.QUERY_EXPRESSION:
            case ParserRuleContext2.TABLE_CONSTRUCTOR_OR_QUERY_EXPRESSION:
            case ParserRuleContext2.SERVICE_CONSTRUCTOR_EXPRESSION:
            case ParserRuleContext2.ORDER_KEY:
                return true;
            default:
                return false;
        }
    }

    /**
     * Get the next parser context to visit after a {@link ParserRuleContext#AFTER_PARAMETER_TYPE}.
     *
     * @return Next parser context
     */
    private int getNextRuleForParamType() {
        int parentCtx;
        parentCtx = getParentContext();
        if (parentCtx == ParserRuleContext2.REQUIRED_PARAM || parentCtx == ParserRuleContext2.DEFAULTABLE_PARAM) {
            return ParserRuleContext2.VARIABLE_NAME;
        } else if (parentCtx == ParserRuleContext2.REST_PARAM) {
            return ParserRuleContext2.ELLIPSIS;
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * Get the next parser context to visit after a {@link ParserRuleContext#COMMA}.
     *
     * @return Next parser context
     */
    private int getNextRuleForComma() {
        int parentCtx = getParentContext();
        switch (parentCtx) {
            case ParserRuleContext2.PARAM_LIST:
            case ParserRuleContext2.REQUIRED_PARAM:
            case ParserRuleContext2.DEFAULTABLE_PARAM:
            case ParserRuleContext2.REST_PARAM:
                endContext();
                return parentCtx;
            case ParserRuleContext2.ARG_LIST:
                return ParserRuleContext2.ARG_START;
            case ParserRuleContext2.MAPPING_CONSTRUCTOR:
                return ParserRuleContext2.MAPPING_FIELD;
            case ParserRuleContext2.LISTENERS_LIST:
            case ParserRuleContext2.LIST_CONSTRUCTOR:
            case ParserRuleContext2.ORDER_KEY:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.ANNOT_ATTACH_POINTS_LIST:
                return ParserRuleContext2.ATTACH_POINT;
            case ParserRuleContext2.TABLE_CONSTRUCTOR:
                return ParserRuleContext2.MAPPING_CONSTRUCTOR;
            case ParserRuleContext2.KEY_SPECIFIER:
                return ParserRuleContext2.VARIABLE_NAME;
            case ParserRuleContext2.LET_EXPR_LET_VAR_DECL:
            case ParserRuleContext2.LET_CLAUSE_LET_VAR_DECL:
                return ParserRuleContext2.LET_VAR_DECL_START;
            case ParserRuleContext2.TYPE_DESC_IN_STREAM_TYPE_DESC:
                return ParserRuleContext2.TYPE_DESCRIPTOR;
            case ParserRuleContext2.BRACED_EXPR_OR_ANON_FUNC_PARAMS:
                return ParserRuleContext2.IMPLICIT_ANON_FUNC_PARAM;
            case ParserRuleContext2.TYPE_DESC_IN_TUPLE:
                return ParserRuleContext2.TYPE_DESCRIPTOR;
            case ParserRuleContext2.LIST_BINDING_PATTERN:
                return ParserRuleContext2.LIST_BINDING_PATTERN_MEMBER;
            case ParserRuleContext2.MAPPING_BINDING_PATTERN:
            case ParserRuleContext2.MAPPING_BP_OR_MAPPING_CONSTRUCTOR:
                return ParserRuleContext2.MAPPING_BINDING_PATTERN_MEMBER;
            case ParserRuleContext2.MULTI_RECEIVE_WORKERS:
                return ParserRuleContext2.RECEIVE_FIELD;
            case ParserRuleContext2.MULTI_WAIT_FIELDS:
                return ParserRuleContext2.WAIT_FIELD_NAME;
            case ParserRuleContext2.ENUM_MEMBER_LIST:
                return ParserRuleContext2.ENUM_MEMBER_START;
            case ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR:
                return ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR_END;
            case ParserRuleContext2.STMT_START_BRACKETED_LIST:
                return ParserRuleContext2.STMT_START_BRACKETED_LIST_MEMBER;
            case ParserRuleContext2.BRACKETED_LIST:
                return ParserRuleContext2.BRACKETED_LIST_MEMBER;
            case ParserRuleContext2.LIST_MATCH_PATTERN:
                return ParserRuleContext2.LIST_MATCH_PATTERN_MEMBER;
            case ParserRuleContext2.FUNCTIONAL_BINDING_PATTERN:
                return ParserRuleContext2.ARG_BINDING_PATTERN;
            case ParserRuleContext2.MAPPING_MATCH_PATTERN:
                return ParserRuleContext2.FIELD_MATCH_PATTERN_MEMBER;
            case ParserRuleContext2.FUNCTIONAL_MATCH_PATTERN:
                return ParserRuleContext2.ARG_MATCH_PATTERN;
            case ParserRuleContext2.NAMED_ARG_MATCH_PATTERN:
                endContext();
                return ParserRuleContext2.NAMED_ARG_MATCH_PATTERN_RHS;
            default:
                throw new IllegalStateException(String.valueOf(parentCtx));
        }
    }

    /**
     * Get the next parser context to visit after a type descriptor.
     *
     * @return Next parser context
     */
    private int getNextRuleForTypeDescriptor() {
        int parentCtx = getParentContext();
        switch (parentCtx) {
            // Contexts that expect a type
            case ParserRuleContext2.TYPE_DESC_IN_ANNOTATION_DECL:
                endContext();
                if (isInTypeDescContext()) {
                    return ParserRuleContext2.TYPEDESC_RHS;
                }
                return ParserRuleContext2.ANNOTATION_TAG;
            case ParserRuleContext2.TYPE_DESC_BEFORE_IDENTIFIER:
            case ParserRuleContext2.TYPE_DESC_IN_RECORD_FIELD:
                endContext();
                if (isInTypeDescContext()) {
                    return ParserRuleContext2.TYPEDESC_RHS;
                }
                return ParserRuleContext2.VARIABLE_NAME;
            case ParserRuleContext2.TYPE_DESC_IN_TYPE_BINDING_PATTERN:
                endContext();
                if (isInTypeDescContext()) {
                    return ParserRuleContext2.TYPEDESC_RHS;
                }
                if (getParentContext() == ParserRuleContext2.FOREACH_STMT) {
                    return ParserRuleContext2.BINDING_PATTERN;
                }
                return ParserRuleContext2.VARIABLE_NAME;
            case ParserRuleContext2.TYPE_DESC_IN_PARAM:
                endContext();
                if (isInTypeDescContext()) {
                    return ParserRuleContext2.TYPEDESC_RHS;
                }
                return ParserRuleContext2.AFTER_PARAMETER_TYPE;
            case ParserRuleContext2.TYPE_DESC_IN_TYPE_DEF:
                endContext();
                if (isInTypeDescContext()) {
                    return ParserRuleContext2.TYPEDESC_RHS;
                }
                return ParserRuleContext2.SEMICOLON;
            case ParserRuleContext2.TYPE_DESC_IN_ANGLE_BRACKETS:
                endContext();
                return ParserRuleContext2.GT;
            case ParserRuleContext2.TYPE_DESC_IN_RETURN_TYPE_DESC:
                endContext();
                if (isInTypeDescContext()) {
                    return ParserRuleContext2.TYPEDESC_RHS;
                }

                parentCtx = getParentContext();
                switch (parentCtx) {
                    case ParserRuleContext2.FUNC_TYPE_DESC:
                        endContext();
                        return ParserRuleContext2.TYPEDESC_RHS;
                    case ParserRuleContext2.FUNC_DEF_OR_FUNC_TYPE:
                        return ParserRuleContext2.FUNC_BODY_OR_TYPE_DESC_RHS;
                    case ParserRuleContext2.FUNC_TYPE_DESC_OR_ANON_FUNC:
                        return ParserRuleContext2.FUNC_TYPE_DESC_RHS_OR_ANON_FUNC_BODY;
                    case ParserRuleContext2.FUNC_DEF:
                        return ParserRuleContext2.FUNC_BODY;
                    case ParserRuleContext2.ANON_FUNC_EXPRESSION:
                        return ParserRuleContext2.ANON_FUNC_BODY;
                    case ParserRuleContext2.NAMED_WORKER_DECL:
                        return ParserRuleContext2.BLOCK_STMT;
                    default:
                        throw new IllegalStateException(String.valueOf(parentCtx));
                }
            case ParserRuleContext2.TYPE_DESC_IN_EXPRESSION:
                endContext();
                if (isInTypeDescContext()) {
                    return ParserRuleContext2.TYPEDESC_RHS;
                }
                return ParserRuleContext2.EXPRESSION_RHS;
            case ParserRuleContext2.COMP_UNIT:
                /*
                 * Fact 1:
                 * ------
                 * FUNC_DEF_OR_FUNC_TYPE is only possible for module level construct or object member
                 * that starts with 'function' keyword. However, until the end of func-signature,
                 * we don't know whether this is a func-def or a function type.
                 * Hence a var-decl-stmt context is not started until this point.
                 *
                 * Fact 2:
                 * ------
                 * We reach here for END_OF_TYPE_DESC context. That means we are going to end the
                 * func-type-desc.
                 */
                startContext(ParserRuleContext2.VAR_DECL_STMT);
                return ParserRuleContext2.VARIABLE_NAME; // TODO add typed-binding-patters
            case ParserRuleContext2.OBJECT_MEMBER:
                return ParserRuleContext2.VARIABLE_NAME;
            case ParserRuleContext2.ANNOTATION_DECL:
                return ParserRuleContext2.IDENTIFIER;
            case ParserRuleContext2.TYPE_DESC_IN_STREAM_TYPE_DESC:
                return ParserRuleContext2.STREAM_TYPE_FIRST_PARAM_RHS;
            case ParserRuleContext2.TYPE_DESC_IN_PARENTHESIS:
                endContext();
                if (isInTypeDescContext()) {
                    return ParserRuleContext2.TYPEDESC_RHS;
                }
                return ParserRuleContext2.CLOSE_PARENTHESIS;
            case ParserRuleContext2.TYPE_DESC_IN_NEW_EXPR:
                endContext();
                if (isInTypeDescContext()) {
                    return ParserRuleContext2.TYPEDESC_RHS;
                }
                return ParserRuleContext2.ARG_LIST_START;
            case ParserRuleContext2.TYPE_DESC_IN_TUPLE:
            case ParserRuleContext2.STMT_START_BRACKETED_LIST:
                return ParserRuleContext2.TYPE_DESC_IN_TUPLE_RHS;
            case ParserRuleContext2.TYPE_REFERENCE:
                endContext();
                return ParserRuleContext2.SEMICOLON;
            default:
                // If none of the above that means we reach here via, anonymous-func-or-func-type context.
                // Then the rhs of this is definitely an expression-rhs
                return ParserRuleContext2.EXPRESSION_RHS;
        }
    }

    private boolean isInTypeDescContext() {
        switch (getParentContext()) {
            case ParserRuleContext2.TYPE_DESC_IN_ANNOTATION_DECL:
            case ParserRuleContext2.TYPE_DESC_BEFORE_IDENTIFIER:
            case ParserRuleContext2.TYPE_DESC_IN_RECORD_FIELD:
            case ParserRuleContext2.TYPE_DESC_IN_PARAM:
            case ParserRuleContext2.TYPE_DESC_IN_TYPE_BINDING_PATTERN:
            case ParserRuleContext2.TYPE_DESC_IN_TYPE_DEF:
            case ParserRuleContext2.TYPE_DESC_IN_ANGLE_BRACKETS:
            case ParserRuleContext2.TYPE_DESC_IN_RETURN_TYPE_DESC:
            case ParserRuleContext2.TYPE_DESC_IN_EXPRESSION:
            case ParserRuleContext2.TYPE_DESC_IN_STREAM_TYPE_DESC:
            case ParserRuleContext2.TYPE_DESC_IN_PARENTHESIS:
            case ParserRuleContext2.TYPE_DESC_IN_NEW_EXPR:
            case ParserRuleContext2.TYPE_DESC_IN_TUPLE:
            case ParserRuleContext2.STMT_START_BRACKETED_LIST:
            case ParserRuleContext2.BRACKETED_LIST:
            case ParserRuleContext2.TYPE_REFERENCE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Get the next parser context to visit after a {@link ParserRuleContext#ASSIGN_OP}.
     *
     * @return Next parser context
     */
    private int getNextRuleForEqualOp() {
        int parentCtx = getParentContext();
        switch (parentCtx) {
            case ParserRuleContext2.EXTERNAL_FUNC_BODY:
                return ParserRuleContext2.EXTERNAL_FUNC_BODY_OPTIONAL_ANNOTS;
            case ParserRuleContext2.REQUIRED_PARAM:
            case ParserRuleContext2.DEFAULTABLE_PARAM:
            case ParserRuleContext2.RECORD_FIELD:
            case ParserRuleContext2.ARG_LIST:
            case ParserRuleContext2.OBJECT_MEMBER:
            case ParserRuleContext2.LISTENER_DECL:
            case ParserRuleContext2.CONSTANT_DECL:
            case ParserRuleContext2.LET_EXPR_LET_VAR_DECL:
            case ParserRuleContext2.LET_CLAUSE_LET_VAR_DECL:
            case ParserRuleContext2.ORDER_KEY:
            case ParserRuleContext2.ENUM_MEMBER_LIST:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.NAMED_ARG_MATCH_PATTERN:
                return ParserRuleContext2.MATCH_PATTERN;
            case ParserRuleContext2.FUNCTIONAL_BINDING_PATTERN:
                return ParserRuleContext2.BINDING_PATTERN;
            default:
                if (isStatement(parentCtx)) {
                    return ParserRuleContext2.EXPRESSION;
                }
                throw new IllegalStateException("equal op cannot exist in a " + parentCtx);
        }
    }

    /**
     * Get the next parser context to visit after a {@link ParserRuleContext#CLOSE_BRACE}.
     *
     * @param nextLookahead Position of the next token to consider, relative to the position of the original error
     * @return Next parser context
     */
    private int getNextRuleForCloseBrace(int nextLookahead) {
        int parentCtx = getParentContext();
        switch (parentCtx) {
            case ParserRuleContext2.FUNC_BODY_BLOCK:
                endContext(); // end body block
                return getNextRuleForCloseBraceInFuncBody();
            case ParserRuleContext2.SERVICE_DECL:
                endContext();
                return ParserRuleContext2.TOP_LEVEL_NODE;
            case ParserRuleContext2.OBJECT_MEMBER:
                endContext(); // end object member
                // fall through
            case ParserRuleContext2.RECORD_TYPE_DESCRIPTOR:
            case ParserRuleContext2.OBJECT_TYPE_DESCRIPTOR:
                endContext(); // end record/object type def
                return ParserRuleContext2.TYPEDESC_RHS;
            case ParserRuleContext2.BLOCK_STMT:
            case ParserRuleContext2.AMBIGUOUS_STMT:
                endContext(); // end block stmt
                parentCtx = getParentContext();
                switch (parentCtx) {
                    case ParserRuleContext2.LOCK_STMT:
                    case ParserRuleContext2.FOREACH_STMT:
                    case ParserRuleContext2.WHILE_BLOCK:
                    case ParserRuleContext2.RETRY_STMT:
                        endContext();
                        return ParserRuleContext2.STATEMENT;
                    case ParserRuleContext2.IF_BLOCK:
                        endContext(); // end parent stmt if/lock/while/ block
                        return ParserRuleContext2.ELSE_BLOCK;
                    case ParserRuleContext2.TRANSACTION_STMT:
                        endContext(); // end transaction context
                        parentCtx = getParentContext();

                        // If this is a retry-transaction block, then end the enclosing retry
                        // context as well.
                        if (parentCtx == ParserRuleContext2.RETRY_STMT) {
                            endContext();
                        }
                        return ParserRuleContext2.STATEMENT;
                    case ParserRuleContext2.NAMED_WORKER_DECL:
                        endContext(); // end named-worker
                        parentCtx = getParentContext();
                        if (parentCtx == ParserRuleContext2.FORK_STMT) {
                            return ParserRuleContext2.STATEMENT;
                        } else {
                            return ParserRuleContext2.STATEMENT;
                        }
                    case ParserRuleContext2.MATCH_BODY:
                        return ParserRuleContext2.MATCH_PATTERN;
                    default:
                        return ParserRuleContext2.STATEMENT;
                }
            case ParserRuleContext2.MAPPING_CONSTRUCTOR:
                endContext(); // end mapping constructor
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext2.TABLE_CONSTRUCTOR) {
                    return ParserRuleContext2.TABLE_ROW_END;
                }

                if (parentCtx == ParserRuleContext2.ANNOTATIONS) {
                    return ParserRuleContext2.ANNOTATION_END;
                }

                return getNextRuleForExpr();
            case ParserRuleContext2.STMT_START_BRACKETED_LIST:
                return ParserRuleContext2.BRACKETED_LIST_MEMBER_END;
            case ParserRuleContext2.MAPPING_BINDING_PATTERN:
            case ParserRuleContext2.MAPPING_BP_OR_MAPPING_CONSTRUCTOR:
                endContext();
                return getNextRuleForBindingPattern();
            case ParserRuleContext2.FORK_STMT:
                endContext(); // end fork-statement
                return ParserRuleContext2.STATEMENT;
            case ParserRuleContext2.INTERPOLATION:
                endContext();
                return ParserRuleContext2.TEMPLATE_MEMBER;
            case ParserRuleContext2.MULTI_RECEIVE_WORKERS:
            case ParserRuleContext2.MULTI_WAIT_FIELDS:
            case ParserRuleContext2.SERVICE_CONSTRUCTOR_EXPRESSION:
            case ParserRuleContext2.DO_CLAUSE:
                endContext();
                return ParserRuleContext2.EXPRESSION_RHS;
            case ParserRuleContext2.ENUM_MEMBER_LIST:
                endContext(); // end ENUM_MEMBER_LIST context
                endContext(); // end MODULE_ENUM_DECLARATION ctx
                return ParserRuleContext2.TOP_LEVEL_NODE;
            case ParserRuleContext2.MATCH_BODY:
                endContext(); // end match body
                endContext(); // end match stmt
                return ParserRuleContext2.STATEMENT;
            case ParserRuleContext2.MAPPING_MATCH_PATTERN:
                endContext();
                return getNextRuleForMatchPattern();
            default:
                throw new IllegalStateException("found close-brace in: " + parentCtx);
        }
    }

    private int getNextRuleForCloseBraceInFuncBody() {
        int parentCtx;
        parentCtx = getParentContext();
        switch (parentCtx) {
            case ParserRuleContext2.SERVICE_DECL:
                return ParserRuleContext2.RESOURCE_DEF;
            case ParserRuleContext2.OBJECT_MEMBER:
                return ParserRuleContext2.OBJECT_MEMBER_START;
            case ParserRuleContext2.COMP_UNIT:
                return ParserRuleContext2.TOP_LEVEL_NODE;
            case ParserRuleContext2.FUNC_DEF:
            case ParserRuleContext2.FUNC_DEF_OR_FUNC_TYPE:
                endContext(); // end func-def
                return getNextRuleForCloseBraceInFuncBody();
            case ParserRuleContext2.ANON_FUNC_EXPRESSION:
            default:
                // Anonynous func
                endContext(); // end anon-func
                return ParserRuleContext2.EXPRESSION_RHS;
        }
    }

    private int getNextRuleForAnnotationEnd(int nextLookahead) {
        int parentCtx;
        STToken nextToken;
        nextToken = this.tokenReader.peek(nextLookahead);
        if (nextToken.kind == SyntaxKind2.AT_TOKEN) {
            return ParserRuleContext2.AT;
        }

        endContext(); // end annotations
        parentCtx = getParentContext();
        switch (parentCtx) {
            case ParserRuleContext2.COMP_UNIT:
                return ParserRuleContext2.TOP_LEVEL_NODE_WITHOUT_METADATA;
            case ParserRuleContext2.FUNC_DEF:
            case ParserRuleContext2.FUNC_TYPE_DESC:
            case ParserRuleContext2.FUNC_DEF_OR_FUNC_TYPE:
            case ParserRuleContext2.ANON_FUNC_EXPRESSION:
            case ParserRuleContext2.FUNC_TYPE_DESC_OR_ANON_FUNC:
                return ParserRuleContext2.TYPE_DESC_IN_RETURN_TYPE_DESC;
            case ParserRuleContext2.LET_EXPR_LET_VAR_DECL:
            case ParserRuleContext2.LET_CLAUSE_LET_VAR_DECL:
                return ParserRuleContext2.TYPE_DESC_IN_TYPE_BINDING_PATTERN;
            case ParserRuleContext2.RECORD_FIELD:
                return ParserRuleContext2.RECORD_FIELD_WITHOUT_METADATA;
            case ParserRuleContext2.OBJECT_MEMBER:
                return ParserRuleContext2.OBJECT_MEMBER_WITHOUT_METADATA;
            case ParserRuleContext2.SERVICE_DECL:
                return ParserRuleContext2.RESOURCE_DEF;
            case ParserRuleContext2.FUNC_BODY_BLOCK:
                return ParserRuleContext2.STATEMENT_WITHOUT_ANNOTS;
            case ParserRuleContext2.EXTERNAL_FUNC_BODY:
                return ParserRuleContext2.EXTERNAL_KEYWORD;
            case ParserRuleContext2.TYPE_CAST:
                return ParserRuleContext2.TYPE_CAST_PARAM_RHS;
            case ParserRuleContext2.ENUM_MEMBER_LIST:
                return ParserRuleContext2.ENUM_MEMBER_NAME;
            default:
                if (isParameter(parentCtx)) {
                    return ParserRuleContext2.REQUIRED_PARAM;
                }

                // everything else, treat as an annotation in an expression
                return ParserRuleContext2.EXPRESSION;
        }
    }

    /**
     * Get the next parser context to visit after a variable/parameter name.
     *
     * @return Next parser context
     */
    private int getNextRuleForVarName() {
        int parentCtx = getParentContext();
        if (isStatement(parentCtx)) {
            return ParserRuleContext2.VAR_DECL_STMT_RHS;
        }

        switch (parentCtx) {
            case ParserRuleContext2.REQUIRED_PARAM:
            case ParserRuleContext2.PARAM_LIST:
                return ParserRuleContext2.REQUIRED_PARAM_NAME_RHS;
            case ParserRuleContext2.DEFAULTABLE_PARAM:
                return ParserRuleContext2.ASSIGN_OP;
            case ParserRuleContext2.REST_PARAM:
                return ParserRuleContext2.PARAM_END;
            case ParserRuleContext2.FOREACH_STMT:
                return ParserRuleContext2.IN_KEYWORD;
            case ParserRuleContext2.TYPED_BINDING_PATTERN:
            case ParserRuleContext2.BINDING_PATTERN_STARTING_IDENTIFIER:
            case ParserRuleContext2.LIST_BINDING_PATTERN:
            case ParserRuleContext2.STMT_START_BRACKETED_LIST_MEMBER:
            case ParserRuleContext2.REST_BINDING_PATTERN:
            case ParserRuleContext2.FIELD_BINDING_PATTERN:
            case ParserRuleContext2.MAPPING_BINDING_PATTERN:
            case ParserRuleContext2.MAPPING_BP_OR_MAPPING_CONSTRUCTOR:
            case ParserRuleContext2.FUNCTIONAL_BINDING_PATTERN:
                return getNextRuleForBindingPattern();
            case ParserRuleContext2.LISTENER_DECL:
            case ParserRuleContext2.CONSTANT_DECL:
                return ParserRuleContext2.VAR_DECL_STMT_RHS;
            case ParserRuleContext2.RECORD_FIELD:
                return ParserRuleContext2.FIELD_DESCRIPTOR_RHS;
            case ParserRuleContext2.ARG_LIST:
                return ParserRuleContext2.NAMED_OR_POSITIONAL_ARG_RHS;
            case ParserRuleContext2.OBJECT_MEMBER:
                return ParserRuleContext2.OBJECT_FIELD_RHS;
            case ParserRuleContext2.ARRAY_TYPE_DESCRIPTOR:
                return ParserRuleContext2.CLOSE_BRACKET;
            case ParserRuleContext2.KEY_SPECIFIER:
                return ParserRuleContext2.TABLE_KEY_RHS;
            case ParserRuleContext2.LET_EXPR_LET_VAR_DECL:
            case ParserRuleContext2.LET_CLAUSE_LET_VAR_DECL:
                return ParserRuleContext2.ASSIGN_OP;
            case ParserRuleContext2.ANNOTATION_DECL:
                return ParserRuleContext2.ANNOT_OPTIONAL_ATTACH_POINTS;
            case ParserRuleContext2.QUERY_EXPRESSION:
                return ParserRuleContext2.IN_KEYWORD;
            case ParserRuleContext2.REST_MATCH_PATTERN:
                endContext(); // end rest match pattern context
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext2.MAPPING_MATCH_PATTERN) {
                    return ParserRuleContext2.CLOSE_BRACE;
                }
                if (parentCtx == ParserRuleContext2.FUNCTIONAL_MATCH_PATTERN) {
                    return ParserRuleContext2.CLOSE_PARENTHESIS;
                }
                return ParserRuleContext2.CLOSE_BRACKET;
            case ParserRuleContext2.MAPPING_MATCH_PATTERN:
                return ParserRuleContext2.COLON;
            default:
                throw new IllegalStateException(String.valueOf(parentCtx));
        }
    }

    /**
     * Get the next parser context to visit after a {@link ParserRuleContext#SEMICOLON}.
     *
     * @param nextLookahead Position of the next token to consider, relative to the position of the original error
     * @return Next parser context
     */
    private int getNextRuleForSemicolon(int nextLookahead) {
        STToken nextToken;
        int parentCtx = getParentContext();
        if (parentCtx == ParserRuleContext2.EXTERNAL_FUNC_BODY) {
            endContext(); // end external func-body
            endContext(); // end func-def
            return ParserRuleContext2.TOP_LEVEL_NODE;
        } else if (parentCtx == ParserRuleContext2.QUERY_EXPRESSION) {
            endContext(); // end expression
            return getNextRuleForSemicolon(nextLookahead);
        } else if (isExpressionContext(parentCtx)) {
            // A semicolon after an expression also means its an end of a statement/field, Hence pop the ctx.
            endContext(); // end statement
            return ParserRuleContext2.STATEMENT;
        } else if (parentCtx == ParserRuleContext2.VAR_DECL_STMT) {
            endContext(); // end var-decl
            parentCtx = getParentContext();
            if (parentCtx == ParserRuleContext2.COMP_UNIT) {
                return ParserRuleContext2.TOP_LEVEL_NODE;
            }
            return ParserRuleContext2.STATEMENT;
        } else if (isStatement(parentCtx)) {
            endContext(); // end statement
            return ParserRuleContext2.STATEMENT;
        } else if (parentCtx == ParserRuleContext2.RECORD_FIELD) {
            endContext(); // end record field
            return ParserRuleContext2.RECORD_FIELD_OR_RECORD_END;
        } else if (parentCtx == ParserRuleContext2.XML_NAMESPACE_DECLARATION) {
            endContext();
            parentCtx = getParentContext();
            if (parentCtx == ParserRuleContext2.COMP_UNIT) {
                return ParserRuleContext2.TOP_LEVEL_NODE;
            }
            return ParserRuleContext2.STATEMENT;
        } else if (parentCtx == ParserRuleContext2.MODULE_TYPE_DEFINITION ||
                parentCtx == ParserRuleContext2.LISTENER_DECL || parentCtx == ParserRuleContext2.CONSTANT_DECL ||
                parentCtx == ParserRuleContext2.ANNOTATION_DECL) {
            endContext(); // end declaration
            return ParserRuleContext2.TOP_LEVEL_NODE;
        } else if (parentCtx == ParserRuleContext2.OBJECT_MEMBER) {
            if (isEndOfObjectTypeNode(nextLookahead)) {
                endContext(); // end object member
                return ParserRuleContext2.CLOSE_BRACE;
            }
            return ParserRuleContext2.OBJECT_MEMBER_START;
        } else if (parentCtx == ParserRuleContext2.IMPORT_DECL) {
            endContext(); // end object member
            nextToken = this.tokenReader.peek(nextLookahead);
            if (nextToken.kind == SyntaxKind2.EOF_TOKEN) {
                return ParserRuleContext2.EOF;
            }
            return ParserRuleContext2.TOP_LEVEL_NODE;
        } else if (parentCtx == ParserRuleContext2.ANNOT_ATTACH_POINTS_LIST) {
            endContext(); // end annot attach points list
            endContext(); // end annot declaration
            nextToken = this.tokenReader.peek(nextLookahead);
            if (nextToken.kind == SyntaxKind2.EOF_TOKEN) {
                return ParserRuleContext2.EOF;
            }
            return ParserRuleContext2.TOP_LEVEL_NODE;
        } else if (parentCtx == ParserRuleContext2.FUNC_DEF || parentCtx == ParserRuleContext2.FUNC_DEF_OR_FUNC_TYPE) {
            endContext(); // end func-def
            nextToken = this.tokenReader.peek(nextLookahead);
            if (nextToken.kind == SyntaxKind2.EOF_TOKEN) {
                return ParserRuleContext2.EOF;
            }
            return ParserRuleContext2.TOP_LEVEL_NODE;
        } else {
            throw new IllegalStateException(String.valueOf(parentCtx));
        }
    }

    private int getNextRuleForDot() {
        int parentCtx = getParentContext();
        if (parentCtx == ParserRuleContext2.IMPORT_DECL) {
            return ParserRuleContext2.IMPORT_MODULE_NAME;
        }
        return ParserRuleContext2.FIELD_ACCESS_IDENTIFIER;
    }

    /**
     * Get the next parser context to visit after a {@link ParserRuleContext#QUESTION_MARK}.
     *
     * @return Next parser context
     */
    private int getNextRuleForQuestionMark() {
        int parentCtx = getParentContext();
        switch (parentCtx) {
            case ParserRuleContext2.OPTIONAL_TYPE_DESCRIPTOR:
                endContext();
                return ParserRuleContext2.TYPEDESC_RHS;
            case ParserRuleContext2.CONDITIONAL_EXPRESSION:
                return ParserRuleContext2.EXPRESSION;
            default:
                return ParserRuleContext2.SEMICOLON;
        }
    }

    /**
     * Get the next parser context to visit after a {@link ParserRuleContext#OPEN_BRACKET}.
     *
     * @return Next parser context
     */
    private int getNextRuleForOpenBracket() {
        int parentCtx = getParentContext();
        switch (parentCtx) {
            case ParserRuleContext2.ARRAY_TYPE_DESCRIPTOR:
                return ParserRuleContext2.ARRAY_LENGTH;
            case ParserRuleContext2.LIST_CONSTRUCTOR:
                return ParserRuleContext2.LIST_CONSTRUCTOR_FIRST_MEMBER;
            case ParserRuleContext2.TABLE_CONSTRUCTOR:
                return ParserRuleContext2.ROW_LIST_RHS;
            case ParserRuleContext2.LIST_BINDING_PATTERN:
                return ParserRuleContext2.LIST_BINDING_PATTERN_MEMBER;
            case ParserRuleContext2.LIST_MATCH_PATTERN:
                return ParserRuleContext2.LIST_MATCH_PATTERNS_START;
            default:
                if (isInTypeDescContext()) {
                    return ParserRuleContext2.TYPE_DESC_IN_TUPLE;
                }
                return ParserRuleContext2.EXPRESSION;
        }
    }

    /**
     * Get the next parser context to visit after a {@link ParserRuleContext#CLOSE_BRACKET}.
     *
     * @return Next parser context
     */
    private int getNextRuleForCloseBracket() {
        int parentCtx = getParentContext();
        switch (parentCtx) {
            case ParserRuleContext2.ARRAY_TYPE_DESCRIPTOR:
            case ParserRuleContext2.TYPE_DESC_IN_TUPLE:
                endContext(); // End array/tuple type descriptor context
                return ParserRuleContext2.TYPEDESC_RHS;
            case ParserRuleContext2.COMPUTED_FIELD_NAME:
                endContext(); // end computed-field-name
                return ParserRuleContext2.COLON;
            case ParserRuleContext2.LIST_BINDING_PATTERN:
                endContext(); // end list-binding-pattern context
                return getNextRuleForBindingPattern();
            case ParserRuleContext2.LIST_CONSTRUCTOR:
            case ParserRuleContext2.TABLE_CONSTRUCTOR:
            case ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR:
                endContext();
                return getNextRuleForExpr();
            case ParserRuleContext2.STMT_START_BRACKETED_LIST:
                endContext();
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext2.STMT_START_BRACKETED_LIST) {
                    return ParserRuleContext2.BRACKETED_LIST_MEMBER_END;
                }

                return ParserRuleContext2.STMT_START_BRACKETED_LIST_RHS;
            case ParserRuleContext2.BRACKETED_LIST:
                endContext();
                return ParserRuleContext2.BRACKETED_LIST_RHS;
            case ParserRuleContext2.LIST_MATCH_PATTERN:
                endContext();
                return getNextRuleForMatchPattern();
            default:
                return getNextRuleForExpr();
        }
    }

    /**
     * Get the next parser context to visit after a {@link ParserRuleContext#DECIMAL_INTEGER_LITERAL}.
     *
     * @return Next parser context
     */
    private int getNextRuleForDecimalIntegerLiteral() {
        int parentCtx = getParentContext();
        switch (parentCtx) {
            case ParserRuleContext2.CONSTANT_EXPRESSION:
                endContext();
                return getNextRuleForConstExpr();
            case ParserRuleContext2.ARRAY_TYPE_DESCRIPTOR:
            default:
                return ParserRuleContext2.CLOSE_BRACKET;
        }
    }

    private int getNextRuleForExpr() {
        int parentCtx;
        parentCtx = getParentContext();
        if (parentCtx == ParserRuleContext2.CONSTANT_EXPRESSION) {
            endContext();
            return getNextRuleForConstExpr();
        }
        return ParserRuleContext2.EXPRESSION_RHS;
    }

    private int getNextRuleForExprStartsWithVarRef() {
        int parentCtx;
        parentCtx = getParentContext();
        if (parentCtx == ParserRuleContext2.CONSTANT_EXPRESSION) {
            endContext();
            return getNextRuleForConstExpr();
        }
        return ParserRuleContext2.VARIABLE_REF_RHS;
    }

    private int getNextRuleForConstExpr() {
        int parentCtx = getParentContext();
        switch (parentCtx) {
            case ParserRuleContext2.XML_NAMESPACE_DECLARATION:
                return ParserRuleContext2.XML_NAMESPACE_PREFIX_DECL;
            default:
                if (isInTypeDescContext()) {
                    return ParserRuleContext2.TYPEDESC_RHS;
                }
                return getNextRuleForMatchPattern();
        }
    }

    private int getNextRuleForLt() {
        int parentCtx = getParentContext();
        switch (parentCtx) {
            case ParserRuleContext2.TYPE_CAST:
                return ParserRuleContext2.TYPE_CAST_PARAM;
            default:
                return ParserRuleContext2.TYPE_DESC_IN_ANGLE_BRACKETS;
        }
    }

    private int getNextRuleForGt(int nextLookahead) {
        int parentCtx = getParentContext();
        if (parentCtx == ParserRuleContext2.TYPE_DESC_IN_STREAM_TYPE_DESC) {
            // Since type-desc in a stream-type can have alternate endings,
            // we haven't end the context. So if its '>', then end the ctx here.
            endContext();
            return ParserRuleContext2.TYPEDESC_RHS;
        }

        if (isInTypeDescContext()) {
            return ParserRuleContext2.TYPEDESC_RHS;
        }

        if (parentCtx == ParserRuleContext2.ROW_TYPE_PARAM) {
            endContext(); // end row type param ctx
            return ParserRuleContext2.TABLE_TYPE_DESC_RHS;
        } else if (parentCtx == ParserRuleContext2.RETRY_STMT) {
            return ParserRuleContext2.RETRY_TYPE_PARAM_RHS;
        }

        if (parentCtx == ParserRuleContext2.XML_NAME_PATTERN) {
            endContext();
            return ParserRuleContext2.EXPRESSION_RHS;
        }

        // Type cast expression:
        endContext();
        return ParserRuleContext2.EXPRESSION;
    }

    /**
     * Get the next parser context to visit after a binding-pattern.
     *
     * @return Next parser context
     */
    private int getNextRuleForBindingPattern() {
        int parentCtx = getParentContext();
        switch (parentCtx) {
            case ParserRuleContext2.BINDING_PATTERN_STARTING_IDENTIFIER:
            case ParserRuleContext2.TYPED_BINDING_PATTERN:
                endContext();
                return getNextRuleForBindingPattern();
            case ParserRuleContext2.FOREACH_STMT:
            case ParserRuleContext2.QUERY_EXPRESSION:
                return ParserRuleContext2.IN_KEYWORD;
            case ParserRuleContext2.LIST_BINDING_PATTERN:
            case ParserRuleContext2.STMT_START_BRACKETED_LIST:
            case ParserRuleContext2.BRACKETED_LIST:
                return ParserRuleContext2.LIST_BINDING_PATTERN_MEMBER_END;
            case ParserRuleContext2.MAPPING_BINDING_PATTERN:
            case ParserRuleContext2.MAPPING_BP_OR_MAPPING_CONSTRUCTOR:
                return ParserRuleContext2.MAPPING_BINDING_PATTERN_END;
            case ParserRuleContext2.REST_BINDING_PATTERN:
                endContext();
                parentCtx = getParentContext();
                if (parentCtx == ParserRuleContext2.LIST_BINDING_PATTERN) {
                    return ParserRuleContext2.CLOSE_BRACKET;
                } else if (parentCtx == ParserRuleContext2.FUNCTIONAL_BINDING_PATTERN) {
                    return ParserRuleContext2.CLOSE_PARENTHESIS;
                }
                return ParserRuleContext2.CLOSE_BRACE; // for mapping binding pattern
            case ParserRuleContext2.AMBIGUOUS_STMT:
                switchContext(ParserRuleContext2.VAR_DECL_STMT);
                return ParserRuleContext2.VAR_DECL_STMT_RHS;
            case ParserRuleContext2.ASSIGNMENT_OR_VAR_DECL_STMT:
            case ParserRuleContext2.VAR_DECL_STMT:
                return ParserRuleContext2.VAR_DECL_STMT_RHS;
            case ParserRuleContext2.LET_CLAUSE_LET_VAR_DECL:
            case ParserRuleContext2.LET_EXPR_LET_VAR_DECL:
            case ParserRuleContext2.ASSIGNMENT_STMT:
                return ParserRuleContext2.ASSIGN_OP;
            case ParserRuleContext2.MATCH_PATTERN:
                return ParserRuleContext2.MATCH_PATTERN_RHS;
            case ParserRuleContext2.LIST_MATCH_PATTERN:
                return ParserRuleContext2.LIST_MATCH_PATTERN_MEMBER_RHS;
            case ParserRuleContext2.FUNCTIONAL_BINDING_PATTERN:
                return ParserRuleContext2.ARG_BINDING_PATTERN_END;
            default:
                return getNextRuleForMatchPattern();
        }
    }

    private int getNextRuleForWaitExprListEnd() {
        // TODO: add other endings based on the locations where action is allowed.
        endContext();
        return ParserRuleContext2.EXPRESSION_RHS;
    }

    private int getNextRuleForIdentifier() {
        int parentCtx;
        parentCtx = getParentContext();
        switch (parentCtx) {
            case ParserRuleContext2.VARIABLE_REF:
                endContext();
                return getNextRuleForExprStartsWithVarRef();
            case ParserRuleContext2.TYPE_REFERENCE:
                endContext();
                if (isInTypeDescContext()) {
                    return ParserRuleContext2.TYPEDESC_RHS;
                }
                if (getParentContext() == ParserRuleContext2.FUNCTIONAL_MATCH_PATTERN) {
                    return ParserRuleContext2.OPEN_PARENTHESIS;
                }
                return ParserRuleContext2.SEMICOLON;
            case ParserRuleContext2.ANNOT_REFERENCE:
                endContext();
                return ParserRuleContext2.ANNOTATION_REF_RHS;
            case ParserRuleContext2.ANNOTATION_DECL:
                return ParserRuleContext2.ANNOT_OPTIONAL_ATTACH_POINTS;
            case ParserRuleContext2.FIELD_ACCESS_IDENTIFIER:
                endContext();
                return ParserRuleContext2.VARIABLE_REF_RHS;
            case ParserRuleContext2.XML_ATOMIC_NAME_PATTERN:
                endContext();
                return ParserRuleContext2.XML_NAME_PATTERN_RHS;
            case ParserRuleContext2.NAMED_ARG_MATCH_PATTERN:
                return ParserRuleContext2.ASSIGN_OP;
            default:
                throw new IllegalStateException(String.valueOf(parentCtx));
        }
    }

    private int getNextRuleForColon() {
        int parentCtx;
        parentCtx = getParentContext();
        switch (parentCtx) {
            case ParserRuleContext2.MAPPING_CONSTRUCTOR:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.MULTI_RECEIVE_WORKERS:
                return ParserRuleContext2.PEER_WORKER_NAME;
            case ParserRuleContext2.MULTI_WAIT_FIELDS:
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.CONDITIONAL_EXPRESSION:
                endContext(); // end conditional-expr
                return ParserRuleContext2.EXPRESSION;
            case ParserRuleContext2.MAPPING_BINDING_PATTERN:
            case ParserRuleContext2.MAPPING_BP_OR_MAPPING_CONSTRUCTOR:
                return ParserRuleContext2.VARIABLE_NAME;
            case ParserRuleContext2.FIELD_BINDING_PATTERN:
                endContext();
                return ParserRuleContext2.VARIABLE_NAME;
            case ParserRuleContext2.XML_ATOMIC_NAME_PATTERN:
                return ParserRuleContext2.XML_ATOMIC_NAME_IDENTIFIER_RHS;
            case ParserRuleContext2.MAPPING_MATCH_PATTERN:
                return ParserRuleContext2.MATCH_PATTERN;
            default:
                return ParserRuleContext2.IDENTIFIER;
        }
    }

    private int getNextRuleForMatchPattern() {
        int parentCtx = getParentContext();
        switch (parentCtx) {
            case ParserRuleContext2.LIST_MATCH_PATTERN:
                return ParserRuleContext2.LIST_MATCH_PATTERN_MEMBER_RHS;
            case ParserRuleContext2.MAPPING_MATCH_PATTERN:
                return ParserRuleContext2.FIELD_MATCH_PATTERN_MEMBER_RHS;
            case ParserRuleContext2.MATCH_PATTERN:
                return ParserRuleContext2.MATCH_PATTERN_RHS;
            case ParserRuleContext2.FUNCTIONAL_MATCH_PATTERN:
            case ParserRuleContext2.NAMED_ARG_MATCH_PATTERN:
                return ParserRuleContext2.ARG_MATCH_PATTERN_RHS;
            default:
                return ParserRuleContext2.OPTIONAL_MATCH_GUARD;
        }
    }

    /**
     * Check whether the given context is a statement.
     *
     * @param ctx Parser context to check
     * @return <code>true</code> if the given context is a statement. <code>false</code> otherwise
     */
    private boolean isStatement(int parentCtx) {
        switch (parentCtx) {
            case ParserRuleContext2.STATEMENT:
            case ParserRuleContext2.STATEMENT_WITHOUT_ANNOTS:
            case ParserRuleContext2.VAR_DECL_STMT:
            case ParserRuleContext2.ASSIGNMENT_STMT:
            case ParserRuleContext2.ASSIGNMENT_OR_VAR_DECL_STMT:
            case ParserRuleContext2.IF_BLOCK:
            case ParserRuleContext2.BLOCK_STMT:
            case ParserRuleContext2.WHILE_BLOCK:
            case ParserRuleContext2.CALL_STMT:
            case ParserRuleContext2.PANIC_STMT:
            case ParserRuleContext2.CONTINUE_STATEMENT:
            case ParserRuleContext2.BREAK_STATEMENT:
            case ParserRuleContext2.RETURN_STMT:
            case ParserRuleContext2.COMPOUND_ASSIGNMENT_STMT:
            case ParserRuleContext2.LOCAL_TYPE_DEFINITION_STMT:
            case ParserRuleContext2.EXPRESSION_STATEMENT:
            case ParserRuleContext2.LOCK_STMT:
            case ParserRuleContext2.FORK_STMT:
            case ParserRuleContext2.FOREACH_STMT:
            case ParserRuleContext2.TRANSACTION_STMT:
            case ParserRuleContext2.RETRY_STMT:
            case ParserRuleContext2.ROLLBACK_STMT:
            case ParserRuleContext2.AMBIGUOUS_STMT:
            case ParserRuleContext2.MATCH_STMT:
                return true;
            default:
                return false;
        }
    }

    /**
     * Check whether the given token refers to a binary operator.
     *
     * @param token Token to check
     * @return <code>true</code> if the given token refers to a binary operator. <code>false</code> otherwise
     */
    private boolean isBinaryOperator(STToken token) {
//        switch (token.kind) {
//            case PLUS_TOKEN:
//            case MINUS_TOKEN:
//            case SLASH_TOKEN:
//            case ASTERISK_TOKEN:
//            case GT_TOKEN:
//            case LT_TOKEN:
//            case DOUBLE_EQUAL_TOKEN:
//            case TRIPPLE_EQUAL_TOKEN:
//            case LT_EQUAL_TOKEN:
//            case GT_EQUAL_TOKEN:
//            case NOT_EQUAL_TOKEN:
//            case NOT_DOUBLE_EQUAL_TOKEN:
//            case BITWISE_AND_TOKEN:
//            case BITWISE_XOR_TOKEN:
//            case PIPE_TOKEN:
//            case LOGICAL_AND_TOKEN:
//            case LOGICAL_OR_TOKEN:
//            case DOUBLE_LT_TOKEN:
//            case DOUBLE_GT_TOKEN:
//            case TRIPPLE_GT_TOKEN:
//            case ELLIPSIS_TOKEN:
//            case DOUBLE_DOT_LT_TOKEN:
//            case ELVIS_TOKEN:
//                return true;
//
//            // Treat these also as binary operators.
//            case RIGHT_ARROW_TOKEN:
//            case RIGHT_DOUBLE_ARROW_TOKEN:
//                return true;
//            default:
//                return false;
//        }
        return false;
    }

    private boolean isParameter(int ctx) {
        switch (ctx) {
            case ParserRuleContext2.REQUIRED_PARAM:
            case ParserRuleContext2.DEFAULTABLE_PARAM:
            case ParserRuleContext2.REST_PARAM:
            case ParserRuleContext2.PARAM_LIST:
                return true;
            default:
                return false;
        }
    }

    /**
     * Get the expected token kind at the given parser rule context. If the parser rule is a terminal,
     * then the corresponding terminal token kind is returned. If the parser rule is a production,
     * then {@link SyntaxKind#NONE} is returned.
     *
     * @param ctx Parser rule context
     * @return Token kind expected at the given parser rule
     */
    @Override
    protected int getExpectedTokenKind(int ctx) {
        switch (ctx) {
            case ParserRuleContext2.EXTERNAL_FUNC_BODY:
                return SyntaxKind2.EQUAL_TOKEN;
            case ParserRuleContext2.FUNC_BODY_OR_TYPE_DESC_RHS:
            case ParserRuleContext2.FUNC_BODY_BLOCK:
            case ParserRuleContext2.FUNC_BODY:
                return SyntaxKind2.OPEN_BRACE_TOKEN;
            case ParserRuleContext2.FUNC_DEF:
            case ParserRuleContext2.FUNC_DEF_OR_FUNC_TYPE:
            case ParserRuleContext2.FUNC_TYPE_DESC:
            case ParserRuleContext2.FUNC_TYPE_DESC_OR_ANON_FUNC:
                return SyntaxKind2.FUNCTION_KEYWORD;
            case ParserRuleContext2.VAR_DECL_STMT_RHS:
                return SyntaxKind2.SEMICOLON_TOKEN;
            case ParserRuleContext2.SIMPLE_TYPE_DESCRIPTOR:
            case ParserRuleContext2.REQUIRED_PARAM:
            case ParserRuleContext2.VAR_DECL_STMT:
            case ParserRuleContext2.ASSIGNMENT_OR_VAR_DECL_STMT:
            case ParserRuleContext2.DEFAULTABLE_PARAM:
            case ParserRuleContext2.REST_PARAM:
                return SyntaxKind2.TYPE_DESC;
            case ParserRuleContext2.TYPE_NAME:
            case ParserRuleContext2.TYPE_REFERENCE:
            case ParserRuleContext2.FIELD_ACCESS_IDENTIFIER:
            case ParserRuleContext2.FUNC_NAME:
            case ParserRuleContext2.FUNCTION_KEYWORD_RHS:
            case ParserRuleContext2.VARIABLE_NAME:
            case ParserRuleContext2.TYPE_NAME_OR_VAR_NAME:
            case ParserRuleContext2.IMPORT_MODULE_NAME:
            case ParserRuleContext2.IMPORT_ORG_OR_MODULE_NAME:
            case ParserRuleContext2.IMPORT_PREFIX:
            case ParserRuleContext2.VARIABLE_REF:
            case ParserRuleContext2.BASIC_LITERAL: // return var-ref for any kind of terminal expression
            case ParserRuleContext2.SERVICE_NAME:
            case ParserRuleContext2.IDENTIFIER:
            case ParserRuleContext2.QUALIFIED_IDENTIFIER:
            case ParserRuleContext2.NAMESPACE_PREFIX:
            case ParserRuleContext2.IMPLICIT_ANON_FUNC_PARAM:
            case ParserRuleContext2.WORKER_NAME_OR_METHOD_NAME:
            case ParserRuleContext2.PEER_WORKER_NAME:
            case ParserRuleContext2.RECEIVE_FIELD_NAME:
            case ParserRuleContext2.WAIT_FIELD_NAME:
            case ParserRuleContext2.FIELD_BINDING_PATTERN_NAME:
            case ParserRuleContext2.XML_ATOMIC_NAME_IDENTIFIER:
            case ParserRuleContext2.MAPPING_FIELD_NAME:
            case ParserRuleContext2.MAPPING_FIELD:
            case ParserRuleContext2.ANNOT_DECL_OPTIONAL_TYPE:
            case ParserRuleContext2.WORKER_NAME:
            case ParserRuleContext2.NAMED_WORKERS:
            case ParserRuleContext2.ANNOTATION_TAG:
            case ParserRuleContext2.CONST_DECL_TYPE:
            case ParserRuleContext2.AFTER_PARAMETER_TYPE:
            case ParserRuleContext2.MODULE_ENUM_NAME:
            case ParserRuleContext2.ENUM_MEMBER_NAME:
            case ParserRuleContext2.TYPED_BINDING_PATTERN_TYPE_RHS:
            case ParserRuleContext2.ASSIGNMENT_STMT:
            case ParserRuleContext2.EXPRESSION:
            case ParserRuleContext2.TERMINAL_EXPRESSION:
            case ParserRuleContext2.XML_NAME:
            case ParserRuleContext2.ACCESS_EXPRESSION:
            case ParserRuleContext2.BINDING_PATTERN_STARTING_IDENTIFIER:
            case ParserRuleContext2.COMPUTED_FIELD_NAME:
            case ParserRuleContext2.FUNCTIONAL_BINDING_PATTERN:
                return SyntaxKind2.IDENTIFIER_TOKEN;
            case ParserRuleContext2.VERSION_NUMBER:
            case ParserRuleContext2.MAJOR_VERSION:
            case ParserRuleContext2.MINOR_VERSION:
            case ParserRuleContext2.PATCH_VERSION:
                return SyntaxKind2.DECIMAL_INTEGER_LITERAL;
            case ParserRuleContext2.IMPORT_DECL_RHS:
            case ParserRuleContext2.IMPORT_SUB_VERSION:
                return SyntaxKind2.SEMICOLON_TOKEN;
            case ParserRuleContext2.STRING_LITERAL:
                return SyntaxKind2.STRING_LITERAL;
            case ParserRuleContext2.NIL_TYPE_DESCRIPTOR:
                return SyntaxKind2.NIL_TYPE_DESC;
            case ParserRuleContext2.OPTIONAL_TYPE_DESCRIPTOR:
                return SyntaxKind2.OPTIONAL_TYPE_DESC;
            case ParserRuleContext2.ARRAY_TYPE_DESCRIPTOR:
                return SyntaxKind2.ARRAY_TYPE_DESC;
            case ParserRuleContext2.OBJECT_MEMBER_WITHOUT_METADATA:
            case ParserRuleContext2.RECORD_FIELD_WITHOUT_METADATA:
            case ParserRuleContext2.PARAMETER_WITHOUT_ANNOTS:
            case ParserRuleContext2.TYPE_DESCRIPTOR:
                return SyntaxKind2.TYPE_DESC;
            case ParserRuleContext2.ARRAY_LENGTH:
                return SyntaxKind2.DECIMAL_INTEGER_LITERAL;
            case ParserRuleContext2.HEX_INTEGER_LITERAL:
                return SyntaxKind2.HEX_INTEGER_LITERAL;
            case ParserRuleContext2.CONSTANT_EXPRESSION:
                return SyntaxKind2.STRING_LITERAL;
            case ParserRuleContext2.CONSTANT_EXPRESSION_START:
            case ParserRuleContext2.XML_NAMESPACE_PREFIX_DECL:
                return SyntaxKind2.SEMICOLON_TOKEN;
            case ParserRuleContext2.NIL_LITERAL:
                return SyntaxKind2.OPEN_PAREN_TOKEN;
            case ParserRuleContext2.DECIMAL_FLOATING_POINT_LITERAL:
                return SyntaxKind2.DECIMAL_FLOATING_POINT_LITERAL;
            case ParserRuleContext2.HEX_FLOATING_POINT_LITERAL:
                return SyntaxKind2.HEX_FLOATING_POINT_LITERAL;
            case ParserRuleContext2.STATEMENT:
            case ParserRuleContext2.STATEMENT_WITHOUT_ANNOTS:
                return SyntaxKind2.CLOSE_BRACE_TOKEN;
            case ParserRuleContext2.DECIMAL_INTEGER_LITERAL:
            case ParserRuleContext2.SIGNED_INT_OR_FLOAT_RHS:
                return SyntaxKind2.DECIMAL_INTEGER_LITERAL;
            case ParserRuleContext2.ENUM_MEMBER_RHS:
            case ParserRuleContext2.ENUM_MEMBER_END:
                return SyntaxKind2.CLOSE_BRACE_TOKEN;
            case ParserRuleContext2.MATCH_PATTERN_RHS:
            case ParserRuleContext2.OPTIONAL_MATCH_GUARD:
                return SyntaxKind2.RIGHT_DOUBLE_ARROW_TOKEN;
            case ParserRuleContext2.FUNCTIONAL_MATCH_PATTERN:
                return SyntaxKind2.OPEN_PAREN_TOKEN;
            case ParserRuleContext2.TOP_LEVEL_NODE_WITHOUT_MODIFIER:
            case ParserRuleContext2.TOP_LEVEL_NODE_WITHOUT_METADATA:
                return SyntaxKind2.EOF_TOKEN;
            default:
                return getExpectedSeperatorTokenKind(ctx);
        }
    }

    protected int getExpectedSeperatorTokenKind(int ctx) {
        switch (ctx) {
            case ParserRuleContext2.BITWISE_AND_OPERATOR:
                return SyntaxKind2.BITWISE_AND_TOKEN;
            case ParserRuleContext2.EQUAL_OR_RIGHT_ARROW:
                return SyntaxKind2.EQUAL_TOKEN;
            case ParserRuleContext2.EOF:
                return SyntaxKind2.EOF_TOKEN;
            case ParserRuleContext2.ASSIGN_OP:
                return SyntaxKind2.EQUAL_TOKEN;
            case ParserRuleContext2.BINARY_OPERATOR:
                return SyntaxKind2.PLUS_TOKEN;
            case ParserRuleContext2.CLOSE_BRACE:
                return SyntaxKind2.CLOSE_BRACE_TOKEN;
            case ParserRuleContext2.CLOSE_PARENTHESIS:
            case ParserRuleContext2.ARG_LIST_END:
                return SyntaxKind2.CLOSE_PAREN_TOKEN;
            case ParserRuleContext2.COMMA:
                return SyntaxKind2.COMMA_TOKEN;
            case ParserRuleContext2.OPEN_BRACE:
                return SyntaxKind2.OPEN_BRACE_TOKEN;
            case ParserRuleContext2.OPEN_PARENTHESIS:
            case ParserRuleContext2.ARG_LIST_START:
            case ParserRuleContext2.PARENTHESISED_TYPE_DESC_START:
                return SyntaxKind2.OPEN_PAREN_TOKEN;
            case ParserRuleContext2.SEMICOLON:
                return SyntaxKind2.SEMICOLON_TOKEN;
            case ParserRuleContext2.ASTERISK:
            case ParserRuleContext2.INFERRED_TYPE_DESC:
                return SyntaxKind2.ASTERISK_TOKEN;
            case ParserRuleContext2.CLOSED_RECORD_BODY_END:
                return SyntaxKind2.CLOSE_BRACE_PIPE_TOKEN;
            case ParserRuleContext2.CLOSED_RECORD_BODY_START:
                return SyntaxKind2.OPEN_BRACE_PIPE_TOKEN;
            case ParserRuleContext2.ELLIPSIS:
                return SyntaxKind2.ELLIPSIS_TOKEN;
            case ParserRuleContext2.QUESTION_MARK:
                return SyntaxKind2.QUESTION_MARK_TOKEN;
            case ParserRuleContext2.RECORD_BODY_START:
                return SyntaxKind2.OPEN_BRACE_PIPE_TOKEN;
            case ParserRuleContext2.RECORD_BODY_END:
                return SyntaxKind2.CLOSE_BRACE_TOKEN;
            case ParserRuleContext2.CLOSE_BRACKET:
            case ParserRuleContext2.MEMBER_ACCESS_KEY_EXPR_END:
                return SyntaxKind2.CLOSE_BRACKET_TOKEN;
            case ParserRuleContext2.DOT:
                return SyntaxKind2.DOT_TOKEN;
            case ParserRuleContext2.OPEN_BRACKET:
            case ParserRuleContext2.TUPLE_TYPE_DESC_START:
                return SyntaxKind2.OPEN_BRACKET_TOKEN;
            case ParserRuleContext2.OBJECT_FIELD_RHS:
                return SyntaxKind2.SEMICOLON_TOKEN;
            case ParserRuleContext2.SLASH:
                return SyntaxKind2.SLASH_TOKEN;
            case ParserRuleContext2.COLON:
                return SyntaxKind2.COLON_TOKEN;
            case ParserRuleContext2.UNARY_OPERATOR:
            case ParserRuleContext2.COMPOUND_BINARY_OPERATOR:
            case ParserRuleContext2.UNARY_EXPRESSION:
            case ParserRuleContext2.EXPRESSION_RHS:
                return SyntaxKind2.PLUS_TOKEN;
            case ParserRuleContext2.AT:
                return SyntaxKind2.AT_TOKEN;
            case ParserRuleContext2.RIGHT_ARROW:
                return SyntaxKind2.RIGHT_ARROW_TOKEN;
            case ParserRuleContext2.GT:
                return SyntaxKind2.GT_TOKEN;
            case ParserRuleContext2.LT:
                return SyntaxKind2.LT_TOKEN;
            case ParserRuleContext2.STMT_START_WITH_EXPR_RHS:
                return SyntaxKind2.EQUAL_TOKEN;
            case ParserRuleContext2.EXPR_STMT_RHS:
                return SyntaxKind2.SEMICOLON_TOKEN;
            case ParserRuleContext2.SYNC_SEND_TOKEN:
                return SyntaxKind2.SYNC_SEND_TOKEN;
            case ParserRuleContext2.ANNOT_CHAINING_TOKEN:
                return SyntaxKind2.ANNOT_CHAINING_TOKEN;
            case ParserRuleContext2.OPTIONAL_CHAINING_TOKEN:
                return SyntaxKind2.OPTIONAL_CHAINING_TOKEN;
            case ParserRuleContext2.DOT_LT_TOKEN:
                return SyntaxKind2.DOT_LT_TOKEN;
            case ParserRuleContext2.SLASH_LT_TOKEN:
                return SyntaxKind2.SLASH_LT_TOKEN;
            case ParserRuleContext2.DOUBLE_SLASH_DOUBLE_ASTERISK_LT_TOKEN:
                return SyntaxKind2.DOUBLE_SLASH_DOUBLE_ASTERISK_LT_TOKEN;
            case ParserRuleContext2.SLASH_ASTERISK_TOKEN:
                return SyntaxKind2.SLASH_ASTERISK_TOKEN;
            case ParserRuleContext2.PLUS_TOKEN:
                return SyntaxKind2.PLUS_TOKEN;
            case ParserRuleContext2.MINUS_TOKEN:
                return SyntaxKind2.MINUS_TOKEN;
            case ParserRuleContext2.LEFT_ARROW_TOKEN:
                return SyntaxKind2.LEFT_ARROW_TOKEN;
            case ParserRuleContext2.RECORD_FIELD_OR_RECORD_END:
                return SyntaxKind2.CLOSE_BRACE_TOKEN;
            case ParserRuleContext2.ATTACH_POINT_END:
                return SyntaxKind2.SEMICOLON_TOKEN;
            case ParserRuleContext2.FIELD_DESCRIPTOR_RHS:
                return SyntaxKind2.SEMICOLON_TOKEN;
            case ParserRuleContext2.CONST_DECL_RHS:
                return SyntaxKind2.EQUAL_TOKEN;
            case ParserRuleContext2.TEMPLATE_END:
            case ParserRuleContext2.TEMPLATE_START:
                return SyntaxKind2.BACKTICK_TOKEN;
            case ParserRuleContext2.LT_TOKEN:
                return SyntaxKind2.LT_TOKEN;
            case ParserRuleContext2.GT_TOKEN:
                return SyntaxKind2.GT_TOKEN;
            case ParserRuleContext2.INTERPOLATION_START_TOKEN:
                return SyntaxKind2.INTERPOLATION_START_TOKEN;
            case ParserRuleContext2.EXPR_FUNC_BODY_START:
            case ParserRuleContext2.RIGHT_DOUBLE_ARROW:
                return SyntaxKind2.RIGHT_DOUBLE_ARROW_TOKEN;
            default:
                return getExpectedKeywordKind(ctx);
        }
    }

    protected int getExpectedKeywordKind(int ctx) {
        switch (ctx) {
            case ParserRuleContext2.EXTERNAL_KEYWORD:
                return SyntaxKind2.EXTERNAL_KEYWORD;
            case ParserRuleContext2.FUNCTION_KEYWORD:
                return SyntaxKind2.FUNCTION_KEYWORD;
            case ParserRuleContext2.RETURNS_KEYWORD:
                return SyntaxKind2.RETURNS_KEYWORD;
            case ParserRuleContext2.PUBLIC_KEYWORD:
                return SyntaxKind2.PUBLIC_KEYWORD;
            case ParserRuleContext2.RECORD_FIELD:
            case ParserRuleContext2.RECORD_KEYWORD:
                return SyntaxKind2.RECORD_KEYWORD;
            case ParserRuleContext2.TYPE_KEYWORD:
                return SyntaxKind2.TYPE_KEYWORD;
            case ParserRuleContext2.OBJECT_KEYWORD:
                return SyntaxKind2.OBJECT_KEYWORD;
            case ParserRuleContext2.PRIVATE_KEYWORD:
                return SyntaxKind2.PRIVATE_KEYWORD;
            case ParserRuleContext2.REMOTE_KEYWORD:
                return SyntaxKind2.REMOTE_KEYWORD;
            case ParserRuleContext2.ABSTRACT_KEYWORD:
                return SyntaxKind2.ABSTRACT_KEYWORD;
            case ParserRuleContext2.CLIENT_KEYWORD:
                return SyntaxKind2.CLIENT_KEYWORD;
            case ParserRuleContext2.OBJECT_TYPE_QUALIFIER:
                return SyntaxKind2.OBJECT_KEYWORD;
            case ParserRuleContext2.IF_KEYWORD:
                return SyntaxKind2.IF_KEYWORD;
            case ParserRuleContext2.ELSE_KEYWORD:
                return SyntaxKind2.ELSE_KEYWORD;
            case ParserRuleContext2.WHILE_KEYWORD:
                return SyntaxKind2.WHILE_KEYWORD;
            case ParserRuleContext2.CHECKING_KEYWORD:
                return SyntaxKind2.CHECK_KEYWORD;
            case ParserRuleContext2.FAIL_KEYWORD:
                return SyntaxKind2.FAIL_KEYWORD;
            case ParserRuleContext2.AS_KEYWORD:
                return SyntaxKind2.AS_KEYWORD;
            case ParserRuleContext2.BOOLEAN_LITERAL:
                return SyntaxKind2.TRUE_KEYWORD;
            case ParserRuleContext2.IMPORT_KEYWORD:
                return SyntaxKind2.IMPORT_KEYWORD;
            case ParserRuleContext2.ON_KEYWORD:
                return SyntaxKind2.ON_KEYWORD;
            case ParserRuleContext2.PANIC_KEYWORD:
                return SyntaxKind2.PANIC_KEYWORD;
            case ParserRuleContext2.RESOURCE_KEYWORD:
                return SyntaxKind2.RESOURCE_KEYWORD;
            case ParserRuleContext2.RETURN_KEYWORD:
                return SyntaxKind2.RETURN_KEYWORD;
            case ParserRuleContext2.SERVICE_KEYWORD:
                return SyntaxKind2.SERVICE_KEYWORD;
            case ParserRuleContext2.BREAK_KEYWORD:
                return SyntaxKind2.BREAK_KEYWORD;
            case ParserRuleContext2.LISTENER_KEYWORD:
                return SyntaxKind2.CONST_KEYWORD;
            case ParserRuleContext2.CONTINUE_KEYWORD:
                return SyntaxKind2.CONTINUE_KEYWORD;
            case ParserRuleContext2.CONST_KEYWORD:
                return SyntaxKind2.CONST_KEYWORD;
            case ParserRuleContext2.FINAL_KEYWORD:
                return SyntaxKind2.FINAL_KEYWORD;
            case ParserRuleContext2.IS_KEYWORD:
                return SyntaxKind2.IS_KEYWORD;
            case ParserRuleContext2.TYPEOF_KEYWORD:
                return SyntaxKind2.TYPEOF_KEYWORD;
            case ParserRuleContext2.TYPEOF_EXPRESSION:
                return SyntaxKind2.TYPEOF_KEYWORD;
            case ParserRuleContext2.MAP_KEYWORD:
                return SyntaxKind2.MAP_KEYWORD;
            case ParserRuleContext2.FUTURE_KEYWORD:
                return SyntaxKind2.FUTURE_KEYWORD;
            case ParserRuleContext2.TYPEDESC_KEYWORD:
                return SyntaxKind2.TYPEDESC_KEYWORD;
            case ParserRuleContext2.NULL_KEYWORD:
                return SyntaxKind2.NULL_KEYWORD;
            case ParserRuleContext2.LOCK_KEYWORD:
                return SyntaxKind2.LOCK_KEYWORD;
            case ParserRuleContext2.ANNOTATION_KEYWORD:
                return SyntaxKind2.ANNOTATION_KEYWORD;
            case ParserRuleContext2.VERSION_KEYWORD:
                return SyntaxKind2.VERSION_KEYWORD;
            case ParserRuleContext2.ANNOT_DECL_RHS:
                return SyntaxKind2.ON_KEYWORD;
            case ParserRuleContext2.ATTACH_POINT_IDENT:
            case ParserRuleContext2.IDENT_AFTER_OBJECT_IDENT:
            case ParserRuleContext2.SINGLE_KEYWORD_ATTACH_POINT_IDENT:
                return SyntaxKind2.TYPE_KEYWORD;
            case ParserRuleContext2.FIELD_IDENT:
                return SyntaxKind2.FIELD_KEYWORD;
            case ParserRuleContext2.FUNCTION_IDENT:
                return SyntaxKind2.FUNCTION_KEYWORD;
            case ParserRuleContext2.OBJECT_IDENT:
                return SyntaxKind2.OBJECT_KEYWORD;
            case ParserRuleContext2.RECORD_IDENT:
                return SyntaxKind2.RECORD_KEYWORD;
            case ParserRuleContext2.RESOURCE_IDENT:
                return SyntaxKind2.RESOURCE_KEYWORD;
            case ParserRuleContext2.XMLNS_KEYWORD:
            case ParserRuleContext2.XML_NAMESPACE_DECLARATION:
                return SyntaxKind2.XMLNS_KEYWORD;
            case ParserRuleContext2.SOURCE_KEYWORD:
                return SyntaxKind2.SOURCE_KEYWORD;
            case ParserRuleContext2.START_KEYWORD:
                return SyntaxKind2.START_KEYWORD;
            case ParserRuleContext2.FLUSH_KEYWORD:
                return SyntaxKind2.FLUSH_KEYWORD;
            case ParserRuleContext2.DEFAULT_KEYWORD:
            case ParserRuleContext2.OPTIONAL_PEER_WORKER:
            case ParserRuleContext2.DEFAULT_WORKER_NAME_IN_ASYNC_SEND:
                return SyntaxKind2.DEFAULT_KEYWORD;
            case ParserRuleContext2.WAIT_KEYWORD:
                return SyntaxKind2.WAIT_KEYWORD;
            case ParserRuleContext2.TRANSACTION_KEYWORD:
                return SyntaxKind2.TRANSACTION_KEYWORD;
            case ParserRuleContext2.TRANSACTIONAL_KEYWORD:
                return SyntaxKind2.TRANSACTIONAL_KEYWORD;
            case ParserRuleContext2.COMMIT_KEYWORD:
                return SyntaxKind2.COMMIT_KEYWORD;
            case ParserRuleContext2.RETRY_KEYWORD:
                return SyntaxKind2.RETRY_KEYWORD;
            case ParserRuleContext2.ROLLBACK_KEYWORD:
                return SyntaxKind2.ROLLBACK_KEYWORD;
            case ParserRuleContext2.ENUM_KEYWORD:
                return SyntaxKind2.ENUM_KEYWORD;
            case ParserRuleContext2.MATCH_KEYWORD:
                return SyntaxKind2.MATCH_KEYWORD;
            case ParserRuleContext2.NEW_KEYWORD:
                return SyntaxKind2.NEW_KEYWORD;
            case ParserRuleContext2.FORK_KEYWORD:
                return SyntaxKind2.FORK_KEYWORD;
            case ParserRuleContext2.NAMED_WORKER_DECL:
            case ParserRuleContext2.WORKER_KEYWORD:
                return SyntaxKind2.WORKER_KEYWORD;
            case ParserRuleContext2.PARAMETERIZED_TYPE:
                return SyntaxKind2.MAP_KEYWORD;
            case ParserRuleContext2.TRAP_KEYWORD:
                return SyntaxKind2.TRAP_KEYWORD;
            case ParserRuleContext2.FOREACH_KEYWORD:
                return SyntaxKind2.FOREACH_KEYWORD;
            case ParserRuleContext2.IN_KEYWORD:
                return SyntaxKind2.IN_KEYWORD;
            case ParserRuleContext2.PIPE:
            case ParserRuleContext2.UNION_OR_INTERSECTION_TOKEN:
                return SyntaxKind2.PIPE_TOKEN;
            case ParserRuleContext2.TABLE_KEYWORD:
                return SyntaxKind2.TABLE_KEYWORD;
            case ParserRuleContext2.KEY_KEYWORD:
                return SyntaxKind2.KEY_KEYWORD;
            case ParserRuleContext2.ERROR_KEYWORD:
                return SyntaxKind2.ERROR_KEYWORD;
            case ParserRuleContext2.STREAM_KEYWORD:
                return SyntaxKind2.STREAM_KEYWORD;
            case ParserRuleContext2.LET_KEYWORD:
                return SyntaxKind2.LET_KEYWORD;
            case ParserRuleContext2.XML_KEYWORD:
                return SyntaxKind2.XML_KEYWORD;
            case ParserRuleContext2.STRING_KEYWORD:
                return SyntaxKind2.STRING_KEYWORD;
            case ParserRuleContext2.BASE16_KEYWORD:
                return SyntaxKind2.BASE16_KEYWORD;
            case ParserRuleContext2.BASE64_KEYWORD:
                return SyntaxKind2.BASE64_KEYWORD;
            case ParserRuleContext2.SELECT_KEYWORD:
                return SyntaxKind2.SELECT_KEYWORD;
            case ParserRuleContext2.WHERE_KEYWORD:
                return SyntaxKind2.WHERE_KEYWORD;
            case ParserRuleContext2.FROM_KEYWORD:
                return SyntaxKind2.FROM_KEYWORD;
            case ParserRuleContext2.ORDER_KEYWORD:
                return SyntaxKind2.ORDER_KEYWORD;
            case ParserRuleContext2.BY_KEYWORD:
                return SyntaxKind2.BY_KEYWORD;
            case ParserRuleContext2.ASCENDING_KEYWORD:
                return SyntaxKind2.ASCENDING_KEYWORD;
            case ParserRuleContext2.DESCENDING_KEYWORD:
                return SyntaxKind2.DESCENDING_KEYWORD;
            case ParserRuleContext2.DO_KEYWORD:
                return SyntaxKind2.DO_KEYWORD;
            case ParserRuleContext2.DISTINCT_KEYWORD:
                return SyntaxKind2.DISTINCT_KEYWORD;
            case ParserRuleContext2.VAR_KEYWORD:
                return SyntaxKind2.VAR_KEYWORD;
            default:
                return SyntaxKind2.NONE;
        }
    }

    /**
     * Check whether a token kind is a basic literal.
     *
     * @param kind Token kind to check
     * @return <code>true</code> if the given token kind belongs to a basic literal.<code>false</code> otherwise
     */
    private boolean isBasicLiteral(int kind) {
        switch (kind) {
            case SyntaxKind2.DECIMAL_INTEGER_LITERAL:
            case SyntaxKind2.HEX_INTEGER_LITERAL:
            case SyntaxKind2.STRING_LITERAL:
            case SyntaxKind2.TRUE_KEYWORD:
            case SyntaxKind2.FALSE_KEYWORD:
            case SyntaxKind2.NULL_KEYWORD:
            case SyntaxKind2.DECIMAL_FLOATING_POINT_LITERAL:
            case SyntaxKind2.HEX_FLOATING_POINT_LITERAL:
                return true;
            default:
                return false;
        }
    }

    /**
     * Check whether the given token refers to a unary operator.
     *
     * @param token Token to check
     * @return <code>true</code> if the given token refers to a unary operator. <code>false</code> otherwise
     */
    private boolean isUnaryOperator(STToken token) {
        switch (token.kind) {
            case SyntaxKind2.PLUS_TOKEN:
            case SyntaxKind2.MINUS_TOKEN:
            case SyntaxKind2.NEGATION_TOKEN:
            case SyntaxKind2.EXCLAMATION_MARK_TOKEN:
                return true;
            default:
                return false;
        }
    }

    private boolean isSingleKeywordAttachPointIdent(int tokenKind) {
        switch (tokenKind) {
            case SyntaxKind2.ANNOTATION_KEYWORD:
            case SyntaxKind2.EXTERNAL_KEYWORD:
            case SyntaxKind2.VAR_KEYWORD:
            case SyntaxKind2.CONST_KEYWORD:
            case SyntaxKind2.LISTENER_KEYWORD:
            case SyntaxKind2.WORKER_KEYWORD:
            case SyntaxKind2.TYPE_KEYWORD:
            case SyntaxKind2.FUNCTION_KEYWORD:
            case SyntaxKind2.PARAMETER_KEYWORD:
            case SyntaxKind2.RETURN_KEYWORD:
            case SyntaxKind2.SERVICE_KEYWORD:
            case SyntaxKind2.FIELD_KEYWORD:
                return true;
            default:
                return false;
        }
    }

    /**
     * Check whether the given token is a parameterized type keyword.
     *
     * @param tokenKind Token to check
     * @return <code>true</code> if the given token is a parameterized type keyword. <code>false</code> otherwise
     */
    public boolean isParameterizedTypeToken(int tokenKind) {
        switch (tokenKind) {
            case SyntaxKind2.MAP_KEYWORD:
            case SyntaxKind2.FUTURE_KEYWORD:
            case SyntaxKind2.TYPEDESC_KEYWORD:
                return true;
            default:
                return false;
        }
    }
}
