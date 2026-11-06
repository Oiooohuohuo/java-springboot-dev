package {{package}}.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应结果封装。
 *
 * <p>所有 Controller 接口均返回该结构,前端只需判断 code 即可,
 * 无需关心 HTTP 状态码与业务状态的差异。</p>
 *
 * @param <T> 业务数据类型
  * <p>模板占位符:{@code {{package}}} 需替换为实际包名(不含子包)。</p>
  * @author hgschool
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务状态码:200 成功,400 参数校验失败,500 业务或系统异常。 */
    private Integer code;

    /** 提示信息。 */
    private String msg;

    /** 业务数据,失败时为 null。 */
    private T data;

    /**
     * 成功且无返回数据。
     *
     * @param <T> 业务数据类型
     * @return 统一响应结构
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    /**
     * 成功并携带业务数据。
     *
     * @param data 业务数据
     * @param <T>  业务数据类型
     * @return 统一响应结构
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /**
     * 成功并携带自定义提示与业务数据。
     *
     * @param msg  提示信息
     * @param data 业务数据
     * @param <T>  业务数据类型
     * @return 统一响应结构
     */
    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(200, msg, data);
    }

    /**
     * 失败并携带错误码与提示。
     *
     * @param code 错误码
     * @param msg  错误提示
     * @param <T>  业务数据类型
     * @return 统一响应结构
     */
    public static <T> Result<T> error(Integer code, String msg) {
        return new Result<>(code, msg, null);
    }
}
