# java-springboot-dev

> 面向 Codex 的 Java 后端工程骨架生成技能:把一段业务需求,变成一套能跑起来、能演进的分层项目。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 为什么会有这个 Skill

写 Java 作业和练手项目时,真正花时间的不是写业务,而是**反复把同一套工程决策重做一遍**:

- 依赖版本怎么定?MP 3.5.9 要不要额外引 `mybatis-plus-jsqlparser`?Flyway 要不要 `flyway-mysql`?
- 表结构用 Flyway 还是手工建?非空库要不要 `baseline-on-migrate`?`baseline-version` 填几?
- 分层到底怎么切?Entity 能不能直接返回前端?DTO 和 VO 差在哪?
- 状态码这种固定取值,写成 `switch` 硬编码、字符串常量,还是枚举?
- DTO 转 Entity 是手写 Builder 还是 `BeanUtils.copyProperties`?各自的坑是什么?

这些问题有**确定的答案**,但答案散落在几十个文档和无数次报错里。`java-springboot-dev` 把它们收敛成一份**可执行的施工图**。

## 它能做什么

| 场景 | 你得到什么 |
| --- | --- |
| 新建一个 SpringBoot 后端项目 | 完整的依赖基线 + 目录骨架 + 横切能力(统一响应 / 全局异常 / 校验 / 日志 / 迁移 / 分页) |
| 给已有项目补齐缺失分层 | 哪些类该建、放在哪个包、职责边界怎么划 |
| 加一个新业务模块 | 一条竖切链路的完整代码:Flyway → Entity → DTO → VO → Mapper → Service → Controller → 测试 |
| 写数据层 | LambdaQueryWrapper 的正确姿势、分页、逻辑删除、自动填充、批量操作、避坑清单 |
| 用 BeanUtils 拷贝 | 三种转换方式对比、浅拷贝的三个坑、深拷贝字段怎么处理 |
| 用枚举 | 完整枚举类模板 + 落库(code/name)、校验、查询、展示五个使用位置 |
| 选 JPA 还是 MP | 决策表 + 两边的写法对照 + 各自适用场景 |

## 快速开始

### 安装

把整个目录放进 Codex 的技能目录:

`
git clone https://github.com/Oiooohuohuo/java-springboot-dev.git
`

`
# Windows
xcopy /E /I java-springboot-dev "%USERPROFILE%.codexskillsjava-springboot-dev"

# macOS / Linux
cp -r java-springboot-dev ~/.codex/skills/
`

目录结构符合 Codex 技能规范(必须含 `SKILL.md`),安装后重启 Codex 即可被自动发现。

### 使用

在对话里直接说需求,例如:

> 用 $java-springboot-dev 生成一个 SpringBoot 项目,做"农产品溯源"业务,需要用户登录和溯源码查询,MySQL + MyBatis-Plus + Flyway。

技能会按下面的流程执行。

## 工作方式:五步生成法

`
需求 ──▶ ①定形态 ──▶ ②定基线 ──▶ ③铺骨架 ──▶ ④竖切一条链路 ──▶ ⑤补横切能力 ──▶ 可运行项目
`

| 步骤 | 做什么 | 为什么这一步不能跳 |
| --- | --- | --- |
| ① 定形态 | 单体还是微服务?一个领域还是多个?用 MP 还是 JPA? | 包结构一铺开就难改;持久层路线中途换 = 重写数据层 |
| ② 定基线 | 锁定 JDK / SpringBoot / MP / Flyway / Hutool / Lombok 版本 | 这些版本之间有硬性依赖,随意升级必踩坑 |
| ③ 铺骨架 | 按清单生成 20 个左右的类,每层都能编译 | 骨架先立住,业务代码才有地方放 |
| ④ 竖切 | 挑一个最简单的业务对象打通全链路 | 一条链路跑通后,后面就是复制 + 改名 |
| ⑤ 补横切 | 统一响应、全局异常、校验、迁移、分页、逻辑删除、时间填充、日志 | 这一步决定项目"像不像产品" |

## 技术栈基线

这套组合在多个真实项目里跑通过,版本之间有硬性依赖:

| 组件 | 版本 | 关键约束 |
| --- | --- | --- |
| JDK | 21 | `--enable-preview` 不要开 |
| Spring Boot | 3.4.5 | 父工程统一管理版本 |
| MyBatis-Plus | 3.5.9 | 必须用 `mybatis-plus-spring-boot3-starter` |
| mybatis-plus-jsqlparser | 3.5.9 | ⚠️ 3.5.9 起独立模块,不引入分页直接失效 |
| Flyway | Boot 托管 | 必须额外引 `flyway-mysql` |
| Hutool | 5.8.32 | `StrUtil` / `CollUtil` / `BeanUtil` |
| Lombok | 1.18.46 | Boot 3.4.x 默认锁 1.18.36,兼容较新 JDK 不足 |

## 目录结构

### Skill 内部结构

`
java-springboot-dev/
├── SKILL.md                    入口:五步生成法 + 版本基线 + 决策速查表 + 自检清单
├── README.md                   本文件
├── LICENSE                     MIT
├── agents/openai.yaml          Codex UI 元数据
├── references/                 按需加载的深入文档
│   ├── architecture.md         分层职责、依赖方向、模型边界、公共模块抽取时机
│   ├── mybatis-plus.md         MP 3.5.9 注解与 API、分页、逻辑删除、自动填充、批量
│   ├── dto-entity-vo.md        三模型边界 + BeanUtils 浅拷贝 + 枚举类完整写法
│   ├── flyway.md               脚本命名、baseline、禁止改历史脚本、报错对照表
│   ├── jpa.md                  Spring Data JPA 写法 + 与 MP 的选型对照
│   ├── java21.md               record / sealed / switch 表达式 / 虚拟线程
│   └── examples.md             端到端完整示例 + 单元测试 + 集成测试
└── assets/templates/           可直接复制的文件
    ├── pom.xml
    ├── application.yml
    ├── logback-spring.xml
    └── java/                   Result / PageResult / BusinessException /
                               GlobalExceptionHandler / MybatisPlusConfig /
                               MyMetaObjectHandler / PlantStatus
`

### 模板占位符

`assets/templates/` 下的文件是**骨架模板**,不是可直接编译的成品。复制到新项目后,按下表替换:

| 占位符 | 含义 | 示例 |
| --- | --- | --- |
| `{{package}}` | 根包名(不含子包),所有 Java 文件的 package 声明都基于它 | `com.example.agri` |
| `{{artifactId}}` | Maven 构件名,同时用于 `pom.xml` 与 `application.yml` 的应用名 | `agri-crop` |

典型用法:

```bash
# 复制模板到自己的项目
cp -r assets/templates/java  src/main/java/com/example/agri
cp assets/templates/pom.xml     pom.xml
cp assets/templates/application.yml src/main/resources/

# 批量替换占位符
grep -rl "{{package}}" src/ | xargs sed -i "s/{{package}}/com.example.agri/g"
```

> 模板里的 Java 文件带 `{{package}}` 占位符,**不是合法 Java**,替换前无法编译。
> 这是有意为之:避免误以为模板可以直接复制进项目就跑。

---

### 生成的项目骨架

`
com.hgschool.<domain>
├── XxxApplication.java
├── common/          Result, PageResult
├── config/          MybatisPlusConfig, MyMetaObjectHandler
├── controller/      XxxController
├── dto/             XxxDTO, XxxQueryDTO
├── entity/          Xxx
├── exception/       BusinessException, GlobalExceptionHandler
├── mapper/          XxxMapper
├── service/         XxxService
│   └── impl/        XxxServiceImpl
└── vo/              XxxVO
`

## 设计理念

**1. 决策优先于规则。**
与其说"必须用 LambdaQueryWrapper",不如给出"什么场景用什么、为什么、代价是什么"的对照表。
规则会被机械执行,决策会被理解。

**2. 给出反例。**
每个"推荐做法"都配一个真实的反面写法(比如 `new BusinessException("品种已存在, username: {}")` 在没有 varargs 构造的异常类里会把 `{}` 原样输出给前端)。
只说"应该怎样"容易被忽略,说清"别那样"更有效。

**3. 版本是契约,不是建议。**
每处依赖都写清"为什么是这个版本"和"配错会报什么错"(`ClassNotFoundException` / `Found non-empty schema`),
让踩坑变成可检索的知识而不是玄学。

**4. 骨架与业务分离。**
骨架负责结构,业务负责逻辑。文档里给出的是"生成顺序"而不是"代码全集",
使用者按需取用,而不是照抄一整个项目。

**5. 单一事实来源。**
枚举只定义一次、错误码只定义一次、依赖只声明一次。
所有重复都会在下次改动时变成不一致。

## 与其它 Skill 的关系

| Skill | 职责 | 关系 |
| --- | --- | --- |
| `develop-norm` | 始终生效的 Java 开发规范(先阐述后执行、异常风格、日志规范、复用三原则) | 本 skill **不重复**规范类约束,专注工程骨架与分层落地 |
| `java-springboot` | SpringBoot 通用最佳实践(教科书式) | 本 skill 是它的**实战版**:带版本锁定、避坑清单和可直接复制的模板 |

## 内容索引

| 文档 | 内容 | 什么时候读 |
| --- | --- | --- |
| `SKILL.md` | 五步生成法、版本基线、决策速查表、自检清单 | 每次开工 |
| `references/architecture.md` | 分层职责、依赖方向、模型边界、common 抽取时机 | 铺骨架时 |
| `references/mybatis-plus.md` | MP 注解、LambdaQueryWrapper、分页、逻辑删除、自动填充 | 写数据层时 |
| `references/dto-entity-vo.md` | BeanUtils 浅拷贝三坑、枚举类五个使用位置 | 写模型转换时 |
| `references/flyway.md` | 脚本命名、baseline、报错对照表 | 动数据库时 |
| `references/jpa.md` | JPA 实体/Repository/Specification/N+1 | 选 JPA 时 |
| `references/java21.md` | record、switch 表达式、虚拟线程 | 想用新特性时 |
| `references/examples.md` | 一条完整链路的全部代码 + 测试 | 想要可复制样板时 |

## 实战来源

本 skill 的骨架与写法来自一套连续演进的教学作业:

- **用户注册登录**(SpringBoot + MyBatis-Plus):构造器注入、`PasswordEncoder` 抽象 + `@ConditionalOnProperty` 多实现策略、统一响应与全局异常;
- **农作物品种管理**(SpringBoot + MP + Flyway):Flyway 版本化迁移、`baseline-on-migrate` + `baseline-version: 0` + `clean-disabled`、分页插件、逻辑删除、时间自动填充、DTO 校验分组;
- **电商微服务骨架**(SpringBoot + SpringCloud + 多模块):`mall-common` 公共层抽取的边界判断。

这些是**来源**,不是**限制**。所有内容都已经抽象成通用写法,换一个领域同样适用。

## Roadmap

- [ ] 补充 Spring Security + JWT 认证授权的骨架模板
- [ ] 补充 Redis 缓存与分布式锁在分层中的落位
- [ ] 补充消息队列(异步解耦)场景的 Service 设计范式
- [ ] 补充 Docker Compose 一键起 MySQL/Redis 的开发环境模板
- [ ] 补充 Testcontainers 集成测试模板,替代连真实开发库
- [ ] 补充前端联调需要的 OpenAPI(SpringDoc)配置

## 贡献

欢迎提 Issue 和 PR:

- **规范类调整**(比如版本升级、枚举模板变更):改 `SKILL.md` 与对应 `references/`;
- **新场景补充**(比如加了 Security 模块):新增 `references/` 文件,并在 `SKILL.md` 的路由表里登记;
- **实战踩坑**:最欢迎的贡献形式 —— 把"报错信息 + 根因 + 正确写法"补进对应文档的避坑表。

## License

[MIT](LICENSE)
