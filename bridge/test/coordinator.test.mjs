import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { mkdtemp, mkdir, rm, writeFile, readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { once } from 'node:events';
import { randomUUID, createHash } from 'node:crypto';
import test from 'node:test';
import { AcceptanceCoordinator, isFatalError } from '../src/coordinator.mjs';
import { resolveReport } from '../src/reports.mjs';
import { CheckpointLog, readCheckpoint } from '../src/checkpoint.mjs';
import { verifyOutcomes } from '../src/outcomes.mjs';
import { gzipSync } from 'node:zlib';
import { prepare, digest } from '../src/plan.mjs';
import { withNativeEvidence } from './fixtures/current-job.mjs';

test('acceptance preparation binds executable and web stage paths to the verified bundles', async t => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'nesql-prepare-paths-'));
  t.after(() => rm(root, {recursive:true, force:true}));
  const bytes = Buffer.from('verified local artifact');
  const sha256 = createHash('sha256').update(bytes).digest('hex');
  const binary = process.platform === 'win32' ? 'elysium-compiler.exe' : 'elysium-compiler';
  for (const [name, file] of [['mod','mod/NESQL++-fixture.jar'], ['compiler',binary], ['web','frontend/dist/index.html']]) {
    const bundle = path.join(root, name);
    await mkdir(path.dirname(path.join(bundle, file)), {recursive:true});
    await writeFile(path.join(bundle, file), bytes);
    await writeFile(path.join(bundle, 'files.json'), JSON.stringify([{path:file, bytes:bytes.length, sha256}]));
  }
  await mkdir(path.join(root, 'saves/test-world'), {recursive:true});
  await writeFile(path.join(root, 'saves/test-world/level.dat'), 'test');
  const worklist = path.join(root, 'worklist.json'), configFile = path.join(root, 'config.json');
  await writeFile(worklist, JSON.stringify({handlers:[{id:'category_'+'a'.repeat(64), classification:'recipe', route:'recipe:native'}]}));
  const config = {instance:root, world:'test-world', version:'fixture', revision:40, modSha256:sha256,
    mod:path.join(root,'mod'), compiler:path.join(root,'compiler'), web:path.join(root,'web'), worklist, reports:path.join(root,'reports')};
  await writeFile(configFile, JSON.stringify(config));
  const accepted = await prepare(configFile);
  for (const [stage, field] of [['compiler','executable'], ['web','entry']]) {
    const stages = structuredClone(accepted.plan.stages);
    const unverified = path.join(root, 'unverified-'+stage);
    await writeFile(unverified, bytes);
    stages[stage][field] = unverified;
    await writeFile(configFile, JSON.stringify({...config, stages}));
    await assert.rejects(() => prepare(configFile), /verified .*bundle/);
  }
  const added = {id:'category_'+'b'.repeat(64), classification:'interactive', route:'interactive:native'};
  const original = {id:'category_'+'a'.repeat(64), classification:'recipe', route:'recipe:native'};
  await writeFile(worklist, JSON.stringify({handlers:[original, added]}));
  const stages = structuredClone(accepted.plan.stages);
  stages.source.execute = false;
  for (const selection of [[original.id], [original.id, added.id, added.id], [], [original.id, 'category_'+'c'.repeat(64)]]) {
    stages.source.handlers = selection;
    await writeFile(configFile, JSON.stringify({...config, stages}));
    await assert.rejects(() => prepare(configFile), /Source selection|Duplicate source handler/,
      'A diagnosed gameplay identity may not silently disappear from the declared Source selection, even before export is enabled');
  }
  stages.source.handlers = [added.id, original.id];
  await writeFile(configFile, JSON.stringify({...config, stages}));
  assert.deepEqual((await prepare(configFile)).plan.stages.source.handlers, [added.id, original.id]);
  await writeFile(worklist, JSON.stringify({handlers:[original, {...added,route:'usage-query:fixture'}]}));
  await assert.rejects(() => prepare(configFile), /Invalid interactive route/,
    'Invalid native usage routing must be caught before any game connection');
});

function exportFixture() {
  const id = 'category_' + 'a'.repeat(64);
  const plan = { world: 'test-world', modSha256, handlers: [{ id, classification: 'recipe' }],
    stages: { source: { key: 'explicit-test', scope: 'recipes', handlers: [id] } } };
  const checkpoint = { createdAt: new Date().toISOString(), fingerprint: 'verified-environment', handlers: { [id]: {
    id, status: 'passed', total: 2, checked: 2, offset: 2, unexamined: 0,
    failed: [], failures: [], excluded: [1], hasFailures: false, failuresOmitted: 0
  } }, failures: [] };
  const runner = new AcceptanceCoordinator(path.join(os.tmpdir(), 'unused-export-gate'));
  runner.saveCheckpoint = async () => {};
  runner.guardResources = async () => {};
  runner.planFingerprint = async () => 'verified-environment';
  const requests = [];
  runner.client = { request: async (method, url, body) => {
    if (method === 'POST') { requests.push(body); return { id: 'export' }; }
    return { job: { state: 'succeeded', result: { path: 'source' } } };
  } };
  return { id, plan, checkpoint, runner, requests };
}

test('source stage keeps the exact explicit selection after complete diagnostics', async () => {
  const { id, plan, checkpoint, runner, requests } = exportFixture();
  const result = await withNativeEvidence(runner).runSourceStage(plan, checkpoint);
  assert.deepEqual(requests[0].handlers, [id]);
  assert.equal(requests[0].scope, 'recipes');
  assert.equal(result.status, 'passed');
  assert.deepEqual(checkpoint.handlers[id].excluded, [1], 'Native exclusions remain visible');
});

test('reference and interactive gameplay handlers require reference rows and enter source selection', async () => {
  const id = 'category_' + 'c'.repeat(64);
  const plan = { world: 'test-world', handlers: [{ id, classification: 'domain', route: 'domain:fixture' }],
    stages: { source: { key: 'reference-test', scope: 'recipes', handlers: [id] } } };
  const checkpoint = { createdAt: new Date().toISOString(), fingerprint: 'verified-environment', handlers: { [id]: { id, status: 'passed', total: 1, checked: 1, offset: 1, unexamined: 0, failed: [], failures: [], excluded: [], hasFailures: false, failuresOmitted: 0 } }, failures: [] };
  const runner = new AcceptanceCoordinator(path.join(os.tmpdir(), 'unused-reference-export'));
  runner.saveCheckpoint = async () => {}; runner.guardResources = async () => {}; runner.planFingerprint = async () => 'verified-environment';
  const requests = []; runner.client = { request: async (method, url, body) => { if (method === 'POST') { requests.push(body); return { id: 'reference-export' }; } return { job: { state: 'succeeded', result: { path: 'source' } } }; } };
  const result = await withNativeEvidence(runner).runSourceStage(plan, checkpoint);
  assert.equal(result.status, 'passed'); assert.deepEqual(requests[0].handlers, [id]);
});

test('reference handler checks reject a recipe record kind', async t => {
  const id = 'category_' + 'd'.repeat(64);
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-reference-kind-'));
  t.after(() => rm(instance, { recursive: true, force: true })); await mkdir(path.join(instance, 'nesql'));
  const raw = Buffer.from(JSON.stringify({ rows: [{ handler: id, status: 'passed', recordKind: 'recipes', totalRecipes: 1, offset: 0, end: 1, checkedRecipes: 1, unexamined: 0, failedRecipes: [], excludedRecipes: [], failures: [], failuresOmitted: 0 }] }));
  const reportPath = path.join(instance, 'nesql', 'reference-kind.json'); await writeFile(reportPath, raw);
  const runner = new AcceptanceCoordinator(instance); runner.client = { session: 'reference-kind', request: async (method, url) => {
    if (url === '/game') return gameState(instance, { ready: true, world: { folder: 'test-world' }, exporter: '0.15.0', revision: 14 });
    if (url === '/checks') return { id: 'reference-kind-job' };
    if (url === '/jobs/reference-kind-job') return { job: { id: 'reference-kind-job', state: 'checked', report: { path: reportPath, bytes: raw.length, sha256: digestForTest(raw) } } };
    throw new Error(`Unexpected request ${method} ${url}`);
  } };
  const checkpoint = await withNativeEvidence(runner).runPlan({ world: 'test-world', modSha256, handlers: [{ id, classification: 'domain', route: 'domain:fixture' }] });
  assert.equal(checkpoint.handlers[id].status, 'failed'); assert.equal(checkpoint.handlers[id].error.code, 'record_kind_mismatch');
});

test('a reference open failure retains its native error before record-kind and total checks',async t=>{
  const id='category_'+'e'.repeat(64),instance=await fixture(path.join(os.tmpdir(),'nesql-reference-open-'));
  t.after(()=>rm(instance,{recursive:true,force:true}));await mkdir(path.join(instance,'nesql'));
  const error={type:'NativeAdmissionFault',code:'reference_unsupported',message:'unadapted actual provider',fatal:false,stack:['native.reader.open'],cause:null,suppressed:[]};
  const detail=Buffer.from(JSON.stringify({format:'nesql.failure',job:'open-failure',target:{handler:id},index:0,error}));
  await writeFile(path.join(instance,'nesql','open-detail.json'),detail);
  const raw=Buffer.from(JSON.stringify({rows:[{handler:id,status:'failed',error,failureFiles:[{path:'open-detail.json',index:0,bytes:detail.length,sha256:digest(detail)}]}]})),reportPath=path.join(instance,'nesql','open-failure.json');await writeFile(reportPath,raw);
  const runner=new AcceptanceCoordinator(instance);runner.client={session:'native-open',request:async(method,url)=>{
    if(url==='/game')return gameState(instance,{ready:true,world:{folder:'test-world'},exporter:'0.15.0',revision:14});
    if(url==='/checks')return{id:'open-failure'};
    if(url==='/jobs/open-failure')return{job:{id:'open-failure',state:'checked',report:{path:reportPath,bytes:raw.length,sha256:digest(raw)}}};
    throw Error('Unexpected '+url);
  }};
  const checkpoint=await withNativeEvidence(runner).runPlan({world:'test-world',modSha256,handlers:[{id,classification:'interactive',route:'interactive:fixture'}]});
  assert.deepEqual(checkpoint.handlers[id].error,error);assert.equal(checkpoint.archivedReports.length,1);assert.equal(checkpoint.handlers[id].status,'failed');
  assert.deepEqual(await readFile(checkpoint.archivedReports[0].files[0].archivePath),detail);
});

test('source stage refuses incomplete, failed, widened or stale plans before posting', async () => {
  const mutations = [
    x => { delete x.plan.stages.source.handlers; },
    x => { x.plan.stages.source.handlers = []; },
    x => { x.plan.stages.source.handlers.push(x.id); },
    x => { x.plan.stages.source.handlers = ['category_' + 'b'.repeat(64)]; },
    x => { x.plan.handlers.push({ id:'category_'+'b'.repeat(64), classification:'recipe' }); },
    x => { delete x.checkpoint.handlers[x.id]; },
    x => { x.checkpoint.handlers[x.id].status = 'excluded'; },
    x => { x.checkpoint.handlers[x.id].status = 'partial'; },
    x => { x.checkpoint.handlers[x.id].unexamined = 1; },
    x => { x.checkpoint.handlers[x.id].checked = 1; },
    x => { x.checkpoint.handlers[x.id].failed = [0]; },
    x => { x.checkpoint.handlers[x.id].hasFailures = true; },
    x => { x.checkpoint.handlers[x.id].failuresOmitted = 1; },
    x => { x.checkpoint.handlers[x.id].error = { code:'failure' }; },
    x => { x.plan.stages.source.scope = 'typo'; },
    x => { x.runner.planFingerprint = async () => 'changed-environment'; }
  ];
  for (const mutate of mutations) {
    const x = exportFixture(); mutate(x);
    await assert.rejects(() => withNativeEvidence(x.runner).runSourceStage(x.plan, x.checkpoint));
    assert.equal(x.requests.length, 0, 'Invalid plan posted a game export');
  }
});

test('source failures retain the real job and error for the export handoff', async () => {
  const { plan, checkpoint, runner } = exportFixture();
  const error = { code:'render_failed', message:'Native icon failed', fatal:false };
  runner.client.request = async method => method === 'POST' ? { id:'failed-source' }
    : { job:{ state:'failed', error } };
  await assert.rejects(() => withNativeEvidence(runner).runSourceStage(plan,checkpoint), /render_failed/);
  assert.equal(checkpoint.stages?.source?.job,'failed-source');
  assert.equal(checkpoint.stages.source.status,'failed');
  assert.deepEqual(checkpoint.stages.source.error,error);
});

test('explicit source recovery forwards a pinned prefix with a new job key and retains previous failures', async () => {
  const { plan, checkpoint, runner, requests } = exportFixture();
  const previous = { status: 'failed', job: randomUUID(), request: { key: 'old-export' }, checkpoint: { sha256: 'c'.repeat(64) } };
  checkpoint.stages = { source: previous };
  plan.stages.source.resume = { job: previous.job, sha256: previous.checkpoint.sha256 };
  await withNativeEvidence(runner).runSourceStage(plan, checkpoint);
  assert.deepEqual(requests[0].resume, plan.stages.source.resume);
  assert.notEqual(requests[0].key, previous.request.key);
  plan.stages.source.key = previous.request.key;
  await assert.rejects(() => withNativeEvidence(runner).runSourceStage(plan, checkpoint), /new key/);
});

test('Source gate requires successful representative rendering when enabled', async () => {
  const x = exportFixture();
  x.plan.stages.source.preflight = true;
  x.runner.runVisualStage = async () => ({ status: 'failed', samples: [] });
  await assert.rejects(() => withNativeEvidence(x.runner).runSourceStage(x.plan, x.checkpoint), /render/i);
  assert.equal(x.requests.length, 0);
});

test('compiler cannot bypass diagnostic collection or incomplete content coverage',async()=>{
  for(const diagnostics of [true,false]){
    const x=exportFixture();let guarded=false;
    x.runner.guardResources=async()=>{guarded=true;};
    if(diagnostics)x.plan.diagnostics={collectVisuals:true};
    else x.checkpoint.stages={diagnosticVisual:{status:'partial'}};
    await assert.rejects(()=>x.runner.runCompilerStage(x.plan,x.checkpoint),/Diagnostic collection|content diagnostics/);
    assert.equal(guarded,false,'Reject before compiler execution or resource sampling');
  }
});

test('diagnostic collection retains blocked handlers and renders independent completed data without export', async t => {
  const root=await mkdtemp(path.join(os.tmpdir(),'nesql-batch-probe-'));t.after(()=>rm(root,{recursive:true,force:true}));
  const x=exportFixture(),blocked='category_'+'b'.repeat(64);
  x.plan.handlers.push({id:blocked,classification:'interactive'});x.plan.stages.source.handlers.push(blocked);
  x.plan.diagnostics={collectVisuals:true};x.plan.stages.source.execute=false;x.plan.stages.compiler={execute:false};x.plan.stages.source.preflight=true;
  x.checkpoint.handlers[x.id].excluded=[];x.checkpoint.handlers[blocked]={id:blocked,status:'failed',total:null,checked:0,unexamined:null,error:{code:'reference_unsupported'}};
  let pending,calls=0;
  x.runner.client.request=async(method,url,request)=>{
    if(method==='POST'){assert.equal(url,'/checks');assert.deepEqual(request.handlers,[x.id]);pending=request;return{id:'batch-'+(++calls)};}
    const id='batch-'+calls,file=path.join(root,id+'.json');
    const bytes=Buffer.from(JSON.stringify({job:id,status:'complete',request:{key:pending.key,world:pending.world,handlers:pending.handlers,check:{render:true,offset:pending.offset,limit:1}},rows:[{handler:x.id,status:'partial',totalRecipes:2,checkedRecipes:1,offset:pending.offset,end:pending.offset+1,failedRecipes:[],excludedRecipes:[],failures:[],visualSamples:[{index:pending.offset,icons:0,pictures:0,pixels:'0',visiblePixels:'0',programs:'0',programNodes:'0',programImages:'0',programPixels:'0'}]}]}));
    await writeFile(file,bytes);return{job:{id,state:'checked',report:{path:file,bytes:bytes.length,sha256:digest(bytes)}}};
  };
  x.runner.archiveReport=async job=>({archivePath:job.report.path,sha256:job.report.sha256});
  const stage=await withNativeEvidence(x.runner).runDiagnosticVisualStage(x.plan,x.checkpoint);
  assert.equal(stage.status,'partial');assert.equal(stage.blocked[0].handler,blocked);assert.equal(stage.samples.length,2);assert.equal(calls,2);
  assert.equal(x.checkpoint.stages.preflight,undefined,'Partial diagnostic coverage must not become a Source gate');
  x.plan.stages.source.execute=true;
  await assert.rejects(()=>withNativeEvidence(x.runner).runDiagnosticVisualStage(x.plan,x.checkpoint),/diagnostic|export/i);assert.equal(calls,2);
});

test('full content diagnostics cover every native entry in bounded ranges and verify every sample',async t=>{
  const root=await mkdtemp(path.join(os.tmpdir(),'nesql-full-content-'));t.after(()=>rm(root,{recursive:true,force:true}));
  const x=exportFixture();x.plan.diagnostics={collectVisuals:true,visualCoverage:'all'};x.plan.stages.source.execute=false;x.plan.stages.compiler={execute:false};x.plan.stages.source.preflight=true;
  const total=4101;
  Object.assign(x.checkpoint.handlers[x.id],{total,checked:total,offset:total,excluded:[5]});
  let pending,calls=0,omit=false,badCategory=false;
  x.runner.client.request=async(method,url,request)=>{
    if(method==='POST'){assert.equal(url,'/checks');assert.equal(request.contentAudit,true);assert.equal(request.offset,calls%2===0?0:4096);assert.equal(request.limit,Math.min(4096,total-request.offset));pending=request;return{id:'content-'+(++calls)};}
    const id='content-'+calls,file=path.join(root,id+'.json');
    const samples=Array.from({length:pending.limit},(_,relative)=>({index:pending.offset+relative,icons:0,pictures:0,pixels:'0',visiblePixels:'0',programs:'0',programNodes:'0',programImages:'0',programPixels:'0'}));if(omit&&pending.offset===0)samples.splice(30,1);
    if(badCategory)samples.push({...samples[0],index:-1,icons:1});
    const bytes=Buffer.from(JSON.stringify({job:id,status:'complete',request:{key:pending.key,world:pending.world,handlers:pending.handlers,check:{render:true,contentAudit:true,offset:pending.offset,limit:pending.limit}},rows:[{handler:x.id,status:'partial',totalRecipes:total,checkedRecipes:pending.limit,offset:pending.offset,end:pending.offset+pending.limit,failedRecipes:[],excludedRecipes:pending.offset===0?[5]:[],failures:[],visualSamples:samples}]}));
    await writeFile(file,bytes);return{job:{id,state:'checked',report:{path:file,bytes:bytes.length,sha256:digest(bytes)}}};
  };
  x.runner.archiveReport=async job=>({archivePath:job.report.path,sha256:job.report.sha256});
  const result=await withNativeEvidence(x.runner).runDiagnosticVisualStage(x.plan,x.checkpoint);assert.equal(result.status,'passed');assert.equal(result.coverage,'all-native-entries-with-passed-data');assert.equal(calls,2);
  delete x.checkpoint.stages.diagnosticVisual;omit=true;assert.equal((await withNativeEvidence(x.runner).runDiagnosticVisualStage(x.plan,x.checkpoint)).status,'failed');
  delete x.checkpoint.stages.diagnosticVisual;omit=false;badCategory=true;assert.equal((await withNativeEvidence(x.runner).runDiagnosticVisualStage(x.plan,x.checkpoint)).status,'failed');
  delete x.plan.diagnostics;await assert.rejects(()=>withNativeEvidence(x.runner).runSourceStage(x.plan,x.checkpoint),/content diagnostics/i);assert.equal(calls,6);
});

test('content collection continues only explicitly isolated job failures and stops on unsafe or unknown severity',async t=>{
  const root=await mkdtemp(path.join(os.tmpdir(),'nesql-content-isolation-'));t.after(()=>rm(root,{recursive:true,force:true}));
  const isolated={code:'reference_unsupported',fatal:false,details:{type:'NativeAdmissionFault',code:'reference_unsupported',fatal:false,suppressed:[]}};
  const unsafe=[
    {...isolated,fatal:true},
    {code:'recipe_capture',details:isolated.details},
    {...isolated,details:{...isolated.details,fatal:undefined}},
    {...isolated,details:{...isolated.details,code:'check_cleanup'}},
    {...isolated,details:{...isolated.details,cause:{code:'native_io',fatal:true}}},
    {...isolated,details:{...isolated.details,suppressed:[{code:'cleanup_failed',fatal:true}]}}
  ];
  for(const [attempt,error]of [isolated,...unsafe].entries()){
    const x=exportFixture(),other='category_'+'b'.repeat(64);
    x.plan.handlers.push({id:other,classification:'recipe'});x.plan.stages.source.handlers.push(other);
    x.plan.diagnostics={collectVisuals:true,visualCoverage:'all'};
    Object.assign(x.plan.stages.source,{execute:false,preflight:true});x.plan.stages.compiler={execute:false};
    for(const id of [x.id,other])x.checkpoint.handlers[id]={...x.checkpoint.handlers[x.id],id,total:1,checked:1,offset:1,excluded:[]};
    let calls=0,pending;
    x.runner.client.request=async(method,url,request)=>{
      if(method==='POST'){assert.equal(url,'/checks');pending=request;return{id:`isolated-${attempt}-${++calls}`};}
      const id=`isolated-${attempt}-${calls}`;
      if(calls===1)return{job:{id,state:'failed',error}};
      const file=path.join(root,id+'.json');
      const bytes=Buffer.from(JSON.stringify({job:id,status:'complete',request:{...pending,check:{render:true,contentAudit:true,offset:0,limit:1}},rows:[{handler:other,status:'passed',totalRecipes:1,checkedRecipes:1,offset:0,end:1,failedRecipes:[],excludedRecipes:[],failures:[],visualSamples:[{index:0,icons:0,pictures:0,pixels:'0',visiblePixels:'0',programs:'0',programNodes:'0',programImages:'0',programPixels:'0'}]}]}));
      await writeFile(file,bytes);return{job:{id,state:'checked',report:{path:file,bytes:bytes.length,sha256:digest(bytes)}}};
    };
    x.runner.archiveReport=async job=>({archivePath:job.report.path,sha256:job.report.sha256});
    if(attempt===0){
      const stage=await withNativeEvidence(x.runner).runDiagnosticVisualStage(x.plan,x.checkpoint);
      assert.equal(calls,2);assert.equal(stage.status,'failed');assert.deepEqual(stage.samples[0].jobSnapshot.error,error);assert.equal(stage.samples[1].status,'passed');
    }else{
      await assert.rejects(()=>withNativeEvidence(x.runner).runDiagnosticVisualStage(x.plan,x.checkpoint),/stopped/);
      assert.equal(calls,1);assert.equal(x.checkpoint.stages.diagnosticVisual.status,'stopped');
    }
  }
});

test('representative rendering checks first/last recipes, verifies evidence and reuses only recorded samples', async t => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'nesql-render-probe-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const x = exportFixture(); x.plan.stages.source.preflight = true; x.checkpoint.handlers[x.id].excluded = [];
  let pending, calls = 0;
  let content = {icons:1, pictures:0, pixels:'100', visiblePixels:'99', programs:'0', programNodes:'0', programImages:'0', programPixels:'0'};
  x.runner.client.request = async (method, url, body) => {
    if (method === 'POST') { pending = body; calls++; return { id: `probe-${calls}` }; }
    const id = `probe-${calls}`, file = path.join(root, `${id}.json`);
    const bytes = Buffer.from(JSON.stringify({ job: id, status: 'complete', request: { key: pending.key, world: pending.world, handlers: pending.handlers,
      check: { render: true, offset: pending.offset, limit: 1 } }, rows: [{ handler: x.id, status: 'partial', totalRecipes: 2,
      checkedRecipes: 1, offset: pending.offset, end: pending.offset + 1, failedRecipes: [], excludedRecipes: [], failures: [],
      visualSamples: [{ index: pending.offset, ...content }] }] }));
    await writeFile(file, bytes);
    return { job: { id, state: 'checked', report: { path: file, bytes: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex') } } };
  };
  x.runner.archiveReport = async job => ({ archivePath: job.report.path, sha256: job.report.sha256 });
  const stage = await withNativeEvidence(x.runner).runVisualStage(x.plan, x.checkpoint);
  assert.equal(stage.status, 'passed'); assert.deepEqual(stage.samples.map(sample => sample.offset), [0, 1]);
  assert.equal(calls, 2);
  await withNativeEvidence(x.runner).runVisualStage(x.plan, x.checkpoint); assert.equal(calls, 2, 'Verified samples were blindly rerendered');
  await writeFile(stage.samples[0].archive.archivePath, '{}');
  await assert.rejects(() => withNativeEvidence(x.runner).runVisualStage(x.plan, x.checkpoint), /evidence changed/);
  for (const [sample, expected] of [
    [{...content, programs:'1', programNodes:'3', programImages:'1', programPixels:'256'}, 'passed'],
    [{icons:0, pictures:0, pixels:'0', visiblePixels:'0', programs:'0', programNodes:'0', programImages:'0', programPixels:'0'}, 'passed'],
    [{...content, pixels:'0', visiblePixels:'0'}, 'failed'],
    [{...content, visiblePixels:'101'}, 'failed'],
    [{...content, programs:'1'}, 'failed'],
    [{...content, programNodes:undefined}, 'failed'],
    [{scenes:1, pixels:'100', visiblePixels:'99'}, 'failed'],
  ]) {
    content = sample;
    delete x.checkpoint.stages.preflight;
    assert.equal((await withNativeEvidence(x.runner).runVisualStage(x.plan, x.checkpoint)).status, expected);
  }
});

test('checkpoint journal recovers durable deltas and ignores only an unfinished final write', async t => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'nesql-journal-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const file = path.join(directory, 'checkpoint.json'), log = new CheckpointLog(file);
  const state = { handlers: { one: { offset: 0 } }, activeJob: null };
  await log.save(state, true);
  state.handlers.one.offset = 512; state.activeJob = 'next';
  await log.save(state);
  assert.equal(JSON.parse(await readFile(file)).handlers.one.offset, 0, 'Every update still rewrites the whole snapshot');
  await writeFile(file + '.journal', '{"unfinished":', { flag: 'a' });
  const recovered = await readCheckpoint(file, { repairTail: true });
  assert.equal(recovered.handlers.one.offset, 512); assert.equal(recovered.activeJob, 'next');
  const resumed = new CheckpointLog(file, recovered);
  recovered.handlers.one.offset = 1024; await resumed.save(recovered, true);
  assert.equal((await readCheckpoint(file)).handlers.one.offset, 1024);
  assert.equal((await readFile(file + '.journal')).length, 0);
});

test('compact reports resolve only hash-verified shared evidence and preserve their raw identity', async t => {
  const directory = await mkdtemp(path.join(os.tmpdir(), 'nesql-shared-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  await mkdir(path.join(directory, 'shared'));
  const bytes = Buffer.from('{"locale":"en_US"}'), sha256 = digestForTest(bytes);
  const reference = { path: `shared/${sha256}.json`, bytes: bytes.length, sha256 };
  await writeFile(path.join(directory, reference.path), bytes);
  const raw = { environmentRef: reference, provenance: { environment: sha256 }, rows: [] };
  const resolved = await resolveReport(raw, directory);
  assert.deepEqual(resolved.environment, { locale: 'en_US' });
  assert.equal(raw.environment, undefined);
  await writeFile(path.join(directory, reference.path), '{"locale":"zh_CN"}');
  await assert.rejects(() => resolveReport(raw, directory), /hash|digest|SHA|mismatch/i);
});

const modBytes = Buffer.from('NESQL protocol fixture');
const modSha256 = createHash('sha256').update(modBytes).digest('hex');
async function fixture(prefix) {
  const instance = await mkdtemp(prefix);
  await writeFile(path.join(instance, 'fixture.jar'), modBytes);
  return instance;
}
function gameState(instance, state) {
  return { sources: { valid: true, rows: [{ id: 'nesql-exporter', valid: true, path: path.join(instance, 'fixture.jar') }] }, ...state };
}

test('AcceptanceCoordinator retains and archives native handler-open errors before checking counts', async t => {
  for (const fatal of [false, true]) {
    const instance = await fixture(path.join(os.tmpdir(), 'nesql-open-'));
    t.after(() => rm(instance, { recursive: true, force: true }));
    await mkdir(path.join(instance, 'nesql'));
    const error = { code: 'handler_unsupported', message: 'No recipe loader for: example.NativeHandler', type: 'Jobs$Fault', fatal, cause: null };
    const detail = Buffer.from(JSON.stringify({ format:'nesql.failure',job:'native-job',target:{handler:'native'},index:0,error }));
    await writeFile(path.join(instance,'nesql','open-detail.json'),detail);
    const raw = Buffer.from(JSON.stringify({ rows: [{ handler: 'native', status: 'failed', error,
      failureFiles:[{path:'open-detail.json',index:0,bytes:detail.length,sha256:digestForTest(detail)}] }] }));
    const reportPath = path.join(instance, 'nesql', 'report.json');
    await writeFile(reportPath, raw);
    const coordinator = new AcceptanceCoordinator(instance);
    let checks = 0;
    coordinator.client = { session: 'test-session', request: async (method, url) => {
      if (url === '/game') return gameState(instance, { ready: true, world: { folder: 'test-world' }, exporter: '0.15.0', revision: 14 });
      if (url === '/checks') { checks++; return { id: 'native-job' }; }
      if (url === '/jobs/native-job') return { job: { id: 'native-job', state: 'checked', report: { path: reportPath, bytes: raw.length, sha256: digestForTest(raw) } } };
      throw new Error(`Unexpected request ${method} ${url}`);
    } };
    const plan = { world: 'test-world', modSha256, handlers: [{ id: 'native', name: 'Native' }, { id: 'tool', classification: 'tooling', implementationStatus: 'excluded_justified' }] };
    if (fatal) await assert.rejects(() => withNativeEvidence(coordinator).runPlan(plan), /Fatal.*handler/i);
    else await withNativeEvidence(coordinator).runPlan(plan);
    const checkpoint = JSON.parse(await readFile(coordinator.checkpointPath));
    assert.deepEqual(checkpoint.handlers.native.error, error);
    assert.equal(checkpoint.handlers.native.status, 'failed');
    assert.equal(checkpoint.handlers.native.checked, 0);
    assert.equal(checks, 1);
    assert.equal(checkpoint.archivedReports.length, 1);
    assert.deepEqual(await readFile(checkpoint.archivedReports[0].archivePath), await readFile(reportPath));
    assert.equal(checkpoint.handlers.tool?.status, fatal ? undefined : 'excluded');
  }
});

function digestForTest(bytes) { return createHash('sha256').update(bytes).digest('hex'); }

test('commit-memory guard persists a resumable stop before dispatch and allows recovery', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-memory-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  let committedBytes = '99';
  let dispatched = 0;
  const coordinator = new AcceptanceCoordinator(instance, {
    readMemory: async () => ({ committedBytes, commitLimitBytes: '100' }),
  });
  coordinator.client = { session: 'memory-session', request: async (method, url) => {
    if (url === '/game') return gameState(instance, { ready: true, world: { folder: 'test-world' }, exporter: '0.15.0', revision: 14 });
    if (url === '/checks') { dispatched++; throw new Error('dispatch after recovery'); }
    throw new Error(`Unexpected request ${method} ${url}`);
  } };
  const plan = { world: 'test-world', modSha256, handlers: [{ id: 'native' }],
    memory: { minimumFreeCommitBytes: '10', maximumCommitPercent: 95 } };
  await assert.rejects(() => withNativeEvidence(coordinator).runPlan(plan), /resource_pressure/);
  assert.equal(dispatched, 0);
  const checkpoint = JSON.parse(await readFile(coordinator.checkpointPath));
  assert.equal(checkpoint.resourceStop.code, 'resource_pressure');
  assert.equal(checkpoint.resourceStop.sample.freeCommitBytes, '1');
  assert.equal(checkpoint.handlers.native.offset, 0);
  assert.equal(checkpoint.handlers.native.status, 'pending');
  assert.equal(checkpoint.activeJob, null);
  committedBytes = '40';
  await assert.rejects(() => withNativeEvidence(coordinator).runPlan(plan), /dispatch after recovery/);
  assert.equal(dispatched, 1);
  assert.equal(JSON.parse(await readFile(coordinator.checkpointPath)).resourceStop, null);
  const events = (await readFile(coordinator.checkpointPath + '.resources.jsonl', 'utf8')).trim().split('\n').map(JSON.parse);
  assert.deepEqual(events.map(event => event.sample.freeCommitBytes), ['1', '60']);
});

test('typed native dispositions are streamed, archived and distinguish duplicates from exclusions', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-native-outcomes-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  const directory = path.join(instance, 'nesql', 'checks'), job = 'outcome-job', recipe = 'recipe_' + 'a'.repeat(64);
  const relative = `${job}-outcomes/${digestForTest(Buffer.from(JSON.stringify({ handler: 'native' })))}.jsonl.gz`;
  await mkdir(path.dirname(path.join(directory, relative)), { recursive: true });
  const entries = [
    { index: 0, outcome: { kind: 'captured', emitted: 1, duplicates: 0, references: 0, duplicateOrigins: [] } },
    { index: 1, outcome: { kind: 'excluded', emitted: 0, duplicates: 0, references: 0, proof: { reason: 'native predicate is empty' } } },
    { index: 2, outcome: { kind: 'duplicate', emitted: 0, duplicates: 1, references: 0, duplicateOrigins: [{ recipe, firstIndex: 0, firstBranch: 0, branch: 0 }] } },
    { index: 3, outcome: { kind: 'captured', emitted: 1, duplicates: 0, references: 0, duplicateOrigins: [] } },
  ];
  const logical = Buffer.from(entries.map(value => JSON.stringify(value) + '\n').join('')), bytes = gzipSync(logical);
  const row = { handler: 'native', accountingRevision: 1, status: 'passed', recordKind: 'recipes', totalRecipes: 4,
    offset: 0, end: 4, checkedRecipes: 4, unexamined: 0, failedRecipes: [], excludedRecipes: [1], duplicateRecipes: [2],
    passedRecipes: 2, referenceRows: 0, emittedBranches: 2, duplicateBranches: 1,
    captureOutcomeFile: { format: 'nesql.capture-outcomes', revision: 1, path: relative, sha256: digestForTest(bytes),
      bytes: String(bytes.length), logicalBytes: String(logical.length), rows: '4', rangeComplete: true } };
  await writeFile(path.join(directory, relative), bytes);
  const checked = await verifyOutcomes(directory, row);
  assert.deepEqual(checked.counts, { captured: 2, excluded: 1, duplicate: 1, reference: 0, emitted: 2, duplicates: 1 });
  const coordinator = new AcceptanceCoordinator(instance);
  const archived = await coordinator.archiveReport({ id: job, report: { path: path.join(directory, 'report.json') } }, Buffer.from(JSON.stringify({ rows: [row] })), [row]);
  assert.deepEqual(await readFile(archived.outcomes[0].archivePath), bytes);
  await assert.rejects(() => verifyOutcomes(directory, { ...row, excludedRecipes: [1, 2] }), /deep-equal|strictly deep-equal/);
  await assert.rejects(() => verifyOutcomes(directory, { ...row, captureOutcomeFile: { ...row.captureOutcomeFile, logicalBytes: String(logical.length - 1) } }), /decompression/);
  await assert.rejects(() => verifyOutcomes(directory, { ...row, captureOutcomeFile: { ...row.captureOutcomeFile, sha256: '0'.repeat(64) } }), /digest/);
  await assert.rejects(() => verifyOutcomes(directory, { ...row, captureOutcomeFile: { ...row.captureOutcomeFile, path: '../outside.jsonl.gz' } }), /path/);
  const wrongOrigin = structuredClone(entries); wrongOrigin[2].outcome.duplicateOrigins[0].firstIndex = 1;
  const wrongLogical = Buffer.from(wrongOrigin.map(value => JSON.stringify(value) + '\n').join('')), wrongBytes = gzipSync(wrongLogical);
  await writeFile(path.join(directory, relative), wrongBytes);
  await assert.rejects(() => verifyOutcomes(directory, { ...row, captureOutcomeFile: { ...row.captureOutcomeFile,
    sha256: digestForTest(wrongBytes), bytes: String(wrongBytes.length), logicalBytes: String(wrongLogical.length) } }), /origin/);
});

test('large failed scans retain every raw detail but bound repeated checkpoint samples', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-bounded-errors-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  const directory = path.join(instance, 'nesql', 'checks');
  await mkdir(directory, { recursive: true });
  const coordinator = new AcceptanceCoordinator(instance, { pageSize: 40 });
  const jobs = new Map();
  coordinator.client = { session: 'bounded', request: async (method, url, body) => {
    if (url === '/game') return gameState(instance, { ready: true, world: { folder: 'test-world' }, exporter: '0.15.0', revision: 14 });
    if (url === '/checks') {
      const id = `page-${body.offset}`;
      const failures = [], failureFiles = [];
      for (let index = body.offset; index < body.offset + 40; index++) {
        const error = { code: 'magic_recipe', type: 'Jobs$Fault', fatal: false, message: `index ${index}: ${'detail'.repeat(1000)}` };
        failures.push({ index, error });
        const raw = Buffer.from(JSON.stringify({ format: 'nesql.failure', job: id, target: { handler: 'native' }, index, error }));
        const name = `${id}-${index}.json`;
        await writeFile(path.join(directory, name), raw);
        failureFiles.push({ path: name, index, bytes: raw.length, sha256: digestForTest(raw) });
      }
      const row = { handler: 'native', status: 'failed', totalRecipes: 80, offset: body.offset,
        end: body.offset + 40, checkedRecipes: 40, unexamined: 40 - body.offset,
        failedRecipes: failures.map(f => f.index), excludedRecipes: [], failures, failureFiles, failuresOmitted: 0 };
      const raw = Buffer.from(JSON.stringify({ rows: [row] }));
      const reportPath = path.join(directory, `${id}.json`);
      await writeFile(reportPath, raw);
      jobs.set(`/jobs/${id}`, { job: { id, state: 'checked', report: { path: reportPath, bytes: raw.length, sha256: digestForTest(raw) } } });
      return { id };
    }
    if (jobs.has(url)) return jobs.get(url);
    throw new Error(`Unexpected request ${method} ${url}`);
  } };
  const checkpoint = await withNativeEvidence(coordinator).runPlan({ world: 'test-world', modSha256, handlers: [{ id: 'native' }] });
  const state = checkpoint.handlers.native;
  assert.equal(state.status, 'failed');
  assert.equal(state.checked, 80);
  assert.deepEqual(state.failed, Array.from({ length: 80 }, (_, i) => i));
  assert.ok(state.failures.length <= 16, 'repeated full error objects must not grow with recipe count');
  assert.equal(state.failures.length + state.failureSamplesOmitted, 80);
  assert.equal(state.failuresOmitted, 0, 'checkpoint sampling is not producer evidence loss');
  assert.ok(checkpoint.failures.length <= 16, 'global checkpoint must reference page archives instead of repeating every stack');
  assert.equal(checkpoint.archivedReports.length, 2);
  let archived = 0;
  for (const report of checkpoint.archivedReports) for (const file of report.files) {
    const raw = await readFile(file.archivePath);
    assert.equal(digestForTest(raw), file.sha256);
    assert.equal(JSON.parse(raw).index, file.index);
    archived++;
  }
  assert.equal(archived, 80);
});

test('AcceptanceCoordinator archives resource failures using the resource identity', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-resource-detail-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  const directory = path.join(instance, 'nesql', 'checks'); await mkdir(directory, { recursive: true });
  const detail = Buffer.from(JSON.stringify({ format: 'nesql.failure', job: 'resource-job', target: { resource: 'demo:textures/a.png' }, index: 0, error: { code: 'resource_limit' } }));
  await writeFile(path.join(directory, 'detail.json'), detail);
  const row = { resource: 'demo:textures/a.png', status: 'failed', failureFiles: [{ path: 'detail.json', index: 0, bytes: detail.length, sha256: digestForTest(detail) }] };
  const raw = Buffer.from(JSON.stringify({ rows: [row] }));
  const coordinator = new AcceptanceCoordinator(instance);
  const result = await coordinator.archiveReport({ id: 'resource-job', report: { path: path.join(directory, 'report.json') } }, raw, [row]);
  assert.deepEqual(await readFile(result.files[0].archivePath), detail);
  assert.deepEqual(await readFile(result.archivePath), raw);
});

test('AcceptanceCoordinator dispatches the recorded singular structure route to controller checks', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-route-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const raw = Buffer.from(JSON.stringify({ rows: [{ controller: 17000, status: 'passed' }] }));
  const reportPath = path.join(instance, 'nesql', 'structure.json');
  await writeFile(reportPath, raw);
  const coordinator = new AcceptanceCoordinator(instance);
  let request;
  coordinator.client = { session: 'test-session', request: async (method, url, body) => {
    if (url === '/game') return gameState(instance, { ready: true, world: { folder: 'test-world' }, exporter: '0.15.0', revision: 14, structures: [{ controller: 17000 }] });
    if (url === '/checks') { request = body; return { id: 'structure-job' }; }
    if (url === '/jobs/structure-job') return { job: { id: 'structure-job', state: 'checked', report: { path: reportPath, bytes: raw.length, sha256: digestForTest(raw) } } };
    throw new Error(`Unexpected request ${method} ${url}`);
  } };
  const checkpoint = await withNativeEvidence(coordinator).runPlan({ world: 'test-world', modSha256, handlers: [{ id: 'structure', name: 'Structure', classification: 'domain', route: 'domain:structure' }] });
  assert.equal(request.domain, 'structures');
  assert.deepEqual(request.controllers, [17000]);
  assert.equal(request.handlers, undefined);
  assert.equal(checkpoint.handlers.structure.status, 'passed');
});


test('AcceptanceCoordinator handles 73-char IDs, keys <= 80 chars, pagination, and resume', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const token = 'a'.repeat(64);
  const session = randomUUID();
  let requestedKeys = [];
  let requestedOffsets = [];
  const realHandlerId = 'category_000c7fb73efe2db4c011955bd10e5ec5f5bbc2ea18fe1062a24bd28ae073f381';

  const game = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    const jsonBody = body ? JSON.parse(body) : undefined;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('X-NESQL-Session', session);

    if (request.url === '/game') {
      response.end(JSON.stringify(gameState(instance, {
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      })));
    } else if (request.url === '/checks' && request.method === 'POST') {
      requestedKeys.push(jsonBody.key);
      requestedOffsets.push(jsonBody.offset);
      assert.ok(jsonBody.key.length <= 80, `Job key must be <= 80 chars: ${jsonBody.key}`);

      const currentOffset = jsonBody.offset;
      const reportFile = path.join(instance, 'nesql', `report-${currentOffset}.json`);
      const isFirst = currentOffset === 0;
      const checked = isFirst ? 4000 : 2000;
      const reportData = {
        rows: [{
          handler: realHandlerId,
          status: 'partial',
          totalRecipes: 6000,
          offset: currentOffset,
          end: currentOffset + checked,
          checkedRecipes: checked,
          unexamined: 6000 - (currentOffset + checked),
          failedRecipes: [],
          excludedRecipes: [],
          failures: [],
          failuresOmitted: 0
        }]
      };
      const rawReport = Buffer.from(JSON.stringify(reportData));
      await writeFile(reportFile, rawReport);
      response.end(JSON.stringify({ id: `job-${currentOffset}`, state: 'queued' }));
    } else if (request.url.startsWith('/jobs/job-')) {
      const offset = Number(request.url.replace('/jobs/job-', ''));
      const reportFile = path.join(instance, 'nesql', `report-${offset}.json`);
      const bytes = (await readFile(reportFile)).length;
      const sha256 = createHash('sha256').update(await readFile(reportFile)).digest('hex');
      response.end(JSON.stringify({
        job: {
          id: `job-${offset}`,
          state: 'checked',
          report: { path: reportFile, total: 6000, bytes, sha256 }
        }
      }));
    } else {
      response.statusCode = 404;
      response.end(JSON.stringify({ error: { code: 'not_found' } }));
    }
  });

  game.listen(0, '127.0.0.1');
  await once(game, 'listening');
  t.after(() => { game.closeAllConnections(); game.close(); });

  await writeFile(
    path.join(instance, 'nesql', 'connection.json'),
    JSON.stringify({ protocol: 1, port: game.address().port, token, session })
  );

  const coordinator = new AcceptanceCoordinator(instance, { pageSize: 4000 });
  const plan = {
    world: 'test-world', modSha256,
    handlers: [{ id: realHandlerId, name: 'Wiremill' }]
  };

  // Run 1: Verify pagination from 0 to 6000
  const checkpoint = await withNativeEvidence(coordinator).runPlan(plan);
  assert.equal(checkpoint.handlers[realHandlerId].status, 'passed');
  assert.equal(checkpoint.handlers[realHandlerId].checked, 6000);
  assert.equal(checkpoint.handlers[realHandlerId].unexamined, 0);
  assert.deepEqual(requestedOffsets, [0, 4000]);
  for (const k of requestedKeys) {
    assert.ok(k.length <= 80, `Key length must be <= 80: ${k}`);
  }

  // Run 2: Verify resume skips completed handler
  requestedOffsets = [];
  const checkpoint2 = await withNativeEvidence(coordinator).runPlan(plan);
  assert.equal(checkpoint2.handlers[realHandlerId].status, 'passed');
  assert.equal(requestedOffsets.length, 0, 'Completed handler must be skipped without re-requesting');
});

test('AcceptanceCoordinator halts whole plan on fatal error and isolates environment', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-fatal-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const token = 'b'.repeat(64);
  const session = randomUUID();

  const game = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('X-NESQL-Session', session);

    if (request.url === '/game') {
      response.end(JSON.stringify(gameState(instance, {
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      })));
    } else if (request.url === '/checks' && request.method === 'POST') {
      response.end(JSON.stringify({ id: 'job-fatal', state: 'queued' }));
    } else if (request.url === '/jobs/job-fatal') {
      response.end(JSON.stringify({
        job: {
          id: 'job-fatal',
          state: 'failed',
          error: { code: 'world_changed', message: 'World instance was replaced during capture', fatal: true, details: { type: 'com.github.dcysteine.nesql.exporter.task.Jobs$Fault', code: 'world_changed', fatal: true, suppressed: [], suppressedOmitted: 0 } }
        }
      }));
    }
  });

  game.listen(0, '127.0.0.1');
  await once(game, 'listening');
  t.after(() => { game.closeAllConnections(); game.close(); });

  await writeFile(
    path.join(instance, 'nesql', 'connection.json'),
    JSON.stringify({ protocol: 1, port: game.address().port, token, session })
  );

  const coordinator = new AcceptanceCoordinator(instance);
  const plan = {
    world: 'test-world', modSha256,
    handlers: [{ id: 'handler-1', name: 'Machine 1' }, { id: 'handler-2', name: 'Machine 2' }]
  };

  await assert.rejects(
    async () => withNativeEvidence(coordinator).runPlan(plan),
    /Fatal execution failure/
  );

  const cp = JSON.parse(await readFile(coordinator.checkpointPath, 'utf8'));
  assert.equal(cp.handlers['handler-1'].status, 'failed');
  assert.equal(cp.handlers['handler-2'], undefined, 'Handler 2 must not be run after fatal failure');
});

test('AcceptanceCoordinator refuses unsupported handler status without marking as passed', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-unsupp-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const token = 'c'.repeat(64);
  const session = randomUUID();
  const unsuppHandlerId = 'category_unsupported_example';

  const game = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('X-NESQL-Session', session);

    if (request.url === '/game') {
      response.end(JSON.stringify(gameState(instance, {
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      })));
    } else if (request.url === '/checks' && request.method === 'POST') {
      const reportFile = path.join(instance, 'nesql', 'report-unsupp.json');
      const reportData = {
        rows: [{
          handler: unsuppHandlerId,
          status: 'unsupported',
          reason: 'No recipe adapter for handler'
        }]
      };
      await writeFile(reportFile, JSON.stringify(reportData));
      response.end(JSON.stringify({ id: 'job-unsupp', state: 'queued' }));
    } else if (request.url === '/jobs/job-unsupp') {
      const reportFile = path.join(instance, 'nesql', 'report-unsupp.json');
      const raw = await readFile(reportFile);
      const bytes = raw.length;
      const sha256 = createHash('sha256').update(raw).digest('hex');
      response.end(JSON.stringify({
        job: {
          id: 'job-unsupp',
          state: 'checked',
          report: { path: reportFile, bytes, sha256 }
        }
      }));
    }
  });

  game.listen(0, '127.0.0.1');
  await once(game, 'listening');
  t.after(() => { game.closeAllConnections(); game.close(); });

  await writeFile(
    path.join(instance, 'nesql', 'connection.json'),
    JSON.stringify({ protocol: 1, port: game.address().port, token, session })
  );

  const coordinator = new AcceptanceCoordinator(instance);
  const plan = {
    world: 'test-world', modSha256,
    handlers: [{ id: unsuppHandlerId, name: 'Unsupported Machine' }]
  };

  const checkpoint = await withNativeEvidence(coordinator).runPlan(plan);
  assert.equal(checkpoint.handlers[unsuppHandlerId].status, 'failed');
  assert.equal(checkpoint.handlers[unsuppHandlerId].error.code, 'handler_unsupported');
  assert.notEqual(checkpoint.handlers[unsuppHandlerId].status, 'passed');
});

test('AcceptanceCoordinator strictly rejects corrupted report SHA256 or mismatched bytes (C2)', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-sha-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const token = 'd'.repeat(64);
  const session = randomUUID();
  const handlerId = 'category_corrupt_sha_test';

  const game = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('X-NESQL-Session', session);

    if (request.url === '/game') {
      response.end(JSON.stringify(gameState(instance, {
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      })));
    } else if (request.url === '/checks' && request.method === 'POST') {
      const reportFile = path.join(instance, 'nesql', 'report-corrupt.json');
      await writeFile(reportFile, JSON.stringify({ rows: [{ handler: handlerId, status: 'passed', totalRecipes: 10, offset: 0, end: 10, checkedRecipes: 10, unexamined: 0, failedRecipes: [], excludedRecipes: [], failures: [] }] }));
      response.end(JSON.stringify({ id: 'job-corrupt', state: 'queued' }));
    } else if (request.url === '/jobs/job-corrupt') {
      const reportFile = path.join(instance, 'nesql', 'report-corrupt.json');
      const bytes = (await readFile(reportFile)).length;
      // Intentionally provide incorrect SHA256
      response.end(JSON.stringify({
        job: {
          id: 'job-corrupt',
          state: 'checked',
          report: { path: reportFile, bytes, sha256: '0'.repeat(64) }
        }
      }));
    }
  });

  game.listen(0, '127.0.0.1');
  await once(game, 'listening');
  t.after(() => { game.closeAllConnections(); game.close(); });

  await writeFile(
    path.join(instance, 'nesql', 'connection.json'),
    JSON.stringify({ protocol: 1, port: game.address().port, token, session })
  );

  const coordinator = new AcceptanceCoordinator(instance);
  const plan = { world: 'test-world', modSha256, handlers: [{ id: handlerId, name: 'Corrupt Machine' }] };

  await assert.rejects(
    async () => withNativeEvidence(coordinator).runPlan(plan),
    /Report SHA256 mismatch/
  );
});

test('AcceptanceCoordinator enforces total recipe count stability across pages (C2)', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-stability-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const token = 'e'.repeat(64);
  const session = randomUUID();
  const handlerId = 'category_unstable_total_test';

  let page = 0;
  const game = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('X-NESQL-Session', session);

    if (request.url === '/game') {
      response.end(JSON.stringify(gameState(instance, {
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      })));
    } else if (request.url === '/checks' && request.method === 'POST') {
      const offset = JSON.parse(body).offset;
      const reportFile = path.join(instance, 'nesql', `report-stab-${offset}.json`);
      // Page 0 reports total 100, page 1 unexpectedly reports total 120!
      const total = offset === 0 ? 100 : 120;
      const reportData = {
        rows: [{
          handler: handlerId,
          status: 'partial',
          totalRecipes: total,
          offset,
          end: offset + 50,
          checkedRecipes: 50,
          unexamined: total - (offset + 50),
          failedRecipes: [],
          excludedRecipes: [],
          failures: [],
          failuresOmitted: 0
        }]
      };
      const raw = Buffer.from(JSON.stringify(reportData));
      await writeFile(reportFile, raw);
      response.end(JSON.stringify({ id: `job-stab-${offset}`, state: 'queued' }));
    } else if (request.url.startsWith('/jobs/job-stab-')) {
      const offset = Number(request.url.replace('/jobs/job-stab-', ''));
      const reportFile = path.join(instance, 'nesql', `report-stab-${offset}.json`);
      const raw = await readFile(reportFile);
      response.end(JSON.stringify({
        job: {
          id: `job-stab-${offset}`,
          state: 'checked',
          report: { path: reportFile, bytes: raw.length, sha256: createHash('sha256').update(raw).digest('hex') }
        }
      }));
    }
  });

  game.listen(0, '127.0.0.1');
  await once(game, 'listening');
  t.after(() => { game.closeAllConnections(); game.close(); });

  await writeFile(
    path.join(instance, 'nesql', 'connection.json'),
    JSON.stringify({ protocol: 1, port: game.address().port, token, session })
  );

  const coordinator = new AcceptanceCoordinator(instance, { pageSize: 50 });
  const plan = { world: 'test-world', modSha256, handlers: [{ id: handlerId, name: 'Unstable Total Machine' }] };

  await assert.rejects(
    async () => withNativeEvidence(coordinator).runPlan(plan),
    /Total recipe stability violation/
  );
});

test('AcceptanceCoordinator isolates recipe failures across pages and archives reports (C2)', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-fail-iso-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const token = 'f'.repeat(64);
  const session = randomUUID();
  const handlerId = 'category_multi_fail_isolated';

  const game = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('X-NESQL-Session', session);

    if (request.url === '/game') {
      response.end(JSON.stringify(gameState(instance, {
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      })));
    } else if (request.url === '/checks' && request.method === 'POST') {
      const offset = JSON.parse(body).offset;
      const reportFile = path.join(instance, 'nesql', `report-fail-${offset}.json`);
      // Page 0 has 2 failures, Page 1 has 1 failure
      const isFirst = offset === 0;
      const failedIndices = isFirst ? [10, 20] : [75];
      const failures = failedIndices.map(idx => ({ index: idx, error: { code: 'test_err', message: `Failure at ${idx}` } }));
      const reportData = {
        rows: [{
          handler: handlerId,
          status: 'failed',
          totalRecipes: 100,
          offset,
          end: offset + 50,
          checkedRecipes: 50,
          unexamined: 100 - (offset + 50),
          failedRecipes: failedIndices,
          excludedRecipes: [],
          failures,
          failuresOmitted: 0
        }]
      };
      const failureFiles = [];
      for (const failure of failures) {
        const entry = `failure-${offset}-${failure.index}.json`;
        const detail = Buffer.from(JSON.stringify({ format: 'nesql.failure', job: `job-fail-${offset}`, target: { handler: handlerId }, ...failure }));
        await writeFile(path.join(instance, 'nesql', entry), detail);
        failureFiles.push({ path: entry, index: failure.index, bytes: detail.length, sha256: createHash('sha256').update(detail).digest('hex') });
      }
      reportData.rows[0].failureFiles = failureFiles;
      const raw = Buffer.from(JSON.stringify(reportData));
      await writeFile(reportFile, raw);
      response.end(JSON.stringify({ id: `job-fail-${offset}`, state: 'queued' }));
    } else if (request.url.startsWith('/jobs/job-fail-')) {
      const offset = Number(request.url.replace('/jobs/job-fail-', ''));
      const reportFile = path.join(instance, 'nesql', `report-fail-${offset}.json`);
      const raw = await readFile(reportFile);
      response.end(JSON.stringify({
        job: {
          id: `job-fail-${offset}`,
          state: 'checked',
          report: { path: reportFile, bytes: raw.length, sha256: createHash('sha256').update(raw).digest('hex') }
        }
      }));
    }
  });

  game.listen(0, '127.0.0.1');
  await once(game, 'listening');
  t.after(() => { game.closeAllConnections(); game.close(); });

  await writeFile(
    path.join(instance, 'nesql', 'connection.json'),
    JSON.stringify({ protocol: 1, port: game.address().port, token, session })
  );

  const coordinator = new AcceptanceCoordinator(instance, { pageSize: 50 });
  const plan = { world: 'test-world', modSha256, handlers: [{ id: handlerId, name: 'Failing Machine' }] };

  const checkpoint = await withNativeEvidence(coordinator).runPlan(plan);
  const handlerState = checkpoint.handlers[handlerId];
  assert.equal(handlerState.status, 'failed');
  assert.equal(handlerState.checked, 100);
  assert.equal(handlerState.unexamined, 0);
  assert.deepEqual(handlerState.failed, [10, 20, 75]);
  assert.equal(checkpoint.archivedReports.length, 2, 'Must archive 2 report shards');
  assert.ok(checkpoint.archivedReports[0].archivePath);
  assert.equal(existsSync(checkpoint.archivedReports[0].archivePath), true, 'Archived report shard must exist on disk');
  assert.equal(checkpoint.archivedReports[0].files.length, 2);
  assert.equal(checkpoint.archivedReports[1].files.length, 1);
});

test('AcceptanceCoordinator restores persisted runId on reload ensuring idempotent job keys (C8)', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-runid-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));

  const fixedRunId = 'run-persistent-test-1234';
  const envFingerprint = AcceptanceCoordinator.fingerprint({
    world: 'test-world', modSha256,
    exporter: '0.15.0',
    revision: 14,
    planHash: 'abc',
    modSha256: null,
    probes: null,
    dependencies: null
  });

  const initialCheckpoint = {
    version: '0.15.0',
    jobIsolationRevision: 1,
    runId: fixedRunId,
    fingerprint: envFingerprint,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    activeJob: null,
    handlers: {},
    summary: { total: 0, checked: 0, failed: 0, excluded: 0, unexamined: 0 },
    failures: [],
    archivedReports: []
  };

  const coordinator1 = new AcceptanceCoordinator(instance);
  await coordinator1.saveCheckpoint(initialCheckpoint);

  // New coordinator instance without explicit runId
  const coordinator2 = new AcceptanceCoordinator(instance);
  assert.notEqual(coordinator2.runId, fixedRunId, 'Initial runId should be newly generated');

  const loaded = await coordinator2.loadCheckpoint(envFingerprint);
  assert.equal(loaded.runId, fixedRunId);
  assert.equal(coordinator2.runId, fixedRunId, 'runId must be restored to coordinator instance');
});

test('job severity is authoritative and missing severity prevents unsafe continuation', () => {
  assert.equal(isFatalError({ code: 'export_failed', type: 'java.io.FileNotFoundException', fatal: true }), true);
  assert.equal(isFatalError({ code: 'recipe_capture', fatal: true }), true);
  assert.equal(isFatalError({ code: 'recipe_capture', fatal: false }), false);
  assert.equal(isFatalError({ code: 'export_failed', message: 'java.io.IOException: read failed' }), true);
  assert.equal(isFatalError({ code: 'recipe_capture', fatal: false, suppressed: [{ code: 'recipe_capture', fatal: false }] }), true);
  assert.equal(isFatalError({ code: 'recipe_capture', fatal: false, suppressedOmitted: 1 }), true);
  assert.equal(isFatalError(undefined), true);
});

test('AcceptanceCoordinator archives structure failures by controller identity', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-structure-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  const reportDir = path.join(instance, 'nesql', 'checks');
  await mkdir(reportDir, { recursive: true });
  const detailsDir = path.join(reportDir, 'job-structure-details');
  await mkdir(detailsDir, { recursive: true });
  const detail = Buffer.from(JSON.stringify({ format: 'nesql.failure', job: 'job-structure', target: { controller: 17000 }, index: 0, error: { code: 'structure_capture' } }));
  const detailPath = path.join(detailsDir, 'detail.json');
  await writeFile(detailPath, detail);
  const entry = { path: 'job-structure-details/detail.json', index: 0, bytes: detail.length, sha256: createHash('sha256').update(detail).digest('hex') };
  const report = Buffer.from(JSON.stringify({ rows: [{ controller: 17000, type: 'example.Controller', status: 'failed', failedStructures: [17000], failureFiles: [entry] }] }));
  const reportPath = path.join(reportDir, 'job-structure.json');
  await writeFile(reportPath, report);
  const coordinator = new AcceptanceCoordinator(instance);
  const archived = await coordinator.archiveReport({ id: 'job-structure', report: { path: reportPath } }, report, [{ controller: 17000, status: 'failed', failedStructures: [17000], failureFiles: [entry] }]);
  assert.equal(archived.files.length, 1);
  assert.equal(existsSync(archived.files[0].archivePath), true);
});

test('AcceptanceCoordinator enforces real mod SHA256 and protocol revision (U4)', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-sha256-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const token = 'f'.repeat(64);
  const session = randomUUID();

  // Create a dummy mod jar on disk
  const dummyJarPath = path.join(instance, 'dummy-mod.jar');
  await writeFile(dummyJarPath, Buffer.from('dummy mod jar content for sha test'));
  const dummyJarSha = createHash('sha256').update(await readFile(dummyJarPath)).digest('hex');

  const game = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('X-NESQL-Session', session);

    if (request.url === '/game') {
      response.end(JSON.stringify(gameState(instance, {
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14,
        sources: {
          valid: true,
          rows: [
            { id: 'nesql', valid: true, name: 'NESQL++', version: '0.15.0', path: dummyJarPath }
          ]
        }
      })));
    } else {
      response.statusCode = 404;
      response.end(JSON.stringify({ error: { code: 'not_found' } }));
    }
  });

  game.listen(0, '127.0.0.1');
  await once(game, 'listening');
  t.after(() => { game.closeAllConnections(); game.close(); });

  await writeFile(
    path.join(instance, 'nesql', 'connection.json'),
    JSON.stringify({ protocol: 1, port: game.address().port, token, session })
  );

  const coordinator = new AcceptanceCoordinator(instance);

  // Mismatched modSha256 must reject
  await assert.rejects(
    async () => withNativeEvidence(coordinator).runPlan({
      world: 'test-world', modSha256,
      modSha256: '0'.repeat(64),
      handlers: []
    }),
    /Loaded mod SHA256 mismatch/
  );

  // Mismatched protocol revision must reject
  await assert.rejects(
    async () => withNativeEvidence(coordinator).runPlan({
      world: 'test-world', modSha256,
      expectedRevision: 99,
      handlers: []
    }),
    /Game exporter protocol mismatch/
  );
});

test('AcceptanceCoordinator routes tooling exclusions and updates summary (U2, U3)', async t => {
  const instance = await fixture(path.join(os.tmpdir(), 'nesql-coord-tooling-'));
  t.after(() => rm(instance, { recursive: true, force: true }));
  await mkdir(path.join(instance, 'nesql'));
  const token = '9'.repeat(64);
  const session = randomUUID();
  let checksCalled = 0;

  const game = createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('X-NESQL-Session', session);

    if (request.url === '/game') {
      response.end(JSON.stringify(gameState(instance, {
        ready: true,
        game: 'Minecraft 1.7.10',
        world: { folder: 'test-world', name: 'Test World' },
        exporter: '0.15.0',
        revision: 14
      })));
    } else if (request.url === '/checks') {
      checksCalled++;
      response.end(JSON.stringify({ id: 'job-unexpected', state: 'queued' }));
    } else {
      response.statusCode = 404;
      response.end(JSON.stringify({ error: { code: 'not_found' } }));
    }
  });

  game.listen(0, '127.0.0.1');
  await once(game, 'listening');
  t.after(() => { game.closeAllConnections(); game.close(); });

  await writeFile(
    path.join(instance, 'nesql', 'connection.json'),
    JSON.stringify({ protocol: 1, port: game.address().port, token, session })
  );

  const coordinator = new AcceptanceCoordinator(instance);
  const plan = {
    world: 'test-world', modSha256,
    handlers: [
      {
        id: 'category_profiler_exclusion_test_ProfilerRecipeHandler',
        name: 'Profiler',
        classification: 'tooling',
        implementationStatus: 'excluded_justified',
        reason: 'Approved tooling exclusion'
      }
    ]
  };

  const checkpoint = await withNativeEvidence(coordinator).runPlan(plan);
  assert.equal(checksCalled, 0, 'Tooling exclusion must not be dispatched to /checks');
  assert.equal(checkpoint.handlers['category_profiler_exclusion_test_ProfilerRecipeHandler'].status, 'excluded');
  assert.equal(checkpoint.summary.total, 1);
  assert.equal(checkpoint.summary.excluded, 1);
  assert.equal(checkpoint.summary.checked, 0);
  assert.equal(checkpoint.summary.failed, 0);
});
