package com.ldapadmin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldapadmin.controller.directory.HrConnectionController;
import com.ldapadmin.dto.hr.CreateHrConnectionRequest;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.HrEmployeeStatus;
import com.ldapadmin.entity.enums.HrMatchConfidence;
import com.ldapadmin.entity.enums.HrProvider;
import com.ldapadmin.entity.enums.HrSyncStatus;
import com.ldapadmin.entity.enums.HrSyncTrigger;
import com.ldapadmin.entity.hr.HrConnection;
import com.ldapadmin.entity.hr.HrEmployee;
import com.ldapadmin.entity.hr.HrSyncRun;
import com.ldapadmin.repository.AccountRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.hr.HrConnectionRepository;
import com.ldapadmin.repository.hr.HrEmployeeRepository;
import com.ldapadmin.repository.hr.HrSyncRunRepository;
import com.ldapadmin.service.EncryptionService;
import com.ldapadmin.service.hr.BambooHrClient;
import com.ldapadmin.service.hr.HrSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HrConnectionController.class)
class HrConnectionControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private HrConnectionRepository connectionRepo;
    @MockBean private HrEmployeeRepository employeeRepo;
    @MockBean private HrSyncRunRepository syncRunRepo;
    @MockBean private DirectoryConnectionRepository directoryRepo;
    @MockBean private AccountRepository accountRepo;
    @MockBean private EncryptionService encryptionService;
    @MockBean private BambooHrClient bambooHrClient;
    @MockBean private HrSyncService syncService;

    private final UUID dirId = UUID.randomUUID();

    @Test
    void getConnection_returns200() throws Exception {
        HrConnection conn = buildConnection();
        when(connectionRepo.findByDirectoryId(dirId)).thenReturn(Optional.of(conn));

        mvc.perform(get("/api/v1/directories/{dirId}/hr", dirId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Test HR"))
                .andExpect(jsonPath("$.subdomain").value("acme"))
                .andExpect(jsonPath("$.hasApiKey").value(true));
    }

    @Test
    void getConnection_notFound_returns404() throws Exception {
        when(connectionRepo.findByDirectoryId(dirId)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/directories/{dirId}/hr", dirId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isNotFound());
    }

    @Test
    void getConnection_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/v1/directories/{dirId}/hr", dirId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createConnection_returns201() throws Exception {
        DirectoryConnection dir = new DirectoryConnection();
        dir.setId(dirId);
        when(directoryRepo.findById(dirId)).thenReturn(Optional.of(dir));
        when(encryptionService.encrypt("my-key")).thenReturn("encrypted");
        when(connectionRepo.save(any())).thenAnswer(inv -> {
            HrConnection c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setCreatedAt(OffsetDateTime.now());
            c.setUpdatedAt(OffsetDateTime.now());
            return c;
        });

        CreateHrConnectionRequest req = new CreateHrConnectionRequest(
                "My HR", "acme", "my-key", "mail", "workEmail", "0 0 * * * ?");

        mvc.perform(post("/api/v1/directories/{dirId}/hr", dirId)
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("My HR"))
                .andExpect(jsonPath("$.subdomain").value("acme"));
    }

    @Test
    void createConnection_missingFields_returns400() throws Exception {
        // displayName and subdomain are required
        String json = """
                {"apiKey": "key"}
                """;

        mvc.perform(post("/api/v1/directories/{dirId}/hr", dirId)
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteConnection_returns204() throws Exception {
        HrConnection conn = buildConnection();
        when(connectionRepo.findByDirectoryId(dirId)).thenReturn(Optional.of(conn));

        mvc.perform(delete("/api/v1/directories/{dirId}/hr", dirId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isNoContent());
    }

    @Test
    void testConnection_success_returns200WithCount() throws Exception {
        when(bambooHrClient.testConnection("acme", "my-key")).thenReturn(50);

        CreateHrConnectionRequest req = new CreateHrConnectionRequest(
                "Test", "acme", "my-key", null, null, null);

        mvc.perform(post("/api/v1/directories/{dirId}/hr/test", dirId)
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.employeeCount").value(50));
    }

    @Test
    void testConnection_failure_returnsSuccessFalse() throws Exception {
        when(bambooHrClient.testConnection("badco", "bad-key")).thenReturn(-1);

        CreateHrConnectionRequest req = new CreateHrConnectionRequest(
                "Test", "badco", "bad-key", null, null, null);

        mvc.perform(post("/api/v1/directories/{dirId}/hr/test", dirId)
                        .with(authentication(superadminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void triggerSync_returns200WithRunDto() throws Exception {
        HrConnection conn = buildConnection();
        when(connectionRepo.findByDirectoryId(dirId)).thenReturn(Optional.of(conn));

        HrSyncRun run = new HrSyncRun();
        run.setId(UUID.randomUUID());
        run.setHrConnection(conn);
        run.setStatus(HrSyncStatus.SUCCESS);
        run.setTotalEmployees(100);
        run.setTriggeredBy(HrSyncTrigger.MANUAL);
        when(syncService.sync(eq(conn), eq(HrSyncTrigger.MANUAL), any())).thenReturn(run);

        mvc.perform(post("/api/v1/directories/{dirId}/hr/sync", dirId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.totalEmployees").value(100));
    }

    @Test
    void listEmployees_returns200WithPage() throws Exception {
        HrConnection conn = buildConnection();
        when(connectionRepo.findByDirectoryId(dirId)).thenReturn(Optional.of(conn));

        HrEmployee emp = new HrEmployee();
        emp.setId(UUID.randomUUID());
        emp.setHrConnection(conn);
        emp.setEmployeeId("1");
        emp.setFirstName("John");
        emp.setLastName("Doe");
        emp.setWorkEmail("john@example.com");
        emp.setStatus(HrEmployeeStatus.ACTIVE);
        emp.setMatchConfidence(HrMatchConfidence.EXACT);

        when(employeeRepo.findByHrConnectionId(eq(conn.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(emp)));

        mvc.perform(get("/api/v1/directories/{dirId}/hr/employees", dirId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].firstName").value("John"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }

    @Test
    void listEmployees_withStatusFilter_filtersCorrectly() throws Exception {
        HrConnection conn = buildConnection();
        when(connectionRepo.findByDirectoryId(dirId)).thenReturn(Optional.of(conn));
        when(employeeRepo.findByHrConnectionIdAndStatus(eq(conn.getId()),
                eq(HrEmployeeStatus.TERMINATED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/directories/{dirId}/hr/employees", dirId)
                        .param("status", "TERMINATED")
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getSummary_returns200WithCounts() throws Exception {
        HrConnection conn = buildConnection();
        when(connectionRepo.findByDirectoryId(dirId)).thenReturn(Optional.of(conn));
        when(employeeRepo.countByHrConnectionId(conn.getId())).thenReturn(100L);
        when(employeeRepo.countByHrConnectionIdAndStatus(conn.getId(), HrEmployeeStatus.ACTIVE)).thenReturn(80L);
        when(employeeRepo.countByHrConnectionIdAndStatus(conn.getId(), HrEmployeeStatus.TERMINATED)).thenReturn(20L);
        when(employeeRepo.countByHrConnectionIdAndMatchedLdapDnIsNotNull(conn.getId())).thenReturn(90L);
        when(employeeRepo.countByHrConnectionIdAndMatchedLdapDnIsNull(conn.getId())).thenReturn(10L);
        when(employeeRepo.findByHrConnectionIdAndStatusAndMatchedLdapDnIsNotNull(
                conn.getId(), HrEmployeeStatus.TERMINATED)).thenReturn(List.of());

        mvc.perform(get("/api/v1/directories/{dirId}/hr/summary", dirId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmployees").value(100))
                .andExpect(jsonPath("$.activeEmployees").value(80))
                .andExpect(jsonPath("$.matchedCount").value(90));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private HrConnection buildConnection() {
        DirectoryConnection dir = new DirectoryConnection();
        dir.setId(dirId);

        HrConnection conn = new HrConnection();
        conn.setId(UUID.randomUUID());
        conn.setDirectory(dir);
        conn.setProvider(HrProvider.BAMBOOHR);
        conn.setDisplayName("Test HR");
        conn.setSubdomain("acme");
        conn.setApiKeyEncrypted("encrypted-key");
        conn.setMatchAttribute("mail");
        conn.setMatchField("workEmail");
        conn.setSyncCron("0 0 * * * ?");
        conn.setCreatedAt(OffsetDateTime.now());
        conn.setUpdatedAt(OffsetDateTime.now());
        return conn;
    }
}
