<?php
declare(strict_types=1);

/**
 * Render product descriptions with readable paragraphs, compact section headings
 * and semantic bullet lists. The original text is escaped, not interpreted as HTML.
 */
function rentmor_render_description($raw): string {
    $text = str_replace(["\r\n", "\r"], "\n", trim((string)$raw));
    if ($text === '') return '<p>Zapytaj nas o szczegóły wynajmu tego sprzętu.</p>';

    $lines = explode("\n", $text);
    $output = [];
    $paragraph = [];
    $list = [];

    $flushParagraph = static function () use (&$paragraph, &$output): void {
        if (!$paragraph) return;
        $parts = array_map(static fn($line) => htmlspecialchars($line, ENT_QUOTES, 'UTF-8'), $paragraph);
        $output[] = '<p class="mb-4">' . implode('<br>', $parts) . '</p>';
        $paragraph = [];
    };
    $flushList = static function () use (&$list, &$output): void {
        if (!$list) return;
        $items = [];
        foreach ($list as $item) {
            $items[] = '<li class="pl-1">' . htmlspecialchars($item, ENT_QUOTES, 'UTF-8') . '</li>';
        }
        $output[] = '<ul class="list-disc pl-5 space-y-2 mb-5">' . implode('', $items) . '</ul>';
        $list = [];
    };

    foreach ($lines as $line) {
        $line = trim($line);
        if ($line === '') {
            $flushList();
            $flushParagraph();
            continue;
        }
        if (preg_match('/^[-–•*]\s+(.+)$/u', $line, $match)) {
            $flushParagraph();
            $list[] = trim($match[1]);
            continue;
        }
        if (preg_match('/^.{2,75}:$/u', $line)) {
            $flushParagraph();
            $flushList();
            $output[] = '<h4 class="text-base sm:text-lg font-bold text-gray-900 mt-5 mb-3">'
                    . htmlspecialchars($line, ENT_QUOTES, 'UTF-8') . '</h4>';
            continue;
        }
        $flushList();
        $paragraph[] = $line;
    }
    $flushList();
    $flushParagraph();
    return implode("\n", $output);
}
