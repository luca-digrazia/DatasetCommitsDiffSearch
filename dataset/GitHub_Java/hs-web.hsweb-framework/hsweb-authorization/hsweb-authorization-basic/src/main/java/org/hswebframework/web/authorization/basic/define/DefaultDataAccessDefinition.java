package org.hswebframework.web.authorization.basic.define;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hswebframework.web.authorization.define.DataAccessDefinition;
import org.hswebframework.web.authorization.define.Phased;

/**
 * @author zhouhao
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DefaultDataAccessDefinition implements DataAccessDefinition {

    private static final long serialVersionUID = 8285566729547666068L;

    private String controller;

    private String idParameterName="id";

    private Phased phased;
}
