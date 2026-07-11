import {readFile, unlink, writeFile} from 'node:fs/promises';


export async function read(filePath: string): Promise<string | null> {
    console.log(`Reading from file ${filePath}`);
    try {
        const content = await readFile(filePath, 'utf-8');
        console.log(`File ${filePath} read successfully.`);
        return content;
    } catch {
        return null;
    }
}

export async function cleanupFile(filePath: string): Promise<void> {
    let content: string | null = await read(filePath);
    if (content == null) {
        console.log(`File ${filePath} is already cleaned up.`);
        return;
    } else if (content == "") {
        console.warn(`File ${filePath} not cleaned up. File is empty. Proceeding to clean up.`);
    } else {
        console.warn(`File ${filePath} not cleaned up. Content is '${content}'. Proceeding to clean up.`);
    }
    await deleteFile(filePath);
    console.log(`File ${filePath} deleted successfully.`);
}

export async function writeContent(filePath: string, content: string): Promise<boolean> {
    console.log(`Writing to file ${filePath}`);
    try {
        await writeFile(filePath, content, 'utf-8');
        console.log("Writing done.")
        return true;
    }
    catch {
        console.error(`Unable to write to file ${filePath}`);
        return false;
    }
}

async function deleteFile(filePath: string): Promise<boolean> {
    console.log(`Deleting file ${filePath}`);
    try {
        await unlink(filePath);
        console.log(`File ${filePath} deleted successfully`);
        return true;
    } catch {
        console.error(`Unable to delete file ${filePath}`);
        return false;
    }
}
