package com.college.growth.common.result;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页数据载体（班级台账、审计日志等列表接口统一使用）。
 *
 * @param <T> 行数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据。 */
    private List<T> records;

    /** 记录总数。 */
    private long total;

    /** 页码，从 1 开始。 */
    private long pageNum;

    /** 每页条数。 */
    private long pageSize;
}
