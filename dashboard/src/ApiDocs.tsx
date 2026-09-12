import React, { useState, useEffect, useMemo } from 'react';
import axios from 'axios';

const API_BASE = 'http://localhost:8080';

type DocSection = {
  type: 'article' | 'api';
  id: string;
  title: string;
  category: string;
  content?: React.ReactNode;
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  path?: string;
  description?: string;
  defaultParams?: string;
  defaultBody?: string;
  response?: string;
};

const DOCS: DocSection[] = [
  {
    type: 'article',
    id: 'intro',
    category: 'Guides & Concepts',
    title: 'Engine Architecture & DAGs',
    content: (
      <div>
        <p className="docs-description">
          The <strong>Workflow Engine</strong> is an enterprise-grade orchestration platform that executes business approval pipelines as <strong>Directed Acyclic Graphs (DAGs)</strong>.
        </p>

        <div className="docs-section-title" style={{ marginTop: 24, marginBottom: 12 }}>What it does</div>
        <p className="docs-description">
          The Workflow Automation Engine allows teams to define, execute, and monitor distributed business processes (workflows). Instead of hardcoding linear chains, users can define generic DAGs representing dependencies between tasks. The engine calculates "readiness" at runtime, only waking up downstream tasks once their dependencies are resolved. It supports conditional branching via Spring Expression Language (SpEL) on edges, N-way fan-outs, and N-way joins, and robust optimistic concurrency control for safe multi-actor approvals.
        </p>

        {/* Visual DAG Flow Diagram */}
        <div style={{
          background: 'var(--bg-surface)',
          border: '1px solid var(--border-default)',
          borderRadius: 'var(--radius-md)',
          padding: '20px',
          marginBottom: '24px'
        }}>
          <div style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.06em', color: 'var(--text-muted)', marginBottom: 14, fontWeight: 600 }}>
            DAG Topology Pipeline Demonstration
          </div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
            <div style={{ background: '#0d1117', border: '1px solid var(--border-default)', padding: '10px 14px', borderRadius: 6, textAlign: 'center', minWidth: 120 }}>
              <div style={{ fontSize: 11, color: 'var(--accent-blue)', fontWeight: 600 }}>TASK 1</div>
              <div style={{ fontSize: 13, fontWeight: 500 }}>Initial Submission</div>
            </div>

            <div style={{ color: 'var(--text-muted)', fontSize: 18 }}>➔</div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <div style={{ background: '#0d1117', border: '1px solid #3fb95040', padding: '8px 12px', borderRadius: 6, minWidth: 140 }}>
                <div style={{ fontSize: 10, color: 'var(--status-completed)', fontWeight: 600 }}>PARALLEL BRANCH A</div>
                <div style={{ fontSize: 12, fontWeight: 500 }}>Finance Review</div>
              </div>
              <div style={{ background: '#0d1117', border: '1px solid #3fb95040', padding: '8px 12px', borderRadius: 6, minWidth: 140 }}>
                <div style={{ fontSize: 10, color: 'var(--status-completed)', fontWeight: 600 }}>PARALLEL BRANCH B</div>
                <div style={{ fontSize: 12, fontWeight: 500 }}>Security Review</div>
              </div>
            </div>

            <div style={{ color: 'var(--text-muted)', fontSize: 18 }}>➔</div>

            <div style={{ background: '#0d1117', border: '1px solid var(--border-default)', padding: '10px 14px', borderRadius: 6, textAlign: 'center', minWidth: 120 }}>
              <div style={{ fontSize: 11, color: 'var(--accent-purple)', fontWeight: 600 }}>JOIN GATE</div>
              <div style={{ fontSize: 13, fontWeight: 500 }}>Executive Approval</div>
            </div>

            <div style={{ color: 'var(--text-muted)', fontSize: 18 }}>➔</div>

            <div style={{ background: '#0d1117', border: '1px solid rgba(63, 185, 80, 0.4)', padding: '10px 14px', borderRadius: 6, textAlign: 'center', minWidth: 100 }}>
              <div style={{ fontSize: 11, color: 'var(--status-completed)', fontWeight: 600 }}>DONE</div>
              <div style={{ fontSize: 13, fontWeight: 500 }}>Workflow Finalized</div>
            </div>
          </div>
        </div>

        <div className="guide-alert note">
          <div style={{ color: 'var(--accent-blue)', display: 'flex', alignItems: 'center' }}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10"></circle>
              <line x1="12" y1="16" x2="12" y2="12"></line>
              <line x1="12" y1="8" x2="12.01" y2="8"></line>
            </svg>
          </div>
          <div>
            <strong>Non-linear Execution:</strong> Real-world processes require parallel branches (e.g. Finance and Security auditing concurrently) that join before final executive sign-off. The engine calculates step readiness automatically via topological dependencies.
          </div>
        </div>

        <div className="docs-section-title" style={{ marginTop: 24 }}>Core Abstractions</div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 12, marginTop: 12 }}>
          <div style={{ background: 'var(--bg-surface)', padding: '14px 16px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-default)' }}>
            <div style={{ fontWeight: 600, color: 'var(--text-primary)', marginBottom: 4 }}>Workflow Definition</div>
            <div style={{ fontSize: 12.5, color: 'var(--text-secondary)' }}>A declarative blueprint of tasks, roles, and dependency edges validated for cycle freedom via Kahn's algorithm.</div>
          </div>
          <div style={{ background: 'var(--bg-surface)', padding: '14px 16px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-default)' }}>
            <div style={{ fontWeight: 600, color: 'var(--text-primary)', marginBottom: 4 }}>Workflow Instance</div>
            <div style={{ fontSize: 12.5, color: 'var(--text-secondary)' }}>A running execution instance holding runtime state, initiator ID, and database optimistic lock versioning.</div>
          </div>
          <div style={{ background: 'var(--bg-surface)', padding: '14px 16px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-default)' }}>
            <div style={{ fontWeight: 600, color: 'var(--text-primary)', marginBottom: 4 }}>Task Instance</div>
            <div style={{ fontSize: 12.5, color: 'var(--text-secondary)' }}>A specific step in the DAG. Progresses through <code>PENDING to READY to IN_PROGRESS to APPROVED/REJECTED</code>.</div>
          </div>
        </div>

        <div className="docs-section-title" style={{ marginTop: 32, marginBottom: 12 }}>Getting Started</div>
        <div style={{ background: '#0d1117', border: '1px solid var(--border-default)', borderRadius: 'var(--radius-md)', padding: '16px' }}>
          <div style={{ fontWeight: 600, color: 'var(--accent-blue)', marginBottom: 8, fontSize: 13 }}>1. Run the Backend (Spring Boot)</div>
          <pre style={{ margin: 0, padding: '10px', background: '#161b22', borderRadius: '4px', fontSize: 12, color: 'var(--text-secondary)' }}>
<code>cd backend
mvn clean package -DskipTests
mvn spring-boot:run</code>
          </pre>
          
          <div style={{ fontWeight: 600, color: 'var(--accent-purple)', marginTop: 20, marginBottom: 8, fontSize: 13 }}>2. Run the Dashboard (React/Vite)</div>
          <pre style={{ margin: 0, padding: '10px', background: '#161b22', borderRadius: '4px', fontSize: 12, color: 'var(--text-secondary)' }}>
<code>cd dashboard
npm install
npm run dev</code>
          </pre>
          <div style={{ marginTop: 12, fontSize: 12, color: 'var(--text-muted)' }}>Navigate to <code>http://localhost:5173</code> to access the interactive dashboard.</div>
        </div>
      </div>
    )
  },
  {
    type: 'article',
    id: 'concurrency',
    category: 'Guides & Concepts',
    title: 'Optimistic Concurrency & Crash Recovery',
    content: (
      <div>
        <p className="docs-description">
          Enterprise workflow systems must maintain consistency even when hundreds of approvers act simultaneously or when the host server experiences an abrupt reboot.
        </p>

        <div className="docs-section-title">1. Optimistic Concurrency Control (OCC)</div>
        <p className="docs-description">
          The <code>WorkflowInstance</code> entity is annotated with JPA <code>@Version</code>. When multiple approvers submit decisions at the exact same millisecond:
        </p>
        <div className="code-block dark" style={{ marginBottom: 16 }}>
          <pre><code>{`// Spring Boot JPA Entity Protection
@Entity
@Table(name = "workflow_instances")
public class WorkflowInstance {
    @Id
    private UUID id;

    @Version
    private Long version; // Incremented atomically on each state transition
}`}</code></pre>
        </div>
        <p className="docs-description">
          If two transactions read version <code>3</code> concurrently, the first commit succeeds (updating version to <code>4</code>). The second attempt fails immediately with an <code>OptimisticLockingFailureException</code>, preventing corrupt or duplicate DAG transitions.
        </p>

        <div className="docs-section-title" style={{ marginTop: 24 }}>2. Boot Crash Recovery Service</div>
        <p className="docs-description">
          If the application server crashes mid-execution while tasks are marked as <code>IN_PROGRESS</code>, the <code>BootRecoveryService</code> activates during Spring Boot startup via <code>ApplicationReadyEvent</code>:
        </p>
        <div className="code-block dark" style={{ marginBottom: 16 }}>
          <pre><code>{`@EventListener(ApplicationReadyEvent.class)
public void recoverIncompleteTasksOnStartup() {
    List<TaskInstance> orphaned = taskRepo.findByStatus(TaskStatus.IN_PROGRESS);
    for (TaskInstance task : orphaned) {
        log.warn("Recovering orphaned task [{}] back to READY", task.getId());
        task.setStatus(TaskStatus.READY);
        taskRepo.save(task);
    }
}`}</code></pre>
        </div>

        <div className="guide-alert tip">
          <div style={{ color: 'var(--status-completed)', display: 'flex', alignItems: 'center' }}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
            </svg>
          </div>
          <div>
            <strong>Self-Healing Guarantee:</strong> Tasks are safely reset to <code>READY</code> so approvers can re-claim them without manual DBA intervention or lost states.
          </div>
        </div>
      </div>
    )
  },
  {
    type: 'api',
    id: 'list-defs',
    category: 'REST API',
    title: 'List Definitions',
    method: 'GET',
    path: '/api/definitions',
    description: 'Retrieves all registered workflow definitions (DAG blueprints) with task dependencies.',
    response: `[
  {
    "id": "9227620e-5b5c-4a9f-b67a-77cb7fd6893b",
    "name": "Load Test Workflow",
    "description": "3-step approval pipeline: Manager -> Finance -> Director",
    "createdAt": "2026-09-12T19:35:00Z"
  }
]`
  },
  {
    type: 'api',
    id: 'create-def',
    category: 'REST API',
    title: 'Create Definition',
    method: 'POST',
    path: '/api/definitions',
    description: 'Registers a new workflow definition DAG. Validates acyclicity using Kahn\'s algorithm.',
    defaultBody: `{\n  "name": "Vendor Contract Review",\n  "description": "Multi-party approval flow",\n  "tasks": [\n    { "taskKey": "legal-audit", "name": "Legal Review", "assigneeRole": "LEGAL" },\n    { "taskKey": "vp-approval", "name": "VP Sign-off", "assigneeRole": "VP" }\n  ],\n  "dependencies": [\n    { "fromTaskKey": "legal-audit", "toTaskKey": "vp-approval" }\n  ]\n}`,
    response: `{\n  "id": "b78b0cb1-6789-49e2-bbdf-6b83f0f913d8",\n  "name": "Vendor Contract Review",\n  "status": "ACTIVE"\n}`
  },
  {
    type: 'api',
    id: 'list-inst',
    category: 'REST API',
    title: 'List Instances',
    method: 'GET',
    path: '/api/instances',
    description: 'Retrieves all running, completed, and rejected workflow instances sorted chronologically.',
    response: `[
  {
    "id": "60094e59-65ea-41c7-b57a-ec59f2dd7784",
    "title": "Expense Voucher #1042",
    "definitionName": "Load Test Workflow",
    "initiatorId": "user-884",
    "status": "RUNNING",
    "createdAt": "2026-09-12T20:15:30Z"
  }
]`
  },
  {
    type: 'api',
    id: 'start-inst',
    category: 'REST API',
    title: 'Start Instance',
    method: 'POST',
    path: '/api/instances',
    description: 'Spawns a new running execution of a workflow DAG from an existing definition.',
    defaultBody: `{\n  "definitionName": "Load Test Workflow",\n  "title": "Ad-Hoc Procurement Review",\n  "initiatorId": "employee-12"\n}`,
    response: `{\n  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",\n  "title": "Ad-Hoc Procurement Review",\n  "status": "RUNNING",\n  "createdAt": "2026-09-13T02:50:00Z"\n}`
  },
  {
    type: 'api',
    id: 'get-pending',
    category: 'REST API',
    title: 'Pending Approvals',
    method: 'GET',
    path: '/api/approvals/pending',
    description: 'Fetches all tasks currently in READY state awaiting decision for a role.',
    defaultParams: '?role=MANAGER',
    response: `[
  {
    "id": "task-552",
    "taskName": "Manager Approval",
    "assigneeRole": "MANAGER",
    "status": "READY",
    "workflowInstanceId": "60094e59-65ea-41c7-b57a-ec59f2dd7784"
  }
]`
  },
  {
    type: 'api',
    id: 'get-metrics',
    category: 'REST API',
    title: 'System Telemetry',
    method: 'GET',
    path: '/api/metrics/summary',
    description: 'Returns real-time aggregated latency (p50, p99) and instance distribution counts.',
    response: `{\n  "runningCount": 12,\n  "completedCount": 1000,\n  "rejectedCount": 14,\n  "failedCount": 0,\n  "p50Ms": 42.5,\n  "p99Ms": 118.2,\n  "totalStepsCompleted": 3042\n}`
  }
];

export default function ApiDocs() {
  const [selectedId, setSelectedId] = useState(DOCS[0].id);
  const [searchQuery, setSearchQuery] = useState('');
  const [copiedText, setCopiedText] = useState<string | null>(null);
  
  // Sandbox state
  const [customParams, setCustomParams] = useState('');
  const [customBody, setCustomBody] = useState('');
  const [sandboxOutput, setSandboxOutput] = useState<{ status: number; durationMs: number; data: any } | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const selected = DOCS.find(d => d.id === selectedId) || DOCS[0];

  // Initialize sandbox inputs when selected doc changes
  useEffect(() => {
    setCustomParams(selected.defaultParams || '');
    setCustomBody(selected.defaultBody || '');
    setSandboxOutput(null);
  }, [selectedId, selected]);

  const handleCopy = (text: string, label: string) => {
    navigator.clipboard.writeText(text);
    setCopiedText(label);
    setTimeout(() => setCopiedText(null), 1800);
  };

  const handleRunSandbox = async () => {
    if (selected.type !== 'api' || !selected.path || !selected.method) return;
    
    setIsLoading(true);
    setSandboxOutput(null);
    const start = performance.now();

    const url = `${API_BASE}${selected.path}${customParams.trim()}`;
    let parsedBody = undefined;
    if (customBody.trim()) {
      try {
        parsedBody = JSON.parse(customBody);
      } catch (err: any) {
        setSandboxOutput({
          status: 400,
          durationMs: 0,
          data: { error: 'Invalid JSON in Request Body input: ' + err.message }
        });
        setIsLoading(false);
        return;
      }
    }

    try {
      const response = await axios({
        method: selected.method,
        url,
        data: parsedBody,
        headers: { 'Content-Type': 'application/json' },
        timeout: 5000
      });
      const durationMs = Math.round(performance.now() - start);
      setSandboxOutput({ status: response.status, durationMs, data: response.data });
    } catch (err: any) {
      const durationMs = Math.round(performance.now() - start);
      setSandboxOutput({ 
        status: err.response?.status || 500,
        durationMs,
        data: err.response?.data || { error: err.message }
      });
    } finally {
      setIsLoading(false);
    }
  };

  // Filter sections by search query
  const filteredDocs = useMemo(() => {
    if (!searchQuery.trim()) return DOCS;
    const q = searchQuery.toLowerCase();
    return DOCS.filter(d => 
      d.title.toLowerCase().includes(q) ||
      d.path?.toLowerCase().includes(q) ||
      d.description?.toLowerCase().includes(q)
    );
  }, [searchQuery]);

  const guides = filteredDocs.filter(d => d.type === 'article');
  const apis = filteredDocs.filter(d => d.type === 'api');

  return (
    <div className="docs-container">
      {/* SIDEBAR */}
      <aside className="docs-sidebar">
        <div className="docs-sidebar-header">
          <div className="docs-search-box">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#656c76" strokeWidth="2">
              <circle cx="11" cy="11" r="8"></circle>
              <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
            </svg>
            <input
              type="text"
              className="docs-search-input"
              placeholder="Filter endpoints & docs..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
            />
          </div>
        </div>

        {guides.length > 0 && (
          <div>
            <div className="sidebar-section-title">Guides & Architecture</div>
            <div className="endpoint-list">
              {guides.map(item => (
                <div
                  key={item.id}
                  className={`endpoint-item ${item.id === selectedId ? 'active' : ''}`}
                  onClick={() => setSelectedId(item.id)}
                >
                  <span style={{ color: 'var(--accent-blue)', display: 'inline-flex', alignItems: 'center' }}>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                      <polyline points="14 2 14 8 20 8"></polyline>
                      <line x1="16" y1="13" x2="8" y2="13"></line>
                      <line x1="16" y1="17" x2="8" y2="17"></line>
                      <polyline points="10 9 9 9 8 9"></polyline>
                    </svg>
                  </span>
                  <span className="endpoint-path-sidebar">{item.title}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {apis.length > 0 && (
          <div>
            <div className="sidebar-section-title" style={{ marginTop: 12 }}>REST API Endpoints</div>
            <div className="endpoint-list">
              {apis.map(item => (
                <div
                  key={item.id}
                  className={`endpoint-item ${item.id === selectedId ? 'active' : ''}`}
                  onClick={() => setSelectedId(item.id)}
                >
                  <span className={`method-badge ${item.method?.toLowerCase()}`}>
                    {item.method}
                  </span>
                  <span className="endpoint-path-sidebar">{item.title}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </aside>

      {/* MAIN DOCUMENTATION CONTENT */}
      <main className="docs-content">
        <div className="docs-header">
          <div className="docs-breadcrumb">
            <span>Workflow Engine</span>
            <span>/</span>
            <span>{selected.category}</span>
            <span>/</span>
            <span style={{ color: 'var(--text-primary)' }}>{selected.title}</span>
          </div>
          <h1 className="docs-summary">{selected.title}</h1>
        </div>

        <div className="docs-body">
          {selected.type === 'article' ? (
            <div className="guide-card">
              {selected.content}
            </div>
          ) : (
            <>
              {/* Endpoint Path Banner */}
              <div className="docs-path-banner">
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <span className={`method-badge lg ${selected.method?.toLowerCase()}`}>
                    {selected.method}
                  </span>
                  <span className="endpoint-path-large">{selected.path}</span>
                </div>

                <div style={{ display: 'flex', gap: 8 }}>
                  <button
                    className="copy-btn"
                    onClick={() => handleCopy(selected.path || '', 'path')}
                  >
                    {copiedText === 'path' ? 'Copied' : 'Copy Path'}
                  </button>
                  <button
                    className="btn-primary"
                    onClick={handleRunSandbox}
                    disabled={isLoading}
                  >
                    {isLoading ? 'Executing...' : 'Run in Sandbox'}
                  </button>
                </div>
              </div>

              <p className="docs-description">{selected.description}</p>

              <div className="docs-grid">
                {/* Left Column: Request Configuration */}
                <div className="docs-left">
                  {selected.defaultParams !== undefined && (
                    <div className="docs-section">
                      <div className="docs-section-title">
                        <span>Query Parameters</span>
                      </div>
                      <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border-default)', borderRadius: 'var(--radius-sm)', padding: '6px 10px' }}>
                        <input
                          type="text"
                          value={customParams}
                          onChange={e => setCustomParams(e.target.value)}
                          placeholder="?role=MANAGER"
                          style={{
                            background: 'transparent',
                            border: 'none',
                            color: 'var(--text-primary)',
                            fontFamily: 'var(--font-mono)',
                            fontSize: 12.5,
                            width: '100%',
                            outline: 'none'
                          }}
                        />
                      </div>
                    </div>
                  )}

                  {selected.defaultBody !== undefined && (
                    <div className="docs-section">
                      <div className="docs-section-title">
                        <span>Request Body (JSON)</span>
                        <button
                          className="copy-btn"
                          onClick={() => handleCopy(customBody, 'req-body')}
                        >
                          {copiedText === 'req-body' ? 'Copied' : 'Copy'}
                        </button>
                      </div>
                      <textarea
                        value={customBody}
                        onChange={e => setCustomBody(e.target.value)}
                        rows={8}
                        style={{
                          width: '100%',
                          background: 'var(--bg-canvas)',
                          border: '1px solid var(--border-default)',
                          borderRadius: 'var(--radius-sm)',
                          padding: '10px 12px',
                          color: 'var(--text-primary)',
                          fontFamily: 'var(--font-mono)',
                          fontSize: 12,
                          lineHeight: 1.5,
                          outline: 'none',
                          resize: 'vertical'
                        }}
                      />
                    </div>
                  )}

                  {/* cURL Preview */}
                  <div className="docs-section">
                    <div className="docs-section-title">
                      <span>cURL Equivalent</span>
                      <button
                        className="copy-btn"
                        onClick={() => {
                          const curl = `curl -X ${selected.method} "http://localhost:8080${selected.path}${customParams}" ${customBody ? `-H "Content-Type: application/json" -d '${customBody}'` : ''}`;
                          handleCopy(curl, 'curl');
                        }}
                      >
                        {copiedText === 'curl' ? 'Copied' : 'Copy'}
                      </button>
                    </div>
                    <div className="code-block dark" style={{ fontSize: 11.5 }}>
                      <code>
                        curl -X {selected.method} "{API_BASE}{selected.path}{customParams}" {customBody ? `\\ \n  -H "Content-Type: application/json" \\ \n  -d '${customBody.replace(/\n/g, '')}'` : ''}
                      </code>
                    </div>
                  </div>
                </div>

                {/* Right Column: Sandbox Terminal & Example Response */}
                <div className="docs-right">
                  {/* macOS Sandbox Terminal */}
                  <div className="docs-section">
                    <div className="docs-section-title">
                      <span>Interactive Sandbox Terminal</span>
                    </div>

                    <div className="terminal-window">
                      <div className="terminal-header">
                        <div className="terminal-dots">
                          <div className="terminal-dot close" />
                          <div className="terminal-dot minimize" />
                          <div className="terminal-dot expand" />
                        </div>
                        <div className="terminal-title">
                          sandbox — {selected.method} {selected.path}
                        </div>
                        {sandboxOutput && (
                          <div style={{
                            fontSize: 11,
                            fontWeight: 600,
                            padding: '1px 6px',
                            borderRadius: 4,
                            background: sandboxOutput.status >= 200 && sandboxOutput.status < 300 ? 'rgba(63, 185, 80, 0.2)' : 'rgba(248, 81, 73, 0.2)',
                            color: sandboxOutput.status >= 200 && sandboxOutput.status < 300 ? 'var(--status-completed)' : 'var(--status-rejected)'
                          }}>
                            {sandboxOutput.status} OK · {sandboxOutput.durationMs}ms
                          </div>
                        )}
                      </div>

                      <div className="terminal-body">
                        {isLoading ? (
                          <div style={{ color: 'var(--accent-blue)', display: 'flex', alignItems: 'center', gap: 8 }}>
                            <div className="spinner" style={{ width: 14, height: 14 }} />
                            <span>Sending request to engine...</span>
                          </div>
                        ) : sandboxOutput ? (
                          <pre style={{ margin: 0 }}>
                            <code>{JSON.stringify(sandboxOutput.data, null, 2)}</code>
                          </pre>
                        ) : (
                          <div style={{ color: 'var(--text-muted)' }}>
                            Click <strong>"Run in Sandbox"</strong> above to send live request to backend at <code>http://localhost:8080</code>.
                          </div>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Schema / Example Response */}
                  <div className="docs-section">
                    <div className="docs-section-title">
                      <span>Mock Schema Response</span>
                      <button
                        className="copy-btn"
                        onClick={() => handleCopy(selected.response || '', 'schema')}
                      >
                        {copiedText === 'schema' ? 'Copied' : 'Copy'}
                      </button>
                    </div>
                    <div className="code-block dark">
                      <pre><code>{selected.response}</code></pre>
                    </div>
                  </div>
                </div>
              </div>
            </>
          )}
        </div>
      </main>
    </div>
  );
}
