import "dotenv/config";
import {config} from "../../../config.ts";
import {metrics} from "@opentelemetry/api";
import {
    MeterProvider,
    PeriodicExportingMetricReader,
    AggregationTemporality,
} from "@opentelemetry/sdk-metrics";
import {OTLPMetricExporter} from "@opentelemetry/exporter-metrics-otlp-http";
import {resourceFromAttributes} from "@opentelemetry/resources";
import {ATTR_SERVICE_NAME} from "@opentelemetry/semantic-conventions";

// Keep stable across invocations so metrics accumulate under one service.
const SERVICE_NAME = "claude-code-hooks";

export type HookEvent = "SessionStart" | "Stop";
export type Outcome = "success" | "failure";

/**
 * Record a single hook invocation and its outcome.
 *
 * Emits `claude_hook_completed_total{hook_event, outcome, reason}`:
 *   - throughput = rate() of the whole counter, or by hook_event
 *   - failure rate = filter/aggregate by outcome; `reason` explains failures
 *
 * `reason` is only meaningful for failures; leave undefined on success.
 * Never throws — a metrics outage must not affect the hook's exit code.
 */
export async function recordHookCompleted(
    hookEvent: HookEvent,
    outcome: Outcome,
    reason?: string,
): Promise<void> {
    try {
        const exporter = new OTLPMetricExporter({
            url: config.OTLP_METRICS_URL,
            temporalityPreference: AggregationTemporality.DELTA,
        });

        const reader = new PeriodicExportingMetricReader({
            exporter,
            exportIntervalMillis: 60_000, // irrelevant — forceFlush below fires immediately
        });

        const meterProvider = new MeterProvider({
            resource: resourceFromAttributes({[ATTR_SERVICE_NAME]: SERVICE_NAME}),
            readers: [reader],
        });

        metrics.setGlobalMeterProvider(meterProvider);

        const meter = metrics.getMeter("claude-code-hooks");
        const counter = meter.createCounter("claude_hook_completed_total", {
            description: "Count of completed Claude Code hook invocations by outcome",
        });

        counter.add(1, {
            hook_event: hookEvent,
            outcome,
            reason: outcome === "failure" ? (reason ?? "unknown") : "",
        });

        await reader.forceFlush();
        await meterProvider.shutdown();
    } catch (err) {
        // Never let a metrics failure affect the hook's own exit code / behavior.
        console.error("metrics push failed:", err);
    }
}
