package {{package}}.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 分页结果封装。
 *
 * <p>屏蔽 MyBatis-Plus 的 IPage 结构,对外只暴露总条数、总页数、当前页与数据列表。</p>
 *
 * @param <T> 列表元素类型
  * <p>模板占位符:{@code {{package}}} 需替换为实际包名(不含子包)。</p>
  * @author hgschool
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 总条数。 */
    private Long total;

    /** 总页数。 */
    private Long pages;

    /** 当前页码。 */
    private Long current;

    /** 每页条数。 */
    private Long size;

    /** 当前页数据列表。 */
    private List<T> records;

    /**
     * 由 MyBatis-Plus 分页对象转换为分页结果。
     *
     * @param page MyBatis-Plus 分页对象
     * @param <T>  列表元素类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        return PageResult.<T>builder()
                .total(page.getTotal())
                .pages(page.getPages())
                .current(page.getCurrent())
                .size(page.getSize())
                .records(page.getRecords())
                .build();
    }
}
