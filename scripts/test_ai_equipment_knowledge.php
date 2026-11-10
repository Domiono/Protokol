<?php
declare(strict_types=1);
require_once __DIR__.'/../server/ai_app_knowledge.php';
$dir=sys_get_temp_dir().'/rm-equip-'.bin2hex(random_bytes(6));mkdir($dir,0700);$n=0;
function eqcheck(bool $b,string $s):void{global$n;if(!$b)throw new RuntimeException($s);$n++;}
function eqclean(string $d):void{foreach(scandir($d)as$f){if($f==='.'||$f==='..')continue;$p=$d.'/'.$f;if(is_dir($p)&&!is_link($p))eqclean($p);else unlink($p);}rmdir($d);}
$catalogue=['products'=>[['name'=>'Agregat WT20i','description'=>'Malowanie farbą'],['name'=>'Puzzi 10/1','description'=>'Pranie tapicerki']]];
function eqrequest(string $d,array $cat,string $aud,string $equipment,string $method='GET',string $text='',string $rev='',string $role='admin'):array{return rm_ai_app_knowledge_request($role,$method,'1',['audience'=>$aud,'equipment'=>$equipment],['audience'=>$aud,'equipment'=>$equipment,'text'=>$text,'revision'=>$rev],$d,$cat);}
try{
 rm_ai_knowledge_save($dir,'GLOBAL PRIVATE',hash('sha256',''));
 [$s,$b]=eqrequest($dir,$catalogue,'public','');eqcheck($s===200&&count($b['equipment_options'])===2,'Catalogue selector');
 [$s,$a]=eqrequest($dir,$catalogue,'public','Agregat WT20i');eqcheck($s===200&&$a['knowledge']['text']==='','Empty equipment note');
 [$s,$b]=eqrequest($dir,$catalogue,'public','Puzzi 10/1');$puzziRev=$b['knowledge']['revision'];
 [$s,$a]=eqrequest($dir,$catalogue,'public','Agregat WT20i','POST','Tylko farby wodne.',$a['knowledge']['revision']);eqcheck($s===200,'Equipment save');
 [$s,$b]=eqrequest($dir,$catalogue,'public','Puzzi 10/1','POST','Wypłukać zbiornik.',$puzziRev);eqcheck($s===200,'Independent equipment revision');
 eqcheck(eqrequest($dir,$catalogue,'public','Agregat WT20i')[1]['knowledge']['text']==='Tylko farby wodne.','Agregat preserved');
 eqcheck(eqrequest($dir,$catalogue,'public','')[1]['knowledge']['text']==='','Global remains separate');
 eqcheck(eqrequest($dir,$catalogue,'private','Agregat WT20i')[1]['knowledge']['text']==='','Private equipment isolated');
 eqcheck(rm_ai_knowledge_read($dir,false)['text']==='GLOBAL PRIVATE','Old private text preserved');
 eqcheck(eqrequest($dir,$catalogue,'public','Agregat WT20i','POST','STALE',hash('sha256',''))[0]===409,'Conflict per equipment');
 eqcheck(eqrequest($dir,$catalogue,'public','../other')[0]===400,'Unknown equipment rejected');
 eqcheck(eqrequest($dir,$catalogue,'public','Agregat WT20i','GET','','','assistant')[0]===403,'Employee denied');
 $note=rm_ai_public_knowledge_read($dir);$context=rm_ai_knowledge_context($catalogue,$note,'Agregat WT20i');
 eqcheck(str_contains($context,'Tylko farby wodne.')&&!str_contains($context,'Wypłukać zbiornik.')&&!str_contains($context,'GLOBAL PRIVATE'),'Relevant public notes only');
 $old=$catalogue;$old['products']=[];eqcheck(in_array('Agregat WT20i',eqrequest($dir,$old,'public','')[1]['equipment_options'],true),'Retain notes after catalogue rename or removal');
 [$s,$b]=eqrequest($dir,$catalogue,'public','Agregat WT20i','POST','',$a['knowledge']['revision']);eqcheck($s===200&&$b['knowledge']['text']==='','Clear just one equipment note');
 eqcheck(eqrequest($dir,$catalogue,'public','Puzzi 10/1')[1]['knowledge']['text']==='Wypłukać zbiornik.','Other equipment not cleared');
}finally{eqclean($dir);}
echo "Equipment knowledge: $n checks passed\n";
