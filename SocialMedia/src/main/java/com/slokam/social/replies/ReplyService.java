package com.slokam.social.replies;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class ReplyService {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  public ReplyService(JdbcTemplate jdbc, TransactionTemplate tx) { this.jdbc=jdbc; this.tx=tx; }
  private static final String SELECT="SELECT m.*,c.platform,c.account_id,c.external_content_id,c.permalink,a.external_account_id,a.account_name FROM social_comments m JOIN social_content c ON c.id=m.content_id JOIN social_accounts a ON a.id=c.account_id";
  public ReplyAccess.Actor reader(String name) {
    return new ReplyAccess.Actor(name, jdbc.queryForList("SELECT id FROM social_accounts", Long.class));
  }
  private Map<String,Object> content(long id, ReplyAccess.Actor actor) {
    var rows=jdbc.queryForList("SELECT c.id AS content_id,c.platform,c.account_id,c.external_content_id,a.external_account_id,a.account_name FROM social_content c JOIN social_accounts a ON a.id=c.account_id WHERE c.id=?",id);
    if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Content not found");
    var row=rows.getFirst(); actor.require(((Number)row.get("account_id")).longValue()); return row;
  }
  public Map<String,Object> enqueueComment(long contentId, String key, String text, ReplyAccess.Actor actor) {
    if(key==null || !key.matches("[A-Za-z0-9_.:-]{1,128}")) throw error(HttpStatus.BAD_REQUEST,"Invalid Idempotency-Key");
    if(text==null || text.isBlank() || text.length()>10000) throw error(HttpStatus.BAD_REQUEST,"Comment must contain 1-10000 characters");
    var c=content(contentId,actor);
    if(!Set.of("youtube","facebook","instagram").contains(c.get("platform"))) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"Platform does not support comments");
    String uuid=UUID.randomUUID().toString();
    try {
      jdbc.update("INSERT INTO social_comment_replies(id,content_id,request_key,reply_text,actor,status,created_at,updated_at) VALUES(?,?,?,?,?,'QUEUED',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",uuid,contentId,key,text,actor.name());
    } catch(DuplicateKeyException e) {
      var rows=jdbc.queryForList("SELECT * FROM social_comment_replies WHERE actor=? AND request_key=?",actor.name(),key);
      if(rows.isEmpty()) throw e;
      var previous=rows.getFirst();
      if(previous.get("comment_id")!=null || !(previous.get("content_id") instanceof Number target) || target.longValue()!=contentId || !text.equals(previous.get("reply_text")))
        throw error(HttpStatus.CONFLICT,"Idempotency key already used for another comment");
      uuid=(String)previous.get("id");
    }
    return get(uuid,actor);
  }
  public Map<String,Object> comment(long id, ReplyAccess.Actor actor) {
    var rows=jdbc.queryForList(SELECT+" WHERE m.id=?",id);
    if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Comment not found");
    var row=rows.getFirst(); actor.require(((Number)row.get("account_id")).longValue()); return row;
  }
  public List<Map<String,Object>> inbox(ReplyAccess.Actor actor, String platform, Long account, String video, boolean needsReply, long after, int limit) {
    if(!actor.allAccounts() && actor.accounts().isEmpty()) return List.of();
    var args=new ArrayList<Object>();
    String where=" WHERE m.id>?";
    if(!actor.allAccounts()) {
      args.addAll(actor.accounts());
      where=" WHERE c.account_id IN ("+String.join(",",Collections.nCopies(args.size(),"?"))+") AND m.id>?";
    }
    args.add(after);
    if(platform!=null && !platform.isBlank()) { where+=" AND c.platform=?"; args.add(platform); }
    if(account!=null) { actor.require(account); where+=" AND c.account_id=?"; args.add(account); }
    if(video!=null && !video.isBlank()) { where+=" AND EXISTS (SELECT 1 FROM application_video_uploads u WHERE u.content_id=c.id AND u.video_id=?)"; args.add(video); }
    if(needsReply) where+=" AND m.resolved_at IS NULL";
    if(needsReply) where+=" AND NOT EXISTS (SELECT 1 FROM social_comment_replies r WHERE r.comment_id=m.id AND r.status='SENT') AND (m.author_id IS NULL OR m.author_id<>a.external_account_id)";
    args.add(limit);
    return jdbc.queryForList(SELECT+where+" ORDER BY m.id LIMIT ?",args.toArray());
  }
  public Map<String,Object> thread(long id, ReplyAccess.Actor actor) {
    var c=comment(id,actor);
    String root=Objects.toString(c.get("parent_external_id"),Objects.toString(c.get("external_comment_id")));
    var out=new LinkedHashMap<String,Object>(); out.put("comment",c);
    out.put("comments",jdbc.queryForList("SELECT * FROM social_comments WHERE content_id=? AND (external_comment_id=? OR parent_external_id=?) ORDER BY id",c.get("content_id"),root,root));
    var replies=jdbc.queryForList("SELECT r.* FROM social_comment_replies r JOIN social_comments m ON m.id=r.comment_id WHERE m.content_id=? AND (m.external_comment_id=? OR m.parent_external_id=?) ORDER BY r.created_at",c.get("content_id"),root,root);
    replies.forEach(row -> row.remove("content_id"));
    out.put("replies",replies);
    return out;
  }
  public Map<String,Object> enqueue(long id, String key, String text, ReplyAccess.Actor actor) {
    if(key==null || !key.matches("[A-Za-z0-9_.:-]{1,128}")) throw error(HttpStatus.BAD_REQUEST,"Invalid Idempotency-Key");
    if(text==null || text.isBlank() || text.length()>10000) throw error(HttpStatus.BAD_REQUEST,"Reply must contain 1-10000 characters");
    var c=comment(id,actor);
    if(!Set.of("youtube","facebook","instagram").contains(c.get("platform"))) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"Platform does not support replies");
    String uuid=UUID.randomUUID().toString();
    try {
      jdbc.update("INSERT INTO social_comment_replies(id,comment_id,request_key,reply_text,actor,status,created_at,updated_at) VALUES(?,?,?,?,?,'QUEUED',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",uuid,id,key,text,actor.name());
    } catch(DuplicateKeyException e) {
      var rows=jdbc.queryForList("SELECT * FROM social_comment_replies WHERE actor=? AND request_key=?",actor.name(),key);
      if(rows.isEmpty()) throw e;
      var previous=rows.getFirst();
      if(!(previous.get("comment_id") instanceof Number target) || target.longValue()!=id || !text.equals(previous.get("reply_text"))) throw error(HttpStatus.CONFLICT,"Idempotency key already used for another reply");
      uuid=(String)previous.get("id");
    }
    return get(uuid,actor);
  }
  public Map<String,Object> get(String id, ReplyAccess.Actor actor) {
    var rows=jdbc.queryForList("SELECT * FROM social_comment_replies WHERE id=?",id);
    if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Reply not found");
    var row=rows.getFirst(); if(row.get("comment_id") instanceof Number target) comment(target.longValue(),actor);
    else content(((Number)row.get("content_id")).longValue(),actor);
    if(row.get("comment_id")!=null) row.remove("content_id");
    return row;
  }
  public void retry(String id, ReplyAccess.Actor actor) {
    get(id,actor);
    if(jdbc.update("UPDATE social_comment_replies SET status='QUEUED',error_message=NULL,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND status='FAILED' AND (next_attempt_at IS NULL OR next_attempt_at<=UTC_TIMESTAMP(6))",id)!=1)
      throw error(HttpStatus.CONFLICT,"Only failed replies past their cooldown may be retried; inspect uncertain results with the provider");
  }
  public void resolve(long id, boolean resolved, ReplyAccess.Actor actor) {
    comment(id,actor);
    jdbc.update("UPDATE social_comments SET resolved_at="+(resolved?"UTC_TIMESTAMP(6)":"NULL")+",resolved_by=? WHERE id=?",resolved?actor.name():null,id);
  }
  // Manual reconciliation records an already observed provider reply; it never sends a POST.
  public void confirm(String id, String remote, ReplyAccess.Actor actor) {
    if(remote==null || !remote.matches("[A-Za-z0-9_.:-]{1,191}")) throw error(HttpStatus.BAD_REQUEST,"Valid provider reply ID required");
    var r=get(id,actor); var c=r.get("comment_id") instanceof Number target ? comment(target.longValue(),actor) : content(((Number)r.get("content_id")).longValue(),actor);
    tx.executeWithoutResult(s -> {
      if(jdbc.update("UPDATE social_comment_replies SET status='SENT',external_reply_id=?,error_message=NULL,reconciled_by=?,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND status='UNKNOWN'",remote,actor.name(),id)!=1)
        throw error(HttpStatus.CONFLICT,"Only uncertain replies may be confirmed");
      String parent=r.get("comment_id")==null ? null : Objects.toString(c.get("parent_external_id"),Objects.toString(c.get("external_comment_id")));
      jdbc.update("INSERT INTO social_comments(content_id,external_comment_id,parent_external_id,author_id,author_name,comment_text,retrieved_at) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE retrieved_at=UTC_TIMESTAMP(6)",c.get("content_id"),remote,parent,c.get("external_account_id"),c.get("account_name"),r.get("reply_text"));
    });
  }
  static ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
