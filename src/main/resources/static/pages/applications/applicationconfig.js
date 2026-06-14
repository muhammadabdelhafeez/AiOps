/* global React, ReactDOM */
(function () {
  'use strict';
  const h = React.createElement;
  const SOURCES = ['SCOM', 'vROps', 'BMC', 'SolarWinds', 'Elastic'];
  const DOMAINS = ['Core Banking', 'Digital Channels', 'Treasury', 'Risk Management', 'HR Systems', 'Infrastructure'];
  const ENVIRONMENTS = ['Production', 'UAT', 'Development', 'DR'];
  const CRITICALITIES = ['Tier1', 'Tier2', 'Tier3', 'Tier4'];
  function params() {
    const url = new URL(window.location.href);
    return { id: url.searchParams.get('id') || 'new', mode: url.searchParams.get('mode') || (url.searchParams.get('id') ? 'edit' : 'create') };
  }
  function setUrlState(id, mode) {
    const url = new URL(window.location.href);
    if (id) url.searchParams.set('id', id);
    if (mode) url.searchParams.set('mode', mode);
    window.history.replaceState({}, '', url.toString());
  }
  function generateId() {
    return window.crypto && window.crypto.randomUUID ? window.crypto.randomUUID() : String(Date.now());
  }
  function esc(value) {
    return String(value || '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }
  function pageContent(response) {
    return response && Array.isArray(response.content) ? response.content : Array.isArray(response) ? response : [];
  }
  function emptyApplication(id) {
    return {
      id: id || 'new',
      name: '',
      shortCode: '',
      domain: DOMAINS[0],
      environment: ENVIRONMENTS[0],
      criticality: CRITICALITIES[2],
      businessOwner: '',
      techOwnerTeam: '',
      escalationGroup: '',
      sources: [],
      healthScore: 0,
      healthStatus: 'Unknown',
      metrics: { open: 0, criticalOpen: 0, newLast15d: 0, recurringLast15d: 0, mtta: 0, mttr: 0, noisyGroups: 0 },
      config: {
        onboardingSources: Object.fromEntries(SOURCES.map(source => [source, { enabled: false, filter: '' }])),
        mttrTarget: 60,
        criticalThreshold: 5,
        noiseSuppressionEnabled: false,
        notifications: { teamsWebhook: '', emailDistro: '', postOnCritical: true }
      }
    };
  }
  function normalizeApplication(row, fallbackId) {
    const base = emptyApplication(fallbackId || row.id || row.applicationId || 'new');
    const healthScore = Number(row.healthScore || 0);
    return Object.assign(base, {
      id: row.id || row.applicationId || base.id,
      name: row.name || row.applicationName || base.name,
      shortCode: row.shortCode || row.code || base.shortCode,
      domain: row.businessDomain || row.domain || base.domain,
      environment: row.environment || base.environment,
      criticality: row.criticality || base.criticality,
      businessOwner: row.businessOwner || '',
      techOwnerTeam: row.techOwnerTeam || row.ownerTeam || '',
      escalationGroup: row.escalationGroup || '',
      sources: Array.isArray(row.sources) ? row.sources : Array.isArray(row.onboardedSources) ? row.onboardedSources : [],
      healthScore,
      healthStatus: healthScore >= 80 ? 'Good' : healthScore >= 50 ? 'Degraded' : healthScore > 0 ? 'Critical' : 'Unknown',
      metrics: row.metrics || base.metrics,
      config: Object.assign({}, base.config, row.config || {})
    });
  }
  function normalizeIncident(row) {
    return {
      id: row.id || row.incidentNumber || '',
      title: row.title || row.summary || '',
      severity: row.severity || '',
      status: row.status || '',
      createdAt: row.createdAt || row.openedAt || '',
      rootCauseSummary: row.rootCauseSummary || ''
    };
  }
  function normalizeAlert(row) {
    return {
      id: row.id || row.alertId || '',
      title: row.title || row.message || '',
      severity: row.severity || '',
      status: row.status || '',
      source: row.sourceSystem || '',
      createdAt: row.createdAt || row.timestamp || ''
    };
  }
  function normalizeInventory(row) {
    return {
      id: row.id || row.resourceId || '',
      type: row.type || row.resourceType || '',
      name: row.name || row.resourceName || '',
      status: row.status || 'Unknown',
      identifier: row.identifier || row.hostname || row.ipAddress || '',
      lastChecked: row.lastChecked || row.updatedAt || ''
    };
  }
  function Card(props) { return h('div', { className: 'glass-card rounded-2xl p-6 ' + (props.className || '') }, props.children); }
  function SectionTitle(props) { return h('div', { className: 'text-sm font-extrabold text-[var(--text-primary)] tracking-tight mb-4' }, props.children); }
  function Badge(props) { return h('span', { className: 'inline-flex items-center px-2 py-0.5 rounded-md border text-[10px] font-bold uppercase tracking-wider ' + (props.className || '') }, props.children); }
  function Kpi(props) { return Card({ children: [h('div', { className: 'text-xs text-gray-500 font-bold uppercase' }, props.label), h('div', { className: 'mt-2 text-3xl font-black ' + (props.valueClass || '') }, props.value), h('div', { className: 'mt-1 text-xs text-gray-500' }, props.footer || '')] }); }
  function TextInput({ value, onChange, placeholder, type }) {
    return h('input', { className: 'kfh-focus w-full px-3 py-2 rounded-xl bg-white border border-gray-200 text-sm', value: value || '', type: type || 'text', placeholder, onChange: e => onChange(e.target.value) });
  }
  function SelectInput({ value, onChange, options }) {
    return h('select', { className: 'kfh-focus w-full px-3 py-2 rounded-xl bg-white border border-gray-200 text-sm', value: value || '', onChange: e => onChange(e.target.value) }, options.map(option => h('option', { key: option, value: option }, option)));
  }
  function Field({ label, children }) { return h('div', null, h('label', { className: 'block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2' }, label), children); }
  function EmptyState({ label }) { return h('div', { className: 'text-sm text-gray-500 border border-dashed border-gray-200 rounded-xl p-8 text-center' }, label); }
  function ApplicationConfigPage() {
    const initial = React.useMemo(params, []);
    const isCreate = initial.id === 'new' || initial.mode === 'create';
    const isView = initial.mode === 'view';
    const [app, setApp] = React.useState(() => emptyApplication(isCreate ? 'new' : initial.id));
    const [draft, setDraft] = React.useState(() => emptyApplication(isCreate ? 'new' : initial.id));
    const [incidents, setIncidents] = React.useState([]);
    const [alerts, setAlerts] = React.useState([]);
    const [inventory, setInventory] = React.useState([]);
    const [activeTab, setActiveTab] = React.useState('overview');
    const [dirty, setDirty] = React.useState(false);
    const [toast, setToast] = React.useState(null);
    function setDraftField(key, value) { setDraft(prev => Object.assign({}, prev, { [key]: value })); setDirty(true); }
    function setConfigField(key, value) { setDraft(prev => Object.assign({}, prev, { config: Object.assign({}, prev.config, { [key]: value }) })); setDirty(true); }
    function setNotificationField(key, value) { setDraft(prev => Object.assign({}, prev, { config: Object.assign({}, prev.config, { notifications: Object.assign({}, prev.config.notifications, { [key]: value }) }) })); setDirty(true); }
    function notify(type, msg) { setToast({ type, msg }); window.setTimeout(() => setToast(null), 2500); }
    function syncDraft(nextApp) {
      setDraft(JSON.parse(JSON.stringify(nextApp)));
    }
    React.useEffect(() => {
      const title = document.getElementById('pageTitle');
      const subtitle = document.getElementById('pageSubtitle');
      const btnReset = document.getElementById('btnReset');
      const btnSave = document.getElementById('btnSave');
      if (title) title.textContent = isCreate ? 'Create Application' : (app.name || 'Application Configuration');
      if (subtitle) subtitle.textContent = isCreate ? 'Define identity, sources, thresholds & notifications' : `${app.shortCode || '-'} | ${app.domain || '-'} | ${app.environment || '-'}`;
      if (btnReset) { btnReset.style.display = isView ? 'none' : 'inline-flex'; btnReset.onclick = () => { syncDraft(app); setDirty(false); notify('info', 'Reset changes'); }; }
      if (btnSave) { btnSave.style.display = isView ? 'none' : 'inline-flex'; btnSave.textContent = isCreate ? 'Create' : 'Save'; btnSave.onclick = () => save(); }
    }, [app, draft, dirty, isCreate, isView]);
    React.useEffect(() => {
      let cancelled = false;
      async function load() {
        if (isCreate || !window.APIClient) return;
        try {
          const [appResponse, incidentResponse, alertResponse, inventoryResponse] = await Promise.all([
            APIClient.applications && APIClient.applications.get ? APIClient.applications.get(initial.id) : Promise.resolve(null),
            APIClient.incidents ? APIClient.incidents.list({ applicationId: initial.id, page: 0, size: 50 }) : Promise.resolve([]),
            APIClient.alerts ? APIClient.alerts.list({ applicationId: initial.id, page: 0, size: 50 }) : Promise.resolve([]),
            APIClient.inventory ? APIClient.inventory.list({ applicationId: initial.id, page: 0, size: 100 }) : Promise.resolve([])
          ]);
          if (cancelled) return;
          const nextApp = normalizeApplication(appResponse || {}, initial.id);
          setApp(nextApp);
          syncDraft(nextApp);
          setIncidents(pageContent(incidentResponse).map(normalizeIncident));
          setAlerts(pageContent(alertResponse).map(normalizeAlert));
          setInventory(pageContent(inventoryResponse).map(normalizeInventory));
        } catch (error) {
          console.warn('[ApplicationConfig] Unable to load production data; rendering empty state.', error);
        }
      }
      load();
      return () => { cancelled = true; };
    }, [initial.id, isCreate]);
    async function save() {
      if (isView) return;
      if (!draft.name || !draft.shortCode) { notify('error', 'Name and short code are required'); return; }
      if (!window.APIClient || !APIClient.applications) { notify('error', 'Applications API is unavailable'); return; }
      const payload = Object.assign({}, draft, { shortCode: String(draft.shortCode).toUpperCase().slice(0, 8) });
      try {
        const response = isCreate
          ? await APIClient.applications.create({ name: payload.name, attributes: payload })
          : await APIClient.applications.update(app.id, { name: payload.name, attributes: payload });
        const saved = normalizeApplication(response || payload, app.id || generateId());
        setApp(saved);
        syncDraft(saved);
        setDirty(false);
        if (isCreate) setUrlState(saved.id, 'edit');
        notify('success', isCreate ? 'Application created' : 'Configuration saved');
      } catch (error) {
        console.warn('[ApplicationConfig] Unable to save application.', error);
        notify('error', 'Unable to save application');
      }
    }
    function renderOverview() {
      return h('div', { className: 'space-y-6' },
        h('div', { className: 'grid grid-cols-1 md:grid-cols-4 gap-4' },
          h(Kpi, { label: 'Health Score', value: String(app.healthScore), valueClass: 'text-[var(--kfh-primary-darker)]', footer: 'Composite service health score' }),
          h(Kpi, { label: 'Open Incidents', value: String(app.metrics.open || incidents.length), valueClass: 'text-red-700', footer: 'Tenant-scoped production incidents' }),
          h(Kpi, { label: 'Critical Open', value: String(app.metrics.criticalOpen || incidents.filter(i => i.severity === 'Critical').length), valueClass: 'text-red-700', footer: 'Critical active incidents' }),
          h(Kpi, { label: 'Inventory', value: String(inventory.length), valueClass: 'text-blue-700', footer: 'Mapped resources' })
        ),
        h(Card, null, h(SectionTitle, null, 'Application Identity'), h('dl', { className: 'grid grid-cols-1 md:grid-cols-3 gap-4 text-sm' },
          [['Name', app.name || '-'], ['Short Code', app.shortCode || '-'], ['Domain', app.domain || '-'], ['Environment', app.environment || '-'], ['Criticality', app.criticality || '-'], ['Owner Team', app.techOwnerTeam || '-']].map(([label, value]) => h('div', { key: label }, h('dt', { className: 'text-gray-500 font-bold' }, label), h('dd', { className: 'font-semibold text-gray-900' }, value)))
        ))
      );
    }
    function renderRows(rows, columns, emptyLabel) {
      if (!rows.length) return h(EmptyState, { label: emptyLabel });
      return h('div', { className: 'overflow-x-auto' }, h('table', { className: 'min-w-full text-sm' },
        h('thead', null, h('tr', null, columns.map(col => h('th', { key: col.key, className: 'text-left py-2 px-3 text-xs uppercase text-gray-500' }, col.label)))),
        h('tbody', null, rows.map(row => h('tr', { key: row.id, className: 'border-t border-gray-100' }, columns.map(col => h('td', { key: col.key, className: 'py-2 px-3' }, row[col.key] || '-')))))
      ));
    }
    function renderConfig() {
      return h('div', { className: 'grid grid-cols-1 lg:grid-cols-2 gap-6' },
        h(Card, null, h(SectionTitle, null, 'Identity'),
          h('div', { className: 'space-y-4' },
            h(Field, { label: 'Name' }, h(TextInput, { value: draft.name, onChange: value => setDraftField('name', value) })),
            h(Field, { label: 'Short Code' }, h(TextInput, { value: draft.shortCode, onChange: value => setDraftField('shortCode', value) })),
            h(Field, { label: 'Domain' }, h(SelectInput, { value: draft.domain, onChange: value => setDraftField('domain', value), options: DOMAINS })),
            h(Field, { label: 'Environment' }, h(SelectInput, { value: draft.environment, onChange: value => setDraftField('environment', value), options: ENVIRONMENTS })),
            h(Field, { label: 'Criticality' }, h(SelectInput, { value: draft.criticality, onChange: value => setDraftField('criticality', value), options: CRITICALITIES }))
          )),
        h(Card, null, h(SectionTitle, null, 'Thresholds & Notifications'),
          h('div', { className: 'space-y-4' },
            h(Field, { label: 'MTTR Target (minutes)' }, h(TextInput, { type: 'number', value: draft.config.mttrTarget, onChange: value => setConfigField('mttrTarget', Number(value)) })),
            h(Field, { label: 'Critical Threshold' }, h(TextInput, { type: 'number', value: draft.config.criticalThreshold, onChange: value => setConfigField('criticalThreshold', Number(value)) })),
            h(Field, { label: 'Email Distribution' }, h(TextInput, { value: draft.config.notifications.emailDistro, onChange: value => setNotificationField('emailDistro', value) })),
            h(Field, { label: 'Teams Webhook Reference' }, h(TextInput, { value: draft.config.notifications.teamsWebhook, onChange: value => setNotificationField('teamsWebhook', value), placeholder: 'Store secret/reference only; do not paste raw secrets in shared output' }))
          ))
      );
    }
    const tabButton = (key, label) => h('button', { key, className: 'px-4 py-2 rounded-xl text-sm font-bold ' + (activeTab === key ? 'bg-[var(--kfh-primary)] text-white' : 'bg-white border border-gray-200'), onClick: () => setActiveTab(key) }, label);
    return h('div', { className: 'space-y-6' },
      toast ? h('div', { className: 'fixed top-6 right-6 z-50 px-4 py-3 rounded-xl border shadow-sm bg-white text-sm font-bold' }, toast.msg) : null,
      h(Card, { className: 'flex flex-wrap gap-2' }, ['overview', 'incidents', 'alerts', 'inventory', 'config'].map(key => tabButton(key, key[0].toUpperCase() + key.slice(1)))),
      activeTab === 'overview' ? renderOverview() : null,
      activeTab === 'incidents' ? h(Card, null, h(SectionTitle, null, 'Latest operational incidents for this application'), renderRows(incidents, [{ key: 'id', label: 'ID' }, { key: 'title', label: 'Title' }, { key: 'severity', label: 'Severity' }, { key: 'status', label: 'Status' }], 'No production incidents were returned for this application.')) : null,
      activeTab === 'alerts' ? h(Card, null, h(SectionTitle, null, 'Raw alerts before incident correlation'), renderRows(alerts, [{ key: 'id', label: 'ID' }, { key: 'title', label: 'Title' }, { key: 'severity', label: 'Severity' }, { key: 'status', label: 'Status' }, { key: 'source', label: 'Source' }], 'No production alerts were returned for this application.')) : null,
      activeTab === 'inventory' ? h(Card, null, h(SectionTitle, null, 'Mapped resources'), renderRows(inventory, [{ key: 'id', label: 'ID' }, { key: 'type', label: 'Type' }, { key: 'name', label: 'Name' }, { key: 'status', label: 'Status' }, { key: 'identifier', label: 'Identifier' }], 'No production inventory was returned for this application.')) : null,
      activeTab === 'config' ? renderConfig() : null,
      dirty && !isView ? h('div', { className: 'fixed bottom-6 right-6 bg-amber-50 border border-amber-200 text-amber-800 rounded-xl px-4 py-3 text-sm font-bold' }, 'Unsaved changes') : null
    );
  }
  const root = document.getElementById('root') || document.getElementById('application-config-root') || document.getElementById('page-root');
  if (root) ReactDOM.render(h(ApplicationConfigPage), root);
})();