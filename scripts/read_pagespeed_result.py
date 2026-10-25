#!/usr/bin/env python3
"""Read a PageSpeed Insights v5 or Lighthouse report without inventing measurements."""
import json,sys
from pathlib import Path

def main():
    path, origin, device = sys.argv[1:]
    payload=json.loads(Path(path).read_text(encoding="utf-8"))
    report = payload.get("lighthouseResult",payload)
    if "categories" not in report or "audits" not in report:
        print("NOT_A_LIGHTHOUSE_RESULT: "+str(payload.get("error",payload.get("message","No audit payload")))[:320])
        raise SystemExit(1)
    if report.get("runtimeError"):
        print("LIGHTHOUSE_RUNTIME_ERROR:",report["runtimeError"])
        raise SystemExit(2)
    cats = {k:round(100*v["score"]) if v.get("score") is not None else None
            for k,v in report.get("categories",{}).items()}
    audits=report["audits"]
    vitals={}
    for key in ["first-contentful-paint","largest-contentful-paint","speed-index",
                "total-blocking-time","cumulative-layout-shift","interactive","server-response-time"]:
        item=audits.get(key,{})
        vitals[key]={"numeric":item.get("numericValue"),"display":item.get("displayValue")}
    diagnostics=[]
    ids=["render-blocking-resources","unused-css-rules","unused-javascript",
         "modern-image-formats","uses-optimized-images","uses-responsive-images",
         "offscreen-images","font-display","uses-text-compression",
         "total-byte-weight","network-requests","lcp-discovery-insight",
         "lcp-breakdown-insight","render-blocking-insight","image-delivery-insight",
         "document-latency-insight","cache-insight","third-parties-insight",
         "network-dependency-tree-insight"]
    for key in ids:
        a=audits.get(key)
        if not a:continue
        saving=(a.get("details") or {}).get("overallSavingsMs")
        est=(a.get("details") or {}).get("overallSavingsBytes")
        diagnostics.append({"id":key,"score":a.get("score"),"value":a.get("displayValue"),
                            "savings_ms":saving,"savings_bytes":est,
                            "title":a.get("title")})
    diagnostics.sort(key=lambda x: (x["savings_ms"] or 0, x["savings_bytes"] or 0),reverse=True)
    experience=payload.get("loadingExperience") or payload.get("originLoadingExperience") or {}
    output={"engine":origin,"device":device,"requested_url":report.get("requestedUrl"),
        "final_url":report.get("finalUrl"),"fetchTime":report.get("fetchTime"),
        "lighthouseVersion":report.get("lighthouseVersion"),
        "scores":cats,"metrics":vitals,"opportunities":diagnostics[:12],
        "crux_overall":experience.get("overall_category"),
        "crux":{k:v.get("percentile") for k,v in experience.get("metrics",{}).items()},
        "warnings":report.get("runWarnings",[])[:3]}
    print("PAGESPEED_AUDIT_RESULT="+json.dumps(output,ensure_ascii=False,separators=(",",":")))
if __name__=="__main__":main()
