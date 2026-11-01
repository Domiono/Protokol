#!/usr/bin/env python3
"""Print non-sensitive GitHub Actions release statuses for two RentMor workflow files."""
import json, os
from urllib.request import Request, urlopen
repo=os.environ.get('GITHUB_REPOSITORY','Domiono/Protokol')
token=os.environ.get('GITHUB_TOKEN','')
for workflow in ('privacy-homepage-hotfix.yml','deploy-rentmor.yml'):
    url=f'https://api.github.com/repos/{repo}/actions/workflows/{workflow}/runs?per_page=15'
    h={'Accept':'application/vnd.github+json','User-Agent':'RentMorReleaseStatus'}
    if token:h['Authorization']='Bearer '+token
    try:
        with urlopen(Request(url,headers=h),timeout=25) as r: data=json.load(r)
        print('WORKFLOW '+workflow)
        for item in data.get('workflow_runs',[]):
            if item.get('event')!='push':continue
            print('RUN',item['id'],'status',item['status'],'conclusion',item.get('conclusion'),'SHA',str(item.get('head_sha',''))[:12], 'url',item.get('html_url',''))
    except Exception as exc:
        print('Release status unavailable for',workflow,type(exc).__name__)
