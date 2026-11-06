# Flyway 数据库迁移

## 1. 它解决什么问题

没有 Flyway 时,表结构靠"手工执行 sql 文件",结果是:

- 谁改的表、改了什么,只有当事人知道;
- 新人拉下代码,数据库结构和代码对不上,只能互相喊;
- 测试库、正式库、生产库的表结构可能各不相同;
- 想回退一次变更?靠 git 里的 sql 文件手动反着执行。

Flyway 把建表/改表变成**版本化脚本**,启动时自动把数据库推进到代码需要的版本。
`flyway_schema_history` 表记录每个脚本的版本、校验和、执行时间。

## 2. 依赖

```xml
<!-- 版本由 SpringBoot 父工程管理,不要自己写版本号 -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<!-- MySQL 官方支持模块,必须单独引入,否则不支持 MySQL -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

## 3. 脚本位置与命名

```
src/main/resources/db/migration/
├── V1__init_crop.sql          # 建表 + 初始化数据
├── V2__add_crop_index.sql     # 加索引
└── V3__alter_crop_add_region.sql
```

命名规则:`V<版本号>__<下划线分隔的描述>.sql`

- `V` 前缀固定,大写;
- 版本号与描述之间是**两个**下划线;
- 版本号用整数递增(`1,2,3`);需要分支时用点号(`2.1`);
- 描述用英文小写下划线,一眼能看懂改了什么;
- 一个脚本只干一件事(建一张表 / 加一个字段)。

## 4. 配置(非空库必看)

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    # 库非空(已有其它业务表)时,必须开 baseline,否则 Flyway 拒绝在非空 schema 上迁移
    baseline-on-migrate: true
    # 基线版本设为 0,让 V1 脚本能正常执行
    baseline-version: 0
    baseline-description: 初始化空版本基线
    # 禁止 clean:clean 会清空 schema,误操作会连带删掉库里的其它表
    clean-disabled: true
    validate-on-migrate: true
```

| 配置 | 不配会怎样 |
| --- | --- |
| `baseline-on-migrate: true` | 目标库非空且没有基线记录时,启动直接失败(`Found non-empty schema`) |
| `baseline-version: 0` | 基线版本若默认为 1,V1 脚本会被认为"已执行"而跳过 |
| `clean-disabled: true` | 一次 `flyway clean` 就把整个 schema 清空 |
| `validate-on-migrate: true` | 已执行脚本被改动时不会被发现,数据静默错位 |

> `baseline-on-migrate` 和 `baseline-version` **必须成对出现**。只写前者,V1 会被跳过;
> 只写后者,非空库仍然启动失败。这是实战中最常见的 Flyway 问题。

## 5. 版本链

```
schema_version  | description        | success
-------------- | ------------------ | --------
0              | 初始化空版本基线    | true     <- baseline
1              | init crop          | true
2              | add crop index     | true
```

规则:

- 版本号**只增不减**,不回填;
- 已在任何环境执行过的脚本,**内容一字都不能改**(改了就 checksum 对不上);
- 要改结构 → 新增 `V3__xxx.sql`;
- 一次性数据订正用可重复脚本 `R__fix_xxx.sql`(每个环境都会重跑,必须幂等);
- 回滚用补偿脚本(如 `V4__revert_crop_index.sql`),不要靠 `flyway undo`(社区版没有 undo)。

## 6. 改动表结构的正确流程

```sql
-- V3__alter_crop_add_sow_date.sql
ALTER TABLE `t_crop`
    ADD COLUMN `sow_date` DATE NULL COMMENT '播种日期' AFTER `growth_cycle`;

-- 字段改名等破坏性变更,必须分两步:先加新列 -> 刷数据 -> 删旧列
```

破坏性变更清单(生产环境要格外小心):

| 操作 | 风险 | 建议 |
| --- | --- | --- |
| `DROP COLUMN` | 数据不可恢复 | 先备份,分两步删 |
| `RENAME COLUMN` | 应用层旧代码立即报错 | 先加新列,双写一段时间 |
| `MODIFY` 改类型 | 大表锁表 | 低峰执行,先在测试库验证 |
| `NOT NULL` 加约束 | 存量数据不满足直接失败 | 先补默认值再改约束 |
| 加索引 | 大表耗时 | 用 `ALGORITHM=INPLACE, LOCK=NONE` |

## 7. 与 JPA 共存

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate    # 只校验表结构与实体是否一致,绝不自动改表
```

`ddl-auto` 取值:

| 取值 | 行为 | 适用 |
| --- | --- | --- |
| `none` | 完全不管 | 表结构由 Flyway 管的纯 MP 项目 |
| `validate` | 启动时校验实体与表结构,不一致就报错 | **推荐**,表结构交给 Flyway |
| `update` | 自动改表 | 仅本地开发,禁止上生产 |
| `create` / `create-drop` | 每次启动重建 | 仅测试 |

用 JPA 时推荐 `validate`:Flyway 负责演进表结构,JPA 负责校验实体与表是否对得上,
两边各司其职,不会出现"Hibernate 偷偷改了表"的情况。

## 8. 多模块 / 多服务的脚本归属

| 形态 | 脚本放哪 | 说明 |
| --- | --- | --- |
| 单体 | `app/src/main/resources/db/migration` | 一份 |
| 多模块(公共模块 + 各服务) | 按服务各自放 | 公共模块放基础表,服务放自己的表 |
| 公共模块放脚本 | ⚠️ 所有服务加载同一份会重复执行 | 必须用 `spring.flyway.locations` 按服务区分目录 |

推荐做法(公共模块 + 各服务):

```yaml
# user-server
spring:
  flyway:
    locations: classpath:db/migration,classpath:db/migration/user

```

## 9. 常见报错对照表

| 报错 | 原因 | 处理 |
| --- | --- | --- |
| `Found non-empty schema(s) without schema history table` | 库非空且没开 baseline | `baseline-on-migrate: true` + `baseline-version: 0` |
| `Validate failed: migration checksum mismatch` | 改过已执行的脚本 | 用 `flyway repair` 重算 checksum,或把改动还原成新脚本 |
| `Found more than one migration with version X` | 重复版本号 | 重命名脚本 |
| `Detected resolved migration not applied to database` | 脚本新增但未执行 | 正常启动会自动执行;若被忽略查 `spring.flyway.enabled` |
| `Unsupported Database: MySQL 8.x` | 缺 `flyway-mysql` | 补依赖 |
| `Migration checksum failed` 且表已存在 | baseline 版本过高导致脚本被跳过 | 调整 `baseline-version` |

## 10. 使用纪律

1. **脚本一旦提交并被执行过,内容就冻结**;
2. 改表 = 新增脚本,不是编辑旧脚本;
3. 每个脚本写清楚注释:改的什么、为什么改;
4. 脚本必须**幂等或可重复执行**(`CREATE TABLE IF NOT EXISTS` 是个好习惯);
5. 生产环境的迁移在发布窗口执行,先在测试库跑一遍;
6. `clean` 永远不要在生产执行 —— 保持 `clean-disabled: true`;
7. 大表 DDL 先评估锁表时间,必要时用在线 DDL 参数。
