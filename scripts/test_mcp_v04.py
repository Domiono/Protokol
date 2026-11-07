#!/usr/bin/env python3
"""Test MCP OAuth read/search/preview with synthetic data; no live owner secrets."""
from pathlib import Path
import hashlib,json,socket,subprocess,shutil,sys,tempfile,time,os
from urllib.request import Request,urlopen
from urllib.error import HTTPError,URLError

mcp_src,extra_src,core_src,index_src=map(Path,sys.argv[1:5])
with tempfile.TemporaryDirectory(prefix="rm-v04-") as folder:
    root=Path(folder)
    site=root/"public_html";plugin=site/"rentmor-control-mcp";private=root/"rentmor-control-private"
    plugin.mkdir(parents=True);private.mkdir()
    for source,name in [(mcp_src,"mcp.php"),(extra_src,"mcp_tools_extra.php"),(core_src,"oauth_core.php"),(index_src,"index.php")]:
        shutil.copyfile(source,plugin/name)
    original='<?php\n// Synthetic public RentMor file\n$city = "Kraków";\n'
    (site/"category.php").write_text(original,encoding="utf-8")
    (site/"robots.txt").write_text("User-agent: *\nAllow: /\n")
    (site/"sitemap.php").write_text("<?php\n// public sitemap\n")
    token="synthetic_nonreal_bearer_for_test_only___1234567890"
    (private/"owner.json").write_text(json.dumps({"password_hash":"synthetic_hash_not_used_for_auth_check"}))
    with socket.socket() as sock:
        sock.bind(("127.0.0.1",0));port=sock.getsockname()[1]
    base=f"http://127.0.0.1:{port}/rentmor-control-mcp"
    state={"access":{hashlib.sha256(token.encode()).hexdigest():
           {"scope":"source:read","resource":base+"/index.php","client":"test","expires":int(time.time())+600}}}
    (private/"state.json").write_text(json.dumps(state))
    process=subprocess.Popen(["php","-S",f"127.0.0.1:{port}","-t",str(site)],
        env=os.environ|{"RM_MCP_TEST_BASE_URL":base},stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    seq=0
    def call(method,params=None,authenticated=False):
        global seq
        seq+=1
        payload=json.dumps({"jsonrpc":"2.0","id":seq,"method":method,"params":params or {}}).encode()
        headers={"Content-Type":"application/json"}
        if authenticated:headers["Authorization"]="Bearer "+token
        try:r=urlopen(Request(base+"/index.php",data=payload,headers=headers),timeout=10)
        except HTTPError as exc:r=exc
        return r.status,json.loads(r.read())
    try:
        for i in range(70):
            try:status,res=call("initialize",{"protocolVersion":"2025-11-25"});break
            except URLError:time.sleep(.1)
        assert status==200 and res["result"]["serverInfo"]["version"]=="0.4.0"
        status,res=call("tools/list");names={t["name"]:t for t in res["result"]["tools"]}
        trio={"inspect_site_files","search_site_source","preview_site_patch"}
        assert trio<=set(names)
        assert all(names[n]["annotations"]["readOnlyHint"] is True for n in trio)
        assert all(names[n]["_meta"]["securitySchemes"][0]["scopes"]==["source:read"] for n in trio)
        status,unauthed=call("tools/call",{"name":"inspect_site_files","arguments":{}})
        assert unauthed["result"]["isError"] is True
        status,meta=call("tools/call",{"name":"inspect_site_files","arguments":{}},True)
        body=meta["result"]["structuredContent"]
        assert body["authenticated"] and body["changes_allowed"] is False
        digest=hashlib.sha256(original.encode()).hexdigest()
        assert next(f for f in body["files"] if f["filename"]=="category.php")["sha256"]==digest
        status,res=call("tools/call",{"name":"search_site_source","arguments":{"query":"Kraków"}},True)
        assert any(m["filename"]=="category.php" and m["line"]==3 for m in res["result"]["structuredContent"]["results"])
        status,res=call("tools/call",{"name":"preview_site_patch","arguments":{
            "filename":"category.php","expected_sha256":digest,"old_text":"Kraków","new_text":"Czyżyny"}},True)
        preview=res["result"]["structuredContent"]
        assert preview["applied"] is False and preview["preview_sha256"]!=digest
        assert (site/"category.php").read_text()==original
        status,res=call("tools/call",{"name":"preview_site_patch","arguments":{
            "filename":"category.php","expected_sha256":"0"*64,"old_text":"Kraków","new_text":"X"}},True)
        assert res["result"]["isError"] is True
        status,res=call("tools/call",{"name":"preview_site_patch","arguments":{
            "filename":"category.php","expected_sha256":digest,"old_text":"i","new_text":"X"}},True)
        assert res["result"]["isError"] is True
        status,res=call("tools/call",{"name":"read_source_file","arguments":{"filename":"../owner.json"}},True)
        assert res["result"]["isError"] is True
        print("PASS MCP v0.4: OAuth enforcement and tool discovery")
        print("PASS MCP v0.4: SHA256 inspection and safe source search")
        print("PASS MCP v0.4: preview never writes; stale SHA, ambiguous matches, private paths rejected")
    finally:
        process.terminate()
        try:process.wait(timeout=4)
        except subprocess.TimeoutExpired:process.kill();process.wait()
