<?php
declare(strict_types=1);
// CLI only: no HTTP handler and no customer values in this script or its output.
if(PHP_SAPI!=='cli'){http_response_code(404);exit;}
require getcwd().'/ai_booking_store.php';
$locks=[];
try {
    if(rm_rental_maintenance())throw new RuntimeException('Maintenance');
    $path=rm_rental_file('rental_assets.php');
    $locks[]=rm_booking_lock(rm_rental_file('reservations.json').'.lock');
    $assetLock=rm_lock_assets();if(!$assetLock)throw new RuntimeException('Lock unavailable');$locks[]=$assetLock;
    $before=rm_read_assets();
    $name='Szlifierka kątowa 230mm Bosch Professional GWS 2200';
    $copies=array_values(array_filter($before,fn($a)=>($a['equipment']??'')===$name));
    if(count($copies)>1)throw new RuntimeException('Count differs from owner confirmation');
    if(!$copies){
        if(is_link($path)||!is_file($path))throw new RuntimeException('Invalid storage');
        $raw=file_get_contents($path);$mode=fileperms($path)&0777;
        $backup=$path.'.before-bosch-'.bin2hex(random_bytes(8)).'.php';
        // Original envelope begins with PHP exit guard; keep it on any backup.
        if(!str_starts_with($raw,"<?php http_response_code(404); exit; ?>\n"))throw new RuntimeException('Invalid envelope');
        $fp=fopen($backup,'x+b');if(!$fp)throw new RuntimeException('Backup unavailable');
        try{if(!chmod($backup,0600)||fwrite($fp,$raw)!==strlen($raw)||!fflush($fp))throw new RuntimeException('Backup failed');}finally{fclose($fp);}
        if(file_get_contents($backup)!==$raw)throw new RuntimeException('Backup differs');
        $after=$before;
        $after[]=['id'=>'bosch230-'.bin2hex(random_bytes(8)),'label'=>'Bosch GWS 2200 — 1','equipment'=>$name,'asset_kind'=>'standard','in_service'=>false,'next_service'=>'','service_every'=>0,'service_at_count'=>0,'repairs'=>[]];
        if(!rm_write_assets($after)||!chmod($path,$mode)||rm_read_assets()!==$after){
            $tmp=$path.'.restore-'.bin2hex(random_bytes(8)).'.php';
            if(file_put_contents($tmp,$raw)!==strlen($raw)||!chmod($tmp,$mode)||!rename($tmp,$path)||file_get_contents($path)!==$raw)throw new RuntimeException('Manual restore required');
            throw new RuntimeException('Restored previous inventory');
        }
        echo "REGISTERED_ONE_COPY_BACKUP_VERIFIED\n";
    }else echo "ONE_COPY_ALREADY_REGISTERED\n";
    $assets=rm_read_assets();
    $item=['equipment'=>$name,'start_date'=>'2026-11-11','time'=>'15:30','end_date'=>'2026-11-12','return_time'=>'15:30'];
    $picked=rm_auto_pick_asset($item,rm_booking_list(rm_rental_file('reservations.json')),$assets);
    echo ($picked['error']===''&&!empty($picked['asset']['id'])?'TARGET_TERM_AVAILABLE':'TARGET_TERM_REQUIRES_OWNER_REVIEW')."\n";
}catch(Throwable $e){fwrite(STDERR,"REGISTRATION_STOPPED\n");exit(1);}
finally{foreach(array_reverse($locks)as $lock){flock($lock,LOCK_UN);fclose($lock);}}
