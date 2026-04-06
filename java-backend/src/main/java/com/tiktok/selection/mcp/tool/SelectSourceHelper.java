package com.tiktok.selection.mcp.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tiktok.selection.entity.EchotikCategory;
import com.tiktok.selection.mapper.EchotikCategoryMapper;
import com.tiktok.selection.mcp.ChainBuildSession;
import com.tiktok.selection.mcp.FieldDictionary;
import com.tiktok.selection.mcp.McpObservation;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据源工具共享工具方法：品类映射、榜单参数计算、Block构建、Session更新。
 * 各 SelectXxxSourceTool 按需调用，不包含 execute() 路由逻辑，
 * 每个工具负责自己的实体参数映射。
 *
 * @author system
 * @date 2026/03/24
 */
@Service
public class SelectSourceHelper {

    private final EchotikCategoryMapper categoryMapper;

    public SelectSourceHelper(EchotikCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    // ==================== 品类映射 ====================

    public record CategoryMappingResult(String categoryId, boolean ambiguous, List<String> candidates) {
        static CategoryMappingResult found(String id) {
            return new CategoryMappingResult(id, false, List.of());
        }
        static CategoryMappingResult ambiguous(List<String> candidates) {
            return new CategoryMappingResult(null, true, candidates);
        }
        static CategoryMappingResult notFound() {
            return new CategoryMappingResult(null, false, List.of());
        }
    }

    /**
     * 将 args 中的 "category" 关键词解析为 categoryId 写入 config。
     *
     * @param configKey 写入 config 的字段名，如 "category_id" 或 "product_category_id"
     * @return 需要向 AI 反馈的 hint（品类正常映射返回 null）
     */
    public String applyCategory(Map<String, Object> config, Map<String, Object> args,
                                 String region, String configKey) {
        String categoryKw = (String) args.get("category");
        if (categoryKw == null) return null;

        CategoryMappingResult cat = mapCategory(categoryKw, region);
        if (cat.categoryId() != null) {
            config.put(configKey, cat.categoryId());
            return null;
        } else if (cat.ambiguous()) {
            return "品类'" + categoryKw + "'匹配到多个结果: " + cat.candidates()
                    + "，请通过resources/read(echotik://categories/" + region.toLowerCase() + ")确认后重新调用";
        } else {
            return "未找到品类'" + categoryKw + "'，已忽略品类限制。"
                    + "可通过echotik://categories/" + region.toLowerCase() + "查看可用品类";
        }
    }

    private CategoryMappingResult mapCategory(String keyword, String region) {
        List<EchotikCategory> exact = categoryMapper.selectList(
                new LambdaQueryWrapper<EchotikCategory>()
                        .eq(EchotikCategory::getRegion, region)
                        .and(w -> w.eq(EchotikCategory::getNameZh, keyword)
                                .or().eq(EchotikCategory::getNameEn, keyword)));
        if (exact.size() == 1) {
            return CategoryMappingResult.found(exact.get(0).getCategoryId());
        }
        List<EchotikCategory> fuzzy = categoryMapper.selectList(
                new LambdaQueryWrapper<EchotikCategory>()
                        .eq(EchotikCategory::getRegion, region)
                        .and(w -> w.like(EchotikCategory::getNameZh, keyword)
                                .or().like(EchotikCategory::getNameEn, keyword))
                        .last("LIMIT 5"));
        if (fuzzy.size() == 1) {
            return CategoryMappingResult.found(fuzzy.get(0).getCategoryId());
        } else if (fuzzy.size() > 1) {
            List<String> candidates = fuzzy.stream()
                    .map(c -> c.getNameZh() + "(" + c.getCategoryId() + ")").toList();
            return CategoryMappingResult.ambiguous(candidates);
        }
        return CategoryMappingResult.notFound();
    }

    // ==================== 榜单参数计算 ====================

    public record RankParams(int rankType, String date) {}

    /**
     * 根据 ranking_period 计算 rank_type（整数）和榜单日期。
     * 日榜=昨天，周榜=上周一，月榜=当月1号（月初8日内取上月1号）。
     */
    public RankParams computeRankParams(String rankingPeriod) {
        return computeRankParams(rankingPeriod, null);
    }

    /**
     * 同上，但支持 customDate 覆盖自动计算的日期（格式 yyyy-MM-dd）。
     * LLM 指定历史榜单日期时使用，避免用 filter 错误模拟历史数据。
     */
    public RankParams computeRankParams(String rankingPeriod, String customDate) {
        int rankType = switch (rankingPeriod != null ? rankingPeriod : "day") {
            case "week"  -> 2;
            case "month" -> 3;
            default      -> 1;
        };
        String date;
        if (customDate != null && !customDate.isBlank()) {
            date = customDate;
        } else {
            LocalDate today = LocalDate.now();
            LocalDate rankDate = switch (rankType) {
                case 2 -> today.getDayOfWeek() == DayOfWeek.MONDAY
                          ? today.minusWeeks(1)
                          : today.with(DayOfWeek.MONDAY);
                case 3 -> today.getDayOfMonth() < 8
                          ? today.minusMonths(1).withDayOfMonth(1)
                          : today.withDayOfMonth(1);
                default -> today.minusDays(1);
            };
            date = rankDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return new RankParams(rankType, date);
    }

    // ==================== Block 构建 ====================

    public Map<String, Object> buildBlock(int seq, String blockId,
                                          Map<String, Object> config, String label) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("seq", seq);
        block.put("blockId", blockId);
        block.put("label", label);
        block.put("config", config);
        return block;
    }

    // ==================== Session 更新 + 观察返回 ====================

    public McpObservation applySessionAndBuild(ChainBuildSession session,
            Map<String, Object> block, String outputType, int estimatedRows,
            String message, String hint) {
        session.getBlocks().add(block);
        session.setSeqCounter((Integer) block.get("seq"));
        session.setHasDataSource(true);
        session.setCurrentOutputType(outputType);
        // 优先用 blockId 精确字段集；getFieldsForBlockId 找不到时返回空列表，再 fallback 到类型级别
        String blockId = (String) block.get("blockId");
        List<String> blockFields = FieldDictionary.getFieldsForBlockId(blockId);
        List<String> fields = blockFields.isEmpty()
                ? new ArrayList<>(FieldDictionary.getFieldsForType(outputType))
                : new ArrayList<>(blockFields);
        session.setAvailableFields(fields);
        session.setEstimatedRowCount(estimatedRows);

        return McpObservation.builder()
                .success(true)
                .message(message)
                .chainLength(session.getBlocks().size())
                .dataType(outputType)
                .availableFields(session.getAvailableFields())
                .estimatedRows(estimatedRows)
                .scoreFields(session.getScoreFields())
                .hint(hint)
                .build();
    }
}
