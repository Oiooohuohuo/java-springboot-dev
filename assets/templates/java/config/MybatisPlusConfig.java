package {{package}}.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类。
 *
 * <p>注册分页插件,使 Mapper 的分页方法能够生成 LIMIT 语句。</p>
 *
  * <p>模板占位符:{@code {{package}}} 需替换为实际包名(不含子包)。</p>
  * @author hgschool
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * MyBatis-Plus 拦截器链,分页插件必须注册在这里才会生效。
     *
     * @return MyBatis-Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大条数,防止前端传入过大的 pageSize 拖垮数据库
        paginationInnerInterceptor.setMaxLimit(500L);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        return interceptor;
    }
}
