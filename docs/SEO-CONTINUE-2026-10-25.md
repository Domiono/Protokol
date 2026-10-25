# RentMor SEO continuation — paused by user
User authorized implementing SEO improvements, then requested continuing in a new chat due to data/usage limit. No changes in this branch have been deployed. These are UNTESTED drafts, not production-ready.

Base main: f0fbe5406b33bac06a14aa7f7e7788d1a69b038d.
Completed earlier on main: WT20i pressure 210 bar (3045 PSI), pump wording "typu GRACO 395", water-only rental restriction, corrected guide PDF/startup wording, external YouTube/PDF checks; last deploy and verify succeeded.

Preserve user's wording "wszystkie farby wodne". Rental rule: "Agregat wynajmujemy wyłącznie do farb i gruntów wodnych. Nie dopuszczamy farb olejnych ani rozpuszczalnikowych." Do not add explanation about smell. Preserve prices, deposits, inventory, names/slugs and private exact address.

Audit findings:
- http://rentmor.pl/ and https://www.rentmor.pl/ returned 200; canonical target https://rentmor.pl/. Old index.php?id product URLs correctly return 301.
- Titles: ozonator typo "ozonator jem"; zaciskarka/drabina brand truncated to "Rent"; improve WT20i/Stanley/bruzdownica titles.
- Puzzi, Stanley+vacuum, WT20i need useful offer sections and contextual guide links.
- Homepage no organization JSON-LD.
- Homepage uses https://cdn.tailwindcss.com; 60 img elements, 59 lazy, no width/height attrs. Sample originals 237–447KB. Gallery CSS already reserves container space so do not claim measured CLS failure.
- No GSC metrics or verified Google Business Profile status; don't claim rankings/index count.

Draft files:
server/rental_offer_details.php — three product-specific sections (known IDs), uses existing terms/equipment, no new promises. Call beside product description.
scripts/patch_seo_template.py — inject helper call and organization JSON-LD into actual index.php. Must test escaping, markers and idempotence.
scripts/patch_seo_titles.py — mutate seo_title only (+ZESTA typo). Validate selectors against fresh data.json, preserve all unrelated fields. Need unit tests.
scripts/patch_canonical_host.py — prepend host/scheme redirects to actual .htaccess; preserve routing/security. Must confirm hosting HTTPS variables to avoid loops and test redirects preserving path/query; only GET/HEAD.

Next:
1. Re-read main/head and compare to branch before edits.
2. Test drafts, wire into existing .github/workflows/deploy-rentmor.yml: deploy helper before index; patch ACTUAL downloaded index and .htaccess, never replace with older source; backup and compare live vs original before publishing. Add live checks.
3. Titles are stored in hosting data.json, not repo. Existing WT20i workflow step demonstrates snapshot/patch/backup/compare/upload/readback. SFTP mv over existing target FAILS on hosting; existing put works. No credentials should be printed. Preserve concurrency group rentmor-public-homepage.
4. Complete performance work if feasible: compile existing Tailwind classes to static CSS using live PHP template + all included helpers/scripts, preserve dynamic state classes; optimize images with responsive versions and correct intrinsic dimensions, retaining originals and gallery full-size viewing. Not implemented yet.
5. Optional small canonical fix for live zasady.php (not in repo). Its rental rules were read; return/cleaning fees are determined by contract/arrangements, no new charges.
6. Deploy authorized changes and verify live site; user need not reconfirm authorization. Don't claim deployment before verification.

Workflow uses SFTP and existing backups under public_html/.rentmor-backups with .htaccess + PHP 404 wrapper. Protected runtime differs from SFTP relative paths; avoid unrelated Firebase changes.
Do not modify Google Business Profile without locating/accessing correct profile. Exact pickup address is disclosed only after reservation.
