# 端到端示例:农作物品种管理

一条完整链路,从建表到测试。**新项目照着这个顺序写一遍,骨架就立住了。**

## 0. 全景

`
V1__init_crop.sql
    ↓
Crop(Entity) + PlantStatus(枚举)
    ↓
CropDTO / CropQueryDTO  →  CropVO
    ↓
CropMapper(BaseMapper)  →  CropService  →  CropServiceImpl  →  CropController
    ↓
CropServiceImplTest(单元) + CropApiIntegrationTest(集成)
`

---

## 1. Flyway 脚本

`
-- src/main/resources/db/migration/V1__init_crop.sql
CREATE TABLE IF NOT EXISTS `t_crop`
(
    `id`           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `crop_name`    VARCHAR(50)    NOT NULL COMMENT '作物名称',
    `variety_name` VARCHAR(50)    NOT NULL COMMENT '品种名称',
    `crop_type`    VARCHAR(20)    NOT NULL COMMENT '作物类型',
    `suit_area`    VARCHAR(255)   DEFAULT NULL COMMENT '适宜种植区域',
    `growth_cycle` INT            NOT NULL DEFAULT 0 COMMENT '生长周期,单位:天',
    `yield_level`  DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '亩产预估,单位:kg',
    `plant_status` TINYINT        NOT NULL DEFAULT 0 COMMENT '品种推广状态:0 停推 1 推广',
    `deleted`      TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除标记:0 正常 1 已删除',
    `create_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_crop_name` (`crop_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='农作物品种信息表';
`

---

## 2. 枚举

见 `dto-entity-vo.md` 的完整实现,这里只看用法:

`
// Entity 字段直接用枚举,配合 @EnumValue 按 code 落库
/** 品种推广状态:0 停推,1 推广。 */
private PlantStatus plantStatus;
`

---

## 3. Entity

`
package com.hgschool.agri.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 农作物品种实体,对应数据库表 t_crop。
 *
 * <p>与数据库表一一映射,是 MyBatis-Plus 操作的载体,属于内部模型,
 * 不直接返回给前端;对外输出统一使用 CropVO。</p>
 *
 * @author hgschool
 */
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

    /** 作物名称,如"水稻"。 */
    private String cropName;

    /** 品种名称,如"湘早稻9号"。 */
    private String varietyName;

    /** 作物类型:粮食 / 蔬菜 / 水果 / 油料。 */
    private String cropType;

    /** 适宜种植区域。 */
    private String suitArea;

    /** 生长周期,单位:天。 */
    private Integer growthCycle;

    /** 亩产预估,单位:kg。 */
    private BigDecimal yieldLevel;

    /** 品种推广状态:0 停推,1 推广。 */
    private PlantStatus plantStatus;

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
`

---

## 4. DTO 与 QueryDTO

`
package com.hgschool.agri.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 农作物品种新增 / 修改请求 DTO。
 *
 * <p>同一个 DTO 服务新增与修改,通过校验分组区分:
 * 新增走 Add 分组(id 由数据库自增生成),修改走 Update 分组(id 必填)。</p>
 *
 * @author hgschool
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CropDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 新增校验分组:id 由数据库自增生成,不校验。 */
    public interface Add {
    }

    /** 修改校验分组:id 必填。 */
    public interface Update {
    }

    /** 主键ID,新增时为空,修改时必填。 */
    @NotNull(message = "品种ID不能为空", groups = Update.class)
    @Min(value = 1, message = "品种ID必须为正整数", groups = Update.class)
    private Long id;

    /** 作物名称,必填,长度不超过 50。 */
    @NotBlank(message = "作物名称不能为空", groups = {Add.class, Update.class})
    @Size(max = 50, message = "作物名称长度不能超过50", groups = {Add.class, Update.class})
    private String cropName;

    /** 品种名称,必填,长度不超过 50。 */
    @NotBlank(message = "品种名称不能为空", groups = {Add.class, Update.class})
    @Size(max = 50, message = "品种名称长度不能超过50", groups = {Add.class, Update.class})
    private String varietyName;

    /** 作物类型,必填,只能是粮食 / 蔬菜 / 水果 / 油料。 */
    @NotBlank(message = "作物类型不能为空", groups = {Add.class, Update.class})
    @Pattern(regexp = "粮食|蔬菜|水果|油料", message = "作物类型只能是: 粮食/蔬菜/水果/油料",
            groups = {Add.class, Update.class})
    private String cropType;

    /** 适宜种植区域,选填,长度不超过 255。 */
    @Size(max = 255, message = "适宜种植区域长度不能超过255", groups = {Add.class, Update.class})
    private String suitArea;

    /** 生长周期,必填,取值 1~365 天。 */
    @NotNull(message = "生长周期不能为空", groups = {Add.class, Update.class})
    @Min(value = 1, message = "生长周期不能小于1天", groups = {Add.class, Update.class})
    @Max(value = 365, message = "生长周期不能大于365天", groups = {Add.class, Update.class})
    private Integer growthCycle;

    /** 亩产预估,必填,取值 0~100000 kg。 */
    @NotNull(message = "亩产预估不能为空", groups = {Add.class, Update.class})
    @DecimalMin(value = "0", message = "亩产预估不能为负数", groups = {Add.class, Update.class})
    @DecimalMax(value = "100000", message = "亩产预估不能超过100000kg", groups = {Add.class, Update.class})
    private BigDecimal yieldLevel;

    /** 品种推广状态,必填,0 停推 / 1 推广。 */
    @NotNull(message = "推广状态不能为空", groups = {Add.class, Update.class})
    @Min(value = 0, message = "推广状态只能是 0(停推) 或 1(推广)", groups = {Add.class, Update.class})
    @Max(value = 1, message = "推广状态只能是 0(停推) 或 1(推广)", groups = {Add.class, Update.class})
    private Integer plantStatus;
}
`

查询条件 DTO:

`
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CropQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页码,从 1 开始。 */
    @Min(value = 1, message = "页码必须大于0")
    @Builder.Default
    private Integer pageNum = 1;

    /** 每页条数,1~500。 */
    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 500, message = "每页条数不能超过500")
    @Builder.Default
    private Integer pageSize = 10;

    /** 作物名称,模糊匹配。 */
    private String cropName;

    /** 品种名称,模糊匹配。 */
    private String varietyName;

    /** 作物类型,精确匹配。 */
    private String cropType;

    /** 推广状态,精确匹配。 */
    private Integer plantStatus;
}
`

---

## 5. VO(BeanUtils 浅拷贝 + 派生字段)

`
package com.hgschool.agri.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hgschool.agri.entity.Crop;
import com.hgschool.agri.enums.PlantStatus;
import lombok.*;
import org.springframework.beans.BeanUtils;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 农作物品种返回 VO。
 *
 * <p>与 Entity 解耦:不暴露 deleted 等数据库内部字段,
 * 并把推广状态转换为中文描述,前端拿到即可直接展示。</p>
 *
 * @author hgschool
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CropVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID。 */
    private Long id;

    /** 作物名称。 */
    private String cropName;

    /** 品种名称。 */
    private String varietyName;

    /** 作物类型。 */
    private String cropType;

    /** 适宜种植区域。 */
    private String suitArea;

    /** 生长周期,单位:天。 */
    private Integer growthCycle;

    /** 亩产预估,单位:kg。 */
    private BigDecimal yieldLevel;

    /** 品种推广状态:0 停推,1 推广。 */
    private Integer plantStatus;

    /** 品种推广状态中文描述,0 -> 停推,1 -> 推广。 */
    private String plantStatusName;

    /** 创建时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 由实体转换为 VO:先浅拷贝同名属性,再补上派生展示字段。
     *
     * @param crop 农作物品种实体
     * @return 农作物品种返回对象
     */
    public static CropVO from(Crop crop) {
        if (crop == null) {
            return null;
        }
        CropVO cropVO = new CropVO();
        BeanUtils.copyProperties(crop, cropVO);
        PlantStatus status = PlantStatus.of(crop.getPlantStatus() == null ? null : crop.getPlantStatus().getCode());
        cropVO.setPlantStatusName(status == null ? "未知" : status.getDesc());
        return cropVO;
    }
}
`

---

## 6. Mapper

`
package com.hgschool.agri.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hgschool.agri.entity.Crop;
import org.apache.ibatis.annotations.Mapper;

/**
 * 农作物品种 Mapper 接口。
 *
 * @author hgschool
 */
@Mapper
public interface CropMapper extends BaseMapper<Crop> {
}
`

---

## 7. Service

`
package com.hgschool.agri.service;

import com.hgschool.agri.common.PageResult;
import com.hgschool.agri.dto.CropDTO;
import com.hgschool.agri.dto.CropQueryDTO;
import com.hgschool.agri.vo.CropVO;

/**
 * 农作物品种业务接口。
 *
 * @author hgschool
 */
public interface CropService {

    /**
     * 新增农作物品种。
     *
     * @param cropDTO 新增参数
     * @return 新增后的品种信息
     */
    CropVO add(CropDTO cropDTO);

    /**
     * 修改农作物品种。
     *
     * @param cropDTO 修改参数,必须携带 id
     * @return 修改后的品种信息
     */
    CropVO update(CropDTO cropDTO);

    /**
     * 根据主键查询农作物品种。
     *
     * @param id 品种ID
     * @return 品种信息
     */
    CropVO getById(Long id);

    /**
     * 删除农作物品种(逻辑删除)。
     *
     * @param id 品种ID
     */
    void delete(Long id);

    /**
     * 分页动态查询农作物品种。
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    PageResult<CropVO> page(CropQueryDTO queryDTO);
}
`

---

## 8. ServiceImpl(核心:LambdaQueryWrapper + 事务 + 枚举)

`
package com.hgschool.agri.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hgschool.agri.common.PageResult;
import com.hgschool.agri.dto.CropDTO;
import com.hgschool.agri.dto.CropQueryDTO;
import com.hgschool.agri.entity.Crop;
import com.hgschool.agri.enums.PlantStatus;
import com.hgschool.agri.exception.BusinessException;
import com.hgschool.agri.mapper.CropMapper;
import com.hgschool.agri.service.CropService;
import com.hgschool.agri.vo.CropVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 农作物品种业务实现。
 *
 * @author hgschool
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CropServiceImpl implements CropService {

    private final CropMapper cropMapper;

    /**
     * 新增农作物品种。
     *
     * @param cropDTO 新增参数
     * @return 新增后的品种信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CropVO add(CropDTO cropDTO) {
        long start = System.currentTimeMillis();
        log.info("新增农作物品种, cropName: {}, varietyName: {}", cropDTO.getCropName(), cropDTO.getVarietyName());

        if (existsSameVariety(cropDTO.getCropName(), cropDTO.getVarietyName(), null)) {
            throw new BusinessException("品种已存在, cropName: {}, varietyName: {}",
                    cropDTO.getCropName(), cropDTO.getVarietyName());
        }

        Crop crop = toEntity(cropDTO);
        cropMapper.insert(crop);

        log.info("新增农作物品种成功, id: {}, 耗时: {}ms", crop.getId(), System.currentTimeMillis() - start);
        return CropVO.from(crop);
    }

    /**
     * 修改农作物品种。
     *
     * @param cropDTO 修改参数
     * @return 修改后的品种信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CropVO update(CropDTO cropDTO) {
        long start = System.currentTimeMillis();
        Crop exist = cropMapper.selectById(cropDTO.getId());
        if (exist == null) {
            throw new BusinessException("农作物品种不存在, id: {}", cropDTO.getId());
        }
        if (existsSameVariety(cropDTO.getCropName(), cropDTO.getVarietyName(), cropDTO.getId())) {
            throw new BusinessException("品种已存在, cropName: {}, varietyName: {}",
                    cropDTO.getCropName(), cropDTO.getVarietyName());
        }

        Crop crop = toEntity(cropDTO);
        cropMapper.updateById(crop);

        log.info("修改农作物品种成功, id: {}, 耗时: {}ms", cropDTO.getId(), System.currentTimeMillis() - start);
        return CropVO.from(cropMapper.selectById(cropDTO.getId()));
    }

    /**
     * 根据主键查询农作物品种。
     *
     * @param id 品种ID
     * @return 品种信息
     */
    @Override
    public CropVO getById(Long id) {
        Crop crop = cropMapper.selectById(id);
        if (crop == null) {
            throw new BusinessException("农作物品种不存在, id: {}", id);
        }
        return CropVO.from(crop);
    }

    /**
     * 删除农作物品种(逻辑删除)。
     *
     * @param id 品种ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Crop crop = cropMapper.selectById(id);
        if (crop == null) {
            throw new BusinessException("农作物品种不存在, id: {}", id);
        }
        // @TableLogic 会把 delete 转换为 update deleted = 1
        cropMapper.deleteById(id);
        log.info("删除农作物品种成功, id: {}", id);
    }

    /**
     * 分页动态查询农作物品种。
     *
     * <p>查询条件全部选填:传了才拼进 SQL,没传就不拼。</p>
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<CropVO> page(CropQueryDTO queryDTO) {
        int pageNum = queryDTO.getPageNum() == null ? 1 : queryDTO.getPageNum();
        int pageSize = queryDTO.getPageSize() == null ? 10 : queryDTO.getPageSize();
        long start = System.currentTimeMillis();

        LambdaQueryWrapper<Crop> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(queryDTO.getCropName())) {
            wrapper.like(Crop::getCropName, queryDTO.getCropName());
        }
        if (StrUtil.isNotBlank(queryDTO.getVarietyName())) {
            wrapper.like(Crop::getVarietyName, queryDTO.getVarietyName());
        }
        if (StrUtil.isNotBlank(queryDTO.getCropType())) {
            wrapper.eq(Crop::getCropType, queryDTO.getCropType());
        }
        if (queryDTO.getPlantStatus() != null) {
            wrapper.eq(Crop::getPlantStatus, PlantStatus.of(queryDTO.getPlantStatus()));
        }
        wrapper.orderByDesc(Crop::getId);

        IPage<Crop> page = cropMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<CropVO> records = page.getRecords().stream().map(CropVO::from).toList();

        log.info("分页查询农作物品种成功, 总条数: {}, 本页: {}条, 耗时: {}ms",
                page.getTotal(), records.size(), System.currentTimeMillis() - start);

        return PageResult.<CropVO>builder()
                .total(page.getTotal())
                .pages(page.getPages())
                .current(page.getCurrent())
                .size(page.getSize())
                .records(records)
                .build();
    }

    /**
     * 判断同一作物下是否存在同名品种。
     *
     * @param cropName    作物名称
     * @param varietyName 品种名称
     * @param excludeId   需要排除的记录ID(修改场景),可为 null
     * @return 是否已存在
     */
    private boolean existsSameVariety(String cropName, String varietyName, Long excludeId) {
        LambdaQueryWrapper<Crop> wrapper = new LambdaQueryWrapper<Crop>()
                .eq(Crop::getCropName, cropName)
                .eq(Crop::getVarietyName, varietyName);
        if (excludeId != null) {
            wrapper.ne(Crop::getId, excludeId);
        }
        return cropMapper.selectCount(wrapper) > 0;
    }

    /**
     * 把 DTO 转换为实体,只搬运业务字段,主键与时间字段由各自机制负责。
     *
     * @param cropDTO DTO 对象
     * @return 实体对象
     */
    private Crop toEntity(CropDTO cropDTO) {
        return Crop.builder()
                .id(cropDTO.getId())
                .cropName(cropDTO.getCropName())
                .varietyName(cropDTO.getVarietyName())
                .cropType(cropDTO.getCropType())
                .suitArea(cropDTO.getSuitArea())
                .growthCycle(cropDTO.getGrowthCycle())
                .yieldLevel(cropDTO.getYieldLevel())
                .plantStatus(PlantStatus.of(cropDTO.getPlantStatus()))
                .build();
    }
}
`

---

## 9. Controller

`
package com.hgschool.agri.controller;

import com.hgschool.agri.common.PageResult;
import com.hgschool.agri.common.Result;
import com.hgschool.agri.dto.CropDTO;
import com.hgschool.agri.dto.CropQueryDTO;
import com.hgschool.agri.service.CropService;
import com.hgschool.agri.vo.CropVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 农作物品种接口层。
 *
 * <p>只负责参数接收、校验触发与结果封装,业务逻辑全部下沉到 Service。</p>
 *
 * @author hgschool
 */
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
     * @param cropDTO 新增参数
     * @return 新增后的品种信息
     */
    @PostMapping("/add")
    public Result<CropVO> add(@Validated(CropDTO.Add.class) @RequestBody CropDTO cropDTO) {
        log.info("调用新增接口, cropName: {}, varietyName: {}", cropDTO.getCropName(), cropDTO.getVarietyName());
        return Result.success("新增成功", cropService.add(cropDTO));
    }

    /**
     * 修改农作物品种。
     *
     * @param cropDTO 修改参数
     * @return 修改后的品种信息
     */
    @PutMapping("/update")
    public Result<CropVO> update(@Validated(CropDTO.Update.class) @RequestBody CropDTO cropDTO) {
        log.info("调用修改接口, id: {}", cropDTO.getId());
        return Result.success("修改成功", cropService.update(cropDTO));
    }

    /**
     * 根据 id 查询农作物品种。
     *
     * @param id 品种ID
     * @return 品种信息
     */
    @GetMapping("/{id}")
    public Result<CropVO> getById(@PathVariable Long id) {
        log.info("调用详情接口, id: {}", id);
        return Result.success("查询成功", cropService.getById(id));
    }

    /**
     * 删除农作物品种(逻辑删除)。
     *
     * @param id 品种ID
     * @return 统一响应结构
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("调用删除接口, id: {}", id);
        cropService.delete(id);
        return Result.success("删除成功", null);
    }

    /**
     * 分页动态查询农作物品种。
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    @GetMapping("/list")
    public Result<PageResult<CropVO>> list(@Valid CropQueryDTO queryDTO) {
        log.info("调用分页查询接口, pageNum: {}, pageSize: {}", queryDTO.getPageNum(), queryDTO.getPageSize());
        return Result.success("查询成功", cropService.page(queryDTO));
    }
}
`

---

## 10. 单元测试(Mockito)

`
package com.hgschool.agri.service.impl;

import com.hgschool.agri.dto.CropDTO;
import com.hgschool.agri.entity.Crop;
import com.hgschool.agri.exception.BusinessException;
import com.hgschool.agri.mapper.CropMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 农作物品种业务单元测试。
 *
 * @author hgschool
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("农作物品种业务测试")
class CropServiceImplTest {

    @Mock
    private CropMapper cropMapper;

    @InjectMocks
    private CropServiceImpl cropService;

    /**
     * 构造新增用的 DTO。
     *
     * @return 新增参数
     */
    private CropDTO buildDTO() {
        return CropDTO.builder()
                .cropName("水稻")
                .varietyName("湘早稻9号")
                .cropType("粮食")
                .growthCycle(115)
                .yieldLevel(new BigDecimal("620.00"))
                .plantStatus(1)
                .build();
    }

    /**
     * 新增成功时应落库并返回带中文状态名的 VO。
     */
    @Test
    @DisplayName("新增成功")
    void shouldAddSuccessfully() {
        when(cropMapper.selectCount(any())).thenReturn(0L);
        when(cropMapper.insert(any(Crop.class))).thenAnswer(invocation -> {
            Crop crop = invocation.getArgument(0);
            crop.setId(15L);
            return 1;
        });

        CropVO vo = cropService.add(buildDTO());

        assertNotNull(vo);
        assertEquals(15L, vo.getId());
        assertEquals("推广", vo.getPlantStatusName());
        verify(cropMapper, times(1)).insert(any(Crop.class));
    }

    /**
     * 品种重复时应抛业务异常且不落库。
     */
    @Test
    @DisplayName("品种重复被拦截")
    void shouldRejectDuplicatedVariety() {
        when(cropMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cropService.add(buildDTO()));

        assertTrue(exception.getMessage().contains("品种已存在"));
        verify(cropMapper, never()).insert(any(Crop.class));
    }

    /**
     * 查询不存在的记录时应抛业务异常。
     */
    @Test
    @DisplayName("查询不存在的记录")
    void shouldThrowWhenNotFound() {
        when(cropMapper.selectById(13L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cropService.getById(13L));

        assertTrue(exception.getMessage().contains("不存在"));
    }
}
`

---

## 11. 集成测试(真实库 + MockMvc)

`
package com.hgschool.agri;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 农作物品种接口集成测试:加载完整容器,真实连 MySQL,走全链路。
 *
 * <p>类上 @Transactional 保证执行完回滚,不污染数据。</p>
 *
 * @author hgschool
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("农作物品种接口集成测试")
class CropApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 新增接口:参数校验失败应返回 400 与中文提示。
     *
     * @throws Exception 请求异常
     */
    @Test
    @DisplayName("新增参数校验失败返回400")
    void shouldReturn400WhenValidationFailed() throws Exception {
        mockMvc.perform(post("/crop/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"cropName":"","growthCycle":0}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").exists());
    }

    /**
     * 详情接口:查询存在的记录。
     *
     * @throws Exception 请求异常
     */
    @Test
    @DisplayName("按 id 查询详情")
    void shouldGetById() throws Exception {
        mockMvc.perform(get("/crop/{id}", 1))
                .andDo(print())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists());
    }
}
`

> 集成测试依赖真实数据库,CI 上要用 Testcontainers 或固定测试库,
> 不要连开发库。同时记得在测试配置文件里处理好 Flyway 与测试数据清理策略。

---

## 12. 手工验证

`
# 新增
curl -X POST http://localhost:8080/crop/add -H "Content-Type: application/json" -d "{"cropName":"水稻","varietyName":"湘早稻9号","cropType":"粮食","suitArea":"湖南","growthCycle":115,"yieldLevel":620.00,"plantStatus":1}"

# 分页查询(条件全不传,应返回全部第一页)
curl "http://localhost:8080/crop/list?pageNum=1&pageSize=10"

# 按条件查询
curl "http://localhost:8080/crop/list?cropName=水稻&plantStatus=1"

# 校验失败示例
curl -X POST http://localhost:8080/crop/add -H "Content-Type: application/json" -d "{"cropName":"","growthCycle":0}"
`

期望返回:

`
{
  "code": 200,
  "msg": "新增成功",
  "data": {
    "id": 15,
    "cropName": "水稻",
    "plantStatus": 1,
    "plantStatusName": "推广",
    "createTime": "2026-11-06 10:30:00"
  }
}
`
