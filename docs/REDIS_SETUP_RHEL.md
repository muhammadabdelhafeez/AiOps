# Redis 7.2.5 Production Setup on RHEL 10 (KFH AIOps)

**Server:** `utvcapap02` (172.17.133.47)
**OS:** Red Hat Enterprise Linux 10.2 (Coughlan)
**Purpose:** Hot state store for KFH AIOps platform (dedup keys, locks, real-time correlation cache)
**Author:** Muhammad Abdelhafeez
**Date:** 2026-06-30
**Redis Version:** 7.2.5 (built from source)

---

## 📋 Table of Contents

1. [Architecture Decisions](#archit1--pre-flight-checks
3. [Install Build Tolchain
4. #step-3--build-redis-from-source
5. [Create User & Directories](#step-4--create-dedicated-user--directories)
6. [Write Redis Config](#step-5--write-redis-config#step-7--systemd-service
9. Firewall Decision
10. #step-9--end-to-end-verification
11rade-auth
12. [AIOps Integration](#aiops-integration)
13. Troubleshooting
14. #final-state
15. [redentials Summary
16. #follow-up-tasks

---

## Architecture Decisions

| Decision | Choice | Why |
|---|---|---|
| **Install method** | Build from source | Corporate RHEL has no Redis package available offline |
| **Install location** | `/opt/redis/` (PREFIX) | Keeps everything tidy in one directory |
| **Process management** | systemd | Auto-restart on crash, boot-time start, log integration |
| **Service user** | `redis` (system, no-login) | Principle of least privilege |
| **Auth mode** | ACL file (Path B) | Per-user identity, audit trail, rotatable per client |
| **Persistence** | AOF (`appendonly yes`) | Durability — dedup keys survive restart |
| **Eviction policy** | `noeviction` | Never silently drop dedup/lock keys — fail loudly |
| **Bind** | Loopback + private IP only | No public exposure |
| **DB number** | `0` only | AIOps platform rule — isolation via key prefix |

---

## Step 1 — Pre-flight Checks

```bash
cat /etc/redhat-release
hostname -I
whoami
```

**Output:**
```
Red Hat Enterprise Linux release 10.2 (Coughlan)
172.17.133.47
root
```

**Confirmed:** RHEL 10.2, private IP `172.17.133.47`, root access.

---

## Step 2 — Install Build Toolchain

```bash
dnf install -y gcc make
gcc --version
make --version
```

**Result:**
- `gcc` 14.3.1 ✅
- `GNU Make` 4.4.1 ✅
- Both already installed

---

## Step 3 — Build Redis from Source

### 3.1 — Locate and extract tarball

```bash
cp /home/sun92338/redis-7.2.5.tar.gz /usr/local/src/
cd /usr/local/src
tar xzf redis-7.2.5.tar.gz
cd redis-7.2.5
```

### 3.2 — Compile

```bash
make -j"$(nproc)"
```

**Result:** Clean build, all binaries linked (`redis-server`, `redis-cli`, `redis-benchmark`, helpers).

### 3.3 — Install into /opt/redis

```bash
make install PREFIX=/opt/redis
ls -la /opt/redis/bin/
/opt/redis/bin/redis-server --version
```

**Output:**
```
Redis server v=7.2.5 sha=00000000:0 malloc=jemalloc-5.3.0 bits=64 build=8298cc8825d4a431
```

---

## Step 4 — Create Dedicated User & Directories

```bash
useradd --system --no-create-home --shell /sbin/nologin redis
mkdir -p /opt/redis/etc /var/lib/redis /var/log/redis
chown -R redis:redis /var/lib/redis /var/log/redis
id redis
ls -ld /opt/redis/etc /var/lib/redis /var/log/redis
```

**Result:**
- `redis` user created (uid=979, gid=977)
- Config dir: `/opt/redis/etc` (root:root)
- Data dir: `/var/lib/redis` (redis:redis)
- Log dir: `/var/log/redis` (redis:redis)

---

## Step 5 — Write Redis Configuration

### File: `/opt/redis/etc/redis.conf`

```conf
# --- Network ---
bind 127.0.0.1 172.17.133.47
protected-mode yes
port 6379

# --- Authentication (replaced by ACL file in Step 10) ---
# requirepass <removed>

# --- Persistence & data ---
dir /var/lib/redis
appendonly yes
maxmemory-policy noeviction

# --- Logging / process ---
logfile /var/log/redis/redis.log
daemonize no

# --- Hardening: disable dangerous commands ---
rename-command FLUSHALL ""
rename-command FLUSHDB ""
rename-command CONFIG ""

# --- ACL file (added in Step 10) ---
aclfile /opt/redis/etc/users.acl
```

### Secure the config (password / ACL file references inside):

```bash
chown root:redis /opt/redis/etc/redis.conf
chmod 640 /opt/redis/etc/redis.conf
```

### Why each directive matters

| Directive | Why |
|---|---|
| `bind 127.0.0.1 172.17.133.47` | Loopback for local CLI + private IP for AIOps. **No public exposure.** |
| `protected-mode yes` | Extra safety — refuses unauthenticated remote access |
| `appendonly yes` | AOF persistence — dedup/lock state survives restart |
| `maxmemory-policy noeviction` | Fail loudly instead of silently dropping correctness-critical keys |
| `daemonize no` | systemd supervises the process |
| `rename-command FLUSHALL ""` | Prevent "delete everything" accidents/attacks |
| `rename-command CONFIG ""` | Block runtime config changes (must edit file + restart) |
| `aclfile` | Production-grade auth via named users |

---

## Step 6 — Kernel Tuning

```bash
echo 'vm.overcommit_memory = 1' | tee /etc/sysctl.d/99-redis.conf
echo 'net.core.somaxconn = 512'  | tee -a /etc/sysctl.d/99-redis.conf
sysctl --system
echo never > /sys/kernel/mm/transparent_hugepage/enabled
```

### Verification:
```bash
sysctl vm.overcommit_memory net.core.somaxconn
cat /sys/kernel/mm/transparent_hugepage/enabled
```

**Output:**
```
vm.overcommit_memory = 1
net.core.somaxconn = 512
always madvise [never]
```

| Setting | Why |
|---|---|
| `vm.overcommit_memory=1` | Lets `fork()` succeed during RDB/AOF background saves under memory pressure |
| `net.core.somaxconn=512` | Handles AIOps burst traffic (~1000 alerts/sec) without SYN-drop |
| `THP=never` | Prevents latency spikes / fork stalls (standard Redis production recommendation) |

⚠️ **THP reverts on reboot** — see [Follow-up Tasks](#follow-up-tasks) for permanent fix.

---

## Step 7 — Systemd Service

### File: `/etc/systemd/system/redis.service`

```ini
[Unit]
Description=Redis (KFH AIOps hot state)
After=network-online.target
Wants=network-online.target

[Service]
User=redis
Group=redis
ExecStart=/opt/redis/bin/redis-server /opt/redis/etc/redis.conf
Restart=on-failure
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
```

### Enable + start:
```bash
systemctl daemon-reload
systemctl enable --now redis
systemctl status redis --no-pager
```

### ⚠️ Issue encountered — old manual Redis was holding port 6379

The first start failed because a Redis instance started manually on Jun 28 (PID 53742) was occupying the port.

**Fix:**
```bash
systemctl stop redis
kill 53742
ss -tlnp | grep 6379          # confirm port free
systemctl reset-failed redis
systemctl start redis
```

### Verification:
```bash
ss -tlnp | grep 6379
```

**Output:**
```
LISTEN 0 511 172.17.133.47:6379 ... users:(("redis-server",pid=156574,fd=7))
LISTEN 0 511     127.0.0.1:6379 ... users:(("redis-server",pid=156574,fd=6))
```

✅ Bound exactly to loopback + private IP. No public exposure.

---

## Step 8 — Firewall Decision

```bash
firewall-cmd --state
firewall-cmd --get-default-zone
```

**Output:**
```
running
trusted
```

**Decision:** Default zone is `trusted` (allows all). Since corporate FW + RFS rules handle the network perimeter, and Redis itself has:
- ✔ Binding to private IP only
- ✔ ACL-based authentication
- ✔ `protected-mode yes`
- ✔ Dangerous commands disabled

→ **Path A (no host-level firewall rule)** chosen. Defense in depth provided by Redis config + corporate FW.

---

## Step 9 — End-to-End Verification

```bash
systemctl is-active redis
/opt/redis/bin/redis-cli -a 'Kfh@Redis2026!StrongSecret' -h 172.17.133.47 ping
/opt/redis/bin/redis-cli -a 'Kfh@Redis2026!StrongSecret' -h 172.17.133.47 set t 1 EX 10
/opt/redis/bin/redis-cli -a 'Kfh@Redis2026!StrongSecret' -h 172.17.133.47 get t
/opt/redis/bin/redis-cli -a 'Kfh@Redis2026!StrongSecret' -h 172.17.133.47 -n 0 info keyspace
```

**Results:**
| Check | Result |
|---|---|
| Service active | `active` ✅ |
| PING | `PONG` ✅ |
| SET with TTL | `OK` ✅ |
| GET | `"1"` ✅ |
| Keyspace | `db0:keys=1,expires=1,avg_ttl=5199` ✅ |

This verified initial `requirepass` setup. Then upgraded to ACL (Step 10).

---

## Step 10 — ACL Migration (Production-grade Auth)

### Why upgrade from `requirepass` to ACL file

| Feature | `requirepass` | ACL file |
|---|---|---|
| Named users | ❌ One shared secret | ✅ Per-client identity |
| Audit trail | ❌ "Someone with the password" | ✅ Username in logs |
| Per-user rotation | ❌ Rotate breaks all clients | ✅ Rotate one user only |
| Scoped permissions | ❌ All-or-nothing | ✅ Per-key-pattern, per-command |
| `ACL WHOAMI` support | ❌ | ✅ |

### 10.1 — Create ACL file

```bash
touch /opt/redis/etc/users.acl
chown root:redis /opt/redis/etc/users.acl
chmod 640 /opt/redis/etc/users.acl

cat > /opt/redis/etc/users.acl << 'EOF'
user default on >Kfh@Redis2026!AdminSecret ~* &* +@all
user kfhaiops on >KfhAiops@2026!AppSecret ~* &* +@all
EOF
```

### ACL flags

| Flag | Meaning |
|---|---|
| `on` | User enabled |
| `>password` | Add password (SHA-256 hashed at load) |
| `~*` | Allow all key patterns |
| `&*` | Allow all pub/sub channels |
| `+@all` | Allow all command categories |

### 10.2 — Update `redis.conf` to use ACL file

```bash
cp /opt/redis/etc/redis.conf /opt/redis/etc/redis.conf.bak  # backup (optional)
sed -i '/^requirepass/d' /opt/redis/etc/redis.conf          # remove requirepass
echo 'aclfile /opt/redis/etc/users.acl' >> /opt/redis/etc/redis.conf
```

⚠️ `requirepass` and `aclfile` are **mutually exclusive** — must remove one before adding the other.

### 10.3 — Restart and verify

```bash
systemctl restart redis
sleep 2
systemctl is-active redis
```

### 10.4 — Test all paths

```bash
# Old requirepass — should fail
/opt/redis/bin/redis-cli -a 'Kfh@Redis2026!StrongSecret' -h 172.17.133.47 ping
# → AUTH failed: WRONGPASS ✅

# New default admin — should succeed
/opt/redis/bin/redis-cli --user default --pass 'Kfh@Redis2026!AdminSecret' -h 172.17.133.47 ping
# → PONG ✅

# kfhaiops app user — should succeed
/opt/redis/bin/redis-cli --user kfhaiops --pass 'KfhAiops@2026!AppSecret' -h 172.17.133.47 ping
# → PONG ✅

# Confirm identity
/opt/redis/bin/redis-cli --user kfhaiops --pass 'KfhAiops@2026!AppSecret' -h 172.17.133.47 ACL WHOAMI
# → "kfhaiops" ✅

# List all users
/opt/redis/bin/redis-cli --user default --pass 'Kfh@Redis2026!AdminSecret' -h 172.17.133.47 ACL LIST
# 1) "user default on sanitize-payload #4ac0a5fe... ~* &* +@all"
# 2) "user kfhaiops on sanitize-payload #7b129e1a... ~* &* +@all"
```

✅ All tests passed. Passwords stored as SHA-256 hashes (`sanitize-payload` redacts them in output).

---

## AIOps Integration

In **AIOps → Settings → Servers & Index → Redis Server**:

| Field | Value |
|---|---|
| **Host / IP** | `172.17.133.47` ⚠️ **not** `localhost` (SSRF guard blocks loopback) |
| **Port** | `6379` |
| **Username** | `kfhaiops` |
| **Password** | `KfhAiops@2026!AppSecret` |
| **Database** | `0` (platform rule — DB 0 only) |
| **TLS** | off |

Click **Test & Save** → green = backend reached Redis (PING→PONG).

---

## Troubleshooting

| AIOps test message | Cause | Fix |
|---|---|---|
| `host is blocked by SSRF protection` | Used `localhost` / `127.0.0.1` | Use `172.17.133.47` |
| `connect timed out` | Firewall blocking / wrong bind | Check `bind` in redis.conf; check corporate FW |
| `authentication failed (-WRONGPASS)` | Wrong password or username | Verify both fields — `kfhaiops` + correct password |
| `-DENIED ... protected mode` | `protected-mode` + no auth | Provide auth (Username + Password) |
| `server may require TLS / empty reply` | TLS mismatch | Match TLS toggle to server (off in this setup) |
| `redis.service: Start request repeated too quickly` | Port 6379 occupied / config error | Kill old process, `systemctl reset-failed redis`, check `journalctl -u redis` |
| `Failed to start - exit-code 1` (no detail) | Hidden error from systemd | Run binary manually: `sudo -u redis /opt/redis/bin/redis-server /opt/redis/etc/redis.conf` |

### Common diagnostics

```bash
systemctl status redis --no-pager
journalctl -u redis --no-pager -n 50
ss -tlnp | grep 6379
tail -50 /var/log/redis/redis.log
```

---

## Final State

```
✅ Redis 7.2.5  (compiled from source, jemalloc-5.3.0, 64-bit)
✅ /opt/redis/bin/                  binaries
✅ /opt/redis/etc/redis.conf        config (root:redis 640)
✅ /opt/redis/etc/users.acl         ACL users (root:redis 640)
✅ /var/lib/redis/                  data (AOF persistence)
✅ /var/log/redis/redis.log         logs
✅ User: redis                      (system, /sbin/nologin)
✅ systemd: redis.service           (enabled, auto-restart on failure)
✅ Bind: 127.0.0.1 + 172.17.133.47  (no public exposure)
✅ Auth: ACL file with 2 users
   ├─ default  (admin) — full access
   └─ kfhaiops (app)   — full access (used by AIOps)
✅ Hardening: FLUSHALL / FLUSHDB / CONFIG disabled
✅ Kernel: overcommit=1, somaxconn=512, THP=never
✅ AIOps Settings: Host=172.17.133.47, Port=6379, User=kfhaiops, DB=0, TLS=off
```

---

## Credentials Summary

> ⚠️ **These are placeholder passwords. Rotate to vault-generated values before production cutover.**

| User | Password | Purpose |
|---|---|---|
| `default` | `Kfh@Redis2026!AdminSecret` | Admin / maintenance |
| `kfhaiops` | `KfhAiops@2026!AppSecret` | AIOps backend application |

### Connection details

```
Host:     172.17.133.47
Port:     6379
Database: 0
TLS:      off
```

### How to rotate a password

```bash
/opt/redis/bin/redis-cli --user default --pass 'Kfh@Redis2026!AdminSecret' -h 172.17.133.47 \
  ACL SETUSER kfhaiops on '>NewStrongPasswordFromVault' '~*' '&*' '+@all'

/opt/redis/bin/redis-cli --user default --pass 'Kfh@Redis2026!AdminSecret' -h 172.17.133.47 \
  ACL SAVE
```

---

## Follow-up Tasks

### 1. 🧊 Make THP=never permanent across reboots

Create a systemd drop-in:

```bash
cat > /etc/systemd/system/disable-thp.service << 'EOF'
[Unit]
Description=Disable Transparent Huge Pages (THP)
DefaultDependencies=no
After=sysinit.target local-fs.target
Before=basic.target

[Service]
Type=oneshot
ExecStart=/bin/sh -c 'echo never > /sys/kernel/mm/transparent_hugepage/enabled'
RemainAfterExit=yes

[Install]
WantedBy=basic.target
EOF

systemctl daemon-reload
systemctl enable disable-thp.service
```

### 2. 🔐 Rotate placeholder passwords to vault values

Use the rotation command above with passwords generated by your corporate secrets vault.

### 3. 🔄 Tighter ACL scoping (defense in depth)

Currently `kfhaiops` has full access (`~* &* +@all`). For production, narrow it:

```bash
/opt/redis/bin/redis-cli --user default --pass 'Kfh@Redis2026!AdminSecret' -h 172.17.133.47 \
  ACL SETUSER kfhaiops on '>KfhAiops@2026!AppSecret' \
  '~dedup:*' '~lock:*' '~rca:*' \
  '+@read' '+@write' '+@string' '+@hash' '+@set' '+@keyspace' '-flushall' '-flushdb' '-config'
```

### 4. 📦 Backup strategy

- AOF file: `/var/lib/redis/appendonlydir/`
- Schedule a daily snapshot copy to NAS / S3
- Include `/opt/redis/etc/redis.conf` and `/opt/redis/etc/users.acl` in config backup

### 5. 📊 Monitoring (tie back to your AIOps stack)

Add Redis to **Site24x7** / **BMC Helix** with these key metrics:
- `INFO replication` — for future HA
- `INFO memory` — `used_memory`, `used_memory_rss`
- `INFO stats` — `instantaneous_ops_per_sec`, `rejected_connections`
- `INFO clients` — `connected_clients`
- `INFO persistence` — `rdb_last_bgsave_status`, `aof_last_write_status`

### 6. 🚀 Next services to set up using same approach

- **Kafka** — verification + ACLs
- **Neo4j** — already up, verify auth + plugins
- **PostgreSQL** — for AIOps platform metadata

---

## Change Log

| Date | Author | Change |
|---|---|---|
| 2026-06-30 | Muhammad Abdelhafeez | Initial Redis 7.2.5 setup on `utvcapap02` for KFH AIOps |
| 2026-06-30 | Muhammad Abdelhafeez | Migrated from `requirepass` to ACL file (Path B) |

---

**End of runbook.**