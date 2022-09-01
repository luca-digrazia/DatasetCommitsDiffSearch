package org.hsweb.web.bean.common;

import java.io.Serializable;
import java.util.List;

/**
 * Created by 浩 on 2016-01-16 0016.
 */
public class PagerResult<Po> implements Serializable {
    private static final long serialVersionUID = -6171751136953308027L;
    private int total;

    private List<Po> data;

    public PagerResult() {
    }

    public PagerResult(int total, List<Po> data) {
        this.total = total;
        this.data = data;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<Po> getData() {
        return data;
    }

    public void setData(List<Po> data) {
        this.data = data;
    }
}
