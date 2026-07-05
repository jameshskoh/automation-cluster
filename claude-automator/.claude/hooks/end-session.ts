import "dotenv/config";
import {config} from "../../config.ts";
import {readFileSync} from "node:fs";
import {readStdin} from "./utils/utils.ts";
import {sendMessage} from "./utils/pubsub-client.ts";
import {recordHookCompleted} from "./utils/metrics.ts";

interface StopHookInput {
    session_id: string;
    transcript_path: string;
    cwd: string;
    permission_mode: string;
    hook_event_name: string;
    stop_hook_active: boolean;
    last_assistant_message?: string;
    background_tasks?: unknown[];
    session_crons?: unknown[];
}

async function main() {
    const raw = await readStdin();
    const input: StopHookInput = JSON.parse(raw);

    let success = false;
    try {
        const result = await sendMessage(input.last_assistant_message ?? "No result.");
        success = result.ok;
        if (result.ok) {
            await recordHookCompleted("Stop", "success");
        } else {
            await recordHookCompleted("Stop", "failure", result.reason);
        }
    } catch (err) {
        console.error(`Failed to send message: ${err}`);
        await recordHookCompleted("Stop", "failure", "publish_error");
    }

    try {
        const pid = Number.parseInt(readFileSync(config.PID_FILE, "utf8").trim(), 10);
        process.kill(pid, "SIGTERM");
    } catch {
        // PID file missing or process already gone
    }
    process.exit(success ? 0 : 1);
}

await main();
