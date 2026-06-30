# Redis Setup on RHEL — KFH AIOps

> Operational runbook for standing up the Redis server that backs the causal funnel
> (hot state, fingerprint dedup, locks, dashboard cache). Aligns with
> [`CAUSAL_PIPELINE.md`](./CAUSAL_PIPELINE.md) §11 and `.github/copilot-instructions.md` §12:
> **Redis 7+, logical DB 0 only, key-prefix isolation per (country, environment),
> password/ACL required, bound to a private interface.**
>
> Tie-back: the runtime client (`org.kfh.aiops.platform.redis`) and `FingerprintDedupService`
> consume the connection you save in **Settings → Servers & Index → Redis** — see
> [`SERVICES_SUPPORT.md`](./SERVICES_SUPPORT.md) and the "Redis (runtime hot state)" section in
> [`RUNBOOKS.md`](./RUNBOOKS.md).

---

## 0. Pre-flight checks

```bash
cat /etc/redhat-release          # confirm RHEL version (e.g. RHEL 10)
hostname -I                      # list this server's IPs — note the PRIVATE one (e.g. 172.17.133.47)
sudo dnf list redis 2>/dev/null  # is a Redis package even available offline?
```
- `cat /etc/redhat-release` — RHEL major version (build steps differ slightly).
- `hostname -I` — every IP this box has. **Record the private IP** the AIOps app reaches; you `bind` Redis to it and enter it in the Settings form (never `127.0.0.1` — the connector test's SSRF guard blocks loopback).
- `sudo dnf list redis` — if it prints a package, use the easy path (§2); on locked-down corporate RHEL it usually prints nothing → build from source (§1).

---

## 1. Install — build from source (offline / restricted RHEL)

### 1.1 Compiler toolchain
```bash
sudo dnf install -y gcc make
```
Redis is C; `gcc` (compiler) + `make` (build orchestrator) are required. `-y` auto-confirms.

### 1.2 Get the source onto the server
```bash
cd /usr/local/src
# Copy redis-7.2.5.tar.gz here via your approved channel (USB/SFTP), then:
tar xzf redis-7.2.5.tar.gz
cd redis-7.2.5
```
- `cd /usr/local/src` — conventional location for manually built source.
- `tar xzf …` — e**x**tract, gun**z**ip, from **f**ile.
- If HTTPS downloads fail with `error 60: self-signed certificate in chain`, that's corporate SSL inspection — see §9; prefer copying the tarball in manually.

### 1.3 Compile and install
```bash
make -j"$(nproc)"
sudo make install PREFIX=/opt/redis
```
- `make -j"$(nproc)"` — compiles; one parallel job per CPU core (`nproc` = core count).
- `make install PREFIX=/opt/redis` — installs binaries into `/opt/redis/bin`, kept tidy under one prefix.

### 1.4 Confirm
```bash
/opt/redis/bin/redis-server --version
```
Prints the version — confirms the build works.

---

## 2. Install — easy path (online RHEL with package access)
*Skip if you built from source.*
```bash
sudo dnf install -y redis        # or: sudo dnf module install redis:7
sudo systemctl enable --now redis
```
- `dnf install redis` — prebuilt package + ready systemd service.
- `enable --now` — start on every boot **and** now. (Skip §6 with this path.)

---

## 3. Dedicated user and directories

```bash
sudo useradd --system --no-create-home --shell /sbin/nologin redis
sudo mkdir -p /opt/redis/etc /var/lib/redis /var/log/redis
sudo chown -R redis:redis /var/lib/redis /var/log/redis
```
- `useradd --system … /sbin/nologin` — unprivileged service account that can't log in (least privilege).
- `mkdir -p` — config/data/log dirs; `-p` creates parents, no error if present.
- `chown -R redis:redis` — let the daemon write RDB/AOF + logs.

---

## 4. Configuration — `/opt/redis/etc/redis.conf`

```conf
# --- Network ---
bind 127.0.0.1 172.17.133.47        # loopback + the PRIVATE app-facing IP
protected-mode yes                  # refuse remote unauthenticated access
port 6379

# --- Authentication (REQUIRED) ---
requirepass CHANGE_ME_STRONG_SECRET # password clients must AUTH with

# --- Persistence & data ---
dir /var/lib/redis
appendonly yes                      # durable append-only log (survives restart)
maxmemory-policy noeviction         # never silently drop dedup/lock keys

# --- Logging / process ---
logfile /var/log/redis/redis.log
daemonize no                        # systemd manages the process

# --- Hardening: neutralize dangerous commands ---
rename-command FLUSHALL ""
rename-command FLUSHDB ""
rename-command CONFIG ""
```
Why each matters:
- `bind 127.0.0.1 172.17.133.47` — exposes Redis to the app server but not the public network. This is the IP you enter in Settings; loopback won't work for the AIOps test (network connect + SSRF guard).
- `protected-mode yes` — rejects external unauthenticated connections (a remote `-DENIED` reply usually means this + missing auth).
- `requirepass` — the platform requires auth; long random secret from the vault.
- `appendonly yes` — dedup/lock state survives restarts.
- `maxmemory-policy noeviction` — fail loudly instead of silently evicting correctness-critical keys.
- `daemonize no` — systemd supervises it.
- `rename-command … ""` — disables FLUSHALL/FLUSHDB/CONFIG so nobody can wipe or reconfigure at runtime.

---

## 5. Kernel tuning (stability)

```bash
echo 'vm.overcommit_memory = 1' | sudo tee /etc/sysctl.d/99-redis.conf
echo 'net.core.somaxconn = 512'  | sudo tee -a /etc/sysctl.d/99-redis.conf
sudo sysctl --system
sudo bash -c 'echo never > /sys/kernel/mm/transparent_hugepage/enabled'
```
- `vm.overcommit_memory = 1` — lets the `fork()` for background saves get the memory it asks for (Redis warns without it).
- `net.core.somaxconn = 512` — larger TCP accept queue for bursts (funnel can spike ~1,000 alerts/sec).
- `tee` / `tee -a` — persist to a sysctl drop-in (`-a` appends); `sysctl --system` applies now.
- `transparent_hugepage … never` — THP causes latency/fork stalls; disabling is the standard Redis recommendation. Make permanent via a tuned profile/rc.local.

---

## 6. systemd service — `/etc/systemd/system/redis.service`

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
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now redis
sudo systemctl status redis --no-pager
```
- `After/Wants=network-online.target` — wait for the network so `bind <private-ip>` succeeds.
- `User/Group=redis` — run as the unprivileged account.
- `Restart=on-failure` — auto-restart on crash.
- `LimitNOFILE=65535` — many connections = many file descriptors.
- `daemon-reload` — re-read unit files; `enable --now` — boot + now; `status --no-pager` — confirm `active (running)`.

---

## 7. Firewall — expose 6379 to the app server only

```bash
sudo firewall-cmd --permanent --add-rich-rule='rule family="ipv4" source address="APP_SERVER_IP/32" port port="6379" protocol="tcp" accept'
sudo firewall-cmd --reload
sudo firewall-cmd --list-rich-rules
```
Allows TCP/6379 **only** from the AIOps app server (`/32` = that one host); everyone else blocked. `--permanent` persists, `--reload` activates, `--list-rich-rules` confirms. Replace `APP_SERVER_IP`.

---

## 8. Verify locally on the server

```bash
sudo systemctl is-active redis
ss -tlnp | grep 6379
/opt/redis/bin/redis-cli -a 'CHANGE_ME_STRONG_SECRET' -h 172.17.133.47 ping
/opt/redis/bin/redis-cli -a 'CHANGE_ME_STRONG_SECRET' -h 172.17.133.47 set t 1 EX 10
/opt/redis/bin/redis-cli -a 'CHANGE_ME_STRONG_SECRET' -h 172.17.133.47 get t
```
- `is-active` — quick state check.
- `ss -tlnp | grep 6379` — confirms it's listening on `172.17.133.47:6379` (bind worked).
- `redis-cli -a <pw> -h <ip> ping` → `PONG` (tests over the private IP, like the app does).
- `set … EX 10` / `get` — round-trip with TTL (how dedup keys self-clean).

---

## 9. Corporate offline notes (SSL inspection / DNS)

```bash
sudo cp corporate-root-ca.crt /etc/pki/ca-trust/source/anchors/
sudo update-ca-trust
```
Corporate networks intercept TLS with their own CA; importing it into the system trust store makes `curl`/`dnf` trust inspected connections. If DNS also fails, copy the tarball manually.

---

## 10. Optional — ACL user instead of a shared password

```bash
/opt/redis/bin/redis-cli -a 'CHANGE_ME_STRONG_SECRET' ACL SETUSER aiops on '>APP_PASSWORD' '~*' '+@all'
/opt/redis/bin/redis-cli -a 'CHANGE_ME_STRONG_SECRET' ACL LIST
```
`ACL SETUSER aiops on >APP_PASSWORD ~* +@all` — enabled user `aiops`, password (`>…`), all keys (`~*`), all commands (`+@all`). Put `aiops` in **Username** in Settings. With plain `requirepass`, leave **Username blank** (tester AUTHs password-only).

---

## 11. Optional — TLS

```bash
make BUILD_TLS=yes -j"$(nproc)"      # source build with TLS support
```
`redis.conf`:
```conf
port 0
tls-port 6379
tls-cert-file /opt/redis/etc/redis.crt
tls-key-file  /opt/redis/etc/redis.key
tls-ca-cert-file /opt/redis/etc/ca.crt
```
- `BUILD_TLS=yes` — compile TLS (off by default).
- `port 0` + `tls-port 6379` — plaintext off, TLS on.
- Enable the **TLS toggle** in Settings; the server cert must be trusted by the **AIOps JVM truststore** (import the CA into the JDK `cacerts` — OS trust is not enough for Java).

---

## 12. Connect it to AIOps

**Settings → Servers & Index → Add Server → Redis Server:**

| Field | Value |
|---|---|
| Host / IP | `172.17.133.47` (private IP from §4 — not localhost) |
| Port | `6379` |
| Username | blank (or `aiops` if §10) |
| Password | your `requirepass` / ACL password |
| Database | `0` (DB 0 only; isolation by key prefix) |
| TLS | off (or on if §11) |

**Test & Save** → green means the backend reached Redis (`PING`→`PONG`). The runtime client then uses this saved, encrypted connection for `dedup:{country}:{env}:…` keys.

---

## 13. Troubleshooting → maps to the AIOps test messages

| Test message | Cause | Fix |
|---|---|---|
| "host is blocked by SSRF protection" | Entered `localhost`/`127.0.0.1` | Use the private IP (§4 `bind`) |
| "connect timed out" | Firewall / bind | §7 rule + `bind` includes the private IP |
| "authentication failed (`-WRONGPASS`)" | Wrong password / username set for `requirepass` | Correct password; username blank unless ACL |
| "`-DENIED` … protected mode" | `protected-mode` + no/incorrect auth | Set `requirepass` and authenticate |
| "server may require TLS / empty reply" | TLS mismatch | Match the TLS toggle to the server (§11) |
