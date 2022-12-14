package org.hsweb.web.authorize.annotation;


import java.lang.annotation.*;

/**
 * 权限注解
 * Created by 浩 on 2015-08-25 0025.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Authorize {

    /**
     * 对角色授权,当使用按角色授权时，对模块以及操作级别授权方式失效
     *
     * @return 进行授权的角色数组
     */
    String[] role() default {};

    /**
     * 对模块授权
     *
     * @return 进行授权的模块
     */
    String[] module() default {};

    /**
     * 操作级别,如增删改查等
     *
     * @return
     */
    String[] level() default {};

    /**
     * 表达式验证如
     *
     * @return
     */
    String expression() default "";

    /**
     * 表达式语言，默认spring表达式
     *
     * @return 表达式语言
     */
    String expressionLanguage() default "spel";

    /**
     * 根据ip地址进行权限控制，支持ip段如:  192.168.1.*; 192.168.2.1-10;192.168.2-5.1-10
     * @return ip表
     */
    String[] ip() default {};

    /**
     * 是否为api接口，为true时，不使用用户登录策略。
     *
     * @return
     */
    boolean api() default false;

    /**
     * 验证模式，在使用多个验证条件时有效，当设置为INTERSECTION时，用户只需要通过其中一个验证即可
     *
     * @return
     */
    MOD mod() default MOD.AND;

    enum MOD {
        /**
         * 并集，需要满足所有验证条件
         */
        AND,
        /**
         * 交集，满足其中一个验证条件
         */
        OR
    }
}
