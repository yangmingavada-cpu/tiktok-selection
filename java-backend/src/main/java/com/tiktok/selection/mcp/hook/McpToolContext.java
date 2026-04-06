package com.tiktok.selection.mcp.hook;

import com.tiktok.selection.mcp.ChainBuildSession;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP 工具调用上下文，在 Pre→Execute→Post 整个链路中传递。
 *
 * <p>{@code attributes} 用于 PreHook 向 PostHook 传递任意数据（如计时起点、审计 ID 等），
 * 无需侵入业务参数。
 */
public class McpToolContext {

    private final String toolName;
    private final String toolTag;
    private final String userId;
    private final String sessionId;
    private final Map<String, Object> arguments;
    private final ChainBuildSession session;

    /** Pre→Post 透传属性袋 */
    private final Map<String, Object> attributes = new HashMap<>();

    private McpToolContext(Builder builder) {
        this.toolName  = builder.toolName;
        this.toolTag   = builder.toolTag;
        this.userId    = builder.userId;
        this.sessionId = builder.sessionId;
        this.arguments = builder.arguments;
        this.session   = builder.session;
    }

    public String getToolName()             { return toolName; }
    public String getToolTag()              { return toolTag; }
    public String getUserId()               { return userId; }
    public String getSessionId()            { return sessionId; }
    public Map<String, Object> getArguments() { return arguments; }
    public ChainBuildSession getSession()   { return session; }
    public Map<String, Object> getAttributes() { return attributes; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String toolName;
        private String toolTag;
        private String userId;
        private String sessionId;
        private Map<String, Object> arguments;
        private ChainBuildSession session;

        public Builder toolName(String v)              { this.toolName  = v; return this; }
        public Builder toolTag(String v)               { this.toolTag   = v; return this; }
        public Builder userId(String v)                { this.userId    = v; return this; }
        public Builder sessionId(String v)             { this.sessionId = v; return this; }
        public Builder arguments(Map<String, Object> v){ this.arguments = v; return this; }
        public Builder session(ChainBuildSession v)    { this.session   = v; return this; }

        public McpToolContext build() { return new McpToolContext(this); }
    }
}
