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
package org.durid.sql.ast.expr;

import org.durid.sql.ast.SQLExprImpl;
import org.durid.sql.ast.SQLName;
import org.durid.sql.visitor.SQLASTVisitor;

public class SQLIdentifierExpr extends SQLExprImpl implements SQLName {

    private static final long serialVersionUID = -4101240977289682659L;

    private String            name;
    private boolean           wrappedInParens;

    public SQLIdentifierExpr(){

    }

    public SQLIdentifierExpr(String name){

        this.name = name;
    }
    
    public String getSimleName() {
        return name;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isWrappedInParens() {
        return this.wrappedInParens;
    }

    public void setWrappedInParens(boolean wrappedInParens) {
        this.wrappedInParens = wrappedInParens;
    }

    public void output(StringBuffer buf) {
        buf.append(this.name);
    }

    protected void accept0(SQLASTVisitor visitor) {
        visitor.visit(this);

        visitor.endVisit(this);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof SQLIdentifierExpr)) {
            return false;
        }
        SQLIdentifierExpr other = (SQLIdentifierExpr) obj;
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        return true;
    }

}
