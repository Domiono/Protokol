import json,sys
p=json.load(open(sys.argv[1]));a=p['audits']
print('SPEED_RESULT',json.dumps({'file':sys.argv[1],'score':p['categories']['performance']['score']*100,**{k:a[k].get('numericValue') for k in ['first-contentful-paint','largest-contentful-paint','speed-index','total-blocking-time','cumulative-layout-shift']}}))
for k in ['largest-contentful-paint-element','render-blocking-resources','unused-css-rules','uses-responsive-images','font-display','lcp-breakdown-insight','lcp-discovery-insight']:
 print(k,json.dumps(a.get(k,{}).get('details',{}))[:6000])
n=a['network-requests']['details']['items']
print('TRANSFER',sum(x.get('transferSize',0) for x in n))
for x in sorted(n,key=lambda x:x.get('transferSize',0),reverse=True)[:12]:print('RESOURCE',x.get('transferSize'),x.get('url'))
