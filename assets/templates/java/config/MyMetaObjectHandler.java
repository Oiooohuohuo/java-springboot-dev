package {{package}}.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器。
 *
 * <p>统一处理创建时间与更新时间的赋值,避免在 Service 中重复写时间字段。</p>
 *
  * <p>模板占位符:{@code {{package}}} 需替换为实际包名(不含子包)。</p>
  * @author hgschool
 */
@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时填充创建时间与更新时间。
     *
     * @param metaObject 待填充的实体元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
        log.debug("插入自动填充时间, createTime: {}, updateTime: {}", now, now);
    }

    /**
     * 更新时填充更新时间。
     *
     * @param metaObject 待填充的实体元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
        log.debug("更新自动填充时间, updateTime: {}", now);
    }
}
