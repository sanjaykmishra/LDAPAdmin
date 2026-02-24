package com.ldapadmin.service;

import com.ldapadmin.dto.csv.BulkImportResult;
import com.ldapadmin.dto.csv.BulkImportRowResult;
import com.ldapadmin.dto.csv.CsvColumnMappingDto;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.ConflictHandling;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.model.LdapUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkUserServiceTest {

    @Mock private LdapUserService userService;

    private BulkUserService service;
    private DirectoryConnection dc;

    @BeforeEach
    void setUp() {
        service = new BulkUserService(userService);
        dc = new DirectoryConnection();
        dc.setId(UUID.randomUUID());
        dc.setBaseDn("dc=example,dc=com");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private InputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private LdapUser ldapUser(String dn, Map<String, List<String>> attrs) {
        return new LdapUser(dn, attrs);
    }

    // ── Import — create ───────────────────────────────────────────────────────

    @Test
    void importCsv_createsNewEntries() throws IOException {
        String csvContent = "uid,cn,mail\njsmith,John Smith,jsmith@example.com\n";
        // No existing entry → ResourceNotFoundException
        when(userService.getUser(eq(dc), eq("uid=jsmith,ou=people,dc=example,dc=com")))
                .thenThrow(new ResourceNotFoundException("LDAP user", "uid=jsmith,..."));

        BulkImportResult result = service.importCsv(
                dc, csv(csvContent),
                "ou=people,dc=example,dc=com",
                "uid",
                ConflictHandling.SKIP,
                List.of());

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(0);
        assertThat(result.errors()).isEqualTo(0);
        assertThat(result.rows().get(0).status()).isEqualTo(BulkImportRowResult.Status.CREATED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, List<String>>> attrsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(userService).createUser(eq(dc),
                eq("uid=jsmith,ou=people,dc=example,dc=com"),
                attrsCaptor.capture());
        assertThat(attrsCaptor.getValue()).containsKey("cn");
        assertThat(attrsCaptor.getValue().get("mail")).containsExactly("jsmith@example.com");
    }

    @Test
    void importCsv_skipsExistingEntry_whenConflictHandlingSkip() throws IOException {
        String csvContent = "uid,cn\nexisting,Existing User\n";
        when(userService.getUser(eq(dc), anyString()))
                .thenReturn(ldapUser("uid=existing,ou=people,dc=example,dc=com", Map.of()));

        BulkImportResult result = service.importCsv(
                dc, csv(csvContent),
                "ou=people,dc=example,dc=com",
                "uid",
                ConflictHandling.SKIP,
                List.of());

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(0);
        assertThat(result.rows().get(0).status()).isEqualTo(BulkImportRowResult.Status.SKIPPED);
        verify(userService, never()).createUser(any(), anyString(), any());
        verify(userService, never()).updateUser(any(), anyString(), any());
    }

    @Test
    void importCsv_updatesExistingEntry_whenConflictHandlingOverwrite() throws IOException {
        String csvContent = "uid,cn,mail\nexisting,Updated Name,new@example.com\n";
        when(userService.getUser(eq(dc), anyString()))
                .thenReturn(ldapUser("uid=existing,ou=people,dc=example,dc=com", Map.of()));

        BulkImportResult result = service.importCsv(
                dc, csv(csvContent),
                "ou=people,dc=example,dc=com",
                "uid",
                ConflictHandling.OVERWRITE,
                List.of());

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.rows().get(0).status()).isEqualTo(BulkImportRowResult.Status.UPDATED);
        verify(userService).updateUser(eq(dc), anyString(), any());
        verify(userService, never()).createUser(any(), anyString(), any());
    }

    @Test
    void importCsv_errorRow_whenKeyAttributeMissing() throws IOException {
        // uid column exists in header but has an empty value in this row
        String csvContent = "uid,cn\n,John Smith\n";

        BulkImportResult result = service.importCsv(
                dc, csv(csvContent),
                "ou=people,dc=example,dc=com",
                "uid",
                ConflictHandling.SKIP,
                List.of());

        assertThat(result.errors()).isEqualTo(1);
        assertThat(result.rows().get(0).status()).isEqualTo(BulkImportRowResult.Status.ERROR);
        assertThat(result.rows().get(0).message()).contains("uid");
        verify(userService, never()).createUser(any(), anyString(), any());
    }

    @Test
    void importCsv_multipleRows_countsCorrectly() throws IOException {
        String csvContent = "uid,cn\nnew1,New One\nnew2,New Two\nexist,Existing\n";

        // new1, new2 → not found; exist → found
        when(userService.getUser(eq(dc), eq("uid=new1,ou=p,dc=example,dc=com")))
                .thenThrow(new ResourceNotFoundException("u", "uid=new1,..."));
        when(userService.getUser(eq(dc), eq("uid=new2,ou=p,dc=example,dc=com")))
                .thenThrow(new ResourceNotFoundException("u", "uid=new2,..."));
        when(userService.getUser(eq(dc), eq("uid=exist,ou=p,dc=example,dc=com")))
                .thenReturn(ldapUser("uid=exist,ou=p,dc=example,dc=com", Map.of()));

        BulkImportResult result = service.importCsv(
                dc, csv(csvContent),
                "ou=p,dc=example,dc=com",
                "uid",
                ConflictHandling.SKIP,
                List.of());

        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.errors()).isEqualTo(0);
    }

    // ── Import — column mappings ───────────────────────────────────────────────

    @Test
    void importCsv_columnMappings_mapsHeadersToLdapAttributes() throws IOException {
        String csvContent = "User Login,Full Name\njsmith,John Smith\n";
        when(userService.getUser(eq(dc), anyString()))
                .thenThrow(new ResourceNotFoundException("u", "d"));

        List<CsvColumnMappingDto> mappings = List.of(
                new CsvColumnMappingDto("User Login", "uid", false),
                new CsvColumnMappingDto("Full Name", "cn", false));

        service.importCsv(dc, csv(csvContent),
                "ou=people,dc=example,dc=com", "uid", ConflictHandling.SKIP, mappings);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, List<String>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(userService).createUser(eq(dc),
                eq("uid=jsmith,ou=people,dc=example,dc=com"), captor.capture());
        assertThat(captor.getValue()).containsKey("cn");
        assertThat(captor.getValue().get("cn")).containsExactly("John Smith");
    }

    @Test
    void importCsv_ignoredColumn_notIncludedInAttributes() throws IOException {
        String csvContent = "uid,password,cn\njsmith,secret,John\n";
        when(userService.getUser(eq(dc), anyString()))
                .thenThrow(new ResourceNotFoundException("u", "d"));

        List<CsvColumnMappingDto> mappings = List.of(
                new CsvColumnMappingDto("uid",      "uid",  false),
                new CsvColumnMappingDto("password", null,   true),   // ignored
                new CsvColumnMappingDto("cn",       "cn",   false));

        service.importCsv(dc, csv(csvContent),
                "ou=people,dc=example,dc=com", "uid", ConflictHandling.SKIP, mappings);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, List<String>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(userService).createUser(any(), anyString(), captor.capture());
        assertThat(captor.getValue()).doesNotContainKey("password");
        assertThat(captor.getValue()).containsKey("cn");
    }

    // ── Export ────────────────────────────────────────────────────────────────

    @Test
    void exportCsv_returnsHeaderAndDataRows() throws IOException {
        LdapUser user = ldapUser("uid=jsmith,ou=people,dc=example,dc=com",
                Map.of("cn",   List.of("John Smith"),
                       "mail", List.of("jsmith@example.com")));
        when(userService.searchUsers(eq(dc), anyString(), isNull(), eq("cn"), eq("mail")))
                .thenReturn(List.of(user));

        byte[] csv = service.exportCsv(dc, null, null, List.of("cn", "mail"));

        String output = new String(csv, StandardCharsets.UTF_8);
        assertThat(output).contains("\"dn\",\"cn\",\"mail\"");
        assertThat(output).contains("uid=jsmith,ou=people,dc=example,dc=com");
        assertThat(output).contains("John Smith");
        assertThat(output).contains("jsmith@example.com");
    }

    @Test
    void exportCsv_multiValuedAttribute_pipeJoined() throws IOException {
        LdapUser user = ldapUser("uid=jsmith,ou=people,dc=example,dc=com",
                Map.of("memberof", List.of("cn=admins,dc=example,dc=com",
                                           "cn=users,dc=example,dc=com")));
        when(userService.searchUsers(eq(dc), anyString(), isNull(), eq("memberof")))
                .thenReturn(List.of(user));

        byte[] csv = service.exportCsv(dc, null, null, List.of("memberof"));

        String output = new String(csv, StandardCharsets.UTF_8);
        assertThat(output).contains("cn=admins,dc=example,dc=com|cn=users,dc=example,dc=com");
    }

    @Test
    void exportCsv_emptyResult_returnsHeaderOnly() throws IOException {
        when(userService.searchUsers(eq(dc), anyString(), isNull(), eq("cn"), eq("mail")))
                .thenReturn(List.of());

        byte[] csv = service.exportCsv(dc, null, null, List.of("cn", "mail"));

        String output = new String(csv, StandardCharsets.UTF_8);
        String[] lines = output.split("\n");
        assertThat(lines[0]).contains("dn");
        assertThat(lines[0]).contains("cn");
    }

    @Test
    void exportCsv_defaultFilter_usedWhenFilterNull() throws IOException {
        when(userService.searchUsers(eq(dc), eq("(objectClass=*)"), isNull(), eq("cn")))
                .thenReturn(List.of());

        service.exportCsv(dc, null, null, List.of("cn"));

        verify(userService).searchUsers(eq(dc), eq("(objectClass=*)"), isNull(), eq("cn"));
    }
}
