package org.hswebframework.web.bean;

import org.apache.commons.beanutils.BeanUtils;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author zhouhao
 * @since 3.0
 */
public class FastBeanCopierTest {

    @Test
    public void test() throws InvocationTargetException, IllegalAccessException {
        Source source = new Source();
        source.setAge(100);
        source.setName("测试");
        source.setIds(new String[]{"1", "2", "3"});
        source.setAge2(2);
        source.setBoy2(true);
        source.setColor(Color.RED);
        source.setNestObject2(Collections.singletonMap("name", "mapTest"));
        NestObject nestObject = new NestObject();
        nestObject.setAge(10);
        nestObject.setPassword("1234567");
        nestObject.setName("测试2");
        source.setNestObject(nestObject);
        source.setNestObject3(nestObject);

        Target target = new Target();
        FastBeanCopier.copy(source, target);


        System.out.println(source);
        System.out.println(target);
        System.out.println(target.getNestObject() == source.getNestObject());
    }

    @Test
    public void testMapArray() {
       Map<String,Object> data =new HashMap<>();
       data.put("colors", Arrays.asList("RED"));


        Target target = new Target();
        FastBeanCopier.copy(data, target);


        System.out.println(target);
        Assert.assertNotNull(target.getColors());
        Assert.assertSame(target.getColors()[0], Color.RED);

    }

    @Test
    public void testCopyMap() {


        Source source = new Source();
        source.setAge(100);
        source.setName("测试");
//        source.setIds(new String[]{"1", "2", "3"});
        NestObject nestObject = new NestObject();
        nestObject.setAge(10);
        nestObject.setName("测试2");
//        source.setNestObject(nestObject);


        Map<String, Object> target = new HashMap<>();


        System.out.println(FastBeanCopier.copy(source, target, FastBeanCopier.include("age")));

        System.out.println(target);
        System.out.println(FastBeanCopier.copy(target, new Target()));
    }


}