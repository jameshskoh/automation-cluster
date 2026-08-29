import "dotenv/config";
import {z} from "zod";
import {config} from "../../../config.ts";
import {v1} from "@google-cloud/pubsub";
import type {google} from "@google-cloud/pubsub/build/protos/protos";
import {read, cleanupFile, writeContent} from "./filesystem.ts";

type IReceivedMessage = google.pubsub.v1.IReceivedMessage;

const subClient = new v1.SubscriberClient();
const pubClient = new v1.PublisherClient();

const QA_USE_CASE = "QA";
const WEATHER_USE_CASE = "WEATHER";
const ASKED_STAGE = "ASKED";
const FETCHED_STAGE = "FETCHED";
const ANSWERED_STAGE = "ANSWERED";

type UseCase = typeof QA_USE_CASE | typeof WEATHER_USE_CASE;

const topicPath = pubClient.projectTopicsPath(config.GCP_PROJECT_ID, config.PUBSUB_TOPIC_ID);

// QA on gateway-requests, WEATHER on weather-svc-results, polled QA-first each round
// (arch/messaging.md). The 1:1 use_case -> subscription mapping also routes sendMessage()'s ack,
// keyed off USE_CASE_PATH (arch/disk-correlation.md).
const SUBSCRIPTIONS: Record<UseCase, string> = {
    [QA_USE_CASE]: subClient.subscriptionPath(config.GCP_PROJECT_ID, config.PUBSUB_SUBSCRIPTION_ID),
    [WEATHER_USE_CASE]: subClient.subscriptionPath(config.GCP_PROJECT_ID, config.PUBSUB_WEATHER_SUBSCRIPTION_ID),
};
const POLL_ORDER: UseCase[] = [QA_USE_CASE, WEATHER_USE_CASE];

// Shared envelope shape (schemas/gateway_message.proto), covering both use cases this service
// consumes. The subscription filter routes delivery but isn't a data-contract guarantee, so we
// re-validate the body's (use_case, stage) pair here. A failure is nacked → redelivered → DLQ after
// max delivery attempts.
const gatewayMessageSchema = z.discriminatedUnion("use_case", [
    z.object({
        use_case: z.literal(QA_USE_CASE),
        stage: z.literal(ASKED_STAGE),
        request_id: z.string().min(1),
        payload: z.string(),
        metadata: z.string(),
    }),
    z.object({
        use_case: z.literal(WEATHER_USE_CASE),
        stage: z.literal(FETCHED_STAGE),
        request_id: z.string().min(1),
        payload: z.string(),
        metadata: z.string(),
    }),
]);

type GatewayMessage = z.infer<typeof gatewayMessageSchema>;

// Its own type, not reused from GatewayMessage: the inbound union's `stage` is ASKED/FETCHED, but
// outbound always stamps ANSWERED — which has no place in the inbound schema (nothing inbound is
// ever ANSWERED).
interface OutboundMessage {
    use_case: UseCase;
    stage: typeof ANSWERED_STAGE;
    request_id: string;
    payload: string;
    metadata: string;
}

// Handed to the agent: `found` distinguishes a real task (text = assembled context) from the
// no-task case (text = the fixed "Nothing to do." sentinel), so callers never pattern-match on
// content to tell them apart.
export type PollResult =
    | {found: true; text: string}
    | {found: false; text: "Nothing to do."};

export async function processUsefulMessage(): Promise<PollResult> {
    for (let i = 0; i < config.POLL_COUNT; i++) {
        console.debug("Starting")
        const pubSubResult = await pollNextMessage();

        if (pubSubResult != null) {
            const {request: message, ackId} = pubSubResult;

            await cleanupFile(config.UUID_PATH);
            await writeContent(config.UUID_PATH, message.request_id)

            await cleanupFile(config.ACK_ID_PATH);
            await writeContent(config.ACK_ID_PATH, ackId)

            await cleanupFile(config.USE_CASE_PATH);
            await writeContent(config.USE_CASE_PATH, message.use_case)

            return {found: true, text: assembleContext(message)};
        }
        console.debug("Got nothing this round. Waiting for the next round ...")
        if (i < config.POLL_COUNT - 1) await sleep(config.POLL_INTERVAL_MS);
    }
    // Poll-count/interval are operational tuning the agent shouldn't see or reason about — it only
    // needs to know there's no task.
    return {found: false, text: "Nothing to do."};
}

// QA hands the agent `metadata` (the question) alone; WEATHER adds `payload` (the weather JSON)
// under an XML-tag delimiter, since it carries both an interpretation prompt and data. The tag
// names are claude-automator's own choice, not a shared contract with weather-svc — see
// docs/use-cases/weather.md's "Alignment note".
function assembleContext(message: GatewayMessage): string {
    if (message.use_case === WEATHER_USE_CASE) {
        return `<prompt>${message.metadata}</prompt><data>${message.payload}</data>`;
    }
    return message.metadata;
}

export type SendResult =
    | {ok: true}
    | {ok: false; reason: string};

export async function sendMessage(message: string): Promise<SendResult> {
    const requestId = await read(config.UUID_PATH);
    const ackId = await read(config.ACK_ID_PATH);
    const useCaseRaw = await read(config.USE_CASE_PATH);

    await cleanupFile(config.UUID_PATH);
    await cleanupFile(config.ACK_ID_PATH);
    await cleanupFile(config.USE_CASE_PATH);

    if (requestId == null) {
        console.error("UUID file is missing — cannot send reply without knowing which question this answers. Aborting.");
        return {ok: false, reason: "uuid_missing"};
    }

    const useCase = asUseCase(useCaseRaw);
    if (useCase == null) {
        console.error(`use_case file is missing or unrecognized (value=${JSON.stringify(useCaseRaw)}) — cannot determine outbound use_case or which subscription to acknowledge. Aborting.`);
        return {ok: false, reason: "use_case_missing"};
    }

    const answer: OutboundMessage = {
        use_case: useCase,
        stage: ANSWERED_STAGE,
        request_id: requestId,
        payload: "",
        metadata: message,
    }
    const data = Buffer.from(JSON.stringify(answer));
    // use_case/stage go in Pub/Sub attributes (not just the body) because consumers' subscription
    // filters select on attributes.*; a message missing them matches no filter and Pub/Sub silently
    // drops it.
    const [response] = await pubClient.publish({
        topic: topicPath,
        messages: [{data, attributes: {use_case: useCase, stage: ANSWERED_STAGE}}],
    });
    console.log(`Published message ID: ${response.messageIds?.[0]}`);

    if (ackId == null) {
        console.warn("ack-id file is missing — reply was published but original message will not be acknowledged and may redeliver.");
        return {ok: false, reason: "ackid_missing"};
    }

    // Ack on the subscription matching the inbound use_case: the 1:1 mapping
    // (arch/disk-correlation.md) is what acks a WEATHER message on the WEATHER subscription, not QA.
    const subscriptionPath = SUBSCRIPTIONS[useCase];
    console.log(`Acknowledging message ID: ${ackId} on subscription: ${subscriptionPath}`);
    await subClient.acknowledge({
        subscription: subscriptionPath,
        ackIds: [ackId],
    });
    console.log("Acknowledged successfully.");
    return {ok: true};
}

function asUseCase(value: string | null): UseCase | null {
    if (value === QA_USE_CASE || value === WEATHER_USE_CASE) return value;
    return null;
}

const sleep = (ms: number): Promise<void> =>
    new Promise(resolve => setTimeout(resolve, ms));

// One message per round regardless of source subscription — preserves the single-session-at-a-time
// shape. POLL_ORDER (QA, then WEATHER) sets priority; the first non-empty subscription wins.
async function pollNextMessage(): Promise<{
    request: GatewayMessage,
    ackId: string
} | null> {
    for (const useCase of POLL_ORDER) {
        const result = await pollPubSub(SUBSCRIPTIONS[useCase]);
        if (result != null) return result;
    }
    return null;
}

async function pollPubSub(subscriptionPath: string): Promise<{
    request: GatewayMessage,
    ackId: string
} | null> {
    const [response] = await subClient.pull({
        subscription: subscriptionPath,
        maxMessages: 1,
    });

    const rawMessages = response.receivedMessages ?? [];
    if (rawMessages.length === 0) {
        console.log(`No message on ${subscriptionPath}.`);
        return null;
    }

    const rawMessage = rawMessages[0];
    const ackId = rawMessage.ackId!;
    const message = deserialize(rawMessage);

    if (message === null) {
        // Got past the subscription filter but violates the data contract (malformed, or wrong
        // use_case/stage). Nack → redeliver → DLQ after max attempts, rather than silently drop.
        console.error(`Invalid inbound GatewayMessage on ${subscriptionPath}, nacking for redelivery/DLQ.`);
        await nack(subscriptionPath, ackId);
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

// Pub/Sub's pull API has no explicit nack; a zero ack-deadline is the equivalent, forcing immediate
// redelivery.
async function nack(subscriptionPath: string, ackId: string): Promise<void> {
    await subClient.modifyAckDeadline({
        subscription: subscriptionPath,
        ackIds: [ackId],
        ackDeadlineSeconds: 0,
    });
}

// null if the payload isn't valid JSON or fails the envelope contract (including a use_case/stage
// pair that isn't QA/ASKED or WEATHER/FETCHED); the caller nacks in that case.
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
