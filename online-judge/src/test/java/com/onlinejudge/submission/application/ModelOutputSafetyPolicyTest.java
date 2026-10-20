package com.onlinejudge.submission.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelOutputSafetyPolicyTest {

    @Test
    void allowsNormalAlgorithmDiagnosisVocabulary() {
        assertThat(ModelOutputSafetyPolicy.containsUnsafeLeak(
                "基础层：先检查状态维度是否足够，再手推窗口状态和枚举顺序是否保持同步。",
                "下一步：用一个最小可见样例观察边界是否进入循环。"
        )).isFalse();
    }

    @Test
    void stillRejectsDirectReplacementInstructions() {
        assertThat(ModelOutputSafetyPolicy.containsUnsafeLeak(
                "基础层：把第 12 行改成 right - left + 1 就可以。"
        )).isTrue();
        assertThat(ModelOutputSafetyPolicy.containsUnsafeLeak(
                "下一步：将循环条件替换为 i <= n。"
        )).isTrue();
    }

    @Test
    void stillRejectsCompleteCodeAndDirectOptimizationRecipe() {
        assertThat(ModelOutputSafetyPolicy.containsUnsafeLeak(
                "参考代码如下：#include <bits/stdc++.h>"
        )).isTrue();
        assertThat(ModelOutputSafetyPolicy.containsUnsafeLeak(
                "提高层：之后可以用两个变量滚动更新。"
        )).isTrue();
    }
}
