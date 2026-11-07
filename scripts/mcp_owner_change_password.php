<?php
declare(strict_types=1);
/** RentMor Control owner password change. No password or private data is ever logged. */
require_once __DIR__.'/oauth_core.php';

function rm_password_csrf(string $nonce, int $until, string $secret): string {
    return hash_hmac('sha256', 'rentmor-owner-change|'.$nonce.'|'.$until, $secret);
}
function rm_password_form(): never {
    $owner = rm_owner();
    if (!$owner) rm_html('Zmiana hasła niedostępna', '<p>Konto właściciela nie jest dostępne.</p>', 503);
    $nonce = rm_random_token(18);
    $until = time() + 600;
    $proof = rm_password_csrf($nonce, $until, $owner['password_hash']);
    rm_html('Zmiana hasła właściciela RentMor Control',
        '<p>Ustaw nowe hasło o długości 20–64 znaków ASCII. Nie przesyłaj haseł w rozmowie.</p>'.
        '<form method="post" action="change-password.php" autocomplete="off">'.
        rm_hidden('nonce', $nonce).rm_hidden('until', (string)$until).rm_hidden('proof', $proof).
        '<label for="old">Obecne hasło właściciela</label>'.
        '<input id="old" name="old" type="password" required autocomplete="current-password">'.
        '<label for="new">Nowe hasło (20–64 znaki)</label>'.
        '<input id="new" name="new" type="password" minlength="20" maxlength="64" required autocomplete="new-password">'.
        '<label for="confirm">Powtórz nowe hasło</label>'.
        '<input id="confirm" name="confirm" type="password" minlength="20" maxlength="64" required autocomplete="new-password">'.
        '<button type="submit">Zmień hasło</button>'.
        '</form><p><small>Po zmianie zostaną unieważnione dotychczasowe tokeny i otwarte formularze OAuth. Istniejące rejestracje klientów zostaną zachowane.</small></p>');
}

try {
    $method = (string)($_SERVER['REQUEST_METHOD'] ?? 'GET');
    if ($method === 'GET') rm_password_form();
    if ($method !== 'POST') rm_html('Niedozwolona metoda', '<p>Nieprawidłowe żądanie.</p>', 405);
    $owner = rm_owner();
    if (!$owner) rm_html('Zmiana hasła niedostępna', '<p>Brak właściciela.</p>', 503);
    if (!rm_rate_allow('change-password', 8, 900)) {
        rm_html('Za dużo prób', '<p>Spróbuj ponownie później.</p>', 429);
    }
    $nonce = (string)($_POST['nonce'] ?? '');
    $until = filter_var($_POST['until'] ?? null, FILTER_VALIDATE_INT);
    $proof = (string)($_POST['proof'] ?? '');
    if (!rm_valid_token($nonce) || !is_int($until) || $until < time() || $until > time()+600 ||
        !preg_match('/^[a-f0-9]{64}$/D', $proof) ||
        !hash_equals(rm_password_csrf($nonce, $until, $owner['password_hash']), $proof)) {
        rm_html('Formularz wygasł', '<p>Otwórz ponownie stronę zmiany hasła i wypełnij nowy formularz.</p>', 403);
    }
    $old = (string)($_POST['old'] ?? '');
    $new = (string)($_POST['new'] ?? '');
    $confirm = (string)($_POST['confirm'] ?? '');
    // ASCII-byte limit avoids bcrypt's silent 72-byte truncation.
    if (strlen($old) > 1024 || !password_verify($old, $owner['password_hash'])) {
        rm_html('Nieprawidłowe hasło', '<p>Nie udało się potwierdzić obecnego hasła.</p>', 403);
    }
    if (strlen($new) < 20 || strlen($new) > 64 ||
        !preg_match('/^[\x21-\x7E]{20,64}$/D', $new) ||
        $confirm !== $new || hash_equals($old, $new)) {
        rm_html('Nieprawidłowe nowe hasło', '<p>Użyj nowego hasła 20–64 znaków ASCII bez spacji, identycznego w obu polach.</p>', 400);
    }
    $hash = password_hash($new, PASSWORD_DEFAULT);
    if (!is_string($hash)) throw new RuntimeException('Password hashing failed');
    $private = rm_private_dir();
    if (!$private) throw new RuntimeException('Private storage unavailable');
    $lock = fopen($private.'/owner.lock', 'c+');
    if (!$lock || !flock($lock, LOCK_EX)) throw new RuntimeException('Owner lock unavailable');
    try {
        // Re-read under exclusive lock: do not overwrite concurrent changes.
        $fresh = rm_owner();
        if (!$fresh || !hash_equals($fresh['password_hash'], $owner['password_hash']) ||
            !password_verify($old, $fresh['password_hash'])) {
            rm_html('Zmiana już wykonana', '<p>Odśwież stronę i spróbuj ponownie.</p>', 409);
        }
        $fresh['password_hash'] = $hash;
        $fresh['changed_at'] = gmdate(DATE_ATOM);
        $json = json_encode($fresh, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES);
        if (!rm_atomically_write($private.'/owner.json', $json)) throw new RuntimeException('Owner write failed');
        // Invalidate OAuth access without erasing registered client metadata.
        rm_state(static function (&$state): void {
            foreach (['access', 'refresh', 'codes', 'login'] as $group) $state[$group] = [];
            unset($state['pair_until']);
        });
    } finally {
        flock($lock, LOCK_UN);
        fclose($lock);
    }
    rm_html('Hasło zmienione', '<p>Nowe hasło zostało zapisane. Dotychczasowe tokeny OAuth zostały unieważnione.</p>'.
        '<p>Wróć do ChatGPT i rozpocznij nowe logowanie, używając nowego hasła.</p>');
} catch (Throwable $e) {
    error_log('RentMor Control password-change error (no details)');
    rm_html('Zmiana hasła niedostępna', '<p>Wystąpił błąd serwera. Nie ponawiaj operacji, zanim nie sprawdzimy jej wyniku.</p>', 503);
}
