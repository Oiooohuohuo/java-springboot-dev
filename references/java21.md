# Java 21 实用特性

只列**能直接提升本项目代码质量**的特性,按"什么时候会用到"组织。

## 1. switch 表达式(替代 if-else 链)

```java
// 旧写法
if (status == 0) {
    name = "停推";
} else if (status == 1) {
    name = "推广";
} else {
    name = "未知";
}

// 新写法:多值分支 + 箭头语法,不需要 break
String name = switch (status) {
    case 0 -> "停推";
    case 1 -> "推广";
    default -> "未知";
};
```

需要复杂逻辑时用 `yield`:

```java
String name = switch (status) {
    case 0 -> {
        log.info("状态为停推, id: {}", id);
        yield "停推";
    }
    case 1 -> "推广";
    default -> "未知";
};
```

枚举配合 switch 是最佳组合:

```java
String desc = switch (plantStatus) {
    case STOP -> PlantStatus.STOP.getDesc();
    case PROMOTE -> PlantStatus.PROMOTE.getDesc();
};
```

> 但当取值需要"落库 code + 中文描述 + 反查"时,优先用**枚举类**而不是裸 switch,
> 理由见 `dto-entity-vo.md`。switch 适合"一次性映射",枚举适合"跨层复用的取值集合"。

## 2. record:不可变数据载体

```java
/**
 * 分页结果。
 *
 * @param total 总条数
 * @param records 数据列表
 */
public record PageResult<T>(Long total, Long pages, Long current, Long size, List<T> records) {
}

适合:DTO / VO / 不可变返回值。

| 优点 | 注意 |
| --- | --- |
| 自动生成构造器、访问器、equals、hashCode、toString | 没有 setter,Jackson 反序列化需要额外配置或用 `@JsonCreator` |
| 天然不可变,线程安全 | 不能被 MyBatis-Plus 的 `@Builder` 直接使用 |
| 组件访问器是 `total()` 而不是 `getTotal()` | 链式调用风格变了 |

> 本项目实践:Entity / DTO / VO 继续用 **Lombok `@Data` + `@Builder`**,
> 因为 MP 的 `@Builder`、`BeanUtils.copyProperties`、Jackson 都对 POJO 更友好。
> record 用在**内部不可变返回值**上,比如分页元信息、统计结果。

## 3. Stream.toList():不用 Collectors.toList()

```java
List<CropVO> records = page.getRecords().stream()
        .map(CropVO::from)
        .toList();          // 不可变列表

// 需要可变列表时用 Collectors.toCollection(ArrayList::new)
List<CropVO> mutable = page.getRecords().stream()
        .map(CropVO::from)
        .collect(Collectors.toCollection(ArrayList::new));
```

`toList()` 返回**不可变**列表,后续 `add` / `remove` 会抛 `UnsupportedOperationException`。
需要修改就换成 `new ArrayList<>(...)`。

## 4. 集合工厂方法

```java
List<String> list = List.of("a", "b");
Set<String> set = Set.of("a", "b");
Map<String, Integer> map = Map.of("a", 1, "b", 2);

// 允许 null,用 HashMap
Map<String, Object> map = new HashMap<>();
```

| 特性 | 说明 |
| --- | --- |
| `List.of` / `Map.of` **不允许 null 元素** | 传 null 抛 `NullPointerException` |
| 返回的集合**不可变** | 需要修改就 `new ArrayList<>(List.of(...))` |
| `Map.of` 最多支持 10 对键值 | 更多用 `Map.ofEntries(...)` |

## 5. 文本块

```java
String sql = """
        SELECT id, crop_name
        FROM t_crop
        WHERE deleted = 0
        ORDER BY id DESC
        """;
```

用在多行 SQL、常量文案上,比字符串拼接可读得多。

## 6. instanceof 模式匹配

```java
// 旧写法
if (obj instanceof Crop) {
    Crop crop = (Crop) obj;
    ...
}

// 新写法
if (obj instanceof Crop crop) {
    ...
}
```

## 7. 虚拟线程(并发场景的杀手锏)

Java 21 起虚拟线程转正。IO 密集型场景(调用第三方接口、等数据库)用虚拟线程,
可以用几百个并发任务而不需要几万个平台线程。

```java
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportClient reportClient;

    /**
     * 并发拉取多份报表。
     *
     * @param ids 报表ID列表
     * @return 报表列表
     */
    public List<Report> fetchAll(List<Long> ids) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return ids.stream()
                    .map(id -> executor.submit(() -> reportClient.fetch(id)))
                    .map(this::await)
                    .toList();
        }
    }
}
```

要点:

- 虚拟线程**不要池化**,`newVirtualThreadPerTaskExecutor()` 本身就是最佳实践;
- `ThreadLocal` 在虚拟线程下每个线程一份,内存占用会变多;
- 大量 ThreadLocal 反而是瓶颈,考虑改用显式传参;
- 连接池仍然要限制并发(数据库连接数是硬约束)。

## 8. 其它常用小特性

| 特性 | 用法 |
| --- | --- |
| `Optional.isEmpty()` | Java 11+ 就有,比 `!isPresent()` 直观 |
| `Optional.stream()` | 把 Optional 当流用,省掉判空 |
| `Optional.or()` | `opt.or(() -> 备选值)`,替代 `orElseGet` 的 lambda 写法 |
| `SequencedCollection` | `list.getFirst()` / `getLast()` / `reversed()`,Java 21 转正 |
| `String.formatted` | `"id: %d".formatted(id)` |
| `Map.computeIfAbsent` | 构建嵌套 Map 的标准写法 |
| `instanceof` + record 解构 | Java 21 的 `record` 模式仍在预览,慎用 |

## 9. 不要开的东西

- `--enable-preview`:预览特性每个版本都可能变,`switch` 模式匹配在 Java 21 仍是预览,生产项目不要开;
- `Thread` 直接 API:`Thread.ofVirtual().start(...)` 只在极底层场景用,业务代码用 Executor;
- `record` 作为 Entity:MP 的 `@Builder`、`@TableField` 与 record 兼容性差,别硬上。
