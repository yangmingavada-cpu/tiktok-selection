package com.tiktok.selection.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户方案创建/更新请求DTO
 *
 * @author system
 * @date 2026/03/24
 */
@Data
public class PlanCreateRequest {

    @NotBlank(message = "方案名称不能为空")
    @Size(max = 100, message = "方案名称不超过100字")
    private String name;

    @Size(max = 500, message = "描述不超过500字")
    private String description;

    private String sourceText;

    private List<Object> blockChain;

    private List<Object> tags;
}
