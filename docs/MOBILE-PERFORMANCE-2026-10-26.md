# RentMor — mobile first paint and image sizing, 26 October 2026

PR: https://github.com/Domiono/Protokol/pull/32
Production commit: `2d60ede50bfbc804f0838f78295e9f0247206148`.
Deployment: https://github.com/Domiono/Protokol/actions/runs/40288306717

## Problem reproduced

The user's Google PageSpeed screenshot showed mobile performance 85, FCP/LCP
3.3s, TBT 0ms and CLS 0. A fresh three-run Lighthouse baseline reproduced a
similar mobile result: score 87 and FCP/LCP about 3.16s. This is a new baseline;
it must not be mixed with the earlier 97-point run from a different time.

Baseline: https://github.com/Domiono/Protokol/actions/runs/40287609023

The official PageSpeed API returned HTTP 429. The controlled comparison uses
Lighthouse 12.8.2 on GitHub Actions, three runs per profile, standard mobile
simulation and desktop preset. These are lab results, not real-user Core Web
Vitals data or a guarantee of the next PageSpeed score.

## Changes

- The exact compiled Tailwind stylesheet and existing gallery stylesheet are
  embedded in the public HTML. Their hashed/cached external links remain as
  nonblocking stylesheets. This removes the dependency on a separate stylesheet
  request for first paint, while keeping all responsive and utility rules and
  supporting JavaScript-disabled rendering. This trades roughly 28KB of raw
  HTML per page for an earlier styled render; the cacheable files are retained.
- New image derivatives at 160px and 320px complement the existing 640px and
  detail variants. Existing derivatives and original uploads are preserved.
- Explicit card/detail/thumbnail roles let the response choose different `sizes`.
  Cards account for the actual grid breakpoints, padding, stage height and
  object-fit:contain aspect ratio. Tiny detail thumbnails declare 78px instead
  of a large card width. Logo sizes reflect its actual 144px/168px width.
- High-DPI selection is still handled by the browser using all available
  candidates. Product detail images, full-resolution zoom and original URLs
  remain available. Homepage photos remain static.
- The v2 middleware has a separate filename. Restoring the previous index.php
  restores its original middleware and CSS behavior. Existing v1 is not replaced.

## Safety and validation

The deployment skips API/admin uploads, Firebase and data migrations for this
public-only release. No client, reservation, Android or admin source is changed.
New derivatives are uploaded before the manifest is replaced; their published
URLs and content types are checked. The current index is downloaded and patched,
compared against the live version before upload, and backed up. Failed critical
post-deploy checks restore the previous index.

Tests cover exact critical CSS and hash agreement, repeated application, rejection
of unknown templates, preserved original image URLs, role-specific sizes,
new thumbnail variants and unchanged pre-existing derivatives. PHP syntax,
Python tests and the existing rental/Android test suite passed before merge.

A previous homepage backup is retained at
`public_html/.rentmor-backups/index-before-site-update-40288306717.php`.
Restore with authenticated SFTP, stripping the single PHP exit-guard line from
the backup before writing index.php. Backups must not be served over HTTP.
The original images and earlier hashed assets are retained.

## Results after deployment

After: https://github.com/Domiono/Protokol/actions/runs/40288946614
All deployment, image verification, public page and browser jobs succeeded.
26 WebP URL checks passed. Live CSS/hash checks passed on home and product pages.
Browser checks at 390px and 1365px passed: static cards, search, category filters,
product navigation and zoom; no page JavaScript errors. Screenshots were reviewed.

Medians of three runs (decimal KB, measured transfer during audit):

| Metric | Mobile before | Mobile after | Desktop before | Desktop after |
|---|---:|---:|---:|---:|
| Performance | 87 | 92 | 86 | 89 |
| FCP | 3.162s | 1.428s | 1.655s | 1.164s |
| LCP | 3.162s | 3.246s | 1.655s | 1.842s |
| Speed Index | 3.162s | 1.453s | 1.655s | 1.202s |
| TBT | 0ms | 1ms | 0ms | 0ms |
| CLS | 0.00222 | 0.00234 | 0.00366 | 0.00040 |
| Transfer | 707.7KB | 457.5KB | 1033.2KB | 676.1KB |

Mobile first paint improved 55%, transfer fell 35%. Desktop transfer fell 35%.
There is no demonstrated LCP improvement: mobile median increased 0.083s,
desktop 0.187s. Mobile after scores were 93/86/92, LCP 2.324/4.145/3.246s
and TBT 245/1/1ms. This variability and the blocking-time outlier are retained,
not discarded. Desktop after scores were 89/89/89. These independent lab
scores do not replace the user's desktop PSI score above 90.

## Remaining work

The LCP element remains the headline. Earlier rendering is confirmed, but font
loading and simulated rendering delay still warrant investigation. The audit
now reports no render-blocking resources. Font Awesome still transfers roughly
170KB including CSS and the solid font; Google Fonts roughly 81KB. Replacing
unused icon-font glyphs with an audited subset or equivalent SVGs is a possible
next change, but requires checking every public template and preserving icons.
Mobile responsive-image estimated waste fell to about 17KB in the after audit.
A future font change should be tested separately against this baseline; this
release does not claim stable LCP below 2.5 seconds or a guaranteed PSI score.

