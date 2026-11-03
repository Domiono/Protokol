#!/usr/bin/env bash
set -euo pipefail
source scripts/secure_sftp_connection.sh
DIR="$RUNNER_TEMP/seo-structure"
mkdir -m 700 -p "$DIR/original" "$DIR/prepared" "$DIR/current" "$DIR/readback"
sftp_run() { lftp --env-password -u "$FTP_USERNAME" "sftp://$FTP_SERVER:22222" -e "$SFTP_CONNECT_SETUP; $1; bye" >/dev/null; }
FILES=(rental_categories.php category.php category_sitemap.php rentmor_accessibility.php seo_structure.php seo-structure.css)
# A successful listing distinguishes absent new files from connection failures.
lftp --env-password -u "$FTP_USERNAME" "sftp://$FTP_SERVER:22222" -e "$SFTP_CONNECT_SETUP; cls -1 public_html/; bye" > "$DIR/listing"
for name in index.php .htaccess "${FILES[@]}"; do
  if [[ "$name" == index.php || "$name" == .htaccess ]] || grep -Eq "(^|/)$name$" "$DIR/listing"; then
    sftp_run "get public_html/$name -o $DIR/original/$name"
  else
    touch "$DIR/original/$name.absent"
  fi
done
cp "$DIR/original/index.php" "$DIR/prepared/index.php"
cp "$DIR/original/.htaccess" "$DIR/prepared/.htaccess"
for name in "${FILES[@]}"; do cp "server/$name" "$DIR/prepared/$name"; done
python3 scripts/patch_seo_structure.py "$DIR/prepared/index.php"
python3 scripts/patch_seo_structure.py "$DIR/prepared/.htaccess"
php -l "$DIR/prepared/index.php"
cp "$DIR/prepared/index.php" "$DIR/first.php"
python3 scripts/patch_seo_structure.py "$DIR/prepared/index.php"
cmp "$DIR/prepared/index.php" "$DIR/first.php"
curl --fail --silent --show-error --max-time 30 https://rentmor.pl/ -o "$DIR/home.html"
# On a repeat deployment the home is already transformed; otherwise require known sections.
if ! grep -q 'data-rm-home-structure="v1"' "$DIR/home.html"; then php scripts/test_seo_structure.php "$DIR/home.html"; fi
# Prepare the same artwork with smaller mobile download sizes, before changing live PHP.
test -s reports/rentmor-blueprint-mobile-20261103.webp
test -s reports/rentmor-blueprint-mobile-20261103-lite.webp
test "$(stat -c%s reports/rentmor-blueprint-mobile-20261103-lite.webp)" -lt 115000
# Upload before the PHP switch; immutable URL and byte-for-byte verification prevent broken backgrounds.
sftp_run "put reports/rentmor-blueprint-mobile-20261103-lite.webp -o public_html/assets/rentmor-blueprint-mobile-20261103-lite.webp; get public_html/assets/rentmor-blueprint-mobile-20261103-lite.webp -o $DIR/hero-lite-readback.webp"
cmp reports/rentmor-blueprint-mobile-20261103-lite.webp "$DIR/hero-lite-readback.webp"
sftp_run "get public_html/rentmor-logo.png -o $DIR/original-logo.png"
cwebp -quiet -q 83 -m 6 -resize 640 0 "$DIR/original-logo.png" -o "$DIR/rentmor-logo-mobile-20261103.webp"
test -s "$DIR/rentmor-logo-mobile-20261103.webp"
test "$(stat -c%s "$DIR/rentmor-logo-mobile-20261103.webp")" -lt 50000
# Immutable filenames avoid changing the existing live assets; verify every byte.
sftp_run "put reports/rentmor-blueprint-mobile-20261103.webp -o public_html/assets/rentmor-blueprint-mobile-20261103.webp; get public_html/assets/rentmor-blueprint-mobile-20261103.webp -o $DIR/hero-readback.webp"
cmp reports/rentmor-blueprint-mobile-20261103.webp "$DIR/hero-readback.webp"
sftp_run "put $DIR/rentmor-logo-mobile-20261103.webp -o public_html/assets/rentmor-logo-mobile-20261103.webp; get public_html/assets/rentmor-logo-mobile-20261103.webp -o $DIR/logo-readback.webp"
cmp "$DIR/rentmor-logo-mobile-20261103.webp" "$DIR/logo-readback.webp"
printf 'Require all denied\n' > "$DIR/backup.htaccess"
sftp_run "set cmd:fail-exit no; mkdir -p public_html/.rentmor-backups; set cmd:fail-exit yes; put $DIR/backup.htaccess -o public_html/.rentmor-backups/.htaccess"
for name in index.php .htaccess "${FILES[@]}"; do
  if [[ -f "$DIR/original/$name" ]]; then
    sftp_run "get public_html/$name -o $DIR/current/$name"
    cmp "$DIR/current/$name" "$DIR/original/$name"
    printf '<?php http_response_code(404); exit; ?>\n' > "$DIR/backup-$name.php"
    cat "$DIR/original/$name" >> "$DIR/backup-$name.php"
    sftp_run "put $DIR/backup-$name.php -o public_html/.rentmor-backups/seo-$GITHUB_RUN_ID-$name.php"
  fi
  # Stage and compare before exposing anything. A PHP staging name avoids source disclosure.
  sftp_run "put $DIR/prepared/$name -o public_html/.rentmor-backups/seo-stage-$GITHUB_RUN_ID-$name.php; get public_html/.rentmor-backups/seo-stage-$GITHUB_RUN_ID-$name.php -o $DIR/readback/$name"
  cmp "$DIR/prepared/$name" "$DIR/readback/$name"
done
rollback() {
  echo '::error::SEO publication failed; restoring the prior homepage, routes and helpers.'
  # Restore entry points before removing any newly introduced dependencies.
  for name in index.php .htaccess "${FILES[@]}"; do
    if [[ -f "$DIR/original/$name" ]]; then
      sftp_run "put $DIR/original/$name -o public_html/$name" || echo "::error::Restore failed for $name"
    else
      sftp_run "set cmd:fail-exit no; rm public_html/$name" || true
    fi
  done
}
trap rollback ERR
for name in "${FILES[@]}" index.php .htaccess; do
  sftp_run "put $DIR/prepared/$name -o public_html/$name; get public_html/$name -o $DIR/deployed-$name"
  cmp "$DIR/prepared/$name" "$DIR/deployed-$name"
done
python3 scripts/verify_seo_structure.py
python3 scripts/verify_public_seo.py
python3 scripts/verify_seo_improvements.py
python3 scripts/verify_public_privacy.py
RM_REQUIRE_NEW_MENU=1 node scripts/seo_structure_browser.cjs
node scripts/seo_structure_assets_browser.cjs
trap - ERR
echo 'PASS: SEO structure published; categories, sitemap, rental schema and legacy routes verified.'
