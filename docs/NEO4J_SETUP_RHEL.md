# Neo4j 5.x Setup on RHEL — KFH AIOps

> Provision the Neo4j server that backs the **topology / banking-flow graph** (Country →
> BusinessDomain → BusinessJourney → Application → Service → Resource + causal paths) and the
> **AlertGroup vector index** (1536-dim cosine). Style mirrors [`REDIS_SETUP_RHEL.md`](./REDIS_SETUP_RHEL.md)
> and [`KAFKA_SETUP_RHEL.md`](./KAFKA_SETUP_RHEL.md).
>
> **Status context:** Neo4j is **Phase 3** (docs/ROADMAP.md — banking flow graph, ⚪ not started). The
> Settings connector + tester exist and the driver is on the classpath, but no topology code consumes
> it yet. This runbook makes the server ready and testable from **Settings → Databases → Neo4j Topology
> Graph**; the ontology gets populated when Phase 3 lands.
>
> **Version rule:** the app ships **neo4j-java-driver 5.15.0**, so run a **Neo4j 5.x** server
> (5.26 LTS recommended; 5.15 matches the README exactly). Do **not** jump to the newer calendar
> releases (2025.x) — keep client and server on the same 5.x line to avoid a Kafka-style skew.

---

## Architecture decisions

| Decision | Choice | Why |
|---|---|---|
| Version | **Neo4j 5.26 LTS** (or 5.15) | Matches the 5.15.0 driver; LTS = longest support; vector indexes GA |
| Edition | **Community** (dev/UAT) | Single `neo4j` DB + label-based country scoping = what the architecture uses; free. Enterprise only if you need RBAC / multi-DB / clustering (licensed) |
| Install | Binary tarball → `/opt/neo4j` | Offline-friendly, matches Redis/Kafka pattern |
| Java | **JDK 21** (already on `utvcapap02`) | Neo4j 5.x supports Java 17 & 21 |
| Data dir | `/opt/neo4j/data` | `/opt` has 44 GB; `/var` is only 9.4 GB (graph data must not fill `/var`) |
| Bind | Private IP only (`172.17.133.47`) | No public exposure; Browser (7474) restricted |
| Auth | `dbms.security.auth_enabled=true`, user `neo4j` | Password set before first start |
| Scoping | Labels/properties (country), single DB | Community has only `neo4j` + `system` DBs; multi-tenancy is by label, per ARCHITECTURE |

---

## Step 0 — Pre-flight

```bash
hostname -I                 # confirm 172.17.133.47
java -version               # Neo4j 5.x needs 17 or 21 — Kafka setup left JDK 21
cat /etc/redhat-release
df -h /opt /var             # ensure /opt has room for graph data
```

---

## Step 1 — Service user & install

```bash
useradd --system --no-create-home --shell /sbin/nologin neo4j
cd /usr/local/src
# copy neo4j-community-5.26.4-unix.tar.gz here (offline), or:
# curl -fSLO https://dist.neo4j.org/neo4j-community-5.26.4-unix.tar.gz
tar xzf neo4j-community-5.26.4-unix.tar.gz
mkdir -p /opt/neo4j
cp -r neo4j-community-5.26.4/* /opt/neo4j/
chown -R neo4j:neo4j /opt/neo4j
sudo -u neo4j /opt/neo4j/bin/neo4j --version
```
- `useradd --system … /sbin/nologin` — unprivileged service account.
- Neo4j ships **compiled** (JVM), so it's a tarball extract, not a build.
- `cp -r … /opt/neo4j` — install under one prefix; `data/`, `logs/`, `conf/` live inside `NEO4J_HOME`.
- `neo4j --version` — confirms the JDK runs it.

---

## Step 2 — Configure `/opt/neo4j/conf/neo4j.conf`

Edit these keys (uncomment/replace as needed):
```properties
# --- Network: bind to the private IP only ---
server.default_listen_address=172.17.133.47
server.bolt.listen_address=172.17.133.47:7687
server.bolt.advertised_address=172.17.133.47:7687
# Neo4j Browser (HTTP) — keep it off the app path; bind private (or 127.0.0.1 for local-only)
server.http.listen_address=172.17.133.47:7474
server.https.enabled=false

# --- Storage ---
server.directories.data=/opt/neo4j/data
server.directories.logs=/opt/neo4j/logs

# --- Security ---
dbms.security.auth_enabled=true

# --- Memory (tune with: bin/neo4j-admin server memory-recommendation) ---
server.memory.heap.initial_size=1g
server.memory.heap.max_size=2g
server.memory.pagecache.size=1g
```
Then lock the config (no secrets in it, but keep it service-owned):
```bash
chown root:neo4j /opt/neo4j/conf/neo4j.conf
chmod 640 /opt/neo4j/conf/neo4j.conf
```
Why the key lines:
- `server.default_listen_address=172.17.133.47` — all connectors bind to the private interface only.
- `server.bolt.advertised_address` — what drivers are told to connect back on; must be the reachable IP (same lesson as Kafka's `advertised.listeners`).
- `server.directories.data=/opt/neo4j/data` — keep the graph on the 44 GB `/opt`, not the tight `/var`.
- Memory: run `sudo -u neo4j /opt/neo4j/bin/neo4j-admin server memory-recommendation` and paste its suggested values for this box.

---

## Step 3 — Set the initial password (before first start)

```bash
sudo -u neo4j /opt/neo4j/bin/neo4j-admin dbms set-initial-password 'CHANGE_ME_NEO4J_PW'
```
- Sets the built-in `neo4j` user's password **before** the store is initialized. If Neo4j has already started once, stop it and use `neo4j-admin dbms set-initial-password --require-password-change=false` or reset via the auth files.

---

## Step 4 — systemd service — `/etc/systemd/system/neo4j.service`

```ini
[Unit]
Description=Neo4j Graph Database - KFH AIOps topology
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=neo4j
Group=neo4j
Environment=JAVA_HOME=/usr/lib/jvm/java-21-openjdk
Environment=NEO4J_HOME=/opt/neo4j
ExecStart=/opt/neo4j/bin/neo4j console
Restart=on-failure
RestartSec=5
TimeoutStartSec=120
LimitNOFILE=60000

[Install]
WantedBy=multi-user.target
```
```bash
systemctl daemon-reload
systemctl enable --now neo4j
systemctl status neo4j --no-pager
ss -tlnp | grep -E '7687|7474'
```
- `neo4j console` runs in the foreground so systemd supervises it directly.
- `LimitNOFILE=60000` — Neo4j needs a high open-file limit (store files + connections).
- `JAVA_HOME` — point at the JDK 21 you used for Kafka (`readlink -f $(which java) | xargs dirname | xargs dirname`).

---

## Step 5 — Firewall

```bash
# Bolt (7687) — only the AIOps app server
firewall-cmd --permanent --add-rich-rule='rule family="ipv4" source address="APP_SERVER_IP/32" port port="7687" protocol="tcp" accept'
# Browser (7474) — only an admin workstation (optional); otherwise leave it closed / local
firewall-cmd --permanent --add-rich-rule='rule family="ipv4" source address="ADMIN_WORKSTATION_IP/32" port port="7474" protocol="tcp" accept'
firewall-cmd --reload
```
- Expose **7687 (bolt)** to the app only. Keep **7474 (Browser)** closed to the network or limited to an admin box — it's an admin console, not an app dependency.

---

## Step 6 — Verify

```bash
NEO=/opt/neo4j/bin/cypher-shell
$NEO -a bolt://172.17.133.47:7687 -u neo4j -p 'CHANGE_ME_NEO4J_PW' "RETURN 1 AS ok;"
$NEO -a bolt://172.17.133.47:7687 -u neo4j -p 'CHANGE_ME_NEO4J_PW' "SHOW DATABASES;"
```
- `RETURN 1` → confirms bolt + auth work over the **private IP** (like the app connects).
- `SHOW DATABASES` → expect `neo4j` (online) + `system`. (Community has exactly these two.)

---

## Step 7 — Project alignment: vector index + constraints

Create the AlertGroup embedding index the platform expects (1536-dim cosine, per README), plus a couple of topology uniqueness constraints:
```bash
$NEO -a bolt://172.17.133.47:7687 -u neo4j -p 'CHANGE_ME_NEO4J_PW' "
CREATE VECTOR INDEX alertGroupEmbedding IF NOT EXISTS
FOR (a:AlertGroup) ON (a.embedding)
OPTIONS { indexConfig: { \`vector.dimensions\`: 1536, \`vector.similarity_function\`: 'cosine' } };
"
$NEO -a bolt://172.17.133.47:7687 -u neo4j -p 'CHANGE_ME_NEO4J_PW' "
CREATE CONSTRAINT app_external_id IF NOT EXISTS FOR (a:Application) REQUIRE a.externalId IS UNIQUE;
CREATE CONSTRAINT resource_external_id IF NOT EXISTS FOR (r:Resource) REQUIRE r.externalId IS UNIQUE;
"
$NEO -a bolt://172.17.133.47:7687 -u neo4j -p 'CHANGE_ME_NEO4J_PW' "SHOW INDEXES;"
```
- The vector index requires **Neo4j 5.11+** (5.26 ✅). It backs semantic AlertGroup similarity.
- The full ontology (`Country / BusinessDomain / BusinessJourney / Application / Service / ApiEndpoint / Server / Database / Storage / NetworkDevice / NetworkLink / ExternalSystem / Team / IncidentRef / AlertGroup / EvidenceRef`) is upserted by the app in **Phase 3** — you don't seed it by hand here.

---

## Step 8 — Add it in the AIOps UI

**Settings → Databases → Neo4j Topology Graph → Edit:**

| Field | Value |
|---|---|
| Bolt URL | `bolt://172.17.133.47:7687` ⚠️ private IP, **not** `localhost` |
| User | `neo4j` |
| Password | your `neo4j` password |
| Database | `neo4j` |
| Country scope | KW / BH / EG / ALL (as needed) |
| Health indicator | on (optional; surfaces on `/actuator/health` + dashboard) |

Click **Test & Save** → the backend uses the neo4j-java-driver to open a bolt session and verify. Secrets are encrypted server-side and never returned.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `ServiceUnavailable` / connection refused | Bolt not reachable | Check `server.bolt.listen_address`, firewall 7687, `systemctl status neo4j` |
| `The client is unauthorized` / auth rate limit | Wrong password / too many attempts | Verify password; wait out `dbms.security.auth_lock_time`; reset via `neo4j-admin dbms set-initial-password` (stop first) |
| Test fails only with `localhost` | Using loopback | Use `172.17.133.47` |
| `advertised_address` wrong | Driver connects to bootstrap then can't reach the advertised host | Set `server.bolt.advertised_address=172.17.133.47:7687` |
| Vector index syntax error | Neo4j < 5.11 | Use 5.15/5.26 |
| Service won't start, `UnsupportedClassVersion` | Wrong Java | Point `JAVA_HOME` at JDK 17/21 |

### Diagnostics
```bash
systemctl status neo4j --no-pager
journalctl -u neo4j --no-pager -n 80
tail -100 /opt/neo4j/logs/neo4j.log
tail -100 /opt/neo4j/logs/debug.log
ss -tlnp | grep -E '7687|7474'
```

---

## Follow-up tasks
- **Rotate** the `neo4j` password to a vault value before production (dev password stays out of git / gets rotated).
- **Enterprise** if you need per-country RBAC roles, online backups, or clustering (Community lacks these).
- **Backups:** `neo4j-admin database dump neo4j --to-path=/opt/neo4j-backups` on a schedule; copy off-box.
- **Monitoring:** enable metrics (`server.metrics.prometheus.enabled=true`, `server.metrics.prometheus.endpoint=172.17.133.47:2004`) and scrape via Site24x7 / BMC Helix.
- **Phase 3 wiring:** a runtime topology service (blast-radius / upstream / journey-impact Cypher) that consumes this connection — not built yet.

---

## Change log
| Date | Author | Change |
|---|---|---|
| 2026-07-01 | — | Initial Neo4j 5.x (Community, KRaft-era) runbook for KFH AIOps topology graph (Phase 3 prep) |
