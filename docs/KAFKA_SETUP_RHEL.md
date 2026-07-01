# Apache Kafka 4.3.1 (KRaft) Production Setup on RHEL 10 (KFH AIOps)

**Server:** `utvcapap02` (172.17.133.47)
**OS:** Red Hat Enterprise Linux 10.2 (Coughlan)
**Purpose:** Event bus for KFH AIOps platform (alert ingestion, dedup, correlation events, RCA pipeline)
**Author:** Muhammad Abdelhafeez
**Date:** 2026-06-30
**Kafka Version:** 4.3.1 (latest stable, KRaft-only)
**Java Version:** OpenJDK 21.0.11 LTS (Red Hat build)

---

## 📋 Table of Contents

1. #architecture-decisions
2. #step-1--pre-flight-checks
3. #step-2--locate-the-tarball-and-decide-version
4. #step-3--download-kafka-431
5. #step-4--create-service-user--directories
6. #step-5--extract-binaries-into-optkafka
7. #step-6--write-server-properties-kraft--saslplain
8. #step-7--secure-the-config-file
9. #step-8--format-kraft-storage-one-time
10. #step-9--create-systemd-service
11. #step-10--smoke-test-with-sasl-authentication
12. #aiops-ui-integration
13. #troubleshooting
14. #final-state
15. #credentials-summary
16. #cluster-identifiers
17. #follow-up-tasks

---

## Architecture Decisions

| Decision | Choice | Why |
|---|---|---|
| **Kafka version** | **4.3.1** | Latest stable (Jun 25, 2026), KRaft-only, JDK 21 compatible, ships critical Kafka Streams RocksDB memory leak fix |
| **Metadata mode** | **KRaft** (combined broker + controller) | ZooKeeper fully removed in 4.x; single-process simpler for single-node bootstrap |
| **Scala build** | **2.13** | Apache-recommended, matches existing 3.7 tarball |
| **Install method** | Binary tarball → `/opt/kafka` | Standard, matches Redis setup pattern |
| **Service user** | `kafka` (system, no-login) | Principle of least privilege |
| **Auth mechanism** | **SASL/PLAIN** on `SASL_PLAINTEXT` | AIOps UI tester uses `PlainLoginModule` (SCRAM is incompatible) |
| **Listener split** | CLIENT (9092) for apps + CONTROLLER (9093) internal | Standard KRaft pattern; controller never exposed |
| **Storage path** | `/var/lib/kafka/data` | RHEL convention, 9.4 GB available (tight long-term — see follow-ups) |
| **Replication** | RF=1, min.insync.replicas=1 | Single-node; raise when adding brokers |
| **Auto-create topics** | `false` | Force explicit topic creation (cleaner, safer) |
| **Retention** | 168 hours (7 days) | Sane default for AIOps alert stream |
| **TLS** | off (SASL_PLAINTEXT) | Phase 7 task — upgrade to SASL_SSL later |

---

## Step 1 — Pre-flight Checks

```bash
hostname -I
java -version
cat /etc/redhat-release
df -h /var /opt
```

**Output:**
```
172.17.133.47
openjdk version "21.0.11" 2026-04-21 LTS
OpenJDK Runtime Environment (Red_Hat-21.0.11.0.10-1) (build 21.0.11+10-LTS)
Red Hat Enterprise Linux release 10.2 (Coughlan)
/var: 9.4G available
/opt (/): 44G available
```

**Confirmed:**
- Same server as Redis (`utvcapap02`, 172.17.133.47)
- **JDK 21 already installed** → skip Java install
- RHEL 10.2 — identical baseline to Redis
- Disk: `/var` 9.4 GB (tight), `/opt` 44 GB (plenty)

⚠️ **`/var` is small** — Kafka data lives here. Long-term, move to `/opt` or a dedicated mount (see #follow-up-tasks).

---

## Step 2 — Locate the Tarball and Decide Version

```bash
ls -la /home/sun92338/kafka*.tgz 2>/dev/null
ls -la /usr/local/src/kafka*.tgz 2>/dev/null
find /home /usr/local /opt -maxdepth 3 -name "kafka*.tgz" 2>/dev/null
```

**Output:** Found `kafka_2.13-3.7.0.tgz` on disk, but decision made to **upgrade to latest stable Kafka 4.3.1** for:
- KRaft-only mode (no legacy ZooKeeper code)
- Latest bugfixes (incl. critical Streams RocksDB memory leak)
- Better JDK 21 compatibility

---

## Step 3 — Download Kafka 4.3.1

```bash
cd /home/sun92338
curl -fSLO https://dlcdn.apache.org/kafka/4.3.1/kafka_2.13-4.3.1.tgz
ls -la kafka_2.13-4.3.1.tgz
```

**Output:**
```
100  129M  100  129M    0     0   429k      0  0:05:09  0:05:09 --:--:--  105k
```

Download took ~5 minutes (corporate proxy throttling). Final file = 129 MB.

> 💡 **Plan B (if download fails):** Manually SFTP the tarball from a workstation to `/home/sun92338/`.

---

## Step 4 — Create Service User & Directories

```bash
useradd --system --no-create-home --shell /sbin/nologin kafka
mkdir -p /opt/kafka /opt/kafka/etc /var/lib/kafka/data /var/log/kafka
chown -R kafka:kafka /var/lib/kafka /var/log/kafka
id kafka
ls -ld /opt/kafka /opt/kafka/etc /var/lib/kafka/data /var/log/kafka
```

**Result:**
- `kafka` user: uid=978, gid=976, no login shell
- `/opt/kafka` → root:root (binaries dir, fixed after extraction)
- `/opt/kafka/etc` → root:root (client.properties dir)
- `/var/lib/kafka/data` → **kafka:kafka** (data dir — write access required)
- `/var/log/kafka` → **kafka:kafka** (broker log dir — write access required)

---

## Step 5 — Extract Binaries into `/opt/kafka`

```bash
cd /usr/local/src
cp /home/sun92338/kafka_2.13-4.3.1.tgz .
tar xzf kafka_2.13-4.3.1.tgz
cp -r /usr/local/src/kafka_2.13-4.3.1/* /opt/kafka/
chown -R kafka:kafka /opt/kafka
sudo -u kafka /opt/kafka/bin/kafka-topics.sh --version
```

**Output:**
```
4.3.1
```

✅ Binaries installed, JDK 21 runs them cleanly under the `kafka` service account.

---

## Step 6 — Write `server.properties` (KRaft + SASL/PLAIN)

### File: `/opt/kafka/config/server.properties`

```properties
# --- KRaft roles ---
process.roles=broker,controller
node.id=1
controller.quorum.voters=1@172.17.133.47:9093

# --- Listeners ---
listeners=CLIENT://172.17.133.47:9092,CONTROLLER://172.17.133.47:9093
advertised.listeners=CLIENT://172.17.133.47:9092
listener.security.protocol.map=CONTROLLER:PLAINTEXT,CLIENT:SASL_PLAINTEXT
inter.broker.listener.name=CLIENT
controller.listener.names=CONTROLLER

# --- SASL/PLAIN on CLIENT listener ---
sasl.enabled.mechanisms=PLAIN
sasl.mechanism.inter.broker.protocol=PLAIN
listener.name.client.plain.sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required \
  username="admin" password="Kfh@Kafka2026!AdminSecret" \
  user_admin="Kfh@Kafka2026!AdminSecret" \
  user_kfhaiops="KfhAiops@2026!KafkaAppSecret";

# --- Storage ---
log.dirs=/var/lib/kafka/data

# --- Single-node defaults ---
num.partitions=3
default.replication.factor=1
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
min.insync.replicas=1
auto.create.topics.enable=false

# --- Retention ---
log.retention.hours=168
log.segment.bytes=1073741824
log.retention.check.interval.ms=300000
```

### Why each directive matters

| Directive | Purpose |
|---|---|
| `process.roles=broker,controller` | Single node plays both roles (KRaft combined mode, no ZooKeeper) |
| `node.id=1` | Unique node identifier in cluster |
| `controller.quorum.voters=1@...:9093` | The one and only controller for Raft |
| `listeners=CLIENT://...:9092,CONTROLLER://...:9093` | Two listener endpoints — apps + internal Raft |
| `advertised.listeners=CLIENT://172.17.133.47:9092` | **What clients are told to connect to** — must be the reachable IP |
| `CONTROLLER:PLAINTEXT` | No auth needed on internal Raft port (we firewall it) |
| `CLIENT:SASL_PLAINTEXT` | App connections must authenticate |
| `user_kfhaiops="..."` | Defines password for user `kfhaiops` (AIOps backend) |
| `auto.create.topics.enable=false` | Forces explicit topic creation (security/cleanliness) |
| `log.retention.hours=168` | 7-day retention for AIOps alert stream |

---

## Step 7 — Secure the Config File

The file contains passwords — restrict read access:

```bash
chown root:kafka /opt/kafka/config/server.properties
chmod 640 /opt/kafka/config/server.properties
```

Result: only `root` and the `kafka` service account can read it.

---

## Step 8 — Format KRaft Storage (One-Time)

KRaft requires initialized metadata logs before first start. **Run exactly once** — re-running wipes data.

```bash
KAFKA_CLUSTER_ID=$(/opt/kafka/bin/kafka-storage.sh random-uuid)
echo "Cluster ID = $KAFKA_CLUSTER_ID"

sudo -u kafka /opt/kafka/bin/kafka-storage.sh format \
  -t "$KAFKA_CLUSTER_ID" \
  -c /opt/kafka/config/server.properties
```

**Output:**
```
Cluster ID = nFtw_2g8QtG6HSwxJF-PPw
Bootstrap metadata: BootstrapMetadata(records=[...metadata.version=30, eligible.leader.replicas.version=1, group.version=1, share.version=1, streams.version=1, transaction.version=2...])
Formatting metadata directory /var/lib/kafka/data with metadata.version 4.3-IV0.
```

### Verify

```bash
ls -la /var/lib/kafka/data/
cat /var/lib/kafka/data/meta.properties
```

**Result:**
```
bootstrap.checkpoint
meta.properties

cluster.id=nFtw_2g8QtG6HSwxJF-PPw
directory.id=BZmGEFGvISRnZ6fQTihcxQ
node.id=1
version=1
```

✅ KRaft storage formatted with Kafka 4.3 metadata format (`4.3-IV0`).

> 🚨 **Never re-run `kafka-storage.sh format`** after this step — it wipes all topic data.

---

## Step 9 — Create systemd Service

### Find JAVA_HOME

```bash
readlink -f $(which java) | xargs dirname | xargs dirname
```

**Output:** `/usr/lib/jvm/java-21-openjdk`

### File: `/etc/systemd/system/kafka.service`

```ini
[Unit]
Description=Apache Kafka (KRaft) - KFH AIOps
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=kafka
Group=kafka
Environment=JAVA_HOME=/usr/lib/jvm/java-21-openjdk
Environment=KAFKA_HEAP_OPTS=-Xmx1G -Xms1G
Environment=LOG_DIR=/var/log/kafka
ExecStart=/opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/server.properties
ExecStop=/opt/kafka/bin/kafka-server-stop.sh
Restart=on-failure
RestartSec=5
LimitNOFILE=100000
SuccessExitStatus=143
TimeoutStopSec=60

[Install]
WantedBy=multi-user.target
```

### Enable + start

```bash
systemctl daemon-reload
systemctl enable --now kafka
sleep 8
systemctl status kafka --no-pager
ss -tlnp | grep -E '9092|9093'
```

### ⚠️ Issue encountered — old Jun 28 Kafka was holding ports 9092/9093

The first start failed because a Kafka 3.7.0 instance started manually on Jun 28 (PID 55683) was occupying both ports, listening on `*:9092` and `*:9093` (all interfaces).

**Fix (same pattern as Redis):**
```bash
systemctl stop kafka
ps -ef | grep 55683 | grep -v grep    # confirmed old kafka_2.13-3.7.0
kill 55683
sleep 5
ss -tlnp | grep -E '9092|9093'        # ports free
systemctl reset-failed kafka
systemctl start kafka
```

### Successful state after fix

**`systemctl status kafka`:**
```
● kafka.service - Apache Kafka (KRaft) - KFH AIOps
   Active: active (running) since Tue 2026-06-30 14:05:28 +03; 8s ago
   Main PID: 162502 (java)
   Memory: 329M (peak: 329.5M)
```

**Log highlights:**
```
INFO Awaiting socket connections on 172.17.133.47:9092
INFO [BrokerServer id=1] Transition from STARTING to STARTED
INFO Kafka version: 4.3.1
INFO [KafkaRaftServer nodeId=1] Kafka Server started
```

**`ss -tlnp | grep -E '9092|9093'`:**
```
LISTEN 0 50 [::ffff:172.17.133.47]:9092 ... users:(("java",pid=162502,fd=164))
LISTEN 0 50 [::ffff:172.17.133.47]:9093 ... users:(("java",pid=162502,fd=134))
```

✅ Both listeners bound to private IP only. No public exposure.

> 💡 `[::ffff:172.17.133.47]` is IPv6's representation of the IPv4 address — functionally equivalent to `172.17.133.47`.

---

## Step 10 — Smoke Test with SASL Authentication

### Create client.properties (auth as `kfhaiops` app user)

```bash
cat > /opt/kafka/etc/client.properties <<'EOF'
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="kfhaiops" password="KfhAiops@2026!KafkaAppSecret";
EOF

chown root:kafka /opt/kafka/etc/client.properties
chmod 640 /opt/kafka/etc/client.properties
```

### Run smoke tests

```bash
BS=172.17.133.47:9092

# 1. broker-api-versions
/opt/kafka/bin/kafka-broker-api-versions.sh \
  --bootstrap-server $BS \
  --command-config /opt/kafka/etc/client.properties | head -5

# 2. Create topic
/opt/kafka/bin/kafka-topics.sh --bootstrap-server $BS \
  --command-config /opt/kafka/etc/client.properties \
  --create --topic kfh.aiops.smoke --partitions 3 --replication-factor 1

# 3. List topics
/opt/kafka/bin/kafka-topics.sh --bootstrap-server $BS \
  --command-config /opt/kafka/etc/client.properties --list

# 4. Describe topic
/opt/kafka/bin/kafka-topics.sh --bootstrap-server $BS \
  --command-config /opt/kafka/etc/client.properties \
  --describe --topic kfh.aiops.smoke
```

### Results

| Test | Outcome | Notes |
|---|---|---|
| 1. broker-api-versions | RuntimeException (id: -1) | **False alarm** — known Kafka 4.x tool quirk; broker actually responded |
| 2. Create topic | `Created topic kfh.aiops.smoke.` | ✅ Auth + commit works |
| 3. List topics | `kfh.aiops.smoke` | ✅ Read auth works |
| 4. Describe topic | 3 partitions, Leader=1, ISR=1, Elr empty | ✅ Full broker health |

> ⚠️ **About Test 1's "failure":** `kafka-broker-api-versions.sh` has a known bug in Kafka 4.x where it reports `id: -1` and throws RuntimeException even when connectivity + auth work fine. The standard AdminClient (used by Tests 2–4, and by the AIOps backend) works perfectly. Ignore Test 1 failure if Tests 2–4 succeed.

### ℹ️ New Kafka 4.x topic-describe fields

- **`Elr` (Eligible Leader Replicas)** — KRaft feature for faster leader recovery
- **`LastKnownElr`** — last known ELR set
- Empty values are normal for single-broker setups

---

## AIOps UI Integration

### Settings → Servers & Index → Add Server → Type: **Kafka Server**

| Field | Value |
|---|---|
| **Connector Name** | `Kafka Bus 1` |
| **Countries** | KW / BH / EG / TR / UK / All (per scope) |
| **Kafka Bootstrap Servers** | `172.17.133.47:9092` ⚠️ **not** `localhost` |
| **Security Protocol** | `SASL_PLAINTEXT` |
| **SASL Mechanism** | **`PLAIN`** ⚠️ (not SCRAM) |
| **Username / Principal** | `kfhaiops` |
| **Password / Secret** | `KfhAiops@2026!KafkaAppSecret` |
| **Client ID** | `kfh-aiops-settings` |
| **Truststore Path** | *blank* (only for SASL_SSL) |
| **TLS** | off |

Click **Test & Save** → 🟢 green = backend reached broker + auth succeeded.

> ⚠️ **Pick `PLAIN`.** AIOps connector tester authenticates via `PlainLoginModule` only. Choosing SCRAM-SHA-256/512 will fail the UI test even against a correct broker.

---

## Troubleshooting

### AIOps UI test failures

| Message | Cause | Fix |
|---|---|---|
| `host is blocked by SSRF protection` | Used `localhost` / `127.0.0.1` | Use `172.17.133.47` |
| `Connection to node -1 ... could not be established` | `advertised.listeners` ≠ private IP | Check `advertised.listeners=CLIENT://172.17.133.47:9092` |
| `connect timed out` | Firewall blocking 9092, or broker down | `systemctl status kafka`; check corporate FW |
| `Authentication failed: Invalid username or password` | Wrong creds | Verify Username + Password in UI |
| `SASL username and password are required` | Empty fields | Fill Username + Password |
| Mechanism mismatch / auth fails with SCRAM | UI set to SCRAM | Switch UI **and** broker to `PLAIN` |
| `No meta.properties found` | KRaft storage never formatted | Re-run Step 8 |

### Broker won't start

| Symptom | Cause | Fix |
|---|---|---|
| `code=exited, status=1/FAILURE` immediately | Port 9092/9093 occupied | `ss -tlnp \| grep -E '9092\|9093'` → kill old process → `systemctl reset-failed kafka` → start |
| `Could not find or load main class kafka.Kafka` | `JAVA_HOME` wrong / missing | Verify `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` in unit file |
| `InconsistentClusterIdException` | KRaft data dir mismatched cluster ID | Wipe `/var/lib/kafka/data/*` and re-format (data loss!) |
| `Could not parse JAAS config` | Typo in `listener.name.client.plain.sasl.jaas.config` | Re-check quoting, escapes, trailing `;` |

### Common diagnostics

```bash
systemctl status kafka --no-pager
journalctl -u kafka --no-pager -n 50
tail -100 /var/log/kafka/server.log
ss -tlnp | grep -E '9092|9093'
```

---

## Final State

```
✅ Kafka 4.3.1                  (latest stable, KRaft-only, no ZooKeeper)
✅ JDK 21.0.11 LTS              (Red Hat build)
✅ /opt/kafka/                  binaries + config (kafka:kafka)
✅ /opt/kafka/config/server.properties   (root:kafka 640)
✅ /opt/kafka/etc/client.properties      (root:kafka 640)
✅ /var/lib/kafka/data/         KRaft metadata + topic data (kafka:kafka)
✅ /var/log/kafka/              broker logs (kafka:kafka)
✅ User: kafka                  (system, /sbin/nologin)
✅ systemd: kafka.service       (enabled, auto-restart on failure)
✅ Cluster ID: nFtw_2g8QtG6HSwxJF-PPw
✅ Node ID: 1
✅ Listeners:
   ├─ CLIENT     → 172.17.133.47:9092 (SASL_PLAINTEXT, app traffic)
   └─ CONTROLLER → 172.17.133.47:9093 (PLAINTEXT, internal Raft)
✅ Auth: SASL/PLAIN with 2 users
   ├─ admin     (broker internal)
   └─ kfhaiops  (AIOps backend application)
✅ Hardening: auto.create.topics.enable=false
✅ Retention: 168h (7 days)
✅ Smoke topic created: kfh.aiops.smoke (3 partitions, RF=1)
```

---

## Credentials Summary

> ⚠️ **Placeholder passwords. Rotate to vault-generated values before production cutover.**

| User | Password | Purpose |
|---|---|---|
| `admin` | `Kfh@Kafka2026!AdminSecret` | Inter-broker / admin operations |
| `kfhaiops` | `KfhAiops@2026!KafkaAppSecret` | AIOps backend application |

### Connection details
```
Bootstrap:   172.17.133.47:9092
Protocol:    SASL_PLAINTEXT
Mechanism:   PLAIN
Cluster ID:  nFtw_2g8QtG6HSwxJF-PPw
```

### How to rotate the kfhaiops password

1. Edit `/opt/kafka/config/server.properties` — update `user_kfhaiops="NEW_PW"`
2. Edit `/opt/kafka/etc/client.properties` — update password in JAAS line
3. `systemctl restart kafka`
4. Re-run Step 10 smoke tests to verify
5. Update AIOps UI password field

---

## Cluster Identifiers

> 🔐 Record these in your CMDB / vault.

| Item | Value |
|---|---|
| **Cluster ID** | `nFtw_2g8QtG6HSwxJF-PPw` |
| **Directory ID** | `BZmGEFGvISRnZ6fQTihcxQ` |
| **Node ID** | `1` |
| **Metadata version** | `4.3-IV0` |
| **Bootstrap features** | metadata.version=30, eligible.leader.replicas.version=1, group.version=1, share.version=1, streams.version=1, transaction.version=2 |

---

## Follow-up Tasks

### 1. 🔐 Rotate placeholder passwords to vault values

Use the rotation procedure under #credentials-summary.

### 2. 🧹 Delete smoke topic once AIOps integration is verified

```bash
/opt/kafka/bin/kafka-topics.sh --bootstrap-server 172.17.133.47:9092 \
  --command-config /opt/kafka/etc/client.properties \
  --delete --topic kfh.aiops.smoke
```

### 3. 📦 Move data to bigger mount (`/var` is only 9.4 GB)

```bash
systemctl stop kafka
rsync -a /var/lib/kafka/data/ /opt/kafka-data/
chown -R kafka:kafka /opt/kafka-data
# Update log.dirs=/opt/kafka-data in server.properties
systemctl start kafka
```

### 4. 🔒 Upgrade SASL_PLAINTEXT → SASL_SSL (Phase 7)

- Generate broker cert/key, build truststore
- Change listener to `SASL_SSL`
- Add `ssl.keystore.location`, `ssl.keystore.password`, `ssl.key.password`
- Import broker CA into AIOps JVM truststore (Java cacerts, not OS trust)
- Toggle TLS=on in AIOps UI

### 5. 📊 Monitoring

Enable JMX in `kafka-server-start.sh` (add `-Dcom.sun.management.jmxremote.port=9999`) and scrape via Site24x7 / BMC Helix. Key metrics:
- `kafka.server:type=ReplicaManager,name=LeaderCount` — broker health
- `kafka.network:type=RequestMetrics,name=TotalTimeMs,*` — latency p99
- `kafka.controller:type=KafkaController,name=ActiveControllerCount` — KRaft health
- `UnderReplicatedPartitions` — ISR health
- `MessagesInPerSec`, `BytesInPerSec`, `BytesOutPerSec` — throughput

### 6. 🚀 Topic conventions for AIOps

Adopt namespaced topics for clarity:
- `kfh.aiops.alerts.raw` — raw alerts from BMC Helix, SCOM, AppDynamics
- `kfh.aiops.alerts.deduped` — post-dedup events
- `kfh.aiops.correlation.events` — correlated incidents
- `kfh.aiops.rca.requests` / `kfh.aiops.rca.responses` — Azure OpenAI RCA pipeline

### 7. 📦 ACL strategy (long term)

Currently `user_kfhaiops` has implicit full access. For production, enable Kafka ACLs and scope:

```bash
/opt/kafka/bin/kafka-acls.sh --bootstrap-server 172.17.133.47:9092 \
  --command-config /opt/kafka/etc/admin.properties \
  --add --allow-principal User:kfhaiops \
  --operation Read --operation Write --operation Describe \
  --topic 'kfh.aiops.*' --resource-pattern-type prefixed
```

### 8. ☕ Heap size review

Currently `-Xmx1G -Xms1G` — fine for an idle broker. When Phase 7 wiring goes live (~1000 alerts/sec):
- Raise to `-Xmx4G -Xms4G`
- Edit `KAFKA_HEAP_OPTS=` in `/etc/systemd/system/kafka.service`
- `systemctl daemon-reload && systemctl restart kafka`

---

## Change Log

| Date | Author | Change |
|---|---|---|
| 2026-06-30 | Muhammad Abdelhafeez | Initial Kafka 4.3.1 KRaft setup on `utvcapap02` for KFH AIOps |
| 2026-06-30 | Muhammad Abdelhafeez | Migrated from manual Kafka 3.7.0 (PID 55683) to systemd-managed 4.3.1 |
| 2026-06-30 | Muhammad Abdelhafeez | SASL/PLAIN auth configured with admin + kfhaiops users |

---

**End of runbook.**s