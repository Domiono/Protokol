#!/usr/bin/env python3
"""Upgrade only recognizable MCP 0.4.0 + OAuth 0.3.3 to owner-approved writes."""
from pathlib import Path
import sys

def swap(text, before, after, label):
    count=text.count(before)
    if count!=1:
        raise ValueError(f"Unknown live code at {label} (matches={count}). Refusing changes.")
    return text.replace(before,after,1)

def upgrade_core(text):
    text=swap(text,"const RM_OAUTH_SCOPE='source:read';",
        "const RM_OAUTH_SCOPE='source:read';\nconst RM_OAUTH_SCOPE_WRITE='source:write';","core write scope")
    text=swap(text,"$scope!==RM_OAUTH_SCOPE) return null;",
        "!in_array($scope,[RM_OAUTH_SCOPE,RM_OAUTH_SCOPE_WRITE,RM_OAUTH_SCOPE.' '.RM_OAUTH_SCOPE_WRITE],true)) return null;",
        "OAuth exact scope validation")
    text=swap(text,"($entry['scope']??null)===$scope;",
        "in_array($scope,explode(' ',(string)($entry['scope']??'')),true);",
        "token granted scope validation")
    return text

def upgrade_oauth(text):
    # Metadata and protected resource both publish available granular scopes.
    original="'scopes_supported'=>[RM_OAUTH_SCOPE]"
    if text.count(original)!=2:
        raise ValueError("Unexpected OAuth metadata structure")
    text=text.replace(original,"'scopes_supported'=>[RM_OAUTH_SCOPE,RM_OAUTH_SCOPE_WRITE]")
    text=swap(text,
        "        rm_html('Połącz RentMor Control','<p>Udostępnienie uprawnienia <b>odczyt wskazanych plików kodu</b> aplikacji ChatGPT.</p>'.\n"
        "            '<p><small>Brak prawa zapisu, publikacji ani dostępu do danych klientów.</small></p>'.",
        "        $writeAccess=in_array(RM_OAUTH_SCOPE_WRITE,explode(' ',$p['scope']),true);\n"
        "        $accessDescription=$writeAccess ? "
        "'<b>PRZYGOTOWANIE I PUBLIKACJA ZATWIERDZONYCH ZMIAN</b> w wybranych plikach kodu RentMor.pl. Każdą zmianę przed zapisem musi zatwierdzić właściciel hasłem.' : "
        "'<b>odczyt wskazanych plików kodu</b>.';\n"
        "        rm_html('Połącz RentMor Control','<p>Wymagany zakres: '.$accessDescription.'</p>'.\n"
        "            '<p><small>Brak dostępu do haseł, danych klientów, OAuth i administratora. Cofanie zmian wymaga oddzielnej zgody.</small></p>'.",
        "OAuth consent details")
    text=swap(text,"'<button type=\"submit\">Zatwierdź dostęp do odczytu</button></form>');",
        "'<button type=\"submit\">'.($writeAccess?'Zatwierdź dostęp do zapisu':'Zatwierdź dostęp do odczytu').'</button></form>');",
        "OAuth write permission button")
    return text

def upgrade_mcp(text):
    text=swap(text,"require_once __DIR__ . '/mcp_tools_extra.php';",
        "require_once __DIR__ . '/mcp_tools_extra.php';\nrequire_once __DIR__ . '/mcp_write_tools.php';",
        "MCP extra write module")
    text=swap(text,"const RM_MCP_VERSION = '0.4.0';","const RM_MCP_VERSION = '0.5.0';","MCP release")
    text=swap(text,"'mode'=>'oauth-source-read-and-preview-only'",
        "'mode'=>'oauth-source-read-and-owner-approved-write'","MCP feature mode")
    text=swap(text,
        "    return rm_tool_error('Nieznane narzędzie.');\n}",
        "    if (rm_write_is_tool($name)) {\n"
        "        if (!rm_is_authorized(RM_OAUTH_SCOPE_WRITE)) {\n"
        "            return ['content'=>[['type'=>'text','text'=>'Wymagana nowa autoryzacja OAuth z zakresem source:write.']],\n"
        "                '_meta'=>['mcp/www_authenticate'=>['Bearer resource_metadata=\"'.rm_oauth_metadata_url().'\", scope=\"source:write\", error=\"insufficient_scope\"']],\n"
        "                'isError'=>true];\n"
        "        }\n"
        "        return rm_write_call($name,$arguments);\n"
        "    }\n"
        "    return rm_tool_error('Nieznane narzędzie.');\n}",
        "MCP protected write route")
    text=swap(text,
        "if ($method==='tools/list') return ['jsonrpc'=>'2.0','id'=>$id,'result'=>['tools'=>array_merge(rm_tools(),rm_ext_tools())]];",
        "if ($method==='tools/list') return ['jsonrpc'=>'2.0','id'=>$id,'result'=>['tools'=>array_merge(rm_tools(),rm_ext_tools(),rm_write_tools())]];",
        "MCP tool registry")
    text=swap(text,
        "'instructions'=>'Publiczne pliki statyczne bez logowania; ograniczony odczyt i bezpieczna analiza kodu przez OAuth. Podgląd zmian bez zapisu. Brak dostępu do danych klientów, sekretów i publikacji.',",
        "'instructions'=>'Publiczne pliki bez logowania; odczyt wymaga OAuth source:read. Edycja wybranych plików wymaga OAuth source:write i osobnej zgody właściciela hasłem na każdą zmianę. Zapis zawsze porównuje SHA256 i tworzy kopię. Brak dostępu do sekretów, OAuth, admina i danych klientów.',",
        "MCP client instructions")
    return text

if __name__=='__main__':
    if len(sys.argv)!=7:
        raise SystemExit('usage: patch_mcp_v05.py mcp.in oauth.in core.in mcp.out oauth.out core.out')
    src=list(map(Path,sys.argv[1:4]))
    dst=list(map(Path,sys.argv[4:7]))
    functions=[upgrade_mcp,upgrade_oauth,upgrade_core]
    for source,output,fn in zip(src,dst,functions):
        transformed=fn(source.read_text())
        output.write_text(transformed)
        print(f"Prepared guarded 0.5.0: {source.name}; source syntax markers verified.")
