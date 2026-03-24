package com.ldapadmin.controller.directory;

import com.ldapadmin.auth.DirectoryId;
import com.ldapadmin.auth.RequiresFeature;
import com.ldapadmin.dto.accessreview.CrossCampaignReportDto;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.service.CrossCampaignReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/directories/{directoryId}/access-reviews/cross-campaign-report")
@RequiredArgsConstructor
public class CrossCampaignReportController {

    private final CrossCampaignReportService reportService;

    @GetMapping
    @RequiresFeature(FeatureKey.REPORTS_RUN)
    public CrossCampaignReportDto getReport(
            @DirectoryId @PathVariable UUID directoryId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) CampaignStatus status) {
        return reportService.generateReport(directoryId, from, to, status);
    }

    @GetMapping("/export")
    @RequiresFeature(FeatureKey.REPORTS_RUN)
    public ResponseEntity<byte[]> export(
            @DirectoryId @PathVariable UUID directoryId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) CampaignStatus status,
            @RequestParam(defaultValue = "csv") String format) throws IOException {

        HttpHeaders headers = new HttpHeaders();
        byte[] data;

        if ("pdf".equalsIgnoreCase(format)) {
            data = reportService.exportPdf(directoryId, from, to, status);
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(
                    ContentDisposition.attachment().filename("cross-campaign-report.pdf").build());
        } else {
            data = reportService.exportCsv(directoryId, from, to, status);
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.setContentDisposition(
                    ContentDisposition.attachment().filename("cross-campaign-report.csv").build());
        }

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }
}
