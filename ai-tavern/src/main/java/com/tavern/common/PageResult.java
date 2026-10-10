package com.tavern.common;

import com.github.pagehelper.PageInfo;
import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {
    private List<T> list;
    private long total;
    private int page;
    private int size;

    public static <T> PageResult<T> of(List<T> list) {
        PageInfo<T> info = new PageInfo<>(list);
        PageResult<T> r = new PageResult<>();
        r.list = list;
        r.total = info.getTotal();
        r.page = info.getPageNum();
        r.size = info.getPageSize();
        return r;
    }

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        PageResult<T> r = new PageResult<>();
        r.list = list;
        r.total = total;
        r.page = page;
        r.size = size;
        return r;
    }
}
