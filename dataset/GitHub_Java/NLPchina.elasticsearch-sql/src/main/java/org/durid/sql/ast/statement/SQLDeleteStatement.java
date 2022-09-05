/*
 * Copyright 1999-2011 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.durid.sql.ast.statement;

import org.durid.sql.ast.SQLExpr;
import org.durid.sql.ast.SQLName;
import org.durid.sql.ast.SQLStatementImpl;
import org.durid.sql.ast.expr.SQLIdentifierExpr;
import org.durid.sql.visitor.SQLASTVisitor;

public class SQLDeleteStatement extends SQLStatementImpl {

    private static final long serialVersionUID = 1L;
    protected SQLTableSource  tableSource;
    protected SQLExpr         where;

    public SQLDeleteStatement(){

    }

    public SQLTableSource getTableSource() {
        return tableSource;
    }

    public SQLExprTableSource getExprTableSource() {
        return (SQLExprTableSource) getTableSource();
    }

    public void setTableSource(SQLExpr expr) {
        this.setTableSource(new SQLExprTableSource(expr));
    }

    public void setTableSource(SQLTableSource tableSource) {
        this.tableSource = tableSource;
    }

    public SQLName getTableName() {
        return (SQLName) getExprTableSource().getExpr();
    }


    public SQLExpr getWhere() {
        return where;
    }

    public void setWhere(SQLExpr where) {
        this.where = where;
    }

    public String getAlias() {
        return this.tableSource.getAlias();
    }

    public void setAlias(String alias) {
        this.tableSource.setAlias(alias);
    }

    @Override
    protected void accept0(SQLASTVisitor visitor) {
        if (visitor.visit(this)) {
            acceptChild(visitor, tableSource);
            acceptChild(visitor, where);
        }

        visitor.endVisit(this);
    }

}
