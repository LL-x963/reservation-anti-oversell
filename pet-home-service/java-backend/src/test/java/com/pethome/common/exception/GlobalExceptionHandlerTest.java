package com.pethome.common.exception;

import com.pethome.common.Result;
import com.pethome.dto.UserLoginRequest;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常处理器测试
 *
 * @author pet-home
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void businessExceptionShouldReturnResultError() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("库存不足"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void systemExceptionShouldNotExposeStackTrace() throws Exception {
        String body = mockMvc.perform(get("/test/system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("系统繁忙，请稍后重试"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("IllegalStateException")
                .doesNotContain("jdbc:mysql")
                .doesNotContain("com.pethome")
                .doesNotContain("at ");
    }

    @Test
    void paramValidationExceptionShouldReturnFieldMessage() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("openid")));
    }

    @Test
    void missingRequestParameterShouldReturnResultError() throws Exception {
        mockMvc.perform(get("/test/missing-param"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("name")));
    }

    @Test
    void unreadableRequestBodyShouldReturnResultError() throws Exception {
        mockMvc.perform(post("/test/not-readable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("请求参数格式错误"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business")
        public Result business() {
            throw new BusinessException(1001, "库存不足");
        }

        @GetMapping("/test/system")
        public Result system() {
            throw new IllegalStateException("连接失败: jdbc:mysql://localhost:3306/pet_home");
        }

        @PostMapping("/test/valid")
        public Result valid(@RequestBody @Valid UserLoginRequest request) {
            return Result.success();
        }

        @GetMapping("/test/missing-param")
        public Result missingParam(@RequestParam String name) {
            return Result.success(name);
        }

        @PostMapping("/test/not-readable")
        public Result notReadable(@RequestBody UserLoginRequest request) {
            return Result.success();
        }
    }

}
