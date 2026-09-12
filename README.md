# Workflow Automation Engine

A high-performance workflow automation engine featuring a modern DAG (Directed Acyclic Graph) visualizer, a Spring Boot Java backend, and a React-based interactive dashboard. The engine is completely shape-agnostic, capable of orchestrating complex dynamic topologies, branches, N-way fan-outs, and N-way joins.

## What It Does

The Workflow Automation Engine allows teams to define, execute, and monitor distributed business processes (workflows). 

- **Dynamic Task Orchestration**: Instead of hardcoding linear chains, users can define generic Directed Acyclic Graphs (DAGs) representing dependencies between tasks. The engine calculates "readiness" at runtime, only waking up downstream tasks once their dependencies are resolved.
- **Conditional Branching (SpEL)**: Supports Spring Expression Language (SpEL) on edges, enabling true decision branches. If an edge evaluates to false, the system automatically cascades a `SKIPPED` status down the dead path, resolving complex asymmetric DAG joins flawlessly.
- **Robust Concurrency Control**: Uses optimistic locking and idempotent checks to guarantee safe multi-actor approvals, ensuring two managers cannot simultaneously approve/reject the same instance and cause state corruption.
- **Graph Validation**: Built-in Graph Validator uses Kahn's algorithm and Depth-First Search to detect and reject cycles and orphan nodes at authoring time.
- **Real-Time Visibility**: A rich visual canvas allows you to view the workflow's topological graph dynamically rendered in 2D, colored by live execution status.

## Features

- **Generic Workflow Execution**: Supports any DAG topology including N-way joins, N-way fan-outs, asymmetric meshes, and cross-pollinated DAGs without special-casing structural nodes.
- **Topological Visualizer**: Interactive, rotatable 2D graph that displays workflow stages, task dependencies, and real-time execution status (using single vibrant color circles).
- **Real-Time Metrics**: Live Server-Sent Events (SSE) streaming of p50/p99 latency, active DAG instances, and step completion statistics.
- **Modern Dashboard**: A clean, GitHub-inspired dark mode UI. Features a resizable split-pane layout to view workflow task lists alongside the interactive DAG canvas.
- **API Sandbox**: Integrated FastAPI-style interactive API documentation directly within the dashboard.

## Tech Stack

- **Backend**: Java 17, Spring Boot, Maven, H2 Database (Embedded)
- **Frontend**: React, TypeScript, Vite, Recharts
- **Design System**: Custom CSS with GitHub dark mode aesthetics, Flexbox, Canvas API for 2D rendering

## Getting Started

### 1. Run the Backend
Start the Spring Boot backend server, which will initialize the in-memory H2 database, validate the topologies, and expose the REST API at port 8080.
```bash
cd backend
mvn clean package -DskipTests
mvn spring-boot:run
```

### 2. Run the Dashboard
Start the React/Vite development server to serve the interactive web UI.
```bash
cd dashboard
npm install
npm run dev
```

Navigate to `http://localhost:5173` in your web browser to access the dashboard. 

### 3. Running Load Tests (Optional)
If you wish to test concurrency and engine throughput, you can run the provided load test script:
```bash
cd load-test
python3 load_test.py
```
