package org.nlpcn.es4sql.parse;

import java.util.LinkedList;
import java.util.List;

import org.nlpcn.es4sql.Util;
import org.nlpcn.es4sql.domain.Field;
import org.nlpcn.es4sql.domain.KVValue;
import org.nlpcn.es4sql.domain.MethodField;
import org.nlpcn.es4sql.exception.SqlParseException;

import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLAggregateExpr;
import com.alibaba.druid.sql.ast.expr.SQLAggregateExpr.Option;
import com.alibaba.druid.sql.ast.expr.SQLAllColumnExpr;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLMethodInvokeExpr;
import com.alibaba.druid.sql.ast.expr.SQLQueryExpr;

/**
 * 一些具有参数的一般在 select 函数.或者group by 函数
 * 
 * @author ansj
 *
 */
public class FieldMaker {
	public static Field makeField(SQLExpr expr, String alias) throws SqlParseException {
		if (expr instanceof SQLIdentifierExpr) {
			return new Field(expr.toString(), alias);
		} else if (expr instanceof SQLQueryExpr) {
			throw new SqlParseException("unknow field name : " + expr);
		} else if (expr instanceof SQLAllColumnExpr) {
		} else if (expr instanceof SQLMethodInvokeExpr) {
			SQLMethodInvokeExpr mExpr = (SQLMethodInvokeExpr) expr;
			return makeMethodField(mExpr.getMethodName(), mExpr.getParameters(), null, alias);
		} else if (expr instanceof SQLAggregateExpr) {
			SQLAggregateExpr sExpr = (SQLAggregateExpr) expr;
			return makeMethodField(sExpr.getMethodName(), sExpr.getArguments(), sExpr.getOption(), alias);
		} else {
			throw new SqlParseException("unknow field name : " + expr);
		}
		return null;
	}

	private static MethodField makeMethodField(String name, List<SQLExpr> arguments, Option option, String alias) throws SqlParseException {
		List<KVValue> paramers = new LinkedList<>();
		for (SQLExpr object : arguments) {
			if (object instanceof SQLBinaryOpExpr) {
				SQLExpr right = ((SQLBinaryOpExpr) object).getRight();
				Object value = Util.expr2Object(right);
				paramers.add(new KVValue(((SQLBinaryOpExpr) object).getLeft().toString(), value));
			} else {
				paramers.add(new KVValue(Util.expr2Object(object)));
			}

		}
		return new MethodField(name, paramers, option == null ? null : option.name(), alias);
	}
}
