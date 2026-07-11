import "dotenv/config";
import {z} from "zod";
import {config} from "../../../config.ts";
import {v1} from "@google-cloud/pubsub";
import type {google} from "@google-cloud/pubsub/build/protos/protos";
import {read, cleanupFile, writeContent} from "./filesystem.ts";

type IReceivedMessage = google.pubsub.v1.IReceivedMessage;

const subClient = new v1.SubscriberClient();
const subscriptionPath = subClient.subscriptionPath(config.GCP_PROJECT_ID, config.PUBSUB_SUBSCRIPTION_ID);

const pubClient = new v1.PublisherClient();
const topicPath = pubClient.projectTopicsPath(config.GCP_PROJECT_ID, config.PUBSUB_TOPIC_ID);

const QA_USE_CASE = "QA";
const ASKED_STAGE = "ASKED";
const ANSWERED_STAGE = "ANSWERED";

// The shared envelope shape (see schemas/gateway_message.proto). Inbound messages are validated
// against this before being acted on — the Pub/Sub subscription filter is a delivery-routing
// optimization, not a data-contract guarantee, so we also assert use_case/stage in the body match
// the stage this service consumes (QA/ASKED). A message that fails validation is nacked so
// Pub/Sub redelivers it and, after max delivery attempts, routes it to the DLQ.
const gatewayMessageSchema = z.object({
    use_case: z.literal(QA_USE_CASE),
    stage: z.literal(ASKED_STAGE),
    request_id: z.string().min(1),
    payload: z.string(),
    metadata: z.string(),
});

type GatewayMessage = z.infer<typeof gatewayMessageSchema>;

export async function processUsefulMessage(): Promise<string> {
    for (let i = 0; i < config.POLL_COUNT; i++) {
        console.debug("Starting")
        const pubSubResult = await pollPubSub();

        if (pubSubResult != null) {
            const {request: message, ackId} = pubSubResult;

            await cleanupFile(config.UUID_PATH);
            await writeContent(config.UUID_PATH, message.request_id)

            await cleanupFile(config.ACK_ID_PATH);
            await writeContent(config.ACK_ID_PATH, ackId)

            return message.metadata;
        }
        console.debug("Got nothing this round. Waiting for the next round ...")
        if (i < config.POLL_COUNT - 1) await sleep(config.POLL_INTERVAL_MS);
    }
    return `Polled ${config.POLL_COUNT} times. Nothing to do now.`;
}

export type SendResult =
    | {ok: true}
    | {ok: false; reason: string};

export async function sendMessage(message: string): Promise<SendResult> {
    const requestId = await read(config.UUID_PATH);
    const ackId = await read(config.ACK_ID_PATH);

    await cleanupFile(config.UUID_PATH);
    await cleanupFile(config.ACK_ID_PATH);

    if (requestId == null) {
        console.error("UUID file is missing — cannot send reply without knowing which question this answers. Aborting.");
        return {ok: false, reason: "uuid_missing"};
    }

    const answer: GatewayMessage = {
        use_case: QA_USE_CASE,
        stage: ANSWERED_STAGE,
        request_id: requestId,
        payload: "",
        metadata: message,
    }
    const data = Buffer.from(JSON.stringify(answer));
    // use_case/stage are set as Pub/Sub message attributes (not just body fields) because the
    // gateway's subscription filter selects on attributes.use_case/attributes.stage. A message
    // missing these attributes matches no filter and is silently dropped by Pub/Sub.
    const [response] = await pubClient.publish({
        topic: topicPath,
        messages: [{data, attributes: {use_case: QA_USE_CASE, stage: ANSWERED_STAGE}}],
    });
    console.log(`Published message ID: ${response.messageIds?.[0]}`);

    if (ackId == null) {
        console.warn("ack-id file is missing — reply was published but original message will not be acknowledged and may redeliver.");
        return {ok: false, reason: "ackid_missing"};
    }

    console.log(`Acknowledging message ID: ${ackId}`);
    await subClient.acknowledge({
        subscription: subscriptionPath,
        ackIds: [ackId],
    });
    console.log("Acknowledged successfully.");
    return {ok: true};
}

const sleep = (ms: number): Promise<void> =>
    new Promise(resolve => setTimeout(resolve, ms));

async function pollPubSub(): Promise<{
    request: GatewayMessage,
    ackId: string
} | null> {
    const [response] = await subClient.pull({
        subscription: subscriptionPath,
        maxMessages: 1,
    });

    const rawMessages = response.receivedMessages ?? [];
    if (rawMessages.length === 0) {
        console.log("No message.");
        return null;
    }

    const rawMessage = rawMessages[0];
    const ackId = rawMessage.ackId!;
    const message = deserialize(rawMessage);

    if (message === null) {
        // Malformed envelope or wrong use_case/stage in the body — the message got past the
        // subscription filter but violates the data contract. Nack it so Pub/Sub redelivers and,
        // after max delivery attempts, routes it to the DLQ rather than silently dropping it.
        console.error("Invalid inbound GatewayMessage, nacking for redelivery/DLQ.");
        await nack(ackId);
        return null;
    }

    if (message.metadata == "") {
        // A well-formed but content-empty message is a benign no-op for this service; ack-drop it.
        await subClient.acknowledge({
            subscription: subscriptionPath,
            ackIds: [ackId],
        });
        return null;
    }

    return {request: message, ackId};
}

// Nacks a message by setting its ack deadline to 0, prompting immediate redelivery (Pub/Sub's
// pull API has no explicit nack; a zero ack-deadline is the equivalent).
async function nack(ackId: string): Promise<void> {
    await subClient.modifyAckDeadline({
        subscription: subscriptionPath,
        ackIds: [ackId],
        ackDeadlineSeconds: 0,
    });
}

// Parses and validates the inbound message against the shared envelope schema. Returns null if the
// payload is not valid JSON or does not satisfy the contract (including a use_case/stage that isn't
// QA/ASKED) — the caller nacks in that case.
function deserialize(payload: IReceivedMessage): GatewayMessage | null {
    const data = payload.message!.data;
    let jsonString: string;
    if (typeof data === "string") {
        jsonString = Buffer.from(data, "base64").toString("utf-8");
    } else {
        jsonString = Buffer.from(data as Uint8Array).toString("utf-8");
    }

    let parsed: unknown;
    try {
        parsed = JSON.parse(jsonString);
    } catch (err) {
        console.error(`Failed to parse inbound message as JSON: ${err}`);
        return null;
    }

    const result = gatewayMessageSchema.safeParse(parsed);
    if (!result.success) {
        console.error("Inbound message failed envelope validation:\n" + result.error.toString());
        return null;
    }
    return result.data;
}
