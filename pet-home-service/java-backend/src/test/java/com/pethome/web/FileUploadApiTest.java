package com.pethome.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.pethome.common.constant.RedisKeyConstant;
import com.pethome.common.enums.UserType;
import com.pethome.config.UploadProperties;
import com.pethome.entity.Employee;
import com.pethome.mapper.EmployeeMapper;
import com.pethome.util.RedisUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文件上传接口集成测试（POST /api/admin/file/upload）
 *
 * @author pet-home
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FileUploadApiTest {

    private static final String EMP_USERNAME = "it_upload_admin";
    private static final String EMP_PASSWORD = "123456";
    /** 最小 PNG 文件头，仅用于测试上传与回读 */
    private static final byte[] IMAGE_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4, 5};

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private UploadProperties uploadProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Employee employee;

    private final List<Path> uploadedFiles = new ArrayList<>();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM employee WHERE username = ?", EMP_USERNAME);

        employee = new Employee();
        employee.setUsername(EMP_USERNAME);
        employee.setName("上传测试员工");
        employee.setPassword(passwordEncoder.encode(EMP_PASSWORD));
        employee.setStatus(1);
        employee.setRole("ADMIN");
        employeeMapper.insert(employee);
    }

    @AfterEach
    void tearDown() {
        uploadedFiles.forEach(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                // 清理失败不影响测试结论
            }
        });
        uploadedFiles.clear();

        jdbcTemplate.update("DELETE FROM employee WHERE username = ?", EMP_USERNAME);
        if (employee != null && employee.getId() != null) {
            redisUtil.delete(RedisKeyConstant.loginTokenKey(UserType.EMPLOYEE, employee.getId()));
        }
    }

    @Test
    void uploadShouldReturnAccessibleUrl() {
        JSONObject response = upload(adminToken(), "test-image.png", IMAGE_BYTES);

        assertThat(response.getInteger("code")).isEqualTo(1);
        String url = response.getString("data");
        assertThat(url).startsWith(uploadProperties.getUrlPrefix()).endsWith(".png");

        Path storedFile = resolve(url);
        uploadedFiles.add(storedFile);
        assertThat(Files.exists(storedFile)).isTrue();

        // 返回的地址可通过静态资源映射直接访问
        ResponseEntity<byte[]> download = restTemplate.getForEntity(url, byte[].class);
        assertThat(download.getStatusCode().value()).isEqualTo(200);
        assertThat(download.getBody()).isEqualTo(IMAGE_BYTES);
    }

    @Test
    void uploadShouldRejectUnsupportedExtension() {
        JSONObject response = upload(adminToken(), "not-image.txt", IMAGE_BYTES);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).contains("仅支持上传");
    }

    @Test
    void uploadShouldRejectEmptyFile() {
        JSONObject response = upload(adminToken(), "empty.png", new byte[0]);

        assertThat(response.getInteger("code")).isZero();
        assertThat(response.getString("msg")).isNotBlank();
    }

    @Test
    void uploadShouldRequireAdminToken() {
        ResponseEntity<String> response = uploadRaw(null, "test-image.png", IMAGE_BYTES);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(JSON.parseObject(response.getBody()).getInteger("code")).isZero();
    }

    private JSONObject upload(String token, String fileName, byte[] content) {
        return JSON.parseObject(uploadRaw(token, fileName, content).getBody());
    }

    private ResponseEntity<String> uploadRaw(String token, String fileName, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });

        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        return restTemplate.exchange("/api/admin/file/upload", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private String adminToken() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/admin/login",
                Map.of("username", EMP_USERNAME, "password", EMP_PASSWORD), String.class);
        JSONObject body = JSON.parseObject(response.getBody());
        assertThat(body.getInteger("code")).isEqualTo(1);
        return body.getJSONObject("data").getString("token");
    }

    private Path resolve(String url) {
        String relative = url.substring(uploadProperties.normalizedUrlPrefix().length());
        return Paths.get(uploadProperties.getBaseDir()).toAbsolutePath().normalize().resolve(relative);
    }

}
