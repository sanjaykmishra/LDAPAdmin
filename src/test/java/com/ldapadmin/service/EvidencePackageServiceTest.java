package com.ldapadmin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ldapadmin.config.AppProperties;
import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.*;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.model.LdapGroup;
import com.ldapadmin.ldap.model.LdapUser;
import com.ldapadmin.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvidencePackageServiceTest {

    @Mock private DirectoryConnectionRepository directoryRepo;
    @Mock private AccessReviewCampaignRepository campaignRepo;
    @Mock private AccessReviewCampaignHistoryRepository historyRepo;
    @Mock private AccessReviewCampaignService campaignService;
    @Mock private SodPolicyRepository sodPolicyRepo;
    @Mock private SodViolationRepository sodViolationRepo;
    @Mock private PendingApprovalRepository approvalRepo;
    @Mock private PdfReportService pdfReportService;
    @Mock private LdapUserService ldapUserService;
    @Mock private LdapGroupService ldapGroupService;
    @Mock private AppProperties appProperties;

    private EvidencePackageService service;

    private DirectoryConnection directory;
    private final UUID directoryId = UUID.randomUUID();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        service = new EvidencePackageService(
                directoryRepo, campaignRepo, historyRepo, campaignService,
                sodPolicyRepo, sodViolationRepo, approvalRepo, pdfReportService,
                ldapUserService, ldapGroupService, appProperties);

        directory = new DirectoryConnection();
        directory.setId(directoryId);
        directory.setDisplayName("Test Directory");
        directory.setEnabled(true);

        // Default encryption key for HMAC
        AppProperties.Encryption enc = new AppProperties.Encryption();
        enc.setKey(Base64.getEncoder().encodeToString(new byte[32])); // 256-bit zero key
        lenient().when(appProperties.getEncryption()).thenReturn(enc);
    }

    @Test
    void generateEvidencePackage_producesValidZip() throws IOException {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(pdfReportService.generateUserAccessReport(eq(directoryId), isNull()))
                .thenReturn("pdf-content".getBytes());
        when(pdfReportService.generatePrivilegedAccountInventory())
                .thenReturn("privileged-pdf".getBytes());
        when(approvalRepo.findAllByDirectoryIdOrderByCreatedAtDesc(directoryId))
                .thenReturn(List.of());

        byte[] zip = service.generateEvidencePackage(
                directoryId, List.of(), false, false, "admin");

        Map<String, byte[]> entries = extractZip(zip);
        assertThat(entries).containsKey("manifest.json");
        assertThat(entries).containsKey("reports/user-access-report.pdf");
        assertThat(entries).containsKey("reports/privileged-account-inventory.pdf");
        assertThat(entries).containsKey("approval-history/approvals.json");
    }

    @Test
    void generateEvidencePackage_includesCampaignData() throws IOException {
        UUID campaignId = UUID.randomUUID();
        AccessReviewCampaign campaign = new AccessReviewCampaign();
        campaign.setId(campaignId);
        campaign.setName("Q1 Review");
        campaign.setDirectory(directory);
        campaign.setReviewGroups(new ArrayList<>());

        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(pdfReportService.generateUserAccessReport(any(), any())).thenReturn(new byte[0]);
        when(pdfReportService.generatePrivilegedAccountInventory()).thenReturn(new byte[0]);
        when(campaignRepo.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(pdfReportService.generateAccessReviewSummary(campaignId)).thenReturn("summary-pdf".getBytes());
        when(campaignService.exportCsv(campaignId)).thenReturn("csv-data".getBytes());
        when(historyRepo.findByCampaignIdOrderByChangedAtAsc(campaignId)).thenReturn(List.of());
        when(approvalRepo.findAllByDirectoryIdOrderByCreatedAtDesc(directoryId)).thenReturn(List.of());

        byte[] zip = service.generateEvidencePackage(
                directoryId, List.of(campaignId), false, false, "admin");

        Map<String, byte[]> entries = extractZip(zip);
        assertThat(entries).containsKey("campaigns/q1_review/access-review-summary.pdf");
        assertThat(entries).containsKey("campaigns/q1_review/decisions.csv");
        assertThat(entries).containsKey("campaigns/q1_review/history.json");
    }

    @Test
    void generateEvidencePackage_includesSodData() throws IOException {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(pdfReportService.generateUserAccessReport(any(), any())).thenReturn(new byte[0]);
        when(pdfReportService.generatePrivilegedAccountInventory()).thenReturn(new byte[0]);
        when(approvalRepo.findAllByDirectoryIdOrderByCreatedAtDesc(directoryId)).thenReturn(List.of());

        SodPolicy policy = new SodPolicy();
        policy.setId(UUID.randomUUID());
        policy.setName("Finance vs IT");
        policy.setSeverity(SodSeverity.HIGH);
        policy.setAction(SodAction.BLOCK);
        policy.setEnabled(true);
        Account createdBy = new Account();
        createdBy.setUsername("admin");
        policy.setCreatedBy(createdBy);
        policy.setCreatedAt(OffsetDateTime.now());
        when(sodPolicyRepo.findByDirectoryId(directoryId)).thenReturn(List.of(policy));

        SodViolation violation = new SodViolation();
        violation.setId(UUID.randomUUID());
        violation.setPolicy(policy);
        violation.setUserDn("cn=jdoe,ou=users,dc=example,dc=com");
        violation.setUserDisplayName("John Doe");
        violation.setStatus(SodViolationStatus.OPEN);
        violation.setDetectedAt(OffsetDateTime.now());
        when(sodViolationRepo.findByDirectoryIdAndStatus(directoryId, SodViolationStatus.OPEN))
                .thenReturn(List.of(violation));

        byte[] zip = service.generateEvidencePackage(
                directoryId, List.of(), true, false, "admin");

        Map<String, byte[]> entries = extractZip(zip);
        assertThat(entries).containsKey("sod/policies.json");
        assertThat(entries).containsKey("sod/violations.json");

        // Verify policy data
        List<Map<String, Object>> policies = objectMapper.readValue(
                entries.get("sod/policies.json"), new TypeReference<>() {});
        assertThat(policies).hasSize(1);
        assertThat(policies.get(0).get("name")).isEqualTo("Finance vs IT");

        // Verify violation data
        List<Map<String, Object>> violations = objectMapper.readValue(
                entries.get("sod/violations.json"), new TypeReference<>() {});
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).get("userDisplayName")).isEqualTo("John Doe");
    }

    @Test
    void generateEvidencePackage_includesEntitlements() throws IOException {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(pdfReportService.generateUserAccessReport(any(), any())).thenReturn(new byte[0]);
        when(pdfReportService.generatePrivilegedAccountInventory()).thenReturn(new byte[0]);
        when(approvalRepo.findAllByDirectoryIdOrderByCreatedAtDesc(directoryId)).thenReturn(List.of());

        LdapUser user = new LdapUser("cn=jdoe,ou=users,dc=example,dc=com",
                Map.of("cn", List.of("John Doe"), "uid", List.of("jdoe"),
                        "mail", List.of("jdoe@example.com")));
        when(ldapUserService.searchUsers(any(DirectoryConnection.class), anyString(), any(),
                anyInt(), any(String.class), any(String.class), any(String.class),
                any(String.class), any(String.class), any(String.class)))
                .thenReturn(List.of(user));

        LdapGroup group = new LdapGroup("cn=devs,ou=groups,dc=example,dc=com",
                Map.of("cn", List.of("devs"),
                        "member", List.of("cn=jdoe,ou=users,dc=example,dc=com")));
        when(ldapGroupService.searchGroups(any(DirectoryConnection.class), anyString(), any(),
                anyInt(), any(String.class), any(String.class), any(String.class),
                any(String.class)))
                .thenReturn(List.of(group));

        byte[] zip = service.generateEvidencePackage(
                directoryId, List.of(), false, true, "admin");

        Map<String, byte[]> entries = extractZip(zip);
        assertThat(entries).containsKey("entitlements/user-entitlements.json");

        List<Map<String, Object>> entitlements = objectMapper.readValue(
                entries.get("entitlements/user-entitlements.json"), new TypeReference<>() {});
        assertThat(entitlements).hasSize(1);
        assertThat(entitlements.get(0).get("dn")).isEqualTo("cn=jdoe,ou=users,dc=example,dc=com");
        @SuppressWarnings("unchecked")
        List<String> groups = (List<String>) entitlements.get(0).get("groups");
        assertThat(groups).contains("devs");
    }

    @Test
    void manifest_containsChecksums() throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("test.txt", "hello".getBytes());

        byte[] manifest = service.buildManifest(files, directoryId, "Test Dir",
                OffsetDateTime.now(), "admin", List.of(), false, false);

        Map<String, Object> parsed = objectMapper.readValue(manifest, new TypeReference<>() {});
        assertThat(parsed).containsKey("generatedAt");
        assertThat(parsed).containsKey("generatedBy");
        assertThat(parsed).containsKey("hmacSha256Signature");

        @SuppressWarnings("unchecked")
        List<Map<String, String>> fileEntries = (List<Map<String, String>>) parsed.get("files");
        assertThat(fileEntries).hasSize(1);
        assertThat(fileEntries.get(0).get("path")).isEqualTo("test.txt");
        assertThat(fileEntries.get(0).get("sha256")).isNotEmpty();
        assertThat(fileEntries.get(0).get("size")).isEqualTo("5");
    }

    @Test
    void sha256Hex_producesCorrectHash() {
        // SHA-256 of empty string is a well-known value
        String hash = service.sha256Hex(new byte[0]);
        assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void hmacSha256_producesConsistentSignature() {
        byte[] data = "test data".getBytes();
        String sig1 = service.hmacSha256(data);
        String sig2 = service.hmacSha256(data);
        assertThat(sig1).isEqualTo(sig2);
        assertThat(sig1).hasSize(64); // 32 bytes = 64 hex chars
    }

    @Test
    void buildZip_createsValidZipWithAllEntries() throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("dir/file1.txt", "content1".getBytes());
        files.put("dir/file2.json", "{}".getBytes());
        files.put("root.txt", "root".getBytes());

        byte[] zip = service.buildZip(files);
        Map<String, byte[]> extracted = extractZip(zip);

        assertThat(extracted).hasSize(3);
        assertThat(new String(extracted.get("dir/file1.txt"))).isEqualTo("content1");
        assertThat(new String(extracted.get("dir/file2.json"))).isEqualTo("{}");
        assertThat(new String(extracted.get("root.txt"))).isEqualTo("root");
    }

    @Test
    void generateEvidencePackage_directoryNotFound_throws() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateEvidencePackage(
                directoryId, List.of(), false, false, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Directory not found");
    }

    @Test
    void generateEvidencePackage_missingCampaign_skippedGracefully() throws IOException {
        UUID missingCampaignId = UUID.randomUUID();
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(pdfReportService.generateUserAccessReport(any(), any())).thenReturn(new byte[0]);
        when(pdfReportService.generatePrivilegedAccountInventory()).thenReturn(new byte[0]);
        when(approvalRepo.findAllByDirectoryIdOrderByCreatedAtDesc(directoryId)).thenReturn(List.of());
        when(campaignRepo.findById(missingCampaignId)).thenReturn(Optional.empty());

        byte[] zip = service.generateEvidencePackage(
                directoryId, List.of(missingCampaignId), false, false, "admin");

        Map<String, byte[]> entries = extractZip(zip);
        // Should still produce a valid ZIP with manifest and other files
        assertThat(entries).containsKey("manifest.json");
        // No campaign entries since it was missing
        assertThat(entries.keySet().stream().filter(k -> k.startsWith("campaigns/"))).isEmpty();
    }

    @Test
    void generateEvidencePackage_pdfFailure_continuesGracefully() throws IOException {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(pdfReportService.generateUserAccessReport(any(), any()))
                .thenThrow(new RuntimeException("LDAP down"));
        when(pdfReportService.generatePrivilegedAccountInventory())
                .thenThrow(new RuntimeException("DB error"));
        when(approvalRepo.findAllByDirectoryIdOrderByCreatedAtDesc(directoryId)).thenReturn(List.of());

        byte[] zip = service.generateEvidencePackage(
                directoryId, List.of(), false, false, "admin");

        Map<String, byte[]> entries = extractZip(zip);
        // Manifest and approval data should still exist
        assertThat(entries).containsKey("manifest.json");
        assertThat(entries).containsKey("approval-history/approvals.json");
        // PDF files should be absent
        assertThat(entries).doesNotContainKey("reports/user-access-report.pdf");
        assertThat(entries).doesNotContainKey("reports/privileged-account-inventory.pdf");
    }

    @Test
    void manifest_hmacSignatureChangesWithDifferentContent() throws IOException {
        Map<String, byte[]> files1 = Map.of("a.txt", "hello".getBytes());
        Map<String, byte[]> files2 = Map.of("a.txt", "world".getBytes());

        byte[] manifest1 = service.buildManifest(files1, directoryId, "Dir",
                OffsetDateTime.now(), "admin", List.of(), false, false);
        byte[] manifest2 = service.buildManifest(files2, directoryId, "Dir",
                OffsetDateTime.now(), "admin", List.of(), false, false);

        Map<String, Object> parsed1 = objectMapper.readValue(manifest1, new TypeReference<>() {});
        Map<String, Object> parsed2 = objectMapper.readValue(manifest2, new TypeReference<>() {});

        // Checksums differ → HMAC signatures differ
        assertThat(parsed1.get("hmacSha256Signature"))
                .isNotEqualTo(parsed2.get("hmacSha256Signature"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Map<String, byte[]> extractZip(byte[] zipBytes) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), zis.readAllBytes());
                zis.closeEntry();
            }
        }
        return entries;
    }
}
