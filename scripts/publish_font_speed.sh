#!/usr/bin/env bash
set -euo pipefail
source scripts/secure_sftp_connection.sh
DIR="$RUNNER_TEMP/font-speed"
mkdir -m 700 -p "$DIR"
sftp_run() { lftp --env-password -u "$FTP_USERNAME" "sftp://$FTP_SERVER:22222" -e "$SFTP_CONNECT_SETUP; $1; bye" >/dev/null; }
sftp_run "get public_html/index.php -o $DIR/original.php; get public_html/.htaccess -o $DIR/original.htaccess"
cp "$DIR/original.php" "$DIR/index.php"
cp "$DIR/original.htaccess" "$DIR/.htaccess"
python3 scripts/patch_font_speed.py "$DIR/index.php" reports/font-assets/manifest.json
python3 scripts/patch_mobile_speed.py "$DIR/.htaccess"
php -l "$DIR/index.php"
cp "$DIR/index.php" "$DIR/first.php"
python3 scripts/patch_font_speed.py "$DIR/index.php" reports/font-assets/manifest.json
cmp "$DIR/index.php" "$DIR/first.php"
sftp_run "get public_html/index.php -o $DIR/current.php; get public_html/.htaccess -o $DIR/current.htaccess"
cmp "$DIR/original.php" "$DIR/current.php"
cmp "$DIR/original.htaccess" "$DIR/current.htaccess"
printf 'Require all denied\n' > "$DIR/backup.htaccess"
printf '<?php http_response_code(404); exit; ?>\n' > "$DIR/backup.php"
cat "$DIR/original.php" >> "$DIR/backup.php"
printf '<?php http_response_code(404); exit; ?>\n' > "$DIR/backup-rules.php"
cat "$DIR/original.htaccess" >> "$DIR/backup-rules.php"
sftp_run "set cmd:fail-exit no; mkdir -p public_html/.rentmor-backups; set cmd:fail-exit yes; put $DIR/backup.htaccess -o public_html/.rentmor-backups/.htaccess; put $DIR/backup.php -o public_html/.rentmor-backups/fonts-before-$GITHUB_RUN_ID.php; put $DIR/backup-rules.php -o public_html/.rentmor-backups/fonts-rules-before-$GITHUB_RUN_ID.php"
for ASSET in reports/font-assets/*; do
  NAME="$(basename "$ASSET")"
  [ "$NAME" = manifest.json ] && continue
  sftp_run "put $ASSET -o public_html/assets/$NAME; get public_html/assets/$NAME -o $DIR/$NAME"
  cmp "$ASSET" "$DIR/$NAME"
done

rollback() {
  echo '::error::Publication verification failed; restoring both hosting files.'
  sftp_run "put $DIR/original.htaccess -o public_html/.htaccess; put $DIR/original.php -o public_html/index.php"
}
trap rollback ERR
sftp_run "put $DIR/.htaccess -o public_html/.htaccess; put $DIR/index.php -o public_html/index.php; get public_html/index.php -o $DIR/readback.php; get public_html/.htaccess -o $DIR/readback.htaccess"
cmp "$DIR/index.php" "$DIR/readback.php"
cmp "$DIR/.htaccess" "$DIR/readback.htaccess"
python3 scripts/check_font_speed.py
python3 scripts/verify_public_privacy.py
trap - ERR
echo 'PASS: mobile speed changes published with backups and public checks.'
