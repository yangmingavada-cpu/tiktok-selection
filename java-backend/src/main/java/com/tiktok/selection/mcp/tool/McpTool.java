package com.tiktok.selection.mcp.tool;

import com.tiktok.selection.mcp.ChainBuildSession;
import com.tiktok.selection.mcp.McpObservation;

import java.util.Map;

/**
 * MCP工具接口——每个工具是自包含模块，定义输入Schema、可用性模型和执行逻辑。
 * 新增工具只需实现此接口并声明为Spring Bean，
 * {@link com.tiktok.selection.mcp.McpDispatcher} 和
 * {@link com.tiktok.selection.mcp.McpToolRegistry} 无需任何修改即可自动识别。
 *
 * @author system
 * @date 2026/03/24
 */
public interface McpTool {

    /**
     * 返回工具唯一名称，对应MCP协议tools/call请求中的name字段。
     */
    String getToolName();

    /**
     * 返回工具所属标签，用于分类管理（如按数据源批量启停）。
     * 默认返回 "custom"，Echotik类工具返回 "echotik"，亚马逊类返回 "amazon"，以此类推。
     */
    default String getTag() {
        return "custom";
    }

    /**
     * 构建工具的JSON Schema（name + description + inputSchema）。
     * 可依赖session状态生成动态enum（如availableFields）。
     *
     * @param session 当前链式构建会话
     * @return MCP tools/list 协议所需的工具描述Map
     */
    Map<String, Object> buildSchema(ChainBuildSession session);

    /**
     * 判断当前会话状态下该工具是否可用。
     * 默认返回true（始终可用），有条件的工具覆盖此方法。
     *
     * @param session 当前链式构建会话
     * @return true 表示工具应出现在tools/list中
     */
    default boolean isAvailable(ChainBuildSession session) {
        return true;
    }

    /**
     * 执行工具调用。
     *
     * @param session 当前链式构建会话
     * @param args    工具参数
     * @return 执行结果观察对象
     */
    McpObservation execute(ChainBuildSession session, Map<String, Object> args);
}
