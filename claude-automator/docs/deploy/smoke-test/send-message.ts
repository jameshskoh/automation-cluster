import {randomUUID} from "node:crypto";
import {v1} from "@google-cloud/pubsub";

// Publishes one QA/ASKED envelope to gateway-requests, as the gateway itself would.
// Envelope shape: ../../arch/messaging.md. Schema claude-automator validates against:
// ../../../.claude/hooks/utils/pubsub-client.ts.

const GCP_PROJECT_ID = requireEnv("GCP_PROJECT_ID");
const GATEWAY_REQUESTS_TOPIC = process.env.GATEWAY_REQUESTS_TOPIC_ID ?? "gateway-requests";
const question = process.argv[2] ?? "What is the capital of France?";

async function main() {
    const pubClient = new v1.PublisherClient();
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
    console.log(`request_id: ${requestId}`);
    console.log(`\nNow run: npx tsx wait-for-response.ts ${requestId}`);
}

function requireEnv(name: string): string {
    const value = process.env[name];
    if (!value) {
        console.error(`Missing required env var: ${name}`);
        process.exit(1);
    }
    return value;
}

await main();
