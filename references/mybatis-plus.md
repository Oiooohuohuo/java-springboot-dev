# MyBatis-Plus 实战

## 1. 依赖与版本陷阱

```xml
<properties>
    <mybatis-plus.version>3.5.9</mybatis-plus.version>
</properties>

<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>${mybatis-plus.version}</version>
</dependency>

<!-- 3.5.9 起 JSqlParser 被拆为独立模块,分页插件必须显式引入,否则启动期 ClassNotFoundException -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-jsqlparser</artifactId>
    <version>${mybatis-plus.version}</version>
</dependency>
```

| 症状 | 根因 | 处理 |
| --- | --- | --- |
| 启动报 `ClassNotFoundException: com.baomidou.mybatisplus.extension.parser....` | 缺 `mybatis-plus-jsqlparser` | 补依赖 |
| 分页接口不生效,返回全量数据 | 缺 `jsqlparser` 或没注册分页插件 | 两处都补 |
| 找不到 `mybatis-plus-spring-boot-starter` | 用了非 boot3 版本 | 换 `mybatis-plus-spring-boot3-starter` |

## 2. 全局配置

```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true          # 下划线 ↔ 驼峰自动映射
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    banner: false
    db-config:
      id-type: auto                              # 主键策略,与 @TableId 一致
      logic-delete-field: deleted                # 逻辑删除字段
      logic-delete-value: 1
      logic-not-delete-value: 0
```

## 3. Entity 注解速查

| 注解 | 用途 | 常用取值 |
| --- | --- | --- |
| `@TableName` | 绑定表名 | `@TableName("t_crop")` |
| `@TableId` | 主键 | `type = IdType.AUTO`(自增) |
| `@TableField` | 字段映射 | `value = "create_time"`,`fill = FieldFill.INSERT` |
| `@TableLogic` | 逻辑删除 | 配 `logic-delete-field` |
| `@Version` | 乐观锁 | 需要乐观锁时再加 |
| `@EnumValue` | 枚举落库字段 | 配合 MP 默认的 `CompositeEnumTypeHandler` |

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_crop")
public class Crop implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID,数据库自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 作物名称。 */
    private String cropName;

    /** 逻辑删除标记:0 正常,1 已删除。 */
    @TableLogic
    @TableField(value = "deleted")
    private Integer deleted;

    /** 创建时间,插入时自动填充。 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间,插入与更新时自动填充。 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

> `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor` 一起用:
`@Builder` 需要全参构造,MyBatis 需要无参构造,Jackson 反序列化需要 setter(`@Data` 提供)。三者缺一会报运行时异常。

## 4. LambdaQueryWrapper:条件为空不拼接

这是最常用也最容易写错的 API。**每个可选条件都要先判空**,
否则 `LIKE %%` 会让索引失效,全表扫描。

```java
LambdaQueryWrapper<Crop> wrapper = new LambdaQueryWrapper<>();

if (StrUtil.isNotBlank(queryDTO.getCropName())) {
    wrapper.like(Crop::getCropName, queryDTO.getCropName());
}
if (StrUtil.isNotBlank(queryDTO.getVarietyName())) {
    wrapper.like(Crop::getVarietyName, queryDTO.getVarietyName());
}
if (queryDTO.getPlantStatus() != null) {
    wrapper.eq(Crop::getPlantStatus, queryDTO.getPlantStatus());
}
if (queryDTO.getCreateTimeStart() != null) {
    wrapper.ge(Crop::getCreateTime, queryDTO.getCreateTimeStart());
}

wrapper.orderByDesc(Crop::getId);

List<Crop> list = cropMapper.selectList(wrapper);
```

| 方法 | 语义 | 说明 |
| --- | --- | --- |
| `eq` | 等于 | 精确匹配 |
| `ne` | 不等于 | 修改场景排除自身 |
| `like` / `likeRight` / `likeLeft` | 模糊 | `like` 是两边都加 `%` |
| `in` / `notIn` | 集合 | 集合要判空,空集合会生成 `IN ()` 报错 |
| `ge` / `le` / `gt` / `lt` | 区间 | 时间、数值区间 |
| `between` | 闭区间 | 左右都含 |
| `orderByAsc` / `orderByDesc` | 排序 | 链式调用,顺序即优先级 |
| `last` | 追加 SQL 片段 | 只用于分页插件生成的 SQL,如 `last("limit 1")` |

**为什么用 Lambda 而不是字符串**:

- 字段改名时编译期就报错,字符串写法要等到运行时才炸;
- 不会出现"列名写错但 SQL 不报错"的情况。

**常用组合**

```java
// 查一条
Crop crop = cropMapper.selectOne(new LambdaQueryWrapper<Crop>()
        .eq(Crop::getId, id));

// 查是否存在(返回 Long,3.5.9 起不是 Integer)
Long exists = cropMapper.selectCount(new LambdaQueryWrapper<Crop>()
        .eq(Crop::getCropName, cropName)
        .eq(Crop::getVarietyName, varietyName));
if (exists != null && exists > 0) { ... }

// 修改时排除自身
LambdaQueryWrapper<Crop> wrapper = new LambdaQueryWrapper<Crop>()
        .eq(Crop::getCropName, cropName)
        .eq(Crop::getVarietyName, varietyName);
if (excludeId != null) {
    wrapper.ne(Crop::getId, excludeId);
}
```

## 5. 分页

```java
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

```java
IPage<Crop> page = cropMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
List<CropVO> records = page.getRecords().stream().map(CropVO::from).toList();

return PageResult.<CropVO>builder()
        .total(page.getTotal())
        .pages(page.getPages())
        .current(page.getCurrent())
        .size(page.getSize())
        .records(records)
        .build();
```

注意:

- `pageSize` 必须有上限(`setMaxLimit`),并在 DTO 上用 `@Max` 再挡一道;
- **不要**把 `IPage` 直接返回给前端,它带着 MP 的内部字段,耦合过深,要用 `PageResult` 转换;
- `records` 的转换用 Stream + `toList()`(Java 16+),不要手写 for 循环。

## 6. 逻辑删除

```java
@TableLogic
@TableField(value = "deleted")
private Integer deleted;
```

```yaml
global-config:
  db-config:
    logic-delete-field: deleted
    logic-delete-value: 1
    logic-not-delete-value: 0
```

效果:

- `deleteById` 实际执行 `UPDATE ... SET deleted = 1`;
- `selectList` / `selectOne` / `selectCount` / `selectPage` 自动追加 `deleted = 0`;
- **手工写的 `@Select` SQL 不会自动加条件**,需要自己写 `WHERE deleted = 0`。

逻辑删除带来的两个坑:

1. 唯一性校验(如"同名品种不允许重复")会因为逻辑删除的记录不参与查询而"失效";
   → 业务上若要求"删过也不能重名",查重要显式带上 `deleted` 字段;
2. 数据不会真正消失,`COUNT(*)` 统计要区分 `deleted`。

## 7. 时间字段自动填充

```java
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
    }

    /**
     * 更新时填充更新时间。
     *
     * @param metaObject 待填充的实体元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
```

`strictInsertFill` / `strictUpdateFill` 只会填充**实体中带 `fill` 注解且类型匹配**的字段,不会误填。
填充后数据库侧的 `DEFAULT CURRENT_TIMESTAMP` 就成了兜底,两边一致。

## 8. 批量与更新

```java
// 批量插入(自动提交,返回 boolean)
cropMapper.insert(crops);

// 批量更新:先查出来改,再 saveBatch
List<Crop> crops = cropMapper.selectList(wrapper);
crops.forEach(crop -> crop.setPlantStatus(PlantStatus.STOP));
cropMapper.updateById(crop);   // 循环里的单条更新
cropMapper.insert(crops);      // MP 3.5.9 起支持批量插入实体集合
```

| 场景 | 推荐 |
| --- | --- |
| 批量新增 | `mapper.insert(list)`(3.5.9+)/ `saveBatch`(IService) |
| 批量改状态 | 先 `selectList` 查出来,内存改,再批量写 |
| 按条件批量改 | `mapper.update(entity, wrapper)`,**wrapper 不能为空** |
| 只改部分列 | `mapper.update(实体, wrapper)`,null 字段不更新 |

**危险操作**:`mapper.update(null, wrapper)` 或 `mapper.delete(wrapper)` 若 wrapper 为空,
会生成 `UPDATE t_crop SET ...`(全表)或 `DELETE FROM t_crop`(全表删)。
务必先判空,或只使用带明确条件的 wrapper。

## 9. 何时不用 LambdaQueryWrapper

下面这些场景直接写 SQL,别硬套构造器:

- 多表 JOIN;
- 复杂聚合(`GROUP BY` + `HAVING` + 子查询);
- 需要 `UNION` / 窗口函数;
- 批量 `INSERT ... ON DUPLICATE KEY UPDATE`。

写法:Mapper 方法上加 `@Select` / `@Update` / `@Insert`,参数用 `@Param` 标注:

```java
@Select("SELECT c.* FROM t_crop c JOIN t_field f ON c.field_id = f.id WHERE c.deleted = 0 AND f.code = #{code}")
List<Crop> selectByFieldCode(@Param("code") String code);
```

复杂 SQL 建议放 XML(`resources/mapper/*.xml`),可读性比注解好。
注意:**手写 SQL 不会被逻辑删除自动处理,`deleted = 0` 要自己写。**
