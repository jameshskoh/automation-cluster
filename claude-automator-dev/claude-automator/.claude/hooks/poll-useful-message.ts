import {processUsefulMessage} from "./utils/pubsub-client.ts";
import {readStdin} from "./utils/utils.ts";
import {recordHookCompleted} from "./utils/metrics.ts";

// Truncated form used for the session title/status line — never fed to the agent as an
// instruction, so it's fine to cut mid-sentence.
function titleFrom(text: string): string {
    return text.length > 80 ? `${text.slice(0, 80)}…` : text;
}

async function main() {
    await readStdin();

    // additionalContext is read by the agent as its task, so it must contain nothing but the
    // task itself — no implementation detail (poll counts, stack traces, "hook ran successfully")
    // that could distract it from just answering the question.
    let additionalContext: string;
    let sessionTitle: string;
    let success = true;
    try {
        const result = await processUsefulMessage();
        additionalContext = result.found ? `New request:\n\n${result.text}` : result.text;
        sessionTitle = result.found ? titleFrom(result.text) : result.text;
        await recordHookCompleted("SessionStart", "success");
    } catch (err) {
        console.error(`Failed to poll Pub/Sub: ${err}`);
        additionalContext = "Nothing to do.";
        sessionTitle = "Nothing to do.";
        success = false;
        await recordHookCompleted("SessionStart", "failure", "poll_error");
    }

    process.stdout.write(JSON.stringify({
        systemMessage: "SessionStart hook completed.",
        hookSpecificOutput: {
            hookEventName: "SessionStart",
            additionalContext,
            sessionTitle,
        },
    }));
    process.exit(success ? 0 : 1);
}

await main();

export {}
