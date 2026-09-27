import json, re, html
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / 'developer/docs.html'
CONTRACT = json.loads((ROOT / 'public-api/boards.json').read_text(encoding='utf-8'))
CORE = json.loads((ROOT / 'public-api/core-examples.json').read_text(encoding='utf-8'))
WORKFLOWS = json.loads((ROOT / 'public-api/workflow-examples.json').read_text(encoding='utf-8'))
U = '97000000-0000-4000-8000-000000000001'
DRAWING = {'version':1,'width':800,'height':600,'strokes':[{'pen':'pixel','color':'#222222','size':4,'points':[[10,10],[40,30]]}]}
VALUES = {'accept':True,'approve':True,'blocked':True,'block_messages':True,'enabled':True,'archived':True,'moderator':True,'spoiler':True,'yeah':True,'name':'Handheld chat','title':'Friends','body':'Hello everyone!','visibility':'public','kind':'icon','reason':'Explain the reason for this action.','code':'12345678','user_ids':[U],'member_ids':[U],'drawing':DRAWING,'payload':{'body':'My unfinished note'},'stationery_id':'plain'}
EXTRAS = {'messages.send':{'body':'Hello!'},'boards.publish':{'body':'Hello everyone!'},'boards.preferences':{'muted':True},'boards.update_board':{'description':'A place to share handheld games.'},'boards.list':{'scope':'explore','limit':30},'boards.feed':{'sort':'newest','limit':30},'boards.restrict':{'kind':'mute'},'boards.join_code':{'code':'REPLACE_WITH_INVITATION_CODE'}}
def sample(endpoint,fields):
    result={}
    for field in fields:
        if field in VALUES:result[field]=VALUES[field]
        elif field.endswith('_id') or field=='id':result[field]=U
        else:raise ValueError((endpoint,field))
    result.update(EXTRAS.get(endpoint,{}))
    return result

def esc(s):return html.escape(s,quote=False)
def block(s):return '<pre class="code-block"><code>'+esc(s)+'</code></pre>'
def pair(js,kt):return '<details class="language-example"><summary>JavaScript</summary>'+block(js)+'</details><details class="language-example"><summary>Kotlin (Android / JVM)</summary>'+block(kt)+'</details>'
def detail(ident,title,body):return '<details class="api-example" id="'+ident+'"><summary>'+esc(title)+'</summary>'+body+'</details>'
entries=[]
for x in CORE:entries.append((x['name'],x['section'],x['scope'],x['fields']))
for name,spec in CONTRACT.items():entries.append(('boards.'+name,'boards-api',spec['scope'],spec['required']))
entries += [('boards.artwork_upload','boards-api','boards:manage',['board_id','kind','operation_id','image']),('boards.prepare_branding','boards-api','boards:manage',['board_id','kind','operation_id','source_hash'])]
VALUES.update(image='REPLACE_WITH_BASE64_IMAGE_BYTES',source_hash='REPLACE_WITH_LOWERCASE_SHA256')
parts=['<section class="panel" id="examples"><h2>Kotlin &amp; JavaScript examples</h2><p>Open a topic, then choose a language. Examples do not execute here. Replace sample UUIDs and values with IDs returned by your API calls. Only run mutations after the corresponding user action. Kotlin examples use Android’s <code>org.json</code> (add that dependency on JVM) and blocking HTTP: call them on a background/IO thread, never the Android main thread. JavaScript examples use browser fetch and Web Crypto. Start with the shared HTTP helpers.</p>']
parts.append('<details class="examples-library"><summary>Browse Kotlin and JavaScript examples</summary>')
for w in WORKFLOWS:
    parts.append(detail('example-'+w['id'],w['title'],'<p>'+w['description']+'</p>'+pair(w['javascript'],w['kotlin'])))
parts.append('<h3>Every endpoint</h3><p>Use <a href="#example-http">the shared HTTP helpers</a>. Fields shown are a minimal useful request; the endpoint reference lists optional fields, response shapes and access rules. Keep the entire request and operation ID for retries, including across app restarts.</p>')
for name,section,scope,fields in entries:
    body=sample(name,fields)
    encoded=json.dumps(body,indent=2)
    js='const request = '+encoded+';\n'
    kt='val request = JSONObject("""\n'+encoded+'\n""")\n'
    for key in ['operation_id','client_operation_id']:
        if key in fields:
            js+=f'// Generate once per user action; persist request before sending.\nrequest.{key} = crypto.randomUUID();\n'
            kt+=f'// Generate once per user action; persist request before sending.\nrequest.put("{key}", java.util.UUID.randomUUID().toString())\n'
    js+=f'const result = await api("{name}", request);\n// Render result in your app; handle ApiError without discarding pending work.'
    kt+=f'val result = api("{name}", request)\n// Render result in your app; handle ApiError without discarding pending work.'
    parts.append(detail('example-'+name.replace('.','-'),name,f'<p>Scope: <code>{esc(scope)}</code>. <a href="#{section}">Endpoint reference</a>.</p>'+pair(js,kt)))
parts.append('</details></section>')
s=DOCS.read_text(encoding='utf-8')
a='<!-- api-examples:start -->';b='<!-- api-examples:end -->'
s=s[:s.index(a)+len(a)]+'\n'+'\n'.join(parts)+'\n          '+s[s.index(b):]
# Link endpoint names without adding columns or disturbing the contract's layout.
for name,section,scope,fields in entries:
    plain=f'<td><code>{name}</code></td>'
    linked=f'<td><code>{name}</code><br><a class="endpoint-example" href="#example-{name.replace(".","-")}">Kotlin / JavaScript</a></td>'
    s=s.replace(plain,linked)
# Each reference section gets a relevant workflow link, even when it has no code block.
links={'overview':'http','getting-started':'oauth','connect':'oauth','redirect-uris':'oauth','scopes':'last-seen','tokens':'tokens','calling':'http','endpoints':'http','boards-api':'boards','blocking-api':'blocking','objects':'last-seen','errors':'http','rate-limits':'http','sync':'pagination','realtime':'presence','media':'media','scope-changes':'oauth','idempotency':'boards','first-party':'http'}
for section,target in links.items():
    marker=f'<p class="section-examples"><a href="#example-{target}">Kotlin &amp; JavaScript examples →</a></p>'
    pat=r'(<h2 id="'+section+r'">.*?</h2>)(?!\s*<p class="section-examples">)'
    s=re.sub(pat,lambda m:m[1]+'\n            '+marker,s)
DOCS.write_text(s,encoding='utf-8',newline='\n')
print(f'Generated {len(entries)} endpoint pairs and {len(WORKFLOWS)} workflow pairs.')
