package org.hsweb.web.socket.cmd.support;

import org.hsweb.web.bean.po.user.User;
import org.hsweb.web.core.session.HttpSessionManager;
import org.hsweb.web.socket.cmd.CmdProcessor;
import org.hsweb.web.socket.utils.SessionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.WebSocketSession;

/**
 * Created by zhouhao on 16-5-30.
 */
public abstract class AbstractCmdProcessor implements CmdProcessor {
    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    private HttpSessionManager httpSessionManager;

    @Autowired
    public void setHttpSessionManager(HttpSessionManager httpSessionManager) {
        this.httpSessionManager = httpSessionManager;
    }

    public User getUser(WebSocketSession socketSession) {
        return SessionUtils.getUser(socketSession, httpSessionManager);
    }
}
