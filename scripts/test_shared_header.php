<?php
/** Read-only regression checks for the shared public RentMor navigation. */
require __DIR__ . '/../server/rental_categories.php';
function nav_check(bool $ok, string $message): void {
    if (!$ok) throw new RuntimeException($message);
}
$menu = rm_category_menu();
preg_match_all('~<a href="([^"]+)">([^<]+)</a>~', $menu, $links, PREG_SET_ORDER);
$expected = [
    ['/sprzet-budowlany/', 'Sprzęt budowlany'],
    ['/sprzet-ogrodniczy/', 'Sprzęt ogrodniczy'],
    ['/sprzet-czyszczacy/', 'Sprzęt czyszczący'],
    ['/#poradniki', 'Poradniki'],
    ['/zasady.php', 'Zasady wynajmu'],
];
nav_check(count($links) === count($expected), 'Exactly five menu links');
foreach ($expected as $i => [$url, $label]) {
    nav_check($links[$i][1] === $url && $links[$i][2] === $label, 'Menu link/order ' . $i);
}
$header = rm_public_site_header();
nav_check(str_contains($header, $menu), 'Category header uses common menu');
nav_check(str_contains($header, 'class="rm-header-phone"'), 'Accessible header telephone');
nav_check(str_contains($header, '/assets/rentmor-logo-mobile-20261103.webp'), 'Optimized logo');

$category = file_get_contents(__DIR__ . '/../server/category.php');
nav_check(str_contains($category, 'rm_public_site_header()'), 'Categories share header');
foreach ([
    'poradnik-agregat-malarski-jak-malowac-dobor-dysz.php',
    'poradnik-pranie-tapicerki-karcher-puzzi.php',
    'poradnik-jaki-papier-scierny-do-zyrafy.php',
    'polityka-prywatnosci.php',
] as $page) {
    $source = file_get_contents(__DIR__ . '/../server/' . $page);
    nav_check(str_contains($source, "require_once __DIR__ . '/rental_categories.php'"), $page . ' loads navigation');
    nav_check(str_contains($source, '<?= rm_category_menu() ?>'), $page . ' embeds same menu');
    nav_check(str_contains($source, 'class="rm-category-header rm-site-header"'), $page . ' uses same header classes');
    nav_check(str_contains($source, '/assets/rentmor-logo-mobile-20261103.webp'), $page . ' uses same logo');
    nav_check(str_contains($source, '/seo-structure.css?v=20261103-unified-header'), $page . ' shares responsive styles');
}
$css = file_get_contents(__DIR__ . '/../server/seo-structure.css');
nav_check(str_contains($css, '.rm-category-header{background:#fff;'), 'Readable light header');
nav_check(str_contains($css, '.rm-category-header .rm-header-phone'), 'Phone button style');
echo "PASS: common public header, five menu links, logo, guides and privacy\n";
