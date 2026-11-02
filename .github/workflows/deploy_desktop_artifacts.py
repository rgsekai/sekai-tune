import os
import sys
import glob
import asyncio
import subprocess
from telethon import TelegramClient
from telethon.errors import FloodWaitError


class UploadProgress:
    def __init__(self, filename: str):
        self.filename = filename
        self.last_percent = -1

    def __call__(self, current: int, total: int):
        if total <= 0:
            return
        percent = int((current / total) * 100)
        bucket = (percent // 10) * 10
        if bucket != self.last_percent and bucket <= 100:
            self.last_percent = bucket
            curr_mb = current / (1024.0 * 1024.0)
            tot_mb = total / (1024.0 * 1024.0)
            print(f"[{self.filename}] Uploading: {bucket}% ({curr_mb:.1f} / {tot_mb:.1f} MB)")


def get_commit_short_sha() -> str:
    commit_sha = os.getenv("COMMIT_SHA", "").strip()
    if commit_sha:
        return commit_sha[:7]
    try:
        output = subprocess.check_output(
            ["git", "log", "-1", "--pretty=format:%h"],
            stderr=subprocess.DEVNULL
        ).decode("utf-8").strip()
        if output:
            return output
    except Exception:
        pass
    return "unknown"


def resolve_files(dist_dir: str):
    exe_candidates = sorted(glob.glob(os.path.join(dist_dir, "*.exe")))
    zip_candidates = sorted(glob.glob(os.path.join(dist_dir, "*-windows-portable.zip")))

    selected = []
    if exe_candidates:
        selected.append(exe_candidates[0])
    else:
        print(f"Warning: No .exe found in {dist_dir}", file=sys.stderr)

    if zip_candidates:
        selected.append(zip_candidates[0])
    else:
        print(f"Warning: No *-windows-portable.zip found in {dist_dir}", file=sys.stderr)

    return selected, exe_candidates


async def send_file_with_retry(client: TelegramClient, chat_id: int, file_path: str, caption: str, topic_id: str | None) -> bool:
    filename = os.path.basename(file_path)
    max_connection_retries = 1
    attempt = 0

    while True:
        attempt += 1
        progress = UploadProgress(filename)
        send_kwargs = {
            "entity": chat_id,
            "file": file_path,
            "caption": caption,
            "parse_mode": "markdown",
            "force_document": True,
            "progress_callback": progress,
        }
        if topic_id:
            try:
                send_kwargs["reply_to"] = int(topic_id)
            except ValueError:
                pass

        try:
            print(f"Starting upload for {filename}...")
            await client.send_file(**send_kwargs)
            print(f"Successfully uploaded {filename}")
            return True
        except FloodWaitError as e:
            wait_seconds = int(e.seconds)
            if wait_seconds <= 120:
                print(f"FloodWait encountered for {filename}: waiting {wait_seconds}s (limit: 120s)...")
                await asyncio.sleep(wait_seconds)
                continue
            print(f"Error: FloodWait requested {wait_seconds}s, exceeding maximum allowed 120s limit.", file=sys.stderr)
            return False
        except (ConnectionError, OSError, asyncio.TimeoutError) as e:
            print(f"Connection error while sending {filename} (attempt {attempt}): {e}")
            if attempt <= max_connection_retries:
                print(f"Retrying upload for {filename} once in 5 seconds...")
                await asyncio.sleep(5)
                continue
            print(f"Error: Connection retry exhausted for {filename}.", file=sys.stderr)
            return False
        except Exception as e:
            print(f"Error sending {filename}: {e}", file=sys.stderr)
            return False


async def main():
    api_id_raw = os.getenv("API_ID")
    api_hash = os.getenv("API_HASH")
    bot_token = os.getenv("BOT_TOKEN")
    chat_id_raw = os.getenv("CHAT_ID")
    topic_id = os.getenv("TOPIC_ID")

    if not api_id_raw or not api_hash or not bot_token or not chat_id_raw:
        print("Error: Missing one or more required Telegram credentials (API_ID, API_HASH, BOT_TOKEN, CHAT_ID).", file=sys.stderr)
        sys.exit(1)

    try:
        api_id = int(api_id_raw)
        chat_id = int(chat_id_raw)
    except ValueError as e:
        print(f"Error: Invalid integer format for API_ID or CHAT_ID: {e}", file=sys.stderr)
        sys.exit(1)

    dist_dir = os.getenv("DESKTOP_DIST_DIR", "desktop/desktopApp/build/distributions")
    files_to_send, exe_candidates = resolve_files(dist_dir)

    if not files_to_send:
        print(f"Error: No desktop packages (.exe or *-windows-portable.zip) found in {dist_dir}.", file=sys.stderr)
        sys.exit(1)

    version = os.getenv("VERSION", "").strip()
    if not version and exe_candidates:
        exe_name = os.path.basename(exe_candidates[0])
        if exe_name.startswith("SekaiTune-") and exe_name.endswith(".exe"):
            version = exe_name[len("SekaiTune-"):-len(".exe")]
    if not version:
        version = "unknown"

    commit_short_sha = get_commit_short_sha()

    max_size_mb = 2000.0
    for file_path in files_to_send:
        file_size_mb = os.path.getsize(file_path) / (1024.0 * 1024.0)
        if file_size_mb > max_size_mb:
            print(f"Error: File {os.path.basename(file_path)} size ({file_size_mb:.2f} MB) exceeds maximum allowed limit of {max_size_mb:.0f} MB.", file=sys.stderr)
            sys.exit(1)

    client = TelegramClient("bot_session", api_id, api_hash)
    await client.start(bot_token=bot_token)

    overall_success = True
    try:
        for file_path in files_to_send:
            size_mb = os.path.getsize(file_path) / (1024.0 * 1024.0)
            caption = (
                f"**{os.path.basename(file_path)}**\n"
                f"Version: `{version}`\n"
                f"Size: `{size_mb:.2f} MB`\n"
                f"Commit: `{commit_short_sha}`"
            )
            ok = await send_file_with_retry(client, chat_id, file_path, caption, topic_id)
            if not ok:
                overall_success = False
    finally:
        if client.is_connected():
            await client.disconnect()

    if not overall_success:
        print("One or more files failed to send to Telegram.", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())
