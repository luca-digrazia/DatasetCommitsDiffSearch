package org.hswebframework.web.organizational.authorization.simple.handler;

import org.hsweb.ezorm.core.param.Term;
import org.hsweb.ezorm.core.param.TermType;
import org.hswebframework.web.organizational.authorization.PersonnelAuthorization;
import org.hswebframework.web.organizational.authorization.access.DataAccessType;
import org.hswebframework.web.organizational.authorization.entity.PositionAttachEntity;

import java.util.Collections;
import java.util.Set;

import static org.hswebframework.web.organizational.authorization.access.DataAccessType.SCOPE_TYPE_CHILDREN;
import static org.hswebframework.web.organizational.authorization.access.DataAccessType.SCOPE_TYPE_ONLY_SELF;

/**
 * TODO 完成注释
 *
 * @author zhouhao
 */
public class PositionScopeDataAccessHandler extends AbstractScopeDataAccessHandler<PositionAttachEntity> {
    @Override
    protected Class<PositionAttachEntity> getEntityClass() {
        return PositionAttachEntity.class;
    }

    @Override
    protected String getSupportScope() {
        return DataAccessType.POSITION_SCOPE;
    }

    @Override
    protected Set<String> getTryOperationScope(String scopeType, PersonnelAuthorization authorization) {
        switch (scopeType) {
            case SCOPE_TYPE_CHILDREN:
                return authorization.getAllPositionId();
            case SCOPE_TYPE_ONLY_SELF:
                return authorization.getRootPositionId();
            default:
                return Collections.emptySet();
        }
    }

    @Override
    protected String getOperationScope(PositionAttachEntity entity) {
        return entity.getPositionId();
    }

    @Override
    protected Term createQueryTerm(Set<String> scope) {
        Term term = new Term();
        term.setColumn(PositionAttachEntity.positionId);
        term.setTermType(TermType.in);
        term.setValue(scope);
        term.setType(Term.Type.and);
        return term;
    }
}
