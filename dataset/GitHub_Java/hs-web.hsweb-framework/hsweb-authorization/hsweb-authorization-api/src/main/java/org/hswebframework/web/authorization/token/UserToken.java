package org.hswebframework.web.authorization.token;


import org.hswebframework.web.authorization.User;

import java.io.Serializable;

/**
 * 用户的token信息
 * @author zhouhao
 * @since 3.0
 */
public interface UserToken extends Serializable, Comparable<UserToken> {
    /**
     *
     * @return 用户id
     * @see  User#getId()
     */
    String getUserId();

    /**
     *
     * @return token
     */
    String getToken();

    /**
     *
     * @return 请求总次数
     */
    long getRequestTimes();

    /**
     *
     * @return 最后一次请求时间
     */
    long getLastRequestTime();

    /**
     *
     * @return 首次请求时间
     */
    long getSignInTime();

    TokenState getState();

    default boolean isEffective(){
        return getState()==TokenState.effective;
    }

    default boolean isExpired(){
        return getState()==TokenState.expired;
    }

    default boolean isOffline(){
        return getState()==TokenState.offline;
    }

    @Override
    default int compareTo(UserToken target) {
        return Long.valueOf(getSignInTime()).compareTo(target.getSignInTime());
    }
}
