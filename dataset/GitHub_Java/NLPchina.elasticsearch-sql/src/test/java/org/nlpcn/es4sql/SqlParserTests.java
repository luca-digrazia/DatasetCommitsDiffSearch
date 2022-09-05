package org.nlpcn.es4sql;

import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.expr.SQLQueryExpr;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.nlpcn.es4sql.domain.*;
import org.nlpcn.es4sql.exception.SqlParseException;
import org.nlpcn.es4sql.parse.ElasticSqlExprParser;
import org.nlpcn.es4sql.parse.SqlParser;

import java.util.List;

/**
 * Created by Eliran on 21/8/2015.
 */
public class SqlParserTests {
    private static SqlParser parser;

    @BeforeClass
    public static void init(){
        parser = new SqlParser();
    }


    @Test
    public void joinParseCheckSelectedFieldsSplit() throws SqlParseException {
        String query = "SELECT a.firstname ,a.lastname , a.gender ,  d.holdersName ,d.name  FROM elasticsearch-sql_test_index/account a " +
                "LEFT JOIN elasticsearch-sql_test_index/dog d on d.holdersName = a.firstname " +
                " AND d.age < a.age " +
                " WHERE a.firstname = 'eliran' AND " +
                " (a.age > 10 OR a.balance > 2000)"  +
                " AND d.age > 1";

        JoinSelect joinSelect = parser.parseJoinSelect((SQLQueryExpr) queryToExpr(query));

        List<Field> t1Fields = joinSelect.getT1SelectedFields();
        Assert.assertEquals(t1Fields.size(),3);
        Assert.assertTrue(fieldExist(t1Fields, "firstname"));
        Assert.assertTrue(fieldExist(t1Fields, "lastname"));
        Assert.assertTrue(fieldExist(t1Fields, "gender"));

        List<Field> t2Fields = joinSelect.getT2SelectedFields();
        Assert.assertEquals(t2Fields.size(),2);
        Assert.assertTrue(fieldExist(t2Fields,"holdersName"));
        Assert.assertTrue(fieldExist(t2Fields,"name"));
    }

    @Test
    public void joinParseCheckConnectedFields() throws SqlParseException {
        String query = "SELECT a.firstname ,a.lastname , a.gender ,  d.holdersName ,d.name  FROM elasticsearch-sql_test_index/account a " +
                "LEFT JOIN elasticsearch-sql_test_index/dog d on d.holdersName = a.firstname " +
                " AND d.age < a.age " +
                " WHERE a.firstname = 'eliran' AND " +
                " (a.age > 10 OR a.balance > 2000)"  +
                " AND d.age > 1";

        JoinSelect joinSelect = parser.parseJoinSelect((SQLQueryExpr) queryToExpr(query));

        List<Field> t1Fields = joinSelect.getT1ConnectedFields();
        Assert.assertEquals(t1Fields.size(),2);
        Assert.assertTrue(fieldExist(t1Fields, "firstname"));
        Assert.assertTrue(fieldExist(t1Fields, "age"));

        List<Field> t2Fields = joinSelect.getT2ConnectedFields();
        Assert.assertEquals(t2Fields.size(),2);
        Assert.assertTrue(fieldExist(t2Fields,"holdersName"));
        Assert.assertTrue(fieldExist(t2Fields,"age"));
    }

    private boolean fieldExist(List<Field> fields, String fieldName) {
        for(Field field : fields)
            if(field.getName().equals(fieldName)) return true;

        return false;
    }


    @Test
    public void joinParseFromsAreSplitedCorrectly() throws SqlParseException {
        String query = "SELECT a.firstname ,a.lastname , a.gender ,  d.holdersName ,d.name  FROM elasticsearch-sql_test_index/account a " +
                "LEFT JOIN elasticsearch-sql_test_index/dog d on d.holdersName = a.firstname" +
                " WHERE a.firstname = 'eliran' AND " +
                " (a.age > 10 OR a.balance > 2000)"  +
                " AND d.age > 1";

        JoinSelect joinSelect = parser.parseJoinSelect((SQLQueryExpr) queryToExpr(query));
        List<From> t1From = joinSelect.getT1Select().getFrom();

        Assert.assertNotNull(t1From);
        Assert.assertEquals(1,t1From.size());
        Assert.assertTrue(checkFrom(t1From.get(0),"elasticsearch-sql_test_index","account","a"));

        List<From> t2From = joinSelect.getT2Select().getFrom();
        Assert.assertNotNull(t2From);
        Assert.assertEquals(1,t2From.size());
        Assert.assertTrue(checkFrom(t2From.get(0),"elasticsearch-sql_test_index","dog","d"));
    }

    private boolean checkFrom(From from, String index, String type, String alias) {
        return from.getAlias().equals(alias) && from.getIndex().equals(index)
                && from.getType().equals(type);
    }

    @Test
    public void joinParseConditionsTestOneCondition() throws SqlParseException {
        String query = "SELECT a.*, a.firstname ,a.lastname , a.gender ,  d.holdersName ,d.name  FROM elasticsearch-sql_test_index/account a " +
                "LEFT JOIN elasticsearch-sql_test_index/dog d on d.holdersName = a.firstname" +
                " WHERE a.firstname = 'eliran' AND " +
                " (a.age > 10 OR a.balance > 2000)"  +
                " AND d.age > 1";

        JoinSelect joinSelect = parser.parseJoinSelect((SQLQueryExpr) queryToExpr(query));
        List<Condition> conditions = joinSelect.getConnectedConditions();
        Assert.assertNotNull(conditions);
        Assert.assertEquals(1,conditions.size());
        Assert.assertTrue("condition not exist: d.holdersName = a.firstname",conditionExist(conditions, "d.holdersName", "a.firstname", Condition.OPEAR.EQ));
    }

    @Test
    public void joinParseConditionsTestTwoConditions() throws SqlParseException {
        String query = "SELECT a.*, a.firstname ,a.lastname , a.gender ,  d.holdersName ,d.name  FROM elasticsearch-sql_test_index/account a " +
                "LEFT JOIN elasticsearch-sql_test_index/dog d on d.holdersName = a.firstname " +
                " AND d.age < a.age " +
                " WHERE a.firstname = 'eliran' AND " +
                " (a.age > 10 OR a.balance > 2000)"  +
                " AND d.age > 1";

        JoinSelect joinSelect = parser.parseJoinSelect((SQLQueryExpr) queryToExpr(query));
        List<Condition> conditions = joinSelect.getConnectedConditions();
        Assert.assertNotNull(conditions);
        Assert.assertEquals(2,conditions.size());
        Assert.assertTrue("condition not exist: d.holdersName = a.firstname",conditionExist(conditions, "d.holdersName", "a.firstname",Condition.OPEAR.EQ));
        Assert.assertTrue("condition not exist: d.age < a.age",conditionExist(conditions, "d.age", "a.age", Condition.OPEAR.LT));
    }


    @Test
    public void joinSplitWhereCorrectly() throws SqlParseException {
        String query = "SELECT a.*, a.firstname ,a.lastname , a.gender ,  d.holdersName ,d.name  FROM elasticsearch-sql_test_index/account a " +
                "LEFT JOIN elasticsearch-sql_test_index/dog d on d.holdersName = a.firstname" +
                " WHERE a.firstname = 'eliran' AND " +
                " (a.age > 10 OR a.balance > 2000)"  +
                " AND d.age > 1";

        JoinSelect joinSelect = parser.parseJoinSelect((SQLQueryExpr) queryToExpr(query));
        String s1Where = joinSelect.getT1Select().getWhere().toString();
        Assert.assertEquals("AND ( AND firstname EQ eliran, AND ( OR age GT 10, OR balance GT 2000 )  ) " , s1Where);
        String s2Where = joinSelect.getT2Select().getWhere().toString();
        Assert.assertEquals("AND age GT 1",s2Where);
    }

    private SQLExpr queryToExpr(String query) {
        return new ElasticSqlExprParser(query).expr();
    }

    private boolean conditionExist(List<Condition> conditions, String from, String to, Condition.OPEAR opear) {
        String[] aliasAndField = to.split("\\.");
        String toAlias = aliasAndField[0];
        String toField = aliasAndField[1];
        for (Condition condition : conditions){
            if(condition.getOpear() !=  opear) continue;

            boolean fromIsEqual = condition.getName().equals(from);
            if(!fromIsEqual) continue;

            SQLPropertyExpr value = (SQLPropertyExpr) condition.getValue();
            boolean toFieldNameIsEqual =value.getName().equals(toField);
            boolean toAliasIsEqual = ((SQLIdentifierExpr) value.getOwner()).getName().equals(toAlias);
            boolean toIsEqual = toAliasIsEqual && toFieldNameIsEqual;

            if(toIsEqual) return true;
        }
        return false;
    }


}
