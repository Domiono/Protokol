#!/usr/bin/env python3
"""Read-only structural audit of the actual live hosting PHP template.
Never print source lines or customer data.
"""
from pathlib import Path
import re
import sys
source = Path(sys.argv[1]).read_text(encoding='utf-8')
flags = {
 'php_sessions': len(re.findall(r'\bsession_start\s*\(',source)),
 'phone_track_calls': len(re.findall(r'\btrackCall\s*\(',source)),
 'phone_track_functions': len(re.findall(r'function\s+trackCall\s*\(',source)),
 'phone_track_arrows': len(re.findall(r'(?:const|let|var)\s+trackCall\s*=|window\.trackCall\s*=',source)),
 'legacy_phone_clicks':len(re.findall(r'phone_clicks|total_phone_clicks',source,re.I)),
 'legacy_click_urls':len(re.findall(r'phone_event\.php|phone_click(?:s)?\.php',source,re.I)),
 'inline_cookie_consent':source.count('rm_stats_consent'),
 'statistics_patch':source.count('RentMor browser-confirmed statistics v2'),
 'site_contact_form':len(re.findall(r'<form\b',source,re.I)),
 'message_write_hook':source.count('RENTMOR_FCM_AFTER_MESSAGE_SAVE_V1'),
 'message_protection':source.count('rentmor-message-retention-v1'),
 'footer_count':len(re.findall(r'</footer\s*>',source,re.I)),
 'body_count':len(re.findall(r'</body\s*>',source,re.I)),
 'page_size_bytes':len(source.encode('utf-8')),
}
for key,value in flags.items():
    print(f'{key}={value}')
# Never upload or output the PHP file; the summary contains only static code structure.
if not (flags['page_size_bytes']>10000 and flags['footer_count']==1 and flags['body_count']==1
        and flags['phone_track_calls']>=1 and flags['site_contact_form']>=1):
    raise SystemExit('Live index does not match expected public structure')
