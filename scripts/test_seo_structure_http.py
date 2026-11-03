"""Exercise category HTTP rendering and sitemap using a temporary public-only fixture."""
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
from urllib.request import urlopen
from urllib.error import HTTPError
import xml.etree.ElementTree as ET
from verify_public_seo import Page
ROOT = Path(__file__).resolve().parents[1]
PHP = os.environ.get('RENTMOR_TEST_PHP', 'php')
with tempfile.TemporaryDirectory() as tmp:
    tmp = Path(tmp)
    for name in ('rental_categories.php','related_offers.php','category.php','category_sitemap.php','sitemap.php'):
        shutil.copy(ROOT/'server'/name,tmp/name)
    products = [{'name':'Młot testowy','cat':'budowlane','price':60}, {'name':'Glebogryzarka','category':'ogrodnicze','price':100}, {'name':'Puzzi <test>','cat':'czyszczace','price':50}]
    (tmp/'data.json').write_text(json.dumps(products))
    (tmp/'router.php').write_text("<?php if (str_starts_with($_SERVER['REQUEST_URI'], '/sitemap.xml')) require __DIR__.'/category_sitemap.php'; else require __DIR__.'/category.php';")
    sock = socket.socket(); sock.bind(('127.0.0.1',0)); port=sock.getsockname()[1]; sock.close()
    proc = subprocess.Popen([PHP,'-S',f'127.0.0.1:{port}',str(tmp/'router.php')],cwd=tmp,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    def fetch(path): return urlopen(f'http://127.0.0.1:{port}'+path,timeout=5)
    try:
        for attempt in range(30):
            try:
                with fetch('/sprzet-budowlany/') as response: response.read()
                break
            except OSError: time.sleep(.1)
        for slug in ('sprzet-budowlany','sprzet-ogrodniczy','sprzet-czyszczacy'):
            with fetch('/'+slug+'/') as response:
                html=response.read().decode(); assert not response.headers.get('Set-Cookie')
            page=Page(html)
            assert page.h1==1 and page.canonicals==['https://rentmor.pl/'+slug+'/']
            collection=next(s for s in page.schemas if s.get('@type')=='CollectionPage')
            assert collection['mainEntity']['numberOfItems']==1
            assert '<test>' not in html and 'cookie-consent.js' in html
            if slug=='sprzet-budowlany':
                assert 'Wypożyczalnia sprzętu budowlanego Kraków' in html
                assert 'szlifierkę do betonu' in html
            assert 'Wynajem sprzętu dla osób prywatnych i firm' in html
            assert 'Czy sprzęt można odebrać tego samego dnia?' in html
        with fetch('/sitemap.xml') as response: xml=response.read()
        urls=[node.text for node in ET.fromstring(xml).findall('{*}url/{*}loc')]
        assert len(urls)==7 and len(set(urls))==7
        for path in ('/category.php?category=sprzet-budowlany','/not-a-category/'):
            try: fetch(path)
            except HTTPError as error: assert error.code==404
            else: raise AssertionError('Unknown category must be 404')
        products.append({'name':'Nowa maszyna','cat':'budowlane','price':80})
        (tmp/'data.json').write_text(json.dumps(products))
        with fetch('/sprzet-budowlany/') as response: assert 'Nowa maszyna' in response.read().decode()
        (tmp/'data.json').write_text('broken')
        try: fetch('/sprzet-budowlany/')
        except HTTPError as error: assert error.code==503
        else: raise AssertionError('Corrupt catalogue must not produce an indexable empty page')
    finally:
        proc.terminate(); proc.wait()
print('PASS: category HTTP, escaping, live catalogue updates, sitemap, 404 and 503')
