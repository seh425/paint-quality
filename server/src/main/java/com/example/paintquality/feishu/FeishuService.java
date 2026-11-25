package com.example.paintquality.feishu;

import com.example.paintquality.AppProperties;
import com.example.paintquality.common.ApiException;
import com.example.paintquality.user.InspectorUser;
import com.example.paintquality.user.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class FeishuService {

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public FeishuService(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public void sendWorkbook(InspectorUser sender, Path workbook, LocalDate workDate) {
        String receiver = sender.getFeishuOpenId();
        if ((receiver == null || receiver.isBlank()) && sender.getRole() == Role.PRIMARY_ADMIN) {
            receiver = properties.getFeishu().getDefaultReceiverOpenId();
        }
        if (receiver == null || receiver.isBlank()) {
            throw ApiException.badRequest("当前用户未设置飞书Open ID，且无可用的默认接收人");
        }
        if (isBlank(properties.getFeishu().getAppId()) || isBlank(properties.getFeishu().getAppSecret())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "服务端未配置飞书应用凭证");
        }

        String accessToken = obtainTenantAccessToken();
        String fileKey = uploadFile(accessToken, workbook, workDate);
        sendFileMessage(accessToken, receiver.trim(), fileKey);
    }

    private String obtainTenantAccessToken() {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "app_id", properties.getFeishu().getAppId(),
                    "app_secret", properties.getFeishu().getAppSecret()));
            HttpRequest request = baseRequest("/open-apis/auth/v3/tenant_access_token/internal")
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            JsonNode response = execute(request, "获取飞书tenant_access_token");
            String token = response.path("tenant_access_token").asText();
            if (token.isBlank()) {
                throw ApiException.badGateway("飞书未返回tenant_access_token");
            }
            return token;
        } catch (IOException exception) {
            throw ApiException.badGateway("构造飞书认证请求失败");
        }
    }

    private String uploadFile(String accessToken, Path workbook, LocalDate workDate) {
        String boundary = "----PaintQuality" + UUID.randomUUID();
        String filename = "quality-check-" + workDate + ".xlsx";
        try {
            byte[] content = Files.readAllBytes(workbook);
            if (content.length == 0) {
                throw ApiException.badGateway("导出的Excel文件为空，无法发送到飞书");
            }
            byte[] body = multipartBody(boundary, filename, content);
            HttpRequest request = baseRequest("/open-apis/im/v1/files")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            JsonNode response = execute(request, "上传飞书文件");
            String fileKey = response.path("data").path("file_key").asText();
            if (fileKey.isBlank()) {
                throw ApiException.badGateway("飞书文件上传成功但未返回file_key");
            }
            return fileKey;
        } catch (IOException exception) {
            throw ApiException.badGateway("读取Excel或构造飞书文件上传请求失败");
        }
    }

    private void sendFileMessage(String accessToken, String receiver, String fileKey) {
        try {
            String content = objectMapper.writeValueAsString(Map.of("file_key", fileKey));
            String body = objectMapper.writeValueAsString(Map.of(
                    "receive_id", receiver,
                    "msg_type", "file",
                    "content", content));
            HttpRequest request = baseRequest("/open-apis/im/v1/messages?receive_id_type=open_id")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            execute(request, "发送飞书文件消息");
        } catch (IOException exception) {
            throw ApiException.badGateway("构造飞书消息请求失败");
        }
    }

    private HttpRequest.Builder baseRequest(String path) {
        String baseUrl = properties.getFeishu().getBaseUrl().replaceAll("/+$", "");
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(30));
    }

    private JsonNode execute(HttpRequest request, String action) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw ApiException.badGateway(action + "网络请求失败");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw ApiException.badGateway(action + "被中断");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String detail = response.body();
            if (detail != null && detail.length() > 300) {
                detail = detail.substring(0, 300);
            }
            throw ApiException.badGateway(action + "失败，HTTP状态码: " + response.statusCode()
                    + (detail == null || detail.isBlank() ? "" : "，返回: " + detail));
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw ApiException.badGateway(action + "返回了无效JSON");
        }
        if (root.path("code").asInt(-1) != 0) {
            String message = root.path("msg").asText("未知错误");
            if (message.length() > 300) {
                message = message.substring(0, 300);
            }
            throw ApiException.badGateway(action + "失败，code=" + root.path("code").asText()
                    + "，msg=" + message);
        }
        return root;
    }

    private byte[] multipartBody(String boundary, String filename, byte[] file) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeTextPart(output, boundary, "file_type", "stream");
        writeTextPart(output, boundary, "file_name", filename);
        write(output, "--" + boundary + "\r\n");
        write(output, "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n");
        write(output, "Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n");
        output.write(file);
        write(output, "\r\n--" + boundary + "--\r\n");
        return output.toByteArray();
    }

    private void writeTextPart(ByteArrayOutputStream output, String boundary,
                               String name, String value) throws IOException {
        write(output, "--" + boundary + "\r\n");
        write(output, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        write(output, value + "\r\n");
    }

    private void write(ByteArrayOutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
