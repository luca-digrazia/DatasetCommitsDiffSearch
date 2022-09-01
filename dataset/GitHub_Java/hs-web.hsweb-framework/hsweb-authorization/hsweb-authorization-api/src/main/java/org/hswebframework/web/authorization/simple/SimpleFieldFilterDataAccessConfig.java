package org.hswebframework.web.authorization.simple;

import org.hswebframework.web.authorization.access.FieldFilterDataAccessConfig;

import java.util.Set;

/**
 * TODO 完成注释
 *
 * @author zhouhao
 */
public class SimpleFieldFilterDataAccessConfig extends AbstractDataAccessConfig implements FieldFilterDataAccessConfig {
    private Set<String> fields;

    private String type;

    @Override
    public Set<String> getFields() {
        return fields;
    }

    public void setFields(Set<String> fields) {
        this.fields = fields;
    }

    @Override
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
