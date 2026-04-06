package com.tiktok.selection.mcp;

import com.tiktok.selection.mcp.tool.McpTool;
import com.tiktok.selection.service.McpToolConfigService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具注册表。
 * 根据 ChainBuildSession 当前状态，动态构建可用工具列表。
 * <p>
 * 每个工具自己负责声明：
 * <ul>
 *   <li>{@link McpTool#isAvailable(ChainBuildSession)} — 在什么状态下可见</li>
 *   <li>{@link McpTool#buildSchema(ChainBuildSession)} — 输入 Schema（含动态 enum）</li>
 * </ul>
 * 新增工具无需修改本类，只需实现 {@link McpTool} 接口并声明为 Spring Bean。
 */
@Component
public class McpToolRegistry {

    private final List<McpTool> tools;
    private final McpToolConfigService toolConfigService;

    public McpToolRegistry(List<McpTool> tools, McpToolConfigService toolConfigService) {
        this.tools = tools;
        this.toolConfigService = toolConfigService;
    }

    public List<Map<String, Object>> listTools(ChainBuildSession session) {
        return tools.stream()
                .filter(t -> toolConfigService.isToolEnabled(t.getToolName()))
                .filter(t -> t.isAvailable(session))
                .map(t -> t.buildSchema(session))
                .toList();
    }
}
