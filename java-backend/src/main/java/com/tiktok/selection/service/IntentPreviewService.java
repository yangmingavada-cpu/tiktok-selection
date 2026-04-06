package com.tiktok.selection.service;

import com.tiktok.selection.dto.response.IntentPreviewResponse;
import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockExecutorRegistry;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.entity.EchotikApiKey;
import com.tiktok.selection.mcp.FieldDictionary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 积木链数据预览服务
 *
 * <p>在用户确认积木链之前，对第一个 DS 数据源 Block 发起小规模试查（page_size=3），
 * 验证当前筛选条件下 Echotik API 是否能返回数据，提前发现"无数据"问题。
 * 每次预览消耗 1 次 Echotik API 配额。
 *
 * @author system
 * @date 2026/03/24
 */
@Service
public class IntentPreviewService {

    private static final Logger log = LoggerFactory.getLogger(IntentPreviewService.class);

    /** 预览时请求的数据条数，足以判断是否有数据即可 */
    private static final int PREVIEW_PAGE_SIZE = 3;

    private final BlockExecutorRegistry blockExecutorRegistry;
    private final EchotikApiKeyService echotikApiKeyService;

    public IntentPreviewService(BlockExecutorRegistry blockExecutorRegistry,
                                EchotikApiKeyService echotikApiKeyService) {
        this.blockExecutorRegistry = blockExecutorRegistry;
        this.echotikApiKeyService = echotikApiKeyService;
    }

    /**
     * 对积木链中第一个 DS 数据源 Block 发起小规模预览查询。
     *
     * @param blockChain 完整积木链（来自 intent/parse 返回值）
     * @return 预览结果
     */
    @SuppressWarnings("unchecked")
    public IntentPreviewResponse preview(List<Map<String, Object>> blockChain) {
        if (blockChain == null || blockChain.isEmpty()) {
            return skipped("积木链为空，跳过预览");
        }

        // 找第一个数据源 Block
        Map<String, Object> dsBlock = null;
        for (Map<String, Object> block : blockChain) {
            String bid = (String) block.get("blockId");
            boolean isSource = bid != null && FieldDictionary.SOURCE_TYPE_BLOCK_MAP.values()
                    .stream().anyMatch(arr -> arr[0].equals(bid));
            if (isSource) {
                dsBlock = block;
                break;
            }
        }
        if (dsBlock == null) {
            return skipped("积木链中无数据源 Block，跳过预览");
        }

        String blockId = (String) dsBlock.get("blockId");

        // 获取可用密钥
        Optional<EchotikApiKey> keyOpt = echotikApiKeyService.getAvailableKey();
        if (keyOpt.isEmpty()) {
            return skipped("无可用 Echotik API 密钥，跳过预览");
        }
        EchotikApiKey key = keyOpt.get();

        // 复制原始配置，覆盖为小规模预览参数
        Map<String, Object> origConfig = dsBlock.get("config") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : new HashMap<>();
        Map<String, Object> previewConfig = new HashMap<>(origConfig);
        previewConfig.put("pageSize", PREVIEW_PAGE_SIZE);
        previewConfig.put("total_pages", 1);

        // 构建执行上下文
        BlockContext context = new BlockContext();
        context.setBlockId(blockId);
        context.setBlockConfig(previewConfig);
        context.setInputData(new ArrayList<>());
        context.setAvailableFields(new ArrayList<>());
        context.setEchotikKeyId(key.getId());
        context.setEchotikApiKey(echotikApiKeyService.decryptApiKey(key));
        context.setEchotikApiSecret(echotikApiKeyService.decryptApiSecret(key));

        try {
            BlockExecutor executor = blockExecutorRegistry.getExecutor(blockId);
            BlockResult result = executor.execute(context);

            // 扣减配额（无论成功与否均计入 1 次调用）
            echotikApiKeyService.decrementRemainingCalls(key.getId(), 1);

            int count = result.getOutputData() != null ? result.getOutputData().size() : 0;
            log.info("Preview completed: blockId={}, count={}", blockId, count);

            if (count > 0) {
                return IntentPreviewResponse.builder()
                        .hasData(true)
                        .sampleCount(count)
                        .blockId(blockId)
                        .status("ok")
                        .message("找到 " + count + " 条数据，筛选条件有效")
                        .build();
            } else {
                return IntentPreviewResponse.builder()
                        .hasData(false)
                        .sampleCount(0)
                        .blockId(blockId)
                        .status("empty")
                        .message("首页抽样未找到数据（仅验证第1页），完整执行时可能有结果；若持续为空可尝试放宽筛选条件")
                        .build();
            }

        } catch (Exception e) {
            log.warn("Preview execution failed for block {}: {}", blockId, e.getMessage());
            // 仍然扣减配额（API 调用已发出）
            echotikApiKeyService.decrementRemainingCalls(key.getId(), 1);
            return IntentPreviewResponse.builder()
                    .hasData(false)
                    .sampleCount(0)
                    .blockId(blockId)
                    .status("error")
                    .message("数据验证失败: " + e.getMessage())
                    .build();
        }
    }

    private IntentPreviewResponse skipped(String reason) {
        log.info("Preview skipped: {}", reason);
        return IntentPreviewResponse.builder()
                .hasData(false)
                .sampleCount(0)
                .status("skipped")
                .message(reason)
                .build();
    }
}
