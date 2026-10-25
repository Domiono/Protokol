## Follow-up FCP/LCP: remove render-blocking external font styles, 2026-10-25

Following the owner's Google PageSpeed Insights mobile screenshot (FCP 3.3 s, LCP 3.6 s, TBT 0 ms, CLS 0, Speed Index 6.5 s), reduced nonessential render-blocking public stylesheets.

- PR: https://github.com/Domiono/Protokol/pull/29
- Production success: https://github.com/Domiono/Protokol/actions/runs/40231924827
- Independent before/after Lighthouse diagnostics: https://github.com/Domiono/Protokol/actions/runs/40230435196 and https://github.com/Domiono/Protokol/actions/runs/40232149029
- Optional Font Awesome 6.4.0 and Google Fonts Roboto CSS changed to `media="print"` with `onload="this.media='all'"`, preconnect for both origins and fonts.gstatic.com, and full `<noscript>` CSS fallback. **Core Tailwind is still a normal blocking stylesheet** to prevent unstyled page flashes. Admin remains untouched.
- Production smoke test confirmed this markup on both homepage and Puzzi offer. Galleries, responsive image deferral, favicon, SEO passed.
- In new Lighthouse lab both external stylesheets disappeared from render-blocking audit (only local Tailwind remained). Audit estimated 0 ms remaining render-blocking savings in both profiles (not guaranteed).
- Single-run Lighthouse performance points varied (mobile 81 before -> 84 after; desktop 92 before -> 87 after). These are noisy lab runs and are **not** proof of an overall performance-score improvement. Network transfer was unchanged, as expected: resources still load, just non-blocking.
- The user's Google PageSpeed screenshot is a separate device/emulated connection. FCP/LCP must be remeasured from the same tool and conditions to validate an actual improvement. The official PageSpeed API previously returned HTTP 429.
- Maintenance: `scripts/check_public_image_markup.py` now recognizes original and deferred WebP image attributes; an earlier audit falsely failed after the gallery deferral even though the site worked. The corrected audit passed on main at https://github.com/Domiono/Protokol/actions/runs/40231924847.

---

## Follow-up PageSpeed transfer reduction, 2026-10-25

**Successfully deployed** homepage/gallery changes: https://github.com/Domiono/Protokol/pull/28
Production deploy and live gallery/favicon smoke test: https://github.com/Domiono/Protokol/actions/runs/40230206475

Source analysis: https://github.com/Domiono/Protokol/actions/runs/40229497971
Post-change analysis: https://github.com/Domiono/Protokol/actions/runs/40230435196

### Actual Lighthouse network comparison (one run each, uncached lab requests)

| Profile | Before transfer | After transfer | Reduction | Before image requests | After image requests |
|---|---:|---:|---:|---:|---:|
| Mobile | 5,419,427 B | 1,415,581 B | 73.9% | 21 | 9 |
| Desktop | 4,468,192 B | 1,995,808 B | 55.3% | 52 | 27 |

- Large existing favicon was 1,510,876 B. The new 64×64 versioned PNG is 7,208 B, without deleting or rewriting the original.
- PHP avoids outputting initial `src` / `srcset` on inactive gallery slides. Gallery JS v3 loads the selected slide lazily; the public end-to-end verification confirmed 24 immediately addressable active images and 35 deferred inactive images, plus intact full-resolution zoom.
- Production gallery and SEO verification succeeded. This change **does not** modify rental terms, price/deposit, reservations, inventory, or source photos.
- Single-run performance scores **fluctuate** with network and lab environment: diagnostic mobile before 93 and after 81, desktop before 87 and after 92; the earlier initial Lighthouse test had scored 80 on both. Do **not** present these as a stable point improvement; the byte/request reductions are the stronger directly validated result.
- Further issue: image `6a7895410c663.png` still transfers ~762 KB; Pillow previously could not decode it, so it was left as-is. Inspect its actual format/source safely before any reupload, not blindly changing its bytes.
- Font Awesome CSS and fonts.gstatic/Google Fonts still exist as render-blocking or third-party dependencies; optimization would require visual review to preserve icon and typography appearance.

---

## Tailwind CDN replaced (2026-10-25)

**Public site: production deployment verified.** The public homepage and product detail pages no longer load `https://cdn.tailwindcss.com`. The static CSS is `/assets/rentmor-tailwind-4a9b40916c35.css` (22,838 bytes), built with pinned Tailwind CLI v3.4.17 from the actual live PHP source and public rendered HTML. Its path includes a content digest for immutable browser caching. The existing public galleries and all original images remain intact.

- PR: https://github.com/Domiono/Protokol/pull/27
- Successful production workflow: https://github.com/Domiono/Protokol/actions/runs/40228335825
- Verified: 4 public pages use identical stylesheet, no runtime CDN script, HTTPS CSS fetched byte-for-byte, MIME/hash and essential selectors, gallery assets, PHP lint, SEO smoke tests.
- Tested 25 public pages and observed 207 classes; CSS built reproducibly at 22,838 bytes.
- Existing live `index.php` is downloaded, patched, backed up and compared before upload; a failed public CSS smoke test restores the previous homepage.
- **Admin is intentionally unchanged**: `server/admin.php` still uses Tailwind CDN and should be handled as a separate migration because it hosts operational workflows. All current public tutorials have their own CSS.
- **No measured Lighthouse/PageSpeed score** has been established from this change. Use field data and PageSpeed Insights later to assess LCP/INP/CLS effects.

---

# RentMor — image and loading performance, 2026-10-25

## Production status: deployed and verified

- Main work: https://github.com/Domiono/Protokol/pull/25
- Deployment sequencing fix: https://github.com/Domiono/Protokol/pull/26
- Successful PHP/site deploy: https://github.com/Domiono/Protokol/actions/runs/40226288352
- Successful gallery deploy: https://github.com/Domiono/Protokol/actions/runs/40226431410
- Successful WebP job: https://github.com/Domiono/Protokol/actions/runs/40226487106
- Successful final public-HTML markup audit: https://github.com/Domiono/Protokol/actions/runs/40226637954
- All SEO public checks successful after deployment. No user inventory, reservations, prices or original photos modified.

## Measured facts

- Before: homepage HTML had 60 `img` elements, no width/height or srcset, 59 with lazy loading; logo original PNG about 1.235 MB; several JPEGs 8–12 MB.
- First WebP conversion: 60 referenced sources including logo; 59 processed, 1 `6a7895410c663.png` not recognized by Pillow and left as original/fallback. Source images processed were 75,175,706 bytes; all generated WebP variants combined were 7,244,214 bytes.
- After: homepage 59 images with intrinsic dimensions/srcset (58 of 59 uploads plus logo). Puzzi offer 1 of 1 uploads and logo optimized. Live SFTP readback and 13 public WebP HEAD requests passed.
- Original `src` values remain and zoom JS uses original image URL, not the responsive `currentSrc`.
- Publicly visible `rentmor-image-manifest.json` contains only image dimensions and public derivative filenames. New derivative filenames include a hash, with immutable caching.
- Weekly Sunday workflow handles newly uploaded photos. It does not touch original uploads. A later upload can remain unoptimized until the next run.

## Technical design

- `server/performance_images.php`: public HTML response output buffer adds intrinsic dimensions, async decoding, WebP srcset and sizes.
- `scripts/prepare_rentmor_images.py`: incremental constrained Pillow conversion, 640w card and 1440w detail at lower quality, conservative size test.
- `scripts/patch_performance_template.py` and `scripts/patch_image_cache.py`: guarded idempotent changes to actual live PHP and .htaccess with backups and validation.
- Deploy order serialized: API → galleries → images. An additional public performance audit confirms responsive markup.

## Remaining opportunity

Tailwind CDN removal from public pages has since been completed; see the status section above. Do not claim measured CSS startup improvements yet. Next phase: build static Tailwind stylesheet from actual live PHP templates, gallery JS/CSS and all dynamically generated class names, then visually regression-test homepage, product detail, filter buttons, responsive layout and modals before removing the CDN. Font Awesome/Google Fonts are external too.

An image source named `6a7895410c663.png` failed decoding; inspect original separately before replacing it. Do not delete automatically.

Actual Google PageSpeed/Lighthouse LCP, CLS, INP improvement was **not measured**, only asset size and HTML implementation.
