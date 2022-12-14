package org.hswebframework.web.exception;


import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.i18n.LocaleUtils;

import java.util.Locale;

/**
 * 支持国际化消息的异常,code为
 *
 * @author zhouhao
 * @see LocaleUtils#resolveMessage(String, Object...)
 * @since 4.0.11
 */
@Getter
@Setter(AccessLevel.PROTECTED)
public class I18nSupportException extends RuntimeException {

    /**
     * 消息code,在message.properties文件中定义的key
     */
    private String code;

    /**
     * 消息参数
     */
    private Object[] args;

    protected I18nSupportException() {

    }

    public I18nSupportException(String code, Object... args) {
        super(code);
        this.code = code;
        this.args = args;
    }

    public I18nSupportException(String code, Throwable cause, Object... args) {
        super(code, cause);
        this.args = args;
        this.code = code;
    }

    @Override
    public String getMessage() {
        return super.getMessage() != null ? super.getMessage() : getLocalizedMessage();
    }

    @Override
    public String getLocalizedMessage() {
        return LocaleUtils.resolveMessage(code, args);
    }
}
