package com.ldapadmin.service;

import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.AccountRole;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.entity.enums.ReviewDecision;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.ldap.model.LdapGroup;
import com.ldapadmin.repository.*;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Generates styled PDF compliance reports using OpenPDF.
 *
 * <p>Three report types are supported:
 * <ol>
 *   <li><b>User Access Report</b> — LDAP group memberships per group</li>
 *   <li><b>Access Review Summary</b> — campaign decision breakdown</li>
 *   <li><b>Privileged Account Inventory</b> — admin accounts and roles</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfReportService {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm Z");
    private static final Font TITLE_FONT =
            new Font(Font.HELVETICA, 18, Font.BOLD, new Color(33, 37, 41));
    private static final Font SUBTITLE_FONT =
            new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(108, 117, 125));
    private static final Font HEADER_FONT =
            new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font CELL_FONT =
            new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(33, 37, 41));
    private static final Font SECTION_FONT =
            new Font(Font.HELVETICA, 13, Font.BOLD, new Color(33, 37, 41));
    private static final Color HEADER_BG = new Color(52, 58, 64);
    private static final Color ALT_ROW_BG = new Color(248, 249, 250);

    private final ApplicationSettingsService settingsService;
    private final LdapGroupService ldapGroupService;
    private final DirectoryConnectionRepository directoryRepo;
    private final AccessReviewCampaignRepository campaignRepo;
    private final AccountRepository accountRepo;
    private final AdminProfileRoleRepository profileRoleRepo;
    private final AdminFeaturePermissionRepository featurePermRepo;

    private static final int MAX_GROUPS = 500;

    /**
     * Validates a campaign belongs to a directory. Used by controllers for authorization.
     */
    @Transactional(readOnly = true)
    public AccessReviewCampaign getCampaignForDirectory(UUID directoryId, UUID campaignId) {
        AccessReviewCampaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));
        if (!campaign.getDirectory().getId().equals(directoryId)) {
            throw new IllegalArgumentException("Campaign does not belong to directory: " + directoryId);
        }
        return campaign;
    }

    // ── User Access Report ─────────────────────────────────────────────────

    /**
     * Generates a PDF showing group memberships for a directory.
     *
     * @param directoryId  target directory
     * @param groupDnFilter optional; restricts to a single group DN
     * @return PDF bytes
     */
    @Transactional(readOnly = true)
    public byte[] generateUserAccessReport(UUID directoryId, String groupDnFilter) throws IOException {
        DirectoryConnection dc = directoryRepo.findById(directoryId)
                .orElseThrow(() -> new IllegalArgumentException("Directory not found: " + directoryId));

        List<LdapGroup> groups;
        if (groupDnFilter != null && !groupDnFilter.isBlank()) {
            LdapGroup group = ldapGroupService.getGroup(dc, groupDnFilter, "cn", "member", "uniqueMember", "memberUid");
            groups = List.of(group);
        } else {
            groups = ldapGroupService.searchGroups(dc,
                    "(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames)(objectClass=posixGroup))",
                    null, MAX_GROUPS, "cn", "member", "uniqueMember", "memberUid");
            if (groups.size() >= MAX_GROUPS) {
                log.warn("User access report hit the {} group limit — results may be truncated", MAX_GROUPS);
            }
        }

        // Build row data: one row per group+member pair
        List<String> headers = List.of("Group DN", "Group Name", "Member");
        List<List<String>> rows = new ArrayList<>();
        for (LdapGroup group : groups) {
            List<String> members = group.getAllMembers();
            if (members.isEmpty()) {
                rows.add(List.of(group.getDn(), safe(group.getCn()), "(no members)"));
            } else {
                for (String member : members) {
                    rows.add(List.of(group.getDn(), safe(group.getCn()), member));
                }
            }
        }

        String subtitle = groupDnFilter != null && !groupDnFilter.isBlank()
                ? "Filtered by group: " + groupDnFilter
                : "All groups in directory";

        return buildPdf("User Access Report", subtitle, headers, rows);
    }

    // ── Access Review Summary ──────────────────────────────────────────────

    /**
     * Generates a PDF summarizing an access review campaign's decisions.
     *
     * @param campaignId target campaign
     * @return PDF bytes
     */
    @Transactional(readOnly = true)
    public byte[] generateAccessReviewSummary(UUID campaignId) throws IOException {
        AccessReviewCampaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));

        // Campaign metadata section
        List<String> metaHeaders = List.of("Field", "Value");
        List<List<String>> metaRows = new ArrayList<>();
        metaRows.add(List.of("Campaign Name", safe(campaign.getName())));
        metaRows.add(List.of("Status", campaign.getStatus().name()));
        metaRows.add(List.of("Created", formatDateTime(campaign.getCreatedAt())));
        metaRows.add(List.of("Deadline", formatDateTime(campaign.getDeadline())));
        if (campaign.getCompletedAt() != null) {
            metaRows.add(List.of("Completed", formatDateTime(campaign.getCompletedAt())));
        }

        // Decision details table
        List<String> decisionHeaders = List.of("Group DN", "Member", "Decision", "Reviewer", "Decided At", "Comment");
        List<List<String>> decisionRows = new ArrayList<>();
        int confirmed = 0, revoked = 0, pending = 0;

        for (AccessReviewGroup reviewGroup : campaign.getReviewGroups()) {
            String groupDn = safe(reviewGroup.getGroupDn());
            String reviewerName = reviewGroup.getReviewer() != null
                    ? safe(reviewGroup.getReviewer().getUsername()) : "";
            for (AccessReviewDecision decision : reviewGroup.getDecisions()) {
                if (decision.getDecision() == null) {
                    pending++;
                    decisionRows.add(List.of(groupDn, safe(decision.getMemberDisplay()),
                            "PENDING", reviewerName, "", ""));
                } else {
                    if (decision.getDecision() == ReviewDecision.CONFIRM) confirmed++;
                    else revoked++;
                    String decidedBy = decision.getDecidedBy() != null
                            ? safe(decision.getDecidedBy().getUsername()) : reviewerName;
                    decisionRows.add(List.of(groupDn, safe(decision.getMemberDisplay()),
                            decision.getDecision().name(), decidedBy,
                            formatDateTime(decision.getDecidedAt()),
                            safe(decision.getComment())));
                }
            }
        }

        metaRows.add(List.of("Confirmed", String.valueOf(confirmed)));
        metaRows.add(List.of("Revoked", String.valueOf(revoked)));
        metaRows.add(List.of("Pending", String.valueOf(pending)));

        String subtitle = "Campaign: " + safe(campaign.getName());
        return buildPdfMultiSection("Access Review Summary", subtitle,
                List.of("Campaign Details", "Decision Details"),
                List.of(metaHeaders, decisionHeaders),
                List.of(metaRows, decisionRows));
    }

    // ── Privileged Account Inventory ───────────────────────────────────────

    /**
     * Generates a PDF listing all admin and superadmin accounts with their roles.
     *
     * @return PDF bytes
     */
    @Transactional(readOnly = true)
    public byte[] generatePrivilegedAccountInventory() throws IOException {
        List<Account> admins = accountRepo.findAllByRole(AccountRole.ADMIN);
        List<Account> superadmins = accountRepo.findAllByRole(AccountRole.SUPERADMIN);

        List<String> headers = List.of("Username", "Display Name", "Role", "Auth Type",
                "Active", "Last Login", "Profile Access", "Feature Overrides");
        List<List<String>> rows = new ArrayList<>();

        List<Account> allAccounts = new ArrayList<>(superadmins);
        allAccounts.addAll(admins);

        for (Account account : allAccounts) {
            // Profile roles
            List<AdminProfileRole> roles = profileRoleRepo.findAllByAdminAccountId(account.getId());
            String profileAccess = roles.isEmpty() ? "(none)" : roles.stream()
                    .map(r -> safe(r.getProfile().getName()) + " [" + r.getBaseRole() + "]")
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("(none)");

            // Feature overrides
            List<AdminFeaturePermission> perms = featurePermRepo.findAllByAdminAccountId(account.getId());
            String featureOverrides = perms.isEmpty() ? "(none)" : perms.stream()
                    .map(p -> p.getFeatureKey().getDbValue() + "=" + (p.isEnabled() ? "ON" : "OFF"))
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("(none)");

            rows.add(List.of(
                    safe(account.getUsername()),
                    safe(account.getDisplayName()),
                    account.getRole().name(),
                    account.getAuthType().name(),
                    account.isActive() ? "Yes" : "No",
                    account.getLastLoginAt() != null ? account.getLastLoginAt().toString() : "Never",
                    profileAccess,
                    featureOverrides
            ));
        }

        return buildPdf("Privileged Account Inventory",
                "All administrator and superadmin accounts", headers, rows);
    }

    // ── PDF construction helpers ───────────────────────────────────────────

    /**
     * Builds a single-section PDF with a header, subtitle, and data table.
     */
    byte[] buildPdf(String title, String subtitle,
                    List<String> headers, List<List<String>> rows) throws IOException {
        return buildPdfMultiSection(title, subtitle,
                List.of(), List.of(headers), List.of(rows));
    }

    /**
     * Builds a multi-section PDF document.
     */
    byte[] buildPdfMultiSection(String title, String subtitle,
                                List<String> sectionTitles,
                                List<List<String>> headersList,
                                List<List<List<String>>> rowsList) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);

        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            // Branding header
            ApplicationSettings settings = settingsService.getEntity();
            String appName = settings.getAppName() != null ? settings.getAppName() : "LDAP Portal";

            Paragraph titlePara = new Paragraph(title, TITLE_FONT);
            titlePara.setSpacingAfter(4);
            document.add(titlePara);

            Paragraph subtitlePara = new Paragraph(
                    appName + "  |  " + subtitle + "  |  Generated: "
                            + OffsetDateTime.now().format(DISPLAY_FMT), SUBTITLE_FONT);
            subtitlePara.setSpacingAfter(16);
            document.add(subtitlePara);

            for (int s = 0; s < headersList.size(); s++) {
                if (s < sectionTitles.size() && !sectionTitles.get(s).isEmpty()) {
                    Paragraph sectionPara = new Paragraph(sectionTitles.get(s), SECTION_FONT);
                    sectionPara.setSpacingBefore(12);
                    sectionPara.setSpacingAfter(6);
                    document.add(sectionPara);
                }

                List<String> headers2 = headersList.get(s);
                List<List<String>> rows2 = rowsList.get(s);

                PdfPTable table = new PdfPTable(headers2.size());
                table.setWidthPercentage(100);
                table.setSpacingBefore(4);

                // Header row
                for (String header : headers2) {
                    PdfPCell cell = new PdfPCell(new Phrase(header, HEADER_FONT));
                    cell.setBackgroundColor(HEADER_BG);
                    cell.setPadding(6);
                    table.addCell(cell);
                }

                // Data rows
                for (int r = 0; r < rows2.size(); r++) {
                    List<String> row = rows2.get(r);
                    Color bg = (r % 2 == 1) ? ALT_ROW_BG : Color.WHITE;
                    for (int c = 0; c < headers2.size(); c++) {
                        String value = c < row.size() ? row.get(c) : "";
                        PdfPCell cell = new PdfPCell(new Phrase(value, CELL_FONT));
                        cell.setBackgroundColor(bg);
                        cell.setPadding(5);
                        table.addCell(cell);
                    }
                }

                if (rows2.isEmpty()) {
                    PdfPCell emptyCell = new PdfPCell(
                            new Phrase("No data available", CELL_FONT));
                    emptyCell.setColspan(headers2.size());
                    emptyCell.setPadding(8);
                    emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    table.addCell(emptyCell);
                }

                document.add(table);
            }

            document.close();
        } catch (DocumentException e) {
            throw new IOException("Failed to generate PDF: " + e.getMessage(), e);
        }

        return baos.toByteArray();
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    private static String formatDateTime(OffsetDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(DISPLAY_FMT);
    }
}
