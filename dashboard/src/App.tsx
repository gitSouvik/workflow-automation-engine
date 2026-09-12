import { useState, useEffect, useCallback, useRef, useMemo } from 'react'
import axios from 'axios'
import {
  AreaChart, Area,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend
} from 'recharts'
import ApiDocs from './ApiDocs'
import DagGraph from './components/DagGraph'
import './index.css'

const API_BASE = 'http://localhost:8080'

// ---- Types ----
interface MetricsSnapshot {
  runningCount: number
  completedCount: number
  rejectedCount: number
  failedCount: number
  totalStepsCompleted: number
  p50Ms: number
  p99Ms: number
  timestamp: string
  latencyHistory: LatencyPoint[]
}

interface LatencyPoint {
  timestamp: string
  p50Ms: number
  p99Ms: number
}

interface WorkflowInstance {
  id: string
  title: string
  definitionName: string
  initiatorId: string
  status: 'RUNNING' | 'COMPLETED' | 'REJECTED' | 'FAILED'
  createdAt: string
  completedAt?: string
  taskInstances: TaskInstance[]
}

interface TaskInstance {
  id: string
  taskKey: string
  taskName: string
  status: 'PENDING' | 'READY' | 'IN_PROGRESS' | 'APPROVED' | 'REJECTED' | 'SKIPPED'
  assigneeRole: string
  startedAt?: string
  completedAt?: string;
  stepDurationMs?: number;
}

// ---- Helpers ----
function formatMs(ms: number): string {
  if (ms === 0) return '—'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function timeAgo(isoStr: string): string {
  const diff = Math.max(0, Date.now() - new Date(isoStr).getTime())
  if (diff < 60000) return `${Math.floor(diff / 1000)}s ago`
  if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`
  return `${Math.floor(diff / 3600000)}h ago`
}

function StatusBadge({ status }: { status: string }) {
  const cls = `status-badge badge-${status.toLowerCase()}`
  return (
    <span className={cls}>
      <span className="status-badge-dot" />
      {status}
    </span>
  )
}

function TaskPipeline({ tasks }: { tasks: TaskInstance[] }) {
  if (!tasks || tasks.length === 0) {
    return <span style={{ color: 'var(--text-muted)', fontSize: 11 }}>—</span>
  }
  return (
    <div className="task-pipeline" title={tasks.map(t => `${t.taskKey}: ${t.status}`).join(' \n ')}>
      {tasks.map(t => (
        <div
          key={t.id || t.taskKey}
          className={`task-pip pip-${t.status.toLowerCase()}`}
          title={`${t.taskName || t.taskKey} (${t.assigneeRole}): ${t.status}`}
        />
      ))}
    </div>
  )
}

// Clean SVG Graph Icon
function GraphIcon({ size = 13 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="18" cy="5" r="3"></circle>
      <circle cx="6" cy="12" r="3"></circle>
      <circle cx="18" cy="19" r="3"></circle>
      <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"></line>
      <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"></line>
    </svg>
  )
}

// ---- Chart Tooltip ----
const LatencyTooltip = ({ active, payload, label }: any) => {
  if (!active || !payload?.length) return null
  return (
    <div className="chart-tooltip-box">
      <div style={{ color: 'var(--text-secondary)', marginBottom: 6, fontSize: 11 }}>
        {new Date(label).toLocaleTimeString()}
      </div>
      {payload.map((p: any) => (
        <div key={p.name} style={{ color: p.color, margin: '3px 0', display: 'flex', justifyContent: 'space-between', gap: 12 }}>
          <span>{p.name.toUpperCase()}:</span>
          <strong style={{ fontFamily: 'var(--font-mono)' }}>{p.value}ms</strong>
        </div>
      ))}
    </div>
  )
}

// ---- MAIN APP ----
export default function App() {
  const [metrics, setMetrics] = useState<MetricsSnapshot | null>(null)
  const [latencyHistory, setLatencyHistory] = useState<Array<{t: string, p50: number, p99: number}>>([])
  const [instances, setInstances] = useState<WorkflowInstance[]>([])
  const [selectedInstance, setSelectedInstance] = useState<WorkflowInstance | null>(null)
  const [modalInstance, setModalInstance] = useState<WorkflowInstance | null>(null)
  const [currentView, setCurrentView] = useState<'dashboard' | 'dag-graph' | 'api-docs'>('dashboard')
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'RUNNING' | 'COMPLETED' | 'REJECTED'>('ALL')
  const [copiedId, setCopiedId] = useState<string | null>(null)
  const sseRef = useRef<EventSource | null>(null)

  // SSE metrics stream
  useEffect(() => {
    const sse = new EventSource(`${API_BASE}/api/metrics/stream`)
    sseRef.current = sse

    sse.addEventListener('metrics', (e: MessageEvent) => {
      const data: MetricsSnapshot = JSON.parse(e.data)
      setMetrics(data)

      setLatencyHistory(prev => {
        const point = { t: data.timestamp, p50: data.p50Ms, p99: data.p99Ms }
        const next = [...prev, point].slice(-30)
        return next
      })
    })

    sse.onerror = () => {}

    return () => { sse.close() }
  }, [])

  // Poll instances every 3s
  const fetchInstances = useCallback(async () => {
    try {
      const res = await axios.get<WorkflowInstance[]>(`${API_BASE}/api/instances`)
      const sorted = res.data.sort((a, b) => {
        if (a.status === 'RUNNING' && b.status !== 'RUNNING') return -1
        if (b.status === 'RUNNING' && a.status !== 'RUNNING') return 1
        return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      })
      setInstances(sorted)

      setSelectedInstance(prev => {
        if (!prev && sorted.length > 0) return sorted[0]
        if (prev) {
          const fresh = sorted.find(inst => inst.id === prev.id)
          if (fresh) return fresh
        }
        return prev
      })
    } catch { /* server starting up */ }
  }, [])

  useEffect(() => {
    fetchInstances()
    const interval = setInterval(fetchInstances, 3000)
    return () => clearInterval(interval)
  }, [fetchInstances])

  // Select instance for DAG Graph visualization
  const handleSelectInstance = useCallback(async (inst: WorkflowInstance) => {
    try {
      const res = await axios.get<TaskInstance[]>(`${API_BASE}/api/instances/${inst.id}/tasks`)
      setSelectedInstance({ ...inst, taskInstances: res.data })
    } catch {
      setSelectedInstance(inst)
    }
  }, [])

  // Select instance to display in DAG Graph
  const handleViewDagGraph = useCallback(async (e: React.MouseEvent, inst: WorkflowInstance) => {
    e.stopPropagation()
    await handleSelectInstance(inst)
  }, [handleSelectInstance])

  // Open modal inspection
  const handleOpenModal = useCallback(async (e: React.MouseEvent, inst: WorkflowInstance) => {
    e.stopPropagation()
    try {
      const res = await axios.get<TaskInstance[]>(`${API_BASE}/api/instances/${inst.id}/tasks`)
      setModalInstance({ ...inst, taskInstances: res.data })
    } catch {
      setModalInstance(inst)
    }
  }, [])

  const handleCopyId = (e: React.MouseEvent, id: string) => {
    e.stopPropagation()
    navigator.clipboard.writeText(id)
    setCopiedId(id)
    setTimeout(() => setCopiedId(null), 1800)
  }

  // Filtered instances
  const filteredInstances = useMemo(() => {
    return instances.filter(inst => {
      const matchesFilter = statusFilter === 'ALL' || inst.status === statusFilter
      if (!matchesFilter) return false
      if (!searchQuery.trim()) return true
      const q = searchQuery.toLowerCase()
      return (
        inst.title.toLowerCase().includes(q) ||
        inst.definitionName.toLowerCase().includes(q) ||
        inst.initiatorId.toLowerCase().includes(q) ||
        inst.id.toLowerCase().includes(q)
      )
    })
  }, [instances, statusFilter, searchQuery])

  const runningCount = metrics?.runningCount ?? 0
  const completedCount = metrics?.completedCount ?? 0
  const rejectedCount = metrics?.rejectedCount ?? 0
  const totalSteps = metrics?.totalStepsCompleted ?? 0
  const p50 = metrics?.p50Ms ?? 0
  const p99 = metrics?.p99Ms ?? 0

  return (
    <div className="dashboard-layout">
      {/* ---- PROFESSIONAL HEADER WITH DAG GRAPH TAB ---- */}
      <header className="dashboard-header">
        <div className="header-brand">
          <div className="header-logo">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon>
            </svg>
          </div>
          <div className="header-titles">
            <span className="header-title">Workflow Engine</span>
            <span className="header-badge">v2.4-prod</span>
          </div>
        </div>

        {/* Navigation Switcher with Graph Symbol */}
        <div className="nav-segmented">
          <button
            className={`nav-tab ${currentView === 'dashboard' ? 'active' : ''}`}
            onClick={() => setCurrentView('dashboard')}
          >
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <rect x="3" y="3" width="7" height="7"></rect>
              <rect x="14" y="3" width="7" height="7"></rect>
              <rect x="14" y="14" width="7" height="7"></rect>
              <rect x="3" y="14" width="7" height="7"></rect>
            </svg>
            Dashboard
          </button>

          <button
            className={`nav-tab ${currentView === 'dag-graph' ? 'active' : ''}`}
            onClick={() => setCurrentView('dag-graph')}
          >
            <GraphIcon size={13} />
            Graph
          </button>

          <button
            className={`nav-tab ${currentView === 'api-docs' ? 'active' : ''}`}
            onClick={() => setCurrentView('api-docs')}
          >
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path>
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path>
            </svg>
            Docs
          </button>
        </div>
      </header>

      {/* ---- MAIN CONTENT ---- */}
      <main className="dashboard-content">
        {currentView === 'dag-graph' ? (
          /* DEDICATED 2D DAG GRAPH PAGE */
          <DagGraph
            instances={instances}
            selectedInstance={selectedInstance}
            onSelectInstance={handleSelectInstance}
            onBackToDashboard={() => setCurrentView('dashboard')}
          />
        ) : currentView === 'api-docs' ? (
          <ApiDocs />
        ) : (
          /* DASHBOARD PAGE */
          <>
            {/* ROW 1: 4 Stat Metrics in a single horizontal strip */}
            <div className="stats-grid">
              <div className="stat-card">
                <div className="stat-header">
                  <span className="stat-label">Running</span>
                  <div className="stat-icon-wrapper" style={{ color: 'var(--status-running)' }}>
                    <svg width="11" height="11" viewBox="0 0 24 24" fill="currentColor">
                      <polygon points="6 4 20 12 6 20 6 4"></polygon>
                    </svg>
                  </div>
                </div>
                <div className="stat-value">{runningCount.toLocaleString()}</div>
                <div className="stat-sublabel">
                  <GraphIcon size={10} />
                  <span>Active DAG instances</span>
                </div>
              </div>

              <div className="stat-card">
                <div className="stat-header">
                  <span className="stat-label">Completed</span>
                  <div className="stat-icon-wrapper" style={{ color: 'var(--status-completed)' }}>
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                      <polyline points="20 6 9 17 4 12"></polyline>
                    </svg>
                  </div>
                </div>
                <div className="stat-value">{completedCount.toLocaleString()}</div>
                <div className="stat-sublabel">
                  <GraphIcon size={10} />
                  <span>All tasks approved</span>
                </div>
              </div>

              <div className="stat-card">
                <div className="stat-header">
                  <span className="stat-label">Rejected</span>
                  <div className="stat-icon-wrapper" style={{ color: 'var(--status-rejected)' }}>
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                      <line x1="18" y1="6" x2="6" y2="18"></line>
                      <line x1="6" y1="6" x2="18" y2="18"></line>
                    </svg>
                  </div>
                </div>
                <div className="stat-value">{rejectedCount.toLocaleString()}</div>
                <div className="stat-sublabel">
                  <GraphIcon size={10} />
                  <span>Cascade terminated</span>
                </div>
              </div>

              <div className="stat-card">
                <div className="stat-header">
                  <span className="stat-label">p50 Latency</span>
                  <div className="stat-icon-wrapper" style={{ color: 'var(--accent-purple)' }}>
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                      <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon>
                    </svg>
                  </div>
                </div>
                <div className="stat-value">{formatMs(p50)}</div>
                <div className="stat-sublabel">p99: {formatMs(p99)} · {totalSteps.toLocaleString()} steps</div>
              </div>
            </div>

            {/* ROW 2: Step Latency Over Time (single full-width row) */}
            <div className="glass-card">
              <div className="card-header">
                <span className="card-title">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M22 12h-4l-3 9L9 3l-3 9H2"/>
                  </svg>
                  Step Latency Over Time
                </span>
                <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Real-time SSE (Last 30 snapshots)</span>
              </div>
              <div className="card-body" style={{ height: 210 }}>
                {latencyHistory.length === 0 ? (
                  <div className="loading-state" style={{ height: 160 }}>
                    <div className="spinner" />
                    <span>Awaiting metrics stream...</span>
                  </div>
                ) : (
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={latencyHistory} margin={{ top: 8, right: 8, left: -10, bottom: 0 }}>
                      <defs>
                        <linearGradient id="p50g" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor="#58a6ff" stopOpacity={0.25}/>
                          <stop offset="95%" stopColor="#58a6ff" stopOpacity={0.0}/>
                        </linearGradient>
                        <linearGradient id="p99g" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor="#a371f7" stopOpacity={0.2}/>
                          <stop offset="95%" stopColor="#a371f7" stopOpacity={0.0}/>
                        </linearGradient>
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="t" hide />
                      <YAxis tick={{ fill: '#656c76', fontSize: 11 }} unit="ms" width={45} axisLine={false} tickLine={false} />
                      <Tooltip content={<LatencyTooltip />} />
                      <Area type="monotone" dataKey="p50" name="p50" stroke="#58a6ff" fill="url(#p50g)" strokeWidth={1.8} dot={false} />
                      <Area type="monotone" dataKey="p99" name="p99" stroke="#a371f7" fill="url(#p99g)" strokeWidth={1.8} dot={false} />
                      <Legend wrapperStyle={{ fontSize: 11, color: '#8b949e', paddingTop: 4 }} />
                    </AreaChart>
                  </ResponsiveContainer>
                )}
              </div>
            </div>

            {/* ROW 3: In-Flight Instances Table (single full-width row with GRAPH SYMBOL on each) */}
            <div className="glass-card">
              <div className="table-toolbar">
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <span className="card-title">
                    In-Flight Instances
                    <span style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 400 }}>
                      ({filteredInstances.length} of {instances.length})
                    </span>
                  </span>
                  
                  <div className="table-search-box">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#656c76" strokeWidth="2">
                      <circle cx="11" cy="11" r="8"></circle>
                      <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                    </svg>
                    <input
                      type="text"
                      className="table-search-input"
                      placeholder="Filter instances..."
                      value={searchQuery}
                      onChange={e => setSearchQuery(e.target.value)}
                    />
                  </div>
                </div>

                <div className="filter-pills">
                  {(['ALL', 'RUNNING', 'COMPLETED', 'REJECTED'] as const).map(st => (
                    <button
                      key={st}
                      className={`filter-pill ${statusFilter === st ? 'active' : ''}`}
                      onClick={() => setStatusFilter(st)}
                    >
                      {st}
                    </button>
                  ))}
                </div>
              </div>

              <div className="table-container" style={{ maxHeight: 440 }}>
                {filteredInstances.length === 0 ? (
                  <div className="empty-state">
                    <div className="empty-state-icon">
                      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon>
                      </svg>
                    </div>
                    <div className="empty-state-text">
                      {instances.length === 0 ? 'No workflow instances detected yet' : 'No matching instances found'}
                    </div>
                  </div>
                ) : (
                  <table className="instances-table">
                    <thead>
                      <tr>
                        <th>Title & ID</th>
                        <th>Definition</th>
                        <th>Status</th>
                        <th>Tasks</th>
                        <th>Created</th>
                        <th style={{ textAlign: 'center' }}>Graph</th>
                        <th>Details</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredInstances.slice(0, 100).map(inst => (
                        <tr
                          key={inst.id}
                          onClick={() => handleSelectInstance(inst)}
                          style={{ cursor: 'pointer' }}
                        >
                          <td>
                            <div className="instance-title">
                              {inst.title}
                            </div>
                            <div style={{ marginTop: 2 }}>
                              <span
                                className="instance-id-badge"
                                onClick={(e) => handleCopyId(e, inst.id)}
                                title="Click to copy full ID"
                              >
                                {copiedId === inst.id ? 'Copied' : `${inst.id.slice(0, 8)}…`}
                              </span>
                            </div>
                          </td>
                          <td style={{ color: 'var(--text-secondary)' }}>{inst.definitionName}</td>
                          <td><StatusBadge status={inst.status} /></td>
                          <td>
                            <TaskPipeline tasks={inst.taskInstances ?? []} />
                          </td>
                          <td style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                            {timeAgo(inst.createdAt)}
                          </td>
                          {/* GRAPH SYMBOL BUTTON ON EACH ROW */}
                          <td style={{ textAlign: 'center' }}>
                            <button
                              className="graph-action-btn"
                              onClick={(e) => handleViewDagGraph(e, inst)}
                              title={`View Graph for ${inst.title}`}
                            >
                              <GraphIcon size={12} />
                              <span>Graph</span>
                            </button>
                          </td>
                          <td>
                            <button
                              className="copy-btn"
                              onClick={(e) => handleOpenModal(e, inst)}
                              title="Open task log breakdown"
                            >
                              Inspect
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
          </>
      )}

        {/* INSTANCE DETAIL MODAL */}
        {modalInstance && (
          <div className="modal-overlay" onClick={() => setModalInstance(null)}>
            <div className="modal-card" onClick={e => e.stopPropagation()}>
              <div className="modal-header">
                <div>
                  <div style={{ fontWeight: 600, fontSize: 16, color: 'var(--text-primary)', marginBottom: 4 }}>
                    {modalInstance.title}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text-muted)', display: 'flex', gap: 8, alignItems: 'center' }}>
                    <span>{modalInstance.definitionName}</span>
                    <span>·</span>
                    <span>{modalInstance.initiatorId}</span>
                    <span>·</span>
                    <span
                      className="instance-id-badge"
                      onClick={(e) => handleCopyId(e, modalInstance.id)}
                    >
                      {copiedId === modalInstance.id ? 'Copied' : modalInstance.id}
                    </span>
                  </div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <button
                    className="graph-action-btn"
                    onClick={() => {
                      setSelectedInstance(modalInstance);
                      setModalInstance(null);
                      setCurrentView('dag-graph');
                    }}
                    style={{ background: 'rgba(88, 166, 255, 0.15)', borderColor: 'rgba(88, 166, 255, 0.4)', color: 'var(--accent-blue)' }}
                  >
                    <GraphIcon size={12} />
                    <span>View in Graph</span>
                  </button>
                  <StatusBadge status={modalInstance.status} />
                  <button
                    className="modal-close-btn"
                    onClick={() => setModalInstance(null)}
                    title="Close modal"
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                      <line x1="18" y1="6" x2="6" y2="18"></line>
                      <line x1="6" y1="6" x2="18" y2="18"></line>
                    </svg>
                  </button>
                </div>
              </div>

              <div className="modal-body">
                <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 10 }}>
                  Task Execution Pipeline
                </div>
                <table className="instances-table">
                  <thead>
                    <tr>
                      <th>Task Name</th>
                      <th>Role</th>
                      <th>Status</th>
                      <th>Started</th>
                      <th>Completed</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(modalInstance.taskInstances ?? []).map(t => (
                      <tr key={t.id || t.taskKey}>
                        <td style={{ fontWeight: 500, color: 'var(--text-primary)' }}>{t.taskName || t.taskKey}</td>
                        <td>
                          <span style={{ fontSize: 11, background: 'rgba(240, 246, 252, 0.05)', padding: '2px 6px', borderRadius: 4, border: '1px solid var(--border-muted)', color: 'var(--text-secondary)' }}>
                            {t.assigneeRole}
                          </span>
                        </td>
                        <td><StatusBadge status={t.status} /></td>
                        <td style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                          {t.startedAt ? timeAgo(t.startedAt) : '—'}
                        </td>
                        <td style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                          {t.completedAt ? timeAgo(t.completedAt) : '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="modal-footer">
                <button className="btn-secondary" onClick={() => setModalInstance(null)}>
                  Close
                </button>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  )
}
