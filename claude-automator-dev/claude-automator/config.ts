import {z} from "zod";

const schema = z.object({
    GCP_PROJECT_ID: z.string().min(1),
    PUBSUB_TOPIC_ID: z.string().min(1),
    PUBSUB_SUBSCRIPTION_ID: z.string().min(1),
    UUID_PATH: z.string().min(1),
    ACK_ID_PATH: z.string().min(1),
    PID_FILE: z.string().min(1),
    POLL_INTERVAL_MS: z.coerce.number().int().positive(),
    POLL_COUNT: z.coerce.number().int().positive(),
    OTLP_METRICS_URL: z.url(),
});

const parsed = schema.safeParse(process.env);
if (!parsed.success) {
    console.error("Invalid config:\n" + parsed.error.toString());
    process.exit(1);
}

export const config = parsed.data;
