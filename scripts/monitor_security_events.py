#!/usr/bin/env python3
"""Read redacted private auth events locally and flag excessive failures; never print log lines."""
import datetime as dt
import json
import pathlib
import sys
WINDOW=dt.timedelta(hours=1)
LIMITS={'password_failed':15,'otp_failed':15,'api_unauthorized':200}
def check(folder: pathlib.Path, now: dt.datetime | None = None):
    now=now or dt.datetime.now(dt.timezone.utc)
    counts={key:0 for key in LIMITS}
    for path in sorted(folder.glob('security-events-*.log')):
        if path.stat().st_size>1048577:raise ValueError('Oversized security log')
        for line in path.read_text(encoding='utf-8').splitlines():
            if not line.strip():continue
            try: record=json.loads(line);stamp=dt.datetime.fromisoformat(record['time'])
            except (ValueError,KeyError,TypeError):raise ValueError('Corrupted security event record')
            if stamp.tzinfo is None:raise ValueError('Timestamp without timezone')
            if now-WINDOW<=stamp<=now and record.get('event') in counts:
                counts[record['event']]+=1
    alarming={key:counts[key] for key,limit in LIMITS.items() if counts[key]>=limit}
    return counts,alarming
if __name__=='__main__':
    try:counts,alarming=check(pathlib.Path(sys.argv[1]))
    except (ValueError,OSError,IndexError) as err:
        print('Unable to audit private security events:',type(err).__name__);sys.exit(1)
    print('Authentication security audit last 60m: '+', '.join(f'{k}={v}' for k,v in counts.items()))
    if alarming:
        print('Possible brute-force or unauthorized API activity detected.');sys.exit(1)
