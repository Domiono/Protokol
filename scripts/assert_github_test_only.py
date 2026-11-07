#!/usr/bin/env python3
"""Policy for PUBLIC RentMor test-only GitHub Actions; no production credentials.
This test checks tracked workflow source and high-risk secret file names.
It DOES NOT rewrite git history or claim all historical contents are clean.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / ".github" / "workflows"
FORBIDDEN_WORKFLOW = {
    "accessing GitHub secrets": r"\bsecrets\s*\.",
    "SFTP/FTP command or credential": r"\b(lftp|sftp|scp|ssh-keyscan|FTP_SERVER|FTP_USERNAME|FTP_PASSWORD|LFTP_PASSWORD)\b|sftp://",
    "release publishing": r"softprops/action-gh-release|gh\s+release\s+create|npm\s+publish|docker\s+push",
    "workflow chaining": r"(?m)^\s*workflow_run\s*:",
    "write token permissions": r"(?m)^\s*contents:\s*write\s*$",
    "deploy via curl": r"\bcurl\b[^\n]*(-X|--request)\s*(POST|PUT|PATCH|DELETE)\b",
}
FORBIDDEN_TRACKED_PATH = re.compile(
    r"(^|/)(\.env(\..*)?|owner\.json|state\.json|credentials\.json|"
    r"service-account\.json|id_rsa|id_ed25519|.*\.(pem|p12|pfx|jks|keystore|sql|sqlite|db|bak))$",
    re.IGNORECASE,
)
def main() -> int:
    errors=[]
    for item in sorted(WORKFLOWS.glob("*.yml"))+sorted(WORKFLOWS.glob("*.yaml")):
        source=item.read_text(encoding="utf-8")
        if not re.search(r"(?m)^permissions:\s*\n\s+contents:\s*read\s*$",source):
            errors.append(f"{item.relative_to(ROOT)}: no minimal read-only permissions")
        for label,pattern in FORBIDDEN_WORKFLOW.items():
            if re.search(pattern,source,re.IGNORECASE):
                errors.append(f"{item.relative_to(ROOT)}: forbidden {label}")
    for item in ROOT.rglob("*"):
        if not item.is_file() or ".git" in item.parts:
            continue
        if FORBIDDEN_TRACKED_PATH.search(item.relative_to(ROOT).as_posix()):
            errors.append("Forbidden secret-like path: "+item.relative_to(ROOT).as_posix())
    if errors:
        print("FAIL: public test-only CI security policy")
        print("\n".join(errors))
        return 1
    print("PASS: all active workflows are test-only, no FTP/SFTP, GitHub secrets or release publishing")
    print("PASS: no risky credential file names in the working tree")
    print("NOTE: this cannot certify historical Git commits or external GitHub settings")
    return 0
if __name__=="__main__":
    sys.exit(main())
