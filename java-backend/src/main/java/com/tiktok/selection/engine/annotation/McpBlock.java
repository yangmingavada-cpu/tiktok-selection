package com.tiktok.selection.engine.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注一个 BlockRequest 类对应的积木块元信息。
 * SchemaGenerator 通过反射读取此注解，自动生成 JSON Schema 和文档。
 *
 * @author system
 * @date 2026/03/26
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpBlock {

    /** 块标识，如 "SOURCE_PRODUCT_LIST"、"FILTER_CONDITION" */
    String blockId();

    /** Echotik API 端点路径，如 "product/list"；纯计算块为空字符串 */
    String endpoint() default "";

    /** 输出数据类型，如 "product_list"、"influencer_list" */
    String outputType() default "";

    /** 块的简短功能说明 */
    String description() default "";
}
