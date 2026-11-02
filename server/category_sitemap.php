<?php
declare(strict_types=1);
require_once __DIR__ . '/rental_categories.php';
// Keep the existing dynamic sitemap as the sole source of product and guide URLs.
ob_start();
require __DIR__ . '/sitemap.php';
$xml = ob_get_clean();
$extra = '';
foreach (rm_categories() as $slug=>$category) {
    $url = 'https://rentmor.pl/'.$slug.'/';
    if (!str_contains($xml, '<loc>'.$url.'</loc>')) $extra .= '  <url><loc>'.$url.'</loc></url>' . "\n";
}
echo str_replace('</urlset>', $extra.'</urlset>', $xml);
