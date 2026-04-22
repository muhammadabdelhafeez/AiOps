/* global React, ReactDOM, Recharts, lucide */
(function () {
  'use strict';

  const h = React.createElement;

  // ---------- URL/state helpers ----------
  function getParams() {
    const url = new URL(window.location.href);
    return {
      id: url.searchParams.get('id') || 'new',
      mode: url.searchParams.get('mode') || (url.searchParams.get('id') ? 'edit' : 'create')
    };
  }

  function setMode(mode) {
    const url = new URL(window.location.href);
    url.searchParams.set('mode', mode);
    window.history.replaceState({}, '', url.toString());
  }

  function setId(id) {
    const url = new URL(window.location.href);
    url.searchParams.set('id', id);
    window.history.replaceState({}, '', url.toString());
  }

  // ---------- Mock domain data (UI only) ----------
  function generateId() {
    return Math.random().toString(36).slice(2, 10);
  }

  const SOURCES = ['SCOM', 'vROps', 'BMC', 'SolarWinds', 'Elastic'];
  const DOMAINS = ['Core Banking', 'Digital Channels', 'Treasury', 'Risk Management', 'HR Systems', 'Infrastructure'];
  const ENVIRONMENTS = ['Production', 'UAT', 'Development', 'DR'];
  const CRITICALITIES = ['Tier1', 'Tier2', 'Tier3', 'Tier4'];

  function rand(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
  }

  function choice(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
  }

  function hourlyData(hours) {
    const data = [];
    const now = new Date();
    for (let i = hours - 1; i >= 0; i--) {
      const time = new Date(now.getTime() - i * 60 * 60 * 1000);
      data.push({
        hour: time.getHours().toString().padStart(2, '0') + ':00',
        total: rand(5, 50),
        new: rand(2, 20),
        recurring: rand(3, 30)
      });
    }
    return data;
  }

  function createSampleApp(id, name, shortCode, domain, env, criticality) {
    const open = rand(0, 12);
    const criticalOpen = Math.min(open, rand(0, 3));
    const healthScore = Math.max(0, Math.min(100, 100 - criticalOpen * 10 - (open - criticalOpen) * 2));
    const healthStatus = healthScore >= 80 ? 'Good' : (healthScore >= 50 ? 'Degraded' : 'Critical');

    return {
      id,
      name,
      shortCode,
      domain,
      environment: env,
      criticality,
      businessOwner: choice(['Ahmed Al-Rashid', 'Fatima Hassan', 'Sara Al-Farsi']),
      techOwnerTeam: choice(['Platform Engineering', 'Core Systems', 'Digital Team', 'Infrastructure']),
      escalationGroup: `ESC-${shortCode}`,
      sources: SOURCES.slice(0, rand(2, 5)),
      healthScore,
      healthStatus,
      metrics: {
        open,
        criticalOpen,
        newLast15d: rand(0, 10),
        recurringLast15d: rand(0, 10),
        mtta: rand(5, 60),
        mttr: rand(30, 180),
        noisyGroups: rand(0, 6)
      },
      hourly: hourlyData(24),
      config: {
        onboardingSources: Object.fromEntries(SOURCES.map(s => [s, { enabled: true, filter: '' }])) ,
        mttrTarget: rand(30, 120),
        criticalThreshold: rand(3, 10),
        noiseSuppressionEnabled: Math.random() > 0.5,
        recurrenceRules: {
          exactMatchEnabled: true,
          familyMatchEnabled: true,
          embeddingMatchEnabled: Math.random() > 0.5
        },
        notifications: {
          teamsWebhook: '',
          emailDistro: '',
          postOnCritical: true
        }
      }
    };
  }

  const SAMPLE_APPS = {
    'app-cbs-001': createSampleApp('app-cbs-001', 'Core Banking System', 'CBS', 'Core Banking', 'Production', 'Tier1'),
    'app-mob-002': createSampleApp('app-mob-002', 'Mobile Banking App', 'MBA', 'Digital Channels', 'Production', 'Tier1'),
    'app-pay-003': createSampleApp('app-pay-003', 'Payment Gateway', 'PGW', 'Core Banking', 'Production', 'Tier1'),
    'app-crm-004': createSampleApp('app-crm-004', 'Customer Portal', 'CRM', 'Digital Channels', 'UAT', 'Tier2')
  };

  // ---------- Local persistence (shared with Applications page; UI-only) ----------
  const STORAGE_KEY = 'kfh.aiops.apps.v1';

  function loadPersistedApps() {
    try {
      const raw = window.localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed : null;
    } catch (e) {
      return null;
    }
  }

  function savePersistedApps(apps) {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(apps || []));
    } catch (e) {
      // ignore (private mode/quota)
    }
  }

  function findPersistedAppById(appId) {
    const all = loadPersistedApps();
    if (!all || !all.length) return null;
    return all.find(a => a && a.id === appId) || null;
  }

  function upsertPersistedApp(app) {
    const all = loadPersistedApps() || [];
    const idx = all.findIndex(a => a && a.id === app.id);
    if (idx >= 0) all[idx] = app;
    else all.unshift(app);
    savePersistedApps(all);
  }

  // ---------- UI primitives ----------
  function Badge(props) {
    const base = 'inline-flex items-center px-2 py-0.5 rounded-md border text-[10px] font-bold uppercase tracking-wider';
    return h('span', { className: base + ' ' + (props.className || '') }, props.children);
  }

  function Card(props) {
    return h('div', { className: 'glass-card rounded-2xl p-6 ' + (props.className || '') }, props.children);
  }

  function SectionTitle(props) {
    return h('div', { className: 'flex items-center gap-2 mb-4' },
      h('div', { className: 'w-2 h-2 rounded-full bg-[var(--kfh-primary)]' }),
      h('div', { className: 'text-sm font-extrabold text-[var(--text-primary)] tracking-tight' }, props.children)
    );
  }

  function TextInput(props) {
    return h('input', {
      className: 'kfh-focus w-full px-3 py-2 rounded-xl bg-white border border-gray-200 text-sm text-[var(--text-primary)] placeholder-gray-400 transition-all',
      value: props.value,
      type: props.type || 'text',
      placeholder: props.placeholder,
      onChange: e => props.onChange(e.target.value)
    });
  }

  function SelectInput(props) {
    return h('select', {
      className: 'kfh-focus w-full px-3 py-2 rounded-xl bg-white border border-gray-200 text-sm text-[var(--text-primary)] transition-all',
      value: props.value,
      onChange: e => props.onChange(e.target.value)
    },
      props.options.map(o => h('option', { key: o, value: o }, o))
    );
  }

  function Field(props) {
    return h('div', { className: props.className || '' },
      h('label', { className: 'block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2' }, props.label),
      props.children
    );
  }

  // ---------- Page ----------
  function ApplicationConfigPage() {
    const { id, mode } = React.useMemo(getParams, []);
    const isCreate = id === 'new' || mode === 'create';
    const isView = mode === 'view';

    const [app, setApp] = React.useState(() => {
      // Prefer apps created/edited in the Applications page drawer.
      const persisted = id ? findPersistedAppById(id) : null;
      if (persisted) {
        // Normalize fields from Applications page model into config-page model.
        return {
          id: persisted.id,
          name: persisted.name,
          shortCode: persisted.shortCode,
          domain: persisted.domain,
          environment: persisted.environment,
          criticality: persisted.criticality,
          businessOwner: persisted.businessOwner || choice(['Ahmed Al-Rashid', 'Fatima Hassan', 'Sara Al-Farsi']),
          techOwnerTeam: persisted.techOwnerTeam || choice(['Platform Engineering', 'Core Systems', 'Digital Team', 'Infrastructure']),
          escalationGroup: persisted.escalationGroup || `ESC-${persisted.shortCode}`,
          sources: (persisted.onboardedSources && persisted.onboardedSources.length) ? persisted.onboardedSources : SOURCES.slice(0, rand(2, 5)),
          healthScore: typeof persisted.healthScore === 'number' ? persisted.healthScore : 86,
          healthStatus: (typeof persisted.healthScore === 'number')
            ? (persisted.healthScore >= 80 ? 'Good' : (persisted.healthScore >= 50 ? 'Degraded' : 'Critical'))
            : 'Good',
          metrics: {
            open: persisted.incidentStats?.openTotal ?? rand(0, 12),
            criticalOpen: persisted.incidentStats?.openCritical ?? rand(0, 3),
            newLast15d: persisted.incidentStats?.new15d ?? rand(0, 10),
            recurringLast15d: persisted.incidentStats?.recurring15d ?? rand(0, 10),
            mtta: rand(5, 60),
            mttr: rand(30, 180),
            noisyGroups: rand(0, 6)
          },
          hourly: hourlyData(24),
          config: Object.assign({}, createSampleApp('tmp', 'tmp', 'TMP', DOMAINS[0], ENVIRONMENTS[0], CRITICALITIES[0]).config, persisted.config || {})
        };
      }

      if (isCreate) {
        return createSampleApp('app-new-' + generateId(), 'New Application', 'NEW', DOMAINS[0], ENVIRONMENTS[0], CRITICALITIES[0]);
      }
      return SAMPLE_APPS[id] || SAMPLE_APPS['app-cbs-001'];
    });

    const [dirty, setDirty] = React.useState(false);
    const [toast, setToast] = React.useState(null);
    const [activeTab, setActiveTab] = React.useState(isView ? 'overview' : 'overview');

    // --- extra mock data for the restored tabs ---
    const SEVERITIES = React.useMemo(() => ['Critical', 'High', 'Medium', 'Low'], []);
    const STATUSES = React.useMemo(() => ['Open', 'Acknowledged', 'InProgress', 'Resolved', 'Closed'], []);

    function badgeForHealth(status) {
      if (status === 'Good') return 'kfh-badge kfh-badge--good';
      if (status === 'Degraded') return 'kfh-badge kfh-badge--degraded';
      return 'kfh-badge kfh-badge--critical';
    }

    function badgeForSeverity(sev) {
      if (sev === 'Critical') return 'kfh-badge kfh-badge--sev-critical';
      if (sev === 'High') return 'kfh-badge kfh-badge--sev-high';
      if (sev === 'Medium') return 'kfh-badge kfh-badge--sev-medium';
      return 'kfh-badge kfh-badge--sev-low';
    }

    function makeIncidentList() {
      const mk = (idx) => {
        const sev = choice(SEVERITIES);
        const st = choice(STATUSES);
        const src = choice(SOURCES);
        const now = Date.now();
        const createdAt = now - rand(0, 15 * 24 * 60 * 60 * 1000);
        return {
          id: 'INC-' + String(idx).padStart(4, '0'),
          title: `${sev}: ${choice(['CPU utilization', 'Memory pressure', 'Disk I/O', 'Network timeout', 'Service degradation'])} on ${choice(['KFHPRD', 'KFHUAT', 'KFHDR'])}-${choice(['APP', 'DB', 'WEB', 'API'])}-${String(rand(1, 99)).padStart(2, '0')}`,
          severity: sev,
          source: src,
          status: st,
          createdAt,
          lastSeenAt: now - rand(0, 2 * 60 * 60 * 1000)
        };
      };
      return Array.from({ length: 50 }, (_, i) => mk(i + 1));
    }

    const [incidents] = React.useState(() => makeIncidentList());

    const [alerts] = React.useState(() => {
      const now = Date.now();
      const types = ['Availability', 'Performance', 'Capacity', 'Security', 'Network'];
      const mk = (idx) => {
        const sev = choice(SEVERITIES);
        const src = choice(SOURCES);
        const when = now - rand(0, 8 * 60 * 60 * 1000);
        return {
          id: 'ALT-' + String(idx).padStart(5, '0'),
          title: `${choice(types)} ￨ ${choice(['Threshold breached', 'Anomaly detected', 'Failure', 'Degradation'])}`,
          severity: sev,
          source: src,
          affected: `${choice(['KFHPRD', 'KFHUAT', 'KFHDR'])}-${choice(['APP', 'DB', 'WEB', 'API'])}-${String(rand(1, 99)).padStart(2, '0')}`,
          status: choice(['Open', 'Suppressed', 'Acknowledged', 'Resolved']),
          createdAt: when
        };
      };
      return Array.from({ length: 60 }, (_, i) => mk(i + 1));
    });

    const [inventory] = React.useState(() => {
      // If this app came from Applications page, reuse its inventoryItems.
      const persisted = id ? findPersistedAppById(id) : null;
      if (persisted && Array.isArray(persisted.inventoryItems)) {
        // Convert to config-page inventory schema (minimal properties used by UI).
        return persisted.inventoryItems.map(it => ({
          id: it.id || ('INV-' + generateId()),
          type: it.type || 'Server',
          name: it.name || 'Unnamed',
          status: it.status || 'Healthy',
          identifier: it.identifier || '',
          lastChecked: it.lastChecked || new Date().toISOString()
        }));
      }

      // Match the strip categories used in the card screenshot.
      const items = [];
      for (let i = 0; i < rand(6, 18); i++) items.push({ id: 'INV-' + generateId(), type: 'Server', name: 'SERVER-' + String(i + 1).padStart(2, '0'), status: choice(['Healthy', 'Warning', 'Critical']) });
      for (let i = 0; i < rand(1, 6); i++) items.push({ id: 'INV-' + generateId(), type: 'Database', name: 'DB-' + String(i + 1).padStart(2, '0'), status: choice(['Healthy', 'Warning', 'Critical']) });
      for (let i = 0; i < rand(0, 10); i++) items.push({ id: 'INV-' + generateId(), type: 'K8s', name: 'K8S-' + String(i + 1).padStart(2, '0'), status: choice(['Healthy', 'Warning', 'Critical']) });
      for (let i = 0; i < rand(1, 8); i++) items.push({ id: 'INV-' + generateId(), type: 'URL', name: 'URL-' + String(i + 1).padStart(2, '0'), status: choice(['Healthy', 'Warning', 'Critical']) });
      return items;
    });

    const [draft, setDraft] = React.useState(() => ({
      name: app.name,
      shortCode: app.shortCode,
      domain: app.domain,
      environment: app.environment,
      criticality: app.criticality,
      businessOwner: app.businessOwner,
      techOwnerTeam: app.techOwnerTeam,
      escalationGroup: app.escalationGroup,
      mttrTarget: app.config.mttrTarget,
      criticalThreshold: app.config.criticalThreshold,
      noiseSuppressionEnabled: app.config.noiseSuppressionEnabled,
      notifications: Object.assign({}, app.config.notifications),
      onboardingSources: Object.assign({}, app.config.onboardingSources)
    }));

    React.useEffect(() => {
      // Header title/subtitle + Top right buttons (Reset/Save)
      // In view-mode we hide these controls.
      const title = document.getElementById('pageTitle');
      const subtitle = document.getElementById('pageSubtitle');
      const btnReset = document.getElementById('btnReset');
      const btnSave = document.getElementById('btnSave');

      if (title) title.textContent = isCreate ? 'Create Application' : (isView ? app.name : `Application: ${app.name}`);
      if (subtitle) subtitle.textContent = isCreate
        ? 'Define identity, sources, thresholds & notifications'
        : `${app.shortCode} ￨ ${app.domain} ￨ ${app.environment} ￨ ${app.criticality}`;

      if (btnReset) {
        btnReset.style.display = isView ? 'none' : 'inline-flex';
        btnReset.onclick = () => reset();
      }

      if (btnSave) {
        btnSave.style.display = isView ? 'none' : 'inline-flex';
        btnSave.textContent = isCreate ? 'Create' : 'Save';
        btnSave.onclick = () => save();
      }
    }, [app, isCreate, isView]);

    React.useEffect(() => {
      if (window.lucide && typeof window.lucide.createIcons === 'function') {
        window.lucide.createIcons();
      }
    });

    // If URL or old state tries to route to removed tabs, normalize.
    React.useEffect(() => {
      if (activeTab === 'evidence') setActiveTab('overview');
    }, [activeTab]);

    function pushToast(type, msg) {
      setToast({ type, msg });
      window.setTimeout(() => setToast(null), 2500);
    }

    function reset() {
      setDraft({
        name: app.name,
        shortCode: app.shortCode,
        domain: app.domain,
        environment: app.environment,
        criticality: app.criticality,
        businessOwner: app.businessOwner,
        techOwnerTeam: app.techOwnerTeam,
        escalationGroup: app.escalationGroup,
        mttrTarget: app.config.mttrTarget,
        criticalThreshold: app.config.criticalThreshold,
        noiseSuppressionEnabled: app.config.noiseSuppressionEnabled,
        notifications: Object.assign({}, app.config.notifications),
        onboardingSources: Object.assign({}, app.config.onboardingSources)
      });
      setDirty(false);
      pushToast('info', 'Reset changes');
    }

    function save() {
      if (isView) return;

      // Basic validation for creation
      if (!draft.name || !draft.shortCode) {
        pushToast('error', 'Name and short code are required');
        return;
      }

      const normalizedShort = String(draft.shortCode || '').toUpperCase().slice(0, 8);

      const next = Object.assign({}, app, {
        name: String(draft.name).trim(),
        shortCode: normalizedShort,
        domain: draft.domain,
        environment: draft.environment,
        criticality: draft.criticality,
        businessOwner: draft.businessOwner,
        techOwnerTeam: draft.techOwnerTeam,
        escalationGroup: draft.escalationGroup,
        config: Object.assign({}, app.config, {
          mttrTarget: Number(draft.mttrTarget) || app.config.mttrTarget,
          criticalThreshold: Number(draft.criticalThreshold) || app.config.criticalThreshold,
          noiseSuppressionEnabled: !!draft.noiseSuppressionEnabled,
          notifications: Object.assign({}, draft.notifications),
          onboardingSources: Object.assign({}, draft.onboardingSources)
        })
      });

      setApp(next);
      setDirty(false);

      if (isCreate) {
        // Update URL to be a concrete id to simulate "created" entity.
        const createdId = 'app-' + normalizedShort.toLowerCase() + '-' + generateId();
        setId(createdId);
        setMode('edit');
        pushToast('success', 'Application created (mock)');
      } else {
        pushToast('success', 'Configuration saved (mock)');
      }
    }

    function ToastView() {
      if (!toast) return null;
      const cls = toast.type === 'success'
        ? 'bg-[var(--kfh-primary-light)] text-[var(--kfh-primary-darker)] border-[var(--kfh-primary)]/20'
        : toast.type === 'error'
          ? 'bg-red-50 text-red-700 border-red-200'
          : 'bg-blue-50 text-blue-700 border-blue-200';
      return h('div', { className: 'fixed top-6 right-6 z-50 animate-fade-in' },
        h('div', { className: 'px-4 py-3 rounded-xl border shadow-sm ' + cls },
          h('div', { className: 'text-sm font-bold' }, toast.msg)
        )
      );
    }

    function setDraftField(key, value) {
      setDraft(prev => Object.assign({}, prev, { [key]: value }));
      setDirty(true);
    }

    function toggleOnboardingSource(source) {
      setDraft(prev => {
        const next = Object.assign({}, prev.onboardingSources);
        next[source] = Object.assign({}, next[source], { enabled: !next[source].enabled });
        return Object.assign({}, prev, { onboardingSources: next });
      });
      setDirty(true);
    }

    function setOnboardingFilter(source, filter) {
      setDraft(prev => {
        const next = Object.assign({}, prev.onboardingSources);
        next[source] = Object.assign({}, next[source], { filter: filter });
        return Object.assign({}, prev, { onboardingSources: next });
      });
      setDirty(true);
    }

    function setNotifications(nextNotifications) {
      setDraft(prev => Object.assign({}, prev, { notifications: nextNotifications }));
      setDirty(true);
    }

    function invCountsFromInventory(list) {
      const counts = { servers: 0, databases: 0, k8s: 0, urls: 0 };
      (list || []).forEach(it => {
        if (it.type === 'Server') counts.servers++;
        else if (it.type === 'Database') counts.databases++;
        else if (it.type === 'K8s') counts.k8s++;
        else if (it.type === 'URL') counts.urls++;
      });
      return counts;
    }

    // Inventory strip: mimic screenshot (icons + counts + items) + NO updated timestamp
    function InventoryStrip() {
      const invCounts = invCountsFromInventory(inventory);
      const total = invCounts.servers + invCounts.databases + invCounts.k8s + invCounts.urls;

      function chip(iconName, label, count) {
        return h('span', { className: 'kfh-pill' },
          h('i', { className: 'w-4 h-4 text-gray-500', 'data-lucide': iconName }),
          h('span', { className: 'text-xs font-extrabold text-gray-700' }, label),
          h('span', { className: 'px-2 py-0.5 rounded-full bg-[var(--kfh-primary-light)] text-[var(--kfh-primary-darker)] text-xs font-extrabold border border-[var(--kfh-primary)]/15' }, String(count))
        );
      }

      return h('div', { className: 'mt-5 flex flex-wrap items-center gap-3' },
        chip('server', 'Servers', invCounts.servers),
        chip('database', 'DB', invCounts.databases),
        chip('cube', 'K8s', invCounts.k8s),
        chip('globe', 'URLs', invCounts.urls),
        h('div', { className: 'ml-auto kfh-pill' },
          h('i', { className: 'w-4 h-4 text-gray-500', 'data-lucide': 'layers' }),
          h('span', { className: 'text-xs font-extrabold text-gray-700' }, total + ' items')
        )
      );
    }

    // ----- Tabs -----
    function TabBar() {
      const tabs = [
        { id: 'overview', label: 'Overview', icon: 'sparkles' },
        { id: 'incidents', label: 'Incidents', icon: 'siren' },
        { id: 'alerts', label: 'Alerts', icon: 'triangle-alert' },
        { id: 'topology', label: 'Topology', icon: 'network' },
        { id: 'configuration', label: 'Configuration', icon: 'settings' }
      ];

      // Keep UX consistent: always show the full tab set.
      // In create flow, we simply start on Configuration (see initial activeTab),
      // but allow users to preview the other tabs.
      const visibleTabs = tabs;

      return h('div', { className: 'kfh-tabbar mb-6' },
        visibleTabs.map(t => h('button', {
          key: t.id,
          type: 'button',
          onClick: () => setActiveTab(t.id),
          className: 'kfh-tab',
          role: 'tab',
          'aria-selected': activeTab === t.id ? 'true' : 'false'
        },
          h('i', { className: 'w-4 h-4', 'data-lucide': t.icon }),
          h('span', null, t.label)
        ))
      );
    }

    function Kpi(props) {
      return h('div', { className: 'kfh-kpi p-5' },
        h('div', { className: 'flex items-start justify-between gap-3' },
          h('div', null,
            h('div', { className: 'kfh-kpi-label' }, props.label),
            h('div', { className: 'kfh-kpi-value mt-2 ' + (props.valueClass || '') }, props.value)
          ),
          props.icon ? h('div', { className: 'w-10 h-10 rounded-2xl bg-[var(--kfh-primary-light)] border border-[var(--kfh-primary)]/15 flex items-center justify-center text-[var(--kfh-primary-darker)]' },
            h('i', { className: 'w-5 h-5', 'data-lucide': props.icon })
          ) : null
        ),
        props.footer ? h('div', { className: 'mt-3 text-xs text-gray-500 font-semibold' }, props.footer) : null
      );
    }

    function OverviewTab() {
      const openAlerts = alerts.filter(a => a.status === 'Open').length;
      const criticalAlerts = alerts.filter(a => a.severity === 'Critical' && a.status === 'Open').length;

      return h('div', { className: 'space-y-6' },
        h(Card, null,
          h('div', { className: 'flex flex-col md:flex-row md:items-center md:justify-between gap-4' },
            h('div', null,
              h('div', { className: 'text-lg font-extrabold text-gray-900' }, app.name),
              h('div', { className: 'mt-2 flex flex-wrap items-center gap-2 text-sm text-gray-600 font-semibold' },
                h('span', { className: 'kfh-pill' },
                  h('i', { className: 'w-4 h-4 text-gray-500', 'data-lucide': 'code' }),
                  h('span', null, app.shortCode)
                ),
                h('span', { className: 'kfh-pill' },
                  h('i', { className: 'w-4 h-4 text-gray-500', 'data-lucide': 'building-2' }),
                  h('span', null, app.domain)
                ),
                h('span', { className: 'kfh-pill' },
                  h('i', { className: 'w-4 h-4 text-gray-500', 'data-lucide': 'cloud' }),
                  h('span', null, app.environment)
                ),
                h('span', { className: 'kfh-pill' },
                  h('i', { className: 'w-4 h-4 text-gray-500', 'data-lucide': 'shield' }),
                  h('span', null, app.criticality)
                ),
                h('span', { className: badgeForHealth(app.healthStatus) }, app.healthStatus)
              )
            ),
            h('div', { className: 'flex flex-wrap items-center gap-2' },
              app.sources.map(s => h('span', { key: s, className: 'kfh-pill text-xs font-extrabold text-gray-700' },
                h('i', { className: 'w-4 h-4 text-gray-500', 'data-lucide': 'database' }),
                s
              ))
            )
          ),
          !isCreate ? h(InventoryStrip) : null
        ),

        h('div', { className: 'grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4' },
          h(Kpi, { label: 'Health Score', value: String(app.healthScore), valueClass: 'text-[var(--kfh-primary-darker)]', icon: 'activity', footer: 'Composite score (mock)' }),
          h(Kpi, { label: 'Open Incidents', value: String(app.metrics.open), icon: 'siren' }),
          h(Kpi, { label: 'Open Alerts', value: String(openAlerts), icon: 'triangle-alert', footer: criticalAlerts ? `${criticalAlerts} critical` : 'No critical open' }),
          h(Kpi, { label: 'MTTR Target', value: String(app.config.mttrTarget) + 'm', icon: 'clock-4', footer: 'From configuration' })
        ),

        (window.Recharts && window.Recharts.AreaChart) ? h(Card, null,
          h('div', { className: 'flex items-center justify-between mb-4' },
            h('div', { className: 'text-sm font-extrabold text-gray-900' }, 'Incidents Trend (24h)'),
            h('div', { className: 'text-xs font-semibold text-gray-500' }, 'Total / hour')
          ),
          h('div', { style: { height: '260px' } },
            h(window.Recharts.ResponsiveContainer, { width: '100%', height: '100%' },
              h(window.Recharts.AreaChart, { data: app.hourly },
                h('defs', null,
                  h('linearGradient', { id: 'kfhTotal', x1: '0', y1: '0', x2: '0', y2: '1' },
                    h('stop', { offset: '0%', stopColor: 'var(--kfh-primary)', stopOpacity: 0.25 }),
                    h('stop', { offset: '100%', stopColor: 'var(--kfh-primary)', stopOpacity: 0 })
                  )
                ),
                h(window.Recharts.CartesianGrid, { strokeDasharray: '3 3', stroke: 'rgba(0,0,0,0.06)' }),
                h(window.Recharts.XAxis, { dataKey: 'hour', stroke: '#9CA3AF', tick: { fill: '#9CA3AF', fontSize: 10 } }),
                h(window.Recharts.YAxis, { stroke: '#9CA3AF', tick: { fill: '#9CA3AF', fontSize: 10 } }),
                h(window.Recharts.Tooltip, { contentStyle: { backgroundColor: '#ffffff', border: '1px solid rgba(0,0,0,0.06)', borderRadius: '14px', boxShadow: '0 18px 40px rgba(0,0,0,0.10)' } }),
                h(window.Recharts.Area, { type: 'monotone', dataKey: 'total', stroke: 'var(--kfh-primary)', fill: 'url(#kfhTotal)', strokeWidth: 2 })
              )
            )
          )
        ) : null
      );
    }

    function IncidentsTab() {
      return h(Card, null,
        h('div', { className: 'flex flex-col md:flex-row md:items-center md:justify-between gap-3 mb-4' },
          h('div', null,
            h('div', { className: 'text-sm font-extrabold text-gray-900' }, 'Incidents'),
            h('div', { className: 'text-xs text-gray-500 font-semibold' }, 'Latest operational incidents for this application (mock)')
          ),
          h('div', { className: 'kfh-pill' },
            h('i', { className: 'w-4 h-4 text-gray-500', 'data-lucide': 'list' }),
            h('span', { className: 'text-xs font-extrabold text-gray-700' }, incidents.length + ' total')
          )
        ),
        h('div', { className: 'overflow-x-auto rounded-2xl border border-black/5 bg-white' },
          h('table', { className: 'kfh-table text-sm' },
            h('thead', null,
              h('tr', { className: 'text-left text-[11px] uppercase tracking-wider text-gray-500 border-b border-black/5' },
                h('th', { className: 'py-3 px-4' }, 'Severity'),
                h('th', { className: 'py-3 px-4' }, 'Incident'),
                h('th', { className: 'py-3 px-4' }, 'Source'),
                h('th', { className: 'py-3 px-4' }, 'Status'),
                h('th', { className: 'py-3 px-4' }, 'Last Seen')
              )
            ),
            h('tbody', null,
              incidents.slice(0, 30).map((inc) => h('tr', { key: inc.id, className: 'kfh-row border-b border-black/5' },
                h('td', { className: 'py-3 px-4' }, h('span', { className: badgeForSeverity(inc.severity) }, inc.severity)),
                h('td', { className: 'py-3 px-4' },
                  h('div', { className: 'font-extrabold text-gray-900' }, inc.title),
                  h('div', { className: 'text-xs text-gray-500 font-mono mt-1' }, inc.id)
                ),
                h('td', { className: 'py-3 px-4 font-semibold text-gray-700' }, inc.source),
                h('td', { className: 'py-3 px-4 text-gray-700 font-semibold' }, inc.status),
                h('td', { className: 'py-3 px-4 text-gray-600 font-semibold' }, new Date(inc.lastSeenAt).toLocaleString())
              ))
            )
          )
        )
      );
    }

    function AlertsTab() {
      const openCount = alerts.filter(a => a.status === 'Open').length;
      const critOpen = alerts.filter(a => a.status === 'Open' && a.severity === 'Critical').length;

      return h('div', { className: 'space-y-6' },
        h('div', { className: 'grid grid-cols-1 md:grid-cols-3 gap-4' },
          h(Kpi, { label: 'Open Alerts', value: String(openCount), icon: 'triangle-alert' }),
          h(Kpi, { label: 'Critical Open', value: String(critOpen), icon: 'flame', valueClass: 'text-red-600' }),
          h(Kpi, { label: 'Sources', value: String(new Set(alerts.map(a => a.source)).size), icon: 'database' })
        ),
        h(Card, null,
          h('div', { className: 'flex items-center justify-between mb-4' },
            h('div', null,
              h('div', { className: 'text-sm font-extrabold text-gray-900' }, 'Alerts'),
              h('div', { className: 'text-xs text-gray-500 font-semibold' }, 'Raw alerts before incident correlation (mock)')
            ),
            h('div', { className: 'kfh-pill' },
              h('i', { className: 'w-4 h-4 text-gray-500', 'data-lucide': 'bell' }),
              h('span', { className: 'text-xs font-extrabold text-gray-700' }, alerts.length + ' total')
            )
          ),
          h('div', { className: 'overflow-x-auto rounded-2xl border border-black/5 bg-white' },
            h('table', { className: 'kfh-table text-sm' },
              h('thead', null,
                h('tr', { className: 'text-left text-[11px] uppercase tracking-wider text-gray-500 border-b border-black/5' },
                  h('th', { className: 'py-3 px-4' }, 'Severity'),
                  h('th', { className: 'py-3 px-4' }, 'Alert'),
                  h('th', { className: 'py-3 px-4' }, 'Source'),
                  h('th', { className: 'py-3 px-4' }, 'Affected'),
                  h('th', { className: 'py-3 px-4' }, 'Status'),
                  h('th', { className: 'py-3 px-4' }, 'Time')
                )
              ),
              h('tbody', null,
                alerts.slice(0, 30).map(a => h('tr', { key: a.id, className: 'kfh-row border-b border-black/5' },
                  h('td', { className: 'py-3 px-4' }, h('span', { className: badgeForSeverity(a.severity) }, a.severity)),
                  h('td', { className: 'py-3 px-4' },
                    h('div', { className: 'font-extrabold text-gray-900' }, a.title),
                    h('div', { className: 'text-xs text-gray-500 font-mono mt-1' }, a.id)
                  ),
                  h('td', { className: 'py-3 px-4 font-semibold text-gray-700' }, a.source),
                  h('td', { className: 'py-3 px-4 text-gray-700 font-semibold' }, a.affected),
                  h('td', { className: 'py-3 px-4 text-gray-700 font-semibold' }, a.status),
                  h('td', { className: 'py-3 px-4 text-gray-600 font-semibold' }, new Date(a.createdAt).toLocaleString())
                ))
              )
            )
          )
        )
      );
    }

    function TopologyTab() {
      const counts = invCountsFromInventory(inventory);
      return h('div', { className: 'space-y-6' },
        h('div', { className: 'grid grid-cols-1 md:grid-cols-4 gap-4' },
          h(Kpi, { label: 'Servers', value: String(counts.servers), icon: 'server' }),
          h(Kpi, { label: 'Databases', value: String(counts.databases), icon: 'database' }),
          h(Kpi, { label: 'K8s', value: String(counts.k8s), icon: 'cube' }),
          h(Kpi, { label: 'URLs', value: String(counts.urls), icon: 'globe' })
        ),
        h(Card, null,
          h('div', { className: 'text-sm font-extrabold text-gray-900' }, 'Topology'),
          h('div', { className: 'mt-2 text-xs text-gray-500 font-semibold' },
            'Topology visualization can be wired to Neo4j later. This is a UI placeholder.'
          )
        )
      );
    }

    function ConfigurationTab() {
      return h('div', { className: 'space-y-6' },
        // Identity
        h(Card, null,
          h(SectionTitle, null, 'Identity'),
          h('div', { className: 'grid grid-cols-1 md:grid-cols-3 gap-4' },
            h(Field, { label: 'Application Name' }, h(TextInput, { value: draft.name, onChange: v => setDraftField('name', v), placeholder: 'Application name', disabled: isView })),
            h(Field, { label: 'Short Code' }, h(TextInput, { value: draft.shortCode, onChange: v => setDraftField('shortCode', v.toUpperCase().slice(0, 8)), placeholder: 'CBS', disabled: isView })),
            h(Field, { label: 'Domain' }, h(SelectInput, { value: draft.domain, options: DOMAINS, onChange: v => setDraftField('domain', v), disabled: isView }))
          ),
          h('div', { className: 'mt-4 grid grid-cols-1 md:grid-cols-3 gap-4' },
            h(Field, { label: 'Environment' }, h(SelectInput, { value: draft.environment, options: ENVIRONMENTS, onChange: v => setDraftField('environment', v), disabled: isView })),
            h(Field, { label: 'Criticality' }, h(SelectInput, { value: draft.criticality, options: CRITICALITIES, onChange: v => setDraftField('criticality', v), disabled: isView })),
            h(Field, { label: 'Escalation Group' }, h(TextInput, { value: draft.escalationGroup, onChange: v => setDraftField('escalationGroup', v), placeholder: 'ESC-XXX', disabled: isView }))
          ),
          !isCreate ? h(InventoryStrip) : null
        ),

        // Ownership
        h(Card, null,
          h(SectionTitle, null, 'Ownership'),
          h('div', { className: 'grid grid-cols-1 md:grid-cols-2 gap-4' },
            h(Field, { label: 'Business Owner' }, h(TextInput, { value: draft.businessOwner, onChange: v => setDraftField('businessOwner', v), placeholder: 'name@kfh.com', disabled: isView })),
            h(Field, { label: 'Tech Owner Team' }, h(TextInput, { value: draft.techOwnerTeam, onChange: v => setDraftField('techOwnerTeam', v), placeholder: 'Platform Engineering', disabled: isView }))
          )
        ),

        // Onboarding Sources
        h(Card, null,
          h(SectionTitle, null, 'Onboarding Sources'),
          h('div', { className: 'grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4' },
            SOURCES.map(source => {
              const enabled = !!(draft.onboardingSources && draft.onboardingSources[source] && draft.onboardingSources[source].enabled);
              const filter = (draft.onboardingSources && draft.onboardingSources[source] && draft.onboardingSources[source].filter) || '';

              return h('div', { key: source, className: 'kfh-card-inset p-4' },
                h('div', { className: 'flex items-center justify-between' },
                  h('div', { className: 'text-sm font-extrabold text-gray-900 flex items-center gap-2' },
                    h('i', { className: 'w-4 h-4 text-gray-500', 'data-lucide': 'database' }),
                    source
                  ),
                  h('label', { className: 'inline-flex items-center gap-2 cursor-pointer select-none' },
                    h('input', {
                      type: 'checkbox',
                      checked: enabled,
                      disabled: isView,
                      onChange: () => toggleOnboardingSource(source),
                      className: 'w-4 h-4 accent-[var(--kfh-primary)]'
                    }),
                    h('span', { className: 'text-xs font-extrabold text-gray-600' }, enabled ? 'Enabled' : 'Disabled')
                  )
                ),
                enabled ? h('div', { className: 'mt-3' },
                  h('div', { className: 'text-[10px] font-extrabold text-gray-500 uppercase tracking-wider mb-2' }, 'Filter (optional)'),
                  h(TextInput, { value: filter, onChange: v => setOnboardingFilter(source, v), placeholder: 'e.g. app=CRM AND severity>=High', disabled: isView })
                ) : null
              );
            })
          )
        ),

        // Thresholds
        h(Card, null,
          h(SectionTitle, null, 'Thresholds & SLO'),
          h('div', { className: 'grid grid-cols-1 md:grid-cols-3 gap-4' },
            h(Field, { label: 'MTTR Target (minutes)' }, h(TextInput, { type: 'number', value: String(draft.mttrTarget), onChange: v => setDraftField('mttrTarget', v), disabled: isView })),
            h(Field, { label: 'Critical Threshold' }, h(TextInput, { type: 'number', value: String(draft.criticalThreshold), onChange: v => setDraftField('criticalThreshold', v), disabled: isView })),
            h('div', { className: 'flex items-end' },
              h('label', { className: 'inline-flex items-center gap-3 cursor-pointer select-none' },
                h('input', {
                  type: 'checkbox',
                  checked: !!draft.noiseSuppressionEnabled,
                  disabled: isView,
                  onChange: e => setDraftField('noiseSuppressionEnabled', e.target.checked),
                  className: 'w-4 h-4 accent-[var(--kfh-primary)]'
                }),
                h('span', { className: 'text-sm font-extrabold text-gray-700' }, 'Noise Suppression')
              )
            )
          )
        ),

        // Notifications
        h(Card, null,
          h(SectionTitle, null, 'Notifications'),
          h('div', { className: 'grid grid-cols-1 md:grid-cols-2 gap-4' },
            h(Field, { label: 'Teams Webhook URL' }, h(TextInput, {
              value: draft.notifications.teamsWebhook || '',
              disabled: isView,
              onChange: v => setNotifications(Object.assign({}, draft.notifications, { teamsWebhook: v })),
              placeholder: 'https://teams.webhook.url/…'
            })),
            h(Field, { label: 'Email Distribution List' }, h(TextInput, {
              value: draft.notifications.emailDistro || '',
              disabled: isView,
              onChange: v => setNotifications(Object.assign({}, draft.notifications, { emailDistro: v })),
              placeholder: 'team@kfh.com'
            })),
            h('div', { className: 'md:col-span-2 flex items-center gap-3 pt-2' },
              h('label', { className: 'inline-flex items-center gap-3 cursor-pointer select-none' },
                h('input', {
                  type: 'checkbox',
                  checked: !!draft.notifications.postOnCritical,
                  disabled: isView,
                  onChange: e => setNotifications(Object.assign({}, draft.notifications, { postOnCritical: e.target.checked })),
                  className: 'w-4 h-4 accent-[var(--kfh-primary)]'
                }),
                h('span', { className: 'text-sm font-extrabold text-gray-700' }, 'Post on Critical incidents')
              )
            )
          )
        )
      );
    }

    // Content routing
    function renderActiveTab() {
      if (activeTab === 'overview') return h(OverviewTab);
      if (activeTab === 'incidents') return h(IncidentsTab);
      if (activeTab === 'alerts') return h(AlertsTab);
      if (activeTab === 'topology') return h(TopologyTab);
      return h(ConfigurationTab);
    }

    return h('div', { className: 'space-y-6 animate-fade-in' },
      h(ToastView),
      h(TabBar),
      renderActiveTab()
    );
  }

  // ---------- Boot ----------
  function boot() {
    const mount = document.getElementById('root');
    if (!mount) return;

    const root = ReactDOM.createRoot ? ReactDOM.createRoot(mount) : null;
    if (root) root.render(h(ApplicationConfigPage));
    else ReactDOM.render(h(ApplicationConfigPage), mount);

    if (window.lucide && typeof window.lucide.createIcons === 'function') {
      window.lucide.createIcons();
    }
  }

  boot();
})();
