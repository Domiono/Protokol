#!/usr/bin/env python3
"""Summarize multi-page Lighthouse lab results; no private data and no requests."""
import json
import sys
from pathlib import Path

for filename in sys.argv[1:]:
    path = Path(filename)
    data = json.loads(path.read_text())
    categories = data.get('categories', {})
    scores = {k: round(v.get('score', 0) * 100) if v.get('score') is not None else None
              for k, v in categories.items()}
    audits = data.get('audits', {})
    metrics = {}
    for k in ('largest-contentful-paint', 'first-contentful-paint', 'total-blocking-time',
              'cumulative-layout-shift', 'speed-index'):
        item = audits.get(k, {})
        metrics[k] = round(item['numericValue'], 2) if isinstance(item.get('numericValue'), (int, float)) else None
    print('GOOGLE_QUALITY', json.dumps({'file': str(path), 'scores': scores, 'metrics': metrics}, ensure_ascii=False))
    failures = [
        {'id': key, 'title': audit.get('title', key),
         'score': round(audit['score'] * 100) if isinstance(audit.get('score'), (int, float)) else None,
         'savings_ms': round(audit.get('details', {}).get('overallSavingsMs', 0) or 0),
         'savings_bytes': round(audit.get('details', {}).get('overallSavingsBytes', 0) or 0)}
        for key, audit in audits.items()
        if audit.get('scoreDisplayMode') not in ('informative', 'manual', 'notApplicable')
        and isinstance(audit.get('score'), (int, float)) and audit['score'] < 0.9
    ]
    failures.sort(key=lambda item: (-(item['savings_ms']), -(item['savings_bytes']), item['score'] or 0))
    print('TOP_ISSUES', json.dumps({'file': str(path), 'issues': failures[:18]}, ensure_ascii=False))
