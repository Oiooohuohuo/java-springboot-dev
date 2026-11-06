# 分层架构

## 1. 分层职责一览

| 层 | 包名 | 职责 | 允许依赖 | 禁止出现 |
| --- | --- | --- | --- | --- |
| Controller | `controller` | 接参、触发校验、封结果、记入口日志 | service / dto / vo / common | 业务 if、Mapper、直接操作 Entity |
| Service | `service` + `service.impl` | 全部业务逻辑、事务边界 | mapper / dto / vo / entity / common | `Result`、HTTP 对象 |
| Mapper | `mapper` | 数据访问 | entity / common | 业务判断(除非确有必要) |
| Entity | `entity` | 表映射,MP 注解 | 无 | 业务校验、HTTP 注解 |
| DTO | `dto` | 入参模型 + 校验规则 | common | 数据库注解(`@TableName` 等) |
| VO | `vo` | 出参模型 | entity(仅用于静态工厂转换) | 敏感字段、密码 |
| common | `common` | 统一响应、分页等无业务横切物 | 无 | 任何业务概念 |
| exception | `exception` | 业务异常 + 全局处理器 | common | 具体业务逻辑 |
| config | `config` | 框架配置(分页插件、自动填充) | 无 | 业务逻辑 |
| util | `util` | 纯工具(无状态) | 无 | Spring Bean(用 `@Component` 管理) |

**依赖方向是单向的**:`Controller → Service → Mapper → Entity`。
下层**不得**反向依赖上层。唯一允许的例外是 `VO` 依赖 `Entity` 做静态转换工厂,`Entity` 不依赖任何东西。

## 2. 依赖注入

全部使用构造器注入,依赖字段声明为 `private final`:

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class CropServiceImpl implements CropService {

    private final CropMapper cropMapper;
```

为什么不用 `@Autowired` 字段注入:

- 字段可以声明为 `final`,对象一旦创建就不再变,天然线程安全;
- 单元测试可以直接 `new CropServiceImpl(cropMapper)`,不需要 Mockito 的 `@InjectMocks`;
- 依赖关系在构造时就确定,漏注入会在启动时直接失败,而不是运行到一半才 NPE。

> `develop-norm` 提到"是否引入并发问题(实例字段、static 变量)":Service 里**不要放可变实例字段**,否则并发请求会互相污染。需要跨请求共享的状态,放 ThreadLocal 或外部存储。

## 3. Entity / DTO / VO 的边界

这三个模型经常被合并,合并的代价是:数据库结构一变,前端接口跟着变;密码等敏感字段迟早被误返回。

| 模型 | 面向 | 谁创建 | 谁消费 | 可以含数据库注解 | 绝不能含 |
| --- | --- | --- | --- | --- | --- |
| Entity | 数据库表 | MyBatis-Plus | Mapper / Service | `@TableName` `@TableId` `@TableLogic` `@TableField` | 校验注解、密码等敏感字段的业务流转 |
| DTO | 接口入参 | 前端 | Controller → Service | ❌ | `@TableName` 等 |
| VO | 接口出参 | Service | Controller → 前端 | ❌ | `password`、`deleted` 等内部字段 |

**典型反例(真实代码里出现过)**:`UserVO` 里放了 `password` 字段,接口一旦返回就把密文推给了前端。
正确做法:VO 字段清单逐个对照 Entity,**只留前端真正需要的**。

## 4. Controller 层的写法

```java
@Slf4j
@Validated
@RestController
@RequestMapping("/crop")
@RequiredArgsConstructor
public class CropController {

    private final CropService cropService;

    /**
     * 新增农作物品种。
     *
     * @param cropDTO 新增参数,@Validated 触发 DTO 分组校验
     * @return 新增后的品种信息
     */
    @PostMapping("/add")
    public Result<CropVO> add(@Validated(CropDTO.Add.class) @RequestBody CropDTO cropDTO) {
        log.info("调用新增接口, cropName: {}, varietyName: {}", cropDTO.getCropName(), cropDTO.getVarietyName());
        CropVO cropVO = cropService.add(cropDTO);
        return Result.success("新增成功", cropVO);
    }
}

约束:

- **不写业务 if**。Controller 只做三件事:记入参日志、调用 Service、包 `Result`;
- 类上放 `@Validated`(类级校验,配合方法参数上的 `@Min` 等);
- `@RequestBody` 上放 `@Validated(Group.class)`(触发 DTO 分组校验);
- 列表接口用 `@Valid` + 查询 DTO 承接,不要用散装 `@RequestParam` 堆参数;
- 入口日志记"关键业务标识 + 入参",不要把整个对象 toString 打进日志(大对象、敏感字段都不合适)。

## 5. Service 层的写法

```java
@Override
@Transactional(rollbackFor = Exception.class)
public CropVO add(CropDTO cropDTO) {
    log.info("新增农作物品种, cropName: {}", cropDTO.getCropName());
    if (existsSameVariety(cropDTO.getCropName(), cropDTO.getVarietyName(), null)) {
        throw new BusinessException(String.format("品种已存在, cropName: %s, varietyName: %s",
                cropDTO.getCropName(), cropDTO.getVarietyName()));
    }
    Crop crop = toEntity(cropDTO);
    cropMapper.insert(crop);
    log.info("新增农作物品种成功, id: {}, 耗时: {}ms", crop.getId(), System.currentTimeMillis() - start);
    return CropVO.from(crop);
}

约束:

- **返回 VO,不返回 Entity**:Entity 是内部模型,直接返回等于把数据库结构暴露给前端;
- **抛异常,不返回错误码**:业务失败抛 `BusinessException`,由全局处理器统一转换;
- 写操作加 `@Transactional(rollbackFor = Exception.class)`:
  - `rollbackFor = Exception.class` 是必需的,默认只回滚 `RuntimeException`,受检异常不回滚;
  - 事务边界放在**最外层的 public 方法**上,不要放在 private 方法上(自调用不生效);
  - 只读方法**不要**加事务,避免不必要的连接占用;
- 耗时统计用 `System.currentTimeMillis()`,在方法入口取起点、出口打日志。

## 6. Mapper 层

```java
@Mapper
public interface CropMapper extends BaseMapper<Crop> {
}

继承 `BaseMapper` 就有了单表 CRUD、条件构造器、分页能力,**能空就空**。
只有以下情况才写自定义方法:

- 多表 JOIN 且需要定制结果集 → 写 `@Select` 或 XML;
- 复杂聚合统计 → 写 `@Select`;
- 批量自定义 SQL → 走 MP 的批量方法或 XML。

> 别为了"显得专业"在 Mapper 里写只有一行转发到 `BaseMapper` 的方法。

## 7. common 层放什么

| 放 | 不放 |
| --- | --- |
| `Result<T>` 统一响应 | 任何带业务语义的东西(如 `UserResult`) |
| `PageResult<T>` 分页响应 | 业务枚举常量 |
| 与框架无关的小工具 | Entity / DTO / VO |
| 全局常量(限流阈值等) | Service 里的单例状态 |

## 8. 公共模块的抽取时机

公共模块是**耦合之源**,过早抽取一定返工。

| 阶段 | 做法 |
| --- | --- |
| 单体 / 单服务 | 所有横切类放本服务 `common` 包,服务之间不共享 jar |
| 第二个服务出现 | 先复制一份,观察差异 |
| 第三个服务出现,且差异稳定 | 才抽 `mall-common` 模块,只放**几乎不变**的部分:`Result`、`PageResult`、异常体系、MP 配置 |
| 业务相关的公共逻辑 | **不要**放公共模块,放到各自领域内,否则公共模块会变成垃圾场 |

真实案例:`mall-cloud` 把 `Result` / `PageResult` / `BusinessException` / `GlobalExceptionHandler` / `MybatisPlusConfig` 和 Flyway 脚本放进 `mall-common`,各服务只依赖它,不共享业务逻辑 —— 这个边界是对的。

## 9. 多模块形态(需要独立部署时才用)

```
mall-cloud (父 pom,packaging=pom,只做版本与模块编排)
├── mall-common        公共横切层 + Flyway 脚本
├── user-server        独立启动,端口 7001
├── product-server     独立启动
└── order-server       独立启动
```

要点:

- 父 pom 用 `<dependencyManagement>` 统一版本,`<modules>` 编排模块;
- 子模块 pom 只声明 parent + 自己的依赖,不重复写版本号;
- 每个服务有独立的 `Application` 启动类与 `application.yml`;
- 公共模块**不要**写启动类;
- Flyway 脚本放公共模块时,各服务加载同一份脚本会重复执行 —— 按服务拆脚本目录,或按 `spring.flyway.locations` 区分。
