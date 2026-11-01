<?php
require __DIR__ . '/../server/public_metadata.php';
function check($ok, $message) { if (!$ok) throw new RuntimeException($message); }
$title = 'Przecinarka do glazury Norton Clipper TR202 – wynajem Kraków | RentMor';
$body = '<body><h1>Original</h1><img data-rm-image-kind="detail" src="/uploads/machine.jpg" alt="Maszyna &quot;A&quot;"><p>130 zł</p></body>';
$source = '<html><head><title>' . $title . '</title><meta property="og:type" content="product"><meta property="og:title" content="' . $title . '"></head>' . $body . '</html>';
$out = rentmor_public_metadata($source);
check(str_contains($out, 'https://rentmor.pl/uploads/machine.jpg'), 'Product photo');
check(str_contains($out, 'Maszyna &quot;A&quot;'), 'Escaped alternative text');
check(substr_count($out, 'Przecinarka Norton TR202 – wynajem Kraków | RentMor') === 2, 'Both titles');
check(str_contains($out, $body), 'Body untouched');
check(rentmor_public_metadata($out) === $out, 'Idempotent');
check(rentmor_public_metadata('{"ok":true}') === '{"ok":true}', 'Non HTML untouched');
$unsafe = str_replace('/uploads/machine.jpg', '//evil.example/a.jpg', $source);
check(str_contains(rentmor_public_metadata($unsafe), 'content="https://rentmor.pl/assets/rm-20261030-512.png"'), 'Reject remote source');
$existing = str_replace('</head>', '<meta property="og:image" content="https://rentmor.pl/existing.png"></head>', $source);
check(substr_count(rentmor_public_metadata($existing), 'property="og:image"') === 1, 'Preserve existing image');
$terms = rentmor_public_metadata('<html><head><title>Zasady</title></head><body>Terms</body></html>');
check(str_contains($terms, 'https://rentmor.pl/zasady.php'), 'Terms URL');
echo "PASS: metadata, product images, escaping, titles and unchanged body\n";
