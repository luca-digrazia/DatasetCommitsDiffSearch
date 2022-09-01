/*
 *
 *  * Copyright 2016 http://www.hswebframework.org
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.hswebframework.web.commons.entity;


import org.hswebframework.web.id.IDGenerator;
import org.hswebframwork.utils.RandomUtil;
import org.hswebframwork.utils.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public interface TreeSupportEntity<PK> extends GenericEntity<PK> {

    String id = "id";

    String treeCode = "treeCode";

    String parentId = "parentId";

    String getTreeCode();

    void setTreeCode(String treeCode);

    PK getParentId();

    void setParentId(PK parentId);

    <T extends TreeSupportEntity<PK>> List<T> getChildren();

    /**
     * 根据treeCode获取父节点的treeCode
     *
     * @param treeCode treeCode
     * @return 父节点treeCode
     */
    static String getParentTreeCode(String treeCode) {
        if (treeCode == null || treeCode.length() < 4) return null;
        return treeCode.substring(0, treeCode.length() - 5);
    }

    /**
     * 将树形结构转为列表结构，并填充对应的数据。<br>
     * 如树结构数据： {name:'父节点',children:[{name:'子节点1'},{name:'子节点2'}]}<br>
     * 解析后:[{id:'id1',name:'父节点',treeCode:'<b>aoSt</b>'},{id:'id2',name:'子节点1',treeCode:'<b>aoSt</b>-oS5a'},{id:'id3',name:'子节点2',treeCode:'<b>aoSt</b>-uGpM'}]
     *
     * @param parent      树结构的根节点
     * @param target      目标集合,转换后的数据将直接添加({@link List#add(Object)})到这个集合.
     * @param <T>         继承{@link TreeSupportEntity}的类型
     * @param idGenerator ID生成策略
     * @param <PK>        主键类型
     */
    static <T extends TreeSupportEntity<PK>, PK> void expandTree2List(TreeSupportEntity<PK> parent, List<T> target, IDGenerator<PK> idGenerator) {
        List<T> children = parent.getChildren();
        if (parent.getTreeCode() == null) {
            parent.setTreeCode(RandomUtil.randomChar(4));
        }
        if (children != null) {
            PK pid = parent.getId();
            if (pid == null) {
                pid = idGenerator.generate();
                parent.setId(pid);
            }
            for (int i = 0; i < children.size(); i++) {
                T child = children.get(i);
                if (child instanceof SortSupportEntity && parent instanceof SortSupportEntity) {
                    ((SortSupportEntity) child).setSortIndex(StringUtils.toLong(((SortSupportEntity) parent).getSortIndex() + "0" + (i + 1)));
                }
                child.setParentId(pid);
                child.setTreeCode(parent.getTreeCode() + "-" + RandomUtil.randomChar(4));
                target.add(child);
                expandTree2List(child, target, idGenerator);
            }
        }
    }

    /**
     * 集合转为树形结构,返回根节点集合
     *
     * @param dataList      需要转换的集合
     * @param childAccepter 设置子节点回调
     * @param <T>           树节点类型
     * @param <PK>          主键类型
     * @return 树形结构集合
     */
    static <T extends TreeSupportEntity<PK>, PK> List<T> list2tree(Collection<T> dataList, BiConsumer<T, List<T>> childAccepter) {
        // id,obj
        Map<PK, T> cache = new HashMap<>();
        // parentId,children
        Map<PK, List<T>> treeCache = dataList.parallelStream()
                .peek(node -> cache.put(node.getId(), node))
                .collect(Collectors.groupingBy(TreeSupportEntity::getParentId));
        return dataList.parallelStream()
                //设置每个节点的子节点
                .peek(node -> childAccepter.accept(node, treeCache.get(node.getId())))
                //获取根节点
                .filter(node -> node.getParentId() == null || cache.get(node.getParentId()) == null)
                .collect(Collectors.toList());
    }
}
