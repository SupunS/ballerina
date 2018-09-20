/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.ballerinalang.compiler.tree.statements;

import org.ballerinalang.model.tree.NodeKind;
import org.ballerinalang.model.tree.TupleVariableNode;
import org.ballerinalang.model.tree.statements.MatchNode;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.tree.BLangNode;
import org.wso2.ballerinalang.compiler.tree.BLangNodeVisitor;
import org.wso2.ballerinalang.compiler.tree.BLangSimpleVariable;
import org.wso2.ballerinalang.compiler.tree.BLangTupleVariable;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangExpression;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

/**
 * {@code BLangMatch} represents a type switch statement in Ballerina.
 *
 * @since 0.966.0
 */
public class BLangMatch extends BLangStatement implements MatchNode {

    public BLangExpression expr;
    public List<BLangMatchStmtSimpleBindingPatternClause> simplePatternClauses;
    public List<BLangMatchStmtTupleBindingPatternClause> tuplePatternClauses;
    public List<BType> exprTypes;

    @Override
    public NodeKind getKind() {
        return NodeKind.MATCH;
    }

    @Override
    public BLangExpression getExpression() {
        return expr;
    }

    @Override
    public List<BLangMatchStmtSimpleBindingPatternClause> getSimplePatternClauses() {
        return simplePatternClauses;
    }

    @Override
    public List<BLangMatchStmtTupleBindingPatternClause> getTuplePatternClauses() {
        return tuplePatternClauses;
    }

    @Override
    public void accept(BLangNodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        StringJoiner sj = new StringJoiner(";");
        simplePatternClauses.forEach(pattern -> sj.add(pattern.toString()));
        return String.valueOf(expr) + " match {" + String.valueOf(sj) + "}";
    }

    /**
     * {@code BLangMatchStmtSimpleBindingPatternClause} represents a pattern inside a type switch statement.
     *
     * @since 0.966.0
     */
    public static class BLangMatchStmtSimpleBindingPatternClause extends BLangNode
            implements MatchStatementSimpleBindingPatternNode {

        public BLangSimpleVariable variable;
        public BLangBlockStmt body;

        // This field is used to capture types that are matched to this pattern.
        public Set<BType> matchedTypesDirect = new HashSet<>();
        public Set<BType> matchedTypesIndirect = new HashSet<>();

        @Override
        public NodeKind getKind() {
            return NodeKind.MATCH_PATTERN_CLAUSE;
        }

        @Override
        public BLangSimpleVariable getVariableNode() {
            return variable;
        }

        @Override
        public BLangStatement getStatement() {
            return body;
        }

        @Override
        public void accept(BLangNodeVisitor visitor) {
            visitor.visit(this);
        }
        
        @Override
        public String toString() {
            return String.valueOf(variable) + " => " + String.valueOf(body);
        }
    }

    /**
     * @since 0.982.0
     */

    public static class BLangMatchStmtTupleBindingPatternClause extends BLangNode
            implements MatchStatementTupleBindingPatternNode {

        public BLangTupleVariable variable;
        public BLangBlockStmt body;

        // This field is used to capture types that are matched to this pattern.
        public Set<BType> matchedTypesDirect = new HashSet<>();
        public Set<BType> matchedTypesIndirect = new HashSet<>();

        @Override
        public NodeKind getKind() {
            return NodeKind.MATCH_PATTERN_CLAUSE;
        }

        @Override
        public TupleVariableNode getTupleVariableNode() {
            return variable;
        }

        @Override
        public BLangStatement getStatement() {
            return body;
        }

        @Override
        public void accept(BLangNodeVisitor visitor) {
            visitor.visit(this);
        }

        @Override
        public String toString() {
            return String.valueOf(variable) + " => " + String.valueOf(body);
        }
    }
}
