package org.hswebframework.web.organizational.authorization.simple;

import org.hswebframework.web.authorization.simple.AbstractDataAccessConfig;
import org.hswebframework.web.organizational.authorization.access.DataAccessType;
import org.hswebframework.web.authorization.access.ScopeDataAccessConfig;

import java.util.Set;

/**
 * TODO 完成注释
 *
 * @author zhouhao
 */
public class SimpleScopeDataAccessConfig extends AbstractDataAccessConfig implements ScopeDataAccessConfig {
    private String      scopeType;
    private Set<Object> scope;
    private String      type;

    public SimpleScopeDataAccessConfig() {
    }

    public SimpleScopeDataAccessConfig(String scopeType, String type) {
        this.scopeType = scopeType;
        this.type = type;
    }

    public SimpleScopeDataAccessConfig(String scopeType, String type, Set<Object> scope) {
        this.scopeType = scopeType;
        this.scope = scope;
        this.type = type;
    }

    @Override
    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    @Override
    public Set<Object> getScope() {
        return scope;
    }

    public void setScope(Set<Object> scope) {
        this.scope = scope;
    }

    @Override
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
