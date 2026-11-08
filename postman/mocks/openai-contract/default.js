// AUTOMATED TEST FIXTURE ONLY. Never use this as a live AI provider.
const http = require('http');
const citationCorrections = new Set();
http.createServer((req,res)=>{
 const send=(status,body)=>{res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(body));};
 const body=req.body||{};
 if(req.method!=='POST')return send(501,{error:{message:'Endpoint not modeled'}});
 if(req.url==='/v1/embeddings'){
  if(!Array.isArray(body.input)||body.model!=='gemini-embedding-001')return send(400,{error:{message:'Invalid Gemini embedding request'}});
  // Constant vectors exercise Qdrant association filters, NOT semantic quality.
  // Live Gemini omits index; preserve input order to reproduce the observed envelope.
  return send(200,{object:'list',model:body.model,data:body.input.map(()=>({object:'embedding',embedding:[3,4,...Array(3070).fill(0)]}))});
 }
 if(req.url==='/v1/chat/completions'){
  if(!Array.isArray(body.messages)||body.response_format?.json_schema?.strict!==true)return send(400,{error:{message:'Structured schema required'}});
  if(body.model!=='gemini-3.1-flash-lite'||body.max_tokens!==4096||body.reasoning_effort!=='minimal')return send(400,{error:{message:'Expected configured Gemini chat parameters'}});
  const user=body.messages.at(-1).content;
  if(user.includes('SIMULATE_RATE_LIMIT'))return send(429,{error:{message:'Simulated rate limit',type:'rate_limit_error'}});
  let content;
  if(body.response_format.json_schema.name==='workspace_query_plan'){
   const catalog=JSON.parse(user.split('Catalog JSON: ')[1]);
   const students=catalog.find(t=>t.columns.some(c=>c.name==='name'));
   const fees=catalog.find(t=>t.columns.some(c=>c.name==='fees'));
   if(!fees){const t=catalog[0];content={target:'PERSONAL',tables:[t.id],joins:[],filters:[{tableId:t.id,column:'marks',operator:'GT',value:'80'}],orderBy:{tableId:t.id,column:'marks',descending:true},limit:50,explanation:'Test-only single-table connected plan.'};}else{
   content={target:'PERSONAL',tables:[students.id,fees.id],joins:[{leftTableId:students.id,leftColumn:'id',rightTableId:fees.id,rightColumn:'student_id'}],filters:[{tableId:fees.id,column:'fees',operator:'GT',value:'150000'}],orderBy:null,limit:50,explanation:'Test-only fixture joining student IDs to fee student_id values.'};
   }
   if(user.includes('SIMULATE_FOREIGN_TABLE'))content.tables=['00000000-0000-0000-0000-000000000001'];
  }else if(body.response_format.json_schema.name==='personal_table_plan'){
   content={filters:[{column:'marks',operator:'GT',value:'80'}],limit:50,orderBy:'marks',descending:true,unsupported:false,explanation:'Automated test fixture, not live research.'};
  }else if(body.response_format.json_schema.name==='query_plan'){
   content={mode:user.includes('Requested mode: SQL')?'SQL':'HYBRID',intent:'PURCHASES',category:'laptop',minPrice:50000,customerId:null,productId:null,limit:50,explanation:'Contract-test plan fixture. Not a live AI interpretation.'};
  }else{
   const evidence=JSON.parse(user),citations=evidence.sources.map(s=>s.id);
   content={answer:`Automated contract-test answer: ${evidence.rows.length} database records. ${citations.map(c=>'['+c+']').join(' ')}`,citations};
  }
  if(user.includes('SIMULATE_BAD_CITATION'))content={answer:'Unsupported claim [D999]',citations:['D999']};
  if(user.includes('SIMULATE_CITATION_CORRECTION')&&!citationCorrections.has(user)){
   citationCorrections.add(user);
   content={answer:'Fixture with missing inline reference.',citations:['D1']};
  }
  return send(200,{id:'contract-fixture',object:'chat.completion',model:body.model,choices:[{index:0,finish_reason:'stop',message:{role:'assistant',content:JSON.stringify(content),refusal:null}}]});
 }
 send(501,{error:{message:'Endpoint not modeled'}});
}).listen(process.env.PORT);
