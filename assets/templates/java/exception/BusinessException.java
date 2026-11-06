package {{package}}.exception;

import lombok.Getter;
import org.slf4j.helpers.MessageFormatter;

import java.io.Serial;

/**
 * 自定义业务异常。
 *
 * <p>用于表达"业务规则不满足"这类可预期的失败,由全局异常处理器统一转成友好提示,
 * 不需要写 try-catch 逐层向上抛。</p>
 *
 * <p>消息支持 SLF4J 风格的 {} 占位符,便于把上下文参数带进异常信息:</p>
 * <pre>{@code
 * throw new BusinessException("农作物品种不存在, id: {}", id);
 * }</pre>
 *
  * <p>模板占位符:{@code {{package}}} 需替换为实际包名(不含子包)。</p>
  * @author hgschool
 */
@Getter
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务错误码,默认 500。 */
    private final int code;

    /**
     * 使用默认错误码 500 构造业务异常。
     *
     * @param message 错误提示,建议包含上下文参数
     */
    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    /**
     * 指定错误码构造业务异常。
     *
     * @param code    错误码
     * @param message 错误提示,建议包含上下文参数
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造带 {} 占位符的业务异常。
     *
     * <p>注意:没有这个构造器时,"id: {}" 这样的模板会原样输出给前端。</p>
     *
     * @param message 消息模板,如 "农作物品种不存在, id: {}"
     * @param args    上下文参数
     */
    public BusinessException(String message, Object... args) {
        super(MessageFormatter.arrayFormat(message, args).getMessage());
        this.code = 500;
    }

    /**
     * 指定错误码并带 {} 占位符构造业务异常。
     *
     * @param code    错误码
     * @param message 消息模板
     * @param args    上下文参数
     */
    public BusinessException(int code, String message, Object... args) {
        super(MessageFormatter.arrayFormat(message, args).getMessage());
        this.code = code;
    }
}
