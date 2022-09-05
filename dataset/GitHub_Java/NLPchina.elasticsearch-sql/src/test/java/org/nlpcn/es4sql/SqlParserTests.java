package org.nlpcn.es4sql;

import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.expr.SQLQueryExpr;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.search.SearchHit;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.nlpcn.es4sql.domain.*;
import org.nlpcn.es4sql.exception.SqlParseException;
import org.nlpcn.es4sql.parse.ElasticSqlExprParser;
import org.nlpcn.es4sql.parse.SqlParser;
import org.nlpcn.es4sql.query.ESHashJoinQueryAction;

import java.io.IOException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.Map;

import static org.nlpcn.es4sql.TestsConstants.TEST_INDEX;

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

        List<Field> t1Fields = joinSelect.getFirstTable().getSelectedFields();
        Assert.assertEquals(t1Fields.size(),3);
        Assert.assertTrue(fieldExist(t1Fields, "firstname"));
        Assert.assertTrue(fieldExist(t1Fields, "lastname"));
        Assert.assertTrue(fieldExist(t1Fields, "gender"));

        List<Field> t2Fields = joinSelect.getSecondTable().getSelectedFields();
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

        List<Field> t1Fields = joinSelect.getFirstTable().getConnectedFields();
        Assert.assertEquals(t1Fields.size(),2);
        Assert.assertTrue(fieldExist(t1Fields, "firstname"));
        Assert.assertTrue(fieldExist(t1Fields, "age"));

        List<Field> t2Fields = joinSelect.getSecondTable().getConnectedFields();
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
        List<From> t1From = joinSelect.getFirstTable().getFrom();

        Assert.assertNotNull(t1From);
        Assert.assertEquals(1,t1From.size());
        Assert.assertTrue(checkFrom(t1From.get(0),"elasticsearch-sql_test_index","account","a"));

        List<From> t2From = joinSelect.getSecondTable().getFrom();
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
        String s1Where = joinSelect.getFirstTable().getWhere().toString();
        Assert.assertEquals("AND ( AND firstname EQ eliran, AND ( OR age GT 10, OR balance GT 2000 )  ) " , s1Where);
        String s2Where = joinSelect.getSecondTable().getWhere().toString();
        Assert.assertEquals("AND age GT 1",s2Where);
    }

    @Test
    public void joinConditionWithComplexObjectComparisonRightSide() throws SQLFeatureNotSupportedException, IOException, SqlParseException {
        String query = String.format("select c.name.firstname,c.parents.father , h.name,h.words from %s/gotCharacters c " +
                "JOIN %s/gotHouses h " +
                "on h.name = c.name.lastname  " +
                "where c.name.firstname='Daenerys'", TEST_INDEX,TEST_INDEX);
        JoinSelect joinSelect = parser.parseJoinSelect((SQLQueryExpr) queryToExpr(query));
        List<Condition> conditions = joinSelect.getConnectedConditions();
        Assert.assertNotNull(conditions);
        Assert.assertEquals(1,conditions.size());
        Assert.assertTrue("condition not exist: h.name = c.name.lastname",conditionExist(conditions, "h.name", "c.name.lastname", Condition.OPEAR.EQ));
    }

    @Test
    public void joinConditionWithComplexObjectComparisonLeftSide() throws SQLFeatureNotSupportedException, IOException, SqlParseException {
        String query = String.format("select c.name.firstname,c.parents.father , h.name,h.words from %s/gotCharacters c " +
                "JOIN %s/gotHouses h " +
                "on c.name.lastname = h.name  " +
                "where c.name.firstname='Daenerys'", TEST_INDEX,TEST_INDEX);
        JoinSelect joinSelect = parser.parseJoinSelect((SQLQueryExpr) queryToExpr(query));
        List<Condition> conditions = joinSelect.getConnectedConditions();
        Assert.assertNotNull(conditions);
        Assert.assertEquals(1,conditions.size());
        Assert.assertTrue("condition not exist: c.name.lastname = h.name",conditionExist(conditions, "c.name.lastname", "h.name", Condition.OPEAR.EQ));
    }

    private SQLExpr queryToExpr(String query) {
        return new ElasticSqlExprParser(query).expr();
    }

    private boolean conditionExist(List<Condition> conditions, String from, String to, Condition.OPEAR opear) {
        String[] aliasAndField = to.split("\\.",2);
        String toAlias = aliasAndField[0];
        String toField = aliasAndField[1];
        for (Condition condition : conditions){
            if(condition.getOpear() !=  opear) continue;

            boolean fromIsEqual = condition.getName().equals(from);
            if(!fromIsEqual) continue;

            SQLPropertyExpr value = (SQLPropertyExpr) condition.getValue();
            String[] valueAliasAndField = value.toString().split("\\.",2);
            boolean toFieldNameIsEqual = valueAliasAndField[1].equals(toField);
            boolean toAliasIsEqual =  valueAliasAndField[0].equals(toAlias);
            boolean toIsEqual = toAliasIsEqual && toFieldNameIsEqual;

            if(toIsEqual) return true;
        }
        return false;
    }


}
