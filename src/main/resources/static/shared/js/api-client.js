/**
 * KFH AIOps Command Center - API Client
 * HTTP client wrapper with multi-tenancy support
 */
window.APIClient = (function() {
  'use strict';

  const BASE_URL = KFHConfig.API_BASE_URL;

  // Get required headers for multi-tenancy
  function getHeaders() {
    const session = KFHConfig.getSession();
    return {
      'Content-Type': 'application/json',
      'X-Tenant-Id': session.tenantId,
      'X-User-Id': session.userId,
      'X-Correlation-Id': KFHUtils.generateUUID()
    };
  }

  // Handle response
  async function handleResponse(response) {
    if (!response.ok) {
      const error = await response.json().catch(() => ({
        message: response.statusText,
        status: response.status
      }));
      throw new APIError(error.message || 'Request failed', response.status, error);
    }

    // Handle 204 No Content
    if (response.status === 204) {
      return null;
    }

    return response.json();
  }

  // Custom API Error class
  class APIError extends Error {
    constructor(message, status, details) {
      super(message);
      this.name = 'APIError';
      this.status = status;
      this.details = details;
    }
  }

  // GET request
  async function get(endpoint, params = {}) {
    const url = new URL(`${BASE_URL}${endpoint}`, window.location.origin);
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null) {
        url.searchParams.append(key, value);
      }
    });

    const response = await fetch(url.toString(), {
      method: 'GET',
      headers: getHeaders()
    });

    return handleResponse(response);
  }

  // POST request
  async function post(endpoint, data = {}) {
    const response = await fetch(`${BASE_URL}${endpoint}`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(data)
    });

    return handleResponse(response);
  }

  // PUT request
  async function put(endpoint, data = {}) {
    const response = await fetch(`${BASE_URL}${endpoint}`, {
      method: 'PUT',
      headers: getHeaders(),
      body: JSON.stringify(data)
    });

    return handleResponse(response);
  }

  // PATCH request
  async function patch(endpoint, data = {}) {
    const response = await fetch(`${BASE_URL}${endpoint}`, {
      method: 'PATCH',
      headers: getHeaders(),
      body: JSON.stringify(data)
    });

    return handleResponse(response);
  }

  // DELETE request
  async function del(endpoint) {
    const response = await fetch(`${BASE_URL}${endpoint}`, {
      method: 'DELETE',
      headers: getHeaders()
    });

    return handleResponse(response);
  }

  // ========== RESOURCE-SPECIFIC ENDPOINTS ==========

  // Incidents
  const incidents = {
    list: (params) => get('/incidents', params),
    get: (id) => get(`/incidents/${id}`),
    create: (data) => post('/incidents', data),
    update: (id, data) => put(`/incidents/${id}`, data),
    updateStatus: (id, status) => patch(`/incidents/${id}/status`, { status }),
    getEvidence: (id) => get(`/incidents/${id}/evidence`),
    getRelated: (id) => get(`/incidents/${id}/related`),
    getTimeline: (id) => get(`/incidents/${id}/timeline`)
  };

  // Alerts
  const alerts = {
    list: (params) => get('/alerts', params),
    get: (id) => get(`/alerts/${id}`),
    acknowledge: (ids) => post('/alerts/acknowledge', { ids }),
    getActivity: (params) => get('/alerts/activity', params)
  };

  // Applications
  const applications = {
    list: (params) => get('/applications', params),
    get: (id) => get(`/applications/${id}`),
    create: (data) => post('/applications', data),
    update: (id, data) => put(`/applications/${id}`, data),
    delete: (id) => del(`/applications/${id}`),
    getInventory: (id) => get(`/applications/${id}/inventory`),
    getIncidents: (id) => get(`/applications/${id}/incidents`),
    getHealth: (id) => get(`/applications/${id}/health`)
  };

  // Inventory
  const inventory = {
    list: (params) => get('/inventory', params),
    get: (id) => get(`/inventory/${id}`),
    create: (data) => post('/inventory', data),
    update: (id, data) => put(`/inventory/${id}`, data),
    delete: (id) => del(`/inventory/${id}`),
    getDependencies: (id) => get(`/inventory/${id}/dependencies`),
    getAlerts: (id) => get(`/inventory/${id}/alerts`)
  };

  // Connectors
  const connectors = {
    list: () => get('/connectors'),
    get: (id) => get(`/connectors/${id}`),
    create: (data) => post('/connectors', data),
    update: (id, data) => put(`/connectors/${id}`, data),
    delete: (id) => del(`/connectors/${id}`),
    toggle: (id, enabled) => patch(`/connectors/${id}/toggle`, { enabled }),
    test: (id) => post(`/connectors/${id}/test`),
    getLogs: (id, params) => get(`/connectors/${id}/logs`, params)
  };

  // Schedules
  const schedules = {
    list: () => get('/schedules'),
    get: (id) => get(`/schedules/${id}`),
    create: (data) => post('/schedules', data),
    update: (id, data) => put(`/schedules/${id}`, data),
    delete: (id) => del(`/schedules/${id}`),
    toggle: (id, enabled) => patch(`/schedules/${id}/toggle`, { enabled }),
    run: (id) => post(`/schedules/${id}/run`),
    getRuns: (id, params) => get(`/schedules/${id}/runs`, params)
  };

  // Reports
  const reports = {
    list: (params) => get('/reports', params),
    get: (id) => get(`/reports/${id}`),
    getRuns: (params) => get('/reports/runs', params),
    getArtifacts: (runId) => get(`/reports/runs/${runId}/artifacts`),
    download: (artifactId) => `${BASE_URL}/reports/artifacts/${artifactId}/download`,
    generate: (params) => post('/reports/generate', params)
  };

  // Users
  const users = {
    list: (params) => get('/users', params),
    get: (id) => get(`/users/${id}`),
    create: (data) => post('/users', data),
    update: (id, data) => put(`/users/${id}`, data),
    delete: (id) => del(`/users/${id}`),
    toggleStatus: (id) => patch(`/users/${id}/toggle`),
    getRoles: () => get('/users/roles'),
    getPermissions: (userId) => get(`/users/${userId}/permissions`)
  };

  // Roles
  const roles = {
    list: () => get('/roles'),
    get: (id) => get(`/roles/${id}`),
    create: (data) => post('/roles', data),
    update: (id, data) => put(`/roles/${id}`, data),
    delete: (id) => del(`/roles/${id}`)
  };

  // Settings
  const settings = {
    get: () => get('/settings'),
    update: (data) => put('/settings', data),
    test: (section, data) => post(`/settings/${section}/test`, data)
  };

  // Audit
  const audit = {
    list: (params) => get('/audit', params),
    get: (id) => get(`/audit/${id}`),
    export: (params) => get('/audit/export', params)
  };

  // Dashboard
  const dashboard = {
    getKpis: (params) => get('/dashboard/kpis', params),
    getTrends: (params) => get('/dashboard/trends', params),
    getTopApps: (params) => get('/dashboard/top-apps', params),
    getSummary: () => get('/dashboard/summary'),
    getSourceBreakdown: () => get('/dashboard/sources')
  };

  // Public API
  return {
    // Raw methods
    get,
    post,
    put,
    patch,
    delete: del,

    // Error class
    APIError,

    // Resource endpoints
    incidents,
    alerts,
    applications,
    inventory,
    connectors,
    schedules,
    reports,
    users,
    roles,
    settings,
    audit,
    dashboard
  };
})();
