package com.example.paintquality;

import com.example.paintquality.auth.AuthTokenRepository;
import com.example.paintquality.auth.PasswordHasher;
import com.example.paintquality.excel.AreaTemplateRepository;
import com.example.paintquality.model.VehicleModelRepository;
import com.example.paintquality.record.QualityRecordRepository;
import com.example.paintquality.user.InspectorUser;
import com.example.paintquality.user.Role;
import com.example.paintquality.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaintQualityServerApplicationTests {

    private static final String ADMIN_PASSWORD = "admin123";
    private static final String USER_PASSWORD = "pass1234";

    @TempDir
    static Path temporaryDirectory;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("app.data-dir", () -> temporaryDirectory.toString());
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:paint-quality-test;DB_CLOSE_DELAY=-1");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    @Autowired
    private AuthTokenRepository tokenRepository;

    @Autowired
    private QualityRecordRepository recordRepository;

    @Autowired
    private VehicleModelRepository modelRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AreaTemplateRepository areaTemplateRepository;

    @BeforeEach
    void resetData() {
        tokenRepository.deleteAll();
        recordRepository.deleteAll();
        modelRepository.deleteAll();
        areaTemplateRepository.deleteAll();
        List<InspectorUser> nonPrimaryUsers = userRepository.findAll().stream()
                .filter(user -> user.getRole() != Role.PRIMARY_ADMIN).toList();
        userRepository.deleteAll(nonPrimaryUsers);
        InspectorUser admin = userRepository.findByRole(Role.PRIMARY_ADMIN).orElseThrow();
        admin.setInspectorName("admin");
        admin.setArea("ADMIN");
        admin.setEnabled(true);
        admin.setEmail(null);
        admin.setCheckArea(null);
        admin.setPasswordHash(PasswordHasher.hash(ADMIN_PASSWORD));
        userRepository.save(admin);
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void registerLoginMeAndLogoutWork() throws Exception {
        Session admin = login("admin", "ADMIN");
        Session registered = register("检查人甲");
        assertEquals(Role.USER.name(), registered.role());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(registered.token())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("inspectorName", "检查人甲", "area", "A1",
                                "password", USER_PASSWORD))))
                .andExpect(status().isForbidden());

        approve(admin, registered, "A1", "前处理");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(registered.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inspectorName").value("检查人甲"))
                .andExpect(jsonPath("$.area").value("A1"))
                .andExpect(jsonPath("$.checkArea").value("前处理"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.enabled").value(true));

        Session loggedIn = login("检查人甲", "A1", "前处理");
        assertNotNull(loggedIn.token());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("inspectorName", "检查人甲",
                                "password", USER_PASSWORD))))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(registered.token())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(registered.token())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administratorRoleLimitsAreEnforced() throws Exception {
        Session admin = login("admin", "ADMIN");
        Session first = register("二级一");
        Session second = register("二级二");
        Session third = register("二级三");
        Session fourth = register("二级四");

        for (Session user : List.of(first, second, third)) {
            changeRole(admin, user, "SECONDARY_ADMIN", 200);
        }
        changeRole(admin, fourth, "SECONDARY_ADMIN", 409);
        changeRole(admin, fourth, "PRIMARY_ADMIN", 409);
        changeRole(admin, admin, "USER", 409);

        mockMvc.perform(patch("/api/admin/users/{id}", admin.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", false))))
                .andExpect(status().isConflict());
    }

    @Test
    void secondaryAdminUploadsOwnAreaTemplateAndRoundsAreParsed() throws Exception {
        Session admin = login("admin", "ADMIN");
        Session secondary = register("密封管理员");
        approve(admin, secondary, "密封", "密封线");
        changeRole(admin, secondary, "SECONDARY_ADMIN", 200);
        MockMultipartFile template = templateFile();

        mockMvc.perform(multipart("/api/admin/template").file(template)
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondary.token())))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/admin/template").file(template).param("area", "UBC")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondary.token())))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/admin/template").file(template).param("area", "面漆1线")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondary.token())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/rounds")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondary.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].number").value(1))
                .andExpect(jsonPath("$[0].sequence").value("第一次检查"))
                .andExpect(jsonPath("$[0].timeArea").value("7：00-9：00 /工程"))
                .andExpect(jsonPath("$[4].sequence").value("第五次检查"));

        Session normal = register("普通检查人");
        approve(admin, normal, "面漆", "面漆1线");
        mockMvc.perform(multipart("/api/admin/template").file(template).param("area", "面漆1线")
                        .header(HttpHeaders.AUTHORIZATION, bearer(normal.token())))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/admin/template").file(template).param("area", "面漆1线")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rounds")
                        .header(HttpHeaders.AUTHORIZATION, bearer(normal.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void secondaryAdminApprovesDeletesUsersAndMaintainsModels() throws Exception {
        Session admin = login("admin", "ADMIN");
        Session secondary = register("二级管理员");
        approve(admin, secondary, "密封", "密封线");
        changeRole(admin, secondary, "SECONDARY_ADMIN", 200);

        Session pending = register("待审人员");
        mockMvc.perform(patch("/api/admin/users/{id}", pending.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondary.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", true, "area", "面漆",
                                "checkArea", "面漆1线"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.checkArea").value("面漆1线"));

        mockMvc.perform(patch("/api/admin/users/{id}", pending.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondary.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "SECONDARY_ADMIN"))))
                .andExpect(status().isForbidden());

        login("待审人员", "面漆", "面漆1线");

        mockMvc.perform(post("/api/models")
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondary.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "SEC1", "modelName", "二级维护车型"))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/users/{id}", pending.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondary.token())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("inspectorName", "待审人员", "area", "面漆",
                                "password", USER_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanSaveOwnEmailForMobileSending() throws Exception {
        Session admin = login("admin", "ADMIN");

        mockMvc.perform(put("/api/profile/email")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "admin@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@example.com"));

        mockMvc.perform(put("/api/profile/email")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "not-an-email"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userCanSelectAreasWithinResponsibility() throws Exception {
        Session admin = login("admin", "ADMIN");
        Session user = register("区域检查人");
        approve(admin, user, "前处理电泳", "前处理");

        mockMvc.perform(patch("/api/auth/me/areas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("area", "密封", "checkArea", "UBC"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.area").value("密封"))
                .andExpect(jsonPath("$.checkArea").value("UBC"));

        mockMvc.perform(patch("/api/auth/me/areas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("area", "面漆", "checkArea", "UBC"))))
                .andExpect(status().isBadRequest());
    }

    private MockMultipartFile templateFile() throws Exception {
        byte[] content;
        try (var input = new ClassPathResource("quality-check-template.xlsx").getInputStream()) {
            content = input.readAllBytes();
        }
        return new MockMultipartFile("file", "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    }

    @Test
    void deletedModelCanBeRecreatedWithTheSameCode() throws Exception {
        Session admin = login("admin", "ADMIN");
        createModel(admin);
        long modelId = modelRepository.findByCodeAndActiveTrue("M1").orElseThrow().getId();

        mockMvc.perform(delete("/api/models/{id}", modelId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/models")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        createModel(admin);
    }

    @Test
    void batchValidationOwnershipAndSharedReadWork() throws Exception {
        Session admin = login("admin", "ADMIN");
        Session owner = register("检查人甲");
        Session other = register("检查人乙");
        approve(admin, owner, "A1", "前处理");
        approve(admin, other, "A2", "电泳");
        createModel(admin);
        LocalDate today = LocalDate.now(clock);
        ObjectNode batch = batch(today, 1, 1, false);

        mockMvc.perform(post("/api/records/batches")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(batch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].vehiclePosition").value(1));

        mockMvc.perform(get("/api/records").param("date", today.toString())
                        .param("checkArea", "前处理")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].inspectorName").value("检查人甲"));

        mockMvc.perform(post("/api/records/batches")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(batch)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/records/batches")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(batch)))
                .andExpect(status().isOk());

        Session sameArea = register("检查人丙");
        approve(admin, sameArea, "A1", "前处理");
        mockMvc.perform(post("/api/records/batches")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sameArea.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(batch)))
                .andExpect(status().isConflict());

        ObjectNode fourVehicles = batch.deepCopy();
        ((ArrayNode) fourVehicles.get("vehicles")).remove(4);
        expectInvalidBatch(owner, fourVehicles);

        ObjectNode duplicatePosition = batch.deepCopy();
        ((ObjectNode) duplicatePosition.get("vehicles").get(1)).put("vehiclePosition", 1);
        expectInvalidBatch(owner, duplicatePosition);

        ObjectNode missingNokNote = batch.deepCopy();
        ObjectNode nokVehicle = (ObjectNode) missingNokNote.get("vehicles").get(0);
        nokVehicle.put("status", "NOK");
        nokVehicle.putNull("issueNote");
        expectInvalidBatch(owner, missingNokNote);

        ObjectNode wrongVehicleNumber = batch.deepCopy();
        ((ObjectNode) wrongVehicleNumber.get("vehicles").get(0)).put("vehicleNumber", "000000");
        expectInvalidBatch(owner, wrongVehicleNumber);

        ObjectNode invalidVin = batch.deepCopy();
        ((ObjectNode) invalidVin.get("vehicles").get(0)).put("vin", "INVALID");
        expectInvalidBatch(owner, invalidVin);

        ObjectNode emptyVinBatch = batch(today, 2, 1, false);
        emptyVinBatch.get("vehicles").forEach(vehicle -> ((ObjectNode) vehicle).put("vin", ""));
        mockMvc.perform(post("/api/records/batches")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(emptyVinBatch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].vin").value(""));

        expectInvalidBatch(owner, batch(today.minusDays(2), 2, 1, false));

        mockMvc.perform(get("/api/records").param("date", today.minusDays(3).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void excelContainsMappedVehiclesSignaturesAndNokComment() throws Exception {
        Session admin = login("admin", "ADMIN");
        Session firstInspector = register("检查人甲");
        Session secondInspector = register("检查人乙");
        approve(admin, firstInspector, "A1", "前处理");
        approve(admin, secondInspector, "A2", "电泳");
        createModel(admin);
        LocalDate today = LocalDate.now(clock);

        saveBatch(firstInspector, batch(today, 1, 1, true));
        saveBatch(secondInspector, batch(today, 1, 2, false));

        MvcResult result = mockMvc.perform(get("/api/excel/{date}.xlsx", today)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk())
                .andReturn();

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheet("5X5X5");
            assertNotNull(sheet);
            assertNotNull(workbook.getSheet("Sheet1"));
            assertNotNull(workbook.getSheet("填写说明"));
            assertEquals("北厂油漆车间质量检查记录单", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals(today.toString(), sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("100001", sheet.getRow(5).getCell(2).getStringCellValue());
            assertEquals("试验车型", sheet.getRow(5).getCell(3).getStringCellValue());
            assertEquals("OK", sheet.getRow(5).getCell(4).getStringCellValue());
            assertEquals("NOK", sheet.getRow(6).getCell(4).getStringCellValue());
            Comment comment = sheet.getRow(6).getCell(4).getCellComment();
            assertNotNull(comment);
            assertEquals("划痕", comment.getString().getString());
            assertEquals("200001", sheet.getRow(5).getCell(5).getStringCellValue());
            String signatures = sheet.getRow(5).getCell(10).getStringCellValue();
            assertTrue(signatures.contains("检查人甲"));
            assertTrue(signatures.contains("检查人乙"));
        }

        assertTrue(java.nio.file.Files.isRegularFile(
                temporaryDirectory.resolve("exports").resolve(today + ".xlsx")));
    }

    private Session register(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("inspectorName", name,
                                "password", USER_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return session(result);
    }

    private void approve(Session admin, Session target, String area, String checkArea) throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}", target.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", true, "area", area, "checkArea", checkArea))))
                .andExpect(status().isOk());
    }

    private Session login(String name, String area) throws Exception {
        return login(name, area, null);
    }

    private Session login(String name, String area, String checkArea) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("inspectorName", name);
        body.put("area", area);
        if (checkArea != null) {
            body.put("checkArea", checkArea);
        }
        body.put("password", "admin".equals(name) ? ADMIN_PASSWORD : USER_PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
        return session(result);
    }

    private Session session(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return new Session(body.get("token").asText(), body.get("user").get("id").asLong(),
                body.get("user").get("role").asText());
    }

    private void createModel(Session admin) throws Exception {
        mockMvc.perform(post("/api/models")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "M1", "modelName", "试验车型"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    private void changeRole(Session admin, Session target, String role, int expectedStatus) throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}", target.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", role))))
                .andExpect(status().is(expectedStatus));
    }

    private void expectInvalidBatch(Session owner, ObjectNode batch) throws Exception {
        mockMvc.perform(post("/api/records/batches")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(batch)))
                .andExpect(status().isBadRequest());
    }

    private void saveBatch(Session owner, ObjectNode batch) throws Exception {
        mockMvc.perform(post("/api/records/batches")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(batch)))
                .andExpect(status().isOk());
    }

    private ObjectNode batch(LocalDate date, int round, int line, boolean withNok) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("workDate", date.toString());
        body.put("roundNumber", round);
        body.put("lineNumber", line);
        ArrayNode vehicles = body.putArray("vehicles");
        for (int position = 1; position <= 5; position++) {
            String suffix = String.format("%06d", line * 100000 + position);
            ObjectNode vehicle = vehicles.addObject();
            vehicle.put("vehiclePosition", position);
            vehicle.put("vin", "1HGCM82633A" + suffix);
            vehicle.put("vehicleNumber", suffix);
            vehicle.put("modelCode", "M1");
            vehicle.put("vehicleModel", "试验车型");
            vehicle.put("status", withNok && position == 2 ? "NOK" : "OK");
            if (withNok && position == 2) {
                vehicle.put("issueNote", "划痕");
            }
        }
        return body;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Session(String token, long id, String role) {
    }
}
