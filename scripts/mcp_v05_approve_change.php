<?php
declare(strict_types=1);
/** Owner's explicit approval page for a single pre-staged change. No publishing here. */
require_once __DIR__.'/oauth_core.php';
require_once __DIR__.'/mcp_write_tools.php';
function rm_write_html_safe(string $x): string {
    return htmlspecialchars($x,ENT_QUOTES|ENT_SUBSTITUTE,'UTF-8');
}
try {
    $id=(string)($_SERVER['REQUEST_METHOD']==='POST'?($_POST['change_id']??''):($_GET['id']??''));
    if(!preg_match('/^[a-f0-9]{32}$/D',$id))rm_html('Brak zmiany','<p>Nieprawidłowy identyfikator.</p>',404);
    $owner=rm_owner();
    $row=rm_write_stage_read($id);
    if(!$owner||!$row||($row['expires']??0)<time()||($row['status']??'')==='applied')
        rm_html('Zmiana niedostępna','<p>Zmiana wygasła, została wykonana lub nie istnieje.</p>',404);
    $fingerprint=hash('sha256',$owner['password_hash']);
    if(!hash_equals((string)($row['owner_fp']??''),$fingerprint))
        rm_html('Zmiana wygasła','<p>Zmieniło się hasło właściciela. Przygotuj zmianę ponownie.</p>',403);
    $approvalHash=hash_hmac('sha256','rm-source-approval|'.$id.'|'.$row['expires'],$owner['password_hash']);
    if(($_SERVER['REQUEST_METHOD']??'GET')==='GET') {
        $new=$row['new_content']??null;
        $oldPreview=(string)($row['preview_before']??'');
        $newPreview=(string)($row['preview_after']??($new??''));
        // Show exact edited fragments (not only the first 1800 bytes of the resulting file).
        // Full copy/rollback views can be large; the SHA identifies the exact stored version.
        $name=rm_write_html_safe((string)$row['filename']);
        $action=rm_write_html_safe((string)$row['action']);
        $body='<p><strong>Ta strona zatwierdza dokładnie jedną operację.</strong> Niczego nie publikuje sama.</p>'.
            '<p>Plik: <code>'.$name.'</code><br>Operacja: <code>'.$action.'</code></p>'.
            '<p>SHA256 przed: <code>'.rm_write_html_safe((string)($row['old_sha']??'brak')).'</code><br>'.
            'SHA256 po: <code>'.rm_write_html_safe((string)($row['new_sha']??'brak')).'</code></p>'.
            '<p><strong>Fragment PRZED zmianą:</strong></p>'.
            '<pre style="white-space:pre-wrap;overflow-wrap:anywhere;max-height:240px;overflow:auto;background:#f1f4fa;padding:12px">'.
            rm_write_html_safe($oldPreview===''?'(nowy plik)':$oldPreview).'</pre>'.
            '<p><strong>Fragment PO zmianie:</strong></p>'.
            '<pre style="white-space:pre-wrap;overflow-wrap:anywhere;max-height:240px;overflow:auto;background:#eaf5ed;padding:12px">'.
            rm_write_html_safe($newPreview===''?'(usunięcie pliku)':$newPreview).'</pre>'.
            '<form method="post" action="approve-change.php">'.
            rm_hidden('change_id',$id).rm_hidden('proof',$approvalHash).
            '<label for="password">Hasło właściciela</label>'.
            '<input type="password" name="password" id="password" required autocomplete="current-password">'.
            '<label><input type="checkbox" name="agree" required style="width:auto"> '.
            'Potwierdzam tę operację na wyświetlonym pliku i wyrażam zgodę na jej publikację.</label>'.
            '<button type="submit">Zatwierdź tę zmianę</button></form>'.
            '<p><small>Po zatwierdzeniu ChatGPT będzie mógł zastosować tylko tę konkretną zmianę w ciągu 5 minut. '.
            'Kopia zapasowa i porównanie SHA256 są obowiązkowe.</small></p>';
        rm_html('Zatwierdź zmianę RentMor',$body);
    }
    if(($_SERVER['REQUEST_METHOD']??'')!=='POST')
        rm_html('Niedozwolona metoda','<p>Wymagany formularz POST.</p>',405);
    if(!rm_rate_allow('mcp-change-approval',8,900))
        rm_html('Za dużo prób','<p>Osiągnięto limit prób zatwierdzania.</p>',429);
    $proof=(string)($_POST['proof']??'');
    $password=(string)($_POST['password']??'');
    if(!hash_equals($approvalHash,$proof)||($_POST['agree']??'')!=='on'||
        strlen($password)>72||strlen($password)<16||
        !password_verify($password,$owner['password_hash']))
        rm_html('Odmowa','<p>Nieprawidłowe potwierdzenie lub hasło.</p>',403);
    $lock=rm_write_lock();
    try {
        $fresh=rm_write_stage_read($id);
        if(!$fresh||($fresh['expires']??0)<time()||($fresh['status']??'')!=='pending'||
            !hash_equals((string)($fresh['owner_fp']??''),$fingerprint))
            rm_html('Zmiana niedostępna','<p>Zatwierdzenie już wykonane lub zmiana wygasła.</p>',409);
        $fresh['status']='approved';
        $fresh['approved_until']=min((int)$fresh['expires'],time()+300);
        rm_write_stage_save($fresh);
    }finally{rm_write_unlock($lock);}
    rm_html('Zatwierdzenie przyjęte','<p>Ta konkretna zmiana została zatwierdzona.</p>'.
        '<p>Wróć do ChatGPT, aby wykonać <code>apply_approved_site_change</code>. '.
        'Zatwierdzenie wygaśnie po maksymalnie pięciu minutach.</p>');
}catch(Throwable $error){
    error_log('RentMor MCP change-approval failed (details redacted)');
    rm_html('Błąd zatwierdzania','<p>Nie udało się bezpiecznie zatwierdzić zmiany.</p>',503);
}
