# Kafka (KRaft) Setup on RHEL — KFH AIOps

> Provision the Kafka broker that will become the durable stream / integration bus for the causal
> funnel. Style and conventions mirror [`REDIS_SETUP_RHEL.md`](./REDIS_SETUP_RHEL.md).
>
> **Status context (read first):** Kafka is **Phase 7 / "Kafka-ready"** in this platform. The
> *current* live bus is the PostgreSQL **outbox** (`ops.outbox_events`) — see
> [`OUTBOX.md`](./OUTBOX.md) and [`ROADMAP.md`](./ROADMAP.md). This runbook stands the broker up so
> it is ready and testable from **Settings → Servers & Index → Kafka**, but **no application code
> consumes it yet**. Provision it now if you want infra ahead of Phase 7; otherwise it can wait.
>
> Modern Kafka (3.7+) runs in **KRaft mode** — no ZooKeeper. This is a single-node broker+controller
> suitable for a dev/UAT server; scale to a 3-node quorum for production.

---

## Architecture decisions

| Decision | Choice | Why |
|---|---|---|
| Mode | **KRaft** (no ZooKeeper) | ZooKeeper is removed in Kafka 4.0 and deprecated in 3.x; KRaft is the supported path |
| Install | Binary tarball to `/opt/kafka` | Kafka ships compiled (JVM); no build step needed |
| Process mgmt | systemd | Auto-restart, boot start, journald logs |
| Service user | `kafka` (system, no-login) | Least privilege |
| Listeners | CLIENT (app) + CONTROLLER (internal) | Separate client traffic from the Raft control plane |
| Auth | **SASL_PLAINTEXT + PLAIN** (recommended) or PLAINTEXT (trusted net) | PLAIN is what the AIOps connector tester supports today |
| Bind | Private IP only | No public exposure (same rule as Redis) |
| Encryption | off by default; SASL_SSL optional | Internal private subnet; enable TLS if required |

---

## Step 0 — Pre-flight

```bash
cat /etc/redhat-release
hostname -I                 # note the private IP (e.g. 172.17.133.47)
java -version               # Kafka needs a JRE/JDK 17+
```
- `hostname -I` — the private IP you'll bind to and enter in the UI (never `localhost` — the connector test's SSRF guard blocks loopback).
- `java -version` — Kafka is a JVM app; **JDK 17+** is required (Kafka 3.7+/4.0).

### Install Java 17 if missing
```bash
sudo dnf install -y java-17-openjdk-headless     # online
# offline: copy a Temurin 17 tarball, extract to /opt/jdk-17, then:
#   sudo alternatives --install /usr/bin/java java /opt/jdk-17/bin/java 1
java -version
```
- `java-17-openjdk-headless` — JRE without GUI libs (a server doesn't need them). On a locked-down box, copy Temurin 17 manually (same pattern as the Redis tarball).

---

## Step 1 — Service user and directories

```bash
sudo useradd --system --no-create-home --shell /sbin/nologin kafka
sudo mkdir -p /opt/kafka /var/lib/kafka/data /var/log/kafka /opt/kafka/etc
sudo chown -R kafka:kafka /var/lib/kafka /var/log/kafka
```
- `useradd --system … /sbin/nologin` — unprivileged service account (can't log in).
- `mkdir -p` — install dir, **data/log dir** (where Kafka stores partitions + the KRaft metadata log), log dir, and a small `etc` for client configs.
- `chown` — let the `kafka` user write data and logs.

---

## Step 2 — Install the Kafka binaries

```bash
KAFKA_VER=3.9.1
cd /usr/local/src
# copy kafka_2.13-${KAFKA_VER}.tgz here (offline) OR download if allowed:
# curl -LO https://downloads.apache.org/kafka/${KAFKA_VER}/kafka_2.13-${KAFKA_VER}.tgz
tar xzf kafka_2.13-${KAFKA_VER}.tgz
sudo cp -r kafka_2.13-${KAFKA_VER}/* /opt/kafka/
sudo chown -R kafka:kafka /opt/kafka
/opt/kafka/bin/kafka-topics.sh --version
```
- `kafka_2.13-3.9.1.tgz` — `2.13` is the Scala build (use 2.13); `3.9.1` is the Kafka version.
- `tar xzf` — extract the tarball.
- `cp -r … /opt/kafka/` — install Kafka under one tidy prefix.
- `kafka-topics.sh --version` — confirms the binaries run on the installed JDK.

---

## Step 3 — Configure KRaft — `/opt/kafka/config/server.properties`

Choose **one** auth path. Both bind only to the private IP.

### Path B (recommended) — SASL_PLAINTEXT + PLAIN (auth, works with the UI test)

```properties
# --- KRaft roles ---
process.roles=broker,controller
node.id=1
controller.quorum.voters=1@172.17.133.47:9093

# --- Listeners (CLIENT = app traffic, CONTROLLER = internal Raft) ---
listeners=CLIENT://172.17.133.47:9092,CONTROLLER://172.17.133.47:9093
advertised.listeners=CLIENT://172.17.133.47:9092
listener.security.protocol.map=CONTROLLER:PLAINTEXT,CLIENT:SASL_PLAINTEXT
inter.broker.listener.name=CLIENT
controller.listener.names=CONTROLLER

# --- SASL/PLAIN on the CLIENT listener ---
sasl.enabled.mechanisms=PLAIN
sasl.mechanism.inter.broker.protocol=PLAIN
listener.name.client.plain.sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required \
  username="admin" password="CHANGE_ME_ADMIN_PW" \
  user_admin="CHANGE_ME_ADMIN_PW" \
  user_kfhaiops="CHANGE_ME_APP_PW";

# --- Storage & single-node replication ---
log.dirs=/var/lib/kafka/data
num.partitions=3
default.replication.factor=1
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
auto.create.topics.enable=false
```

What each block does:
- **`process.roles=broker,controller`** — this one node is both the data broker and the KRaft controller (the Raft quorum that replaces ZooKeeper). `node.id` is its unique id; `controller.quorum.voters=1@<ip>:9093` lists the controller quorum (just itself here).
- **`listeners` / `advertised.listeners`** — Kafka listens on two ports: `9092` for clients (the app) and `9093` for the internal controller. `advertised.listeners` is the address clients are told to connect back on — it **must be the reachable private IP**, not `localhost`, or remote clients (and the AIOps test) can't connect.
- **`listener.security.protocol.map`** — maps each listener name to a security protocol. Controller plane is plaintext (internal, single node); the CLIENT plane uses **SASL_PLAINTEXT** (username/password auth, no TLS).
- **`inter.broker.listener.name=CLIENT`** — brokers talk to each other over the authenticated listener.
- **SASL JAAS line** — defines the PLAIN users *inline*: `username/password` are the broker's own inter-broker credentials; each `user_<name>="pw"` entry creates a login. Here `admin` (maintenance) and **`kfhaiops`** (the app user the AIOps backend authenticates as).
- **Replication factors = 1** — required on a single node (you only have one broker to hold replicas). Raise to 3 on a real cluster.
- **`auto.create.topics.enable=false`** — topics must be created deliberately (avoids typo-topics polluting the bus).

### Path A (simplest) — PLAINTEXT (no auth, trusted subnet only)
Replace the listener/SASL block with:
```properties
listeners=PLAINTEXT://172.17.133.47:9092,CONTROLLER://172.17.133.47:9093
advertised.listeners=PLAINTEXT://172.17.133.47:9092
listener.security.protocol.map=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
inter.broker.listener.name=PLAINTEXT
controller.listener.names=CONTROLLER
```
Use only on an isolated, firewall-segmented subnet — anyone who can reach 9092 can read/write.

### Secure the config (it holds passwords in Path B)
```bash
sudo chown root:kafka /opt/kafka/config/server.properties
sudo chmod 640 /opt/kafka/config/server.properties
```
- `640` + `root:kafka` — only root writes it; the `kafka` group reads it; other users can't see the JAAS passwords.

---

## Step 4 — Format the KRaft storage (once)

```bash
KAFKA_CLUSTER_ID=$(/opt/kafka/bin/kafka-storage.sh random-uuid)
echo "Cluster ID: $KAFKA_CLUSTER_ID"   # save this
sudo -u kafka /opt/kafka/bin/kafka-storage.sh format \
  -t "$KAFKA_CLUSTER_ID" -c /opt/kafka/config/server.properties
```
- `kafka-storage.sh random-uuid` — generates a unique cluster id (KRaft requires the log directory to be stamped with one before first start).
- `kafka-storage.sh format -t <id> -c <config>` — initializes `log.dirs` with that id. Run **as the `kafka` user** so the files are owned correctly. Do this **once** — re-formatting wipes the metadata.

---

## Step 5 — systemd service — `/etc/systemd/system/kafka.service`

```ini
[Unit]
Description=Apache Kafka (KRaft) - KFH AIOps
After=network-online.target
Wants=network-online.target

[Service]
User=kafka
Group=kafka
Environment=KAFKA_HEAP_OPTS=-Xmx1G -Xms1G
ExecStart=/opt/kafka/bin/kafka-server-start.sh /opt/kafka/config/server.properties
ExecStop=/opt/kafka/bin/kafka-server-stop.sh
Restart=on-failure
LimitNOFILE=100000

[Install]
WantedBy=multi-user.target
```
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now kafka
sudo systemctl status kafka --no-pager
```
- `After/Wants=network-online.target` — wait for the network so `advertised.listeners` (private IP) binds.
- `KAFKA_HEAP_OPTS=-Xmx1G -Xms1G` — pins the JVM heap (tune to the box; 1 GB is a reasonable dev default).
- `ExecStart/ExecStop` — Kafka's own start/stop scripts (the start script stays in the foreground so systemd can supervise it).
- `LimitNOFILE=100000` — Kafka keeps many open file handles (one per partition segment + per connection).
- `enable --now` — boot start + start now; `status` — confirm `active (running)`.

---

## Step 6 — Firewall (expose 9092 to the app server only)

```bash
sudo firewall-cmd --permanent --add-rich-rule='rule family="ipv4" source address="APP_SERVER_IP/32" port port="9092" protocol="tcp" accept'
sudo firewall-cmd --reload
sudo firewall-cmd --list-rich-rules
```
- Allows the client port **9092** only from the AIOps app server. The controller port **9093** is internal — do **not** expose it. Replace `APP_SERVER_IP`. (If your zone is `trusted`/perimeter-firewalled like the Redis box, this still adds real defense-in-depth.)

---

## Step 7 — Verify locally

Create a client config so the CLI can authenticate (Path B):
```bash
sudo tee /opt/kafka/etc/client.properties >/dev/null <<'EOF'
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="kfhaiops" password="CHANGE_ME_APP_PW";
EOF
sudo chmod 640 /opt/kafka/etc/client.properties
```
Then:
```bash
BS=172.17.133.47:9092
/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server $BS --command-config /opt/kafka/etc/client.properties | head
/opt/kafka/bin/kafka-topics.sh --bootstrap-server $BS --command-config /opt/kafka/etc/client.properties \
  --create --topic kfh.aiops.smoke --partitions 1 --replication-factor 1
/opt/kafka/bin/kafka-topics.sh --bootstrap-server $BS --command-config /opt/kafka/etc/client.properties --list
```
- `client.properties` — clients (including the CLI) need to know how to authenticate; this mirrors what the AIOps backend sends. For **Path A** (PLAINTEXT) omit `--command-config` entirely.
- `kafka-broker-api-versions.sh` — connects and lists the broker's supported APIs; the simplest "can I reach + auth?" check (this is essentially what the UI test does via AdminClient).
- `kafka-topics.sh --create / --list` — proves create + metadata round-trips.

---

## Step 8 — Add the server in the AIOps UI

**Settings → Servers & Index → Add Server → Kafka Server:**

| Field | Value (Path B) |
|---|---|
| Connector Name | `Kafka Bus 1` |
| Countries | KW / BH / EG / All |
| Kafka Bootstrap Servers | `172.17.133.47:9092` ⚠️ private IP, **not** localhost |
| Security Protocol | `SASL_PLAINTEXT` (or `PLAINTEXT` for Path A) |
| SASL Mechanism | **`PLAIN`** ⚠️ (see limitation below) |
| Username / Principal | `kfhaiops` |
| Password / Secret | the `kfhaiops` password |
| Client ID | `kfh-aiops-settings` |
| Truststore Path | blank (set only for `SASL_SSL`/`SSL`) |

Click **Test & Save** → the backend runs an `AdminClient.describeCluster()` probe; green = it reached the broker and (for SASL) authenticated.

> ⚠️ **Tester limitation — use `PLAIN`.** The current connector tester builds its SASL login with
> `PlainLoginModule` regardless of the Mechanism dropdown. So **SASL_PLAINTEXT + PLAIN** passes, but
> picking **SCRAM-SHA-256/512** (or GSSAPI) in the UI will **fail the test** even against a correctly
> configured broker. Configure the broker for PLAIN, or wait for the tester to gain SCRAM support
> (see Follow-ups). For encryption, use **SASL_SSL** + a JVM-trusted broker cert and set Truststore Path.

---

## Troubleshooting → maps to the AIOps test messages

| Test / log message | Cause | Fix |
|---|---|---|
| "Kafka bootstrap servers must be host:port entries without URL syntax" | You typed a URL (`kafka://…`) | Use `host:port[,host:port]` only |
| "host is blocked by SSRF protection" | `localhost`/`127.0.0.1` in bootstrap | Use the private IP (`advertised.listeners`) |
| "Kafka SASL username and password are required" | Protocol = SASL but creds blank | Fill Username + Password |
| Timeout / `describeCluster` fails | Firewall, wrong `advertised.listeners`, or broker down | Check 9092 reachable, advertised IP = private IP, `systemctl status kafka` |
| Auth fails only with SCRAM picked | Tester uses PlainLoginModule | Switch broker + UI to **PLAIN** |
| Broker won't start, "No `meta.properties`" | Storage not formatted | Run Step 4 `kafka-storage.sh format` |

### Diagnostics
```bash
sudo systemctl status kafka --no-pager
sudo journalctl -u kafka --no-pager -n 80
ss -tlnp | grep -E '9092|9093'
sudo tail -100 /var/log/kafka/server.log
```

---

## Credentials & follow-ups

> ⚠️ Do **not** commit real Kafka passwords to this repo (same rule as Redis — `SECURITY.md`).
> Keep them in your secrets vault; use placeholders in any committed doc.

- **Rotate** the `kfhaiops`/`admin` PLAIN passwords to vault-generated values before production; edit `server.properties` JAAS + restart.
- **3-node quorum + replication factor 3** for production HA.
- **SASL_SSL** (TLS) if traffic must be encrypted on the wire.
- **Topic plan** for Phase 7: align names with [`OUTBOX.md`](./OUTBOX.md) event types (e.g. `kfh.aiops.ingestion-batch`, `kfh.aiops.ai-narrative-requested`).
- **Platform enhancement:** teach the connector tester to use `ScramLoginModule` for SCRAM-SHA-256/512 so the UI's SCRAM options work end-to-end.
- **Runtime client (future):** a Kafka producer/consumer that consumes these saved settings (mirroring the Part D Redis client) is Phase 7 work — not built yet.

---

## Change log
| Date | Author | Change |
|---|---|---|
| 2026-06-30 | — | Initial KRaft single-node Kafka runbook for KFH AIOps (Phase 7 prep) |
