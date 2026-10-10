package com.pethome.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一返回结果单元测试
 *
 * @author pet-home
 */
class ResultTest {

    @Test
    void successWithoutData() {
        Result result = Result.success();
        assertThat(result.getCode()).isEqualTo(1);
        assertThat(result.getMsg()).isEqualTo("success");
        assertThat(result.getData()).isNull();
    }

    @Test
    void successWithData() {
        Result result = Result.success("payload");
        assertThat(result.getCode()).isEqualTo(1);
        assertThat(result.getMsg()).isEqualTo("success");
        assertThat(result.getData()).isEqualTo("payload");
    }

    @Test
    void errorWithMsg() {
        Result result = Result.error("库存不足");
        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getMsg()).isEqualTo("库存不足");
        assertThat(result.getData()).isNull();
    }

    @Test
    void errorWithoutMsg() {
        Result result = Result.error();
        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getMsg()).isEqualTo("操作失败");
    }

}
