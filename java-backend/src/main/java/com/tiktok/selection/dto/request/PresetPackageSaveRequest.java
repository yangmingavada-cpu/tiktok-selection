package com.tiktok.selection.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * 管理员创建/更新预设套餐请求体
 *
 * @author system
 * @date 2026/03/24
 */
@Data
public class PresetPackageSaveRequest {

    @NotBlank(message = "套餐代码不能为空")
    @Size(max = 64, message = "套餐代码最长64位")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "套餐代码只允许大写字母、数字和下划线，且以大写字母开头")
    private String pkgCode;

    @NotBlank(message = "中文名称不能为空")
    @Size(max = 128, message = "中文名称最长128位")
    private String nameZh;

    @NotBlank(message = "英文名称不能为空")
    @Size(max = 128, message = "英文名称最长128位")
    private String nameEn;

    @Size(max = 500, message = "描述最长500字")
    private String description;

    /** 积木链定义（JSON数组），最多200个积木块 */
    @NotNull(message = "积木链不能为null")
    private List<Object> blockChain;

    /** 标签列表，最多20个 */
    private List<Object> tags;

    @Min(value = 0, message = "排序值不能为负数")
    private Integer sortOrder;

    private Boolean active;
}
