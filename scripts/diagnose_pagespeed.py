#!/usr/bin/env python3
"""Safe performance diagnostics from one Chrome Lighthouse JSON report."""
import json,sys
from pathlib import Path
from urllib.parse import urlparse
p=json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
a=p["audits"]
print("PERF",json.dumps({k:round(v["score"]*100) for k,v in p["categories"].items() if v.get("score") is not None}))
for key in ("mainthread-work-breakdown","bootup-time","long-tasks","third-party-summary",
            "network-requests","resource-summary","critical-request-chains",
            "render-blocking-resources","unused-css-rules","lcp-breakdown-insight",
            "lcp-discovery-insight"):
    o=a.get(key,{})
    details=o.get("details") or {}
    print("AUDIT",key,"value",o.get("displayValue"),"items",len(details.get("items",[])))
    items=details.get("items",[])
    if key=="network-requests":
        rows=[]
        for x in items:
            url=x.get("url","")
            path=urlparse(url).path.split("/")[-1]
            rows.append((int(x.get("transferSize") or 0),x.get("resourceType"),urlparse(url).netloc,path[:75],int(x.get("startTime") or 0)))
        for entry in sorted(rows,reverse=True)[:28]:print("NETWORK",json.dumps(entry))
        print("NETWORK_TOTAL",sum(x[0] for x in rows),"COUNT",len(rows),"IMAGE_TRANSFER",sum(x[0] for x in rows if x[1]=="Image"))
    elif key=="resource-summary":
        for x in items:print("RESOURCE",json.dumps({k:x.get(k) for k in ("resourceType","requestCount","transferSize","size")}))
    elif key in ("bootup-time","third-party-summary","mainthread-work-breakdown","render-blocking-resources","long-tasks"):
        for x in items[:12]:
            safe={k:x.get(k) for k in ("group","label","duration","total","scripting","mainThreadTime","blockingTime","wastedMs","url","source","startTime") if k in x}
            if "url" in safe:
                safe["url"]=urlparse(safe["url"]).netloc+urlparse(safe["url"]).path[:90]
            print("DETAIL",json.dumps(safe))
    else:print("DETAIL_KEYS",list(details))

# Post-deployment measurement 2026-10-25: compare against baseline run 40229497971.
