#!/usr/bin/env python3
import datetime as dt
import json
import pathlib
import tempfile
from monitor_security_events import check
now=dt.datetime(2026,10,29,20,tzinfo=dt.timezone.utc)
with tempfile.TemporaryDirectory() as temp:
    folder=pathlib.Path(temp)
    (folder/'security-events-2026-10-29.log').write_text(''.join(json.dumps({'time':(now-dt.timedelta(minutes=5)).isoformat(),'event':'otp_failed','origin':'redacted'})+'\n' for _ in range(15)))
    counts,bad=check(folder,now)
    assert bad=={'otp_failed':15} and counts['otp_failed']==15
    (folder/'security-events-2026-10-29.log').write_text(json.dumps({'time':(now-dt.timedelta(hours=2)).isoformat(),'event':'otp_failed'})+'\n')
    assert not check(folder,now)[1]
print('PASS: private authentication anomaly thresholds and time filtering')
