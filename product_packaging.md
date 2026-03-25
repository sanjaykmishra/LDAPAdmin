# Product Packaging Recommendation — LDAPAdmin v1

## Recommendation: Docker Containers (Customer-Hosted)

### Why Not SaaS

- LDAP servers sit inside corporate networks behind firewalls. A cloud-hosted SaaS would require customers to expose their directory services to the internet or set up VPN tunnels — both are hard sells to security teams.
- The data is highly sensitive (user directories, group memberships, credentials). Enterprise customers managing LDAP are exactly the ones who will resist sending directory data to a third party.
- Latency matters for operations like integrity checks across 5000+ entries — being on the same network as the LDAP server is a significant advantage.

### Why Docker Containers

- A working `docker-compose.yml` already exists with the full stack (app + PostgreSQL).
- Deployment is a single `docker-compose up` with three env vars (`ENCRYPTION_KEY`, `JWT_SECRET`, `BOOTSTRAP_SUPERADMIN_PASSWORD`).
- Customers can run it on any Linux VM, Kubernetes cluster, or container platform they already have.
- Updates are just pulling new image tags.

## v1 Delivery Model

| Component    | Approach                                                                                      |
| ------------ | --------------------------------------------------------------------------------------------- |
| **Packaging** | Published Docker images to a private registry (e.g., AWS ECR, GitHub Container Registry)     |
| **Database**  | Bundled PostgreSQL container for small deployments; customer-managed PostgreSQL for production |
| **Config**    | `.env` file + `docker-compose.yml` — no installer needed                                     |
| **Updates**   | New image tags; Flyway handles DB migrations automatically on startup                        |
| **Licensing** | License key checked at startup or embedded in the image build per customer                   |

## Pre-v1 Shipping Checklist

1. **Health check endpoint** (`/actuator/health`) — customers need monitoring integration.
2. **Backup/restore docs** — PostgreSQL dump instructions for the bundled DB.
3. **TLS termination guidance** — reverse proxy (nginx/Traefik) in front, or configure Spring Boot's embedded TLS.
4. **Image versioning** — semantic versioning on Docker tags so customers can pin versions.

## Future (v2+): Hybrid Model

Once there is traction, consider a **managed control plane** (SaaS dashboard for license management, update notifications, config templates) with the **data plane remaining on-prem** (the Docker containers). This is the pattern tools like HashiCorp, Teleport, and Grafana use for similar on-prem-connected products.
