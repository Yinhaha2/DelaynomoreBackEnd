package org.example.schoolshop.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {

    private List<T> list;
    private long total;
    private long page;
    private long pageSize;
    private boolean hasMore;

    public static <T> PageResult<T> of(IPage<T> pageData) {
        PageResult<T> r = new PageResult<>();
        r.setList(pageData.getRecords());
        r.setTotal(pageData.getTotal());
        r.setPage(pageData.getCurrent());
        r.setPageSize(pageData.getSize());
        r.setHasMore(pageData.getCurrent() * pageData.getSize() < pageData.getTotal());
        return r;
    }
}
