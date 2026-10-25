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

**Tailwind still loads from `https://cdn.tailwindcss.com` on public pages.** Do not claim CSS startup improvements yet. Next phase: build static Tailwind stylesheet from actual live PHP templates, gallery JS/CSS and all dynamically generated class names, then visually regression-test homepage, product detail, filter buttons, responsive layout and modals before removing the CDN. Font Awesome/Google Fonts are external too.

An image source named `6a7895410c663.png` failed decoding; inspect original separately before replacing it. Do not delete automatically.

Actual Google PageSpeed/Lighthouse LCP, CLS, INP improvement was **not measured**, only asset size and HTML implementation.
