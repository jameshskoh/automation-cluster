import {processUsefulMessage} from "./utils/pubsub-client.ts";
import {readStdin} from "./utils/utils.ts";
import {recordHookCompleted} from "./utils/metrics.ts";

async function main() {
    await readStdin();

    let additionalContext;
    let success = true;
    try {
        additionalContext = await processUsefulMessage();
        await recordHookCompleted("SessionStart", "success");
    } catch (err) {
        additionalContext = `Failed to poll Pub/Sub: ${err}`;
        success = false;
        await recordHookCompleted("SessionStart", "failure", "poll_error");
    }

    process.stdout.write(JSON.stringify({
        systemMessage: `SessionStart hook ran successfully. Context: ${additionalContext}`,
        hookSpecificOutput: {
            hookEventName: "SessionStart",
            additionalContext,
            sessionTitle: additionalContext,
        },
    }));
    process.exit(success ? 0 : 1);
}

await main();

export {}
