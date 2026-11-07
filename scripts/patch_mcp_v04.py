#!/usr/bin/env python3
"""Fail closed when updating live RentMor MCP 0.3.3; never touch OAuth or customer data."""
import argparse
from pathlib import Path

def change(before: bytes) -> bytes:
    text = before.decode('utf-8')
    if "const RM_MCP_VERSION = '0.3.3';" not in text:
        raise ValueError("Unexpected live MCP revision. No change applied.")
    replacements = [
      ("require_once __DIR__ . '/oauth_core.php';",
       "require_once __DIR__ . '/oauth_core.php';\nrequire_once __DIR__ . '/mcp_tools_extra.php';"),
      ("const RM_MCP_VERSION = '0.3.3';","const RM_MCP_VERSION = '0.4.0';"),
      ("if ($method==='tools/list') return ['jsonrpc'=>'2.0','id'=>$id,'result'=>['tools'=>rm_tools()]];",
       "if ($method==='tools/list') return ['jsonrpc'=>'2.0','id'=>$id,'result'=>['tools'=>array_merge(rm_tools(),rm_ext_tools())]];"),
      ("    return rm_tool_error('Nieznane narzędzie.');",
       "    if (rm_ext_is_tool($name)) {\n"
       "        if (!rm_is_authorized()) return rm_source_unauthorized();\n"
       "        return rm_ext_call_tool($name,$arguments);\n"
       "    }\n"
       "    return rm_tool_error('Nieznane narzędzie.');"),
      ("        'poradnik-pranie-tapicerki-karcher-puzzi.php',",
       "        'poradnik-pranie-tapicerki-karcher-puzzi.php',\n"
       "        'poradnik-agregat-malarski-jak-malowac-dobor-dysz.php',\n"
       "        'sitemap.php',\n"
       "        'public_metadata.php',"),
    ]
    for old,new in replacements:
        if text.count(old)!=1:
            raise ValueError(f"Ambiguous/changed live MCP source: {old[:64]} (matches {text.count(old)})")
        text=text.replace(old,new,1)
    text=text.replace("'mode'=>'public-and-oauth-source-read-only'",
                      "'mode'=>'oauth-source-read-and-preview-only'",1)
    return text.encode('utf-8')

if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('source',type=Path)
    p.add_argument('destination',type=Path)
    args=p.parse_args()
    args.destination.write_bytes(change(args.source.read_bytes()))
    print("Prepared v0.4 MCP from verified v0.3.3, no change to OAuth or private state.")
