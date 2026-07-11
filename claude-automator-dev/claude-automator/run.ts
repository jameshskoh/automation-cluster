import "dotenv/config";
import {config} from "./config.ts";
import {spawn} from "node:child_process";
import {writeFileSync} from "node:fs";

async function runSession(): Promise<void> {
    return new Promise((resolve) => {
        const child = spawn("claude", ["Work on the request provided by SessionStart. Write your answer to the question directly in chat. Do nothing else."], {stdio: "inherit"});
        writeFileSync(config.PID_FILE, String(child.pid));
        child.on("exit", () => resolve());
    });
}

async function main() {
    while (true) {
        await runSession();
    }
}

await main();
