import {randomUUID} from "node:crypto";
import {v1} from "@google-cloud/pubsub";
import type {google} from "@google-cloud/pubsub/build/protos/protos.js";

type IReceivedMessage = google.pubsub.v1.IReceivedMessage;

// Publishes one QA/ASKED envelope to gateway-requests, as the gateway itself would, then waits for
// the matching QA/ANSWERED reply on claude-automator-responses — confirms a deployment (see
// ../README.md) works end-to-end. Envelope shape: ../../arch/messaging.md. Schema
// claude-automator validates against: ../../../.claude/hooks/utils/pubsub-client.ts.
//
// The temporary subscription is created *before* publishing: Pub/Sub subscriptions never receive
// messages published before they existed, so subscribing first is what makes a fast answer
// impossible to miss.
//
// Run from an operator's own authenticated `gcloud`/ADC session, **not** from inside the deployed
// container: this needs subscription create/delete rights, broader than what the deployed service
// itself needs (publish/pull/ack only).

const GCP_PROJECT_ID = requireEnv("GCP_PROJECT_ID");
const GATEWAY_REQUESTS_TOPIC = process.env.GATEWAY_REQUESTS_TOPIC_ID ?? "gateway-requests";
const RESPONSES_TOPIC = process.env.CLAUDE_AUTOMATOR_RESPONSES_TOPIC_ID ?? "claude-automator-responses";
const TIMEOUT_MS = Number(process.env.SMOKE_TEST_TIMEOUT_MS ?? 120_000);
const question = process.argv[2] ?? "What is the capital of France?";

async function main() {
    const subClient = new v1.SubscriberClient();
    const pubClient = new v1.PublisherClient();

    const responsesTopicPath = subClient.projectTopicsPath(GCP_PROJECT_ID, RESPONSES_TOPIC);
    const subscriptionName = `smoke-test-claude-automator-responses-${randomUUID().slice(0, 8)}`;
    const subscriptionPath = subClient.subscriptionPath(GCP_PROJECT_ID, subscriptionName);

    console.log(`Creating temporary subscription: ${subscriptionName}`);
    await subClient.createSubscription({
        name: subscriptionPath,
        topic: responsesTopicPath,
        filter: 'attributes.use_case="QA" AND attributes.stage="ANSWERED"',
        ackDeadlineSeconds: 60,
        // Safety net in case this process is killed before reaching the finally block below.
        // 24h is Pub/Sub's enforced minimum expiration TTL (a shorter value is rejected outright).
        expirationPolicy: {ttl: {seconds: 86400}},
    });

    try {
        const requestId = await publishQuestion(pubClient);
        console.log(`Published question. request_id: ${requestId}`);

        const answer = await pollForMatch(subClient, subscriptionPath, requestId);
        if (answer == null) {
            console.error(`Timed out after ${TIMEOUT_MS}ms waiting for request_id=${requestId}`);
            process.exit(1);
        }
        console.log("Answer received:\n" + answer);
    } finally {
        console.log(`Deleting temporary subscription: ${subscriptionName}`);
        await subClient.deleteSubscription({subscription: subscriptionPath});
    }
}

async function publishQuestion(pubClient: InstanceType<typeof v1.PublisherClient>): Promise<string> {
    const topicPath = pubClient.projectTopicsPath(GCP_PROJECT_ID, GATEWAY_REQUESTS_TOPIC);
    const requestId = randomUUID();
    const envelope = {
        use_case: "QA",
        stage: "ASKED",
        request_id: requestId,
        payload: "",
        metadata: question,
    };

    const [response] = await pubClient.publish({
        topic: topicPath,
        messages: [{
            data: Buffer.from(JSON.stringify(envelope)),
            attributes: {use_case: "QA", stage: "ASKED"},
        }],
    });
    console.log(`Published message ID: ${response.messageIds?.[0]}`);
    return requestId;
}

async function pollForMatch(
    subClient: InstanceType<typeof v1.SubscriberClient>,
    subscriptionPath: string,
    expectedRequestId: string,
): Promise<string | null> {
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
