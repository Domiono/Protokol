<?php
declare(strict_types=1);

/**
 * Keep original src URLs for fallback and full-size gallery, but offer
 * size-appropriate WebP via srcset. Works without changing DOM hierarchy.
 */
function rentmor_optimize_public_html(string $html): string {
    $file = __DIR__ . '/rentmor-image-manifest.json';
    if (!is_file($file)) return $html;
    $manifest = json_decode((string)file_get_contents($file), true);
    if (!is_array($manifest)) return $html;
    $rewrite = static function (string $fragment) use ($manifest): string {
        return (string)preg_replace_callback('~<img\b[^>]*>~i', static function (array $m) use ($manifest): string {
        $tag = $m[0];
        if (preg_match('~\bsrcset\s*=~i', $tag)) return $tag;
        if (!preg_match('~\bsrc\s*=\s*(["\'])(.*?)\1~is', $tag, $match)) return $tag;
        $source = html_entity_decode($match[2], ENT_QUOTES | ENT_HTML5, 'UTF-8');
        $path = parse_url($source, PHP_URL_PATH);
        if (!is_string($path)) return $tag;
        if ($path === '/rentmor-logo.png') {
            $key = 'rentmor-logo.png';
        } elseif (preg_match('~^/?uploads/([a-zA-Z0-9_.-]+\.(?:jpe?g|png|webp))$~i', $path, $parts)) {
            $key = $parts[1];
        } else {
            return $tag;
        }
        $asset = $manifest[$key] ?? null;
        if (!is_array($asset) || empty($asset['width']) || empty($asset['height'])) return $tag;
        $attr = '';
        if (!preg_match('~\bwidth\s*=~i', $tag)) $attr .= ' width="' . (int)$asset['width'] . '"';
        if (!preg_match('~\bheight\s*=~i', $tag)) $attr .= ' height="' . (int)$asset['height'] . '"';
        if (!preg_match('~\bdecoding\s*=~i', $tag)) $attr .= ' decoding="async"';
        $sources = [];
        foreach (['thumb', 'small', 'card', 'detail'] as $kind) {
            if (isset($asset[$kind]['url'], $asset[$kind]['width'])
                && preg_match('~^/(?:uploads/rentmor-webp/|assets/)[a-zA-Z0-9_.-]+\.webp$~', $asset[$kind]['url'])) {
                $sources[] = htmlspecialchars($asset[$kind]['url'], ENT_QUOTES, 'UTF-8')
                    . ' ' . (int)$asset[$kind]['width'] . 'w';
            }
        }
        if ($sources) {
            $attr .= ' srcset="' . implode(', ', $sources) . '"';
            $role = '';
            if (preg_match('~data-rm-image-kind="(card|detail|thumb)"~', $tag, $roleMatch)) $role = $roleMatch[1];
            if ($key === 'rentmor-logo.png') {
                $sizes = '(max-width: 639px) min(65vw, 144px), 168px';
            } elseif ($role === 'thumb') {
                $sizes = '78px';
            } elseif ($role === 'card') {
                // object-fit:contain draws portrait photos narrower than the img box.
                // Card stage: 232/256px high, 40px padding; grid uses 16px gutters,
                // 32px gaps and a 1280px maximum width. Keep every high-DPI candidate.
                $ratio = (float)$asset['width'] / (float)$asset['height'];
                $mobileWidth = (int)ceil(192 * $ratio);
                $wideWidth = (int)ceil(216 * $ratio);
                $sizes = '(max-width: 639px) min(calc(100vw - 74px), ' . $mobileWidth . 'px), '
                    . '(max-width: 640px) min(calc(50vw - 74px), ' . $mobileWidth . 'px), '
                    . '(max-width: 1023px) min(calc(50vw - 74px), ' . $wideWidth . 'px), '
                    . 'min(calc((min(100vw, 1280px) - 96px) / 3 - 42px), ' . $wideWidth . 'px)';
            } else {
                $sizes = '(max-width: 768px) 100vw, 900px';
            }
            $attr .= ' sizes="' . $sizes . '"';
            $attr .= ' data-rentmor-optimized="1"';
        }
        return substr_replace($tag, $attr, -1, 0);
        }, $fragment);
    };
    // Do not rewrite image-like strings inside JavaScript, including gallery templates.
    $parts = preg_split('~(<script\b[^>]*>.*?</script>)~is', $html, -1, PREG_SPLIT_DELIM_CAPTURE);
    if (!is_array($parts)) return $html;
    foreach ($parts as $index => $part) {
        if ($index % 2 === 0) $parts[$index] = $rewrite($part);
    }
    $html = implode('', $parts);
    // Loading="lazy" alone does not stop hidden carousel slides being fetched:
    // the browser may download every hidden slide in each visible card.
    // Keep real src/srcset only on the first active slide. The existing
    // gallery JS hydrates each other slide on demand, preserving zoom originals.
    return (string)preg_replace_callback(
        '~(<div\b(?=[^>]*\bclass\s*=\s*["\'][^"\']*\brm-gallery-slide\b)[^>]*>)(.*?)(</div>)~is',
        static function (array $slide): string {
            if (preg_match('~\brm-active\b~', $slide[1])) return $slide[0];
            $inside = (string)preg_replace_callback('~<img\b[^>]*>~is', static function (array $image): string {
                return (string)preg_replace_callback(
                    '~\s+(src|srcset|sizes)\s*=\s*(["\'])(.*?)\2~is',
                    static function (array $attribute): string {
                        return ' data-rm-' . strtolower($attribute[1]) . '='
                             . $attribute[2] . $attribute[3] . $attribute[2];
                    },
                    $image[0]
                );
            }, $slide[2]);
            return $slide[1] . $inside . $slide[3];
        },
        $html
    );
}

function rentmor_start_image_optimizer(): void {
    ob_start('rentmor_optimize_public_html');
}
