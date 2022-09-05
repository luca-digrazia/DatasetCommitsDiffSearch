package org.nlpcn.es4sql;

import java.util.List;
import java.util.Map;

import org.nlpcn.es4sql.domain.KVValue;
import org.nlpcn.es4sql.exception.SqlParseException;

import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLNumericLiteralExpr;

public class Util {
	public static String joiner(List<KVValue> lists, String oper) {
		if (lists.size() == 0) {
			return null;
		}

		StringBuilder sb = new StringBuilder(lists.get(0).toString());
		for (int i = 1; i < lists.size(); i++) {
			sb.append(oper);
			sb.append(lists.get(i).toString());
		}

		return sb.toString();
	}

	public static List<Map<String, Object>> sortByMap(List<Map<String, Object>> lists) {

		return lists;
	}

	public static Object expr2Object(SQLExpr expr) throws SqlParseException {
		Object value = null;
		if (expr instanceof SQLNumericLiteralExpr) {
			value = ((SQLNumericLiteralExpr) expr).getNumber();
		} else if (expr instanceof SQLCharExpr) {
			value = ((SQLCharExpr) expr).getText();
		} else if (expr instanceof SQLIdentifierExpr) {
			value = expr.toString();
		} else {
			throw new SqlParseException("can not support this type " + expr.getClass());
		}
		return value;
	}

}
