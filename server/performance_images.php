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
        foreach (['card', 'detail'] as $kind) {
            if (isset($asset[$kind]['url'], $asset[$kind]['width'])
                && preg_match('~^/(?:uploads/rentmor-webp/|assets/)[a-zA-Z0-9_.-]+\.webp$~', $asset[$kind]['url'])) {
                $sources[] = htmlspecialchars($asset[$kind]['url'], ENT_QUOTES, 'UTF-8')
                    . ' ' . (int)$asset[$kind]['width'] . 'w';
            }
        }
        if ($sources) {
            $attr .= ' srcset="' . implode(', ', $sources) . '"';
            $attr .= ' sizes="' . ($key === 'rentmor-logo.png'
                ? '(max-width: 640px) 65vw, 260px'
                : (strpos($tag, 'fetchpriority="high"') !== false
                    ? '(max-width: 768px) 100vw, 900px'
                    : '(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 440px')) . '"';
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
    return implode('', $parts);
}

function rentmor_start_image_optimizer(): void {
    ob_start('rentmor_optimize_public_html');
}
