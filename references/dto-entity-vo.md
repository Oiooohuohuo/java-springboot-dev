# DTO / Entity / VO 与转换策略

## 1. 三个模型的分工

| 模型 | 面向 | 生命周期 | 关键约束 |
| --- | --- | --- | --- |
| Entity | 数据库表 | 持久化过程中 | 带 MP 注解;不能直接返回前端 |
| DTO | 接口入参 | 请求期间 | 带校验注解;不带数据库注解 |
| VO | 接口出参 | 响应期间 | 只含对外字段;敏感字段绝不出现 |

分层边界详见 `architecture.md`,这里只讲**怎么在三者之间搬数据**。

## 2. 三种转换方式,先选对再动手

| 方式 | 写法 | 适合 | 代价 |
| --- | --- | --- | --- |
| 手写 Builder | `Crop.builder().cropName(dto.getCropName())...` | DTO → Entity(写库) | 字段多时啰嗦,但字段错写会编译报错 |
| `BeanUtils.copyProperties` | 一行拷贝同名属性 | Entity → VO(读库)、DTO → VO | 字段名错位时**静默不拷贝** |
| MapStruct | 注解 + 编译期生成 | 映射关系复杂、字段多、长期演进 | 多一个依赖与注解体系 |

**默认选择**:

- 写库方向(DTO → Entity):**手写 Builder**。写库要精确控制"写哪几列",`id`、时间、审计字段不能被前端覆盖,显式最安全;
- 读库方向(Entity → VO):**`BeanUtils.copyProperties` + 手动补派生字段**。VO 字段与 Entity 基本对齐时收益最大;
- 字段超过 15 个或存在多种类型转换:上 MapStruct。

## 3. BeanUtils.copyProperties 的正确用法与三个坑

```java
import org.springframework.beans.BeanUtils;

/**
 * 由实体转换为 VO:先整体浅拷贝,再补上 VO 独有的展示字段。
 *
 * @param crop 农作物品种实体
 * @return 农作物品种返回对象
 */
public static CropVO from(Crop crop) {
    if (crop == null) {
        return null;
    }
    CropVO cropVO = new CropVO();
    // 浅拷贝:同名同类型的属性一次性搬过去
    BeanUtils.copyProperties(crop, cropVO);
    // 派生字段(枚举中文名)无法自动拷贝,显式补
    PlantStatus status = PlantStatus.of(crop.getPlantStatus());
    cropVO.setPlantStatusName(status == null ? "未知" : status.getDesc());
    return cropVO;
}

### 坑 1:null 会覆盖目标已有值

源属性为 `null` 时,目标同名字段会被**清成 null**,而不是"跳过"。
目标对象如果已经预置了默认值,拷贝完就没了。

### 坑 2:浅拷贝,引用类型字段共享同一个引用

`List` / `Map` / 自定义对象字段复制的是**引用**。

```java
// Entity 里有个 tags: List<String>
BeanUtils.copyProperties(entity, vo);
vo.getTags().add("新标签");   // ← entity.getTags() 也被改了
```

需要独立副本时,拷贝后手动处理:

```java
if (CollUtil.isNotEmpty(crop.getTags())) {
    cropVO.setTags(new ArrayList<>(crop.getTags()));
}

> `CollUtil.isNotEmpty` 是 Hutool 习惯用法,别写 `tags != null && !tags.isEmpty()`。

### 坑 3:字段名错位是静默失败

- 源有 `userName`、目标有 `username` → **不拷贝,不报错**,字段静默丢失;
- 目标没有 setter 的属性 → 静默忽略;
- 源属性类型与目标 setter 参数类型不兼容 → 抛 `IllegalArgumentException`(参数类型不匹配)。

**规避办法**:拷贝完成后,断言关键字段一定有值。

```java
BeanUtils.copyProperties(crop, cropVO);
if (cropVO.getCropName() == null) {
    throw new IllegalStateException("CropVO 字段名与 Crop 不一致, 检查 copyProperties 是否生效");
}

### 排除字段

```java
// Spring:排除指定属性
BeanUtils.copyProperties(crop, cropVO, "deleted");

// Hutool:更宽容,类型不同会尝试转换,并支持排除与忽略错误
BeanUtil.copyProperties(crop, cropVO, CopyOptions.create()
        .setIgnoreProperties("deleted")
        .setIgnoreError(true));
```

| 维度 | Spring `BeanUtils` | Hutool `BeanUtil` |
| --- | --- | --- |
| 类型不兼容 | 抛 `IllegalArgumentException` | 尝试转换,失败按 `ignoreError` 处理 |
| 排除属性 | `copyProperties(src, tgt, "a", "b")` | `CopyOptions.setIgnoreProperties` |
| 忽略 null 源 | 不支持(会覆盖) | `CopyOptions.setIgnoreNullValue(true)` |
| 深拷贝 | 不支持 | `CopyOptions.setDeepCopy(true)` |

## 4. 枚举类:把固定取值收进类型

固定取值(状态、类型、来源)最容易被写成魔法值:

```java
// 反面教材:魔法值散落各处,改了要全项目搜
if (crop.getPlantStatus() == 1) { ... }
crop.setCropType("粮食");
```

收敛成枚举后,取值集合只有一处定义,可反查、可校验、可落库:

```java
package com.hgschool.agri.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;

/**
 * 品种推广状态。
 *
 * <p>落库值为 {@code code}(与数据库 tinyint 对应),展示值为 {@code desc}。
 * {@code @EnumValue} 标注的字段是唯一落库依据,</p>
 *
 * @author hgschool
 */
@Getter
public enum PlantStatus {

    /** 停推。 */
    STOP(0, "停推"),

    /** 推广。 */
    PROMOTE(1, "推广");

    /** 落库值:与数据库 tinyint 一致,改动会导致历史数据错位。 */
    @EnumValue
    private final Integer code;

    /** 中文描述:直接给前端展示。 */
    @JsonValue
    private final String desc;

    /**
     * 构造推广状态。
     *
     * @param code 落库值
     * @param desc 中文描述
     */
    PlantStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按落库值反查枚举。
     *
     * @param code 落库值
     * @return 对应枚举,未匹配返回 null
     */
    public static PlantStatus of(Integer code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}

### 枚举的五个使用位置

**1) Entity 字段(落库按 code)**

```java
/** 品种推广状态:0 停推,1 推广。 */
@TableField("plant_status")
private PlantStatus plantStatus;
```

MyBatis-Plus 3.5.2 起默认注册 `CompositeEnumTypeHandler`:
字段带 `@EnumValue` → 按 `code` 落库;不带 → 按 `name()` 落库。
**一定要加 `@EnumValue`**,否则枚举常量改名 = 历史数据全部失效。

**2) LambdaQueryWrapper 条件**

```java
// 实体字段是枚举类型时,直接传枚举
wrapper.eq(Crop::getPlantStatus, PlantStatus.PROMOTE);
// 实体字段是 Integer 时,传 code
wrapper.eq(Crop::getPlantStatus, PlantStatus.PROMOTE.getCode());
```

**3) DTO 校验(用枚举替掉正则)**

```java
/** 作物类型:粮食 / 蔬菜 / 水果 / 油料。 */
@NotNull(message = "作物类型不能为空", groups = {Add.class, Update.class})
private CropType cropType;
```

DTO 用枚举字段后,Jackson 自动完成字符串 → 枚举转换;
传了非法值会抛 `HttpMessageNotReadableException`,由全局处理器统一返回 400。
相比 `@Pattern(regexp = "粮食|蔬菜|水果|油料")`,枚举不需要重复维护取值清单,新增类型只改一处。

若前端必须传数字 code,DTO 就用 `Integer` + `@Min/@Max`,在 Service 里用 `CropType.of(code)` 转换并判空。

**4) VO 展示名**

```java
/** 品种推广状态中文描述。 */
private String plantStatusName;

public static CropVO from(Crop crop) {
    CropVO cropVO = new CropVO();
    BeanUtils.copyProperties(crop, cropVO);
    PlantStatus status = PlantStatus.of(crop.getPlantStatus() == null ? null : crop.getPlantStatus().getCode());
    cropVO.setPlantStatusName(status == null ? "未知" : status.getDesc());
    return cropVO;
}

**5) 业务分支**

```java
// 原来:散落的 if (status == 1)
// 现在:类型自解释
if (PlantStatus.STOP.equals(crop.getPlantStatus())) {
    throw new BusinessException("停推品种不允许入库, id: " + crop.getId());
}
```

### 枚举的注意事项

- `of(code)` 未匹配时返回 `null`,调用方必须处理,别直接 `.getDesc()` 触发 NPE;
- `@JsonValue` 标在 `desc` 上 → **出参是中文**;此时前端若要回传 code,入参 DTO 就不能用同一个枚举字段。
  常见做法:DTO 用枚举(接收英文名)或 Integer(接收 code),VO 用 `@JsonValue` 输出 desc;
- 枚举常量名一旦上线就是数据库契约,改名字要走数据订正流程;
- 枚举类不要放业务逻辑,只放"取值 + 描述 + 反查"。复杂判断写回 Service。

## 5. 校验分组:一个 DTO 服务新增与修改

新增时 `id` 由数据库自增(前端不传),修改时 `id` 必填。直接写 `@NotNull` 会让新增永远失败,不写又会让修改漏校验。
用分组接口精确区分:

```java
public class CropDTO implements Serializable {

    /** 新增校验分组:id 由数据库自增生成,不校验。 */
    public interface Add {
    }

    /** 修改校验分组:id 必填。 */
    public interface Update {
    }

    @NotNull(message = "品种ID不能为空", groups = Update.class)
    @Min(value = 1, message = "品种ID必须为正整数", groups = Update.class)
    private Long id;

    @NotBlank(message = "作物名称不能为空", groups = {Add.class, Update.class})
    @Size(max = 50, message = "作物名称长度不能超过50", groups = {Add.class, Update.class})
    private String cropName;
}

```java
// Controller:分组决定校验哪些规则
public Result<CropVO> add(@Validated(CropDTO.Add.class) @RequestBody CropDTO cropDTO) { ... }
public Result<CropVO> update(@Validated(CropDTO.Update.class) @RequestBody CropDTO cropDTO) { ... }
```

注意:加了 `groups` 后,**不在任何分组里的规则默认不生效**。
公共规则要写 `groups = {Add.class, Update.class}`,漏写就是静默失效。

## 6. 敏感字段

Entity 里可以有 `password`,VO 里**绝不能有**。
如果某个字段既要在 Entity 保留、又不能出现在 VO,除了不建同名 VO 字段,还可以:

```java
@JsonIgnore                       // Jackson 序列化时忽略
@JsonProperty(access = Access.WRITE_ONLY)   // 只允许反序列化(入参可写、出参不输出)
```

本项目更推荐的做法:VO 字段清单人工核对,不依赖注解兜底 —— 注解兜底容易在改字段时被顺手删掉。
