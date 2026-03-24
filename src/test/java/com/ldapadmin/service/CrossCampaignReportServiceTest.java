package com.ldapadmin.service;

import com.ldapadmin.dto.accessreview.CampaignMetricRow;
import com.ldapadmin.dto.accessreview.CrossCampaignReportDto;
import com.ldapadmin.dto.accessreview.ReviewerMetricRow;
import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.entity.enums.ReviewDecision;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AccessReviewCampaignRepository;
import com.ldapadmin.repository.AccessReviewDecisionRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrossCampaignReportServiceTest {

    @Mock private AccessReviewCampaignRepository campaignRepo;
    @Mock private AccessReviewDecisionRepository decisionRepo;
    @Mock private DirectoryConnectionRepository directoryRepo;
    @Mock private ApplicationSettingsService settingsService;

    private CrossCampaignReportService service;

    private final UUID directoryId = UUID.randomUUID();
    private final OffsetDateTime from = OffsetDateTime.now().minusMonths(12);
    private final OffsetDateTime to = OffsetDateTime.now();

    private DirectoryConnection directory;

    @BeforeEach
    void setUp() {
        service = new CrossCampaignReportService(
                campaignRepo, decisionRepo, directoryRepo, settingsService);

        directory = new DirectoryConnection();
        directory.setId(directoryId);
        directory.setDisplayName("Test Directory");
    }

    // ── generateReport ──────────────────────────────────────────────────────

    @Test
    void generateReport_withCampaigns_returnsAggregatedMetrics() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));

        AccessReviewCampaign c1 = buildCampaign("Q1 Review", CampaignStatus.CLOSED);
        AccessReviewCampaign c2 = buildCampaign("Q2 Review", CampaignStatus.ACTIVE);

        when(campaignRepo.findByDirectoryIdAndCreatedAtBetween(eq(directoryId), any(), any()))
                .thenReturn(List.of(c1, c2));

        // c1: 10 total, 6 confirmed, 3 revoked
        when(decisionRepo.countTotalByCampaignId(c1.getId())).thenReturn(10L);
        when(decisionRepo.countByCampaignIdAndDecision(c1.getId(), ReviewDecision.CONFIRM)).thenReturn(6L);
        when(decisionRepo.countByCampaignIdAndDecision(c1.getId(), ReviewDecision.REVOKE)).thenReturn(3L);

        // c2: 5 total, 2 confirmed, 0 revoked
        when(decisionRepo.countTotalByCampaignId(c2.getId())).thenReturn(5L);
        when(decisionRepo.countByCampaignIdAndDecision(c2.getId(), ReviewDecision.CONFIRM)).thenReturn(2L);
        when(decisionRepo.countByCampaignIdAndDecision(c2.getId(), ReviewDecision.REVOKE)).thenReturn(0L);

        when(decisionRepo.findDecidedByCampaignIds(any())).thenReturn(List.of());

        CrossCampaignReportDto report = service.generateReport(directoryId, from, to, null);

        assertThat(report.totalCampaigns()).isEqualTo(2);
        assertThat(report.totalDecisions()).isEqualTo(15);
        assertThat(report.totalConfirmed()).isEqualTo(8);
        assertThat(report.totalRevoked()).isEqualTo(3);
        assertThat(report.totalPending()).isEqualTo(4);
        assertThat(report.overallRevocationRate()).isCloseTo(27.3, within(0.5));
        assertThat(report.campaigns()).hasSize(2);
        assertThat(report.campaignsByStatus()).containsEntry("CLOSED", 1L);
        assertThat(report.campaignsByStatus()).containsEntry("ACTIVE", 1L);
    }

    @Test
    void generateReport_withStatusFilter_filtersResults() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));

        AccessReviewCampaign c1 = buildCampaign("Q1 Review", CampaignStatus.CLOSED);
        AccessReviewCampaign c2 = buildCampaign("Q2 Review", CampaignStatus.ACTIVE);

        when(campaignRepo.findByDirectoryIdAndCreatedAtBetween(eq(directoryId), any(), any()))
                .thenReturn(List.of(c1, c2));

        when(decisionRepo.countTotalByCampaignId(c1.getId())).thenReturn(10L);
        when(decisionRepo.countByCampaignIdAndDecision(c1.getId(), ReviewDecision.CONFIRM)).thenReturn(6L);
        when(decisionRepo.countByCampaignIdAndDecision(c1.getId(), ReviewDecision.REVOKE)).thenReturn(3L);
        when(decisionRepo.findDecidedByCampaignIds(any())).thenReturn(List.of());

        CrossCampaignReportDto report = service.generateReport(directoryId, from, to, CampaignStatus.CLOSED);

        assertThat(report.totalCampaigns()).isEqualTo(1);
        assertThat(report.campaigns()).hasSize(1);
        assertThat(report.campaigns().get(0).name()).isEqualTo("Q1 Review");
    }

    @Test
    void generateReport_noCampaigns_returnsEmptyReport() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(campaignRepo.findByDirectoryIdAndCreatedAtBetween(eq(directoryId), any(), any()))
                .thenReturn(List.of());

        CrossCampaignReportDto report = service.generateReport(directoryId, from, to, null);

        assertThat(report.totalCampaigns()).isZero();
        assertThat(report.totalDecisions()).isZero();
        assertThat(report.overallRevocationRate()).isZero();
        assertThat(report.avgCompletionDays()).isNull();
        assertThat(report.campaigns()).isEmpty();
        assertThat(report.reviewers()).isEmpty();
    }

    @Test
    void generateReport_directoryNotFound_throws() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateReport(directoryId, from, to, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void generateReport_withCompletedCampaign_calculatesAvgCompletionDays() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));

        AccessReviewCampaign c = buildCampaign("Q1 Review", CampaignStatus.CLOSED);
        c.setStartsAt(OffsetDateTime.now().minusDays(15));
        c.setCompletedAt(OffsetDateTime.now().minusDays(5));

        when(campaignRepo.findByDirectoryIdAndCreatedAtBetween(eq(directoryId), any(), any()))
                .thenReturn(List.of(c));
        when(decisionRepo.countTotalByCampaignId(c.getId())).thenReturn(5L);
        when(decisionRepo.countByCampaignIdAndDecision(c.getId(), ReviewDecision.CONFIRM)).thenReturn(5L);
        when(decisionRepo.countByCampaignIdAndDecision(c.getId(), ReviewDecision.REVOKE)).thenReturn(0L);
        when(decisionRepo.findDecidedByCampaignIds(any())).thenReturn(List.of());

        CrossCampaignReportDto report = service.generateReport(directoryId, from, to, null);

        assertThat(report.avgCompletionDays()).isNotNull();
        assertThat(report.avgCompletionDays()).isCloseTo(10.0, within(1.0));
    }

    // ── buildReviewerMetrics ────────────────────────────────────────────────

    @Test
    void buildReviewerMetrics_aggregatesAcrossCampaigns() {
        Account reviewer1 = makeAccount(UUID.randomUUID(), "reviewer1");
        Account reviewer2 = makeAccount(UUID.randomUUID(), "reviewer2");

        AccessReviewCampaign c = buildCampaign("Q1", CampaignStatus.CLOSED);

        AccessReviewGroup group = new AccessReviewGroup();
        group.setId(UUID.randomUUID());
        group.setCampaign(c);

        AccessReviewDecision d1 = makeDecision(group, reviewer1, ReviewDecision.CONFIRM, c.getStartsAt().plusHours(24));
        AccessReviewDecision d2 = makeDecision(group, reviewer1, ReviewDecision.REVOKE, c.getStartsAt().plusHours(48));
        AccessReviewDecision d3 = makeDecision(group, reviewer2, ReviewDecision.CONFIRM, c.getStartsAt().plusHours(12));

        when(decisionRepo.findDecidedByCampaignIds(List.of(c.getId())))
                .thenReturn(List.of(d1, d2, d3));

        List<ReviewerMetricRow> reviewers = service.buildReviewerMetrics(List.of(c));

        assertThat(reviewers).hasSize(2);

        // reviewer1: 2 decisions (1 confirm, 1 revoke)
        ReviewerMetricRow r1 = reviewers.stream()
                .filter(r -> r.username().equals("reviewer1")).findFirst().orElseThrow();
        assertThat(r1.totalDecisions()).isEqualTo(2);
        assertThat(r1.confirmed()).isEqualTo(1);
        assertThat(r1.revoked()).isEqualTo(1);
        assertThat(r1.revocationRate()).isCloseTo(50.0, within(0.1));

        // reviewer2: 1 decision (1 confirm)
        ReviewerMetricRow r2 = reviewers.stream()
                .filter(r -> r.username().equals("reviewer2")).findFirst().orElseThrow();
        assertThat(r2.totalDecisions()).isEqualTo(1);
        assertThat(r2.revoked()).isZero();
    }

    @Test
    void buildReviewerMetrics_emptyCampaigns_returnsEmpty() {
        List<ReviewerMetricRow> reviewers = service.buildReviewerMetrics(List.of());
        assertThat(reviewers).isEmpty();
    }

    // ── exportCsv ───────────────────────────────────────────────────────────

    @Test
    void exportCsv_returnsValidCsvWithHeaders() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));

        AccessReviewCampaign c = buildCampaign("Q1 Review", CampaignStatus.CLOSED);
        when(campaignRepo.findByDirectoryIdAndCreatedAtBetween(eq(directoryId), any(), any()))
                .thenReturn(List.of(c));
        when(decisionRepo.countTotalByCampaignId(c.getId())).thenReturn(3L);
        when(decisionRepo.countByCampaignIdAndDecision(c.getId(), ReviewDecision.CONFIRM)).thenReturn(2L);
        when(decisionRepo.countByCampaignIdAndDecision(c.getId(), ReviewDecision.REVOKE)).thenReturn(1L);
        when(decisionRepo.findDecidedByCampaignIds(any())).thenReturn(List.of());

        byte[] csv = service.exportCsv(directoryId, from, to, null);
        String content = new String(csv);

        assertThat(content).contains("Campaign,Status,Activated,Completed,Duration Days,Total,Confirmed,Revoked,Pending,Completion %");
        assertThat(content).contains("Q1 Review");
        assertThat(content).contains("Reviewer,Total Decisions,Confirmed,Revoked,Revocation Rate %,Avg Response Hours");
    }

    // ── exportPdf ───────────────────────────────────────────────────────────

    @Test
    void exportPdf_generatesValidPdf() throws IOException {
        stubSettings();
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));

        AccessReviewCampaign c = buildCampaign("Q1 Review", CampaignStatus.CLOSED);
        when(campaignRepo.findByDirectoryIdAndCreatedAtBetween(eq(directoryId), any(), any()))
                .thenReturn(List.of(c));
        when(decisionRepo.countTotalByCampaignId(c.getId())).thenReturn(3L);
        when(decisionRepo.countByCampaignIdAndDecision(c.getId(), ReviewDecision.CONFIRM)).thenReturn(2L);
        when(decisionRepo.countByCampaignIdAndDecision(c.getId(), ReviewDecision.REVOKE)).thenReturn(1L);
        when(decisionRepo.findDecidedByCampaignIds(any())).thenReturn(List.of());

        byte[] pdf = service.exportPdf(directoryId, from, to, null);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(0);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void exportPdf_noCampaigns_generatesValidPdf() throws IOException {
        stubSettings();
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(campaignRepo.findByDirectoryIdAndCreatedAtBetween(eq(directoryId), any(), any()))
                .thenReturn(List.of());

        byte[] pdf = service.exportPdf(directoryId, from, to, null);

        assertThat(pdf).isNotNull();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void stubSettings() {
        ApplicationSettings settings = new ApplicationSettings();
        settings.setAppName("Test LDAP Portal");
        when(settingsService.getEntity()).thenReturn(settings);
    }

    private AccessReviewCampaign buildCampaign(String name, CampaignStatus status) {
        Account creator = makeAccount(UUID.randomUUID(), "admin");
        AccessReviewCampaign c = new AccessReviewCampaign();
        c.setId(UUID.randomUUID());
        c.setDirectory(directory);
        c.setName(name);
        c.setStatus(status);
        c.setCreatedBy(creator);
        c.setCreatedAt(OffsetDateTime.now().minusDays(30));
        c.setDeadline(OffsetDateTime.now().plusDays(30));
        c.setStartsAt(OffsetDateTime.now().minusDays(25));
        if (status == CampaignStatus.CLOSED || status == CampaignStatus.EXPIRED) {
            c.setCompletedAt(OffsetDateTime.now().minusDays(5));
        }
        c.setReviewGroups(new ArrayList<>());
        return c;
    }

    private Account makeAccount(UUID id, String username) {
        Account account = new Account();
        account.setId(id);
        account.setUsername(username);
        return account;
    }

    private AccessReviewDecision makeDecision(AccessReviewGroup group, Account reviewer,
                                               ReviewDecision decision, OffsetDateTime decidedAt) {
        AccessReviewDecision d = new AccessReviewDecision();
        d.setId(UUID.randomUUID());
        d.setReviewGroup(group);
        d.setDecision(decision);
        d.setDecidedBy(reviewer);
        d.setDecidedAt(decidedAt);
        d.setMemberDn("uid=user" + UUID.randomUUID().toString().substring(0, 4) + ",dc=test");
        d.setMemberDisplay("User");
        return d;
    }
}
