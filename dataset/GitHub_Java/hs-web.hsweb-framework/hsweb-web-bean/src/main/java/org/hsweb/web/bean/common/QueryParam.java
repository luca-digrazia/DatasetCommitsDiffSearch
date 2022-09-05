package org.hsweb.web.bean.common;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by 浩 on 2016-01-16 0016.
 */
public class QueryParam extends SqlParam<QueryParam> implements Serializable {
    private static final long serialVersionUID = 7941767360194797891L;

    /**
     * 是否进行分页，默认为true
     */
    private boolean paging = true;

    /**
     * 第几页 从0开始
     */
    private int pageIndex = 0;

    /**
     * 每页显示记录条数
     */
    private int pageSize = 25;

    /**
     * 排序字段
     */
    @Deprecated
    private Set<String> sortField = new LinkedHashSet<>();

    /**
     * 排序字段
     */
    private List<Sort> sorts = new LinkedList<>();

    /**
     * 排序方式 DESC 反序 ASC 正序
     */
    @Deprecated
    private String sortOrder = "asc";

    public QueryParam select(String... fields) {
        return this.includes(fields);
    }

    public Sort orderBy(String sortField) {
        Sort sort = new Sort(this, sortField);
        sorts.add(sort);
        return sort;
    }

    public QueryParam doPaging(int pageIndex) {
        this.pageIndex = pageIndex;
        this.paging = true;
        return this;
    }

    public QueryParam noPaging() {
        this.paging = false;
        return this;
    }

    public QueryParam doPaging(int pageIndex, int pageSize) {
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
        this.paging = true;
        return this;
    }

    public QueryParam rePaging(int total) {
        paging = true;
        // 当前页没有数据后跳转到最后一页
        if (this.getPageIndex() != 0 && (pageIndex * pageSize) >= total) {
            int tmp = total / this.getPageSize();
            pageIndex = total % this.getPageSize() == 0 ? tmp - 1 : tmp;
        }
        return this;
    }

    public boolean isPaging() {
        return paging;
    }

    public void setPaging(boolean paging) {
        this.paging = paging;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public Set<String> getSortField() {
        return sortField;
    }

    public void setSortField(Set<String> sortField) {
        this.sortField = sortField;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        if (sortOrder.contains("desc") || sortOrder.contains("DESC"))
            sortOrder = "desc";
        this.sortOrder = sortOrder;
    }

    public static QueryParam newInstance() {
        return new QueryParam();
    }

    public List<Sort> getSorts() {
        return sorts;
    }

    public void setSorts(List<Sort> sorts) {
        this.sorts = sorts;
    }

    public static QueryParam build() {
        return new QueryParam();
    }
}
