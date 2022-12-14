package org.hsweb.web.message;

import com.alibaba.fastjson.JSON;
import org.hsweb.web.exception.AuthorizeException;
import org.hsweb.web.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webbuilder.utils.common.DateTimeUtils;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 响应消息。controller中处理后，返回此对象，响应请求结果给客户端。
 */
public class ResponseMessage implements Serializable {
    private static final long serialVersionUID = 8992436576262574064L;

    /**
     * message处理类，可以自定义message处理方案
     */
    private transient static final Map<Class, MessageHandler> handlers = new HashMap<>();

    /**
     * 注册一个消息处理器
     *
     * @param dataType 消息类型
     * @param handler  处理器实例
     * @return 已注册的消息处理器
     */
    public static final <T> MessageHandler<T> registerMessageHandler(Class<T> dataType, MessageHandler<T> handler) {
        return handlers.put(dataType, handler);
    }

    /**
     * 注销一个消息处理器
     *
     * @param dataType 消息类型
     * @return 已注册的消息处理器
     */
    public static final <T> MessageHandler<T> cancelMessageHandler(Class<T> dataType) {
        return handlers.remove(dataType);
    }

    /**
     * 注册默认的消息处理
     */
    static {
        registerMessageHandler(Object.class, new MessageHandler<Object>() {
            @Override
            public Object handle(ResponseMessage message, Object msg) {
                return msg;
            }
        });
        //默认异常信息处理
        registerMessageHandler(Throwable.class, new MessageHandler<Throwable>() {
            @Override
            public Object handle(ResponseMessage message, Throwable e) {
                LOGGER.error(e.getMessage(), e);
                return e.getMessage();
            }
        });
        //默认业务异常信息处理
        registerMessageHandler(BusinessException.class, new MessageHandler<BusinessException>() {
            @Override
            public Object handle(ResponseMessage message, BusinessException e) {
                LOGGER.error(e.getMessage());
                return e.getMessage();
            }
        });
        //权限验证异常
        registerMessageHandler(AuthorizeException.class, new MessageHandler<AuthorizeException>() {
            @Override
            public Object handle(ResponseMessage message, AuthorizeException e) {
                message.setCode("502");
                return e.getMessage();
            }
        });
    }

    private static final <T> MessageHandler<T> getMessageHandler(Class<T> type) {
        return handlers.get(type);
    }

    private transient static final Logger LOGGER = LoggerFactory.getLogger(ResponseMessage.class);

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 反馈数据
     */
    private Object data;

    /**
     * 响应码
     */
    private String code;

    /**
     * 进行响应的元数据,不会被序列化,只是提供aop和拦截器访问
     */
    private transient Object sourceData;

    /**
     * 过滤字段：指定需要序列化的字段
     */
    private transient Map<Class<?>, Set<String>> includes;

    /**
     * 过滤字段：指定不需要序列化的字段
     */
    private transient Map<Class<?>, Set<String>> excludes;

    private transient boolean onlyData;

    public ResponseMessage(boolean success, Object data) {
        this.code = success ? "200" : "500";
        if (data == null)
            data = "null";
        sourceData = data;
        //获取消息处理器
        MessageHandler messageHandler = getMessageHandler(data.getClass());
        if (messageHandler == null) {
            if (data instanceof Throwable) {
                //未获取到指定的异常信息处理器，使用通用异常处理器
                messageHandler = getMessageHandler(Throwable.class);
            } else {
                messageHandler = getMessageHandler(Object.class);
            }
        }
        this.success = success;
        if (messageHandler == null)
            this.data = data;
        else
            this.data = messageHandler.handle(this, data);

    }

    public ResponseMessage(boolean success, Object data, String code) {
        this(success, data);
        this.code = code;
    }


    public ResponseMessage include(Class<?> type, String... fileds) {
        if (includes == null)
            includes = new HashMap<>();
        getStringListFormMap(includes, type).addAll(Arrays.asList(fileds));
        return this;
    }

    public ResponseMessage exclude(Class type, String... fileds) {
        if (excludes == null)
            excludes = new HashMap<>();
        getStringListFormMap(excludes, type).addAll(Arrays.asList(fileds));
        return this;
    }

    protected Set<String> getStringListFormMap(Map<Class<?>, Set<String>> map, Class type) {
        Set<String> list = map.get(type);
        if (list == null) {
            list = new HashSet<>();
            map.put(type, list);
        }
        return list;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Object getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return JSON.toJSONStringWithDateFormat(this, DateTimeUtils.YEAR_MONTH_DAY_HOUR_MINUTE_SECOND);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public interface MessageHandler<T> {
        Object handle(ResponseMessage message, T msg);
    }

    public Object getSourceData() {
        return sourceData;
    }

    public static ResponseMessage fromJson(String json) {
        return JSON.parseObject(json, ResponseMessage.class);
    }

    public Map<Class<?>, Set<String>> getExcludes() {
        return excludes;
    }

    public Map<Class<?>, Set<String>> getIncludes() {
        return includes;
    }

    public ResponseMessage onlyData() {
        setOnlyData(true);
        return this;
    }

    public void setOnlyData(boolean onlyData) {
        this.onlyData = onlyData;
    }

    public boolean isOnlyData() {
        return onlyData;
    }
}