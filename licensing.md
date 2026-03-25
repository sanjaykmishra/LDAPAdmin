# Licensing Recommendation — LDAPAdmin

## Approach: Signed License Key Validated at Startup

A self-contained license key that the app validates locally — no phone-home required (enterprise customers behind firewalls will reject license checks that require internet access).

### How It Works

1. **You generate** a signed JSON license key per customer containing:
   - Customer name / org ID
   - Edition (e.g., Standard, Enterprise)
   - Feature flags (SoD, Access Reviews, Drift Detection, etc.)
   - Max directories / max users
   - Expiration date
   - Ed25519 signature

2. **Customer sets** the license key as an environment variable (`LICENSE_KEY`) or mounts a `license.key` file

3. **App validates** the signature at startup using a baked-in public key — no network call needed

4. **Feature gates** check the parsed license to enable/disable features at the controller/service layer

### Why This Approach

| Consideration | Recommendation |
|---|---|
| **No internet required** | Enterprise LDAP environments are often air-gapped or restricted. Phone-home licensing is a dealbreaker. |
| **Tamper-proof** | Asymmetric signature — you sign with a private key, the app verifies with the embedded public key. Customers can't forge or modify the license. |
| **Simple to deliver** | One string in `.env` or one file. No license server to run. |
| **Graceful expiry** | App can warn 30 days before expiration via the dashboard, then degrade to read-only (not hard-stop). |
| **Offline renewals** | Customer sends you their org ID, you send back a new key. No infrastructure needed. |

### What to Gate

Start simple — gate by **edition**, not individual features:

| Gate | Standard | Enterprise |
|---|---|---|
| Directories | 1 | Unlimited |
| Admin accounts | 5 | Unlimited |
| Users managed | 1,000 | Unlimited |
| Access Reviews | - | Yes |
| SoD Policies | - | Yes |
| Access Drift | - | Yes |
| SIEM Export | - | Yes |
| HR Integration | - | Yes |
| Compliance Reports | - | Yes |

### Implementation Sketch

**License structure** (JSON, then base64-encoded with appended signature):
```json
{
  "lid": "a1b2c3d4-...",
  "customer": "Acme Corp",
  "edition": "ENTERPRISE",
  "features": ["SOD", "ACCESS_REVIEW", "DRIFT", "SIEM", "HR", "COMPLIANCE"],
  "maxDirectories": -1,
  "maxUsers": -1,
  "issuedAt": "2026-03-25",
  "expiresAt": "2027-03-25"
}
```

**Backend integration points:**
- `LicenseService` — parses and validates the key at startup, caches the result
- `@RequiresLicense("SOD")` annotation (similar pattern to existing `@RequiresFeature`) — AOP-based check on controllers
- Dashboard warning component when expiry is within 30 days
- `/actuator/health` includes license status for monitoring

### What to Avoid

- **Don't phone home** — kills adoption in restricted environments
- **Don't hard-kill the app on expiry** — let it go read-only with a banner. Hard stops create emergencies and erode trust.
- **Don't tie licenses to MAC/IP addresses** — customers move containers between hosts, and Kubernetes assigns IPs dynamically
- **Don't build a license server** — unnecessary infrastructure for v1. The signed key approach scales to hundreds of customers before you'd ever need one.

---

## Signing Mechanism — Detailed Design

### Key Pair Generation (One-Time, Offline)

Generate an Ed25519 key pair once. Ed25519 is preferred over RSA — keys and signatures are small (fits in a single env var), fast to verify, and no configuration choices to get wrong.

```bash
# Generate private key (keep secret — never in the repo or Docker image)
openssl genpkey -algorithm Ed25519 -out license-private.pem

# Extract public key (this gets embedded in the app)
openssl pkey -in license-private.pem -pubout -out license-public.pem
```

The private key lives on your machine (or a KMS). The public key gets committed into the app source.

### License Key Format

The license key is a single string with two parts separated by a dot:

```
<base64url-encoded-payload>.<base64url-encoded-signature>
```

**Payload** — JSON with the license terms (see structure above).

**Signature** — Ed25519 signature over the exact payload bytes (before base64 encoding).

### Signing (Your Side — Offline CLI Tool)

```
┌─────────────────────────────────────────────────────┐
│  License issuer (your machine / CI)                 │
│                                                     │
│  1. Compose JSON payload                            │
│  2. payloadBytes = UTF-8 bytes of JSON string       │
│  3. signature = Ed25519.sign(privateKey, payload)    │
│  4. licenseKey = base64url(payload) + "."           │
│                + base64url(signature)               │
│  5. Send licenseKey to customer                     │
└─────────────────────────────────────────────────────┘
```

In Java (for a CLI tool you'd run locally):

```java
// Sign
Signature signer = Signature.getInstance("Ed25519");
signer.initSign(privateKey);  // loaded from license-private.pem
signer.update(payloadBytes);
byte[] sig = signer.sign();

String licenseKey = base64url(payloadBytes) + "." + base64url(sig);
```

### Verification (App Side — At Startup)

```
┌─────────────────────────────────────────────────────┐
│  LDAPAdmin app (customer's environment)             │
│                                                     │
│  1. Read LICENSE_KEY env var                         │
│  2. Split on "." → payloadB64, signatureB64         │
│  3. payloadBytes = base64url.decode(payloadB64)     │
│  4. sigBytes = base64url.decode(signatureB64)       │
│  5. Load embedded public key from classpath          │
│  6. Ed25519.verify(publicKey, payloadBytes, sig)    │
│  7. If valid → parse JSON, check expiresAt          │
│  8. If invalid/expired → start in degraded mode     │
└─────────────────────────────────────────────────────┘
```

In Java (what the app runs):

```java
// Verify — no network call, no external dependency
Signature verifier = Signature.getInstance("Ed25519");
verifier.initVerify(publicKey);  // embedded in app
verifier.update(payloadBytes);
boolean valid = verifier.verify(sigBytes);
```

### Why This Is Tamper-Proof

The customer has the **public key** (embedded in the JAR) and their **license key string**. To forge a license they'd need to:

1. **Modify the payload** (e.g., change `expiresAt`) — the signature won't match. Ed25519 verification fails.
2. **Re-sign with a different key** — the app only accepts signatures from your public key. They don't have your private key.
3. **Replace the embedded public key** — requires decompiling and rebuilding the JAR. At that point they've reverse-engineered the app, which is a contractual issue, not a technical one. No DRM scheme survives this level of effort, and enterprise customers don't do this.

```
Customer can read:     payload (JSON) + public key
Customer cannot forge: signature (requires private key)
Customer cannot alter: payload (signature verification fails)
```

### Key Rotation

If your private key is ever compromised:

1. Generate a new key pair
2. Embed the new public key in the next release
3. Re-issue license keys to all customers signed with the new private key
4. Optionally embed both old and new public keys during a transition period (try new key first, fall back to old)

### What the Customer Sees

In their `.env`:
```
LICENSE_KEY=eyJsaWQiOiJhMWIyYzNkNC0uLi4iLCJjdXN0b21lciI6IkFjbWUgQ29ycCIsImVkaXRpb24iOiJFTlRFUlBSSVNFIiwiZmVhdHVyZXMiOlsiU09EIiwiQUNDRVNTX1JFVklFVyJdLCJtYXhEaXJlY3RvcmllcyI6LTEsIm1heFVzZXJzIjotMSwiaXNzdWVkQXQiOiIyMDI2LTAzLTI1IiwiZXhwaXJlc0F0IjoiMjAyNy0wMy0yNSJ9.MEUCIQC7Xz8...
```

One opaque string. No files to manage beyond the `.env` they already have.

---

## Future (v2+)

- **Usage telemetry** (opt-in) — helps understand which features are used for pricing decisions
- **Self-service renewal portal** — customer uploads expiring key, gets new one after payment
- **Floating/concurrent licenses** — if multi-tenant support is added
