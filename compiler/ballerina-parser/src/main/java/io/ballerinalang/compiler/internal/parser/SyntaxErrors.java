/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerinalang.compiler.internal.parser;

import io.ballerinalang.compiler.internal.diagnostics.DiagnosticCode;
import io.ballerinalang.compiler.internal.diagnostics.DiagnosticErrorCode;
import io.ballerinalang.compiler.internal.parser.tree.STNode;
import io.ballerinalang.compiler.internal.parser.tree.STNodeDiagnostic;
import io.ballerinalang.compiler.internal.parser.tree.STNodeFactory;
import io.ballerinalang.compiler.internal.parser.tree.STNodeList;
import io.ballerinalang.compiler.internal.parser.tree.STToken;
import io.ballerinalang.compiler.syntax.tree.SyntaxKind2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * This class contains utilities to handle syntax errors.
 *
 * @since 2.0.0
 */
public class SyntaxErrors {

    private SyntaxErrors() {
    }

    public static STNodeDiagnostic createDiagnostic(DiagnosticCode diagnosticCode, Object... args) {
        return STNodeDiagnostic.from(diagnosticCode, args);
    }

    public static <T extends STNode> T addDiagnostic(T node, DiagnosticCode diagnosticCode, Object... args) {
        return addSyntaxDiagnostic(node, createDiagnostic(diagnosticCode, args));
    }

    public static <T extends STNode> T addSyntaxDiagnostic(T node, STNodeDiagnostic diagnostic) {
        return addSyntaxDiagnostics(node, Collections.singletonList(diagnostic));
    }

    @SuppressWarnings("unchecked")
    public static <T extends STNode> T addSyntaxDiagnostics(T node, Collection<STNodeDiagnostic> diagnostics) {
        if (diagnostics.isEmpty()) {
            return node;
        }

        Collection<STNodeDiagnostic> newDiagnostics;
        Collection<STNodeDiagnostic> oldDiagnostics = node.diagnostics();
        if (oldDiagnostics.isEmpty()) {
            newDiagnostics = new ArrayList<>(diagnostics);
        } else {
            // Merge all diagnostics
            newDiagnostics = new ArrayList<>(oldDiagnostics);
            newDiagnostics.addAll(diagnostics);
        }
        return (T) node.modifyWith(newDiagnostics);
    }

    public static STToken createMissingToken(int expectedKind) {
        return STNodeFactory.createMissingToken(expectedKind);
    }

    public static STToken createMissingTokenWithDiagnostics(int expectedKind) {
        return createMissingTokenWithDiagnostics(expectedKind, getErrorCode(expectedKind));
    }

    public static STToken createMissingTokenWithDiagnostics(int expectedKind,
                                                            DiagnosticCode diagnosticCode) {
        List<STNodeDiagnostic> diagnosticList = new ArrayList<>();
        diagnosticList.add(createDiagnostic(diagnosticCode));
        return STNodeFactory.createMissingToken(expectedKind, diagnosticList);
    }

    private static DiagnosticCode getErrorCode(int expectedKind) {
        switch (expectedKind) {
            // Keywords
            case SyntaxKind2.PUBLIC_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_PUBLIC_KEYWORD;
            case SyntaxKind2.PRIVATE_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_PRIVATE_KEYWORD;
            case SyntaxKind2.REMOTE_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_REMOTE_KEYWORD;
            case SyntaxKind2.ABSTRACT_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_ABSTRACT_KEYWORD;
            case SyntaxKind2.CLIENT_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_CLIENT_KEYWORD;
            case SyntaxKind2.IMPORT_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_IMPORT_KEYWORD;
            case SyntaxKind2.FUNCTION_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_FUNCTION_KEYWORD;
            case SyntaxKind2.CONST_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_CONST_KEYWORD;
            case SyntaxKind2.LISTENER_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_LISTENER_KEYWORD;
            case SyntaxKind2.SERVICE_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_SERVICE_KEYWORD;
            case SyntaxKind2.XMLNS_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_XMLNS_KEYWORD;
            case SyntaxKind2.ANNOTATION_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_ANNOTATION_KEYWORD;
            case SyntaxKind2.TYPE_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_TYPE_KEYWORD;
            case SyntaxKind2.RECORD_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_RECORD_KEYWORD;
            case SyntaxKind2.OBJECT_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_OBJECT_KEYWORD;
            case SyntaxKind2.VERSION_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_VERSION_KEYWORD;
            case SyntaxKind2.AS_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_AS_KEYWORD;
            case SyntaxKind2.ON_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_ON_KEYWORD;
            case SyntaxKind2.RESOURCE_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_RESOURCE_KEYWORD;
            case SyntaxKind2.FINAL_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_FINAL_KEYWORD;
            case SyntaxKind2.SOURCE_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_SOURCE_KEYWORD;
            case SyntaxKind2.WORKER_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_WORKER_KEYWORD;
            case SyntaxKind2.PARAMETER_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_PARAMETER_KEYWORD;
            case SyntaxKind2.FIELD_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_FIELD_KEYWORD;

            case SyntaxKind2.RETURNS_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_RETURNS_KEYWORD;
            case SyntaxKind2.RETURN_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_RETURN_KEYWORD;
            case SyntaxKind2.EXTERNAL_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_EXTERNAL_KEYWORD;
            case SyntaxKind2.TRUE_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_TRUE_KEYWORD;
            case SyntaxKind2.FALSE_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_FALSE_KEYWORD;
            case SyntaxKind2.IF_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_IF_KEYWORD;
            case SyntaxKind2.ELSE_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_ELSE_KEYWORD;
            case SyntaxKind2.WHILE_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_WHILE_KEYWORD;
            case SyntaxKind2.CHECK_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_CHECK_KEYWORD;
            case SyntaxKind2.CHECKPANIC_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_CHECKPANIC_KEYWORD;
            case SyntaxKind2.PANIC_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_PANIC_KEYWORD;
            case SyntaxKind2.CONTINUE_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_CONTINUE_KEYWORD;
            case SyntaxKind2.BREAK_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_BREAK_KEYWORD;
            case SyntaxKind2.TYPEOF_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_TYPEOF_KEYWORD;
            case SyntaxKind2.IS_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_IS_KEYWORD;
            case SyntaxKind2.NULL_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_NULL_KEYWORD;
            case SyntaxKind2.LOCK_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_LOCK_KEYWORD;
            case SyntaxKind2.FORK_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_FORK_KEYWORD;
            case SyntaxKind2.TRAP_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_TRAP_KEYWORD;
            case SyntaxKind2.IN_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_IN_KEYWORD;
            case SyntaxKind2.FOREACH_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_FOREACH_KEYWORD;
            case SyntaxKind2.TABLE_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_TABLE_KEYWORD;
            case SyntaxKind2.KEY_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_KEY_KEYWORD;
            case SyntaxKind2.LET_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_LET_KEYWORD;
            case SyntaxKind2.NEW_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_NEW_KEYWORD;
            case SyntaxKind2.FROM_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_FROM_KEYWORD;
            case SyntaxKind2.WHERE_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_WHERE_KEYWORD;
            case SyntaxKind2.SELECT_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_SELECT_KEYWORD;
            case SyntaxKind2.START_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_START_KEYWORD;
            case SyntaxKind2.FLUSH_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_FLUSH_KEYWORD;
            case SyntaxKind2.DEFAULT_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_DEFAULT_KEYWORD;
            case SyntaxKind2.WAIT_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_WAIT_KEYWORD;
            case SyntaxKind2.DO_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_DO_KEYWORD;
            case SyntaxKind2.TRANSACTION_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_TRANSACTION_KEYWORD;
            case SyntaxKind2.TRANSACTIONAL_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_TRANSACTIONAL_KEYWORD;
            case SyntaxKind2.COMMIT_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_COMMIT_KEYWORD;
            case SyntaxKind2.ROLLBACK_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_ROLLBACK_KEYWORD;
            case SyntaxKind2.RETRY_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_RETRY_KEYWORD;
            case SyntaxKind2.ENUM_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_ENUM_KEYWORD;
            case SyntaxKind2.BASE16_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_BASE16_KEYWORD;
            case SyntaxKind2.BASE64_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_BASE64_KEYWORD;
            case SyntaxKind2.MATCH_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_MATCH_KEYWORD;

            // Type keywords
            case SyntaxKind2.INT_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_INT_KEYWORD;
            case SyntaxKind2.BYTE_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_BYTE_KEYWORD;
            case SyntaxKind2.FLOAT_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_FLOAT_KEYWORD;
            case SyntaxKind2.DECIMAL_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_DECIMAL_KEYWORD;
            case SyntaxKind2.STRING_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_STRING_KEYWORD;
            case SyntaxKind2.BOOLEAN_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_BOOLEAN_KEYWORD;
            case SyntaxKind2.XML_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_XML_KEYWORD;
            case SyntaxKind2.JSON_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_JSON_KEYWORD;
            case SyntaxKind2.HANDLE_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_HANDLE_KEYWORD;
            case SyntaxKind2.ANY_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_ANY_KEYWORD;
            case SyntaxKind2.ANYDATA_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_ANYDATA_KEYWORD;
            case SyntaxKind2.NEVER_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_NEVER_KEYWORD;
            case SyntaxKind2.VAR_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_VAR_KEYWORD;
            case SyntaxKind2.MAP_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_MAP_KEYWORD;
            case SyntaxKind2.FUTURE_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_FUTURE_KEYWORD;
            case SyntaxKind2.TYPEDESC_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_TYPEDESC_KEYWORD;
            case SyntaxKind2.ERROR_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_ERROR_KEYWORD;
            case SyntaxKind2.STREAM_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_STREAM_KEYWORD;
            case SyntaxKind2.READONLY_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_READONLY_KEYWORD;
            case SyntaxKind2.DISTINCT_KEYWORD:
                return DiagnosticErrorCode.ERROR_MISSING_DISTINCT_KEYWORD;

            // Separators
            case SyntaxKind2.OPEN_BRACE_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_OPEN_BRACE_TOKEN;
            case SyntaxKind2.CLOSE_BRACE_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_CLOSE_BRACE_TOKEN;
            case SyntaxKind2.OPEN_PAREN_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_OPEN_PAREN_TOKEN;
            case SyntaxKind2.CLOSE_PAREN_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_CLOSE_PAREN_TOKEN;
            case SyntaxKind2.OPEN_BRACKET_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_OPEN_BRACKET_TOKEN;
            case SyntaxKind2.CLOSE_BRACKET_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_CLOSE_BRACKET_TOKEN;
            case SyntaxKind2.SEMICOLON_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_SEMICOLON_TOKEN;
            case SyntaxKind2.DOT_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_DOT_TOKEN;
            case SyntaxKind2.COLON_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_COLON_TOKEN;
            case SyntaxKind2.COMMA_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_COMMA_TOKEN;
            case SyntaxKind2.ELLIPSIS_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_ELLIPSIS_TOKEN;
            case SyntaxKind2.OPEN_BRACE_PIPE_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_OPEN_BRACE_PIPE_TOKEN;
            case SyntaxKind2.CLOSE_BRACE_PIPE_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_CLOSE_BRACE_PIPE_TOKEN;
            case SyntaxKind2.AT_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_AT_TOKEN;
            case SyntaxKind2.HASH_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_HASH_TOKEN;
            case SyntaxKind2.BACKTICK_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_BACKTICK_TOKEN;
            case SyntaxKind2.DOUBLE_QUOTE_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_DOUBLE_QUOTE_TOKEN;
            case SyntaxKind2.SINGLE_QUOTE_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_SINGLE_QUOTE_TOKEN;

            // Operators
            case SyntaxKind2.EQUAL_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_EQUAL_TOKEN;
            case SyntaxKind2.DOUBLE_EQUAL_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_DOUBLE_EQUAL_TOKEN;
            case SyntaxKind2.TRIPPLE_EQUAL_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_TRIPPLE_EQUAL_TOKEN;
            case SyntaxKind2.PLUS_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_PLUS_TOKEN;
            case SyntaxKind2.MINUS_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_MINUS_TOKEN;
            case SyntaxKind2.SLASH_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_SLASH_TOKEN;
            case SyntaxKind2.PERCENT_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_PERCENT_TOKEN;
            case SyntaxKind2.ASTERISK_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_ASTERISK_TOKEN;
            case SyntaxKind2.LT_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_LT_TOKEN;
            case SyntaxKind2.LT_EQUAL_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_LT_EQUAL_TOKEN;
            case SyntaxKind2.GT_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_GT_TOKEN;
            case SyntaxKind2.RIGHT_DOUBLE_ARROW_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_RIGHT_DOUBLE_ARROW_TOKEN;
            case SyntaxKind2.QUESTION_MARK_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_QUESTION_MARK_TOKEN;
            case SyntaxKind2.PIPE_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_PIPE_TOKEN;
            case SyntaxKind2.GT_EQUAL_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_GT_EQUAL_TOKEN;
            case SyntaxKind2.EXCLAMATION_MARK_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_EXCLAMATION_MARK_TOKEN;
            case SyntaxKind2.NOT_EQUAL_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_NOT_EQUAL_TOKEN;
            case SyntaxKind2.NOT_DOUBLE_EQUAL_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_NOT_DOUBLE_EQUAL_TOKEN;
            case SyntaxKind2.BITWISE_AND_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_BITWISE_AND_TOKEN;
            case SyntaxKind2.BITWISE_XOR_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_BITWISE_XOR_TOKEN;
            case SyntaxKind2.LOGICAL_AND_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_LOGICAL_AND_TOKEN;
            case SyntaxKind2.LOGICAL_OR_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_LOGICAL_OR_TOKEN;
            case SyntaxKind2.NEGATION_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_NEGATION_TOKEN;
            case SyntaxKind2.RIGHT_ARROW_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_RIGHT_ARROW_TOKEN;
            case SyntaxKind2.INTERPOLATION_START_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_INTERPOLATION_START_TOKEN;
            case SyntaxKind2.XML_PI_START_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_XML_PI_START_TOKEN;
            case SyntaxKind2.XML_PI_END_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_XML_PI_END_TOKEN;
            case SyntaxKind2.XML_COMMENT_START_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_XML_COMMENT_START_TOKEN;
            case SyntaxKind2.XML_COMMENT_END_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_XML_COMMENT_END_TOKEN;
            case SyntaxKind2.SYNC_SEND_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_SYNC_SEND_TOKEN;
            case SyntaxKind2.LEFT_ARROW_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_LEFT_ARROW_TOKEN;
            case SyntaxKind2.DOUBLE_DOT_LT_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_DOUBLE_DOT_LT_TOKEN;
            case SyntaxKind2.DOUBLE_LT_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_DOUBLE_LT_TOKEN;
            case SyntaxKind2.ANNOT_CHAINING_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_ANNOT_CHAINING_TOKEN;
            case SyntaxKind2.OPTIONAL_CHAINING_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_OPTIONAL_CHAINING_TOKEN;
            case SyntaxKind2.ELVIS_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_ELVIS_TOKEN;
            case SyntaxKind2.DOT_LT_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_DOT_LT_TOKEN;
            case SyntaxKind2.SLASH_LT_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_SLASH_LT_TOKEN;
            case SyntaxKind2.DOUBLE_SLASH_DOUBLE_ASTERISK_LT_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_DOUBLE_SLASH_DOUBLE_ASTERISK_LT_TOKEN;
            case SyntaxKind2.SLASH_ASTERISK_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_SLASH_ASTERISK_TOKEN;
            case SyntaxKind2.DOUBLE_GT_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_DOUBLE_GT_TOKEN;
            case SyntaxKind2.TRIPPLE_GT_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_TRIPPLE_GT_TOKEN;

            case SyntaxKind2.IDENTIFIER_TOKEN:
                return DiagnosticErrorCode.ERROR_MISSING_IDENTIFIER;
            case SyntaxKind2.STRING_LITERAL:
                return DiagnosticErrorCode.ERROR_MISSING_STRING_LITERAL;
            case SyntaxKind2.DECIMAL_INTEGER_LITERAL:
                return DiagnosticErrorCode.ERROR_MISSING_DECIMAL_INTEGER_LITERAL;
            case SyntaxKind2.HEX_INTEGER_LITERAL:
                return DiagnosticErrorCode.ERROR_MISSING_HEX_INTEGER_LITERAL;
            case SyntaxKind2.DECIMAL_FLOATING_POINT_LITERAL:
                return DiagnosticErrorCode.ERROR_MISSING_DECIMAL_FLOATING_POINT_LITERAL;
            case SyntaxKind2.HEX_FLOATING_POINT_LITERAL:
                return DiagnosticErrorCode.ERROR_MISSING_HEX_FLOATING_POINT_LITERAL;
            case SyntaxKind2.XML_TEXT_CONTENT:
                return DiagnosticErrorCode.ERROR_MISSING_XML_TEXT_CONTENT;
            case SyntaxKind2.TEMPLATE_STRING:
                return DiagnosticErrorCode.ERROR_MISSING_TEMPLATE_STRING;

            case SyntaxKind2.TYPE_DESC:
                return DiagnosticErrorCode.ERROR_MISSING_TYPE_DESC;
            default:
                return DiagnosticErrorCode.ERROR_SYNTAX_ERROR;
        }
    }

    /**
     * Clone the given {@code STNode} with the invalid node as leading minutiae.
     *
     * @param toClone     the node to be cloned
     * @param invalidNode the invalid node
     * @return a cloned node with the given invalidNode minutiae
     */
    public static STNode cloneWithLeadingInvalidNodeMinutiae(STNode toClone, STNode invalidNode) {
        return cloneWithLeadingInvalidNodeMinutiae(toClone, invalidNode, null);
    }

    /**
     * Clone the given {@code STNode} with the invalid node as leading minutiae.
     *
     * @param toClone        the node to be cloned
     * @param invalidNode    the invalid node
     * @param diagnosticCode the {@code DiagnosticCode} to be added
     * @param args           additional arguments required to format the diagnostic message
     * @return a cloned node with the given invalidNode minutiae
     */
    public static STNode cloneWithLeadingInvalidNodeMinutiae(STNode toClone,
                                                             STNode invalidNode,
                                                             DiagnosticCode diagnosticCode,
                                                             Object... args) {
        STToken firstToken = toClone.firstToken();
        STToken firstTokenWithInvalidNodeMinutiae = cloneWithLeadingInvalidNodeMinutiae(firstToken,
                invalidNode, diagnosticCode, args);
        return toClone.replace(firstToken, firstTokenWithInvalidNodeMinutiae);
    }

    /**
     * Clone the given {@code STToken} with the invalid node as leading minutiae.
     *
     * @param toClone        the token to be cloned
     * @param invalidNode    the invalid node
     * @param diagnosticCode the {@code DiagnosticCode} to be added
     * @param args           additional arguments required to format the diagnostic message
     * @return a cloned token with the given invalidNode minutiae
     */
    public static STToken cloneWithLeadingInvalidNodeMinutiae(STToken toClone,
                                                              STNode invalidNode,
                                                              DiagnosticCode diagnosticCode,
                                                              Object... args) {
        STNode invalidNodeMinutiae = STNodeFactory.createInvalidNodeMinutiae(invalidNode);
        STNodeList leadingMinutiae = (STNodeList) toClone.leadingMinutiae();
        leadingMinutiae = leadingMinutiae.add(0, invalidNodeMinutiae);
        STToken cloned = toClone.modifyWith(leadingMinutiae, toClone.trailingMinutiae());
        return diagnosticCode == null ? cloned : addDiagnostic(cloned, diagnosticCode, args);
    }

    /**
     * Clone the given {@code STNode} with the invalid node as trailing minutiae.
     *
     * @param toClone     the node to be cloned
     * @param invalidNode the invalid node
     * @return a cloned node with the given invalidNode minutiae
     */
    public static STNode cloneWithTrailingInvalidNodeMinutiae(STNode toClone, STNode invalidNode) {
        return cloneWithTrailingInvalidNodeMinutiae(toClone, invalidNode, null);
    }

    /**
     * Clone the given {@code STNode} with the invalid node as trailing minutiae.
     *
     * @param toClone        the node to be cloned
     * @param invalidNode    the invalid node
     * @param diagnosticCode the {@code DiagnosticCode} to be added
     * @param args           additional arguments required to format the diagnostic message
     * @return a cloned node with the given invalidNode minutiae
     */
    public static STNode cloneWithTrailingInvalidNodeMinutiae(STNode toClone,
                                                              STNode invalidNode,
                                                              DiagnosticCode diagnosticCode,
                                                              Object... args) {
        STToken lastToken = toClone.lastToken();
        STToken lastTokenWithInvalidNodeMinutiae = cloneWithTrailingInvalidNodeMinutiae(lastToken,
                invalidNode, diagnosticCode, args);
        return toClone.replace(lastToken, lastTokenWithInvalidNodeMinutiae);
    }

    /**
     * Clone the given {@code STToken} with the invalid node as trailing minutiae.
     *
     * @param toClone        the token to be cloned
     * @param invalidNode    the invalid node
     * @param diagnosticCode the {@code DiagnosticCode} to be added
     * @param args           additional arguments required to format the diagnostic message
     * @return a cloned token with the given invalidNode minutiae
     */
    public static STToken cloneWithTrailingInvalidNodeMinutiae(STToken toClone,
                                                               STNode invalidNode,
                                                               DiagnosticCode diagnosticCode,
                                                               Object... args) {
        STNode invalidNodeMinutiae = STNodeFactory.createInvalidNodeMinutiae(invalidNode);
        STNodeList trailingMinutiae = (STNodeList) toClone.trailingMinutiae();
        trailingMinutiae = trailingMinutiae.add(invalidNodeMinutiae);
        STToken cloned = toClone.modifyWith(toClone.leadingMinutiae(), trailingMinutiae);
        return diagnosticCode == null ? cloned : addDiagnostic(cloned, diagnosticCode, args);
    }
}
