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
import org.durid.sql.visitor.SQLASTVisitor;

public class SQLExprTableSource extends SQLTableSourceImpl {

    private static final long serialVersionUID = 1L;

    protected SQLExpr         expr;
	private String tablename;

	public SQLExprTableSource(){

    }

    public SQLExprTableSource(SQLExpr expr){
        this.expr = expr;
		this.tablename = expr.toString().replace(" ", "");
    }

	public SQLExprTableSource(String tablename){
		this.tablename = tablename;
	}

    public SQLExpr getExpr() {
        return this.expr;
    }

    public void setExpr(SQLExpr expr) {
        this.expr = expr;
		this.tablename = expr.toString().replace(" ", "");
    }

    @Override
    protected void accept0(SQLASTVisitor visitor) {
        if (visitor.visit(this)) {
            acceptChild(visitor, this.expr);
        }
        visitor.endVisit(this);
    }

    public void output(StringBuffer buf) {
        this.expr.output(buf);
    }

	@Override
	public String getTablename() {
		return this.tablename;
	}
}
