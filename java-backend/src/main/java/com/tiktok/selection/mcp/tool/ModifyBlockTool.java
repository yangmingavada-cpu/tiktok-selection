package com.tiktok.selection.mcp.tool;

import com.tiktok.selection.mcp.ChainBuildSession;
import com.tiktok.selection.mcp.McpObservation;
import com.tiktok.selection.engine.annotation.request.LlmCommentAnnotationRequest;
import com.tiktok.selection.engine.compute.request.CustomFormulaComputeRequest;
import com.tiktok.selection.engine.compute.request.GrowthRateComputeRequest;
import com.tiktok.selection.engine.compute.request.ProfitMarginComputeRequest;
import com.tiktok.selection.engine.control.request.PauseWaitRequest;
import com.tiktok.selection.engine.datasource.request.*;
import com.tiktok.selection.engine.enrichment.request.*;
import com.tiktok.selection.engine.schema.SchemaGenerator;
import com.tiktok.selection.engine.score.request.LlmSemanticScoreRequest;
import com.tiktok.selection.engine.score.request.NumericScoreRequest;
import com.tiktok.selection.engine.score.request.ScoreAggregateRequest;
import com.tiktok.selection.engine.transform.request.ConditionalFilterTransformRequest;
import com.tiktok.selection.engine.transform.request.FieldTrimTransformRequest;
import com.tiktok.selection.engine.transform.request.SortTopNTransformRequest;
import com.tiktok.selection.engine.traverse.request.*;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.tiktok.selection.mcp.ToolSchemaHelper.*;

/**
 * modify_block 工具：修改已有积木块配置（增量模式）
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class ModifyBlockTool implements McpTool {

    private static final String BLOCK_PARAM_DOCS = SchemaGenerator.describeBlocks(
            ProductListFilterRequest.class, ProductRanklistRequest.class,
            InfluencerListFilterRequest.class, InfluencerRanklistRequest.class,
            SellerListFilterRequest.class, SellerRanklistRequest.class,
            VideoListFilterRequest.class, VideoRanklistRequest.class,
            TrendingHashtagListRequest.class, KeywordInsightRequest.class,
            InfluencerProductTraverseRequest.class, SellerProductTraverseRequest.class,
            VideoProductTraverseRequest.class, HashtagVideoTraverseRequest.class,
            ProductDetailEnrichmentRequest.class, ProductTrendEnrichmentRequest.class,
            ProductCommentEnrichmentRequest.class,
            InfluencerDetailEnrichmentRequest.class, InfluencerTrendEnrichmentRequest.class,
            ConditionalFilterTransformRequest.class, FieldTrimTransformRequest.class,
            SortTopNTransformRequest.class, PauseWaitRequest.class,
            GrowthRateComputeRequest.class, ProfitMarginComputeRequest.class,
            CustomFormulaComputeRequest.class,
            NumericScoreRequest.class, LlmSemanticScoreRequest.class, ScoreAggregateRequest.class,
            LlmCommentAnnotationRequest.class
    );

    @Override
    public String getToolName() {
        return "modify_block";
    }

    @Override
    public Map<String, Object> buildSchema(ChainBuildSession session) {
        int maxStep = session.getBlocks().size();
        return tool("modify_block",
                "修改已有积木块的配置参数（仅限增量模式）。\n"
                + "可通过 resources/read(chain://state) 查看当前各步骤的blockId和配置，再决定修改哪一步。\n"
                + "modifications中的键值对将与原配置合并（覆盖同名key，不影响未提及的key）。\n\n"
                + "各块类型的可配参数（*=必填，[值1|值2]=枚举，括号内为默认值）：\n"
                + BLOCK_PARAM_DOCS,
                schema(props(
                        propReq("target_step", "integer",
                                "要修改的步骤序号，范围1-" + maxStep + "，参考chain://state中的seq字段"),
                        propReq("modifications", "object",
                                "要修改的配置键值对，如{\"top_n\": 100}或{\"region\": \"US\"}")
                ), List.of("target_step", "modifications")));
    }

    @Override
    public boolean isAvailable(ChainBuildSession session) {
        return session.isIncrementalMode();
    }

    @Override
    @SuppressWarnings("unchecked")
    public McpObservation execute(ChainBuildSession session, Map<String, Object> args) {
        Object targetStepObj = args.get("target_step");
        Object modificationsObj = args.get("modifications");

        if (targetStepObj == null || modificationsObj == null) {
            return McpObservation.builder()
                    .success(false)
                    .error("target_step和modifications均为必填参数")
                    .build();
        }

        int targetStep = targetStepObj instanceof Number n ? n.intValue() : -1;
        Map<String, Object> modifications = modificationsObj instanceof Map<?, ?>
                ? (Map<String, Object>) modificationsObj : new HashMap<>();

        List<Map<String, Object>> blocks = session.getBlocks();
        if (targetStep < 1 || targetStep > blocks.size()) {
            return McpObservation.builder()
                    .success(false)
                    .error("target_step超出范围(1-" + blocks.size() + "): " + targetStep)
                    .build();
        }

        Map<String, Object> targetBlock = blocks.get(targetStep - 1);
        Map<String, Object> config = targetBlock.get("config") instanceof Map<?, ?>
                ? (Map<String, Object>) targetBlock.get("config") : new LinkedHashMap<>();

        // 合并修改
        config.putAll(modifications);
        targetBlock.put("config", config);

        return McpObservation.builder()
                .success(true)
                .message("已修改第" + targetStep + "步(" + targetBlock.get("blockId") + ")的配置")
                .chainLength(blocks.size())
                .dataType(session.getCurrentOutputType())
                .availableFields(session.getAvailableFields())
                .estimatedRows(session.getEstimatedRowCount())
                .scoreFields(session.getScoreFields())
                .hint("配置已修改，若修改影响数据类型或字段，可能需要重新执行")
                .build();
    }
}
