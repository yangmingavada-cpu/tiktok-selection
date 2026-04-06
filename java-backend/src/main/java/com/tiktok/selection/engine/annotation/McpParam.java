package com.tiktok.selection.engine.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 BlockRequest 类中的可配参数字段。
 * SchemaGenerator 通过反射读取此注解，自动生成字段级 JSON Schema。
 *
 * <p>字段命名规范：使用 snake_case 作为主名称（与 API 字段对齐），
 * 通过 {@code @JsonAlias} 添加 camelCase 兼容别名。
 *
 * @author system
 * @date 2026/03/26
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpParam {

    /** 字段说明，供 LLM 理解参数含义 */
    String desc();

    /** 是否必填，默认 false */
    boolean required() default false;

    /**
     * JSON Schema 类型，支持 "string"、"integer"、"number"、"boolean"、"array"。
     * 默认 "string"。
     */
    String type() default "string";

    /** 枚举值列表，非空时在 Schema 中生成 "enum" 字段 */
    String[] enumValues() default {};

    /** 默认值说明（文字描述，不做类型转换） */
    String defaultValue() default "";

    /** 示例值，供文档展示 */
    String example() default "";
}
