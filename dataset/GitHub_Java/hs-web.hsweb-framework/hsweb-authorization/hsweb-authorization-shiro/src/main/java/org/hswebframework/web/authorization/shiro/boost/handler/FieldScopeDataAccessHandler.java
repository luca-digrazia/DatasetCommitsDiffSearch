package org.hswebframework.web.authorization.shiro.boost.handler;

import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.beanutils.PropertyUtilsBean;
import org.hsweb.ezorm.core.param.Term;
import org.hsweb.ezorm.core.param.TermType;
import org.hswebframework.web.authorization.Permission;
import org.hswebframework.web.authorization.access.DataAccessConfig;
import org.hswebframework.web.authorization.access.DataAccessHandler;
import org.hswebframework.web.authorization.access.FieldScopeDataAccessConfig;
import org.hswebframework.web.authorization.annotation.RequiresDataAccess;
import org.hswebframework.web.boost.aop.context.MethodInterceptorParamContext;
import org.hswebframework.web.commons.entity.param.QueryParamEntity;
import org.hswebframework.web.controller.QueryController;
import org.hswebframework.web.service.QueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zhouhao
 */
public class FieldScopeDataAccessHandler implements DataAccessHandler {
    private PropertyUtilsBean propertyUtilsBean = BeanUtilsBean.getInstance().getPropertyUtils();

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public boolean isSupport(DataAccessConfig access) {
        return access instanceof FieldScopeDataAccessConfig;
    }

    @Override
    public boolean handle(DataAccessConfig access, MethodInterceptorParamContext context) {
        FieldScopeDataAccessConfig own = ((FieldScopeDataAccessConfig) access);
        Object controller = context.getTarget();
        if (controller != null) {
            switch (access.getAction()) {
                case Permission.ACTION_QUERY:
                    return doQueryAccess(own, context);
                case Permission.ACTION_GET:
                case Permission.ACTION_DELETE:
                case Permission.ACTION_UPDATE:
                    return doRWAccess(own, context, controller);
                case Permission.ACTION_ADD:
                default:
                    logger.warn("action: {} not support now!", access.getAction());
            }
        } else {
            logger.warn("target is null!");
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    protected boolean doRWAccess(FieldScopeDataAccessConfig access, MethodInterceptorParamContext context, Object controller) {
        //获取注解
        RequiresDataAccess dataAccess = context.getAnnotation(RequiresDataAccess.class);
        Object id = context.<String>getParameter(dataAccess.idParamName()).orElse(null);
        //通过QueryController获取QueryService
        //然后调用selectByPk 查询旧的数据,进行对比
        if (controller instanceof QueryController) {
            QueryService queryService = (QueryService) ((QueryController) controller).getService();
            Object oldData = queryService.selectByPk(id);
            if (oldData != null) {
                try {
                    Object value = propertyUtilsBean.getProperty(oldData, access.getField());
                    return access.getScope().contains(value);
                } catch (Exception e) {
                    logger.error("can't read property {}", access.getField(), e);
                }
                return false;
            }
        } else {
            logger.warn("controller is not instanceof QueryController");
        }
        return true;
    }


    protected boolean doQueryAccess(FieldScopeDataAccessConfig access, MethodInterceptorParamContext context) {
        QueryParamEntity entity = context.getParams()
                .values().stream()
                .filter(QueryParamEntity.class::isInstance)
                .map(QueryParamEntity.class::cast)
                .findAny().orElse(null);
        if (entity == null) {
            logger.warn("try validate query access, but query entity is null or not instance of org.hswebframework.web.commons.entity.Entity");
            return true;
        }
        //重构查询条件
        //如: 旧的条件为 where column =? or column = ?
        //重构后为: where creatorId=? and (column = ? or column = ?)
        List<Term> oldParam = entity.getTerms();
        //清空旧的查询条件
        entity.setTerms(new ArrayList<>());
        //添加一个查询条件
        entity.addTerm(createQueryTerm(access))
                //客户端提交的参数 作为嵌套参数
                .nest().setTerms(oldParam);
        return true;
    }

    protected Term createQueryTerm(FieldScopeDataAccessConfig access) {
        Term term = new Term();
        term.setType(Term.Type.and);
        term.setColumn(access.getField());
        term.setTermType(TermType.in);
        term.setValue(access.getScope());
        return term;
    }
}
