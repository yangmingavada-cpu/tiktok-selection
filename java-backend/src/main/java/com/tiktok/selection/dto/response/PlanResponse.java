package com.tiktok.selection.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户方案响应DTO
 *
 * @author system
 * @date 2026/03/24
 */
@Data
public class PlanResponse {

    private String id;
    private String name;
    private String description;
    private String sourceText;
    private List<Object> blockChain;
    private List<Object> tags;
    private Integer useCount;
    private Boolean publicFlag;
    private LocalDateTime lastUsedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
