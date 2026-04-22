/* global React, ReactDOM, Recharts */

// KFH AIOps Command Center - Schedules
// NOTE: This page is intentionally self-contained and uses only local vendor assets.
// Wrapped in IIFE to avoid global const/let redeclaration errors when re-injected by router.

(function() {
'use strict';

// Tailwind config (local tailwind runtime reads this)
// Keep identity aligned to Beyond Horizons (KFH green + gold)
if (typeof tailwind !== 'undefined') {
  tailwind.config = {
    darkMode: 'class',
    theme: {
      extend: {
        fontFamily: {
          sans: ['Outfit', 'system-ui', 'sans-serif'],
        },
        colors: {
          kfh: {
            primary: 'var(--kfh-primary)',
            primaryDark: 'var(--kfh-primary-dark)',
            primaryLight: 'var(--kfh-primary-light)',
            gold: 'var(--kfh-gold)',
            goldDark: 'var(--kfh-gold-dark)',
            bg: 'var(--surface-bg)',
            card: 'var(--surface-card)',
            border: 'var(--surface-border)'
          }
        }
      }
    }
  };
}

const { useState, useEffect, useCallback, useMemo, createContext, useContext } = React;
const {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer
} = Recharts;

// ============== ICONS ==============
// Uses inline SVGs from provided design (kept as-is)
const Icons = {
  Calendar: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 012.25-2.25h13.5A2.25 2.25 0 0121 7.5v11.25m-18 0A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75m-18 0v-7.5A2.25 2.25 0 015.25 9h13.5A2.25 2.25 0 0121 11.25v7.5" />
    </svg>
  ),
  Clock: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  ),
  Play: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M5.25 5.653c0-.856.917-1.398 1.667-.986l11.54 6.348a1.125 1.125 0 010 1.971l-11.54 6.347a1.125 1.125 0 01-1.667-.985V5.653z" />
    </svg>
  ),
  Pause: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 5.25v13.5m-7.5-13.5v13.5" />
    </svg>
  ),
  Search: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
    </svg>
  ),
  Plus: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
    </svg>
  ),
  List: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 6.75h12M8.25 12h12m-12 5.25h12M3.75 6.75h.007v.008H3.75V6.75zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zM3.75 12h.007v.008H3.75V12zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm-.375 5.25h.007v.008H3.75v-.008zm.375 0a.375.375375 0 11-.75 0 .375.375 0 01.75 0z" />
    </svg>
  ),
  Check: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
    </svg>
  ),
  X: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
    </svg>
  ),
  AlertTriangle: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
    </svg>
  ),
  Cog: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.324.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 011.37.49l1.296 2.247a1.125 1.125 0 01-.26 1.431l-1.003.827c-.293.24-.438.613-.431.992a6.759 6.759 0 010 .255c-.007.378.138.75.43.99l1.005.828c.424.35.534.954.26 1.43l-1.298 2.247a1.125 1.125 0 01-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.57 6.57 0 01-.22.128c-.331.183-.581.495-.644.869l-.213 1.28c-.09.543-.56.941-1.11.941h-2.594c-.55 0-1.02-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 01-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 01-1.369-.49l-1.297-2.247a1.125 1.125 0 01.26-1.431l1.004-.827c.292-.24.437-.613.43-.992a6.932 6.932 0 010-.255c.007-.378-.138-.75-.43-.99l-1.004-.828a1.125 1.125 0 01-.26-1.43l1.297-2.247a1.125 1.125 0 011.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.087.22-.128.332-.183.582-.495.644-.869l.214-1.281z" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
    </svg>
  ),
  History: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  ),
  Terminal: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M6.75 7.5l3 2.25-3 2.25m4.5 0h3m-9 8.25h13.5A2.25 2.25 0 0021 18V6a2.25 2.25 0 00-2.25-2.25H5.25A2.25 2.25 0 003 6v12a2.25 2.25 0 002.25 2.25z" />
    </svg>
  ),
  Trash: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" />
    </svg>
  ),
  Edit: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931zm0 0L19.5 7.125M18 14v4.75A2.25 2.25 0 0115.75 21H5.25A2.25 2.25 0 013 18.75V8.25A2.25 2.25 0 015.25 6H10" />
    </svg>
  ),
  Refresh: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182m0-4.991v4.99" />
    </svg>
  ),
  Zap: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75z" />
    </svg>
  ),
  FileText: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
    </svg>
  ),
  Bell: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M14.857 17.082a23.848 23.848 0 005.454-1.31A8.967 8.967 0 0118 9.75v-.7V9A6 6 0 006 9v.75a8.967 8.967 0 01-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 01-5.714 0m5.714 0a3 3 0 11-5.714 0" />
    </svg>
  ),
  Archive: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M20.25 7.5l-.625 10.632a2.25 2.25 0 01-2.247 2.118H6.622a2.25 2.25 0 01-2.247-2.118L3.75 7.5M10 11.25h4M3.375 7.5h17.25c.621 0 1.125-.504 1.125-1.125v-1.5c0-.621-.504-1.125-1.125-1.125H3.375c-.621 0-1.125.504-1.125 1.125v1.5c0 .621.504 1.125 1.125 1.125z" />
    </svg>
  ),
  Activity: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" />
    </svg>
  ),
  TrendingUp: ({ className }) => (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 18L9 11.25l4.306 4.307a11.95 11.95 0 015.814-5.519l2.74-1.22m0 0l-5.94-2.28m5.94 2.28l-2.28 5.941" />
    </svg>
  ),
  Loader: ({ className }) => (
    <svg className={`${className} animate-spin`} fill="none" viewBox="0 0 24 24">
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
    </svg>
  )
};

// ============== TYPES & ENUMS ==============
const JobType = {
  HourlyAnalysis: 'HourlyAnalysis',
  ConnectorSync: 'ConnectorSync',
  EvidenceExport: 'EvidenceExport',
  ReportPack: 'ReportPack',
  Retention: 'Retention',
  Notification: 'Notification'
};

const Environment = {
  Prod: 'Prod',
  DR: 'DR',
  Both: 'Both'
};

const Priority = {
  High: 'High',
  Med: 'Med',
  Low: 'Low'
};

const JobResult = {
  Success: 'Success',
  Failed: 'Failed',
  Skipped: 'Skipped',
  Never: 'Never'
};

const RunStatus = {
  Queued: 'Queued',
  Running: 'Running',
  Success: 'Success',
  Failed: 'Failed',
  Canceled: 'Canceled'
};

// ============== MOCK DATA ==============
const generateId = () => Math.random().toString(36).substr(2, 9);
const now = new Date();

const initialJobs = [
  {
    id: 'job-001',
    name: 'Hourly Alert Analysis',
    jobType: JobType.HourlyAnalysis,
    enabled: true,
    timezone: 'Asia/Kuwait',
    cronOrInterval: '0 * * * *',
    windowMinutes: 15,
    scope: { env: Environment.Both, domain: 'Core Banking', appIds: ['APP-001', 'APP-002'] },
    nextRunAt: new Date(now.getTime() + 25 * 60000).toISOString(),
    lastRunAt: new Date(now.getTime() - 35 * 60000).toISOString(),
    lastResult: JobResult.Success,
    avgDurationSec: 142,
    lastDurationSec: 138,
    ownerTeam: 'Platform Ops',
    priority: Priority.High,
    errorRate7d: 2.1,
    runs7d: 168,
    notes: 'Primary analysis job for core banking alerts'
  },
  {
    id: 'job-002',
    name: 'ServiceNow Connector Sync',
    jobType: JobType.ConnectorSync,
    enabled: true,
    timezone: 'Asia/Kuwait',
    cronOrInterval: '*/15 * * * *',
    windowMinutes: 5,
    scope: { env: Environment.Prod, connectorIds: ['CONN-SNOW-001'] },
    nextRunAt: new Date(now.getTime() + 8 * 60000).toISOString(),
    lastRunAt: new Date(now.getTime() - 7 * 60000).toISOString(),
    lastResult: JobResult.Success,
    avgDurationSec: 45,
    lastDurationSec: 42,
    ownerTeam: 'Integration',
    priority: Priority.High,
    errorRate7d: 0.5,
    runs7d: 672,
    notes: 'Syncs incidents with ServiceNow'
  },
  {
    id: 'job-003',
    name: 'Daily Evidence Export',
    jobType: JobType.EvidenceExport,
    enabled: true,
    timezone: 'Asia/Kuwait',
    cronOrInterval: '0 6 * * *',
    windowMinutes: 60,
    scope: { env: Environment.Prod, domain: 'All' },
    nextRunAt: new Date(now.getTime() + 12 * 3600000).toISOString(),
    lastRunAt: new Date(now.getTime() - 12 * 3600000).toISOString(),
    lastResult: JobResult.Success,
    avgDurationSec: 1850,
    lastDurationSec: 1720,
    ownerTeam: 'Compliance',
    priority: Priority.Med,
    errorRate7d: 0,
    runs7d: 7,
    notes: 'Daily compliance evidence export for audit'
  }
];

const generateLogLines = (status) => {
  const lines = [
    { level: 'INFO', timestamp: '00:00:00', message: 'Job started' },
    { level: 'INFO', timestamp: '00:00:01', message: 'Initializing connections...' },
    { level: 'INFO', timestamp: '00:00:02', message: 'Connected to data sources' },
    { level: 'DEBUG', timestamp: '00:00:05', message: 'Fetching alert data...' },
    { level: 'INFO', timestamp: '00:00:15', message: 'Processing batch 1 of 3' },
    { level: 'INFO', timestamp: '00:00:30', message: 'Processing batch 2 of 3' },
    { level: 'INFO', timestamp: '00:00:45', message: 'Processing batch 3 of 3' }
  ];

  if (status === RunStatus.Failed) {
    lines.push(
      { level: 'WARN', timestamp: '00:00:50', message: 'Connection timeout detected' },
      { level: 'ERROR', timestamp: '00:00:55', message: 'Failed to complete processing: ETIMEDOUT' },
      { level: 'INFO', timestamp: '00:00:56', message: 'Job failed - rolling back changes' }
    );
  } else {
    lines.push(
      { level: 'INFO', timestamp: '00:00:50', message: 'All batches processed successfully' },
      { level: 'INFO', timestamp: '00:00:55', message: 'Job completed successfully' }
    );
  }

  return lines;
};

const generateJobRuns = (jobId, count = 10) => {
  const runs = [];
  const statuses = [RunStatus.Success, RunStatus.Success, RunStatus.Success, RunStatus.Failed];
  for (let i = 0; i < count; i++) {
    const startedAt = new Date(now.getTime() - (i + 1) * 3600000);
    const duration = Math.floor(Math.random() * 200) + 50;
    const status = statuses[Math.floor(Math.random() * statuses.length)];
    runs.push({
      id: `run-${jobId}-${i}`,
      jobId,
      startedAt: startedAt.toISOString(),
      endedAt: new Date(startedAt.getTime() + duration * 1000).toISOString(),
      status,
      summary: status === RunStatus.Success
        ? `Processed ${Math.floor(Math.random() * 500) + 100} alerts successfully`
        : 'Connection timeout to upstream service',
      logLines: generateLogLines(status),
      metrics: {
        processedAlerts: Math.floor(Math.random() * 500) + 100,
        createdIncidents: Math.floor(Math.random() * 10),
        newCount: Math.floor(Math.random() * 50),
        recurringCount: Math.floor(Math.random() * 100),
        errors: status === RunStatus.Failed ? Math.floor(Math.random() * 5) + 1 : 0
      }
    });
  }
  return runs;
};

// ============== STORE ==============
const StoreContext = createContext(null);
const useStoreContext = () => useContext(StoreContext);

const useStore = () => {
  const [jobs, setJobs] = useState(initialJobs);
  const [jobRuns, setJobRuns] = useState(() => {
    const runs = {};
    initialJobs.forEach(job => { runs[job.id] = generateJobRuns(job.id); });
    return runs;
  });
  const [toasts, setToasts] = useState([]);

  const addToast = useCallback((message, type = 'success') => {
    const id = generateId();
    setToasts(prev => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, 4000);
  }, []);

  const addJob = useCallback((job) => {
    const newJob = { ...job, id: generateId() };
    setJobs(prev => [...prev, newJob]);
    setJobRuns(prev => ({ ...prev, [newJob.id]: [] }));
    addToast(`Job "${job.name}" created successfully`);
    return newJob;
  }, [addToast]);

  const updateJob = useCallback((id, updates) => {
    setJobs(prev => prev.map(job => job.id === id ? { ...job, ...updates } : job));
    addToast('Job updated successfully');
  }, [addToast]);

  const toggleJob = useCallback((id) => {
    setJobs(prev => prev.map(job => {
      if (job.id !== id) return job;
      const enabled = !job.enabled;
      const nextRunAt = enabled ? new Date(Date.now() + Math.random() * 3600000).toISOString() : null;
      return { ...job, enabled, nextRunAt };
    }));
  }, []);

  const runJobNow = useCallback((jobId) => {
    return new Promise(resolve => {
      const runId = generateId();
      const startedAt = new Date().toISOString();
      const queuedRun = {
        id: runId,
        jobId,
        startedAt,
        endedAt: null,
        status: RunStatus.Queued,
        summary: 'Job queued for execution',
        logLines: [{ level: 'INFO', timestamp: '00:00:00', message: 'Job queued' }],
        metrics: { processedAlerts: 0, createdIncidents: 0, newCount: 0, recurringCount: 0, errors: 0 }
      };

      setJobRuns(prev => ({
        ...prev,
        [jobId]: [queuedRun, ...(prev[jobId] || [])]
      }));

      setTimeout(() => {
        setJobRuns(prev => ({
          ...prev,
          [jobId]: prev[jobId].map(run => run.id === runId
            ? {
              ...run,
              status: RunStatus.Running,
              summary: 'Job is running...',
              logLines: [
                ...run.logLines,
                { level: 'INFO', timestamp: '00:00:01', message: 'Job started' },
                { level: 'INFO', timestamp: '00:00:02', message: 'Initializing...' }
              ]
            }
            : run)
        }));
      }, 400);

      setTimeout(() => {
        setJobRuns(prev => ({
          ...prev,
          [jobId]: prev[jobId].map(run => run.id === runId
            ? {
              ...run,
              logLines: [
                ...run.logLines,
                { level: 'INFO', timestamp: '00:00:05', message: 'Connecting to data sources...' },
                { level: 'DEBUG', timestamp: '00:00:08', message: 'Fetching alert data...' }
              ]
            }
            : run)
        }));
      }, 1200);

      setTimeout(() => {
        setJobRuns(prev => ({
          ...prev,
          [jobId]: prev[jobId].map(run => run.id === runId
            ? {
              ...run,
              logLines: [
                ...run.logLines,
                { level: 'INFO', timestamp: '00:00:15', message: 'Processing batch 1/3...' },
                { level: 'INFO', timestamp: '00:00:25', message: 'Processing batch 2/3...' }
              ]
            }
            : run)
        }));
      }, 2200);

      setTimeout(() => {
        const success = Math.random() > 0.15;
        const endedAt = new Date().toISOString();
        const metrics = {
          processedAlerts: Math.floor(Math.random() * 500) + 100,
          createdIncidents: Math.floor(Math.random() * 10),
          newCount: Math.floor(Math.random() * 50),
          recurringCount: Math.floor(Math.random() * 100),
          errors: success ? 0 : Math.floor(Math.random() * 5) + 1
        };

        setJobRuns(prev => ({
          ...prev,
          [jobId]: prev[jobId].map(run => run.id === runId
            ? {
              ...run,
              status: success ? RunStatus.Success : RunStatus.Failed,
              endedAt,
              summary: success
                ? `Processed ${metrics.processedAlerts} alerts successfully`
                : 'Job failed: Connection timeout',
              logLines: [
                ...run.logLines,
                { level: 'INFO', timestamp: '00:00:35', message: 'Processing batch 3/3...' },
                success
                  ? { level: 'INFO', timestamp: '00:00:45', message: 'All batches completed' }
                  : { level: 'ERROR', timestamp: '00:00:40', message: 'Connection timeout to upstream service' },
                { level: 'INFO', timestamp: '00:00:50', message: success ? 'Job completed successfully' : 'Job failed' }
              ],
              metrics
            }
            : run)
        }));

        setJobs(prev => prev.map(job => job.id === jobId
          ? {
            ...job,
            lastRunAt: endedAt,
            lastResult: success ? JobResult.Success : JobResult.Failed,
            lastDurationSec: Math.floor((new Date(endedAt) - new Date(startedAt)) / 1000)
          }
          : job));

        addToast(success ? 'Job completed successfully' : 'Job failed', success ? 'success' : 'error');
        resolve({ success, runId });
      }, 3800);
    });
  }, [addToast]);

  return {
    jobs,
    jobRuns,
    toasts,
    addJob,
    updateJob,
    toggleJob,
    runJobNow
  };
};

// ============== UTILITY ==============
const formatDuration = (seconds) => {
  if (!seconds) return '-';
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
  return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`;
};

const formatRelativeTime = (dateStr) => {
  if (!dateStr) return 'Never';
  const date = new Date(dateStr);
  const nowLocal = new Date();
  const diff = date - nowLocal;
  const abs = Math.abs(diff);

  if (abs < 60000) return diff > 0 ? 'In < 1m' : '< 1m ago';
  if (abs < 3600000) {
    const mins = Math.floor(abs / 60000);
    return diff > 0 ? `In ${mins}m` : `${mins}m ago`;
  }
  if (abs < 86400000) {
    const hours = Math.floor(abs / 3600000);
    return diff > 0 ? `In ${hours}h` : `${hours}h ago`;
  }
  const days = Math.floor(abs / 86400000);
  return diff > 0 ? `In ${days}d` : `${days}d ago`;
};

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-';
  return new Date(dateStr).toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
};

const getJobTypeIcon = (type) => {
  switch (type) {
    case JobType.HourlyAnalysis: return Icons.Activity;
    case JobType.ConnectorSync: return Icons.Cog;
    case JobType.EvidenceExport: return Icons.FileText;
    case JobType.ReportPack: return Icons.FileText;
    case JobType.Retention: return Icons.Archive;
    case JobType.Notification: return Icons.Bell;
    default: return Icons.Cog;
  }
};

// Map tags to KFH theme (green/gold) and semantic tokens
const getJobTypeColor = (type) => {
  switch (type) {
    case JobType.HourlyAnalysis: return 'text-[var(--color-info)] bg-[var(--color-info-bg)]';
    case JobType.ConnectorSync: return 'text-[var(--kfh-primary-dark)] bg-[var(--kfh-primary-light)]';
    case JobType.EvidenceExport: return 'text-[var(--kfh-primary)] bg-[var(--kfh-primary-lighter)]';
    case JobType.ReportPack: return 'text-[var(--kfh-gold-dark)] bg-[var(--kfh-gold-light)]';
    case JobType.Retention: return 'text-[var(--text-secondary)] bg-[var(--surface-off-white)]';
    case JobType.Notification: return 'text-[var(--color-high)] bg-[var(--color-high-bg)]';
    default: return 'text-[var(--text-secondary)] bg-[var(--surface-off-white)]';
  }
};

const getResultColor = (result) => {
  switch (result) {
    case JobResult.Success: return 'text-[var(--color-success)]';
    case JobResult.Failed: return 'text-[var(--color-danger)]';
    case JobResult.Skipped: return 'text-[var(--color-warning)]';
    default: return 'text-[var(--text-muted)]';
  }
};

const getResultBg = (result) => {
  switch (result) {
    case JobResult.Success: return 'bg-[var(--color-success-bg)] border-[var(--color-success-border)]';
    case JobResult.Failed: return 'bg-[var(--color-danger-bg)] border-[var(--color-danger-border)]';
    case JobResult.Skipped: return 'bg-[var(--color-warning-bg)] border-[var(--color-warning-border)]';
    default: return 'bg-[var(--surface-off-white)] border-[var(--surface-border)]';
  }
};

const getPriorityColor = (priority) => {
  switch (priority) {
    case Priority.High: return 'text-[var(--color-danger)] bg-[var(--color-danger-bg)]';
    case Priority.Med: return 'text-[var(--color-warning)] bg-[var(--color-warning-bg)]';
    case Priority.Low: return 'text-[var(--text-secondary)] bg-[var(--surface-off-white)]';
    default: return 'text-[var(--text-secondary)] bg-[var(--surface-off-white)]';
  }
};

// ============== COMPONENTS ==============
const Toast = ({ toast }) => (
  <div className={`flex items-center gap-3 px-4 py-3 rounded-lg border shadow-lg transition-all duration-300 ${
    toast.type === 'success'
      ? 'bg-[var(--color-success-bg)] border-[var(--color-success-border)] text-[var(--color-success)]'
      : toast.type === 'error'
        ? 'bg-[var(--color-danger-bg)] border-[var(--color-danger-border)] text-[var(--color-danger)]'
        : 'bg-[var(--color-info-bg)] border-[var(--color-info-border)] text-[var(--color-info)]'
  }`}
  >
    {toast.type === 'success' ? (
      <Icons.Check className="w-5 h-5" />
    ) : toast.type === 'error' ? (
      <Icons.X className="w-5 h-5" />
    ) : (
      <Icons.Bell className="w-5 h-5" />
    )}
    <span className="text-sm font-medium">{toast.message}</span>
  </div>
);

const KpiStrip = ({ jobs, jobRuns }) => {
  const stats = useMemo(() => {
    const enabledJobs = jobs.filter(j => j.enabled);
    const failedLastRun = jobs.filter(j => j.lastResult === JobResult.Failed);
    const today = new Date().toDateString();
    const runsToday = Object.values(jobRuns).flat().filter(r => new Date(r.startedAt).toDateString() === today).length;
    const avgDuration = jobs.length > 0 ? Math.round(jobs.reduce((sum, j) => sum + (j.avgDurationSec || 0), 0) / jobs.length) : 0;
    const avgErrorRate = jobs.length > 0 ? (jobs.reduce((sum, j) => sum + (j.errorRate7d || 0), 0) / jobs.length).toFixed(1) : '0';

    return [
      { label: 'Total Jobs', value: jobs.length, icon: Icons.Calendar },
      { label: 'Enabled', value: enabledJobs.length, icon: Icons.Check },
      { label: 'Failed Last Run', value: failedLastRun.length, icon: Icons.AlertTriangle },
      { label: 'Runs Today', value: runsToday, icon: Icons.Zap },
      { label: 'Avg Duration', value: formatDuration(avgDuration), icon: Icons.Clock },
      { label: 'Error Rate 7d', value: `${avgErrorRate}%`, icon: Icons.TrendingUp }
    ];
  }, [jobs, jobRuns]);

  return (
    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
      {stats.map((stat, i) => (
        <div key={i} className="kfh-card p-4">
          <div className="flex items-center justify-between mb-2">
            <stat.icon className="w-5 h-5 text-[var(--kfh-primary)]" />
            <span className="text-2xl font-bold text-[var(--kfh-primary)]">{stat.value}</span>
          </div>
          <p className="text-xs text-[var(--text-secondary)]">{stat.label}</p>
        </div>
      ))}
    </div>
  );
};

const FiltersBar = ({ search, setSearch, filters, setFilters, view, setView, onAddJob }) => {
  const teams = ['Platform Ops', 'Integration', 'Compliance', 'Management', 'NOC', 'DR Team', 'Security', 'Digital Banking'];

  return (
    <div className="flex flex-col lg:flex-row gap-4 items-start lg:items-center justify-between">
      <div className="flex flex-wrap gap-3 items-center">
        <div className="relative">
          <Icons.Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-[var(--text-secondary)]" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search jobs..."
            className="pl-10 pr-4 py-2 bg-white border rounded-lg text-sm focus:outline-none focus:ring-2 w-64"
            style={{ borderColor: 'rgba(0,0,0,0.08)', boxShadow: 'none' }}
          />
        </div>

        <select
          value={filters.jobType}
          onChange={(e) => setFilters({ ...filters, jobType: e.target.value })}
          className="px-3 py-2 bg-white border rounded-lg text-sm focus:outline-none focus:ring-2"
          style={{ borderColor: 'rgba(0,0,0,0.08)' }}
        >
          <option value="">All Types</option>
          {Object.values(JobType).map(type => (
            <option key={type} value={type}>{type.replace(/([A-Z])/g, ' $1').trim()}</option>
          ))}
        </select>

        <select
          value={filters.enabled}
          onChange={(e) => setFilters({ ...filters, enabled: e.target.value })}
          className="px-3 py-2 bg-white border rounded-lg text-sm focus:outline-none focus:ring-2"
          style={{ borderColor: 'rgba(0,0,0,0.08)' }}
        >
          <option value="">All Status</option>
          <option value="true">Enabled</option>
          <option value="false">Disabled</option>
        </select>

        <select
          value={filters.lastResult}
          onChange={(e) => setFilters({ ...filters, lastResult: e.target.value })}
          className="px-3 py-2 bg-white border rounded-lg text-sm focus:outline-none focus:ring-2"
          style={{ borderColor: 'rgba(0,0,0,0.08)' }}
        >
          <option value="">All Results</option>
          {Object.values(JobResult).map(result => (
            <option key={result} value={result}>{result}</option>
          ))}
        </select>

        <select
          value={filters.ownerTeam}
          onChange={(e) => setFilters({ ...filters, ownerTeam: e.target.value })}
          className="px-3 py-2 bg-white border rounded-lg text-sm focus:outline-none focus:ring-2"
          style={{ borderColor: 'rgba(0,0,0,0.08)' }}
        >
          <option value="">All Teams</option>
          {teams.map(team => (
            <option key={team} value={team}>{team}</option>
          ))}
        </select>

        <select
          value={filters.env}
          onChange={(e) => setFilters({ ...filters, env: e.target.value })}
          className="px-3 py-2 bg-white border rounded-lg text-sm focus:outline-none focus:ring-2"
          style={{ borderColor: 'rgba(0,0,0,0.08)' }}
        >
          <option value="">All Environments</option>
          {Object.values(Environment).map(env => (
            <option key={env} value={env}>{env}</option>
          ))}
        </select>
      </div>

      <div className="flex gap-3 items-center">
        <div className="flex bg-white border rounded-lg overflow-hidden" style={{ borderColor: 'rgba(0,0,0,0.08)' }}>
          <button
            onClick={() => setView('list')}
            className={`px-3 py-2 flex items-center gap-2 text-sm transition-colors ${
              view === 'list' ? 'bg-[var(--kfh-primary-light)] text-[var(--kfh-primary-dark)]' : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
            }`}
          >
            <Icons.List className="w-4 h-4" />
            List
          </button>
          <button
            onClick={() => setView('timeline')}
            className={`px-3 py-2 flex items-center gap-2 text-sm transition-colors ${
              view === 'timeline' ? 'bg-[var(--kfh-primary-light)] text-[var(--kfh-primary-dark)]' : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
            }`}
          >
            <Icons.Calendar className="w-4 h-4" />
            Timeline
          </button>
        </div>

        <button
          onClick={onAddJob}
          className="px-4 py-2 kfh-btn kfh-btn-primary"
          style={{ paddingTop: '0.5rem', paddingBottom: '0.5rem' }}
        >
          <Icons.Plus className="w-4 h-4" />
          Add Job
        </button>
      </div>
    </div>
  );
};

const JobsTable = ({ jobs, onSelect, onRunNow, onToggle, runningJobs }) => (
  <div className="kfh-card overflow-hidden">
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead>
          <tr className="border-b" style={{ borderColor: 'rgba(0,0,0,0.04)', background: 'var(--surface-off-white)' }}>
            <th className="text-left px-4 py-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Job</th>
            <th className="text-left px-4 py-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Type</th>
            <th className="text-center px-4 py-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Status</th>
            <th className="text-left px-4 py-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Scope</th>
            <th className="text-left px-4 py-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Schedule</th>
            <th className="text-left px-4 py-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Next Run</th>
            <th className="text-left px-4 py-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Last Run</th>
            <th className="text-center px-4 py-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Result</th>
            <th className="text-right px-4 py-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Avg Duration</th>
            <th className="text-center px-4 py-3 text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Actions</th>
          </tr>
        </thead>
        <tbody style={{ borderColor: 'rgba(0,0,0,0.04)' }}>
          {jobs.map(job => {
            const TypeIcon = getJobTypeIcon(job.jobType);
            const isRunning = runningJobs.includes(job.id);

            return (
              <tr key={job.id} className="hover:bg-[var(--surface-off-white)] transition-colors cursor-pointer" onClick={() => onSelect(job)}>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-3">
                    <div className={`w-2 h-2 rounded-full ${job.enabled ? 'bg-[var(--kfh-primary)] pulse-dot' : 'bg-gray-400'}`} />
                    <div>
                      <p className="font-medium text-[var(--text-primary)]">{job.name}</p>
                      <p className="text-xs text-[var(--text-secondary)]">{job.ownerTeam}</p>
                    </div>
                  </div>
                </td>
                <td className="px-4 py-3">
                  <div className={`inline-flex items-center gap-2 px-2 py-1 rounded-md text-xs font-medium ${getJobTypeColor(job.jobType)}`}>
                    <TypeIcon className="w-3.5 h-3.5" />
                    {job.jobType.replace(/([A-Z])/g, ' $1').trim()}
                  </div>
                </td>
                <td className="px-4 py-3 text-center">
                  <button
                    onClick={(e) => { e.stopPropagation(); onToggle(job.id); }}
                    className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${job.enabled ? 'bg-[var(--kfh-primary)]' : 'bg-gray-400'}`}
                    aria-label="Toggle job"
                  >
                    <span className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${job.enabled ? 'translate-x-4' : 'translate-x-0.5'}`} />
                  </button>
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <span className="px-2 py-0.5 rounded text-xs font-medium bg-[var(--kfh-primary-light)] text-[var(--kfh-primary-dark)]">
                      {job.scope.env}
                    </span>
                    {job.scope.domain && (
                      <span className="text-xs text-[var(--text-secondary)]">{job.scope.domain}</span>
                    )}
                  </div>
                </td>
                <td className="px-4 py-3">
                  <code className="text-xs bg-[var(--surface-off-white)] px-2 py-1 rounded" style={{ color: 'var(--kfh-primary-dark)', fontFamily: 'var(--font-mono)' }}>
                    {job.cronOrInterval}
                  </code>
                </td>
                <td className="px-4 py-3">
                  <span className={`text-sm ${job.nextRunAt ? 'text-[var(--kfh-primary)]' : 'text-[var(--text-muted)]'}`}>
                    {formatRelativeTime(job.nextRunAt)}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <span className="text-sm text-[var(--text-secondary)]">{formatRelativeTime(job.lastRunAt)}</span>
                </td>
                <td className="px-4 py-3 text-center">
                  <span className={`inline-flex items-center gap-1.5 px-2 py-1 rounded-md text-xs font-medium border ${getResultBg(job.lastResult)} ${getResultColor(job.lastResult)}`}>
                    {job.lastResult === JobResult.Success && <Icons.Check className="w-3 h-3" />}
                    {job.lastResult === JobResult.Failed && <Icons.X className="w-3 h-3" />}
                    {job.lastResult === JobResult.Skipped && <Icons.Pause className="w-3 h-3" />}
                    {job.lastResult}
                  </span>
                </td>
                <td className="px-4 py-3 text-right">
                  <span className="text-sm text-[var(--text-secondary)]">{formatDuration(job.avgDurationSec)}</span>
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center justify-center gap-2">
                    <button
                      onClick={(e) => { e.stopPropagation(); onRunNow(job.id); }}
                      disabled={isRunning}
                      className={`p-1.5 rounded-lg transition-colors ${
                        isRunning ? 'bg-[var(--kfh-primary-light)] text-[var(--kfh-primary)] cursor-wait' : 'hover:bg-[var(--surface-off-white)] text-[var(--text-secondary)] hover:text-[var(--kfh-primary)]'
                      }`}
                      title="Run Now"
                    >
                      {isRunning ? <Icons.Loader className="w-4 h-4" /> : <Icons.Play className="w-4 h-4" />}
                    </button>
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>

    {jobs.length === 0 && (
      <div className="py-12 text-center">
        <Icons.Calendar className="w-12 h-12 mx-auto text-[var(--text-muted)] mb-4" />
        <p className="text-[var(--text-secondary)]">No jobs found matching your filters</p>
      </div>
    )}
  </div>
);

const TimelineView = ({ jobs, onSelect }) => {
  const hours = useMemo(() => {
    const nowH = new Date();
    return Array.from({ length: 12 }, (_, i) => {
      const hour = new Date(nowH);
      hour.setHours(nowH.getHours() + i, 0, 0, 0);
      return hour;
    });
  }, []);

  const jobsByHour = useMemo(() => {
    const grouped = {};
    hours.forEach(hour => {
      const hourKey = hour.toISOString();
      grouped[hourKey] = jobs.filter(job => {
        if (!job.nextRunAt || !job.enabled) return false;
        const nextRun = new Date(job.nextRunAt);
        return nextRun >= hour && nextRun < new Date(hour.getTime() + 3600000);
      });
    });
    return grouped;
  }, [jobs, hours]);

  return (
    <div className="kfh-card overflow-hidden">
      <div className="p-4 border-b" style={{ borderColor: 'rgba(0,0,0,0.04)' }}>
        <h3 className="font-semibold flex items-center gap-2 text-[var(--text-primary)]">
          <Icons.Clock className="w-5 h-5 text-[var(--kfh-primary)]" />
          Next 12 Hours Timeline
        </h3>
        <p className="text-sm text-[var(--text-secondary)] mt-1">Upcoming scheduled jobs</p>
      </div>

      <div className="overflow-x-auto">
        <div className="min-w-[1000px] p-4">
          <div className="flex gap-4">
            {hours.map((hour, i) => {
              const hourKey = hour.toISOString();
              const hourJobs = jobsByHour[hourKey] || [];
              const isCurrentHour = new Date().getHours() === hour.getHours() && new Date().getDate() === hour.getDate();

              return (
                <div key={i} className="flex-1 min-w-[90px]">
                  <div className={`text-center pb-2 mb-2 border-b ${isCurrentHour ? 'border-[var(--kfh-primary)]' : 'border-[rgba(0,0,0,0.04)]'}`}>
                    <p className={`text-xs font-medium ${isCurrentHour ? 'text-[var(--kfh-primary)]' : 'text-[var(--text-secondary)]'}`}>
                      {hour.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })}
                    </p>
                    {isCurrentHour && (
                      <div className="w-2 h-2 bg-[var(--kfh-primary)] rounded-full mx-auto mt-1 pulse-dot" />
                    )}
                  </div>
                  <div className="space-y-2">
                    {hourJobs.map(job => {
                      const TypeIcon = getJobTypeIcon(job.jobType);
                      return (
                        <button
                          key={job.id}
                          onClick={() => onSelect(job)}
                          className={`w-full p-2 rounded-lg border text-left transition-all hover:scale-[1.02] ${getJobTypeColor(job.jobType)} border-[rgba(0,0,0,0.08)]`}
                        >
                          <div className="flex items-center gap-1.5 mb-1">
                            <TypeIcon className="w-3 h-3" />
                            <span className="text-xs font-medium truncate">{job.name}</span>
                          </div>
                          <p className="text-[10px] opacity-75">
                            {new Date(job.nextRunAt).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })}
                          </p>
                        </button>
                      );
                    })}
                    {hourJobs.length === 0 && (
                      <div className="h-16 border border-dashed rounded-lg flex items-center justify-center" style={{ borderColor: 'rgba(0,0,0,0.08)' }}>
                        <span className="text-xs text-[var(--text-muted)]">-</span>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
};

const JobDrawer = ({ job, onClose, onUpdate, onRunNow, isRunning, activeTab: initialTab = 'overview' }) => {
  const [activeTab, setActiveTab] = useState(initialTab);
  const [editMode, setEditMode] = useState(false);
  const [formData, setFormData] = useState(job);
  const [selectedRun, setSelectedRun] = useState(null);
  const store = useStoreContext();
  const runs = store.jobRuns[job.id] || [];
  const TypeIcon = getJobTypeIcon(job.jobType);

  useEffect(() => { setFormData(job); }, [job]);

  const handleSave = () => {
    onUpdate(job.id, formData);
    setEditMode(false);
  };

  const tabs = [
    { id: 'overview', label: 'Overview', icon: Icons.Calendar },
    { id: 'config', label: 'Configuration', icon: Icons.Cog },
    { id: 'history', label: 'History', icon: Icons.History },
    { id: 'logs', label: 'Logs', icon: Icons.Terminal }
  ];

  const chartData = useMemo(() => {
    return runs.slice(0, 20).reverse().map((run, i) => ({
      name: `Run ${i + 1}`,
      duration: run.endedAt ? Math.floor((new Date(run.endedAt) - new Date(run.startedAt)) / 1000) : 0
    }));
  }, [runs]);

  return (
    <div className="fixed inset-0 z-50 flex">
      <div className="flex-1 bg-black/40" onClick={onClose} />
      <div className="w-full max-w-2xl bg-white border-l overflow-hidden flex flex-col" style={{ borderColor: 'rgba(0,0,0,0.08)' }}>
        <div className="p-4 border-b flex items-start justify-between" style={{ borderColor: 'rgba(0,0,0,0.04)' }}>
          <div className="flex items-start gap-3">
            <div className={`p-2 rounded-lg ${getJobTypeColor(job.jobType)}`}>
              <TypeIcon className="w-5 h-5" />
            </div>
            <div>
              <h2 className="font-semibold text-lg text-[var(--text-primary)]">{job.name}</h2>
              <div className="flex items-center gap-2 mt-1">
                <span className={`text-xs px-2 py-0.5 rounded border ${getResultBg(job.lastResult)} ${getResultColor(job.lastResult)}`}>
                  {job.lastResult}
                </span>
                <span className={`text-xs px-2 py-0.5 rounded ${getPriorityColor(job.priority)}`}>
                  {job.priority}
                </span>
                <span className="text-xs text-[var(--text-secondary)]">{job.ownerTeam}</span>
              </div>
            </div>
          </div>
          <button onClick={onClose} className="p-1 hover:bg-[var(--surface-off-white)] rounded-lg transition-colors">
            <Icons.X className="w-5 h-5 text-[var(--text-secondary)]" />
          </button>
        </div>

        <div className="flex border-b" style={{ borderColor: 'rgba(0,0,0,0.04)' }}>
          {tabs.map(tab => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex-1 px-4 py-3 flex items-center justify-center gap-2 text-sm font-medium transition-colors ${
                activeTab === tab.id
                  ? 'text-[var(--kfh-primary)] border-b-2 border-[var(--kfh-primary)] bg-[var(--kfh-primary-lighter)]'
                  : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
              }`}
            >
              <tab.icon className="w-4 h-4" />
              {tab.label}
            </button>
          ))}
        </div>

        <div className="flex-1 overflow-y-auto p-4">
          {activeTab === 'overview' && (
            <div className="space-y-6">
              <div className="flex gap-3">
                <button
                  onClick={() => onRunNow(job.id)}
                  disabled={isRunning}
                  className={`flex-1 py-3 rounded-lg font-medium flex items-center justify-center gap-2 transition-colors ${
                    isRunning
                      ? 'bg-[var(--kfh-primary-light)] text-[var(--kfh-primary)] cursor-wait'
                      : 'bg-[var(--kfh-primary)] hover:bg-[var(--kfh-primary-dark)] text-white'
                  }`}
                >
                  {isRunning ? <Icons.Loader className="w-5 h-5" /> : <Icons.Play className="w-5 h-5" />}
                  {isRunning ? 'Running...' : 'Run Now'}
                </button>
                <button
                  onClick={() => { setActiveTab('config'); setEditMode(true); }}
                  className="px-4 py-3 border rounded-lg hover:bg-[var(--surface-off-white)] transition-colors flex items-center gap-2"
                  style={{ borderColor: 'rgba(0,0,0,0.08)' }}
                >
                  <Icons.Edit className="w-4 h-4" />
                  Edit
                </button>
              </div>

              {job.enabled && job.nextRunAt && (
                <div className="rounded-lg p-4 border" style={{ background: 'var(--color-success-bg)', borderColor: 'var(--color-success-border)' }}>
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="w-3 h-3 bg-[var(--kfh-primary)] rounded-full pulse-dot" />
                      <div>
                        <p className="text-sm text-[var(--kfh-primary-dark)] font-medium">Next Run</p>
                        <p className="text-xs text-[var(--text-secondary)]">{formatDateTime(job.nextRunAt)}</p>
                      </div>
                    </div>
                    <span className="text-2xl font-bold text-[var(--kfh-primary-dark)]">{formatRelativeTime(job.nextRunAt)}</span>
                  </div>
                </div>
              )}

              {!job.enabled && (
                <div className="rounded-lg p-4 border" style={{ background: 'var(--color-warning-bg)', borderColor: 'var(--color-warning-border)' }}>
                  <div className="flex items-center gap-3">
                    <Icons.Pause className="w-5 h-5 text-[var(--color-warning)]" />
                    <div>
                      <p className="text-sm text-[var(--color-warning)] font-medium">Job Disabled</p>
                      <p className="text-xs text-[var(--text-secondary)]">This job will not run until enabled</p>
                    </div>
                  </div>
                </div>
              )}

              <div className="grid grid-cols-2 gap-4">
                <div className="rounded-lg p-4" style={{ background: 'var(--surface-off-white)' }}>
                  <p className="text-xs text-[var(--text-secondary)] mb-1">Last Duration</p>
                  <p className="text-xl font-bold text-[var(--text-primary)]">{formatDuration(job.lastDurationSec)}</p>
                </div>
                <div className="rounded-lg p-4" style={{ background: 'var(--surface-off-white)' }}>
                  <p className="text-xs text-[var(--text-secondary)] mb-1">Avg Duration</p>
                  <p className="text-xl font-bold text-[var(--text-primary)]">{formatDuration(job.avgDurationSec)}</p>
                </div>
              </div>

              {job.notes && (
                <div className="rounded-lg p-4" style={{ background: 'var(--surface-off-white)' }}>
                  <h4 className="text-sm font-medium mb-2 text-[var(--text-secondary)]">Notes</h4>
                  <p className="text-sm text-[var(--text-primary)]">{job.notes}</p>
                </div>
              )}
            </div>
          )}

          {activeTab === 'config' && (
            editMode
              ? <ConfigForm formData={formData} setFormData={setFormData} onSave={handleSave} onCancel={() => setEditMode(false)} />
              : <div className="space-y-4">
                <button
                  onClick={() => setEditMode(true)}
                  className="w-full py-3 border rounded-lg hover:bg-[var(--surface-off-white)] transition-colors flex items-center justify-center gap-2"
                  style={{ borderColor: 'rgba(0,0,0,0.08)' }}
                >
                  <Icons.Edit className="w-4 h-4" />
                  Edit Configuration
                </button>
                <div className="rounded-lg p-4" style={{ background: 'var(--surface-off-white)' }}>
                  <p className="text-xs text-[var(--text-secondary)] mb-1">Schedule (Cron)</p>
                  <code className="text-sm" style={{ color: 'var(--kfh-primary-dark)', fontFamily: 'var(--font-mono)' }}>{job.cronOrInterval}</code>
                </div>
              </div>
          )}

          {activeTab === 'history' && (
            <div className="space-y-6">
              {chartData.length > 0 && (
                <div className="rounded-lg p-4" style={{ background: 'var(--surface-off-white)' }}>
                  <h4 className="text-sm font-medium mb-4 flex items-center gap-2 text-[var(--text-primary)]">
                    <Icons.TrendingUp className="w-4 h-4 text-[var(--kfh-primary)]" />
                    Duration Trend (Last 20 Runs)
                  </h4>
                  <div className="h-40">
                    <ResponsiveContainer width="100%" height="100%">
                      <AreaChart data={chartData}>
                        <defs>
                          <linearGradient id="durationGrad" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="var(--kfh-primary)" stopOpacity={0.25} />
                            <stop offset="95%" stopColor="var(--kfh-primary)" stopOpacity={0} />
                          </linearGradient>
                        </defs>
                        <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.08)" />
                        <XAxis dataKey="name" tick={{ fill: '#6B7280', fontSize: 10 }} />
                        <YAxis tick={{ fill: '#6B7280', fontSize: 10 }} />
                        <Tooltip contentStyle={{ backgroundColor: '#FFFFFF', border: '1px solid rgba(0,0,0,0.08)', borderRadius: '8px' }} />
                        <Area type="monotone" dataKey="duration" stroke="var(--kfh-primary)" fill="url(#durationGrad)" strokeWidth={2} />
                      </AreaChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              )}

              <div className="space-y-2">
                <h4 className="text-sm font-medium flex items-center gap-2 text-[var(--text-primary)]">
                  <Icons.History className="w-4 h-4 text-[var(--kfh-primary)]" />
                  Run History
                </h4>
                <div className="space-y-2 max-h-96 overflow-y-auto">
                  {runs.map(run => (
                    <button
                      key={run.id}
                      onClick={() => { setSelectedRun(run); setActiveTab('logs'); }}
                      className="w-full rounded-lg p-3 text-left transition-colors hover:bg-[var(--surface-off-white)]"
                      style={{ border: '1px solid rgba(0,0,0,0.04)', background: '#fff' }}
                    >
                      <div className="flex items-center justify-between mb-2">
                        <span className="text-xs px-2 py-0.5 rounded" style={{ background: run.status === RunStatus.Failed ? 'var(--color-danger-bg)' : 'var(--color-success-bg)', color: run.status === RunStatus.Failed ? 'var(--color-danger)' : 'var(--color-success)' }}>
                          {run.status}
                        </span>
                        <span className="text-xs text-[var(--text-secondary)]">{formatDateTime(run.startedAt)}</span>
                      </div>
                      <p className="text-sm text-[var(--text-secondary)] truncate">{run.summary}</p>
                    </button>
                  ))}
                </div>
              </div>
            </div>
          )}

          {activeTab === 'logs' && (
            <div className="space-y-4">
              <select
                value={selectedRun?.id || ''}
                onChange={(e) => setSelectedRun(runs.find(r => r.id === e.target.value))}
                className="w-full px-3 py-2 bg-white border rounded-lg text-sm focus:outline-none focus:ring-2"
                style={{ borderColor: 'rgba(0,0,0,0.08)' }}
              >
                <option value="">Select a run to view logs</option>
                {runs.map(run => (
                  <option key={run.id} value={run.id}>{formatDateTime(run.startedAt)} - {run.status}</option>
                ))}
              </select>

              {selectedRun ? (
                <div className="rounded-lg p-4 font-mono text-sm" style={{ background: '#0b1f1a', color: '#e5e7eb' }}>
                  <div className="space-y-1">
                    {selectedRun.logLines.map((line, i) => (
                      <div key={i} className="flex gap-3">
                        <span className="text-gray-500 select-none">{line.timestamp}</span>
                        <span className={`w-14 ${
                          line.level === 'ERROR' ? 'text-red-300' :
                            line.level === 'WARN' ? 'text-yellow-300' :
                              line.level === 'DEBUG' ? 'text-purple-300' :
                                'text-blue-300'
                        }`}>[{line.level}]</span>
                        <span className="text-gray-200">{line.message}</span>
                      </div>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="text-center py-12 text-[var(--text-secondary)]">
                  <Icons.Terminal className="w-12 h-12 mx-auto mb-3 opacity-50" />
                  <p>Select a run to view logs</p>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

const ConfigForm = ({ formData, setFormData, onSave, onCancel }) => {
  const teams = ['Platform Ops', 'Integration', 'Compliance', 'Management', 'NOC', 'DR Team', 'Security', 'Digital Banking'];
  return (
    <div className="space-y-4">
      <div>
        <label className="block text-xs text-[var(--text-secondary)] mb-1">Job Name</label>
        <input
          type="text"
          value={formData.name}
          onChange={(e) => setFormData({ ...formData, name: e.target.value })}
          className="w-full px-3 py-2 bg-white border rounded-lg text-sm focus:outline-none focus:ring-2"
          style={{ borderColor: 'rgba(0,0,0,0.08)' }}
        />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-xs text-[var(--text-secondary)] mb-1">Job Type</label>
          <select
            value={formData.jobType}
            onChange={(e) => setFormData({ ...formData, jobType: e.target.value })}
            className="w-full px-3 py-2 bg-white border rounded-lg text-sm focus:outline-none focus:ring-2"
            style={{ borderColor: 'rgba(0,0,0,0.08)' }}
          >
            {Object.values(JobType).map(type => (
              <option key={type} value={type}>{type.replace(/([A-Z])/g, ' $1').trim()}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs text-[var(--text-secondary)] mb-1">Priority</label>
          <select
            value={formData.priority}
            onChange={(e) => setFormData({ ...formData, priority: e.target.value })}
            className="w-full px-3 py-2 bg-white border rounded-lg text-sm focus:outline-none focus:ring-2"
            style={{ borderColor: 'rgba(0,0,0,0.08)' }}
          >
            {Object.values(Priority).map(p => (
              <option key={p} value={p}>{p}</option>
            ))}
          </select>
        </div>
      </div>

      <div>
        <label className="block text-xs text-[var(--text-secondary)] mb-1">Cron/Interval</label>
        <input
          type="text"
          value={formData.cronOrInterval}
          onChange={(e) => setFormData({ ...formData, cronOrInterval: e.target.value })}
          className="w-full px-3 py-2 bg-white border rounded-lg text-sm font-mono focus:outline-none focus:ring-2"
          style={{ borderColor: 'rgba(0,0,0,0.08)' }}
        />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-xs text-[var(--text-secondary)] mb-1">Timezone</label>
          <select
            value={formData.timezone}
            onChange={(e) => setFormData({ ...formData, timezone: e.target.value })}
            className="w-full px-3 py-2 bg-white border rounded-lg text-sm focus:outline-none focus:ring-2"
            style={{ borderColor: 'rgba(0,0,0,0.08)' }}
          >
            <option value="Asia/Kuwait">Asia/Kuwait</option>
            <option value="UTC">UTC</option>
            <option value="Asia/Dubai">Asia/Dubai</option>
            <option value="Asia/Riyadh">Asia/Riyadh</option>
          </select>
        </div>
        <div>
          <label className="block text-xs text-[var(--text-secondary)] mb-1">Window (minutes)</label>
          <input
            type="number"
            value={formData.windowMinutes}
            onChange={(e) => setFormData({ ...formData, windowMinutes: parseInt(e.target.value, 10) || 0 })}
            className="w-full px-3 py-2 bg-white border rounded-lg text-sm focus:outline-none focus:ring-2"
            style={{ borderColor: 'rgba(0,0,0,0.08)' }}
          />
        </div>
      </div>

      <div>
        <label className="block text-xs text-[var(--text-secondary)] mb-1">Environment Scope</label>
        <select
          value={formData.scope?.env || Environment.Both}
          onChange={(e) => setFormData({ ...formData, scope: { ...formData.scope, env: e.target.value } })}
          className="w-full px-3 py-2 bg-white border rounded-lg text-sm focus:outline-none focus:ring-2"
          style={{ borderColor: 'rgba(0,0,0,0.08)' }}
        >
          {Object.values(Environment).map(env => (
            <option key={env} value={env}>{env}</option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-xs text-[var(--text-secondary)] mb-1">Owner Team</label>
        <select
          value={formData.ownerTeam}
          onChange={(e) => setFormData({ ...formData, ownerTeam: e.target.value })}
          className="w-full px-3 py-2 bg-white border rounded-lg text-sm focus:outline-none focus:ring-2"
          style={{ borderColor: 'rgba(0,0,0,0.08)' }}
        >
          {teams.map(team => (
            <option key={team} value={team}>{team}</option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-xs text-[var(--text-secondary)] mb-1">Notes</label>
        <textarea
          value={formData.notes || ''}
          onChange={(e) => setFormData({ ...formData, notes: e.target.value })}
          rows={3}
          className="w-full px-3 py-2 bg-white border rounded-lg text-sm focus:outline-none focus:ring-2 resize-none"
          style={{ borderColor: 'rgba(0,0,0,0.08)' }}
        />
      </div>

      <div className="flex items-center gap-3">
        <input
          type="checkbox"
          id="enabled"
          checked={!!formData.enabled}
          onChange={(e) => setFormData({ ...formData, enabled: e.target.checked })}
          className="w-4 h-4 rounded"
        />
        <label htmlFor="enabled" className="text-sm text-[var(--text-primary)]">Enabled</label>
      </div>

      <div className="flex gap-3 pt-4">
        <button onClick={onSave} className="flex-1 kfh-btn kfh-btn-primary">Save Changes</button>
        <button onClick={onCancel} className="kfh-btn kfh-btn-outline">Cancel</button>
      </div>
    </div>
  );
};

const AddJobModal = ({ onClose, onAdd }) => {
  const [formData, setFormData] = useState({
    name: '',
    jobType: JobType.HourlyAnalysis,
    enabled: true,
    timezone: 'Asia/Kuwait',
    cronOrInterval: '0 * * * *',
    windowMinutes: 15,
    scope: { env: Environment.Both },
    nextRunAt: new Date(Date.now() + 3600000).toISOString(),
    lastRunAt: null,
    lastResult: JobResult.Never,
    avgDurationSec: 0,
    lastDurationSec: 0,
    ownerTeam: 'Platform Ops',
    priority: Priority.Med,
    errorRate7d: 0,
    runs7d: 0,
    notes: ''
  });

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!formData.name.trim()) return;
    onAdd(formData);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="relative bg-white border rounded-xl w-full max-w-lg max-h-[90%] overflow-hidden flex flex-col" style={{ borderColor: 'rgba(0,0,0,0.08)' }}>
        <div className="p-4 border-b flex items-center justify-between" style={{ borderColor: 'rgba(0,0,0,0.04)' }}>
          <h2 className="font-semibold text-lg flex items-center gap-2 text-[var(--text-primary)]">
            <Icons.Plus className="w-5 h-5 text-[var(--kfh-primary)]" />
            Add New Job
          </h2>
          <button onClick={onClose} className="p-1 hover:bg-[var(--surface-off-white)] rounded-lg transition-colors">
            <Icons.X className="w-5 h-5 text-[var(--text-secondary)]" />
          </button>
        </div>
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-4">
          <ConfigForm formData={formData} setFormData={setFormData} onSave={handleSubmit} onCancel={onClose} />
        </form>
      </div>
    </div>
  );
};

const SchedulesPage = () => {
  const store = useStoreContext();
  const [search, setSearch] = useState('');
  const [filters, setFilters] = useState({ jobType: '', enabled: '', lastResult: '', ownerTeam: '', env: '' });
  const [view, setView] = useState('list');
  const [selectedJob, setSelectedJob] = useState(null);
  const [selectedTab, setSelectedTab] = useState('overview');
  const [showAddModal, setShowAddModal] = useState(false);
  const [runningJobs, setRunningJobs] = useState([]);

  const filteredJobs = useMemo(() => {
    return store.jobs.filter(job => {
      if (search && !job.name.toLowerCase().includes(search.toLowerCase())) return false;
      if (filters.jobType && job.jobType !== filters.jobType) return false;
      if (filters.enabled && String(job.enabled) !== filters.enabled) return false;
      if (filters.lastResult && job.lastResult !== filters.lastResult) return false;
      if (filters.ownerTeam && job.ownerTeam !== filters.ownerTeam) return false;
      if (filters.env && job.scope.env !== filters.env && job.scope.env !== Environment.Both) return false;
      return true;
    });
  }, [store.jobs, search, filters]);

  const handleRunNow = async (jobId) => {
    setRunningJobs(prev => [...prev, jobId]);
    await store.runJobNow(jobId);
    setRunningJobs(prev => prev.filter(id => id !== jobId));
  };

  const handleSelectJob = (job, tab = 'overview') => {
    setSelectedJob(job);
    setSelectedTab(tab);
  };

  return (
    <div className="h-full flex flex-col" style={{ background: 'var(--surface-bg)' }}>
      <div className="p-6 border-b" style={{ background: 'var(--surface-card)', borderColor: 'rgba(0,0,0,0.04)' }}>

        <KpiStrip jobs={store.jobs} jobRuns={store.jobRuns} />
      </div>

      <div className="flex-1 overflow-auto p-6 space-y-6">
        <FiltersBar
          search={search}
          setSearch={setSearch}
          filters={filters}
          setFilters={setFilters}
          view={view}
          setView={setView}
          onAddJob={() => setShowAddModal(true)}
        />

        {view === 'list' ? (
          <JobsTable
            jobs={filteredJobs}
            onSelect={handleSelectJob}
            onRunNow={handleRunNow}
            onToggle={store.toggleJob}
            runningJobs={runningJobs}
          />
        ) : (
          <TimelineView jobs={filteredJobs} onSelect={handleSelectJob} />
        )}
      </div>

      {selectedJob && (
        <JobDrawer
          job={selectedJob}
          onClose={() => setSelectedJob(null)}
          onUpdate={store.updateJob}
          onRunNow={handleRunNow}
          isRunning={runningJobs.includes(selectedJob.id)}
          activeTab={selectedTab}
        />
      )}

      {showAddModal && (
        <AddJobModal onClose={() => setShowAddModal(false)} onAdd={store.addJob} />
      )}

      <div className="fixed bottom-4 left-1/2 -translate-x-1/2 z-50 space-y-2">
        {store.toasts.map(toast => (
          <Toast key={toast.id} toast={toast} />
        ))}
      </div>
    </div>
  );
};

const App = () => {
  const storeValue = useStore();
  return (
    <StoreContext.Provider value={storeValue}>
      <SchedulesPage />
    </StoreContext.Provider>
  );
};

const mountEl = document.getElementById('page-root') || document.getElementById('content-area') || document.getElementById('root');
if (mountEl) {
  // If the router navigates away and back, ensure we don't mount twice on the same container.
  try {
    if (mountEl._reactRoot) {
      mountEl._reactRoot.unmount();
    }
  } catch (e) {}

  const root = ReactDOM.createRoot(mountEl);
  mountEl._reactRoot = root;
  root.render(<App />);
}

})(); // End IIFE

