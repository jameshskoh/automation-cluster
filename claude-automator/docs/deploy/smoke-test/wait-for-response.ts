import {randomUUID} from "node:crypto";
import {v1} from "@google-cloud/pubsub";
import type {google} from "@google-cloud/pubsub/build/protos/protos.js";

type IReceivedMessage = google.pubsub.v1.IReceivedMessage;

// Waits for the ANSWERED envelope matching a request_id produced by send-message.ts, via a
// temporary subscription on claude-automator-responses (deleted on exit) rather than the
// gateway's real one — Pub/Sub fans out independently per subscription, so this never competes
// with production traffic. See README.md for the IAM prerequisite this implies.

const GCP_PROJECT_ID = requireEnv("GCP_PROJECT_ID");
const RESPONSES_TOPIC = process.env.CLAUDE_AUTOMATOR_RESPONSES_TOPIC_ID ?? "claude-automator-responses";
const TIMEOUT_MS = Number(process.env.SMOKE_TEST_TIMEOUT_MS ?? 120_000);

const expectedRequestId = process.argv[2];
if (!expectedRequestId) {
    console.error("Usage: npx tsx wait-for-response.ts <request_id>");
    process.exit(1);
}

async function main() {
    const subClient = new v1.SubscriberClient();
    const topicPath = subClient.projectTopicsPath(GCP_PROJECT_ID, RESPONSES_TOPIC);
    const subscriptionName = `smoke-test-claude-automator-responses-${randomUUID().slice(0, 8)}`;
    const subscriptionPath = subClient.subscriptionPath(GCP_PROJECT_ID, subscriptionName);

    console.log(`Creating temporary subscription: ${subscriptionName}`);
    await subClient.createSubscription({
        name: subscriptionPath,
        topic: topicPath,
        filter: 'attributes.use_case="QA" AND attributes.stage="ANSWERED"',
        ackDeadlineSeconds: 60,
        // Safety net in case this process is killed before reaching the finally block below.
        // 24h is Pub/Sub's enforced minimum expiration TTL (a shorter value is rejected outright).
        expirationPolicy: {ttl: {seconds: 86400}},
    });

    try {
        const answer = await pollForMatch(subClient, subscriptionPath);
        if (answer == null) {
            console.error(`Timed out after ${TIMEOUT_MS}ms waiting for request_id=${expectedRequestId}`);
            process.exit(1);
        }
        console.log("Answer received:\n" + answer);
    } finally {
        console.log(`Deleting temporary subscription: ${subscriptionName}`);
        await subClient.deleteSubscription({subscription: subscriptionPath});
    }
}

async function pollForMatch(subClient: InstanceType<typeof v1.SubscriberClient>, subscriptionPath: string): Promise<string | null> {
    const deadline = Date.now() + TIMEOUT_MS;

    while (Date.now() < deadline) {
        const [response] = await subClient.pull({subscription: subscriptionPath, maxMessages: 10});
        const rawMessages = response.receivedMessages ?? [];

        if (rawMessages.length === 0) {
            await sleep(2000);
            continue;
        }

        for (const rawMessage of rawMessages) {
            const parsed = deserialize(rawMessage);
            if (parsed?.request_id === expectedRequestId) {
                await subClient.acknowledge({subscription: subscriptionPath, ackIds: [rawMessage.ackId!]});
                return parsed.metadata;
            }
            // Not ours (e.g. a real production answer) — nack so it redelivers here instead of
            // being silently dropped.
            await subClient.modifyAckDeadline({
                subscription: subscriptionPath,
                ackIds: [rawMessage.ackId!],
                ackDeadlineSeconds: 0,
            });
        }
    }
    return null;
}

function deserialize(rawMessage: IReceivedMessage): {request_id: string; metadata: string} | null {
    const data = rawMessage.message!.data;
    const jsonString = typeof data === "string"
        ? Buffer.from(data, "base64").toString("utf-8")
        : Buffer.from(data as Uint8Array).toString("utf-8");
    try {
        return JSON.parse(jsonString);
    } catch {
        return null;
    }
}

function requireEnv(name: string): string {
    const value = process.env[name];
    if (!value) {
        console.error(`Missing required env var: ${name}`);
        process.exit(1);
    }
    return value;
}

const sleep = (ms: number): Promise<void> => new Promise(resolve => setTimeout(resolve, ms));

await main();
