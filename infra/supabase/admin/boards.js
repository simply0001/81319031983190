(() => {
  "use strict";
  const node = (tag, text, className) => {
    const el = document.createElement(tag);
    if (text !== undefined) el.textContent = text;
    if (className) el.className = className;
    return el;
  };
  const label = (text, control) => { const el = node("label", undefined, "field"); el.append(node("span", text), control); return el; };
  const input = (value = "", type = "text", max = 1000) => { const el = node("input"); el.type = type; el.value = value ?? ""; el.maxLength = max; return el; };
  const area = (value = "", max = 1000) => { const el = node("textarea"); el.value = value ?? ""; el.maxLength = max; el.rows = 4; return el; };
  const select = (options, value) => { const el = node("select"); options.forEach(([key, title]) => { const option = node("option", title); option.value = key; el.append(option); }); el.value = value; return el; };
  const checkbox = value => { const el = input("", "checkbox"); el.checked = !!value; return el; };
  const section = (root, title, description) => { const el = node("section", undefined, "panel stack"); el.append(node("h2", title)); if(description) el.append(node("p", description, "panel-sub")); root.append(el); return el; };
  const when = value => { const date = new Date(value); return Number.isNaN(date.getTime()) ? "Unknown date" : date.toLocaleString(); };
  const auditTitles = {
    create:"Created Board", permanent_delete:"Permanently deleted Board", update_board:"Updated Board settings",
    archive:"Changed Board archive status", transfer:"Offered Board ownership", accept_transfer:"Accepted Board ownership",
    set_moderator:"Changed moderator role", decide_join:"Decided membership request",
    delete_post:"Removed note", spoiler:"Changed spoiler label", draw_branding:"Updated Board artwork",
    resolve_report:"Resolved report", resolve_appeal:"Resolved appeal",
    staff_settings:"Changed Boards feature settings", staff_filter:"Updated word filter",
    staff_filter_resolve:"Reviewed filtered text", staff_stationery:"Updated stationery",
    review_access:"Viewed Board", member_review:"Viewed members", management_review:"Viewed Board management",
    history_review:"Viewed note history", branding_review:"Viewed Board artwork",
    retained_artwork_review:"Viewed retained artwork", report_review:"Viewed report",
  };
  const auditTitle = entry => entry.action === "restrict"
    ? (entry.board_id ? "Restricted Board participation" : "Suspended participation across Boards")
    : entry.action === "revoke_restriction"
      ? (entry.board_id ? "Revoked Board restriction" : "Revoked global Board suspension")
      : auditTitles[entry.action] || entry.action.replaceAll("_"," ").replace(/^./,letter=>letter.toUpperCase());
  const auditEntry = entry => {
    const card = node("article",undefined,"board-audit-entry");
    card.append(node("h3",auditTitle(entry)));
    card.append(node("p",`${when(entry.created_at)} · ${entry.board_id ? entry.board_name || "Deleted Board" : "All Boards"} · ${entry.central ? "PocketPass staff" : "Board team"}`,"board-audit-meta"));
    card.append(node("p",`By ${entry.actor_name || (entry.actor_id ? "Former account" : "System")}${entry.subject_id ? ` · Affected: ${entry.subject_name || "Former account"}` : ""}`));
    if(entry.reason) card.append(node("p",`Reason: ${entry.reason}`));
    const details = node("details"); details.append(node("summary","Show record IDs"));
    for(const [label,value] of [["Audit ID",entry.id],["Board ID",entry.board_id],["Actor ID",entry.actor_id],["Affected account ID",entry.subject_id],["Note ID",entry.post_id]]) {
      if(value) { const line=node("p",`${label}: `); line.append(node("code",value)); details.append(line); }
    }
    card.append(details);
    return card;
  };

  async function render({root, rpc, can, accountId, upload}) {
    root.replaceChildren();
    const status = node("p", "", "hint"); status.setAttribute("role", "status"); root.append(status);
    const query = (operation, args = {}) => rpc("boards_query", {p_operation: operation, p_args: {review:true,staff_review:true,...args}});
    const mutate = async (operation, args) => {
      args = {review:true,staff_review:true,...args};
      const fingerprint = JSON.stringify([accountId, operation, args]);
      const storageKey = `pp_board_operation:${fingerprint}`;
      let operationId = sessionStorage.getItem(storageKey);
      if(!operationId) { operationId = crypto.randomUUID(); sessionStorage.setItem(storageKey, operationId); }
      const result = await rpc("boards_mutate", {p_operation: operation, p_args: args, p_operation_id: operationId});
      sessionStorage.removeItem(storageKey);
      return result;
    };
    const button = (parent, title, action, green = false) => {
      const el = node("button", title, `btn btn-small ${green ? "btn-green" : "btn-grey"}`); el.type = "button";
      el.addEventListener("click", async () => {
        el.disabled = true; status.textContent = "";
        try { await action(); } catch(error) { status.textContent = error.message || "The request failed. Retry to use the same operation."; }
        finally { el.disabled = false; }
      }); parent.append(el); return el;
    };
    const save = (parent, title, operation, args, after) => button(parent, title, async()=>{
      const result = await mutate(operation, typeof args === "function" ? args() : args);
      status.textContent = result?.case_id ? `${title} succeeded. Case ID: ${result.case_id}.` : "Saved.";
      if(after) await after(result);
    }, true);
    const paging = (parent, load) => {
      let offset = 0; const rows = node("div", undefined, "stack"); const controls = node("div", undefined, "actions"); parent.append(rows, controls);
      button(controls, "Previous", async()=>{ offset = Math.max(0,offset-50); await load(rows,offset); });
      button(controls, "Next", async()=>{ offset += 50; await load(rows,offset); });
      return load(rows,offset);
    };
    const reason = (parent, title = "Reason shown to the member") => { const control = area(); control.required = true; parent.append(label(title,control)); return () => { if(!control.value.trim()) throw new Error("A reason is required."); return control.value.trim(); }; };

    if(can("board_settings")) {
      const settings = await query("staff_settings");
      const panel = section(root,"Feature controls","Disabling Boards preserves stored boards and drafts. No boards are seeded automatically.");
      const enabled = checkbox(settings.enabled), requests = checkbox(settings.requests_open);
      const submissions = input(settings.submissions_per_minute,"number"), reactions = input(settings.reactions_per_minute,"number"), invitations = input(settings.invitations_reports_per_minute,"number");
      panel.append(label("Enable Boards",enabled),label("Accept board proposals",requests),label("Post/reply submissions per account per minute",submissions),label("Reaction changes per account per minute",reactions),label("Invitations and reports per account per minute",invitations));
      save(panel,"Save feature settings","staff_settings",()=>({enabled:enabled.checked,requests_open:requests.checked,submissions_per_minute:Number(submissions.value),reactions_per_minute:Number(reactions.value),invitations_reports_per_minute:Number(invitations.value)}));
    }
    if(can("board_filters")) {
      const panel=section(root,"Word and phrase filters","Literal, case-insensitive matches on new submissions and edits. Bios always reject a match; private messages are excluded.");
      const toolbar=node("div",undefined,"board-filter-toolbar");
      const count=node("p",undefined,"board-filter-count");
      button(toolbar,"Add rule",()=>showEditor(),true);
      toolbar.prepend(count);
      const search=input("","search",100); search.placeholder="Find a word or phrase";
      const list=node("div",undefined,"board-filter-list");
      const editor=node("div",undefined,"board-filter-editor");
      panel.append(toolbar,label("Search current rules",search),list,editor);
      let rules=[];
      const showList=()=>{
        const term=search.value.trim().toLocaleLowerCase();
        const matches=rules.filter(rule=>rule.phrase.toLocaleLowerCase().includes(term));
        count.textContent=`${rules.length} saved ${rules.length===1?"rule":"rules"} · ${rules.filter(rule=>rule.enabled).length} active`;
        list.replaceChildren();
        if(!matches.length) {
          list.append(node("p",rules.length?"No rules match this search.":"No rules saved yet. Add a word or phrase to get started.","hint"));
          return;
        }
        matches.forEach(rule=>{
          const item=node("article",undefined,"board-filter-row");
          const content=node("div",undefined,"board-filter-row-content");
          const title=node("h3",rule.phrase);
          const state=node("span",rule.enabled?"Active":"Disabled",`board-filter-state ${rule.enabled?"is-active":"is-disabled"}`);
          const heading=node("div",undefined,"board-filter-row-heading"); heading.append(title,state);
          content.append(heading,node("p",rule.action==="block"?"Block with a reason":"Censor and send for staff review","board-filter-action"),node("p",rule.reason,"board-filter-reason"));
          item.append(content);
          button(item,"Edit",()=>showEditor(rule));
          list.append(item);
        });
      };
      const reload=async()=>{ rules=await query("staff_filters"); showList(); };
      const showEditor=(rule,confirmation="")=>{
        editor.replaceChildren();
        const form=node("div",undefined,"board-filter-form stack");
        form.append(node("h3",rule?"Edit rule":"Add rule"));
        const phrase=input(rule?.phrase||"","text",100);
        const action=select([["censor","Censor and send for staff review"],["block","Block with a reason"]],rule?.action||"censor");
        const explain=area(rule?.reason||"",500); explain.required=true;
        const enabled=checkbox(rule?.enabled!==false);
        form.append(label("Word or phrase",phrase),label("Action",action),label("Reason shown when text is rejected (required)",explain),label("Enabled",enabled));
        const feedback=node("p",confirmation,"board-filter-feedback"); feedback.setAttribute("role","status");
        const actions=node("div",undefined,"actions");
        button(actions,rule?"Save changes":"Save new rule",async()=>{
          const word=phrase.value.trim(), why=explain.value.trim();
          feedback.classList.remove("is-error");
          if(!word || !why) { feedback.textContent="Enter both a word or phrase and a reason before saving."; feedback.classList.add("is-error"); return; }
          let result;
          try {
            result=await mutate("staff_filter",{...(rule?.id?{id:rule.id}:{}),phrase:word,action:action.value,reason:why,enabled:enabled.checked});
          } catch(error) { feedback.textContent=error.message||"The rule could not be saved. Try again."; feedback.classList.add("is-error"); return; }
          search.value="";
          try {
            await reload();
            showEditor(rules.find(saved=>saved.id===result.id)||{id:result.id,phrase:word,action:action.value,reason:why,enabled:enabled.checked},"Saved. This rule appears in the current list above.");
          } catch(error) {
            showEditor({id:result.id,phrase:word,action:action.value,reason:why,enabled:enabled.checked},"Saved, but the current list could not reload. Refresh the page to see it.");
          }
        },true);
        button(actions,"Close",()=>editor.replaceChildren());
        form.append(actions,feedback); editor.append(form);
        phrase.focus();
      };
      search.addEventListener("input",showList);
      await reload();
    }
    if(can("board_requests")) {
      const panel = section(root,"Board proposals");
      await paging(panel,async(rows,offset)=>{
        rows.replaceChildren(); const proposals = await query("staff_requests",{offset,limit:50});
        if(!proposals.length) rows.append(node("p","No pending proposals."));
        proposals.forEach(proposal=>{
          const item = section(rows,proposal.name,`${proposal.visibility} · Requested by ${proposal.author_id}`);
          item.append(node("p",proposal.description),node("pre",proposal.rules,"board-prose"));
          const rejection = reason(item,"Rejection reason");
          save(item,"Approve","staff_proposal",{id:proposal.id,approve:true},()=>item.remove());
          save(item,"Reject","staff_proposal",()=>({id:proposal.id,approve:false,reason:rejection()}),()=>item.remove());
        });
      });
    }
    const detailRoot = node("div",undefined,"stack");
    if(can("boards")) {
      const panel = section(root,"Create a board");
      const name=input("","text",60), owner=input("","text",36), description=area(), rules=area("",4000), visibility=select([["public","Public"],["private","Private"]],"public");
      panel.append(label("Name",name),label("Owner account ID (blank uses your account)",owner),label("Description",description),label("Rules",rules),label("Visibility",visibility));
      save(panel,"Create board","staff_create",()=>({name:name.value.trim(),description:description.value,rules:rules.value,visibility:visibility.value,...(owner.value.trim()?{user_id:owner.value.trim()}:{})}),()=>{name.value="";description.value="";rules.value="";});
      const list = section(root,"Boards");
      await paging(list,async(rows,offset)=>{
        rows.replaceChildren(); const boards = await query("staff_boards",{offset,limit:50});
        if(!boards.length) rows.append(node("p","No boards on this page."));
        boards.forEach(board=>button(rows,`${board.name} · ${board.visibility}${board.archived?" · Archived":""}`,()=>openBoard(board.id,{scrollToDetail:true})));
      });
    }
    if(!can("boards") && (can("board_members") || can("board_content") || can("board_delete"))) {
      const list=section(root,"Boards");
      await paging(list,async(rows,offset)=>{
        rows.replaceChildren();const boards=await query("staff_boards",{offset,limit:50});
        boards.forEach(board=>button(rows,`${board.name} · ${board.visibility}`,()=>openBoard(board.id,{scrollToDetail:true})));
      });
    }
    root.append(detailRoot);

    async function openBoard(id, {openArtworkKind,scrollToDetail}={}) {
      const board = await query("staff_board",{board_id:id});
      detailRoot.replaceChildren();
      const panel = section(detailRoot,board.name,`${board.visibility} board · ${board.id}`);
      panel.append(node("p",board.description),node("pre",board.rules,"board-prose"));
      if(scrollToDetail) detailRoot.scrollIntoView({behavior:window.matchMedia("(prefers-reduced-motion: reduce)").matches?"auto":"smooth",block:"start"});
      if(can("boards")) {
      const name=input(board.name,"text",60), description=area(board.description), rules=area(board.rules,4000), accent=select(["blue","green","pink","purple","orange","teal"].map(x=>[x,x]),board.accent);
      const joining=select([["open","Anyone can join"],["approval","Approve membership requests"]],board.join_policy);
      const codes=select([["open","Codes grant entry"],["approval","Codes request approval"]],board.code_policy), memberInvites=checkbox(board.members_can_invite);
      panel.append(label("Name",name),label("Description",description),label("Rules",rules),label("Accent",accent),label("Joining",joining),label("Invitation codes",codes),label("Allow members to invite",memberInvites));
      save(panel,"Save board","update_board",()=>({board_id:id,name:name.value,description:description.value,rules:rules.value,accent:accent.value,join_policy:joining.value,code_policy:codes.value,members_can_invite:memberInvites.checked}));
      save(panel,board.archived?"Reopen board":"Archive board","archive",{board_id:id,archived:!board.archived},()=>openBoard(id));
      if(board.visibility === "public") button(panel,"Make private",async()=>{
        if(!window.confirm("Make this board private? It cannot become public again.")) return;
        await mutate("update_board",{board_id:id,visibility:"private"}); await openBoard(id);
      });
      }
      if(can("boards") || can("board_content")) {
        const artwork=section(detailRoot,"Board artwork","Open an item to see its current image or import a replacement.");
        for(const kind of ["icon","cover"]) {
          const disclosure=node("details",undefined,"board-artwork-disclosure");
          const summary=node("summary",`${kind==="icon"?"Icon":"Cover"} · ${board[`${kind}_asset_id`]||board[`${kind}_drawing`]?"View current":"No artwork"}${can("boards")&&upload?" / import":""}`);
          const body=node("div",undefined,"board-artwork-body stack");
          const previewRoot=node("div"); body.append(previewRoot);
          disclosure.append(summary,body); artwork.append(disclosure);
          let previewLoaded=false;
          disclosure.addEventListener("toggle",async()=>{
            if(!disclosure.open || previewLoaded) return;
            previewLoaded=true;
            if(!board[`${kind}_asset_id`] && !board[`${kind}_drawing`]) { previewRoot.append(node("p",`No ${kind} is set for this Board.`,"hint")); return; }
            previewRoot.replaceChildren(node("p","Loading artwork…","hint"));
            try {
              const preview=node("img"); preview.alt=`Board ${kind}`; preview.className=`board-artwork-preview board-artwork-${kind}`;
              if(board[`${kind}_asset_id`]) {
                const image=await query("asset",{asset_id:board[`${kind}_asset_id`]}); preview.src=`data:${image.mime};base64,${image.data}`;
              } else {
                const image=await query("branding_preview",{board_id:id,kind}); preview.src=`data:image/svg+xml;charset=utf-8,${encodeURIComponent(image.svg)}`;
              }
              previewRoot.replaceChildren(preview);
            } catch(error) { previewRoot.replaceChildren(node("p",error.message||"Could not load the current artwork.","board-inline-feedback is-error")); previewLoaded=false; }
          });
          if(can("boards") && upload) {
            const media=input("","file"); media.accept="image/png,image/jpeg,image/webp,image/gif";
            body.append(label(`Import ${kind}`,media));
            let operationId=crypto.randomUUID(); media.addEventListener("change",()=>operationId=crypto.randomUUID());
            const feedback=node("p",undefined,"board-inline-feedback"); feedback.setAttribute("role","status");
            button(body,`Upload ${kind}`,async()=>{
              feedback.textContent=""; feedback.classList.remove("is-error");
              try {
                const file=media.files[0]; if(!file) throw new Error("Choose an image first.");
                if(file.size>10*1024*1024) throw new Error("Choose an image smaller than 10 MB.");
                const image=await new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(reader.result.split(",")[1]);reader.onerror=reject;reader.readAsDataURL(file);});
                await upload({board_id:id,kind,operation_id:operationId,image});
              } catch(error) { feedback.textContent=error.message||"The artwork could not be uploaded. Try again."; feedback.classList.add("is-error"); return; }
              await openBoard(id,{openArtworkKind:kind});
            });
            body.append(feedback);
          }
          if(openArtworkKind===kind) disclosure.open=true;
        }
      }
      if(can("board_members")) {
        const management = {requests:[],restrictions:[],appeals:[],invitations:[]};
        let offset=0;
        do { const page=await query("management",{board_id:id,offset,limit:100}); for(const key of Object.keys(management)) management[key].push(...(page[key]||[])); offset=page.next_offset; } while(offset!=null);
        const members=[];let memberCursor=null;
        do { const page=await query("members",{board_id:id,review:true,limit:100,...(memberCursor?{cursor:memberCursor}:{})});members.push(...page.items);memberCursor=page.cursor; } while(memberCursor);
        const list = section(detailRoot,"Membership and staff");
        const owner=input("","text",36); list.append(label("Replacement owner account ID (must already be a member)",owner));
        save(list,"Appoint replacement owner","transfer",()=>({board_id:id,user_id:owner.value.trim()}));
        members.forEach(member=>{
          const item = section(list,`${member.display_name} · ${member.role}`,member.user_id);
          if(member.role !== "owner") save(item,member.role === "moderator"?"Remove moderator":"Appoint moderator","set_moderator",{board_id:id,user_id:member.user_id,moderator:member.role!=="moderator"});
          const explain = reason(item);
          save(item,"Mute participation","restrict",()=>({board_id:id,user_id:member.user_id,kind:"mute",reason:explain()}));
          save(item,"Ban from board","restrict",()=>({board_id:id,user_id:member.user_id,kind:"ban",reason:explain()}));
        });
        (management.requests||[]).forEach(request=>{
          const item=section(list,`Membership request: ${request.display_name}`);
          save(item,"Accept","decide_join",{board_id:id,user_id:request.user_id,approve:true},()=>item.remove());
          save(item,"Decline","decide_join",{board_id:id,user_id:request.user_id,approve:false},()=>item.remove());
        });
        (management.appeals||[]).forEach(appeal=>{
          const item=section(list,`Appeal · Case ${appeal.case_id}`,appeal.body), explanation=reason(item,"Appeal resolution");
          save(item,"Resolve appeal","resolve_appeal",()=>({board_id:id,id:appeal.id,reason:explanation(),status:"resolved"}),()=>item.remove());
          save(item,"Dismiss appeal","resolve_appeal",()=>({board_id:id,id:appeal.id,reason:explanation(),status:"dismissed"}),()=>item.remove());
        });
        (management.invitations||[]).forEach(invitation=>{
          const item=section(list,invitation.recipient_id?"Direct invitation":"Invitation code",invitation.id);
          save(item,"Revoke invitation","revoke_invitation",{board_id:id,id:invitation.id},()=>item.remove());
        });
        const activeRestrictions=section(list,"Board restrictions","Case IDs are shown here for mutes and bans on this Board.");
        if(!management.restrictions.length) activeRestrictions.append(node("p","No Board restrictions."));
        (management.restrictions||[]).forEach(restriction=>{
          const member=members.find(row=>row.user_id===restriction.user_id);
          const kind=restriction.kind==="ban"?"Ban":"Mute";
          const item=section(activeRestrictions,`${kind}: ${member?.display_name || "PocketPass member"}`,`Case ID: ${restriction.id}`);
          item.append(node("p",`Account ID: ${restriction.user_id}`),node("p",`Reason: ${restriction.reason}`),node("p",`Applied ${when(restriction.created_at)}${restriction.expires_at?` · Expires ${when(restriction.expires_at)}`:""}`));
          button(item,"Revoke restriction",async()=>{
            if(!window.confirm(`Revoke this ${kind.toLowerCase()}?\nCase ID: ${restriction.id}`)) return;
            await mutate("revoke_restriction",{board_id:id,id:restriction.id});
            item.remove(); status.textContent=`${kind} revoked. Case ID: ${restriction.id}.`;
          },true);
        });
      }
      if(can("board_content")) {
        const notes = section(detailRoot,"Content review","Private board access and moderation actions are recorded. Drafts are never available here.");
        let cursor = null;
        const rows = node("div",undefined,"stack"); notes.append(rows);
        const load = async()=>{
          const page=await query("feed",{board_id:id,review:true,limit:30,...(cursor?{cursor}:{})}); cursor=page.cursor;
          for(const post of page.items) await note(rows,post);
        };
        button(notes,"Load more notes",load); await load();
      }
      if(can("board_delete")) {
        const danger=section(detailRoot,"Permanent deletion","Deletes the board's notes, replies, branding, drafts, reports, and retained content. Only a minimal action audit remains.");
        const explanation=reason(danger,"Reason for deletion");
        button(danger,"Permanently delete board",async()=>{
          const why=explanation(); if(window.prompt(`Type ${board.name} to permanently delete this board.`)!==board.name) return;
          await mutate("staff_delete",{board_id:id,reason:why}); detailRoot.replaceChildren(); status.textContent="Board permanently deleted.";
        });
      }
    }
    async function note(parent,post) {
      const panel=section(parent,post.author_name||"Deleted account",`${post.id}${post.edited_at?" · Edited":""}`);
      if(post.removed) panel.append(node("p","This note was removed."));
      else if(post.spoiler) button(panel,"Reveal spoiler for review",async()=>{const revealed=await query("post",{post_id:post.id,review:true,reveal:true});panel.remove();await note(parent,{...revealed,spoiler:false});});
      else {
        panel.append(node("p",post.body,"board-prose"));
        if(post.has_drawing) {
          const preview=await query("preview",{post_id:post.id,review:true,reveal:true});
          if(preview.svg) {const img=node("img");img.alt="Drawn note";img.className="board-note-preview";img.src=`data:image/svg+xml;charset=utf-8,${encodeURIComponent(preview.svg)}`;panel.append(img);}
        }
      }
      const explain=reason(panel,"Moderation reason");
      if(!post.removed) save(panel,"Remove note","delete_post",()=>({board_id:post.board_id,post_id:post.id,reason:explain()}),()=>panel.remove());
      save(panel,post.spoiler?"Remove spoiler label":"Mark spoiler","spoiler",()=>({board_id:post.board_id,post_id:post.id,spoiler:!post.spoiler,reason:explain()}));
      button(panel,"Show retained versions",async()=>{
        const versions=await query("history",{board_id:post.board_id,post_id:post.id});
        versions.forEach(version=>{const item=section(panel,"Previous version",`Retained until ${version.expires_at}`);item.append(node("p",version.content.body||"Drawn note","board-prose"));});
      });
      if(!post.thread_id) {
        let cursor=null; const replies=node("div",undefined,"stack");panel.append(replies);
        button(panel,"Load replies",async()=>{const page=await query("replies",{post_id:post.id,review:true,limit:30,...(cursor?{cursor}:{})});cursor=page.cursor;for(const reply of page.items) await note(replies,reply);});
      }
    }
    if(can("board_content")) {
      const panel=section(root,"Reports and filtered text");
      await paging(panel,async(rows,offset)=>{
        rows.replaceChildren(); const data=await query("staff_reports",{offset,limit:50});
        if(!data.reports.length&&!data.filters.length) rows.append(node("p","No open reports or filter reviews on this page."));
        for(const report of data.reports) {
          const item=section(rows,`Report ${report.id}`,report.reason);
          if(report.post_id) button(item,"Review reported note",async()=>{const post=await query("post",{post_id:report.post_id,review:true});await note(item,post);});
          else button(item,"Review board branding",()=>openBoard(report.board_id));
          const resolution=reason(item,"Resolution");
          save(item,"Resolve","resolve_report",()=>({board_id:report.board_id,id:report.id,reason:resolution(),status:"resolved"}),()=>item.remove());
          save(item,"Dismiss","resolve_report",()=>({board_id:report.board_id,id:report.id,reason:resolution(),status:"dismissed"}),()=>item.remove());
        }
        data.filters.forEach(review=>{
          const item=section(rows,`Filter review · ${review.context}`);
          item.append(node("h3","Original (staff only)"),node("p",review.original,"board-prose"),node("h3","Published text"),node("p",review.filtered,"board-prose"));
          save(item,"Mark reviewed","staff_filter_resolve",{id:review.id},()=>item.remove());
        });
      });
      const audit=section(root,"Board audit","Recent decisions and changes appear first. Switch to all entries to include read-access records.");
      const auditFilter=select([["changes","Decisions and changes"],["all","All entries"]],"changes");
      audit.append(label("Show",auditFilter));
      const auditRows=node("div",undefined,"board-audit-list");
      const auditControls=node("div",undefined,"actions");
      audit.append(auditRows,auditControls);
      let auditOffset=0;
      const loadAudit=async()=>{
        const page=await rpc("boards_staff_audit",{p_offset:auditOffset,p_limit:50,p_include_reads:auditFilter.value==="all"});
        auditRows.replaceChildren();
        if(!page.items.length) auditRows.append(node("p","No Board audit entries on this page."));
        page.items.forEach(entry=>auditRows.append(auditEntry(entry)));
        auditPrevious.disabled=auditOffset===0;
        auditNext.disabled=!page.has_more;
      };
      const auditPrevious=button(auditControls,"Previous",async()=>{auditOffset=Math.max(0,auditOffset-50);await loadAudit();});
      const auditNext=button(auditControls,"Next",async()=>{auditOffset+=50;await loadAudit();});
      auditFilter.addEventListener("change",()=>{auditOffset=0;loadAudit().catch(error=>{status.textContent=error.message || "Could not load Board audit.";});});
      await loadAudit();
    }
    if(can("board_suspensions")) {
      const panel=section(root,"Global board participation","This affects participation across Boards. It does not suspend the entire PocketPass account.");
      const user=input("","text",36); panel.append(label("Account ID (UUID, not Friend Code)",user));
      const explain=reason(panel);
      const confirmation=node("p","","hint board-case-confirmation");confirmation.setAttribute("role","status");
      button(panel,"Suspend board participation",async()=>{
        confirmation.textContent="";
        try {
          const target=user.value.trim(), why=explain();
          if(!target) throw new Error("Enter the account ID to suspend.");
          if(!window.confirm(`Suspend Board participation for account ${target}?\n\nThey will be unable to join or participate in any Board until this suspension is revoked. Their PocketPass account remains active.\n\nReason: ${why}`)) return;
          const result=await mutate("restrict",{user_id:target,reason:why});
          confirmation.textContent=`Board participation suspended. Case ID: ${result.case_id}.`;
          status.textContent=confirmation.textContent;
          try { await loadSuspensions(); }
          catch(error) { confirmation.textContent+=` The active-case list could not refresh: ${error.message || "try Find again"}.`; }
        } catch(error) { confirmation.textContent=error.message || "Could not suspend Board participation."; throw error; }
      },true);
      panel.append(confirmation);

      const cases=node("div",undefined,"board-case-list stack");
      cases.append(node("h3","Active global suspensions"),node("p","Search by name, account ID, or case ID. Each result can be revoked here.","panel-sub"));
      const search=input("","search",80); search.placeholder="Name, account ID, or case ID";
      cases.append(label("Find suspension",search));
      const caseRows=node("div",undefined,"stack"), caseControls=node("div",undefined,"actions");
      cases.append(caseRows,caseControls); panel.append(cases);
      let caseOffset=0, appliedSearch="";
      const loadSuspensions=async()=>{
        const page=await rpc("boards_staff_suspensions",{p_offset:caseOffset,p_limit:50,p_search:appliedSearch});
        caseRows.replaceChildren();
        if(!page.items.length) caseRows.append(node("p","No active suspensions match this search."));
        page.items.forEach(restriction=>{
          const item=section(caseRows,restriction.display_name,`Case ID: ${restriction.id}`);
          item.append(node("p",`Account ID: ${restriction.user_id}`),node("p",`Reason: ${restriction.reason}`),
            node("p",`Suspended ${when(restriction.created_at)}${restriction.expires_at?` · Expires ${when(restriction.expires_at)}`:""}`));
          button(item,"Revoke suspension",async()=>{
            if(!window.confirm(`Revoke the Board suspension for ${restriction.display_name}?\nCase ID: ${restriction.id}`)) return;
            await mutate("revoke_restriction",{id:restriction.id});
            confirmation.textContent=`Board suspension revoked. Case ID: ${restriction.id}.`;
            status.textContent=confirmation.textContent;
            if(page.items.length===1 && caseOffset>0) caseOffset-=50;
            try { await loadSuspensions(); }
            catch(error) { confirmation.textContent+=` The active-case list could not refresh: ${error.message || "try Find again"}.`; }
          },true);
        });
        casePrevious.disabled=caseOffset===0;
        caseNext.disabled=!page.has_more;
      };
      button(caseControls,"Find",async()=>{appliedSearch=search.value.trim();caseOffset=0;await loadSuspensions();});
      const casePrevious=button(caseControls,"Previous",async()=>{caseOffset=Math.max(0,caseOffset-50);await loadSuspensions();});
      const caseNext=button(caseControls,"Next",async()=>{caseOffset+=50;await loadSuspensions();});
      await loadSuspensions();
    }
    if(can("board_stationery")) {
      const panel=section(root,"Stationery","Plain paper ships with Boards. Add supplied artwork later. Supporters may use all active stationery and can still buy permanent ownership.");
      const editPaper=(paper={})=>{
        const item=section(panel,paper.name||"New stationery");
        if(paper.id==="plain") {item.append(node("p","Always free. Plain paper cannot be disabled."));return;}
        const id=input(paper.id||"","text",80), name=input(paper.name||"","text",80), access=select([["free","Free"],["tokens","Token purchase"],["achievement","Achievement unlock"]],paper.access||"free"), price=input(paper.price||0,"number"), achievement=input(paper.achievement_key||""), active=checkbox(paper.active!==false), artwork=area(JSON.stringify(paper.artwork||{version:1,background:"#FFFFFF"},null,2),100000);
        id.disabled=!!paper.id;
        item.append(label("Catalogue ID",id),label("Name",name),label("Access",access),label("Token price",price),label("Achievement key",achievement),label("Available for new use",active),label("Artwork manifest (version 1, white background, optional drawing strokes)",artwork));
        save(item,"Save stationery","staff_stationery",()=>({stationery_id:id.value,name:name.value,access:access.value,price:Number(price.value),achievement_key:achievement.value||null,active:active.checked,artwork:JSON.parse(artwork.value)}));
      };
      (await query("staff_stationery")).forEach(editPaper);editPaper();
    }
  }
  window.PocketPassBoardsAdmin={render};
})();
