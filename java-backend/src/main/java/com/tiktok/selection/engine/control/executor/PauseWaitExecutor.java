package com.tiktok.selection.engine.control.executor;

import com.tiktok.selection.common.BlockSecurityUtil;
import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.control.request.PauseWaitRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PauseWaitExecutor implements BlockExecutor {

    private static final McpBlock BLOCK_META = PauseWaitRequest.class.getAnnotation(McpBlock.class);

    @Override
    public String getBlockId() {
        return BLOCK_META.blockId();
    }

    @Override
    public BlockResult execute(BlockContext context) {
        List<Map<String, Object>> inputData = context.getInputData();
        PauseWaitRequest req = PauseWaitRequest.from(context.getBlockConfig());
        String pauseMessage = BlockSecurityUtil.sanitizeMessage(
            req.pause_message, "执行已暂停，请查看数据后继续");

        BlockResult result = BlockResult.success(
            inputData,
            context.getAvailableFields(),
            context.getCurrentOutputType(),
            inputData != null ? inputData.size() : 0,
            inputData != null ? inputData.size() : 0,
            0L
        );
        result.setStatus("paused");
        result.setErrorMessage(pauseMessage);
        return result;
    }
}
