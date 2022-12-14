package org.hswebframework.web.service.organizational.simple.terms;

import org.hswebframework.ezorm.core.param.Term;
import org.hswebframework.ezorm.rdb.meta.RDBColumnMetaData;
import org.hswebframework.ezorm.rdb.render.SqlAppender;
import org.hswebframework.ezorm.rdb.render.dialect.term.BoostTermTypeMapper;
import org.hswebframework.web.dao.mybatis.mapper.ChangedTermValue;
import org.hswebframework.web.service.organizational.PositionService;

import java.util.List;


/**
 * 查询岗位中的用户
 *
 * @author zhouhao
 * @since 3.0.0-RC
 */
public class UserInPositionSqlTerm extends UserInSqlTerm {

    private boolean not;

    public UserInPositionSqlTerm(boolean not, boolean child, String term, PositionService positionService) {
        super(term, positionService);
        setChild(child);
        this.not = not;
    }

    @Override
    public String getTableName() {
        return "_pos";
    }

    @Override
    public SqlAppender accept(String wherePrefix, Term term, RDBColumnMetaData column, String tableAlias) {
        ChangedTermValue termValue = createChangedTermValue(term);

        SqlAppender appender = new SqlAppender();
        appender.addSpc(not ? "not" : "", "exists(select 1 from s_person_position tmp_");
        if (isChild()) {
            appender.addSpc(",s_position _pos");
        }
        if (!isForPerson()) {
            appender.addSpc(",s_person _person");
        }

        appender.addSpc("where ",
                createColumnName(column, tableAlias), "=",
                isForPerson() ? " _tmp.person_id" : "_person.user_id and _person.u_id=tmp_.person_id");

        if (isChild()) {
            appender.addSpc("and _pos.u_id=tmp_.position_id");
        }

        List<Object> positionIdList = BoostTermTypeMapper.convertList(column, termValue.getOld());
        if (!positionIdList.isEmpty()) {
            appender.addSpc("and");
            termValue.setValue(appendCondition(positionIdList, wherePrefix, appender, "tmp_.position_id"));
        }

        appender.add(")");

        return appender;
    }
}
