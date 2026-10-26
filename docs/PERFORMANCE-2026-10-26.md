# RentMor public performance — 26 October 2026

## Release

PR #30: https://github.com/Domiono/Protokol/pull/30

Production commit: `c8144d180136f8d9f796396069869bf51dbbf6db`.
Deployment: https://github.com/Domiono/Protokol/actions/runs/40273701678

Homepage cards now render only the first photo, without carousel controls,
timers, swipe handlers or subsequent slide downloads. Product detail galleries,
full-resolution originals, search, categories, contact links and offer URLs remain.
The current PHP template is patched on hosting, rather than overwritten from a
stale repository copy. The complete product image arrays in data.json are untouched.

An image named `6a7895410c663.png` (761,254 bytes) had an invalid EXIF chunk CRC.
Pillow rejected the file, while browsers rendered it. The converter now retries
only this type of failure on an in-memory copy with the corrupt EXIF chunk removed.
It validates all other chunk CRCs, rejects damaged pixel data or truncated files,
and never rewrites source uploads. The decoded pixel buffer was compared with an
independently stripped reference and was identical. A 640px WebP derivative is
55,920 bytes (92.7% smaller) in production; the 891px detail derivative is
95,328 bytes (87.5% smaller). Both live URLs returned HTTP 200 and image/webp.
The locally tested prototype was about 52KB; the values here are the actual
published derivatives from the pinned CI converter.

Before image-manifest publication, the homepage HTML decreased from 138,768 to
92,182 bytes (33.6%). Image elements decreased from 60 to 25: 24 product previews
plus the logo. There are no carousel navigation buttons or initialized galleries
on the homepage; the detail galleries remain initialized.

## Audit findings and decisions

- The homepage LCP element is the H1 text on both profiles, not a product photo.
  Optimizing photos reduces transfer, but does not imply an equal LCP improvement.
- The baseline measured TTFB component varied roughly from 0.5 to 1.4 seconds.
  These lab values include network latency, so they do not isolate PHP execution.
  Session handling and dynamic responses were retained; no blanket HTML cache was
  introduced that could interfere with forms or sessions.
- The existing critical Tailwind stylesheet was 22,838 bytes uncompressed and
  about 4,995 bytes transferred in Lighthouse. The audit estimated 0ms savings
  from removing the remaining blocking stylesheet. It remains blocking to preserve
  the styled first paint. Rebuilding from the patched live template produced
  22,931 bytes, verified by hash and MIME type on four public pages.
- Google Fonts and Font Awesome remain asynchronous with no-JavaScript fallbacks.
  Font-display diagnostics reported no blocked-text savings. The baseline loaded
  roughly 62KB of Roboto WOFF2 and 151KB of the solid icon font. Font Awesome CSS
  had about 18.6KB unused (99%); this is a remaining opportunity for a carefully
  validated local icon subset, not a claim that fonts were removed in this release.
- Several responsive photos are still larger than their displayed content area,
  particularly portrait images fitted into landscape cards. Exact object-fit-aware
  sizes and smaller thumbnail derivatives are a possible follow-up. Existing high-DPI
  quality and product detail sources were preserved in this release.

## Measurement method

Lighthouse 12.8.2 in GitHub Actions, three separate cold browser runs per profile,
standard mobile simulation and desktop preset, performance category only.
Before: https://github.com/Domiono/Protokol/actions/runs/40273021379

Compare medians and retain the spread. The previously reported 84/87 scores and
PageSpeed FCP 3.3s/LCP 3.6s came from different runs and are not the controlled
baseline here. These are lab measurements, not Google Search Console field data.

Baseline mobile scores: 80, 98, 98. Desktop scores: 85, 85, 86.
One mobile run had 610ms TBT while the other two had 0ms; this is why a single
score should not be presented as a stable performance result.

After: https://github.com/Domiono/Protokol/actions/runs/40274038265

| Metric (median of 3) | Mobile before | Mobile after | Desktop before | Desktop after |
|---|---:|---:|---:|---:|
| Performance score | 98 | 97 | 85 | 89 |
| FCP | 1.525 s | 1.652 s | 1.450 s | 1.474 s |
| LCP | 2.173 s | 2.291 s | 1.450 s | 1.474 s |
| Speed Index | 2.811 s | 2.033 s | 2.456 s | 1.474 s |
| TBT | 0 ms | 0 ms | 0 ms | 0 ms |
| CLS | 0.00220 | 0.00222 | 0.00359 | 0.00366 |
| Transferred bytes | 1,415,679 | 707,689 | 1,995,926 | 1,033,161 |

After mobile scores: 94, 98, 97; desktop: 88, 89, 91. The first mobile
post-change run had 186ms TBT, the others 0ms. All raw Lighthouse JSON reports
are retained as workflow artifacts.

The robust improvement is transfer: **50.0% lower on mobile and 48.2% lower on
desktop**. Median Speed Index improved by 27.7% and 40.0%, respectively, with
homepage carousel movement removed. FCP and LCP did **not** improve in this
sample; their medians were slightly higher. Do not describe the change as an
FCP/LCP improvement or compare the new scores directly to the user's older
PageSpeed screenshots. Static previews and less transfer are the validated gains.

The initial post-change browser smoke step timed out waiting for `networkidle`.
Lighthouse completed all six measurements successfully. The smoke test was
corrected to wait for DOM, gallery CSS and gallery initialization rather than
complete network silence, which background tracking requests need not allow.
A separate browser-only run verifies both 390px and 1365px viewports without
rerunning or selecting different Lighthouse results.

Browser verification passed:
https://github.com/Domiono/Protokol/actions/runs/40285183086

Both mobile (390px) and desktop (1365px) passed: 24 one-image cards, no carousel
arrows/initialization, unchanged image after five seconds, working search and
category filters, retained product gallery navigation, image decoding and
full-resolution zoom, no JavaScript page errors. Screenshots of both homepage
viewports and detail zoom are attached to that run.

## Validation and recovery

- Python unit tests cover idempotence, fail-closed template matching, actual PHP
  rendering of one homepage image versus all detail photos, links and controls.
- PNG tests cover bad EXIF recovery, unchanged source bytes and rejection of
  corrupted IDAT data. The real problematic photo was inspected visually.
- CI tests for rental rules, statistics, API behavior, Android unit tests/build,
  SEO and PHP syntax passed. No Android or rental application source was changed.
- Production smoke checks passed for responsive images, tiny favicon, asynchronous
  fonts, static CSS, sitemap, canonical redirects, guides and offer pages.
- The deployment scope classifier skipped API/admin uploads, Firebase setup and
  diagnostics, WT20i data migration and SEO-title data migration for this release.
- A pre-write comparison refuses a concurrent homepage edit. The prior index is
  backed up at `public_html/.rentmor-backups/index-before-site-update-40273701678.php`
  with denied HTTP access and a one-line PHP exit guard. Restore using authenticated
  SFTP, removing that guard line before restoring index.php. Never expose the backup
  over HTTP. Failed critical smoke checks restore the original index automatically.
- Old hashed assets and original photos remain available. Newly created WebPs are
  additive and the previous image manifest entries are preserved.
