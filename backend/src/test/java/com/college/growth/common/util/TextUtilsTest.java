package com.college.growth.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 文本规范化规则测试（需求文档 4.1.3 通用校验第 1 条）。
 */
class TextUtilsTest {

    @Test
    @DisplayName("去除首尾空格并把连续空格压缩为一个")
    void shouldNormalizeBlankCharacters() {
        assertEquals("张 伟", TextUtils.normalize("  张   伟 "));
        assertEquals("浙江 杭州", TextUtils.normalize("浙江\u3000杭州"));
        assertEquals("a b", TextUtils.normalize("a\t\n b"));
    }

    @Test
    @DisplayName("null 原样返回，不抛异常")
    void shouldKeepNull() {
        assertNull(TextUtils.normalize(null));
    }

    @Test
    @DisplayName("空白判定")
    void shouldDetectBlank() {
        assertTrue(TextUtils.isBlank(null));
        assertTrue(TextUtils.isBlank("   "));
        assertTrue(TextUtils.isBlank(""));
    }
}
