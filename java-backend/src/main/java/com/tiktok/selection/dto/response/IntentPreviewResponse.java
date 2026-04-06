package com.tiktok.selection.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 积木链数据预览响应 DTO
 *
 * @author system
 * @date 2026/03/24
 */
@Data
@Builder
public class IntentPreviewResponse {

    /** 数据源是否有数据 */
    private boolean hasData;

    /** 预览到的数据条数 */
    private int sampleCount;

    /** 被验证的 Block ID（如 DS01） */
    private String blockId;

    /**
     * 预览状态：
     * <ul>
     *   <li>ok      — 有数据，筛选条件有效
     *   <li>empty   — 无数据，建议调整条件
     *   <li>skipped — 无 DS Block 或无可用密钥，跳过
     *   <li>error   — API 调用失败
     * </ul>
     */
    private String status;

    /** 展示给用户的描述 */
    private String message;
}
