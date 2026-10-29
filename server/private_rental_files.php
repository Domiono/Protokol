<?php
declare(strict_types=1);
/** Explicit cut-over marker ensures all writers use the same storage location. */
if (isset($_SERVER['SCRIPT_FILENAME']) && realpath((string)$_SERVER['SCRIPT_FILENAME']) === __FILE__) { http_response_code(404); exit; }
function rm_rental_private_dir(): string { return dirname(__DIR__).'/rentmor-private/rental-data'; }
function rm_rental_migrated(): bool { return is_file(rm_rental_private_dir().'/active.flag'); }
function rm_rental_maintenance(): bool { return is_file(rm_rental_private_dir().'/maintenance.flag'); }
function rm_rental_file(string $name): string {
    if (!in_array($name,['reservations.json','protocols.json','customers.json','costs.json','rental_assets.php'],true))
        throw new InvalidArgumentException('Unsupported rental data path');
    return (rm_rental_migrated()?rm_rental_private_dir():__DIR__).'/'.$name;
}
