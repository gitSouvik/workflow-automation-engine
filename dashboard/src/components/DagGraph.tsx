import React, { useEffect, useRef, useState, useCallback, useMemo } from 'react';
import axios from 'axios';

const API_BASE = 'http://localhost:8080';

export interface TaskInstance {
  id: string;
  taskKey: string;
  taskName: string;
  status: 'PENDING' | 'READY' | 'IN_PROGRESS' | 'APPROVED' | 'REJECTED' | 'SKIPPED';
  assigneeRole: string;
  startedAt?: string;
  completedAt?: string;
  stepDurationMs?: number;
}

export interface WorkflowInstance {
  id: string;
  title: string;
  definitionName: string;
  initiatorId: string;
  status: 'RUNNING' | 'COMPLETED' | 'REJECTED' | 'FAILED';
  createdAt: string;
  completedAt?: string;
  taskInstances: TaskInstance[];
}

interface DefinitionTask {
  id?: string;
  taskKey: string;
  name: string;
  assigneeRole: string;
  displayOrder?: number;
  nodeType?: string;
}

interface DefinitionDependency {
  fromTaskKey: string;
  toTaskKey: string;
  conditionExpression?: string;
}

interface WorkflowDefinition {
  id: string;
  name: string;
  description: string;
  tasks: DefinitionTask[];
  dependencies: DefinitionDependency[];
}

interface Node2D {
  taskKey: string;
  name: string;
  role: string;
  status: 'PENDING' | 'READY' | 'IN_PROGRESS' | 'APPROVED' | 'REJECTED' | 'SKIPPED';
  x: number;
  y: number;
  radius: number;
  orderNumber: number;
  nodeType: string;
}

interface DagGraphProps {
  instances: WorkflowInstance[];
  selectedInstance: WorkflowInstance | null;
  onSelectInstance: (instance: WorkflowInstance) => void;
  onBackToDashboard?: () => void;
}

const DEFAULT_TASKS: DefinitionTask[] = [
  { taskKey: 'SUBMIT', name: 'Submit Request', assigneeRole: 'SUBMITTER', displayOrder: 1 },
  { taskKey: 'MANAGER_REVIEW', name: 'Manager Review', assigneeRole: 'MANAGER', displayOrder: 2 },
  { taskKey: 'FINANCE_CHECK', name: 'Finance Check', assigneeRole: 'FINANCE', displayOrder: 2 },
  { taskKey: 'FINAL_APPROVAL', name: 'Final Approval', assigneeRole: 'DIRECTOR', displayOrder: 3 }
];

const DEFAULT_DEPS: DefinitionDependency[] = [
  { fromTaskKey: 'SUBMIT', toTaskKey: 'MANAGER_REVIEW' },
  { fromTaskKey: 'SUBMIT', toTaskKey: 'FINANCE_CHECK' },
  { fromTaskKey: 'MANAGER_REVIEW', toTaskKey: 'FINAL_APPROVAL' },
  { fromTaskKey: 'FINANCE_CHECK', toTaskKey: 'FINAL_APPROVAL' }
];

const STATUS_THEMES: Record<string, { fill: string; stroke: string; glow: string; text: string }> = {
  APPROVED: { fill: '#3fb950', stroke: '#2ea043', glow: 'rgba(63, 185, 80, 0.35)', text: '#3fb950' },
  IN_PROGRESS: { fill: '#e3b341', stroke: '#d29922', glow: 'rgba(227, 179, 65, 0.55)', text: '#e3b341' },
  READY: { fill: '#38bdf8', stroke: '#0284c7', glow: 'rgba(56, 189, 248, 0.35)', text: '#38bdf8' },
  PENDING: { fill: '#a855f7', stroke: '#9333ea', glow: 'rgba(168, 85, 247, 0.35)', text: '#d8b4fe' },
  REJECTED: { fill: '#f85149', stroke: '#da3633', glow: 'rgba(248, 81, 73, 0.45)', text: '#f85149' },
  SKIPPED: { fill: '#f43f5e', stroke: '#e11d48', glow: 'rgba(244, 63, 94, 0.35)', text: '#fda4af' }
};

export default function DagGraph({ instances, selectedInstance, onSelectInstance }: DagGraphProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const searchInputRef = useRef<HTMLInputElement | null>(null);

  const [definitions, setDefinitions] = useState<WorkflowDefinition[]>([]);
  const [hoveredNode, setHoveredNode] = useState<Node2D | null>(null);
  const [selectedTaskKey, setSelectedTaskKey] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [showTaskPanel, setShowTaskPanel] = useState(true);

  // 2D Pan and Zoom
  const [panX, setPanX] = useState<number>(0);
  const [panY, setPanY] = useState<number>(0);
  const [zoom, setZoom] = useState<number>(1.0);

  const isDraggingRef = useRef(false);
  const lastMouseRef = useRef({ x: 0, y: 0 });
  const panXRef = useRef(panX);
  const panYRef = useRef(panY);
  const zoomRef = useRef(zoom);

  panXRef.current = panX;
  panYRef.current = panY;
  zoomRef.current = zoom;

  const particleOffsetRef = useRef(0);

  // Focus search on '/' key press (GitHub shortcut)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === '/' && document.activeElement?.tagName !== 'INPUT' && document.activeElement?.tagName !== 'TEXTAREA') {
        e.preventDefault();
        searchInputRef.current?.focus();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  useEffect(() => {
    let isMounted = true;
    axios.get<WorkflowDefinition[]>(`${API_BASE}/api/definitions`)
      .then(res => {
        if (isMounted && res.data) {
          setDefinitions(res.data);
        }
      })
      .catch(() => {
        setDefinitions([{
          id: 'default',
          name: 'Load Test Workflow',
          description: '4-step approval flow with parallel reviews',
          tasks: DEFAULT_TASKS,
          dependencies: DEFAULT_DEPS
        }]);
      });
    return () => { isMounted = false; };
  }, []);

  const currentInstance = selectedInstance || (instances.length > 0 ? instances[0] : null);
  
  const activeDefinition = useMemo(() => {
    if (currentInstance?.definitionId) {
      return definitions.find(d => d.id === currentInstance.definitionId) || definitions[0];
    }
    return definitions[0];
  }, [definitions, currentInstance]);

  const activeTasks = activeDefinition?.tasks || DEFAULT_TASKS;
  const activeDeps = activeDefinition?.dependencies || DEFAULT_DEPS;

  // Filter instances by search
  const filteredInstances = useMemo(() => {
    if (!searchQuery.trim()) return instances;
    const q = searchQuery.toLowerCase();
    return instances.filter(inst =>
      inst.title.toLowerCase().includes(q) ||
      inst.id.toLowerCase().includes(q) ||
      inst.status.toLowerCase().includes(q)
    );
  }, [instances, searchQuery]);

  // Filter tasks in the right panel by search query
  const displayedTasks = useMemo(() => {
    if (!searchQuery.trim()) return activeTasks;
    const q = searchQuery.toLowerCase();
    const matched = activeTasks.filter(t =>
      (t.name || '').toLowerCase().includes(q) ||
      (t.taskKey || '').toLowerCase().includes(q) ||
      (t.assigneeRole || '').toLowerCase().includes(q)
    );
    return matched.length > 0 ? matched : activeTasks;
  }, [activeTasks, searchQuery]);

  // Map task key to runtime status from selected instance
  const taskStatusMap = useMemo(() => {
    const map: Record<string, 'PENDING' | 'READY' | 'IN_PROGRESS' | 'APPROVED' | 'REJECTED' | 'SKIPPED'> = {};
    if (currentInstance?.taskInstances) {
      currentInstance.taskInstances.forEach(t => {
        map[t.taskKey] = t.status;
      });
    }
    return map;
  }, [currentInstance]);

  // Compute 2D node coordinates along DAG stages
  const nodes2D: Node2D[] = useMemo(() => {
    const inDegree: Record<string, number> = {};
    const adj: Record<string, string[]> = {};
    activeTasks.forEach(t => {
      inDegree[t.taskKey] = 0;
      adj[t.taskKey] = [];
    });
    activeDeps.forEach(d => {
      if (adj[d.fromTaskKey]) adj[d.fromTaskKey].push(d.toTaskKey);
      inDegree[d.toTaskKey] = (inDegree[d.toTaskKey] || 0) + 1;
    });

    const layer: Record<string, number> = {};
    const queue = activeTasks.filter(t => inDegree[t.taskKey] === 0).map(t => t.taskKey);
    queue.forEach(k => { layer[k] = 0; });

    const visited = new Set<string>();
    while (queue.length > 0) {
      const u = queue.shift()!;
      visited.add(u);
      const currentLayer = layer[u] || 0;
      (adj[u] || []).forEach(v => {
        layer[v] = Math.max(layer[v] || 0, currentLayer + 1);
        if (!visited.has(v)) {
          queue.push(v);
        }
      });
    }

    const layerBuckets: Record<number, DefinitionTask[]> = {};
    activeTasks.forEach(t => {
      const l = layer[t.taskKey] !== undefined ? layer[t.taskKey] : (t.displayOrder ? t.displayOrder - 1 : 0);
      if (!layerBuckets[l]) layerBuckets[l] = [];
      layerBuckets[l].push(t);
    });

    const maxLayer = Math.max(...Object.keys(layerBuckets).map(Number), 1);
    const xSpan = 280;
    const ySpan = 110;

    const result: Node2D[] = [];
    let orderCounter = 1;

    Object.keys(layerBuckets).sort((a, b) => Number(a) - Number(b)).forEach(lStr => {
      const l = Number(lStr);
      const tasksInLayer = layerBuckets[l];
      const count = tasksInLayer.length;
      const x = -xSpan / 2 + (l / maxLayer) * xSpan;

      tasksInLayer.forEach((t, idx) => {
        let y = 0;
        if (count > 1) {
          y = -ySpan / 2 + (idx / (count - 1)) * ySpan;
        }

        const status = taskStatusMap[t.taskKey] || (currentInstance?.status === 'COMPLETED' ? 'APPROVED' : 'READY');

        result.push({
          taskKey: t.taskKey,
          name: t.name || t.taskKey,
          role: t.assigneeRole || 'ROLE',
          status,
          x,
          y,
          radius: 20,
          orderNumber: orderCounter++,
          nodeType: t.nodeType || 'APPROVAL'
        });
      });
    });

    return result;
  }, [activeTasks, activeDeps, taskStatusMap, currentInstance]);

  // Render 2D DAG Graph on Canvas
  useEffect(() => {
    let animId: number;
    const canvas = canvasRef.current;
    const container = containerRef.current;
    if (!canvas || !container) return;

    const render = () => {
      const dpr = window.devicePixelRatio || 1;
      const displayWidth = Math.max(260, container.clientWidth || 600);
      const displayHeight = Math.max(260, container.clientHeight || 460);

      if (canvas.width !== Math.floor(displayWidth * dpr) || canvas.height !== Math.floor(displayHeight * dpr)) {
        canvas.width = Math.floor(displayWidth * dpr);
        canvas.height = Math.floor(displayHeight * dpr);
        canvas.style.width = `100%`;
        canvas.style.height = `100%`;
      }

      const ctx = canvas.getContext('2d');
      if (!ctx) {
        animId = requestAnimationFrame(render);
        return;
      }

      particleOffsetRef.current = (particleOffsetRef.current + 0.012) % 1;

      ctx.save();
      ctx.scale(dpr, dpr);
      ctx.clearRect(0, 0, displayWidth, displayHeight);

      const cx = displayWidth / 2 + panXRef.current;
      const cy = displayHeight / 2 + panYRef.current;
      const curZoom = zoomRef.current;

      const screenNodeMap: Record<string, { x: number; y: number; node: Node2D }> = {};
      nodes2D.forEach(n => {
        screenNodeMap[n.taskKey] = {
          x: cx + n.x * curZoom,
          y: cy + n.y * curZoom,
          node: n
        };
      });

      // 1. Subtle Background Grid
      ctx.save();
      ctx.strokeStyle = 'rgba(48, 54, 61, 0.2)';
      ctx.lineWidth = 1;
      const gridSize = 36 * curZoom;
      const startX = (cx % gridSize) - gridSize;
      const startY = (cy % gridSize) - gridSize;

      for (let x = startX; x < displayWidth + gridSize; x += gridSize) {
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, displayHeight);
        ctx.stroke();
      }
      for (let y = startY; y < displayHeight + gridSize; y += gridSize) {
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(displayWidth, y);
        ctx.stroke();
      }
      ctx.restore();

      // 2. Directed Connecting Line Edges
      activeDeps.forEach(dep => {
        const from = screenNodeMap[dep.fromTaskKey];
        const to = screenNodeMap[dep.toTaskKey];
        if (!from || !to) return;

        const isApproved = from.node.status === 'APPROVED';
        const isProgress = from.node.status === 'APPROVED' && to.node.status === 'IN_PROGRESS';

        ctx.save();

        if (isProgress) {
          ctx.strokeStyle = '#e3b341';
          ctx.lineWidth = 2.2 * curZoom;
          ctx.shadowColor = 'rgba(227, 179, 65, 0.6)';
          ctx.shadowBlur = 6;
        } else if (isApproved) {
          ctx.strokeStyle = '#3fb950';
          ctx.lineWidth = 1.8 * curZoom;
        } else {
          ctx.strokeStyle = 'rgba(110, 118, 129, 0.4)';
          ctx.lineWidth = 1.4 * curZoom;
        }

        const dx = to.x - from.x;
        const cp1x = from.x + dx * 0.45;
        const cp1y = from.y;
        const cp2x = from.x + dx * 0.55;
        const cp2y = to.y;

        ctx.beginPath();
        ctx.moveTo(from.x, from.y);
        ctx.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, to.x, to.y);
        ctx.stroke();

        const t = 0.72;
        const ax = Math.pow(1 - t, 3) * from.x +
                   3 * Math.pow(1 - t, 2) * t * cp1x +
                   3 * (1 - t) * Math.pow(t, 2) * cp2x +
                   Math.pow(t, 3) * to.x;
        const ay = Math.pow(1 - t, 3) * from.y +
                   3 * Math.pow(1 - t, 2) * t * cp1y +
                   3 * (1 - t) * Math.pow(t, 2) * cp2y +
                   Math.pow(t, 3) * to.y;

        const tax = 3 * Math.pow(1 - t, 2) * (cp1x - from.x) +
                    6 * (1 - t) * t * (cp2x - cp1x) +
                    3 * Math.pow(t, 2) * (to.x - cp2x);
        const tay = 3 * Math.pow(1 - t, 2) * (cp1y - from.y) +
                    6 * (1 - t) * t * (cp2y - cp1y) +
                    3 * Math.pow(t, 2) * (to.y - cp2y);
        const angle = Math.atan2(tay, tax);

        ctx.beginPath();
        ctx.fillStyle = ctx.strokeStyle;
        const arrowSize = 6 * curZoom;
        ctx.moveTo(ax, ay);
        ctx.lineTo(ax - arrowSize * Math.cos(angle - Math.PI / 6), ay - arrowSize * Math.sin(angle - Math.PI / 6));
        ctx.lineTo(ax - arrowSize * Math.cos(angle + Math.PI / 6), ay - arrowSize * Math.sin(angle + Math.PI / 6));
        ctx.closePath();
        ctx.fill();

        if (dep.conditionExpression) {
          ctx.font = `500 ${Math.max(7, Math.round(8 * curZoom))}px 'JetBrains Mono', monospace`;
          ctx.fillStyle = '#8b949e';
          ctx.textAlign = 'center';
          ctx.textBaseline = 'middle';
          
          const mt = 0.5;
          const mx = Math.pow(1 - mt, 3) * from.x +
                     3 * Math.pow(1 - mt, 2) * mt * cp1x +
                     3 * (1 - mt) * Math.pow(mt, 2) * cp2x +
                     Math.pow(mt, 3) * to.x;
          const my = Math.pow(1 - mt, 3) * from.y +
                     3 * Math.pow(1 - mt, 2) * mt * cp1y +
                     3 * (1 - mt) * Math.pow(mt, 2) * cp2y +
                     Math.pow(mt, 3) * to.y;
                     
          ctx.fillText(dep.conditionExpression, mx, my - 10 * curZoom);
        }

        if (isApproved || isProgress) {
          const pt = particleOffsetRef.current;
          const px = Math.pow(1 - pt, 3) * from.x +
                     3 * Math.pow(1 - pt, 2) * pt * cp1x +
                     3 * (1 - pt) * Math.pow(pt, 2) * cp2x +
                     Math.pow(pt, 3) * to.x;
          const py = Math.pow(1 - pt, 3) * from.y +
                     3 * Math.pow(1 - pt, 2) * pt * cp1y +
                     3 * (1 - pt) * Math.pow(pt, 2) * cp2y +
                     Math.pow(pt, 3) * to.y;

          ctx.beginPath();
          ctx.arc(px, py, 3 * curZoom, 0, Math.PI * 2);
          ctx.fillStyle = '#ffffff';
          ctx.shadowColor = '#58a6ff';
          ctx.shadowBlur = 6;
          ctx.fill();
        }

        ctx.restore();
      });

      // 3. Draw Colored Circle Nodes
      nodes2D.forEach(node => {
        const pos = screenNodeMap[node.taskKey];
        if (!pos) return;

        const theme = STATUS_THEMES[node.status] || STATUS_THEMES.PENDING;
        const r = node.radius * curZoom;
        const isSelected = selectedTaskKey === node.taskKey;

        ctx.save();

        if (isSelected) {
          ctx.beginPath();
          if (node.nodeType === 'CONDITIONAL') {
            ctx.moveTo(pos.x, pos.y - r * 1.3);
            ctx.lineTo(pos.x + r * 1.3, pos.y);
            ctx.lineTo(pos.x, pos.y + r * 1.3);
            ctx.lineTo(pos.x - r * 1.3, pos.y);
            ctx.closePath();
          } else {
            ctx.arc(pos.x, pos.y, r * 1.3, 0, Math.PI * 2);
          }
          ctx.fillStyle = 'rgba(88, 166, 255, 0.4)';
          ctx.fill();
        }

        ctx.beginPath();
        if (node.nodeType === 'CONDITIONAL') {
          ctx.moveTo(pos.x, pos.y - r);
          ctx.lineTo(pos.x + r, pos.y);
          ctx.lineTo(pos.x, pos.y + r);
          ctx.lineTo(pos.x - r, pos.y);
          ctx.closePath();
        } else {
          ctx.arc(pos.x, pos.y, r, 0, Math.PI * 2);
        }
        ctx.fillStyle = theme.fill;
        ctx.fill();

        ctx.lineWidth = isSelected ? 2.8 : 1.5;
        ctx.strokeStyle = isSelected ? '#58a6ff' : theme.stroke;
        ctx.stroke();

        ctx.font = `500 ${Math.max(8, Math.round(9 * curZoom))}px Inter, sans-serif`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'top';
        ctx.fillStyle = '#f0f6fc';
        ctx.fillText(node.name, pos.x, pos.y + r + 6);

        ctx.font = `500 ${Math.max(7, Math.round(7.5 * curZoom))}px 'JetBrains Mono', monospace`;
        ctx.fillStyle = '#8b949e';
        ctx.fillText(node.role, pos.x, pos.y + r + 18);

        ctx.restore();
      });

      ctx.restore();

      animId = requestAnimationFrame(render);
    };

    animId = requestAnimationFrame(render);
    return () => cancelAnimationFrame(animId);
  }, [nodes2D, activeDeps, selectedTaskKey]);

  // Mouse pan handlers
  const handleMouseDown = useCallback((e: React.MouseEvent<HTMLCanvasElement>) => {
    isDraggingRef.current = true;
    lastMouseRef.current = { x: e.clientX, y: e.clientY };
  }, []);

  const handleMouseMove = useCallback((e: React.MouseEvent<HTMLCanvasElement>) => {
    if (isDraggingRef.current) {
      const deltaX = e.clientX - lastMouseRef.current.x;
      const deltaY = e.clientY - lastMouseRef.current.y;
      lastMouseRef.current = { x: e.clientX, y: e.clientY };

      const nextPanX = panXRef.current + deltaX;
      const nextPanY = panYRef.current + deltaY;
      panXRef.current = nextPanX;
      panYRef.current = nextPanY;
      setPanX(nextPanX);
      setPanY(nextPanY);
    }

    const canvas = canvasRef.current;
    const container = containerRef.current;
    if (!canvas || !container) return;
    const rect = canvas.getBoundingClientRect();
    const mx = e.clientX - rect.left;
    const my = e.clientY - rect.top;

    const displayWidth = container.clientWidth || 600;
    const displayHeight = container.clientHeight || 460;
    const cx = displayWidth / 2 + panXRef.current;
    const cy = displayHeight / 2 + panYRef.current;
    const curZoom = zoomRef.current;

    let hit: Node2D | null = null;
    nodes2D.forEach(n => {
      const nx = cx + n.x * curZoom;
      const ny = cy + n.y * curZoom;
      const dx = mx - nx;
      const dy = my - ny;
      if (Math.sqrt(dx * dx + dy * dy) <= n.radius * curZoom + 8) {
        hit = n;
      }
    });
    setHoveredNode(hit);
  }, [nodes2D]);

  const handleMouseUp = useCallback(() => {
    isDraggingRef.current = false;
  }, []);

  const handleClickCanvas = () => {
    if (hoveredNode) {
      setSelectedTaskKey(prev => prev === hoveredNode.taskKey ? null : hoveredNode.taskKey);
    }
  };

  const handleWheel = useCallback((e: React.WheelEvent<HTMLCanvasElement>) => {
    e.preventDefault();
    const delta = e.deltaY * -0.001;
    const nextZoom = Math.max(0.5, Math.min(2.2, zoomRef.current + delta));
    zoomRef.current = nextZoom;
    setZoom(nextZoom);
  }, []);

  const handleResetView = () => {
    panXRef.current = 0;
    panYRef.current = 0;
    zoomRef.current = 1.0;
    setPanX(0);
    setPanY(0);
    setZoom(1.0);
    setSelectedTaskKey(null);
  };

  return (
    <div className="dag-graph-card">
      {/* COMPACT SLIM TOP BAR WITH GITHUB-STYLE SEARCH BAR */}
      <div className="dag-graph-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0 }}>
          <div style={{ minWidth: 0 }}>
            <div className="dag-title">
              {currentInstance ? currentInstance.title : 'Template Graph'}
              {currentInstance && (
                <span className={`status-badge badge-${currentInstance.status.toLowerCase()}`} style={{ marginLeft: 5, padding: '1px 5px', fontSize: 9.5 }}>
                  {currentInstance.status}
                </span>
              )}
            </div>
            <div className="dag-subtitle">
              {currentInstance ? `Instance ID: ${currentInstance.id}` : ''}
            </div>
          </div>

          <div className="table-search-box" title="Search instances or tasks (Press / to focus)" style={{ marginLeft: 16 }}>
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#656c76" strokeWidth="2">
              <circle cx="11" cy="11" r="8"></circle>
              <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
            </svg>
            <input
              ref={searchInputRef}
              type="text"
              className="table-search-input"
              placeholder="Filter tasks..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
            />
            {searchQuery ? (
              <button
                onClick={() => setSearchQuery('')}
                style={{ background: 'none', border: 'none', color: '#8b949e', cursor: 'pointer', padding: 0, display: 'flex', alignItems: 'center' }}
                title="Clear"
              >
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <line x1="18" y1="6" x2="6" y2="18"></line>
                  <line x1="6" y1="6" x2="18" y2="18"></line>
                </svg>
              </button>
            ) : (
              <kbd className="github-search-kbd">/</kbd>
            )}
          </div>
        </div>

        {/* Zoom Controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
          <button
            className="filter-pill"
            onClick={handleResetView}
            title="Reset 2D view"
            style={{ padding: '2px 6px', fontSize: 10, height: 24 }}
          >
            Reset
          </button>

          <button
            className="filter-pill"
            onClick={() => {
              const n = Math.min(2.2, zoomRef.current + 0.15);
              zoomRef.current = n;
              setZoom(n);
            }}
            title="Zoom in"
            style={{ padding: '2px 6px', fontSize: 10, height: 24 }}
          >
            +
          </button>
          <button
            className="filter-pill"
            onClick={() => {
              const n = Math.max(0.5, zoomRef.current - 0.15);
              zoomRef.current = n;
              setZoom(n);
            }}
            title="Zoom out"
            style={{ padding: '2px 6px', fontSize: 10, height: 24 }}
          >
            -
          </button>

          <button
            className={`filter-pill ${showTaskPanel ? 'active' : ''}`}
            onClick={() => setShowTaskPanel(prev => !prev)}
            title={showTaskPanel ? 'Hide tasks panel' : 'Show tasks panel'}
            style={{ padding: '2px 6px', fontSize: 10, height: 24, display: 'flex', alignItems: 'center', gap: 3 }}
          >
            <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
              <line x1="15" y1="3" x2="15" y2="21"></line>
            </svg>
            <span>Tasks</span>
          </button>
        </div>
      </div>

      {/* SPLIT BODY: Task Panel on Left, 2D Canvas on Right */}
      <div className="dag-split-body">
        {/* LEFT PANEL: TASK NAMES AND STUFF */}
        {showTaskPanel && (
          <div className="dag-tasks-left-panel">
            <div className="dag-panel-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                <span>Workflow Tasks</span>
                <span className="status-badge badge-pending" style={{ fontSize: 9, padding: '0 4px', height: 16 }}>
                  {displayedTasks.length}
                </span>
              </div>
              <span style={{ fontSize: 9.5, color: 'var(--text-muted)' }}>
                {currentInstance ? `v${currentInstance.taskInstances?.length || activeTasks.length}` : 'Blueprint'}
              </span>
            </div>

            <div className="dag-tasks-list">
              {displayedTasks.map(t => {
                const taskInst = currentInstance?.taskInstances?.find(ti => ti.taskKey === t.taskKey);
                const status = taskInst?.status || taskStatusMap[t.taskKey] || 'PENDING';
                const theme = STATUS_THEMES[status] || STATUS_THEMES.PENDING;
                const isSelected = selectedTaskKey === t.taskKey;

                return (
                  <div
                    key={t.taskKey}
                    className={`dag-task-card ${isSelected ? 'selected' : ''}`}
                    onClick={() => setSelectedTaskKey(prev => prev === t.taskKey ? null : t.taskKey)}
                    title="Click to highlight node"
                  >
                    <div className="dag-task-card-header">
                      <div style={{ display: 'flex', alignItems: 'center', gap: 5, minWidth: 0 }}>
                        <span className="dag-task-dot" style={{ background: theme.fill }} />
                        <span className="dag-task-name" title={t.name || t.taskKey}>
                          {t.name || t.taskKey}
                        </span>
                      </div>
                      <span className={`status-badge badge-${status.toLowerCase()}`} style={{ fontSize: 9, padding: '1px 5px' }}>
                        {status}
                      </span>
                    </div>

                    <div className="dag-task-meta">
                      <span>Role: <strong style={{ color: 'var(--text-primary)' }}>{t.assigneeRole}</strong></span>
                      {taskInst?.stepDurationMs !== undefined && (
                        <span>{taskInst.stepDurationMs}ms</span>
                      )}
                    </div>

                    {taskInst?.startedAt && (
                      <div className="dag-task-time">
                        <span>Start: {new Date(taskInst.startedAt).toLocaleTimeString()}</span>
                        {taskInst.completedAt && (
                          <span>End: {new Date(taskInst.completedAt).toLocaleTimeString()}</span>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* 2D DAG CANVAS */}
        <div
          ref={containerRef}
          className="dag-canvas-flex"
          style={{ cursor: isDraggingRef.current ? 'grabbing' : 'grab' }}
        >
          <canvas
            ref={canvasRef}
            onMouseDown={handleMouseDown}
            onMouseMove={handleMouseMove}
            onMouseUp={handleMouseUp}
            onMouseLeave={handleMouseUp}
            onClick={handleClickCanvas}
            onWheel={handleWheel}
          />

          {/* Hover node tooltip */}
          {hoveredNode && (
            <div
              className="dag-hover-tooltip"
              style={{
                left: Math.min(
                  (containerRef.current?.clientWidth || 500) / 2 + panX + hoveredNode.x * zoom + 16,
                  (containerRef.current?.clientWidth || 500) - 180
                ),
                top: Math.max(
                  10,
                  (containerRef.current?.clientHeight || 400) / 2 + panY + hoveredNode.y * zoom - 24
                )
              }}
            >
              <div style={{ fontWeight: 600, color: '#f0f6fc', marginBottom: 2, fontSize: 11 }}>
                {hoveredNode.name}
              </div>
              <div style={{ fontSize: 10, color: 'var(--text-secondary)' }}>
                Role: <strong>{hoveredNode.role}</strong>
              </div>
              <div style={{ fontSize: 10, color: STATUS_THEMES[hoveredNode.status]?.text || '#8b949e', marginTop: 2 }}>
                Status: <strong>{hoveredNode.status}</strong>
              </div>
            </div>
          )}

          {/* Instructions overlay */}
          <div className="dag-interaction-hint">
            <span>Drag to pan</span>
            <span>·</span>
            <span>Scroll to zoom</span>
          </div>

          {/* Status Legend */}
          <div className="dag-legend">
            <div className="dag-legend-item">
              <span className="dag-legend-dot" style={{ background: '#3fb950' }} />
              <span>Approved</span>
            </div>
            <div className="dag-legend-item">
              <span className="dag-legend-dot" style={{ background: '#e3b341' }} />
              <span>In Progress</span>
            </div>
            <div className="dag-legend-item">
              <span className="dag-legend-dot" style={{ background: '#38bdf8' }} />
              <span>Ready</span>
            </div>
            <div className="dag-legend-item">
              <span className="dag-legend-dot" style={{ background: '#6e7681' }} />
              <span>Pending</span>
            </div>
            <div className="dag-legend-item">
              <span className="dag-legend-dot" style={{ background: '#f85149' }} />
              <span>Rejected</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
