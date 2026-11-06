---
name: java-springboot-dev
description: 快速生成 Java 21 + SpringBoot 3.4 + MyBatis-Plus 3.5.9 + Flyway 的后端工程骨架与分层代码。适用于"新建/脚手架一个 Java 项目""按需求搭出 DTO/VO/Entity/Controller/Service/Mapper 分层""补齐统一响应/全局异常/参数校验/Flyway 迁移/LambdaQueryWrapper 查询/枚举与 BeanUtils 拷贝""写 Spring Data JPA 数据层"等任务。纯代码规范类问题(日志格式、异常风格、复用原则)由 develop-norm 负责,本 skill 不重复约束。
metadata:
  short-description: Java 21 + SpringBoot 3.4 项目骨架生成与分层落地
---

# Java SpringBoot 项目骨架生成

面向的任务是:拿到一段业务需求,产出一套**能跑起来、能演进**的 Java 后端工程。

核心方法只有四步:**定基线 → 铺骨架 → 竖切一条链路 → 补横切能力**。
骨架先立住,业务代码才有地方放;先竖切一条完整链路,再横向复制,比先写完所有 Service 再调 Controller 快得多。

> 代码规范类约束(异常风格、日志格式、判空习惯、复用三原则)不在本 skill 重复定义,统一遵守 `develop-norm`。本 skill 只回答"骨架怎么搭、代码落到哪一层"。

---

## 1. 适用与不适用

| 场景 | 用本 skill |
| --- | --- |
| 新建 Java 后端项目 / 作业 / 脚手架 | ✅ |
| 给已有项目补齐缺失分层(DTO / VO / 统一异常 / Flyway) | ✅ |
| 给新需求加一个完整业务模块 | ✅ |
| 已有代码的重构、疑难排查、性能优化 | ❌ 走 `develop-norm` |
| 前端、算法、运维脚本 | ❌ |

---

## 2. 第 0 步:开工前必须定的三件事

这三件事不定,后面每一步都会返工。

| 决定 | 可选项 | 判断依据 | 默认 |
| --- | --- | --- | --- |
| 工程形态 | 单体 / 多模块微服务 | 是否要求各服务独立部署,服务边界是否已清晰 | 单体 |
| 领域划分 | 单域 / 按域分包 / 按模块拆分 | 后续会不会拆服务 | 单域 `com.hgschool.<domain>` |
| 持久层路线 | MyBatis-Plus / Spring Data JPA | 见 §3 决策表 | MyBatis-Plus |

**为什么必须先定**:

- 包结构一旦铺开,再改要动全部分层文件与 import;
- 持久层路线是**不可逆决策**:MP 与 JPA 的实体写法、查询写法、事务边界、批量写法完全不同,中途换等于重写数据层;
- 单体先行:先把一个业务域做扎实(接口 + 库 + 迁移 + 测试),再拆模块。反过来做,联调成本远高于收益。

---

## 3. 版本基线(照抄,不要自己拍)

这套组合已在多个真实项目里跑通,**版本之间有硬性依赖**,随意升级会踩坑。

| 组件 | 版本 | 关键约束 |
| --- | --- | --- |
| JDK | **21** | `--enable-preview` 不要开,record / sealed / 虚拟线程都已转正 |
| Spring Boot | **3.4.5** | 父工程统一管理版本,子模块只写 groupId/artifactId |
| MyBatis-Plus | **3.5.9** | 必须用 `mybatis-plus-spring-boot3-starter`(SpringBoot 3 专用) |
| mybatis-plus-jsqlparser | **3.5.9** | ⚠️ 3.5.9 起 JSqlParser 被拆成独立模块,**不引入会导致启动期 `ClassNotFoundException`,分页插件直接失效** |
| Flyway | Boot 托管(3.4.5 → 10.x) | 必须额外引入 `flyway-mysql`,否则不支持 MySQL |
| Hutool | **5.8.32** | `StrUtil` / `CollUtil` / `BeanUtil` |
| Lombok | **1.18.46** | Boot 3.4.x 默认锁 1.18.36,不兼容较新的 JDK class 文件格式 |
| MySQL 驱动 | Boot 托管 `mysql-connector-j` | `scope=runtime` |
| 测试 | Boot 托管 JUnit 5 + Mockito + Spring Test | surefire 显式固定 `3.5.6` |

完整可复制的 `pom.xml` 见 `assets/templates/pom.xml`。

---

## 4. 第 1 步:铺骨架文件清单

按下面顺序生成,每生成一层就能编译一次。**不要一次铺 20 个文件再统一编译**。

| # | 文件 | 职责 | 生成要点 |
| --- | --- | --- | --- |
| 1 | `pom.xml` | 依赖基线 | 见上表,注意 `jsqlparser` 与 `flyway-mysql` |
| 2 | `application.yml` | 环境配置 | 数据源 / Flyway / MP / 日志,见 `assets/templates/application.yml` |
| 3 | `logback-spring.xml` | 日志落地 | 控制台 + 按天滚动文件 |
| 4 | `XxxApplication.java` | 启动类 | `@SpringBootApplication`,只放启动类,不放业务 |
| 5 | `common/Result.java` | 统一响应 | `code / msg / data` + 三个静态工厂 |
| 6 | `common/PageResult.java` | 分页响应 | 屏蔽 `IPage`,只暴露 total/pages/current/size/records |
| 7 | `exception/BusinessException.java` | 业务异常 | **必须带 varargs 构造**(见下方避坑) |
| 8 | `exception/GlobalExceptionHandler.java` | 全局异常 | 校验失败 / 业务异常 / 系统异常分开处理 |
| 9 | `config/MybatisPlusConfig.java` | 分页插件 | `PaginationInnerInterceptor` + `maxLimit` |
| 10 | `config/MyMetaObjectHandler.java` | 时间自动填充 | `insertFill` / `updateFill` |
| 11 | `entity/Xxx.java` | 数据库映射 | `@TableName` / `@TableId` / `@TableLogic` / `@TableField(fill=...)` |
| 12 | `dto/XxxDTO.java` | 入参模型 | 校验注解 + 分组接口,`Serializable` |
| 13 | `dto/XxxQueryDTO.java` | 查询条件 | 分页参数 + 可选业务条件 |
| 14 | `vo/XxxVO.java` | 出参模型 | 只放对外字段,敏感字段(`password`)绝不出现 |
| 15 | `mapper/XxxMapper.java` | 数据访问 | 继承 `BaseMapper<T>`,能空则空 |
| 16 | `service/XxxService.java` | 业务接口 | 只有方法签名 + javadoc |
| 17 | `service/impl/XxxServiceImpl.java` | 业务实现 | 业务逻辑唯一落点 |
| 18 | `controller/XxxController.java` | 接口层 | 只接参、触发校验、封结果 |
| 19 | `db/migration/V1__init_xxx.sql` | 建表脚本 | Flyway 版本化脚本 |
| 20 | `test/...` | 测试 | 单元测试(Mockito)+ 集成测试(SpringBootTest) |

目录树(以单域 `com.hgschool.agri` 为例):

```
com.hgschool.agri
├── AgriApplication.java
├── common/          Result, PageResult
├── config/          MybatisPlusConfig, MyMetaObjectHandler
├── controller/      CropController
├── dto/             CropDTO, CropQueryDTO
├── entity/          Crop
├── exception/       BusinessException, GlobalExceptionHandler
├── mapper/          CropMapper
├── service/         CropService
│   └── impl/        CropServiceImpl
└── vo/              CropVO
```

**骨架阶段的纪律**:

- 每个 public 类、public 方法都要有 javadoc,写清"用途",不是复制粘贴的类名;
- Entity 只加 MP 注解,不要塞业务校验;校验属于 DTO;
- Controller 里**不允许**出现 `if` 业务判断;
- Service 里**不允许**出现 `HttpServletRequest`、也不返回 `Result`;
- 不写 `System.out.println`,统一 `@Slf4j` 参数化日志。

---

## 5. 第 2 步:竖切一条业务链路

骨架铺完先别铺开做,**挑一个最简单的业务对象**(如"农作物品种")把一条链路打通:

```
Flyway 建表 → Entity → DTO(+校验) → VO → Mapper → Service → Controller → 集成测试
```

顺序有讲究:先写 Flyway 脚本(表结构是地基),再写 Entity(与表结构对齐),再自底向上写。
一条链路跑通后,后续模块就是复制 + 改名。

每层的关键决策见 `references/architecture.md`;完整可运行示例见 `references/examples.md`。

---

## 6. 第 3 步:补齐横切能力

横切能力决定项目"像不像产品"。八件事,逐个确认:

| 横切项 | 落地方式 | 不做的后果 |
| --- | --- | --- |
| 统一响应 | `Result<T>` + `PageResult<T>` | 前端要适配 N 种返回格式 |
| 全局异常 | `@RestControllerAdvice` 统一转 `Result` | 报错信息格式五花八门 |
| 参数校验 | DTO 上的 JSR-380 注解 + 分组接口 | 脏数据进库 |
| 数据库迁移 | Flyway 版本化脚本,禁止改已执行脚本 | 表结构与代码不一致,无法回溯 |
| 分页 | `PaginationInnerInterceptor` + `PageResult` | 一次拉全表,大数据量直接 OOM |
| 逻辑删除 | `@TableLogic` + 全局 `logic-delete-field` | 误删无法恢复 |
| 时间字段 | `MetaObjectHandler` 自动填充 | 手写 `now()` 必漏更新 |
| 日志 | `@Slf4j` + logback,关键节点记入参/耗时 | 出问题无法定位 |

---

## 7. 决策速查表

遇到具体问题时查这里,不要凭感觉写。

| 场景 | 推荐 | 不推荐 | 理由 |
| --- | --- | --- | --- |
| DTO → Entity(写库) | 手写 Builder,字段少 | BeanUtils 拷贝 | 写库要精确控制"写哪些列",显式最安全 |
| Entity → VO(读库) | `BeanUtils.copyProperties` + 手动补派生字段 | 十几行 setter 手搬 | 字段对齐时收益最大;派生字段仍需手写 |
| DTO → VO(透传) | `BeanUtils.copyProperties` | 逐字段 set | 同上 |
| 大量复杂映射 | MapStruct | 手写 / BeanUtils | 编译期生成,改字段编译即报错 |
| 状态/类型这类固定取值 | **枚举类** | `switch` 硬编码 / `String` | 取值集中、可反查、可校验、落库可控 |
| 枚举落库 | `@EnumValue` 标 code | 默认按 `name()` 落库 | name 改名即断数据;code 稳定 |
| 单表查询 | `LambdaQueryWrapper` | 字符串拼 SQL | 编译期校验字段名,重构安全 |
| 动态条件查询 | 条件为空则不拼(`StrUtil.isNotBlank` 判断) | 无条件全拼 | 避免 `LIKE %%` 拖垮索引 |
| 批量写入 | `saveBatch` / `insertIgnoreBatch` | 循环 `insert` | N 次网络往返 |
| 大数据量查询 | 必须分页 / 分批 | `selectList(null)` | OOM |
| 业务失败 | 抛 `BusinessException` | `if` + 返回错误 | 异常统一在全局处理器转换,代码干净 |
| 校验失败提示 | `message` 写人话,多条用 `；` 拼接 | 抛原始字段名 | 前端可直接展示 |
| 新增/修改共用 DTO | 校验分组 `Add` / `Update` | 拆两个 DTO | 字段大多相同,分组更省 |
| 表结构变更 | 新增 Flyway 脚本 | 改已执行脚本 | checksum 校验会直接启动失败 |
| 公共代码复用 | 单体内先放本域 `common`,两个以上服务再抽 `mall-common` | 过早抽公共模块 | 公共模块是耦合之源,过早抽必返工 |
| 持久层选型 | 见下方专项 | MP 与 JPA 混用 | 两套并存 = 两套写法,认知成本翻倍 |

**MP vs JPA 怎么选**:

| 条件 | 选 MyBatis-Plus | 选 Spring Data JPA |
| --- | --- | --- |
| 业务形态 | 以 CRUD + 报表为主 | 复杂聚合、领域对象关系紧密 |
| SQL 可控性 | 要求 SQL 明确可控,便于优化 | 以对象图操作为主 |
| 团队熟悉度 | 熟悉 MyBatis 体系 | 熟悉 JPA/Hibernate |
| 现状 | 已有大量 MP 代码 | 已有 JPA 代码 |
| 默认建议 | ✅ 本 skill 默认路线 | 仅在明确需要脏检查/级联时切换 |

JPA 详细写法见 `references/jpa.md`。**同一个项目里不要同时用两套。**

---

## 8. 自检清单(生成完逐条核对)

```
[ ] 编译通过:mvn -q clean compile
[ ] 启动通过:mvn spring-boot:run 无异常
[ ] Flyway 版本链正确:flyway_schema_history 里版本号连续
[ ] 表结构与 Entity 字段一一对应(驼峰 ↔ 下划线映射正确)
[ ] Controller 无业务 if,Service 无 Result / HttpServletRequest
[ ] 业务异常全部是 BusinessException,message 含上下文参数
[ ] 全局异常处理器覆盖:业务异常 / 参数校验 / 请求体格式 / 缺参 / 兜底
[ ] 所有列表接口分页,且 pageSize 有上限
[ ] 状态/类型取值全部收敛到枚举,没有裸字符串
[ ] 日志参数化,且关键节点记录了耗时
[ ] 单元测试(mock)与集成测试(真实库)都能跑通
[ ] 敏感字段(password 等)不出现在任何 VO
```

---

## 9. 深入资料(按需读)

| 文档 | 内容 | 什么时候读 |
| --- | --- | --- |
| `references/architecture.md` | 分层职责、依赖方向、Entity/DTO/VO 边界、公共模块抽取时机 | 铺骨架、定分层时 |
| `references/mybatis-plus.md` | MP 3.5.9 注解、分页、逻辑删除、自动填充、批量、条件构造器细节 | 写数据层时 |
| `references/dto-entity-vo.md` | 三模型边界、BeanUtils 浅拷贝三种姿势与坑、枚举类完整写法 | 写 DTO/VO/转换时 |
| `references/flyway.md` | 脚本命名、baseline、禁止改历史脚本、常见报错处理 | 动数据库时 |
| `references/jpa.md` | Spring Data JPA 实体、Repository、Specification、投影、N+1 | 选 JPA 时 |
| `references/java21.md` | record / sealed / switch 表达式 / 虚拟线程 / 集合工厂 | 想用新特性时 |
| `references/examples.md` | 一条完整业务链路的全部代码 + 单元测试 + 集成测试 | 想要可复制样板时 |
| `assets/templates/` | pom / yml / logback / 公共类模板(**含 `{{package}}` `{{artifactId}}` 占位符,复制后需替换**) | 搭骨架时 |
