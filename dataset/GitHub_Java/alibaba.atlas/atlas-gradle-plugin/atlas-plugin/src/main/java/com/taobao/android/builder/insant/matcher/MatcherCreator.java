package com.taobao.android.builder.insant.matcher;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 创建日期：2018/11/23 on 下午3:04
 * 描述:
 * 作者:zhayu.ll
 */
public class MatcherCreator {

    static Map<String, Imatcher> sMatchers = new LinkedHashMap<>();

    public static Imatcher create(String rule) {
        if (sMatchers.containsKey(rule)) {
            return sMatchers.get(rule);
        }
        if (StringUtils.isEmpty(rule)) {
            return new NoMatcher();
        }

        if (rule.equals("**")) {
            return new AllMatcher();
        }
        Imatcher imatcher = null;
        if (rule.startsWith("!")) {
            imatcher = new ExcludeMatcher(rule);
        } else if (rule.endsWith(".**")) {
            imatcher = new PackageMatcher(rule);
        } else if (rule.endsWith(".*")) {
            imatcher = new SubPackgeMatcher(rule);
        } else {
            imatcher = new ClassMatcher(rule);
        }

        sMatchers.put(rule, imatcher);

        return imatcher;

    }
}
