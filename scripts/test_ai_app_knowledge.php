<?php
declare(strict_types=1);
require_once __DIR__.'/../server/ai_app_knowledge.php';
require_once __DIR__.'/../server/ai_chat_service.php';
$dir=sys_get_temp_dir().'/rm-knowledge-test-'.bin2hex(random_bytes(8));mkdir($dir,0700);$n=0;
function check(bool $ok,string $what):void{global $n;if(!$ok)throw new RuntimeException($what);$n++;}
function callk(string $dir,string $role='admin',string $method='GET',?array $input=null,string $audience='public',string $header='1'):array{return rm_ai_app_knowledge_request($role,$method,$header,['audience'=>$audience],$input,$dir);}
function clean(string $dir):void{foreach(scandir($dir)as$f){if($f==='.'||$f==='..')continue;$p=$dir.'/'.$f;if(is_dir($p)&&!is_link($p))clean($p);else unlink($p);}rmdir($dir);}
try{
 foreach(['assistant','','owner','ADMIN']as$role){foreach(['GET','POST']as$method){[$s,$b]=callk('/invalid',$role,$method,['text'=>'x','revision'=>str_repeat('0',64),'audience'=>'public']);check($s===403&&!isset($b['knowledge']),'Denied role');}}
 check(scandir($dir)===['.','..'],'Denied roles never touch storage');
 [$s,$b]=callk($dir);check($s===200&&$b['knowledge']['text']==='','Empty public knowledge');$empty=$b['knowledge']['revision'];
 rm_ai_knowledge_save($dir,'PRIVATE FIXTURE ONLY',hash('sha256',''));
 [$s,$b]=callk($dir);check($b['knowledge']['text']==='','Private knowledge never becomes public');
 $draft=['audience'=>'public','text'=>"Zasada testowa: używaj wody.\nPolskie znaki: ąęść.",'revision'=>$empty];
 check(callk($dir,'admin','POST',$draft,'public','')[0]===403,'Write header required');
 [$s,$b]=callk($dir,'admin','POST',$draft);check($s===200&&$b['knowledge']['text']===$draft['text'],'Public write roundtrip');$rev=$b['knowledge']['revision'];
 check((fileperms($dir.'/public-knowledge')&0777)===0700,'Private public-knowledge directory');
 check((fileperms($dir.'/public-knowledge/knowledge.json')&0777)===0600,'Private knowledge file');
 check(rm_ai_knowledge_read($dir)['text']==='PRIVATE FIXTURE ONLY','Private note preserved');
 check(callk($dir,'admin','POST',$draft)[0]===409,'Stale write conflict');
 check(rm_ai_public_knowledge_read($dir)['revision']===$rev,'Conflict preserves current note');
 $draft['revision']=$rev;$draft['text']='NOWA WIEDZA';[$s,$b]=callk($dir,'admin','POST',$draft);check($s===200,'Second save');
 $previous=json_decode(file_get_contents($dir.'/public-knowledge/knowledge.previous.json'),true);check(str_contains($previous['text'],'Polskie znaki'),'Previous copy');
 $draft['revision']=$b['knowledge']['revision'];
 foreach([str_repeat('ą',6001),"x\0y","bad\xff",'sk-'.str_repeat('a',40)]as$bad){$input=$draft;$input['text']=$bad;check(callk($dir,'admin','POST',$input)[0]===400,'Invalid text rejected');}
 check(callk($dir,'admin','GET',null,'../private')[0]===400,'Audience traversal rejected');
 check(callk($dir,'admin','DELETE')[0]===405,'Invalid method rejected');
 check(callk($dir,'admin','POST',['audience'=>'public','text'=>'x','revision'=>[]])[0]===400,'Malformed revision');
 $ctx=rm_ai_knowledge_context(['products'=>[]],rm_ai_public_knowledge_read($dir),'test');check(str_contains($ctx,'NOWA WIEDZA')&&!str_contains($ctx,'PRIVATE FIXTURE'),'Only published knowledge in public context');
 $draft['text']='';[$s,$b]=callk($dir,'admin','POST',$draft);check($s===200&&$b['knowledge']['text']==='','Clear published knowledge');
 $file=$dir.'/public-knowledge/knowledge.json';file_put_contents($file,'{broken');check(callk($dir)[0]===503,'Corrupt store fails closed');
 unlink($file);
 symlink($dir.'/knowledge.json',$file);check(callk($dir)[0]===503,'Symlink rejected');
}finally{clean($dir);}
echo "AI knowledge: $n checks passed\n";
