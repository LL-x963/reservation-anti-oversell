package com.pethome.common.exception;

import com.pethome.common.Result;
import com.pethome.dto.BeauticianAddRequest;
import com.pethome.dto.UserLoginRequest;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
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
                .andExpect(jsonPath("$.bizCode").value(1001))
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
    void duplicateKeyExceptionShouldReturnFriendlyMessage() throws Exception {
        String body = mockMvc.perform(get("/test/duplicate-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("数据已存在，请检查名称是否重复"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Duplicate entry").doesNotContain("uk_name");
    }

    @Test
    void paramValidationExceptionShouldReturnFieldMessage() throws Exception {
        // 提示格式为「字段名 + 提示」，这里用密码长度触发校验
        // 注意：openid 已改为可选的两种登录方式之一（与手机号+密码二选一），不再必填
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("password")));
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

    @Test
    void fieldFormatErrorShouldReturnLocatableMessage() throws Exception {
        // 日期字段被传入非法文本（例如页面手输 yyyy-09-Tu）→ 提示具体字段与期望格式
        mockMvc.perform(post("/test/date-format")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryTime\":\"yyyy-09-Tu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("入职日期格式不正确，应为 yyyy-MM-dd"));

        // 非日期字段类型错误 → 提示「类型不正确」，且不暴露 Jackson 原始信息
        String body = mockMvc.perform(post("/test/date-format")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryTime\":\"2024-01-01\",\"status\":\"在职\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("参数 status 类型不正确"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("InvalidFormatException")
                .doesNotContain("java.lang")
                .doesNotContain("com.fasterxml");
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

        @GetMapping("/test/duplicate-key")
        public Result duplicateKey() {
            throw new DuplicateKeyException("Duplicate entry '基础洗护' for key 'service_category.uk_name'");
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

        @PostMapping("/test/date-format")
        public Result dateFormat(@RequestBody BeauticianAddRequest request) {
            return Result.success();
        }
    }

}
