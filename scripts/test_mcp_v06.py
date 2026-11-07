#!/usr/bin/env python3
"""End-to-end RentMor Control 0.6.0 synthetic owner approval, isolated tests + write security tests."""
from __future__ import annotations
import base64,hashlib,json,os,re,shutil,socket,subprocess,sys,tempfile,time
from pathlib import Path
from html.parser import HTMLParser
from urllib.parse import urlencode
from urllib.request import Request,urlopen
from urllib.error import HTTPError,URLError

class Inputs(HTMLParser):
    def __init__(self):super().__init__();self.values={}
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if tag=='input' and a.get('type')=='hidden':self.values[a['name']]=a.get('value','')

def main():
    if len(sys.argv)!=8:
        raise SystemExit('test_mcp_v06.py mcp.php oauth.php oauth_core.php mcp_tools_extra.php mcp_write_tools.php approve-change.php mcp_test_runner.php')
    mcp,oauth,core,extra,write,approval,runner=map(Path,sys.argv[1:])
    with tempfile.TemporaryDirectory(prefix='rm-mcp-v05-') as d:
        root=Path(d)
        site=root/'public_html';public=site/'rentmor-control-mcp';private=root/'rentmor-control-private'
        public.mkdir(parents=True);private.mkdir()
        for src,dst in [(mcp,'mcp.php'),(oauth,'oauth.php'),(core,'oauth_core.php'),
                        (extra,'mcp_tools_extra.php'),(write,'mcp_write_tools.php'),
                        (approval,'approve-change.php'),(runner,'mcp_test_runner.php')]:
            shutil.copyfile(src,public/dst)
        # index includes mcp.php and executes the live endpoint handler.
        (public/'index.php').write_text('<?php require __DIR__."/mcp.php"; rm_handle_request();')
        original='<?php\n// Public source test\n$city="Krakow";\n'
        (site/'category.php').write_text(original)
        (site/'robots.txt').write_text('User-agent: *\nAllow: /\n')
        password='SyntheticOwnerPasswordForLocalTest123!'
        ownerhash=subprocess.check_output(['php','-r','echo password_hash($argv[1], PASSWORD_DEFAULT);',password],text=True)
        (private/'owner.json').write_text(json.dumps({'password_hash':ownerhash}))
        readtok='syntheticreadtok1234567890ABCDEFGHIJKLMNOP'
        writetok='syntheticwritetok1234567890ABCDEFGHIJKLMNOP'
        with socket.socket() as s:s.bind(('127.0.0.1',0));port=s.getsockname()[1]
        base=f'http://127.0.0.1:{port}/rentmor-control-mcp'
        resource=base+'/index.php'
        state={'access':{hashlib.sha256(readtok.encode()).hexdigest():{'scope':'source:read','client':'tester',
                 'resource':resource,'expires':int(time.time()+3600)},
                         hashlib.sha256(writetok.encode()).hexdigest():{'scope':'source:read source:write',
                  'client':'tester','resource':resource,'expires':int(time.time()+3600)}}}
        (private/'state.json').write_text(json.dumps(state))
        env=os.environ|{'RM_MCP_TEST_BASE_URL':base}
        proc=subprocess.Popen(['php','-S',f'127.0.0.1:{port}','-t',str(site)],
            stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,env=env)
        seq=0
        def request(url,payload=None,headers=None):
            if isinstance(payload,dict): payload=urlencode(payload).encode()
            req=Request(url,data=payload,headers=headers or {},method='POST' if payload is not None else 'GET')
            try:r=urlopen(req,timeout=10)
            except HTTPError as error:r=error
            return r.status,dict(r.headers),r.read().decode()
        def rpc(method,params=None,token=None):
            nonlocal seq
            seq+=1
            headers={'Content-Type':'application/json'}
            if token:headers['Authorization']='Bearer '+token
            data=json.dumps({'jsonrpc':'2.0','id':seq,'method':method,'params':params or {}}).encode()
            status,headers,body=request(resource,data,headers)
            return status,json.loads(body)
        def tool(name,args=None,token=writetok):
            status,body=rpc('tools/call',{'name':name,'arguments':args or {}},token)
            assert status==200,(name,status,body)
            return body['result']
        def approved(change_id):
            url=base+'/approve-change.php?id='+change_id
            status,_,body=request(url)
            assert status==200 and 'Zatwierdź zmianę RentMor' in body,(status,body)
            form=Inputs();form.feed(body)
            assert 'proof' in form.values
            data=form.values|{'password':password,'agree':'on'}
            status,_,body=request(base+'/approve-change.php',data)
            assert status==200 and 'Zatwierdzenie przyjęte' in body,(status,body)
        try:
            for _ in range(50):
                try:code,_,_=request(base+'/oauth.php?op=metadata');break
                except URLError:time.sleep(.1)
            assert code==200
            _,_,meta=request(base+'/oauth.php?op=metadata')
            assert 'source:write' in json.loads(meta)['scopes_supported']
            # Generate a valid authorization request for a registered ChatGPT public client.
            registered_client='testclient_abcdefghijklmnopqrstuvwxyz'
            state=json.loads((private/'state.json').read_text())
            state['clients']={registered_client:{'redirect_uris':['https://chatgpt.com/connector_platform_oauth_redirect'],
                    'expires':int(time.time()+3600)}}
            (private/'state.json').write_text(json.dumps(state))
            proof=base64.urlsafe_b64encode(hashlib.sha256(b'A'*43).digest()).rstrip(b'=').decode()
            params={'response_type':'code','client_id':registered_client,
                    'redirect_uri':'https://chatgpt.com/connector_platform_oauth_redirect',
                    'code_challenge':proof,'code_challenge_method':'S256',
                    'scope':'source:read source:write','resource':resource,'state':'SYNTHETIC_STATE_12345'}
            status,_,consent=request(base+'/oauth.php?op=authorize&'+urlencode(params))
            assert status==200 and 'Zatwierdź dostęp do zapisu' in consent,(status,consent[:250])
            status,info=rpc('initialize',{'protocolVersion':'2025-11-25'})
            assert info['result']['serverInfo']['version']=='0.6.0'
            status,listed=rpc('tools/list')
            names={x['name']:x for x in listed['result']['tools']}
            assert {'stage_site_change','apply_approved_site_change','list_site_changes','stage_site_rollback'}<=set(names)
            assert names['apply_approved_site_change']['annotations']['destructiveHint'] is True
            new_tools={'run_site_tests','test_staged_site_change','get_site_test_report'}
            assert new_tools<=set(names), 'Safe test tools not published'
            assert names['run_site_tests']['annotations']['readOnlyHint'] is True
            assert names['run_site_tests']['_meta']['securitySchemes'][0]['scopes']==['source:read']
            assert names['test_staged_site_change']['_meta']['securitySchemes'][0]['scopes']==['source:write']
            unauth=tool('run_site_tests',{'suite':'runtime'},token=None)
            assert unauth['isError'] is True
            runtime=tool('run_site_tests',{'suite':'runtime'},readtok)['structuredContent']
            assert runtime['independent_of_github'] is True and runtime['no_production_code_executed'] is True
            assert runtime['summary']['failed']==0, runtime
            source=tool('run_site_tests',{'suite':'source','filename':'category.php'},readtok)['structuredContent']
            assert source['summary']['passed']==2 and source['summary']['failed']==0, source
            fixture=tool('run_site_tests',{'suite':'sandbox'},readtok)['structuredContent']
            assert fixture['summary']['passed']==2 and fixture['summary']['failed']==0, fixture
            security=tool('run_site_tests',{'suite':'security'},readtok)['structuredContent']
            assert security['summary']['failed']==0, security
            report=tool('get_site_test_report',{'report_id':source['report_id']},readtok)['structuredContent']
            assert report['report_id']==source['report_id'] and report['summary']==source['summary']
            assert (site/'category.php').read_text()==original, 'Tests must not modify app source'
            # Existing read credentials cannot stage or apply changes.
            denied=tool('stage_site_change',{'action':'create','filename':'rentmor-ai-test.txt','new_text':'TEST'},readtok)
            assert denied['isError'] is True and 'source:write' in str(denied)
            denied=tool('stage_site_change',{'action':'delete','filename':'oauth.php'},writetok)
            assert denied['isError'] is True
            sha=hashlib.sha256(original.encode()).hexdigest()
            resp=tool('stage_site_change',{'action':'replace','filename':'category.php',
                  'expected_sha256':sha,'old_text':'Krakow','new_text':'Czyzyny'})
            assert resp['isError'] is False,resp
            stage=resp['structuredContent']
            ident=stage['change_id']
            staged_denied=tool('test_staged_site_change',{'change_id':ident},readtok)
            assert staged_denied['isError'] is True and 'source:write' in str(staged_denied)
            staged=tool('test_staged_site_change',{'change_id':ident},writetok)['structuredContent']
            assert staged['summary']['failed']==0 and staged['overall']=='passed',staged
            assert staged['independent_of_github'] is True
            assert (site/'category.php').read_text()==original, 'Staged tests cannot publish'
            assert stage['published'] is False and stage['approval_url'].endswith(ident)
            assert (site/'category.php').read_text()==original
            denied=tool('apply_approved_site_change',{'change_id':ident})
            assert denied['isError'] is True, 'Must not publish without owner approval'
            approved(ident)
            res=tool('apply_approved_site_change',{'change_id':ident})
            assert res['structuredContent']['applied'] is True,res
            assert 'Czyzyny' in (site/'category.php').read_text()
            denied=tool('apply_approved_site_change',{'change_id':ident})
            assert denied['isError'] is True, 'Do not publish the same stage twice'
            # Test rollback, which also requires approval; check SHA and audit history.
            undo=tool('stage_site_rollback',{'change_id':ident})
            assert undo['isError'] is False,undo
            undo_id=undo['structuredContent']['change_id']
            denied=tool('apply_approved_site_change',{'change_id':undo_id})
            assert denied['isError'] is True
            approved(undo_id)
            out=tool('apply_approved_site_change',{'change_id':undo_id})
            assert out['structuredContent']['applied'] is True
            assert (site/'category.php').read_text()==original
            history=tool('list_site_changes')['structuredContent']['changes']
            assert len(history)==2 and all('password' not in str(x) for x in history)
            assert not ('owner.json' in str(history) or 'state.json' in str(history))
            # Create then delete an isolated static file with independent approvals.
            created=tool('stage_site_change',{'action':'create','filename':'rentmor-ai-test.txt','new_text':'Synthetic content'})
            approved(created['structuredContent']['change_id'])
            tool('apply_approved_site_change',{'change_id':created['structuredContent']['change_id']})
            assert (site/'rentmor-ai-test.txt').read_text()=='Synthetic content'
            targetsha=hashlib.sha256(b'Synthetic content').hexdigest()
            deleted=tool('stage_site_change',{'action':'delete','filename':'rentmor-ai-test.txt','expected_sha256':targetsha})
            approved(deleted['structuredContent']['change_id'])
            tool('apply_approved_site_change',{'change_id':deleted['structuredContent']['change_id']})
            assert not (site/'rentmor-ai-test.txt').exists()
            # Wrong password does not approve.
            newstage=tool('stage_site_change',{'action':'create','filename':'rentmor-ai-denied.txt','new_text':'No publish'})
            cid=newstage['structuredContent']['change_id']
            _,_,form=request(base+'/approve-change.php?id='+cid)
            hp=Inputs();hp.feed(form)
            code,_,body=request(base+'/approve-change.php',hp.values|{'password':'bad-password','agree':'on'})
            assert code==403
            denied=tool('apply_approved_site_change',{'change_id':cid})
            assert denied['isError'] and not (site/'rentmor-ai-denied.txt').exists()
            # Protect against stale SHA after stage approval.
            res=tool('stage_site_change',{'action':'replace','filename':'category.php',
                'expected_sha256':sha,'old_text':'Krakow','new_text':'Remote'})
            cid=res['structuredContent']['change_id']
            approved(cid)
            (site/'category.php').write_text('Modified by another deploy')
            denied=tool('apply_approved_site_change',{'change_id':cid})
            assert denied['isError'] is True and (site/'category.php').read_text()=='Modified by another deploy'
            print('PASS: independent fixed suites: runtime, source, security and isolated fixture')
            print('PASS: tests have redacted stored reports and never modify source')
            print('PASS: staged candidate syntax/SHA test uses write OAuth but cannot publish')
            print('PASS: OAuth metadata + owner consent exposes explicit source:write scope')
            print('PASS: source:read cannot write; dangerous/private files denied')
            print('PASS: stage does not publish; manual owner password approval is required')
            print('PASS: SHA-pinned apply, verified backup, history and separately approved rollback')
            print('PASS: create/delete isolated files, wrong password and stale SHA are blocked')
        finally:
            proc.terminate()
            try:proc.wait(timeout=5)
            except subprocess.TimeoutExpired:proc.kill();proc.wait()

if __name__=='__main__':main()
