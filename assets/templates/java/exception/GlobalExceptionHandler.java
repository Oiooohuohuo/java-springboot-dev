package {{package}}.exception;

import {{package}}.common.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>把校验异常、业务异常与未知异常统一转换成 Result 结构返回,
 * 前端拿到的响应格式永远一致。</p>
 *
  * <p>模板占位符:{@code {{package}}} 需替换为实际包名(不含子包)。</p>
  * @author hgschool
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常,属于可预期的正常流程分支,日志级别为 warn。
     *
     * @param e 业务异常
     * @return 统一响应结构
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常, message: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理 @RequestBody 上的 @Valid 校验失败(校验分组场景)。
     *
     * @param e 参数校验异常
     * @return 统一响应结构
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(";"));
        log.warn("参数校验失败, message: {}", message);
        return Result.error(400, message);
    }

    /**
     * 处理表单绑定校验失败。
     *
     * @param e 参数绑定异常
     * @return 统一响应结构
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(";"));
        log.warn("参数绑定失败, message: {}", message);
        return Result.error(400, message);
    }

    /**
     * 处理方法参数上的约束校验失败(如 @RequestParam 上的 @Min)。
     *
     * @param e 约束校验异常
     * @return 统一响应结构
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(";"));
        log.warn("参数约束校验失败, message: {}", message);
        return Result.error(400, message);
    }

    /**
     * 处理必填请求参数缺失。
     *
     * @param e 缺少参数异常
     * @return 统一响应结构
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数, parameter: {}", e.getParameterName());
        return Result.error(400, "缺少必填参数: " + e.getParameterName());
    }

    /**
     * 处理请求体格式错误(如 JSON 解析失败、类型不匹配)。
     *
     * @param e 消息不可读异常
     * @return 统一响应结构
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误, message: {}", e.getMessage());
        return Result.error(400, "请求体格式错误");
    }

    /**
     * 兜底处理未预期的系统异常,必须打印完整堆栈。
     *
     * @param e 系统异常
     * @return 统一响应结构
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(500, "系统异常, 请联系管理员");
    }
}
