#!/usr/bin/env bash
# Configure encrypted SFTP, trusting only the two previously observed host keys.
set -eu
: "${FTP_SERVER:?Missing server}"
: "${FTP_USERNAME:?Missing username}"
: "${LFTP_PASSWORD:?Missing password}"
SFTP_KEYS="$RUNNER_TEMP/rentmor_sftp_known_hosts"
SFTP_SCAN="$RUNNER_TEMP/rentmor_sftp_keyscan"
for attempt in 1 2 3; do
    ssh-keyscan -p 22222 -T 12 -t ed25519,rsa "$FTP_SERVER" 2>/dev/null > "$SFTP_SCAN" && test -s "$SFTP_SCAN" && break
    sleep 2
done
if [ ! -s "$SFTP_SCAN" ]; then
    echo "SFTP server not responding. Credentials were not sent." >&2
    exit 1
fi
: > "$SFTP_KEYS"
while IFS= read -r line; do
    printf '%s\n' "$line" > "$RUNNER_TEMP/rentmor_single_key"
    fingerprint="$(ssh-keygen -lf "$RUNNER_TEMP/rentmor_single_key" -E sha256)"
    case "$fingerprint" in
        *SHA256:nJyQovQRMmJEJuzI44hZS/VhZPrOhzRoEyh92Go6TEk*|*SHA256:GDfRh92pfMOviF06fYXQSIHQ0OSKwDNOXqzr+Tslrr0*)
            printf '%s\n' "$line" >> "$SFTP_KEYS" ;;
    esac
done < "$SFTP_SCAN"
if [ ! -s "$SFTP_KEYS" ]; then
    echo "SFTP host key mismatch. Credentials were not sent." >&2
    exit 1
fi
chmod 600 "$SFTP_KEYS"
SFTP_CONNECT_SETUP="set cmd:fail-exit yes; set net:max-retries 2; set net:timeout 25; set sftp:connect-program \"ssh -a -x -o StrictHostKeyChecking=yes -o UserKnownHostsFile=$SFTP_KEYS\""
echo "SFTP host key matches the pinned value."
