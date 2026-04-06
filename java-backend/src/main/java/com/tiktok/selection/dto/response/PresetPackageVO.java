package com.tiktok.selection.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预设套餐视图对象
 *
 * @author system
 * @date 2026/03/24
 */
@Data
@Builder
public class PresetPackageVO {

    private String id;
    private String pkgCode;
    private String nameZh;
    private String nameEn;
    private String description;
    private List<Object> blockChain;
    private List<Object> tags;
    private Integer useCount;
    private Boolean active;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
