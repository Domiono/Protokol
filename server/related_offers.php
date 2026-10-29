<?php
declare(strict_types=1);

/**
 * Public catalogue recommendations. No API, AI calls or manual product IDs.
 * The only source is the same public data.json used by sitemap.php.
 */
function rm_related_normalize(string $value): string {
    $value = function_exists('mb_strtolower') ? mb_strtolower($value, 'UTF-8') : strtolower($value);
    $value = strtr($value, [
        'ą'=>'a', 'ć'=>'c', 'ę'=>'e', 'ł'=>'l', 'ń'=>'n',
        'ó'=>'o', 'ś'=>'s', 'ź'=>'z', 'ż'=>'z',
        'Ą'=>'a', 'Ć'=>'c', 'Ę'=>'e', 'Ł'=>'l', 'Ń'=>'n',
        'Ó'=>'o', 'Ś'=>'s', 'Ź'=>'z', 'Ż'=>'z',
    ]);
    return trim((string)preg_replace('/[^a-z0-9]+/', ' ', $value));
}

function rm_related_slug(string $name): string {
    return str_replace(' ', '-', rm_related_normalize($name));
}

/** Classify products by actual titles, not their changing database IDs. */
function rm_related_types(array $item): array {
    $name = rm_related_normalize((string)($item['name'] ?? ''));
    $patterns = [
        'wall_sander_set' => ['zyrafa', 'odkurzacz', 'zestaw'],
        'wall_sander' => ['zyrafa', 'szlifierka do gladzi', 'szlifierka do gipsu'],
        'floor_sander' => ['szlifierka do betonu', 'szlifierka do posadzek', 'msw fg 250'],
        'angle_grinder' => ['szlifierka katowa'],
        'industrial_vacuum' => ['odkurzacz przemyslowy', 'bosch gas', 'metabo asr'],
        'extractor' => ['puzzi', 'odkurzacz pioracy', 'ekstrakcyjny'],
        'wall_chaser' => ['bruzdownica'],
        'demolition_hammer' => ['mlot', 'sds max'],
        'wood_chipper' => ['rebak', 'rozdrabniacz do galezi'],
        'cultivator' => ['glebogryzarka'],
        'scarifier' => ['wertykulator'],
        'soil_auger' => ['wiertnica do gleby'],
        'ladder' => ['drabina'],
        'airless' => ['agregat malarski'],
        'pressure_washer' => ['myjka cisnieniowa', 'karcher hd'],
        'dehumidifier' => ['osuszacz'],
        'power_generator' => ['agregat pradotworczy'],
        'compactor' => ['zageszczarka'],
        'tile_cutter' => ['przecinarka do plytek', 'maszyna do ciecia plytek'],
        'saw' => ['ukosnica', 'zaglebiarka', 'pilarka'],
        'ozonator' => ['ozonator'],
        'pipe_press' => ['zaciskarka', 'praska do rur'],
    ];
    // A sander + vacuum package is not a second standalone vacuum/sander.
    if (str_contains($name, 'zyrafa') && str_contains($name, 'odkurzacz')
        && (str_contains($name, 'zestaw') || str_contains($name, 'szlifierka'))) {
        return ['wall_sander_set'];
    }
    $types = [];
    foreach ($patterns as $type => $needles) {
        if ($type === 'wall_sander_set') continue;
        foreach ($needles as $needle) {
            if (str_contains($name, $needle)) {
                $types[] = $type;
                break;
            }
        }
    }
    return array_values(array_unique($types));
}

function rm_related_score(array $current, array $candidate): int {
    $types = rm_related_types($current);
    $other = rm_related_types($candidate);
    $complements = [
        'wall_sander' => ['wall_sander_set'=>135, 'industrial_vacuum'=>120, 'ladder'=>50, 'airless'=>42],
        'wall_sander_set' => ['airless'=>60, 'ladder'=>55, 'wall_sander'=>20],
        'floor_sander' => ['industrial_vacuum'=>125, 'angle_grinder'=>115],
        'angle_grinder' => ['industrial_vacuum'=>95, 'floor_sander'=>100],
        'industrial_vacuum' => ['wall_sander'=>125, 'floor_sander'=>115, 'wall_chaser'=>105, 'angle_grinder'=>80],
        'wall_chaser' => ['industrial_vacuum'=>125, 'demolition_hammer'=>95],
        'demolition_hammer' => ['wall_chaser'=>100, 'industrial_vacuum'=>65],
        'wood_chipper' => ['wood_chipper'=>130, 'cultivator'=>46, 'scarifier'=>45],
        'cultivator' => ['scarifier'=>85, 'wood_chipper'=>55, 'soil_auger'=>55],
        'scarifier' => ['cultivator'=>85, 'wood_chipper'=>55],
        'soil_auger' => ['cultivator'=>70],
        'extractor' => ['dehumidifier'=>100, 'industrial_vacuum'=>55],
        'dehumidifier' => ['extractor'=>105],
        'ladder' => ['wall_sander'=>90, 'airless'=>80, 'wall_sander_set'=>75],
        'airless' => ['wall_sander'=>70, 'ladder'=>65, 'wall_sander_set'=>65],
        'pressure_washer' => ['power_generator'=>50],
        'power_generator' => ['pressure_washer'=>50],
        'tile_cutter' => ['industrial_vacuum'=>55, 'angle_grinder'=>40],
        'saw' => ['industrial_vacuum'=>45],
    ];
    $score = 0;
    foreach ($types as $type) {
        foreach ($other as $candidateType) {
            if ($type === $candidateType) {
                $score = max($score, $type === 'wood_chipper' ? 130 : 72);
            }
            $score = max($score, $complements[$type][$candidateType] ?? 0);
        }
    }
    $category = rm_related_normalize((string)($current['category'] ?? $current['cat'] ?? ''));
    $candidateCategory = rm_related_normalize((string)($candidate['category'] ?? $candidate['cat'] ?? ''));
    // Fallback for a newly-added product without any recognized type.
    if ($category !== '' && $category === $candidateCategory) {
        $score = max($score, 12);
    }
    return $score;
}

/** Stable ranking; produces up to three actual existing catalogue items. */
function rm_related_pick(array $current, array $catalogue, int $limit = 3): array {
    $name = trim((string)($current['name'] ?? ''));
    if ($name === '' || $limit < 1) return [];
    $ownSlug = rm_related_slug($name);
    $ownId = (string)($current['id'] ?? '');
    $ranked = [];
    $seen = [];
    foreach ($catalogue as $candidate) {
        if (!is_array($candidate) || !isset($candidate['name']) || !is_string($candidate['name'])) continue;
        $candidateName = trim($candidate['name']);
        $slug = rm_related_slug($candidateName);
        if ($slug === '' || $slug === $ownSlug) continue;
        if ($ownId !== '' && (string)($candidate['id'] ?? '') === $ownId) continue;
        if (isset($seen[$slug])) continue;
        $score = rm_related_score($current, $candidate);
        if ($score <= 0) continue;
        $ranked[] = ['score'=>$score, 'slug'=>$slug, 'product'=>$candidate];
        $seen[$slug] = true;
    }
    usort($ranked, static function (array $a, array $b): int {
        return ($b['score'] <=> $a['score']) ?: strcmp($a['slug'], $b['slug']);
    });
    return array_map(static fn(array $item): array => $item['product'], array_slice($ranked, 0, $limit));
}

function rm_related_price(array $item): string {
    $raw = $item['price'] ?? null;
    if (!is_string($raw) && !is_int($raw) && !is_float($raw)) return '';
    $value = trim((string)$raw);
    if (!preg_match('/^\d+(?:[,.]\d{1,2})?(?:\s*zł)?$/u', $value)) return '';
    $number = (float)str_replace(',', '.', preg_replace('/\s*zł$/u', '', $value));
    if ($number <= 0) return '';
    return rtrim(rtrim(number_format($number, 2, ',', ''), '0'), ',') . ' zł / doba';
}

function rm_related_image(array $item): string {
    $images = $item['images'] ?? [];
    if (!is_array($images)) return '';
    foreach ($images as $raw) {
        if (!is_string($raw)) continue;
        $file = basename(trim($raw));
        if ($file !== '' && preg_match('/^[^\/\\\x00-\x1f]+\.(?:jpe?g|png|webp|gif)$/i', $file)) {
            return '/uploads/' . rawurlencode($file);
        }
    }
    return '';
}

function rentmor_render_related_offers(array $product, ?array $catalogue = null): string {
    if ($catalogue === null) {
        $file = __DIR__ . '/data.json';
        $json = is_file($file) ? @file_get_contents($file) : false;
        $decoded = $json === false ? null : json_decode($json, true);
        $catalogue = is_array($decoded) ? $decoded : [];
    }
    $items = rm_related_pick($product, $catalogue);
    if (!$items) return '';
    $e = static fn(string $value): string => htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
    $html = '<section class="mt-8 border-t border-gray-200 pt-6" aria-label="Polecany sprzęt" data-rentmor-recommendations="v1">';
    $html .= '<h2 class="text-xl sm:text-2xl font-bold mb-3">Zobacz również inne urządzenia</h2>';
    $html .= '<p class="text-sm text-gray-600 mb-4">Sprzęt dobrany automatycznie z aktualnych ofert. Dostępność na termin potwierdzamy przy rezerwacji.</p>';
    $html .= '<div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:12px">';
    foreach ($items as $item) {
        $name = (string)$item['name'];
        $href = '/' . rm_related_slug($name) . '/';
        $image = rm_related_image($item);
        $price = rm_related_price($item);
        $html .= '<a data-rentmor-recommendation="1" class="block rounded-xl border border-gray-200 bg-white p-3 hover:border-gray-400" href="' . $e($href) . '">';
        if ($image !== '') {
            $html .= '<img class="w-full h-32 object-contain mb-3" src="' . $e($image) . '" alt="' . $e($name) . '" loading="lazy" decoding="async" width="240" height="128">';
        }
        $html .= '<strong class="block text-sm leading-5 text-gray-900">' . $e($name) . '</strong>';
        if ($price !== '') $html .= '<span class="block text-sm text-gray-700 mt-2">' . $e($price) . '</span>';
        $html .= '</a>';
    }
    return $html . '</div></section>';
}
