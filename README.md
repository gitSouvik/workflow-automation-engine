# Workflow Automation Engine

A high-performance workflow automation engine featuring a modern DAG (Directed Acyclic Graph) visualizer, a Spring Boot Java backend, and a React-based interactive dashboard.

## Features

- **Workflow Orchestration**: Define complex workflows using a DAG structure.
- **DAG Visualizer**: Interactive, rotatable 2D graph that displays workflow stages, task dependencies, and real-time execution status.
- **Real-Time Metrics**: Live Server-Sent Events (SSE) streaming of p50/p99 latency, active DAG instances, and step completion statistics.
- **Modern Dashboard**: A clean, GitHub-inspired dark mode UI. Features a resizable split-pane layout to view workflow task lists alongside the interactive DAG canvas.
- **API Sandbox**: Integrated FastAPI-style interactive API documentation directly within the dashboard.

## Tech Stack

- **Backend**: Java 17, Spring Boot, Maven
- **Frontend**: React, TypeScript, Vite, Recharts
- **Design System**: Custom CSS with GitHub dark mode aesthetics, Flexbox, Canvas API for 2D rendering

## Quick Start

### 1. Run the Backend
```bash
cd backend
mvn clean package -DskipTests
mvn spring-boot:run
```

### 2. Run the Dashboard
```bash
cd dashboard
npm install
npm run dev
```

Navigate to `http://localhost:5173` to access the dashboard.
