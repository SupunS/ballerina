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
package io.ballerinalang.compiler.syntax.tree;

/**
 * Define various kinds of syntax tree nodes, tokens and minutiae.
 *
 * @since 2.0.0
 */
public class SyntaxKind2 {

    // Keywords
    public static final int PUBLIC_KEYWORD = 50;
    public static final int PRIVATE_KEYWORD = 51;
    public static final int REMOTE_KEYWORD = 52;
    public static final int ABSTRACT_KEYWORD = 53;
    public static final int CLIENT_KEYWORD = 54;
    public static final int IMPORT_KEYWORD = 100;
    public static final int FUNCTION_KEYWORD = 101;
    public static final int CONST_KEYWORD = 102;
    public static final int LISTENER_KEYWORD = 103;
    public static final int SERVICE_KEYWORD = 104;
    public static final int XMLNS_KEYWORD = 105;
    public static final int ANNOTATION_KEYWORD = 106;
    public static final int TYPE_KEYWORD = 107;
    public static final int RECORD_KEYWORD = 108;
    public static final int OBJECT_KEYWORD = 109;
    public static final int VERSION_KEYWORD = 110;
    public static final int AS_KEYWORD = 111;
    public static final int ON_KEYWORD = 112;
    public static final int RESOURCE_KEYWORD = 113;
    public static final int FINAL_KEYWORD = 114;
    public static final int SOURCE_KEYWORD = 115;
    public static final int WORKER_KEYWORD = 117;
    public static final int PARAMETER_KEYWORD = 118;
    public static final int FIELD_KEYWORD = 119;

    public static final int RETURNS_KEYWORD = 200;
    public static final int RETURN_KEYWORD = 201;
    public static final int EXTERNAL_KEYWORD = 202;
    public static final int TRUE_KEYWORD = 203;
    public static final int FALSE_KEYWORD = 204;
    public static final int IF_KEYWORD = 205;
    public static final int ELSE_KEYWORD = 206;
    public static final int WHILE_KEYWORD = 207;
    public static final int CHECK_KEYWORD = 208;
    public static final int CHECKPANIC_KEYWORD = 209;
    public static final int PANIC_KEYWORD = 210;
    public static final int CONTINUE_KEYWORD = 211;
    public static final int BREAK_KEYWORD = 212;
    public static final int TYPEOF_KEYWORD = 213;
    public static final int IS_KEYWORD = 214;
    public static final int NULL_KEYWORD = 215;
    public static final int LOCK_KEYWORD = 216;
    public static final int FORK_KEYWORD = 217;
    public static final int TRAP_KEYWORD = 218;
    public static final int IN_KEYWORD = 219;
    public static final int FOREACH_KEYWORD = 220;
    public static final int TABLE_KEYWORD = 221;
    public static final int KEY_KEYWORD = 222;
    public static final int LET_KEYWORD = 223;
    public static final int NEW_KEYWORD = 224;
    public static final int FROM_KEYWORD = 225;
    public static final int WHERE_KEYWORD = 226;
    public static final int SELECT_KEYWORD = 227;
    public static final int START_KEYWORD = 228;
    public static final int FLUSH_KEYWORD = 229;
    public static final int DEFAULT_KEYWORD = 230;
    public static final int WAIT_KEYWORD = 231;
    public static final int DO_KEYWORD = 232;
    public static final int TRANSACTION_KEYWORD = 233;
    public static final int TRANSACTIONAL_KEYWORD = 234;
    public static final int COMMIT_KEYWORD = 235;
    public static final int ROLLBACK_KEYWORD = 236;
    public static final int RETRY_KEYWORD = 237;
    public static final int ENUM_KEYWORD = 238;
    public static final int BASE16_KEYWORD = 239;
    public static final int BASE64_KEYWORD = 240;
    public static final int MATCH_KEYWORD = 241;
    public static final int CONFLICT_KEYWORD = 242;
    public static final int LIMIT_KEYWORD = 243;
    public static final int JOIN_KEYWORD = 244;
    public static final int OUTER_KEYWORD = 245;
    public static final int EQUALS_KEYWORD = 246;
    public static final int ORDER_KEYWORD = 247;
    public static final int BY_KEYWORD = 248;
    public static final int ASCENDING_KEYWORD = 249;
    public static final int DESCENDING_KEYWORD = 250;

    public static final int INT_KEYWORD = 300;
    public static final int BYTE_KEYWORD = 301;
    public static final int FLOAT_KEYWORD = 302;
    public static final int DECIMAL_KEYWORD = 303;
    public static final int STRING_KEYWORD = 304;
    public static final int BOOLEAN_KEYWORD = 305;
    public static final int XML_KEYWORD = 306;
    public static final int JSON_KEYWORD = 307;
    public static final int HANDLE_KEYWORD = 308;
    public static final int ANY_KEYWORD = 309;
    public static final int ANYDATA_KEYWORD = 310;
    public static final int NEVER_KEYWORD = 311;
    public static final int VAR_KEYWORD = 312;
    public static final int MAP_KEYWORD = 313;
    public static final int FUTURE_KEYWORD = 314;
    public static final int TYPEDESC_KEYWORD = 315;
    public static final int ERROR_KEYWORD = 316;
    public static final int STREAM_KEYWORD = 317;
    public static final int READONLY_KEYWORD = 318;
    public static final int DISTINCT_KEYWORD = 319;
    public static final int FAIL_KEYWORD = 320;

    public static final int OPEN_BRACE_TOKEN = 500;
    public static final int CLOSE_BRACE_TOKEN = 501;
    public static final int OPEN_PAREN_TOKEN = 502;
    public static final int CLOSE_PAREN_TOKEN = 503;
    public static final int OPEN_BRACKET_TOKEN = 504;
    public static final int CLOSE_BRACKET_TOKEN = 505;
    public static final int SEMICOLON_TOKEN = 506;
    public static final int DOT_TOKEN = 507;
    public static final int COLON_TOKEN = 508;
    public static final int COMMA_TOKEN = 509;
    public static final int ELLIPSIS_TOKEN = 510;
    public static final int OPEN_BRACE_PIPE_TOKEN = 511;
    public static final int CLOSE_BRACE_PIPE_TOKEN = 512;
    public static final int AT_TOKEN = 513;
    public static final int HASH_TOKEN = 514;
    public static final int BACKTICK_TOKEN = 515;
    public static final int DOUBLE_QUOTE_TOKEN = 516;
    public static final int SINGLE_QUOTE_TOKEN = 517;

    public static final int EQUAL_TOKEN = 550;
    public static final int DOUBLE_EQUAL_TOKEN = 551;
    public static final int TRIPPLE_EQUAL_TOKEN = 552;
    public static final int PLUS_TOKEN = 553;
    public static final int MINUS_TOKEN = 554;
    public static final int SLASH_TOKEN = 555;
    public static final int PERCENT_TOKEN = 556;
    public static final int ASTERISK_TOKEN = 557;
    public static final int LT_TOKEN = 558;
    public static final int LT_EQUAL_TOKEN = 559;
    public static final int GT_TOKEN = 560;
    public static final int RIGHT_DOUBLE_ARROW_TOKEN = 561;
    public static final int QUESTION_MARK_TOKEN = 562;
    public static final int PIPE_TOKEN = 563;
    public static final int GT_EQUAL_TOKEN = 564;
    public static final int EXCLAMATION_MARK_TOKEN = 565;
    public static final int NOT_EQUAL_TOKEN = 566;
    public static final int NOT_DOUBLE_EQUAL_TOKEN = 567;
    public static final int BITWISE_AND_TOKEN = 568;
    public static final int BITWISE_XOR_TOKEN = 569;
    public static final int LOGICAL_AND_TOKEN = 570;
    public static final int LOGICAL_OR_TOKEN = 571;
    public static final int NEGATION_TOKEN = 572;
    public static final int RIGHT_ARROW_TOKEN = 573;
    public static final int INTERPOLATION_START_TOKEN = 574;
    public static final int XML_PI_START_TOKEN = 575;
    public static final int XML_PI_END_TOKEN = 576;
    public static final int XML_COMMENT_START_TOKEN = 577;
    public static final int XML_COMMENT_END_TOKEN = 578;
    public static final int SYNC_SEND_TOKEN = 579;
    public static final int LEFT_ARROW_TOKEN = 580;
    public static final int DOUBLE_DOT_LT_TOKEN = 581;
    public static final int DOUBLE_LT_TOKEN = 582;
    public static final int ANNOT_CHAINING_TOKEN = 583;
    public static final int OPTIONAL_CHAINING_TOKEN = 584;
    public static final int ELVIS_TOKEN = 585;
    public static final int DOT_LT_TOKEN = 586;
    public static final int SLASH_LT_TOKEN = 587;
    public static final int DOUBLE_SLASH_DOUBLE_ASTERISK_LT_TOKEN = 588;
    public static final int SLASH_ASTERISK_TOKEN = 589;
    public static final int DOUBLE_GT_TOKEN = 590;
    public static final int TRIPPLE_GT_TOKEN = 591;

    public static final int TYPE_DOC_REFERENCE_TOKEN = 900;
    public static final int SERVICE_DOC_REFERENCE_TOKEN = 901;
    public static final int VARIABLE_DOC_REFERENCE_TOKEN = 902;
    public static final int VAR_DOC_REFERENCE_TOKEN = 903;
    public static final int ANNOTATION_DOC_REFERENCE_TOKEN = 904;
    public static final int MODULE_DOC_REFERENCE_TOKEN = 905;
    public static final int FUNCTION_DOC_REFERENCE_TOKEN = 906;
    public static final int PARAMETER_DOC_REFERENCE_TOKEN = 907;
    public static final int CONST_DOC_REFERENCE_TOKEN = 908;

    public static final int IDENTIFIER_TOKEN = 1000;
    public static final int STRING_LITERAL = 1001;
    public static final int DECIMAL_INTEGER_LITERAL = 1002;
    public static final int HEX_INTEGER_LITERAL = 1003;
    public static final int DECIMAL_FLOATING_POINT_LITERAL = 1004;
    public static final int HEX_FLOATING_POINT_LITERAL = 1005;
    public static final int XML_TEXT_CONTENT = 1006;
    public static final int TEMPLATE_STRING = 1007;

    public static final int WHITESPACE_MINUTIAE = 1500;
    public static final int END_OF_LINE_MINUTIAE = 1501;
    public static final int COMMENT_MINUTIAE = 1502;
    public static final int INVALID_NODE_MINUTIAE = 1503;

    public static final int INVALID_TOKEN = 1600;

    public static final int IMPORT_DECLARATION = 2000;
    public static final int FUNCTION_DEFINITION = 2001;
    public static final int TYPE_DEFINITION = 2002;
    public static final int SERVICE_DECLARATION = 2003;
    public static final int MODULE_VAR_DECL = 2004;
    public static final int LISTENER_DECLARATION = 2005;
    public static final int CONST_DECLARATION = 2006;
    public static final int ANNOTATION_DECLARATION = 2007;
    public static final int MODULE_XML_NAMESPACE_DECLARATION = 2008;
    public static final int ENUM_DECLARATION = 2009;

    public static final int BLOCK_STATEMENT = 1200;
    public static final int LOCAL_VAR_DECL = 1201;
    public static final int ASSIGNMENT_STATEMENT = 1202;
    public static final int IF_ELSE_STATEMENT = 1203;
    public static final int ELSE_BLOCK = 1204;
    public static final int WHILE_STATEMENT = 1205;
    public static final int CALL_STATEMENT = 1206;
    public static final int PANIC_STATEMENT = 1207;
    public static final int RETURN_STATEMENT = 1208;
    public static final int CONTINUE_STATEMENT = 1209;
    public static final int BREAK_STATEMENT = 1210;
    public static final int COMPOUND_ASSIGNMENT_STATEMENT = 1211;
    public static final int LOCAL_TYPE_DEFINITION_STATEMENT = 1212;
    public static final int ACTION_STATEMENT = 1213;
    public static final int LOCK_STATEMENT = 1214;
    public static final int NAMED_WORKER_DECLARATION = 1215;
    public static final int FORK_STATEMENT = 1216;
    public static final int FOREACH_STATEMENT = 1217;
    public static final int TRANSACTION_STATEMENT = 1218;
    public static final int ROLLBACK_STATEMENT = 1219;
    public static final int RETRY_STATEMENT = 1220;
    public static final int XML_NAMESPACE_DECLARATION = 1221;
    public static final int MATCH_STATEMENT = 1222;
    public static final int INVALID_EXPRESSION_STATEMENT = 1223;

    // Expressions
    public static final int BINARY_EXPRESSION = 1300;
    public static final int BRACED_EXPRESSION = 1301;
    public static final int FUNCTION_CALL = 1302;
    public static final int QUALIFIED_NAME_REFERENCE = 1303;
    public static final int INDEXED_EXPRESSION = 1304;
    public static final int FIELD_ACCESS = 1305;
    public static final int METHOD_CALL = 1306;
    public static final int CHECK_EXPRESSION = 1307;
    public static final int MAPPING_CONSTRUCTOR = 1308;
    public static final int TYPEOF_EXPRESSION = 1309;
    public static final int UNARY_EXPRESSION = 1310;
    public static final int TYPE_TEST_EXPRESSION = 1311;
    public static final int BASIC_LITERAL = 1312;
    public static final int SIMPLE_NAME_REFERENCE = 1313;
    public static final int TRAP_EXPRESSION = 1314;
    public static final int LIST_CONSTRUCTOR = 1315;
    public static final int TYPE_CAST_EXPRESSION = 1316;
    public static final int TABLE_CONSTRUCTOR = 1317;
    public static final int LET_EXPRESSION = 1318;
    public static final int XML_TEMPLATE_EXPRESSION = 1319;
    public static final int RAW_TEMPLATE_EXPRESSION = 1320;
    public static final int STRING_TEMPLATE_EXPRESSION = 1321;
    public static final int IMPLICIT_NEW_EXPRESSION = 1322;
    public static final int EXPLICIT_NEW_EXPRESSION = 1323;
    public static final int PARENTHESIZED_ARG_LIST = 1324;
    public static final int EXPLICIT_ANONYMOUS_FUNCTION_EXPRESSION = 1325;
    public static final int IMPLICIT_ANONYMOUS_FUNCTION_EXPRESSION = 1326;
    public static final int QUERY_EXPRESSION = 1327;
    public static final int ANNOT_ACCESS = 1328;
    public static final int OPTIONAL_FIELD_ACCESS = 1329;
    public static final int CONDITIONAL_EXPRESSION = 1330;
    public static final int TRANSACTIONAL_EXPRESSION = 1331;
    public static final int SERVICE_CONSTRUCTOR_EXPRESSION = 1332;
    public static final int XML_FILTER_EXPRESSION = 1333;
    public static final int XML_STEP_EXPRESSION = 1334;
    public static final int XML_NAME_PATTERN_CHAIN = 1335;
    public static final int XML_ATOMIC_NAME_PATTERN = 1336;
    public static final int FAIL_EXPRESSION = 1337;

    // Type descriptors
    public static final int TYPE_DESC = 2000;
    public static final int RECORD_TYPE_DESC = 2001;
    public static final int OBJECT_TYPE_DESC = 2002;
    public static final int NIL_TYPE_DESC = 2003;
    public static final int OPTIONAL_TYPE_DESC = 2004;
    public static final int ARRAY_TYPE_DESC = 2005;
    public static final int INT_TYPE_DESC = 2006;
    public static final int BYTE_TYPE_DESC = 2007;
    public static final int FLOAT_TYPE_DESC = 2008;
    public static final int DECIMAL_TYPE_DESC = 2009;
    public static final int STRING_TYPE_DESC = 2010;
    public static final int BOOLEAN_TYPE_DESC = 2011;
    public static final int XML_TYPE_DESC = 2012;
    public static final int JSON_TYPE_DESC = 2013;
    public static final int HANDLE_TYPE_DESC = 2014;
    public static final int ANY_TYPE_DESC = 2015;
    public static final int ANYDATA_TYPE_DESC = 2016;
    public static final int NEVER_TYPE_DESC = 2017;
    public static final int VAR_TYPE_DESC = 2018;
    public static final int SERVICE_TYPE_DESC = 2019;
    public static final int PARAMETERIZED_TYPE_DESC = 2020;
    public static final int UNION_TYPE_DESC = 2021;
    public static final int ERROR_TYPE_DESC = 2022;
    public static final int STREAM_TYPE_DESC = 2023;
    public static final int TABLE_TYPE_DESC = 2024;
    public static final int FUNCTION_TYPE_DESC = 2025;
    public static final int TUPLE_TYPE_DESC = 2026;
    public static final int PARENTHESISED_TYPE_DESC = 2027;
    public static final int READONLY_TYPE_DESC = 2028;
    public static final int DISTINCT_TYPE_DESC = 2029;
    public static final int INTERSECTION_TYPE_DESC = 2030;
    public static final int SINGLETON_TYPE_DESC = 2031;
    public static final int TYPE_REFERENCE_TYPE_DESC = 2032;
    public static final int TYPEDESC_TYPE_DESC = 2033;


    // Actions
    public static final int REMOTE_METHOD_CALL_ACTION = 2500;
    public static final int BRACED_ACTION = 2501;
    public static final int CHECK_ACTION = 2502;
    public static final int START_ACTION = 2503;
    public static final int TRAP_ACTION = 2504;
    public static final int FLUSH_ACTION = 2505;
    public static final int ASYNC_SEND_ACTION = 2506;
    public static final int SYNC_SEND_ACTION = 2507;
    public static final int RECEIVE_ACTION = 2508;
    public static final int WAIT_ACTION = 2509;
    public static final int QUERY_ACTION = 2510;
    public static final int COMMIT_ACTION = 2511;
    public static final int FAIL_ACTION = 2512;

    // Other
    public static final int RETURN_TYPE_DESCRIPTOR = 3000;
    public static final int REQUIRED_PARAM = 3001;
    public static final int DEFAULTABLE_PARAM = 3002;
    public static final int REST_PARAM = 3003;
    public static final int EXTERNAL_FUNCTION_BODY = 3004;
    public static final int RECORD_FIELD = 3005;
    public static final int RECORD_FIELD_WITH_DEFAULT_VALUE = 3006;
    public static final int TYPE_REFERENCE = 3007;
    public static final int RECORD_REST_TYPE = 3008;
    public static final int POSITIONAL_ARG = 3009;
    public static final int NAMED_ARG = 3010;
    public static final int REST_ARG = 3011;
    public static final int OBJECT_FIELD = 3012;
    public static final int IMPORT_ORG_NAME = 3013;
    public static final int MODULE_NAME = 3014;
    public static final int SUB_MODULE_NAME = 3015;
    public static final int IMPORT_VERSION = 3016;
    public static final int IMPORT_SUB_VERSION = 3017;
    public static final int IMPORT_PREFIX = 3018;
    public static final int SPECIFIC_FIELD = 3019;
    public static final int COMPUTED_NAME_FIELD = 3020;
    public static final int SPREAD_FIELD = 3021;
    public static final int EXPRESSION_LIST_ITEM = 3022;
    public static final int SERVICE_BODY = 3023;
    public static final int ANNOTATION = 3024;
    public static final int METADATA = 3025;
    public static final int ARRAY_DIMENSION = 3026;
    public static final int NIL_LITERAL = 3027;
    public static final int ANNOTATION_ATTACH_POINT = 3028;
    public static final int FUNCTION_BODY_BLOCK = 3029;
    public static final int NAMED_WORKER_DECLARATOR = 3030;
    public static final int EXPRESSION_FUNCTION_BODY = 3031;
    public static final int TYPE_CAST_PARAM = 3032;
    public static final int KEY_SPECIFIER = 3033;
    public static final int EXPLICIT_TYPE_PARAMS = 3034;
    public static final int ERROR_TYPE_PARAMS = 3035;
    public static final int LET_VAR_DECL = 3036;
    public static final int STREAM_TYPE_PARAMS = 3037;
    public static final int FUNCTION_SIGNATURE = 3038;
    public static final int INFER_PARAM_LIST = 3039;
    public static final int TYPE_PARAMETER = 3040;
    public static final int KEY_TYPE_CONSTRAINT = 3041;
    public static final int QUERY_CONSTRUCT_TYPE = 3042;
    public static final int FROM_CLAUSE = 3043;
    public static final int WHERE_CLAUSE = 3044;
    public static final int LET_CLAUSE = 3045;
    public static final int QUERY_PIPELINE = 3046;
    public static final int SELECT_CLAUSE = 3047;
    public static final int METHOD_DECLARATION = 3048;
    public static final int TYPED_BINDING_PATTERN = 3049;
    public static final int BINDING_PATTERN = 3050;
    public static final int CAPTURE_BINDING_PATTERN = 3051;
    public static final int REST_BINDING_PATTERN = 3052;
    public static final int LIST_BINDING_PATTERN = 3053;
    public static final int RECEIVE_FIELDS = 3054;
    public static final int REST_TYPE = 3055;
    public static final int WAIT_FIELDS_LIST = 3056;
    public static final int WAIT_FIELD = 3057;
    public static final int ENUM_MEMBER = 3058;
    public static final int BRACKETED_LIST = 3059;
    public static final int LIST_BP_OR_LIST_CONSTRUCTOR = 3060;
    public static final int BYTE_ARRAY_LITERAL = 3061;
    public static final int MAPPING_BINDING_PATTERN = 3062;
    public static final int FIELD_BINDING_PATTERN = 3063;
    public static final int MAPPING_BP_OR_MAPPING_CONSTRUCTOR = 3064;
    public static final int WILDCARD_BINDING_PATTERN = 3065;
    public static final int MATCH_CLAUSE = 3066;
    public static final int MATCH_GUARD = 3067;
    public static final int OBJECT_METHOD_DEFINITION = 3068;
    public static final int ON_CONFLICT_CLAUSE = 3069;
    public static final int LIMIT_CLAUSE = 3070;
    public static final int JOIN_CLAUSE = 3071;
    public static final int ON_CLAUSE = 3072;
    public static final int LIST_MATCH_PATTERN = 3073;
    public static final int REST_MATCH_PATTERN = 3074;
    public static final int MAPPING_MATCH_PATTERN = 3075;
    public static final int FIELD_MATCH_PATTERN = 3076;
    public static final int FUNCTIONAL_MATCH_PATTERN = 3077;
    public static final int NAMED_ARG_MATCH_PATTERN = 3078;
    public static final int FUNCTIONAL_BINDING_PATTERN = 3079;
    public static final int NAMED_ARG_BINDING_PATTERN = 3080;
    public static final int TUPLE_TYPE_DESC_OR_LIST_CONST = 3081;
    public static final int ORDER_BY_CLAUSE = 3082;
    public static final int ORDER_KEY = 3083;

    // XML
    public static final int XML_ELEMENT = 4000;
    public static final int XML_EMPTY_ELEMENT = 4001;
    public static final int XML_TEXT = 4002;
    public static final int XML_COMMENT = 4003;
    public static final int XML_PI = 4004;
    public static final int XML_ELEMENT_START_TAG = 4005;
    public static final int XML_ELEMENT_END_TAG = 4006;
    public static final int XML_SIMPLE_NAME = 4007;
    public static final int XML_QUALIFIED_NAME = 4008;
    public static final int XML_ATTRIBUTE = 4009;
    public static final int XML_ATTRIBUTE_VALUE = 4010;
    public static final int INTERPOLATION = 4011;

    // Documentation
    public static final int MARKDOWN_DOCUMENTATION = 4500;
    public static final int MARKDOWN_DOCUMENTATION_LINE = 4501;
    public static final int MARKDOWN_REFERENCE_DOCUMENTATION_LINE = 4502;
    public static final int MARKDOWN_PARAMETER_DOCUMENTATION_LINE = 4503;
    public static final int MARKDOWN_RETURN_PARAMETER_DOCUMENTATION_LINE = 4504;
    public static final int MARKDOWN_DEPRECATION_DOCUMENTATION_LINE = 4505;
    public static final int DOCUMENTATION_DESCRIPTION = 4506;
    public static final int DOCUMENTATION_REFERENCE = 4507;
    public static final int PARAMETER_NAME = 4508;
    public static final int BACKTICK_CONTENT = 4509;
    public static final int DEPRECATION_LITERAL = 4510;
    public static final int DOCUMENTATION_STRING = 4511;

    public static final int INVALID = 4;
    public static final int MODULE_PART = 3;
    public static final int EOF_TOKEN = 2;
    public static final int LIST = 1;
    public static final int NONE = 0;
}
