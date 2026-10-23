<?php
declare(strict_types=1);
header('Content-Type: application/xml; charset=UTF-8');
header('Cache-Control: public, max-age=600');
header('X-Content-Type-Options: nosniff');

// Sitemap is generated from the current public inventory for every request;
// added, edited or removed products do not require an uploaded static XML file.
function rm_sitemap_slug($name): string {
    $name = function_exists('mb_strtolower') ? mb_strtolower(trim((string)$name), 'UTF-8') : strtolower(trim((string)$name));
    $name = strtr($name, ['ą'=>'a','ć'=>'c','ę'=>'e','ł'=>'l','ń'=>'n','ó'=>'o','ś'=>'s','ź'=>'z','ż'=>'z']);
    return trim((string)preg_replace('/[^a-z0-9]+/', '-', $name), '-');
}
$base = 'https://rentmor.pl';
$urls = [$base.'/' => null];

// Only include public static pages which really exist on the server.
// Do not include admin, API, protocol, private document or authentication pages.
$staticPublicPages = [
    'zasady.php',
];
foreach ($staticPublicPages as $filename) {
    $file = __DIR__ . '/' . $filename;
    if (!is_file($file)) continue;
    $mtime = @filemtime($file);
    $urls[$base . '/' . $filename] = $mtime ? gmdate('Y-m-d', $mtime) : null;
}

$products = [];
$inventory = __DIR__ . '/data.json';
if (is_file($inventory)) {
    $decoded = json_decode((string)@file_get_contents($inventory), true);
    if (is_array($decoded)) $products = $decoded;
}
foreach ($products as $product) {
    if (!is_array($product) || empty($product['name'])) continue;
    $slug = rm_sitemap_slug($product['name']);
    if ($slug === '') continue;
    $location = $base . '/' . $slug . '/';
    // Product edit dates in data.json are not consistently modification timestamps.
    // Omit lastmod rather than provide a misleading value.
    $urls[$location] = null;
}

echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n";
echo '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">' . "\n";
foreach ($urls as $location => $lastmod) {
    echo '  <url><loc>' . htmlspecialchars($location, ENT_QUOTES | ENT_XML1, 'UTF-8') . '</loc>';
    if ($lastmod !== null) echo '<lastmod>' . $lastmod . '</lastmod>';
    echo "</url>\n";
}
echo "</urlset>\n";
