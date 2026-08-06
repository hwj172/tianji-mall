"""PreCompact hook: check that engramory sync was performed recently.

Reads hook input from stdin (JSON).  Checks whether any memory file was
modified in the last N minutes.  If not, blocks compaction and reminds
the agent to sync memory first.

Exit 0 → allow compact.  Exit 1 → block compact (stderr goes to the user).

Configure in .claude/settings.local.json:
  "PreCompact": [{
    "matcher": "",
    "hooks": [{
      "type": "command",
      "command": "D:/Users/黄文杰/AppData/Local/Programs/Python/Python313/python.exe",
      "args":  [".claude/hooks/pre_compact_engramory_check.py"]
    }]
  }]
"""

import json
import os
import sys
import time

# ── configuration ──────────────────────────────────────────────
MEMORY_ROOT = os.path.join(
    os.path.expandvars(r"%USERPROFILE%"),
    ".claude", "projects", "D--TEST-tianji-mall", "memory"
)
GRACE_MINUTES = 20  # how long since last sync is "fresh enough"
# ────────────────────────────────────────────────────────────────

def _latest_mtime(root: str) -> float:
    """Return the mtime of the most recently changed .md file under root.
    Falls back to the root directory's own mtime.  Returns 0 if the dir
    does not exist."""
    if not os.path.isdir(root):
        return 0.0
    best = os.path.getmtime(root)
    for fn in os.listdir(root):
        if not fn.endswith(".md"):
            continue
        m = os.path.getmtime(os.path.join(root, fn))
        if m > best:
            best = m
    return best


def main() -> int:
    # Consume stdin if present (Claude Code passes hook context as JSON)
    try:
        raw = sys.stdin.read()
        if raw.strip():
            json.loads(raw)
    except Exception:
        pass

    latest = _latest_mtime(MEMORY_ROOT)
    age_min = (time.time() - latest) / 60.0 if latest > 0 else float("inf")

    if age_min < GRACE_MINUTES:
        return 0  # recent sync → allow compact

    msg = (
        "\n"
        + "=" * 58 + "\n"
        + "  Engramory 记忆库同步检查\n"
        + "=" * 58 + "\n"
        + f"  最近同步: {age_min:.0f} 分钟前 (阈值 {GRACE_MINUTES}min)\n"
        + "\n"
        + "  compact 前需要先同步记忆库。请：\n"
        + "  1. 加载 engramory-master skill\n"
        + "  2. 更新 current-task.md 等 project memory\n"
        + "  3. 重试 /compact\n"
        + "=" * 58 + "\n"
    )
    print(msg, file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
