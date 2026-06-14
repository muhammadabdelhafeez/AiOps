/* global React, ReactDOM, lucide */
(function () {
  'use strict';

  var h = React.createElement;

  // Global drawer control - set by App component, called by button
  var openDrawerFn = null;

  // Bind button immediately when script loads
  document.addEventListener('DOMContentLoaded', function() {
    var btn = document.getElementById('btnNew');
    if (btn) {
      btn.onclick = function(e) {
        e.preventDefault();
        if (openDrawerFn) {
          openDrawerFn();
        }
      };
    }
  });

  // ---------- Utilities ----------
  function generateId(prefix) {
    return (window.crypto && window.crypto.randomUUID ? window.crypto.randomUUID() : (prefix || 'id') + '_' + Date.now());
  }

  function nowIso() {
    return new Date().toISOString();
  }

  function safeText(v) {
    return (v == null) ? '' : String(v);
  }

  function clampInt(n, min, max) {
    var x = Number.isFinite(Number(n)) ? parseInt(String(n), 10) : min;
    return Math.max(min, Math.min(max, isNaN(x) ? min : x));
  }

  function pageContent(response) {
    return response && Array.isArray(response.content) ? response.content : Array.isArray(response) ? response : [];
  }

  function normalizeApplication(row) {
    var name = row.name || row.applicationName || 'Untitled application';
    var shortCode = row.shortCode || row.code || name.split(/\s+/).map(function(p) { return p[0]; }).join('').slice(0, 6).toUpperCase();
    return {
      id: row.id || row.applicationId || generateId('app'),
      name: name,
      shortCode: shortCode,
      domain: row.businessDomain || row.domain || '',
      environment: row.environment || 'PROD',
      criticality: row.criticality || 'Tier3',
      tags: Array.isArray(row.tags) ? row.tags : [],
      businessOwner: row.businessOwner || '',
      techOwnerTeam: row.techOwnerTeam || row.ownerTeam || '',
      onboardedSources: Array.isArray(row.onboardedSources) ? row.onboardedSources : [],
      healthScore: Number(row.healthScore || 0),
      incidentStats: row.incidentStats || { openTotal: 0, openCritical: 0, new15d: 0, recurring15d: 0 },
      inventoryCount: Number(row.inventoryCount || 0),
      inventoryCounts: row.inventoryCounts || { servers: 0, databases: 0, k8s: 0, urls: 0 },
      createdAt: row.createdAt || nowIso(),
      updatedAt: row.updatedAt || nowIso(),
      config: row.config || { onboardingSources: {} }
    };
  }

  // ---------- UI option catalogs ----------
  var DOMAINS = ['Core Banking', 'Digital Channels', 'Treasury', 'Risk Management', 'HR Systems', 'Infrastructure'];
  var ENVIRONMENTS = ['Production', 'UAT', 'Development', 'DR'];
  var CRITICALITIES = ['Tier1', 'Tier2', 'Tier3', 'Tier4'];
  var SOURCES = ['SCOM', 'vROps', 'BMC', 'SolarWinds', 'Elastic'];

  function emptyApplications() {
    return [];
  }

  // ---------- Design helpers ----------
  function healthLabel(score) {
    if (score >= 80) return { label: 'Good', badge: 'bg-emerald-100 text-emerald-700 border-emerald-200', bar: 'bg-emerald-500' };
    if (score >= 50) return { label: 'Degraded', badge: 'bg-amber-100 text-amber-700 border-amber-200', bar: 'bg-amber-500' };
    return { label: 'Critical', badge: 'bg-red-100 text-red-700 border-red-200', bar: 'bg-red-500' };
  }

  function criticalityBadge(c) {
    if (c === 'Tier1') return 'bg-red-50 text-red-700 border-red-200';
    if (c === 'Tier2') return 'bg-amber-50 text-amber-700 border-amber-200';
    if (c === 'Tier3') return 'bg-blue-50 text-blue-700 border-blue-200';
    return 'bg-gray-50 text-gray-700 border-gray-200';
  }

  // ---------- Components ----------
  function Pill(props) {
    return h('span', { className: 'inline-flex items-center px-2 py-0.5 rounded-md border text-[10px] font-bold uppercase tracking-wider ' + (props.className || '') }, props.children);
  }

  function Card(props) {
    return h('div', { className: 'kfh-card ' + (props.className || '') }, props.children);
  }

  function Drawer(props) {
    if (!props.open) return null;

    return h(React.Fragment, null,
      h('div', {
        className: 'kfh-drawer-overlay fixed inset-0 z-50 bg-black/40'
        // Removed click handler - only close via Close button
      }),
      h('div', {
        className: 'kfh-drawer fixed top-0 right-0 h-full w-full max-w-2xl z-50 bg-white shadow-2xl border-l border-black/5 overflow-hidden flex flex-col'
      },
        h('div', { className: 'h-16 px-6 flex items-center justify-between border-b border-black/5 bg-white' },
          h('div', null,
            h('div', { className: 'text-lg font-bold text-[var(--text-primary)]' }, props.title),
            props.subtitle ? h('div', { className: 'text-xs text-[var(--text-secondary)] -mt-0.5' }, props.subtitle) : null
          ),
          h('button', { className: 'kfh-btn kfh-btn-ghost', onClick: props.onClose, type: 'button' }, 'Close')
        ),
        h('div', { className: 'flex-1 overflow-y-auto p-6 bg-[var(--surface-bg)]' }, props.children),
        props.footer
      )
    );
  }

  function Field(props) {
    return h('div', { className: props.className || '' },
      props.label ? h('label', { className: 'block text-xs font-bold text-gray-600 uppercase tracking-wider mb-2' }, props.label) : null,
      props.children
    );
  }

  function TextInput(props) {
    return h('input', {
      type: props.type || 'text',
      value: props.value,
      onChange: function(e) { props.onChange(e.target.value); },
      placeholder: props.placeholder,
      className: 'w-full px-3 py-2 rounded-xl bg-white border border-gray-200 text-sm text-[var(--text-primary)] placeholder-gray-400 focus:outline-none focus:border-[var(--kfh-primary)] focus:ring-2 focus:ring-[var(--kfh-primary)]/20 transition-all'
    });
  }

  function Select(props) {
    return h('select', {
      value: props.value,
      onChange: function(e) { props.onChange(e.target.value); },
      className: 'w-full px-3 py-2 rounded-xl bg-white border border-gray-200 text-sm text-[var(--text-primary)] focus:outline-none focus:border-[var(--kfh-primary)] focus:ring-2 focus:ring-[var(--kfh-primary)]/20 transition-all'
    },
      props.options.map(function(o) { return h('option', { key: o, value: o }, o); })
    );
  }

  function SourceChip(props) {
    return h('span', { className: 'inline-flex items-center px-2 py-1 rounded-lg bg-[var(--kfh-primary-light)] text-[var(--kfh-primary-dark)] text-xs font-semibold' }, props.source);
  }

  function Tabs(props) {
    return h('div', { className: 'flex gap-2 p-1 bg-white rounded-2xl border border-black/5 shadow-sm' },
      props.tabs.map(function(t) { return h('button', {
        key: t.id,
        type: 'button',
        onClick: function() { props.onChange(t.id); },
        className: 'px-4 py-2 rounded-xl text-sm font-extrabold transition-all ' +
          (props.active === t.id
            ? 'bg-[var(--kfh-primary)] text-white shadow'
            : 'bg-transparent text-gray-700 hover:bg-gray-50')
      }, t.label); })
    );
  }

  // Compact modern AppCard
  function AppCard(props) {
    var app = props.app;
    var health = healthLabel(app.healthScore);

    return h('div', {
      className: 'kfh-card kfh-card-interactive p-4 cursor-pointer',
      onClick: function() { props.onOpen(app); }
    },
      // Header: Name, Code, Tier
      h('div', { className: 'flex items-center justify-between gap-2 mb-3' },
        h('div', { className: 'flex items-center gap-2 min-w-0 flex-1' },
          h('div', { className: 'text-sm font-bold text-[var(--text-primary)] truncate' }, app.name),
          h('span', { className: 'px-1.5 py-0.5 bg-gray-100 text-gray-600 text-[10px] font-mono rounded' }, app.shortCode)
        ),
        h(Pill, { className: criticalityBadge(app.criticality) }, app.criticality)
      ),

      // Domain & Environment
      h('div', { className: 'flex items-center gap-1.5 text-xs text-gray-500 mb-3' },
        h('span', null, app.domain),
        h('span', null, '•'),
        h('span', null, app.environment)
      ),

      // Health Score + Status Row
      h('div', { className: 'flex items-center gap-3 mb-3' },
        h('div', { className: 'flex items-center gap-2' },
          h('div', {
            className: 'w-10 h-10 rounded-xl flex items-center justify-center text-lg font-bold',
            style: { backgroundColor: 'var(--kfh-primary-light)', color: 'var(--kfh-primary-dark)' }
          }, String(app.healthScore)),
          h('div', null,
            h('div', { className: 'text-[10px] text-gray-400 uppercase font-semibold' }, 'Health'),
            h('span', { className: 'px-1.5 py-0.5 rounded text-[10px] font-semibold ' + health.badge }, health.label)
          )
        ),
        h('div', { className: 'flex-1 h-1.5 bg-gray-100 rounded-full overflow-hidden' },
          h('div', { className: 'h-full rounded-full ' + health.bar, style: { width: app.healthScore + '%' } })
        )
      ),

      // Incident Stats - Compact inline
      h('div', { className: 'flex items-center gap-2 text-xs mb-3 py-2 px-2 bg-gray-50 rounded-lg' },
        h('span', { className: 'text-gray-500' }, 'Incidents:'),
        h('span', { className: 'font-bold text-gray-700' }, app.incidentStats.openTotal + ' Open'),
        h('span', { className: 'text-gray-300' }, '|'),
        h('span', { className: 'font-bold text-red-600' }, app.incidentStats.openCritical + ' Critical'),
        h('span', { className: 'text-gray-300' }, '|'),
        h('span', { className: 'font-bold text-amber-600' }, app.incidentStats.recurring15d + ' Recurring')
      ),

      // Sources - Compact chips
      h('div', { className: 'flex items-center gap-1.5 flex-wrap' },
        app.onboardedSources.map(function(s) {
          return h('span', {
            key: s,
            className: 'px-2 py-0.5 bg-[var(--kfh-primary-light)] text-[var(--kfh-primary-dark)] text-[10px] font-semibold rounded'
          }, s);
        }),
        h('span', { className: 'ml-auto text-[10px] text-gray-400' }, (app.inventoryCount || 24) + ' assets')
      ),

      // Action buttons
      h('div', { className: 'flex gap-2 mt-3 pt-3 border-t border-gray-100' },
        h('button', {
          className: 'flex-1 px-3 py-1.5 text-xs font-semibold text-[var(--kfh-primary)] bg-[var(--kfh-primary-light)] rounded-lg hover:bg-[var(--kfh-primary)] hover:text-white transition-all',
          type: 'button',
          onClick: function(e) { e.stopPropagation(); props.onOpen(app); }
        }, 'View Details'),
        h('button', {
          className: 'px-3 py-1.5 text-xs font-semibold text-gray-600 bg-gray-100 rounded-lg hover:bg-gray-200 transition-all',
          type: 'button',
          onClick: function(e) { e.stopPropagation(); props.onEdit(app); }
        }, 'Edit')
      )
    );
  }

  // KPI Summary Card Component
  function KPICard(props) {
    return h('div', { className: 'bg-white rounded-xl border border-gray-100 p-4 flex items-center gap-3' },
      h('div', {
        className: 'w-10 h-10 rounded-lg flex items-center justify-center',
        style: { backgroundColor: props.bgColor || 'var(--kfh-primary-light)', color: props.color || 'var(--kfh-primary-dark)' }
      },
        h('span', { className: 'text-lg' }, props.icon)
      ),
      h('div', null,
        h('div', { className: 'text-xl font-bold', style: { color: props.valueColor || 'var(--text-primary)' } }, props.value),
        h('div', { className: 'text-xs text-gray-500' }, props.label)
      )
    );
  }

  function metric(label, value, valueClass) {
    return h('div', { className: 'bg-white rounded-2xl border border-black/5 p-3 shadow-sm' },
      h('div', { className: 'text-[10px] font-bold text-gray-500 uppercase tracking-wider' }, label),
      h('div', { className: 'text-2xl font-extrabold mt-1 ' + (valueClass || '') }, value)
    );
  }

  function AppCreateDrawer(props) {
    // Guard clause first
    if (!props.open) return null;

    // All state hooks must come after guard clause workaround using a wrapper
    return h(WizardModal, { onClose: props.onClose, onSave: props.onSave });
  }

  function WizardModal(props) {
    var [step, setStep] = React.useState(1);
    var [formData, setFormData] = React.useState({
      name: '',
      shortCode: '',
      domain: DOMAINS[0],
      environment: ENVIRONMENTS[0],
      criticality: CRITICALITIES[0],
      businessOwner: '',
      techOwnerTeam: 'Platform Engineering',
      escalationGroup: '',
      mttrTarget: 60,
      criticalThreshold: 5,
      noiseSuppressionEnabled: true,
      onboardedSources: ['SCOM', 'vROps', 'BMC'],
      teamsWebhook: '',
      emailDistro: '',
      postOnCritical: true
    });
    var [inventoryItems, setInventoryItems] = React.useState([]);
    var [newItem, setNewItem] = React.useState({ type: 'Server', name: '', identifier: '', status: 'Healthy' });

    function updateField(field, value) {
      setFormData(function(prev) {
        var next = Object.assign({}, prev);
        next[field] = value;
        return next;
      });
    }

    function toggleSource(src) {
      setFormData(function(prev) {
        var sources = prev.onboardedSources.slice();
        var idx = sources.indexOf(src);
        if (idx >= 0) sources.splice(idx, 1);
        else sources.push(src);
        return Object.assign({}, prev, { onboardedSources: sources });
      });
    }

    function addItem() {
      if (!newItem.name.trim() || !newItem.identifier.trim()) return;
      setInventoryItems(function(prev) {
        return prev.concat([Object.assign({ id: 'inv_' + Date.now() }, newItem)]);
      });
      setNewItem({ type: 'Server', name: '', identifier: '', status: 'Healthy' });
    }

    function removeItem(id) {
      setInventoryItems(function(prev) { return prev.filter(function(i) { return i.id !== id; }); });
    }

    // Validation for each step
    var step1Valid = formData.name.trim().length >= 2 && formData.shortCode.trim().length >= 2;
    var step2Valid = true; // Inventory is optional
    var step3Valid = true; // Settings are optional
    var canCreate = step1Valid;

    function handleCreate() {
      if (!canCreate) return;
      var counts = { servers: 0, databases: 0, k8s: 0, urls: 0 };
      inventoryItems.forEach(function(i) {
        if (i.type === 'Server') counts.servers++;
        else if (i.type === 'Database') counts.databases++;
        else if (i.type === 'K8s') counts.k8s++;
        else if (i.type === 'URL') counts.urls++;
      });
      props.onSave({
        draft: formData,
        inventory: inventoryItems,
        invSummary: { total: inventoryItems.length, counts: counts }
      });
    }

    function nextStep() {
      if (step < 3) setStep(step + 1);
    }

    function prevStep() {
      if (step > 1) setStep(step - 1);
    }

    // Step indicator component
    function StepIndicator() {
      var steps = [
        { num: 1, label: 'Configuration' },
        { num: 2, label: 'Inventory' },
        { num: 3, label: 'Settings & Review' }
      ];

      return h('div', { style: { display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '8px', padding: '24px 0' } },
        steps.map(function(s, idx) {
          var isActive = step === s.num;
          var isCompleted = step > s.num;
          var circleStyle = {
            width: '40px',
            height: '40px',
            borderRadius: '50%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '16px',
            fontWeight: 'bold',
            transition: 'all 0.3s ease',
            backgroundColor: isActive ? '#128754' : isCompleted ? '#10b981' : '#e5e7eb',
            color: isActive || isCompleted ? 'white' : '#9ca3af',
            border: isActive ? '3px solid #0d6e45' : 'none',
            boxShadow: isActive ? '0 4px 12px rgba(18, 135, 84, 0.3)' : 'none'
          };
          var labelStyle = {
            fontSize: '12px',
            fontWeight: '600',
            marginTop: '8px',
            color: isActive ? '#128754' : isCompleted ? '#10b981' : '#9ca3af',
            textAlign: 'center'
          };
          var lineStyle = {
            width: '60px',
            height: '3px',
            backgroundColor: isCompleted ? '#10b981' : '#e5e7eb',
            borderRadius: '2px'
          };

          return h(React.Fragment, { key: s.num },
            h('div', { style: { display: 'flex', flexDirection: 'column', alignItems: 'center' } },
              h('div', { style: circleStyle }, isCompleted ? '✓' : String(s.num)),
              h('div', { style: labelStyle }, s.label)
            ),
            idx < steps.length - 1 && h('div', { style: Object.assign({}, lineStyle, { marginBottom: '28px' }) })
          );
        })
      );
    }

    // Input field helper
    function InputField(label, value, onChange, placeholder, type, required) {
      return h('div', { style: { marginBottom: '20px' } },
        h('label', { style: { display: 'block', fontSize: '13px', fontWeight: '600', color: '#374151', marginBottom: '8px' } },
          label,
          required && h('span', { style: { color: '#ef4444', marginLeft: '4px' } }, '*')
        ),
        h('input', {
          type: type || 'text',
          value: value,
          onChange: function(e) { onChange(e.target.value); },
          placeholder: placeholder,
          style: {
            width: '100%',
            padding: '12px 16px',
            borderRadius: '10px',
            border: '2px solid #e5e7eb',
            fontSize: '14px',
            transition: 'border-color 0.2s ease',
            outline: 'none'
          }
        })
      );
    }

    // Select field helper
    function SelectField(label, value, options, onChange, required) {
      return h('div', { style: { marginBottom: '20px' } },
        h('label', { style: { display: 'block', fontSize: '13px', fontWeight: '600', color: '#374151', marginBottom: '8px' } },
          label,
          required && h('span', { style: { color: '#ef4444', marginLeft: '4px' } }, '*')
        ),
        h('select', {
          value: value,
          onChange: function(e) { onChange(e.target.value); },
          style: {
            width: '100%',
            padding: '12px 16px',
            borderRadius: '10px',
            border: '2px solid #e5e7eb',
            fontSize: '14px',
            backgroundColor: 'white',
            cursor: 'pointer'
          }
        },
          options.map(function(o) { return h('option', { key: o, value: o }, o); })
        )
      );
    }

    // Main modal overlay
    return h('div', {
      style: {
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.6)',
        zIndex: 9999,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '20px'
      }
    },
      h('div', {
        style: {
          backgroundColor: 'white',
          borderRadius: '20px',
          width: '100%',
          maxWidth: '800px',
          maxHeight: '90vh',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          overflow: 'hidden'
        }
      },
        // Header
        h('div', {
          style: {
            padding: '24px 32px',
            borderBottom: '1px solid #e5e7eb',
            background: 'linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%)'
          }
        },
          h('div', { style: { display: 'flex', justifyContent: 'space-between', alignItems: 'center' } },
            h('div', null,
              h('h2', { style: { fontSize: '24px', fontWeight: 'bold', color: '#1d1d1d', margin: 0 } }, 'Create New Application'),
              h('p', { style: { fontSize: '14px', color: '#6b7280', margin: '4px 0 0 0' } }, 'Follow the steps to configure your application')
            ),
            h('button', {
              type: 'button',
              onClick: props.onClose,
              style: {
                width: '40px',
                height: '40px',
                borderRadius: '10px',
                border: 'none',
                backgroundColor: '#f3f4f6',
                cursor: 'pointer',
                fontSize: '20px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#6b7280'
              }
            }, '×')
          )
        ),

        // Step Indicator
        h('div', { style: { backgroundColor: '#fafafa', borderBottom: '1px solid #e5e7eb' } },
          h(StepIndicator)
        ),

        // Content Area
        h('div', {
          style: {
            flex: 1,
            overflowY: 'auto',
            padding: '32px',
            backgroundColor: '#fafafa'
          }
        },
          // Step 1: Configuration
          step === 1 && h('div', null,
            h('div', { style: { marginBottom: '32px' } },
              h('h3', { style: { fontSize: '18px', fontWeight: 'bold', color: '#1d1d1d', marginBottom: '8px' } }, 'Application Identity'),
              h('p', { style: { fontSize: '14px', color: '#6b7280', margin: 0 } }, 'Enter the basic information for your application')
            ),
            h('div', { style: { backgroundColor: 'white', borderRadius: '16px', padding: '24px', marginBottom: '24px', border: '1px solid #e5e7eb' } },
              h('div', { style: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' } },
                InputField('Application Name', formData.name, function(v) { updateField('name', v); }, 'e.g. Core Banking System', 'text', true),
                InputField('Short Code', formData.shortCode, function(v) { updateField('shortCode', v.toUpperCase().slice(0, 6)); }, 'e.g. CBS', 'text', true),
                SelectField('Domain', formData.domain, DOMAINS, function(v) { updateField('domain', v); }, true),
                SelectField('Environment', formData.environment, ENVIRONMENTS, function(v) { updateField('environment', v); }, true),
                SelectField('Criticality', formData.criticality, CRITICALITIES, function(v) { updateField('criticality', v); }, true)
              )
            ),
            h('div', { style: { marginBottom: '24px' } },
              h('h3', { style: { fontSize: '18px', fontWeight: 'bold', color: '#1d1d1d', marginBottom: '8px' } }, 'Ownership'),
              h('p', { style: { fontSize: '14px', color: '#6b7280', margin: 0 } }, 'Define who is responsible for this application')
            ),
            h('div', { style: { backgroundColor: 'white', borderRadius: '16px', padding: '24px', marginBottom: '24px', border: '1px solid #e5e7eb' } },
              h('div', { style: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' } },
                InputField('Business Owner', formData.businessOwner, function(v) { updateField('businessOwner', v); }, 'owner@kfh.com'),
                InputField('Tech Owner Team', formData.techOwnerTeam, function(v) { updateField('techOwnerTeam', v); }, 'Platform Engineering')
              ),
              InputField('Escalation Group', formData.escalationGroup, function(v) { updateField('escalationGroup', v); }, 'e.g. ESC-CBS')
            ),
            h('div', { style: { marginBottom: '16px' } },
              h('h3', { style: { fontSize: '18px', fontWeight: 'bold', color: '#1d1d1d', marginBottom: '8px' } }, 'Monitoring Sources'),
              h('p', { style: { fontSize: '14px', color: '#6b7280', margin: 0 } }, 'Select which monitoring tools to integrate')
            ),
            h('div', { style: { backgroundColor: 'white', borderRadius: '16px', padding: '24px', border: '1px solid #e5e7eb' } },
              h('div', { style: { display: 'flex', flexWrap: 'wrap', gap: '12px' } },
                SOURCES.map(function(s) {
                  var isOn = formData.onboardedSources.indexOf(s) >= 0;
                  return h('button', {
                    key: s,
                    type: 'button',
                    onClick: function() { toggleSource(s); },
                    style: {
                      padding: '12px 20px',
                      borderRadius: '10px',
                      border: isOn ? '2px solid #128754' : '2px solid #e5e7eb',
                      fontSize: '14px',
                      fontWeight: '600',
                      cursor: 'pointer',
                      backgroundColor: isOn ? '#dcfce7' : 'white',
                      color: isOn ? '#128754' : '#374151',
                      transition: 'all 0.2s ease'
                    }
                  }, isOn ? '✓ ' + s : s);
                })
              )
            )
          ),

          // Step 2: Inventory
          step === 2 && h('div', null,
            h('div', { style: { marginBottom: '32px' } },
              h('h3', { style: { fontSize: '18px', fontWeight: 'bold', color: '#1d1d1d', marginBottom: '8px' } }, 'Application Inventory'),
              h('p', { style: { fontSize: '14px', color: '#6b7280', margin: 0 } }, 'Add servers, databases, and other resources (optional)')
            ),
            h('div', { style: { backgroundColor: 'white', borderRadius: '16px', padding: '24px', marginBottom: '24px', border: '1px solid #e5e7eb' } },
              h('div', { style: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' } },
                SelectField('Type', newItem.type, ['Server', 'Database', 'K8s', 'URL'], function(v) { setNewItem(function(p) { return Object.assign({}, p, { type: v }); }); }),
                SelectField('Status', newItem.status, ['Healthy', 'Warning', 'Critical'], function(v) { setNewItem(function(p) { return Object.assign({}, p, { status: v }); }); }),
                InputField('Name', newItem.name, function(v) { setNewItem(function(p) { return Object.assign({}, p, { name: v }); }); }, 'e.g. KFHPRD-APP-01'),
                InputField('Identifier', newItem.identifier, function(v) { setNewItem(function(p) { return Object.assign({}, p, { identifier: v }); }); }, 'e.g. 10.10.10.10')
              ),
              h('div', { style: { textAlign: 'right' } },
                h('button', {
                  type: 'button',
                  onClick: addItem,
                  disabled: !newItem.name.trim() || !newItem.identifier.trim(),
                  style: {
                    padding: '12px 24px',
                    borderRadius: '10px',
                    border: 'none',
                    backgroundColor: (!newItem.name.trim() || !newItem.identifier.trim()) ? '#e5e7eb' : '#128754',
                    color: (!newItem.name.trim() || !newItem.identifier.trim()) ? '#9ca3af' : 'white',
                    fontSize: '14px',
                    fontWeight: '600',
                    cursor: (!newItem.name.trim() || !newItem.identifier.trim()) ? 'not-allowed' : 'pointer'
                  }
                }, '+ Add Item')
              )
            ),
            inventoryItems.length > 0
              ? h('div', { style: { backgroundColor: 'white', borderRadius: '16px', padding: '24px', border: '1px solid #e5e7eb' } },
                  h('div', { style: { fontSize: '14px', fontWeight: 'bold', color: '#374151', marginBottom: '16px' } }, inventoryItems.length + ' item(s) added'),
                  inventoryItems.map(function(item) {
                    var statusColors = {
                      Healthy: { bg: '#dcfce7', text: '#166534' },
                      Warning: { bg: '#fef9c3', text: '#854d0e' },
                      Critical: { bg: '#fee2e2', text: '#991b1b' }
                    };
                    var colors = statusColors[item.status] || statusColors.Healthy;
                    return h('div', {
                      key: item.id,
                      style: {
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        padding: '16px',
                        backgroundColor: '#f9fafb',
                        borderRadius: '12px',
                        marginBottom: '12px',
                        border: '1px solid #e5e7eb'
                      }
                    },
                      h('div', { style: { display: 'flex', alignItems: 'center', gap: '16px' } },
                        h('div', {
                          style: {
                            width: '40px',
                            height: '40px',
                            borderRadius: '10px',
                            backgroundColor: '#e0f2fe',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontSize: '18px'
                          }
                        }, item.type === 'Server' ? '🖥️' : item.type === 'Database' ? '🗄️' : item.type === 'K8s' ? '☸️' : '🌐'),
                        h('div', null,
                          h('div', { style: { fontWeight: '600', color: '#1d1d1d' } }, item.name),
                          h('div', { style: { fontSize: '13px', color: '#6b7280' } }, item.type + ' • ' + item.identifier)
                        )
                      ),
                      h('div', { style: { display: 'flex', alignItems: 'center', gap: '12px' } },
                        h('span', {
                          style: {
                            padding: '6px 12px',
                            borderRadius: '20px',
                            fontSize: '12px',
                            fontWeight: '600',
                            backgroundColor: colors.bg,
                            color: colors.text
                          }
                        }, item.status),
                        h('button', {
                          type: 'button',
                          onClick: function() { removeItem(item.id); },
                          style: {
                            width: '32px',
                            height: '32px',
                            borderRadius: '8px',
                            border: 'none',
                            backgroundColor: '#fee2e2',
                            color: '#dc2626',
                            cursor: 'pointer',
                            fontSize: '16px'
                          }
                        }, '×')
                      )
                    );
                  })
                )
              : h('div', {
                  style: {
                    backgroundColor: 'white',
                    borderRadius: '16px',
                    padding: '48px',
                    textAlign: 'center',
                    border: '2px dashed #e5e7eb'
                  }
                },
                  h('div', { style: { fontSize: '48px', marginBottom: '16px' } }, '📦'),
                  h('div', { style: { fontSize: '16px', fontWeight: '600', color: '#374151', marginBottom: '8px' } }, 'No inventory items yet'),
                  h('div', { style: { fontSize: '14px', color: '#6b7280' } }, 'Add servers, databases, or other resources above')
                )
          ),

          // Step 3: Settings & Review
          step === 3 && h('div', null,
            h('div', { style: { marginBottom: '32px' } },
              h('h3', { style: { fontSize: '18px', fontWeight: 'bold', color: '#1d1d1d', marginBottom: '8px' } }, 'Settings & Review'),
              h('p', { style: { fontSize: '14px', color: '#6b7280', margin: 0 } }, 'Configure thresholds and review your application')
            ),
            h('div', { style: { backgroundColor: 'white', borderRadius: '16px', padding: '24px', marginBottom: '24px', border: '1px solid #e5e7eb' } },
              h('h4', { style: { fontSize: '16px', fontWeight: '600', color: '#374151', marginBottom: '16px' } }, 'SLA Thresholds'),
              h('div', { style: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' } },
                InputField('MTTR Target (minutes)', String(formData.mttrTarget), function(v) { updateField('mttrTarget', parseInt(v) || 60); }, '60', 'number'),
                InputField('Critical Alert Threshold', String(formData.criticalThreshold), function(v) { updateField('criticalThreshold', parseInt(v) || 5); }, '5', 'number')
              )
            ),
            h('div', { style: { backgroundColor: 'white', borderRadius: '16px', padding: '24px', marginBottom: '24px', border: '1px solid #e5e7eb' } },
              h('h4', { style: { fontSize: '16px', fontWeight: '600', color: '#374151', marginBottom: '16px' } }, 'Notifications'),
              InputField('Teams Webhook URL', formData.teamsWebhook, function(v) { updateField('teamsWebhook', v); }, 'https://outlook.office.com/webhook/...'),
              InputField('Email Distribution List', formData.emailDistro, function(v) { updateField('emailDistro', v); }, 'ops-team@kfh.com'),
              h('label', { style: { display: 'flex', alignItems: 'center', gap: '12px', cursor: 'pointer', marginTop: '8px' } },
                h('input', {
                  type: 'checkbox',
                  checked: formData.postOnCritical,
                  onChange: function(e) { updateField('postOnCritical', e.target.checked); },
                  style: { width: '20px', height: '20px', accentColor: '#128754' }
                }),
                h('span', { style: { fontSize: '14px', fontWeight: '500', color: '#374151' } }, 'Automatically post on critical incidents')
              )
            ),
            // Summary Card
            h('div', { style: { backgroundColor: '#f0fdf4', borderRadius: '16px', padding: '24px', border: '2px solid #86efac' } },
              h('h4', { style: { fontSize: '16px', fontWeight: '600', color: '#166534', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' } },
                '✓ Ready to Create'
              ),
              h('div', { style: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', fontSize: '14px' } },
                h('div', null, h('span', { style: { color: '#6b7280' } }, 'Name: '), h('span', { style: { fontWeight: '600', color: '#1d1d1d' } }, formData.name || '-')),
                h('div', null, h('span', { style: { color: '#6b7280' } }, 'Code: '), h('span', { style: { fontWeight: '600', color: '#1d1d1d' } }, formData.shortCode || '-')),
                h('div', null, h('span', { style: { color: '#6b7280' } }, 'Domain: '), h('span', { style: { fontWeight: '600', color: '#1d1d1d' } }, formData.domain)),
                h('div', null, h('span', { style: { color: '#6b7280' } }, 'Environment: '), h('span', { style: { fontWeight: '600', color: '#1d1d1d' } }, formData.environment)),
                h('div', null, h('span', { style: { color: '#6b7280' } }, 'Criticality: '), h('span', { style: { fontWeight: '600', color: '#1d1d1d' } }, formData.criticality)),
                h('div', null, h('span', { style: { color: '#6b7280' } }, 'Inventory: '), h('span', { style: { fontWeight: '600', color: '#1d1d1d' } }, inventoryItems.length + ' items')),
                h('div', null, h('span', { style: { color: '#6b7280' } }, 'Sources: '), h('span', { style: { fontWeight: '600', color: '#1d1d1d' } }, formData.onboardedSources.join(', ') || 'None'))
              )
            )
          )
        ),

        // Footer with navigation
        h('div', {
          style: {
            padding: '20px 32px',
            borderTop: '1px solid #e5e7eb',
            backgroundColor: 'white',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center'
          }
        },
          h('button', {
            type: 'button',
            onClick: step === 1 ? props.onClose : prevStep,
            style: {
              padding: '12px 24px',
              borderRadius: '10px',
              border: '2px solid #e5e7eb',
              backgroundColor: 'white',
              color: '#374151',
              fontSize: '14px',
              fontWeight: '600',
              cursor: 'pointer'
            }
          }, step === 1 ? 'Cancel' : '← Back'),
          h('div', { style: { display: 'flex', gap: '12px' } },
            step < 3 && h('button', {
              type: 'button',
              onClick: nextStep,
              disabled: step === 1 && !step1Valid,
              style: {
                padding: '12px 32px',
                borderRadius: '10px',
                border: 'none',
                backgroundColor: (step === 1 && !step1Valid) ? '#e5e7eb' : '#128754',
                color: (step === 1 && !step1Valid) ? '#9ca3af' : 'white',
                fontSize: '14px',
                fontWeight: '600',
                cursor: (step === 1 && !step1Valid) ? 'not-allowed' : 'pointer'
              }
            }, 'Next Step →'),
            step === 3 && h('button', {
              type: 'button',
              onClick: handleCreate,
              disabled: !canCreate,
              style: {
                padding: '12px 32px',
                borderRadius: '10px',
                border: 'none',
                backgroundColor: !canCreate ? '#e5e7eb' : '#128754',
                color: !canCreate ? '#9ca3af' : 'white',
                fontSize: '14px',
                fontWeight: '600',
                cursor: !canCreate ? 'not-allowed' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '8px'
              }
            }, '✓ Create Application')
          )
        )
      )
    );
  }

  function App() {
    var [apps, setApps] = React.useState(emptyApplications);
    var [search, setSearch] = React.useState('');
    var [createOpen, setCreateOpen] = React.useState(false);

    // Register the global drawer opener function
    React.useEffect(function() {
      openDrawerFn = function() {
        setCreateOpen(true);
      };
      return function() {
        openDrawerFn = null;
      };
    }, []);

    // Auto-open create drawer when routed with ?create=1
    React.useEffect(function() {
      try {
        var hash = window.location.hash || '';
        var qIndex = hash.indexOf('?');
        if (qIndex < 0) return;
        var qs = hash.slice(qIndex + 1);
        var params = new URLSearchParams(qs);
        if (params.get('create') === '1') {
          setCreateOpen(true);
        }
      } catch (e) {
        // ignore
      }
    }, []);

    React.useEffect(function() {
      var cancelled = false;
      async function loadApplications() {
        if (!window.APIClient || !APIClient.applications) {
          if (!cancelled) setApps([]);
          return;
        }
        try {
          var response = await APIClient.applications.list({ page: 0, size: 100 });
          if (!cancelled) setApps(pageContent(response).map(normalizeApplication));
        } catch (error) {
          console.warn('[Applications] Unable to load production applications; rendering empty state.', error);
          if (!cancelled) setApps([]);
        }
      }
      loadApplications();
      return function() { cancelled = true; };
    }, []);

    var filtered = React.useMemo(function() {
      var q = search.trim().toLowerCase();
      if (!q) return apps;
      return apps.filter(function(a) {
        return a.name.toLowerCase().includes(q) ||
        a.shortCode.toLowerCase().includes(q) ||
        a.domain.toLowerCase().includes(q) ||
        a.environment.toLowerCase().includes(q);
      });
    }, [apps, search]);

    function goToConfig(app, mode) {
      var id = app && app.id ? app.id : 'app-cbs-001';
      var nextMode = mode || 'view';
      // Always use absolute path from site root
      window.location.href = '/pages/applications/applicationconfig.html?id=' + encodeURIComponent(id) + '&mode=' + encodeURIComponent(nextMode);
    }

    function openDetails(app) {
      goToConfig(app, 'view');
    }

    function openEdit(app) {
      goToConfig(app, 'edit');
    }

    function openCreateDrawer() {
      setCreateOpen(true);
    }

    async function handleCreateSave(payload) {
      var draft = payload.draft;
      var invSummary = payload.invSummary;

      var shortCode = String(draft.shortCode || '').toUpperCase().slice(0, 6);
      var id = 'app-' + shortCode.toLowerCase() + '-' + generateId().slice(-3);

      var businessOwner = (draft.businessOwner || '').trim() || ('owner.' + shortCode.toLowerCase() + '@kfh.com');
      var techOwnerTeam = (draft.techOwnerTeam || '').trim() || 'Platform Engineering';
      var escalationGroup = (draft.escalationGroup || '').trim() || ('ESC-' + shortCode);

      // In the Apps list we store aggregate counts only (UI-only).
      var app = {
        id: id,
        name: String(draft.name || '').trim(),
        shortCode: shortCode,
        domain: draft.domain,
        environment: draft.environment,
        criticality: draft.criticality,
        tags: [draft.domain.split(' ')[0], draft.environment.toLowerCase(), draft.criticality.toLowerCase()],
        businessOwner: businessOwner,
        techOwnerTeam: techOwnerTeam,
        escalationGroup: escalationGroup,
        onboardedSources: Array.isArray(draft.onboardedSources) ? draft.onboardedSources.slice(0) : [],
        healthScore: 100,
        incidentStats: { openTotal: 0, openCritical: 0, new15d: 0, recurring15d: 0 },
        inventoryCount: invSummary.total,
        inventoryCounts: invSummary.counts,
        createdAt: nowIso(),
        updatedAt: nowIso(),
        config: {
          onboardingSources: Object.fromEntries(SOURCES.map(function(s) { return [s, { enabled: (Array.isArray(draft.onboardedSources) ? draft.onboardedSources.includes(s) : true), filter: '' }]; })),
          mttrTarget: draft.mttrTarget || 60,
          criticalThreshold: draft.criticalThreshold || 5,
          noiseSuppressionEnabled: draft.noiseSuppressionEnabled !== false,
          notifications: draft.notifications || { teamsWebhook: '', emailDistro: '', postOnCritical: true }
        },
        // Keep inventory items so applicationconfig page can load it later (still UI-only)
        inventoryItems: payload.inventory || []
      };

      try {
        var created = await APIClient.applications.create({ name: app.name, attributes: app });
        app = normalizeApplication(created || app);
        setApps(function(prev) { return [app].concat(prev); });
        setCreateOpen(false);
      } catch (error) {
        console.warn('[Applications] Unable to create application.', error);
        return;
      }

      // Navigate into config view (create mode) for the full multi-tab experience.
      window.location.href = '/pages/applications/applicationconfig.html?id=' + encodeURIComponent(app.id) + '&mode=create';
    }

    // Run lucide icons only once on mount
    React.useEffect(function() {
      if (window.lucide && typeof window.lucide.createIcons === 'function') {
        window.lucide.createIcons();
      }
    }, []);

    // Calculate KPIs from apps data
    var kpis = React.useMemo(function() {
      var totalApps = apps.length;
      var healthyApps = apps.filter(function(a) { return a.healthScore >= 80; }).length;
      var degradedApps = apps.filter(function(a) { return a.healthScore >= 50 && a.healthScore < 80; }).length;
      var criticalApps = apps.filter(function(a) { return a.healthScore < 50; }).length;
      var totalIncidents = apps.reduce(function(sum, a) { return sum + (a.incidentStats?.openTotal || 0); }, 0);
      var criticalIncidents = apps.reduce(function(sum, a) { return sum + (a.incidentStats?.openCritical || 0); }, 0);
      var tier1Apps = apps.filter(function(a) { return a.criticality === 'Tier1'; }).length;
      return { totalApps: totalApps, healthyApps: healthyApps, degradedApps: degradedApps, criticalApps: criticalApps, totalIncidents: totalIncidents, criticalIncidents: criticalIncidents, tier1Apps: tier1Apps };
    }, [apps]);

    return h('div', { className: 'space-y-4', style: { padding: '24px 32px 32px' } },
      h('div', { className: 'kfh-card', style: { padding: 20, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 } },
        h('div', null,
          h('p', { style: { margin: 0, color: 'var(--kfh-gold)', fontSize: 12, fontWeight: 900, letterSpacing: '0.08em', textTransform: 'uppercase' } }, 'Application Portfolio'),
          h('h1', { style: { margin: '4px 0 0', fontSize: 28, fontWeight: 900, color: 'var(--text-primary)' } }, 'Applications')
        ),
        h('div', { style: { display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap', justifyContent: 'flex-end' } },
          h('input', {
            className: 'kfh-input',
            value: search,
            onChange: function(e) { setSearch(e.target.value || ''); },
            placeholder: 'Search applications',
            style: { width: 280 }
          }),
          h('button', {
            type: 'button',
            onClick: function() { setCreateOpen(true); },
            className: 'px-4 py-2 bg-[var(--kfh-primary)] text-white text-sm font-semibold rounded-lg shadow-sm'
          }, '+ Add Application')
        )
      ),

      // KPI Summary Row
      h('div', { className: 'grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-3' },
        h(KPICard, { icon: '📱', value: kpis.totalApps, label: 'Total Apps', bgColor: 'var(--kfh-primary-light)', color: 'var(--kfh-primary-dark)' }),
        h(KPICard, { icon: '✅', value: kpis.healthyApps, label: 'Healthy', bgColor: '#D1FAE5', color: '#047857', valueColor: '#047857' }),
        h(KPICard, { icon: '⚠️', value: kpis.degradedApps, label: 'Degraded', bgColor: '#FEF3C7', color: '#B45309', valueColor: '#B45309' }),
        h(KPICard, { icon: '🔴', value: kpis.criticalApps, label: 'Critical', bgColor: '#FEE2E2', color: '#B91C1C', valueColor: '#B91C1C' }),
        h(KPICard, { icon: '🎫', value: kpis.totalIncidents, label: 'Open Incidents', bgColor: '#EFF6FF', color: '#1D4ED8' }),
        h(KPICard, { icon: '⭐', value: kpis.tier1Apps, label: 'Tier 1 Apps', bgColor: '#FEF2F2', color: '#DC2626' })
      ),

      // Applications Grid - 3 columns on XL
      h('div', { className: 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-3 gap-4' },
        filtered.map(function(app) { return h(AppCard, {
          key: app.id,
          app: app,
          onOpen: openDetails,
          onEdit: openEdit
        }); })
      ),

      // Empty state
      filtered.length === 0 && h('div', { className: 'text-center py-12' },
        h('div', { className: 'text-4xl mb-2' }, '📱'),
        h('div', { className: 'text-gray-500' }, 'No applications found'),
        h('button', {
          type: 'button',
          onClick: function() { setCreateOpen(true); },
          className: 'mt-4 px-4 py-2 bg-[var(--kfh-primary)] text-white text-sm font-semibold rounded-lg'
        }, 'Add Your First Application')
      ),

      h(AppCreateDrawer, {
        open: createOpen,
        onClose: function() { setCreateOpen(false); },
        onSave: handleCreateSave
      })
    );
  }

  // ---------- Boot ----------
  function boot() {
    var mount = document.getElementById('page-root') || document.getElementById('content-area') || document.getElementById('root');
    if (!mount) return;

    var root = ReactDOM.createRoot ? ReactDOM.createRoot(mount) : null;
    if (root) root.render(h(App));
    else ReactDOM.render(h(App), mount);
  }

  boot();
})();
