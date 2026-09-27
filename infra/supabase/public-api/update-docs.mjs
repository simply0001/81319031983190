import { readFile, writeFile } from 'node:fs/promises';
import assert from 'node:assert/strict';

const contract=JSON.parse(await readFile(new URL('./boards.json',import.meta.url),'utf8'));
const file=new URL('../developer/docs.html',import.meta.url);
const docs=await readFile(file,'utf8');
const start='<!-- boards-endpoints:start -->',end='<!-- boards-endpoints:end -->';
const descriptions={
  settings:'Feature/request switches and your board push preference.',
  list:'Board objects in items; joined by default, or explore for public boards.',
  get:'One board with your role, membership settings, member count, access revision and pending ownership offer.',
  feed:'Top-level notes in items; newest, activity or popular ordering.',
  replies:'The parent post and chronological replies in items.',
  post:'One note or reply. reveal explicitly reveals a spoiler.',
  preview:'{ svg } static preview, or null while a spoiler is hidden.',
  asset:'{ mime, data } with base64 image bytes. Current audience is checked on every read.',
  branding_preview:'{ svg } for the drawn icon or cover.',
  drafts:'Your current cloud draft heads in items, including conflicting versions.',
  inbox:'Grouped activity in items, with event counts and read markers; no note text.',
  invitations:'Your unaccepted direct invitations in items.',
  proposals:'Your board proposals and their decisions in items.',
  notices:'Your restrictions, actions and appeals arrays, each paginated with the same cursor.',
  members:'Members in items, including display_name and avatar_path.',
  management:'Board, requests, invitations, restrictions, appeals and reports. Each queue uses the same cursor. Reports omit reporter identity.',
  outgoing_invitations:'Invitations in items. Members see only those they created; owner/moderators see all. No code hashes or reports.',
  history:'Retained text/content versions in items, for board moderators only.',
  stationery:'Catalogue items with available and owned flags; includes permanently owned inactive items.',
  propose:'Submit a proposal for dashboard approval; returns id. Does not create a board immediately.',
  join:'Join a public board or request approval; returns board_id and status (joined or requested).',
  accept_invitation:'Accept a direct invitation; returns board_id and status.',
  join_code:'Join or request approval using a code; returns board_id and status.',
  decline_invitation:'Decline your own direct invitation.',
  decide_join:'Approve or reject a membership request as a board moderator.',
  invite:'Create a direct invitation; returns id. The recipient must accept.',
  create_code:'Create a reusable invitation; returns id and the code. Save the returned code securely.',
  revoke_invitation:'Revoke an invitation or code you are allowed to manage.',
  leave:'Leave a board. An owner must first transfer ownership or archive it.',
  preferences:'Change your per-board mute and push settings.',
  push_preference:'Change your global Boards push preference.',
  update_board:'Update board details, joining/invitation rules or branding accent as the owner.',
  archive:'Archive or reopen your board.',
  transfer:'Offer ownership to a current member; they must accept.',
  set_moderator:'Appoint or remove a board moderator as the owner.',
  accept_transfer:'Accept an ownership offer addressed to you.',
  publish:'Publish a note or reply; returns id, board_id and thread_id. Drawings are immutable after publishing.',
  edit:'Edit your note or reply text; preserves drawing and adds an edited marker.',
  delete_post:'Remove content while preserving a placeholder and replies. Moderating another author requires a reason and boards:moderate.',
  spoiler:'Set a spoiler flag. Moderating another author requires a reason and boards:moderate.',
  react:'Set or clear your Yeah reaction; repeated true values do not add reactions.',
  draw_branding:'Set a drawn icon or cover as the owner.',
  save_draft:'Save an immutable revision; returns revision_id and conflict. Does not publish.',
  discard_draft:'Discard one of your own draft revisions.',
  report:'Report a note/reply, or board branding when post_id is omitted; returns case_id.',
  resolve_report:'Resolve or dismiss a local board report. Reports against board staff remain dashboard-only.',
  restrict:'Issue a board mute or ban; returns case_id. Cannot issue a global suspension.',
  revoke_restriction:'Revoke a local board restriction. Central restrictions cannot be revoked here.',
  appeal:'Appeal your local board moderation case; returns case_id. Central appeals go through Discord.',
  resolve_appeal:'Resolve or dismiss a board appeal; revoking the restriction is a separate action.',
  read_event:'Mark your activity event read.',
  read_thread:'Mark your activity in a thread read, or all activity for the board when thread_id is omitted.',
  buy_stationery:'Buy permanent stationery ownership with your tokens, including while subscribed; returns stationery_id and owned.',
};
const escape=text=>text.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;');
const rows=Object.entries(contract).map(([name,spec])=>{
  assert.ok(descriptions[name],name);
  const fields=spec.fields.map(field=>`<code>${field}${spec.required.includes(field)?'':'?'}</code>`).join(', ')||'<code>{}</code>';
  return `                  <tr><td><code>boards.${name}</code><br><a class="endpoint-example" href="#example-boards-${name}">Kotlin / JavaScript</a></td><td><code>${spec.scope}</code></td><td>${fields}</td><td>${escape(descriptions[name])}${spec.page?' Returns next_cursor.':''}</td></tr>`;
}).join('\n');
assert.ok(docs.includes(start)&&docs.includes(end),'Missing generated-table markers');
const updated=docs.slice(0,docs.indexOf(start)+start.length)+'\n'+rows+'\n                  '+docs.slice(docs.indexOf(end));
if(process.argv.includes('--check')) assert.equal(docs,updated,'Run public-api/update-docs.mjs to update the endpoint reference');
else await writeFile(file,updated);
