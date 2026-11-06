# Spring Data JPA

## 1. 定位:什么时候用它,什么时候用 MP

JPA 不是"MP 的替代品",而是**另一种数据访问范式**。选错了会让整套代码别扭。

| 维度 | MyBatis-Plus | Spring Data JPA |
| --- | --- | --- |
| 心智模型 | SQL 优先,构造器拼条件 | 对象图优先,实体状态自动跟踪 |
| 一次查询的成本 | 一条 SQL,结果确定 | 可能触发懒加载产生 N+1 查询 |
| 脏检查 | 无,改了就写 | 有,事务内改字段自动 UPDATE |
| 复杂 SQL | 直接写,灵活 | JPQL / 原生 SQL,可读性一般 |
| 调试 | 打印 SQL 直觉 | 同样可打印,但对象关系抽象掉了细节 |
| 学习成本 | 低(会 SQL 就会) | 中高(要懂持久化上下文、懒加载、脏检查) |
| 适合 | CRUD + 报表 + 要求 SQL 可控 | 复杂聚合、领域模型关系紧密 |

**选择建议**:

- 已有 MP 代码 → 继续 MP,不要为了"框架更流行"重写数据层;
- 需求明确要求 JPA / Hibernate → 用 JPA;
- 复杂实体关系 + 大量聚合操作,且团队熟悉 JPA → JPA 更省力;
- **同一个项目里不要两套并存**:实体写法、查询写法、事务写法全都不一样,认知成本翻倍。

## 2. 依赖与配置

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate    # 表结构交给 Flyway,JPA 只校验
    open-in-view: false     # 关闭 OSIV,避免视图层触发懒加载
    properties:
      hibernate:
        format_sql: true
show-sql: false
```

`open-in-view: false` 很重要:默认开启时,Controller 返回后视图层仍能访问懒加载字段,
把数据库连接一直占到请求结束。关掉它,强制在 Service 事务内把数据取全。

## 3. 实体写法

```java
package com.hgschool.agri.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 农作物品种实体。
 *
 * @author hgschool
 */
@Entity
@Table(name = "t_crop")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Crop implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID,数据库自增。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 作物名称。 */
    @Column(name = "crop_name", nullable = false, length = 50)
    private String cropName;

    /** 品种名称。 */
    @Column(name = "variety_name", nullable = false, length = 50)
    private String varietyName;

    /** 创建时间。 */
    @Column(name = "create_time")
    private LocalDateTime createTime;
}

| 注解 | 说明 |
| --- | --- |
| `@Entity` | 声明为 JPA 实体(Bean Validation 与 JPA 校验是两套,别混用) |
| `@Table(name = ...)` | 表名 |
| `@Id` + `@GeneratedValue` | 主键;`IDENTITY` 对应自增 |
| `@Column` | 列名、长度、可空、唯一约束 |
| `@Enumerated(EnumType.STRING)` | 枚举按**名字**存 |
| `@Convert(converter = ...)` | 枚举按**自定义 code** 存(见下) |
| `@Version` | 乐观锁版本号 |
| `@Enumerated` + `AttributeConverter` | 组合使用,推荐 |

**关系映射注意**:

- `@OneToMany` / `@ManyToOne` 默认 EAGER 加载一对多,数据量大时直接拖垮查询;
- 一对多必须 `@OneToMany(mappedBy = "...", fetch = FetchType.LAZY)`;
- `@ManyToMany` 需要中间表,别用它表达"多对多且带业务字段"的关系 —— 那种应该建中间实体;
- 双向关系的 `equals` / `hashCode` 用 Lombok 的 `@Data` 会互相引用导致栈溢出,
  用 `@EqualsAndHashCode(of = "id")` 或 `@Getter` + 手写。

## 4. 枚举落库(code 存法)

JPA 默认只有 `EnumType.STRING`(存名字)和 `EnumType.ORDINAL`(存序号,**极不推荐**,枚举顺序一变数据全乱)。
要像 MP 的 `@EnumValue` 一样存 code,写 `AttributeConverter`:

```java
/**
 * 推广状态与数据库 code 的转换器。
 *
 * @author hgschool
 */
@Converter(autoApply = true)
public class PlantStatusConverter implements AttributeConverter<PlantStatus, Integer> {

    /**
     * 枚举转数据库值。
     *
     * @param attribute 枚举值
     * @return 数据库 code
     */
    @Override
    public Integer convertToDatabaseColumn(PlantStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    /**
     * 数据库值转枚举。
     *
     * @param dbData 数据库 code
     * @return 枚举值
     */
    @Override
    public PlantStatus convertToEntityAttribute(Integer dbData) {
        return PlantStatus.of(dbData);
    }
}
```

## 5. Repository

```java
/**
 * 农作物品种 Repository。
 *
 * @author hgschool
*/
public interface CropRepository extends JpaRepository<Crop, Long> {

    /**
     * 按品种名称精确查询。
     *
     * @param varietyName 品种名称
     * @return 匹配的农作物品种
     */
    Optional<Crop> findByVarietyName(String varietyName);

    /**
     * 判断作物名称与品种名称的组合是否存在。
     *
     * @param cropName    作物名称
     * @param varietyName 品种名称
     * @return 是否存在
     */
    boolean existsByCropNameAndVarietyName(String cropName, String varietyName);
}

### 方法名派生查询

不用写 SQL,靠方法名拼:`find` / `exists` / `count` / `delete` + `By` + 条件 + `And/Or` + 属性名。
属性名用**驼峰**,复杂条件用 `Top5`、`OrderByIdDesc` 表达。

### @Query

```java
@Query("select c from Crop c where c.cropName like %:cropName% and c.deleted = 0")
List<Crop> searchByCropName(@Param("cropName") String cropName);

@Query(value = "SELECT * FROM t_crop WHERE deleted = 0 AND growth_cycle > ?1", nativeQuery = true)
List<Crop> findByGrowthCycleGreaterThan(int growthCycle);
```

JPQL 操作的是**实体与属性名**(驼峰),原生 SQL 操作的是**表与列名**(下划线),两者别混。

### 动态查询(Specification)

这是 JPA 对应 MP `LambdaQueryWrapper` 的能力。

```java
/**
 * 农作物品种动态查询条件。
 *
 * @author hgschool
 */
public class CropSpecifications {

    /**
     * 构造动态查询条件。
     *
     * @param cropName    作物名称,模糊匹配
     * @param varietyName 品种名称,模糊匹配
     * @return 查询条件
     */
    public static Specification<Crop> withCondition(String cropName, String varietyName) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StrUtil.isNotBlank(cropName)) {
                predicates.add(cb.like(root.get("cropName"), "%" + cropName + "%"));
            }
            if (StrUtil.isNotBlank(varietyName)) {
                predicates.add(cb.like(root.get("varietyName"), "%" + varietyName + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

```java
@Service
@RequiredArgsConstructor
public class CropServiceImpl {

    private final CropRepository cropRepository;

    /**
     * 分页动态查询。
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    public PageResult<CropVO> page(CropQueryDTO queryDTO) {
        Pageable pageable = PageRequest.of(queryDTO.getPageNum() - 1, queryDTO.getPageSize());
        Specification<Crop> spec = CropSpecifications.withCondition(
                queryDTO.getCropName(), queryDTO.getVarietyName());
        Page<Crop> page = cropRepository.findAll(spec, pageable);
        List<CropVO> records = page.getContent().stream().map(CropVO::from).toList();
        return PageResult.<CropVO>builder()
                .total(page.getTotalElements())
                .pages(page.getTotalPages())
                .current(page.getNumber() + 1L)
                .size((long) page.getSize())
                .records(records)
                .build();
    }
}
```

注意 JPA 的页码从 **0** 开始,MP 的 `Page` 从 **1** 开始,对外要统一。

### 修改与删除

```java
@Modifying
@Query("update Crop c set c.plantStatus = :status where c.id = :id")
int updateStatus(@Param("id") Long id, @Param("status") PlantStatus status);
```

`@Modifying` 的两个必配项:

- `@Transactional`:批量修改必须开事务;
- `clearAutomatically = true` / `flushAutomatically = true`:批量改完要清持久化上下文,
  否则内存里的旧实体还是脏的,后续读到的还是旧值。

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("update Crop c set c.plantStatus = :status where c.id in :ids")
int updateStatusBatch(@Param("ids") List<Long> ids, @Param("status") PlantStatus status);
```

## 6. 事务与懒加载的坑

```java
@Service
@RequiredArgsConstructor
public class CropServiceImpl {

    private final CropRepository cropRepository;

    /**
     * 查询详情。
     *
     * @param id 品种ID
     * @return 农作物品种
     */
    @Transactional(readOnly = true)
    public Crop getById(Long id) {
        return cropRepository.findById(id).orElseThrow(
                () -> new BusinessException("农作物品种不存在, id: " + id));
    }
}
```

| 坑 | 现象 | 处理 |
| --- | --- | --- |
| 懒加载在事务外触发 | `LazyInitializationException` | 关 `open-in-view`,在事务内把数据取全;或用 `@EntityGraph` 一次查完 |
| N+1 查询 | 查 1 条列表发 N+1 条 SQL | `@EntityGraph(attributePaths = {"..."})` 或 join fetch |
| 脏数据没写回 | 改了字段但事务提交后没更新 | 确认实体在事务内被修改;用 `save()` 显式落库更保险 |
| `save()` 的语义 | id 为 null → insert,id 不为 null → update,但会先 select | 主键自增场景用 `save()` 即可,但要知道它有额外查询 |

`@EntityGraph` 示例:

```java
/**
 * 查询品种及其所属地块。
 *
 * @param id 品种ID
 * @return 农作物品种
 */
@EntityGraph(attributePaths = {"field"})
Optional<Crop> findWithFieldById(Long id);
```

## 7. 测试

```java
/**
 * 农作物品种 Repository 测试。
 *
 * @author hgschool
 */
@DataJpaTest
class CropRepositoryTest {

    @Autowired
    private CropRepository cropRepository;

    /**
     * 按品种名称查询存在的记录。
     */
    @Test
    void shouldFindByVarietyName() {
        cropRepository.save(Crop.builder().cropName("水稻").varietyName("湘早稻9号").build());
        Optional<Crop> found = cropRepository.findByVarietyName("湘早稻9号");
        assertTrue(found.isPresent());
    }
}
```

`@DataJpaTest` 只会加载 JPA 相关 Bean,启动快,适合测 Repository。
注意:它默认用内存库并**回滚事务**,不会污染真实数据。

## 8. 与 MP 的直观对照

| 需求 | MyBatis-Plus | Spring Data JPA |
| --- | --- | --- |
| 按主键查一条 | `selectById(id)` | `findById(id)` |
| 查一条或抛异常 | `selectOne(wrapper)` | `findById(id).orElseThrow(...)` |
| 条件查列表 | `selectList(wrapper)` | `findAll(spec)` |
| 条件查是否存在 | `selectCount(wrapper) > 0` | `existsByXxx(...)` |
| 分页 | `selectPage(page, wrapper)` | `findAll(spec, pageable)` |
| 插入 | `insert(entity)` | `save(entity)` |
| 按条件更新 | `update(entity, wrapper)` | `@Modifying @Query` |
| 逻辑删除 | `@TableLogic` 自动处理 | 需自己写 `@SQLDelete` 或软删字段 |
| 批量插入 | `insert(list)` / `saveBatch` | `saveAll(list)` |
| 动态条件 | `LambdaQueryWrapper` | `Specification` |
| 投影查询 | 查实体再转 VO,或 XML 自定义 | 接口投影 `interface CropProjection` |
