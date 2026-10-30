"""Apply the chosen blueprint background only to the live RentMor homepage hero.

The actual hosting index.php is downloaded first; never replace that file with a
repository snapshot. Preserve all heading text and other SEO/interaction markup.
"""
import re
import sys
from pathlib import Path

MARKER = "rentmor-hero-blueprint-v1"
ASSET = "/assets/rentmor-blueprint-final-20261030.webp"
STYLE = f"""<!-- {MARKER} -->
<style id="{MARKER}">
[data-rentmor-blueprint="v1"] {{
  background-color: #061c38 !important;
  background-image: linear-gradient(90deg,rgba(2,15,36,.10),rgba(2,15,36,.16),rgba(2,15,36,.10)),url("{ASSET}") !important;
  background-repeat: no-repeat !important;
  background-position: center center !important;
  background-size: cover !important;
}}
@media (min-width: 900px) {{
  [data-rentmor-blueprint="v1"] {{ background-position: center 45% !important; }}
}}
</style>
<!-- /{MARKER} -->
"""
HEADLINE = re.compile(r"<h1\b[^>]*>(.*?)</h1\s*>", re.I | re.S)
TAGS = re.compile(r"<(/?)(section|header)\b[^>]*>", re.I | re.S)

def patch(source: str) -> str:
    matches = []
    for h1 in HEADLINE.finditer(source):
        text = re.sub(r"<[^>]*>", " ", h1.group(1))
        if (re.search("wypożyczalnia", text, re.I)
            and re.search("ogrodniczego", text, re.I)
            and re.search("krakowie", text, re.I)):
            matches.append(h1)
    if len(matches) != 1:
        raise ValueError(f"Expected exactly one homepage H1, got {len(matches)}")
    before = source[:matches[0].start()]
    stack = []
    for token in TAGS.finditer(before):
        if token.group(1):
            if stack and stack[-1].group(2).lower() == token.group(2).lower():
                stack.pop()
        else:
            stack.append(token)
    if not stack:
        raise ValueError("Unable to locate open hero section/header")
    chosen = stack[-1]
    opening = chosen.group(0)
    if 'data-rentmor-blueprint=' not in opening:
        source = (source[:chosen.start()] + opening[:-1]
                  + ' data-rentmor-blueprint="v1">' + source[chosen.end():])
    if source.lower().count("</head>") != 1:
        raise ValueError("Unexpected document head")
    if f"<!-- {MARKER} -->" in source:
        if (source.count(f"<!-- {MARKER} -->") != 1
            or source.count(f"<!-- /{MARKER} -->") != 1):
            raise ValueError("Corrupt hero markers")
        source = re.sub(r"<!-- "+MARKER+r" -->.*?<!-- /"+MARKER+r" -->\s*",
                        "", source, count=1, flags=re.S)
    return re.sub("</head>", lambda m: STYLE + m.group(), source,
                  count=1, flags=re.I)

if __name__ == "__main__":
    file = Path(sys.argv[1])
    original = file.read_text(encoding="utf-8")
    updated = patch(original)
    if updated != original:
        file.write_text(updated, encoding="utf-8")
    print("Blueprint hero patch applied; heading and catalogue unmodified")
