#!/usr/bin/env python3
"""Add fixed-suite, GitHub-independent MCP tests to known RentMor Control v0.5."""
from pathlib import Path
import argparse

def replace_once(text, old, new):
    count=text.count(old)
    if count!=1:
        raise ValueError(f'Unknown live MCP source (matches={count}) for: {old[:70]}')
    return text.replace(old,new,1)

def patch(original: bytes) -> bytes:
    text=original.decode('utf-8')
    text=replace_once(text,
        "require_once __DIR__ . '/mcp_write_tools.php';",
        "require_once __DIR__ . '/mcp_write_tools.php';\nrequire_once __DIR__ . '/mcp_test_runner.php';")
    text=replace_once(text,
        "const RM_MCP_VERSION = '0.5.0';",
        "const RM_MCP_VERSION = '0.6.0';")
    text=replace_once(text,
        "'mode'=>'oauth-source-read-and-owner-approved-write'",
        "'mode'=>'oauth-source-read-owner-approved-write-and-independent-tests'")
    text=replace_once(text,
        "if ($method==='tools/list') return ['jsonrpc'=>'2.0','id'=>$id,'result'=>['tools'=>array_merge(rm_tools(),rm_ext_tools(),rm_write_tools())]];",
        "if ($method==='tools/list') return ['jsonrpc'=>'2.0','id'=>$id,'result'=>['tools'=>array_merge(rm_tools(),rm_ext_tools(),rm_write_tools(),rm_test_tools())]];")
    text=replace_once(text,
        "    if (rm_write_is_tool($name)) {",
        "    if (rm_test_is_tool($name)) {\n"
        "        $scope=($name==='test_staged_site_change')?RM_OAUTH_SCOPE_WRITE:RM_OAUTH_SCOPE;\n"
        "        if (!rm_is_authorized($scope)) {\n"
        "            return ['content'=>[['type'=>'text','text'=>'Wymagana autoryzacja OAuth z zakresem '.$scope.'.']],\n"
        "                '_meta'=>['mcp/www_authenticate'=>['Bearer resource_metadata=\"'.rm_oauth_metadata_url().'\", scope=\"'.$scope.'\", error=\"insufficient_scope\"']],\n"
        "                'isError'=>true];\n"
        "        }\n"
        "        return rm_test_call($name,$arguments);\n"
        "    }\n"
        "    if (rm_write_is_tool($name)) {")
    # Amend only the informational note; OAuth permission logic is unchanged.
    old="'notes'=>'Odczyt dozwolonego kodu wymaga OAuth source:read. Zapis wybranych jawnych plików wymaga scope source:write oraz osobnej zgody właściciela hasłem na każdą zmianę. Brak dostępu do danych klientów, sekretów, administratora i kodu OAuth.',"
    new="'notes'=>'Odczyt i bezpieczne zestawy testów bez GitHuba wymagają OAuth source:read. Zapis wybranych plików wymaga source:write i osobnej zgody właściciela na każdą publikację. Testy nie uruchamiają dowolnego kodu ani nie korzystają z danych klientów.',"
    if old in text:text=replace_once(text,old,new)
    return text.encode('utf-8')

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('source',type=Path)
    parser.add_argument('destination',type=Path)
    args=parser.parse_args()
    args.destination.write_bytes(patch(args.source.read_bytes()))
    print('Prepared RentMor Control 0.6.0 fixed-suite test tools; OAuth and write logic unchanged.')
