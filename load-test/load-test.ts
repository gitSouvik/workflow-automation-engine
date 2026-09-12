/**
 * WORKFLOW ENGINE LOAD TEST HARNESS
 *
 * Spins up 1,000 concurrent workflow instances and drives them through to completion.
 * Records per-step latency and computes p50/p99.
 *
 * Usage:
 *   npm install
 *   npx ts-node load-test.ts [--instances 1000] [--concurrency 50] [--baseUrl http://localhost:8080]
 *
 * Output:
 *   - Live progress to stdout
 *   - results.json with full latency data
 *   - Summary table with p50/p99 per workflow step
 */

import axios, { AxiosInstance } from "axios";
import * as fs from "fs";

// ---- Config ----
const BASE_URL = process.argv.includes("--baseUrl")
  ? process.argv[process.argv.indexOf("--baseUrl") + 1]
  : "http://localhost:8080";

const INSTANCE_COUNT = process.argv.includes("--instances")
  ? parseInt(process.argv[process.argv.indexOf("--instances") + 1])
  : 1000;

const CONCURRENCY = process.argv.includes("--concurrency")
  ? parseInt(process.argv[process.argv.indexOf("--concurrency") + 1])
  : 50;

const MAX_RETRIES = 3;
const RETRY_BASE_MS = 100;
const THINK_TIME_MS = { min: 20, max: 80 }; // Simulated human think time between approvals

// ---- Types ----
interface TaskInstance {
  id: string;
  taskKey: string;
  taskName: string;
  assigneeRole: string;
  status: string;
  startedAt?: string;
}

interface WorkflowInstance {
  id: string;
  status: string;
  taskInstances: TaskInstance[];
}

interface StepLatency {
  instanceId: string;
  taskKey: string;
  durationMs: number;
  timestamp: number;
}

interface LoadTestResults {
  totalInstances: number;
  completed: number;
  rejected: number;
  failed: number;
  stepLatencies: StepLatency[];
  p50Ms: number;
  p99Ms: number;
  durationMs: number;
  retryCount: number;
  conflictCount: number;
}

// ---- HTTP client ----
const http: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
});

// ---- Utilities ----
function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}

function randomThinkTime(): number {
  return THINK_TIME_MS.min + Math.random() * (THINK_TIME_MS.max - THINK_TIME_MS.min);
}

function computePercentile(sorted: number[], p: number): number {
  if (sorted.length === 0) return 0;
  const idx = Math.ceil((p / 100) * sorted.length) - 1;
  return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
}

// ---- Retry with exponential backoff ----
async function withRetry<T>(
  fn: () => Promise<T>,
  retries: number = MAX_RETRIES,
  onConflict?: () => void
): Promise<T> {
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      return await fn();
    } catch (err: any) {
      if (err.response?.status === 409) {
        if (onConflict) onConflict();
        if (attempt < retries) {
          const delay = RETRY_BASE_MS * Math.pow(2, attempt) + Math.random() * 50;
          await sleep(delay);
          continue;
        }
      }
      throw err;
    }
  }
  throw new Error("Max retries exceeded");
}

// ---- Limit concurrency without external library ----
class Semaphore {
  private queue: (() => void)[] = [];
  private running = 0;

  constructor(private limit: number) {}

  async acquire(): Promise<void> {
    if (this.running < this.limit) {
      this.running++;
      return;
    }
    await new Promise<void>((resolve) => this.queue.push(resolve));
    this.running++;
  }

  release(): void {
    this.running--;
    const next = this.queue.shift();
    if (next) next();
  }
}

// ---- Core load test logic ----
let totalConflicts = 0;
let totalRetries = 0;
let completedInstances = 0;
const allLatencies: StepLatency[] = [];

async function runSingleWorkflow(
  definitionId: string,
  instanceNum: number
): Promise<"completed" | "rejected" | "failed"> {
  try {
    // Start instance
    const startRes = await http.post<WorkflowInstance>("/api/instances", {
      definitionId,
      title: `Load Test Instance #${instanceNum}`,
      initiatorId: `load-tester-${instanceNum % 10}`,
    });
    const instanceId = startRes.data.id;

    // Drive through all tasks until workflow is complete
    let attempts = 0;
    while (attempts < 30) {
      attempts++;
      await sleep(randomThinkTime());

      // Get current task states
      const tasksRes = await http.get<TaskInstance[]>(`/api/instances/${instanceId}/tasks`);
      const tasks = tasksRes.data;

      // Find IN_PROGRESS tasks to approve
      const inProgress = tasks.filter((t) => t.status === "IN_PROGRESS");

      if (inProgress.length === 0) {
        // Check workflow status
        const instRes = await http.get<WorkflowInstance>(`/api/instances/${instanceId}`);
        if (instRes.data.status !== "RUNNING") {
          completedInstances++;
          if (completedInstances % 50 === 0) {
            process.stdout.write(`\r  Progress: ${completedInstances}/${INSTANCE_COUNT} instances completed`);
          }
          return instRes.data.status === "COMPLETED" ? "completed" : "rejected";
        }
        // Still running, wait for scheduler to claim READY tasks
        await sleep(200);
        continue;
      }

      // Approve all in-progress tasks
      for (const task of inProgress) {
        const approvalStart = Date.now();
        try {
          await withRetry(
            () =>
              http.post(`/api/approvals/${task.id}`, {
                actorId: `approver-${task.assigneeRole}-${instanceNum % 5}`,
                action: "APPROVE",
                comment: `Load test approval for ${task.taskKey}`,
              }),
            MAX_RETRIES,
            () => {
              totalConflicts++;
              totalRetries++;
            }
          );
          const durationMs = Date.now() - approvalStart;
          allLatencies.push({
            instanceId,
            taskKey: task.taskKey,
            durationMs,
            timestamp: approvalStart,
          });
        } catch (err: any) {
          // Already approved by someone else — that's fine
          if (err.response?.status !== 409 && err.response?.status !== 400) {
            throw err;
          }
        }
      }
    }

    return "failed";
  } catch (err: any) {
    console.error(`\nInstance ${instanceNum} failed: ${err.message}`, err.response?.data);
    return "failed";
  }
}

// ---- Workflow definition setup ----
async function createWorkflowDefinition(): Promise<string> {
  // 4-step workflow with diamond pattern: A → (B, C parallel) → D
  const defRes = await http.post("/api/definitions", {
    name: "Load Test Workflow",
    description: "4-step workflow for load testing with parallel tasks",
    tasks: [
      { taskKey: "SUBMIT", name: "Submit Request", assigneeRole: "SUBMITTER", displayOrder: 1 },
      { taskKey: "MANAGER_REVIEW", name: "Manager Review", assigneeRole: "MANAGER", displayOrder: 2 },
      { taskKey: "FINANCE_CHECK", name: "Finance Check", assigneeRole: "FINANCE", displayOrder: 2 },
      { taskKey: "FINAL_APPROVAL", name: "Final Approval", assigneeRole: "DIRECTOR", displayOrder: 3 },
    ],
    dependencies: [
      { fromTaskKey: "SUBMIT", toTaskKey: "MANAGER_REVIEW" },
      { fromTaskKey: "SUBMIT", toTaskKey: "FINANCE_CHECK" },
      { fromTaskKey: "MANAGER_REVIEW", toTaskKey: "FINAL_APPROVAL" },
      { fromTaskKey: "FINANCE_CHECK", toTaskKey: "FINAL_APPROVAL" },
    ],
  });
  return defRes.data.id;
}

// ---- Main ----
async function main() {
  console.log("===========================================");
  console.log("  WORKFLOW ENGINE LOAD TEST HARNESS");
  console.log("===========================================");
  console.log(`  Target:      ${BASE_URL}`);
  console.log(`  Instances:   ${INSTANCE_COUNT}`);
  console.log(`  Concurrency: ${CONCURRENCY}`);
  console.log("===========================================\n");

  // Verify server is up
  try {
    await http.get("/api/metrics/summary");
    console.log("✓ Server is reachable\n");
  } catch {
    console.error("✗ Cannot reach server at " + BASE_URL);
    console.error("  Make sure the backend is running: cd backend && mvn spring-boot:run");
    process.exit(1);
  }

  // Create workflow definition
  console.log("Creating workflow definition (4-step diamond pattern)...");
  const definitionId = await createWorkflowDefinition();
  console.log(`✓ Definition created: ${definitionId}\n`);

  // Run load test
  const startTime = Date.now();
  const sem = new Semaphore(CONCURRENCY);
  const results: Array<"completed" | "rejected" | "failed"> = [];

  console.log(`Starting ${INSTANCE_COUNT} concurrent workflow instances...`);

  const tasks = Array.from({ length: INSTANCE_COUNT }, async (_, i) => {
    await sem.acquire();
    try {
      const result = await runSingleWorkflow(definitionId, i + 1);
      results.push(result);
    } finally {
      sem.release();
    }
  });

  await Promise.all(tasks);
  const durationMs = Date.now() - startTime;

  console.log("\n");

  // Compute statistics
  const durations = allLatencies.map((l) => l.durationMs).sort((a, b) => a - b);
  const p50 = computePercentile(durations, 50);
  const p99 = computePercentile(durations, 99);

  const completed = results.filter((r) => r === "completed").length;
  const rejected = results.filter((r) => r === "rejected").length;
  const failed = results.filter((r) => r === "failed").length;

  // Per-task-key latency breakdown
  const taskKeyLatencies: Record<string, number[]> = {};
  for (const lat of allLatencies) {
    if (!taskKeyLatencies[lat.taskKey]) taskKeyLatencies[lat.taskKey] = [];
    taskKeyLatencies[lat.taskKey].push(lat.durationMs);
  }

  // Print results
  console.log("===========================================");
  console.log("  LOAD TEST RESULTS");
  console.log("===========================================");
  console.log(`  Total instances:   ${INSTANCE_COUNT}`);
  console.log(`  Completed:         ${completed}`);
  console.log(`  Rejected:          ${rejected}`);
  console.log(`  Failed:            ${failed}`);
  console.log(`  Total duration:    ${(durationMs / 1000).toFixed(1)}s`);
  console.log(`  Throughput:        ${(INSTANCE_COUNT / (durationMs / 1000)).toFixed(1)} workflows/sec`);
  console.log(`  Total steps:       ${durations.length}`);
  console.log(`  Conflicts (409):   ${totalConflicts}`);
  console.log(`  Retries:           ${totalRetries}`);
  console.log("");
  console.log("  STEP LATENCY (all steps combined):");
  console.log(`    p50:  ${p50}ms`);
  console.log(`    p99:  ${p99}ms`);
  console.log(`    min:  ${durations[0] ?? 0}ms`);
  console.log(`    max:  ${durations[durations.length - 1] ?? 0}ms`);
  console.log("");
  console.log("  LATENCY BY TASK KEY:");
  for (const [key, lats] of Object.entries(taskKeyLatencies)) {
    const sorted = lats.sort((a, b) => a - b);
    console.log(
      `    ${key.padEnd(20)} p50=${computePercentile(sorted, 50)}ms  p99=${computePercentile(sorted, 99)}ms  n=${sorted.length}`
    );
  }
  console.log("===========================================");

  // Write results JSON for dashboard
  const output: LoadTestResults = {
    totalInstances: INSTANCE_COUNT,
    completed,
    rejected,
    failed,
    stepLatencies: allLatencies,
    p50Ms: p50,
    p99Ms: p99,
    durationMs,
    retryCount: totalRetries,
    conflictCount: totalConflicts,
  };

  fs.writeFileSync("results.json", JSON.stringify(output, null, 2));
  console.log("\n✓ Full results written to results.json");
}

main().catch((err) => {
  console.error("Load test failed:", err);
  process.exit(1);
});
