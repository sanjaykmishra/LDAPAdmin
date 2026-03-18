package com.ldapadmin.ldap;

import com.ldapadmin.entity.DirectoryConnection;
import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.sdk.*;
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Exports LDAP entries in LDIF format (RFC 2849).
 *
 * <p>Supports single-entry and subtree exports. Binary attribute values are
 * base64-encoded. Results are streamed to an {@link OutputStream} so that
 * large subtrees do not accumulate in memory.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LdifService {

    private final LdapConnectionFactory connectionFactory;

    /**
     * Exports a single entry at {@code dn} as LDIF.
     */
    public void exportEntry(DirectoryConnection dc, String dn, OutputStream out) {
        connectionFactory.withConnection(dc, conn -> {
            try {
                SearchResultEntry entry = conn.getEntry(dn);
                if (entry != null) {
                    Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                    writeEntry(writer, entry);
                    writer.flush();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return null;
        });
    }

    /**
     * Exports entries matching the given scope from {@code baseDn} as LDIF.
     *
     * @param dc     directory connection
     * @param baseDn search base DN
     * @param scope  LDAP search scope (BASE, ONE, SUB)
     * @param out    output stream to write LDIF content to
     */
    public void exportSubtree(DirectoryConnection dc, String baseDn,
                              SearchScope scope, OutputStream out) {
        connectionFactory.withConnection(dc, conn -> {
            try {
                Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                int pageSize = dc.getPagingSize();
                ASN1OctetString cookie = null;
                boolean firstEntry = true;

                do {
                    SearchRequest request = new SearchRequest(
                            baseDn, scope,
                            Filter.createPresenceFilter("objectClass"),
                            "*", "+"); // all user + operational attributes
                    request.addControl(new SimplePagedResultsControl(pageSize, cookie));

                    SearchResult result;
                    try {
                        result = conn.search(request);
                    } catch (LDAPSearchException e) {
                        if (e.getResultCode() == ResultCode.NO_SUCH_OBJECT) {
                            log.debug("Base '{}' does not exist — empty export", baseDn);
                            return null;
                        }
                        throw e;
                    }

                    for (SearchResultEntry entry : result.getSearchEntries()) {
                        if (!firstEntry) {
                            writer.write("\n");
                        }
                        writeEntry(writer, entry);
                        firstEntry = false;
                    }

                    SimplePagedResultsControl pageResponse =
                            SimplePagedResultsControl.get(result);
                    cookie = (pageResponse != null && pageResponse.moreResultsToReturn())
                            ? pageResponse.getCookie()
                            : null;
                } while (cookie != null && cookie.getValue().length > 0);

                writer.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return null;
        });
    }

    /**
     * Writes a single LDAP entry in LDIF format.
     */
    private void writeEntry(Writer writer, SearchResultEntry entry) throws IOException {
        writer.write("dn: ");
        writeLdifValue(writer, entry.getDN());
        writer.write("\n");

        for (Attribute attr : entry.getAttributes()) {
            for (byte[] rawValue : attr.getValueByteArrays()) {
                if (isSafeString(rawValue)) {
                    writer.write(attr.getBaseName());
                    writer.write(": ");
                    writer.write(new String(rawValue, StandardCharsets.UTF_8));
                    writer.write("\n");
                } else {
                    // Base64-encode binary values
                    writer.write(attr.getBaseName());
                    writer.write(":: ");
                    writer.write(Base64.getEncoder().encodeToString(rawValue));
                    writer.write("\n");
                }
            }
        }
    }

    /**
     * Writes a DN or string value, base64-encoding if it contains unsafe characters.
     */
    private void writeLdifValue(Writer writer, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (isSafeString(bytes)) {
            writer.write(value);
        } else {
            writer.write(": ");
            writer.write(Base64.getEncoder().encodeToString(bytes));
        }
    }

    /**
     * Checks whether a byte array is a safe LDIF string (printable ASCII,
     * doesn't start with space/colon/less-than, no NUL bytes).
     */
    private boolean isSafeString(byte[] value) {
        if (value.length == 0) return true;
        byte first = value[0];
        if (first == ' ' || first == ':' || first == '<' || first == '\n' || first == '\r') {
            return false;
        }
        for (byte b : value) {
            if (b == 0 || (b & 0xFF) > 127) {
                return false;
            }
        }
        return true;
    }
}
