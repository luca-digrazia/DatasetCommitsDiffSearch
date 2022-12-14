package org.hswebframework.web.commons.entity.param;

import org.hswebframework.ezorm.core.NestConditional;
import org.hswebframework.ezorm.core.dsl.Query;
import org.hswebframework.ezorm.core.param.Term;
import org.hswebframework.ezorm.core.param.TermType;

import java.util.Arrays;
import java.util.List;

/**
 * 动态条件表达式解析器
 * name=测试 and age=test
 */
public class TermExpressionParser {

    public static List<Term> parse(String expression) {
        Query<?, QueryParamEntity> conditional = QueryParamEntity.newQuery();

        NestConditional<?> nest = null;

        char[] buf = new char[128];
        byte current = 0;
        byte spaceLen = 0;

        char[] currentColumn = null;

        String currentTermType = null;
        char[] currentValue = null;
        char[] all = expression.toCharArray();
        String currentType = "and";
        for (char c : all) {

            if (c == '(') {
                nest = (nest == null ?
                        (currentType.equals("or") ? conditional.orNest() : conditional.nest()) :
                        (currentType.equals("or") ? nest.orNest() : nest.nest()));
                current = 0;

                continue;
            } else if (c == ')') {
                if (nest == null) {
                    continue;
                }
                if (null != currentColumn) {
                    currentValue = Arrays.copyOf(buf, current);
                    nest.accept(new String(currentColumn), convertTermType(currentTermType), new String(currentValue));
                    currentColumn = null;
                    currentTermType = null;
                }
                Object end = nest.end();
                nest = end instanceof NestConditional ? ((NestConditional) end) : null;
                current = 0;
                spaceLen++;
                continue;
            } else if (c == '=' || c == '>' || c == '<') {
                if (currentTermType != null) {
                    currentTermType += String.valueOf(c);
                } else {
                    currentTermType = String.valueOf(c);
                }

                if (currentColumn == null) {
                    currentColumn = Arrays.copyOf(buf, current);
                }
                spaceLen++;
                current = 0;
                continue;
            } else if (c == ' ') {
                if (current == 0) {
                    continue;
                }
                spaceLen++;
                if (currentColumn == null && (spaceLen == 1 || spaceLen % 5 == 0)) {
                    currentColumn = Arrays.copyOf(buf, current);
                    current = 0;
                    continue;
                }
                if (null != currentColumn) {
                    if (null == currentTermType) {
                        currentTermType = new String(Arrays.copyOf(buf, current));
                        current = 0;
                        continue;
                    }
                    currentValue = Arrays.copyOf(buf, current);
                    if (nest != null) {
                        nest.accept(new String(currentColumn), convertTermType(currentTermType), new String(currentValue));
                    } else {
                        conditional.accept(new String(currentColumn), convertTermType(currentTermType), new String(currentValue));
                    }
                    currentColumn = null;
                    currentTermType = null;
                    current = 0;
                    continue;
                } else if (current == 2 || current == 3) {
                    String type = new String(Arrays.copyOf(buf, current));
                    if (type.equalsIgnoreCase("or")) {
                        currentType = "or";
                        if (nest != null) {
                            nest.or();
                        } else {
                            conditional.or();
                        }
                        current = 0;
                        continue;
                    } else if (type.equalsIgnoreCase("and")) {
                        currentType = "and";
                        if (nest != null) {
                            nest.and();
                        } else {
                            conditional.and();
                        }
                        current = 0;
                        continue;
                    } else {
                        currentColumn = Arrays.copyOf(buf, current);
                        current = 0;
                        spaceLen++;
                    }
                }
                continue;
            }

            buf[current++] = c;
        }
        if (null != currentColumn) {
            currentValue = Arrays.copyOf(buf, current);
            if (nest != null) {
                nest.accept(new String(currentColumn), convertTermType(currentTermType), new String(currentValue));
            } else {
                conditional.accept(new String(currentColumn), convertTermType(currentTermType), new String(currentValue));
            }
        }
        return conditional.getParam().getTerms();
    }

    private static String convertTermType(String termType) {
        if (termType == null) {
            return TermType.eq;
        }
        switch (termType) {
            case "=":
                return TermType.eq;
            case ">":
                return TermType.gt;
            case "<":
                return TermType.lt;
            case ">=":
                return TermType.gte;
            case "<=":
                return TermType.lte;
            default:
                return termType;
        }

    }
}
