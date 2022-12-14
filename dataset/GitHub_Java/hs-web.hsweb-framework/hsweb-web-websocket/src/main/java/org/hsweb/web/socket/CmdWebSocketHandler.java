package org.hsweb.web.socket;

import com.alibaba.fastjson.JSON;
import org.hsweb.web.bean.po.user.User;
import org.hsweb.web.socket.cmd.CMD;
import org.hsweb.web.socket.cmd.CmdProcessor;
import org.hsweb.web.socket.cmd.CmdProcessorContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 基于命令的socket处理器
 * Created by 浩 on 2015-09-08 0008.
 */
@Component
public class CmdWebSocketHandler extends TextWebSocketHandler {

    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private CmdProcessorContainer processorContainer;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (logger.isInfoEnabled())
            logger.info("handleMessage,id:{} msg={}", session.getId(), message.getPayload());
        try {
            CMD request = JSON.parseObject(message.getPayload(), CMD.class);
            CmdProcessor processor = processorContainer.getCmdProcessor(request.getCmd());
            if (null != processor) {
                request.setSession(session);
                processor.exec(request);
            }
        } catch (Exception e) {
            logger.error("handleTextMessage error!", e);
        }
    }

    private User getUser(WebSocketSession session) {
        return (User) session.getAttributes().get("user");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        User user = getUser(session);
//        if (user == null) {
//            session.close(CloseStatus.BAD_DATA.withReason("请登陆!"));
//            return;
//        }
        for (CmdProcessor processor : processorContainer.getAll()) {
            processor.onSessionClose(session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        for (CmdProcessor processor : processorContainer.getAll()) {
            processor.onSessionClose(session);
        }
    }

}
