#!/usr/bin/env python3
"""Emit repeatable, additive MySQL UI fixtures. No production credentials or queued replies."""
import json
from datetime import datetime, timedelta, timezone
now = datetime.now(timezone.utc).replace(tzinfo=None)
def dt(days=0): return (now + timedelta(days=days)).isoformat(sep=' ', timespec='microseconds')
def q(v):
    if v is None: return 'NULL'
    if isinstance(v, (int,float)): return str(v)
    if isinstance(v, (dict,list)): v=json.dumps(v, ensure_ascii=False)
    return "'"+str(v).replace('\\','\\\\').replace("'","''")+"'"
def insert(table, **row):
    print('INSERT INTO '+table+' ('+','.join(row)+') VALUES ('+','.join(q(v) for v in row.values())+') ON DUPLICATE KEY UPDATE '+next(iter(row))+'='+next(iter(row))+';')
def ref(name, table, **where):
    print('SET @'+name+'=(SELECT id FROM '+table+' WHERE '+' AND '.join(k+'='+q(v) for k,v in where.items())+');')
# SQL variable references must remain unquoted.
class Var(str): pass
baseq=q
def q(v): return str(v) if isinstance(v,Var) else baseq(v)
def v(name): return Var('@'+name)
print('SET NAMES utf8mb4; SET time_zone="+00:00"; START TRANSACTION;')
platforms=['instagram','facebook','youtube','linkedin']
for a in range(8):
    p=platforms[a%4]; ext=f'ui-demo-account-{a+1}'
    insert('social_accounts',platform=p,external_account_id=ext,account_name=f'UI Demo · {p.title()} {a+1}',enabled=int(a<6),created_at=dt(-90),updated_at=dt())
    ref('a'+str(a),'social_accounts',platform=p,external_account_id=ext)
    insert('social_credentials',account_id=v('a'+str(a)),credential_reference='env:UI_DEMO_UNCONFIGURED',expires_at=dt(-1 if a%3==0 else 30) if a%3!=2 else None,created_at=dt(-90),updated_at=dt())
    insert('social_account_leases',account_id=v('a'+str(a)),owner=None,expires_at=dt(-1))
    for mode in ['INITIAL','INCREMENTAL','BACKFILL']:
        insert('social_sync_checkpoints',account_id=v('a'+str(a)),sync_mode=mode,last_successful_sync_at=dt(-1),backfill_completed=int(a%2==0),last_attempt_at=dt(),last_status='FAILED' if a%3==0 else 'SUCCESS',window_from=dt(-30),window_to=dt(),created_at=dt(-30),updated_at=dt())
    # Stable high IDs are only used in tables with no natural fixture key.
    for j,status in enumerate(['SUCCESS','FAILED','PARTIAL']):
        insert('social_sync_jobs',id=910000+a*10+j,account_id=v('a'+str(a)),sync_mode=['INITIAL','INCREMENTAL','BACKFILL'][j],started_at=dt(-j-1),completed_at=dt(-j-0.99),status=status,fetched_count=30,inserted_count=20 if j==0 else 10,updated_count=5,skipped_count=5,failed_count=10 if j else 0,error_message='Demo: provider rate limit; retry later' if j else None)
# One empty account; enough content for multi-page lists on each other account.
for i in range(84):
    a=i%7; p=platforms[a%4]; c=f'ui-demo-post-{i+1:03}'
    types={'youtube':['VIDEO','SHORT_VIDEO','LIVE'],'instagram':['IMAGE','REEL','STORY','CAROUSEL','VIDEO'],'facebook':['TEXT','IMAGE','VIDEO','LIVE','CAROUSEL'],'linkedin':['TEXT','IMAGE','VIDEO','CAROUSEL']}
    typ=types[p][(i//7)%len(types[p])]
    title=['Community launch','Product walkthrough','Behind the scenes','Customer stories','Monthly highlights','Live questions'][i%6]
    insert('social_content',account_id=v('a'+str(a)),platform=p,external_content_id=c,content_type=typ,provider_content_type=typ,author_id=f'ui-demo-account-{a+1}',author_name=f'UI Demo {p.title()}',title=f'{title} · Demo {i+1}',caption=('తెలుగు • नमस्ते • Hello 🌍\n' if i%9==0 else '')+('A detailed community update. '*50 if i%13==0 else 'Explore our latest update. #UIDemo #Community'),permalink=f'https://example.com/ui-demo/{c}',thumbnail_url=None if i%11==0 else f'https://picsum.photos/seed/{c}/640/360',published_at=dt(-i-1),provider_updated_at=dt(-i),retrieved_at=dt(),engagement_metadata={'likes':0 if i%8==0 else i*31,'comments':6,'shares':i*3,'views':i*1200},provider_metadata={'uiDemo':True},content_status='UNAVAILABLE' if i%17==0 else 'AVAILABLE',created_at=dt(-90),updated_at=dt())
    ref('c'+str(i),'social_content',platform=p,account_id=v('a'+str(a)),external_content_id=c)
    if typ!='TEXT':
        for n in range(3 if typ=='CAROUSEL' else 1):
            video=typ in ['VIDEO','SHORT_VIDEO','REEL','LIVE']
            insert('social_media_assets',content_id=v('c'+str(i)),external_asset_id=f'{c}-asset-{n}',media_type='VIDEO' if video else 'IMAGE',media_url='https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4' if video else f'https://picsum.photos/seed/{c}-{n}/1200/800',thumbnail_url=f'https://picsum.photos/seed/{c}-{n}/640/360',width=1920 if video else 1200,height=1080 if video else 800,duration_seconds=62.5 if video else None,display_order=n,created_at=dt(-30),updated_at=dt())
    for j in range(6):
        comment=f'{c}-comment-{j}'
        insert('social_comments',content_id=v('c'+str(i)),external_comment_id=comment,parent_external_id=f'{c}-comment-0' if j==5 else None,author_id=f'demo-viewer-{j}',author_name=['Alex Morgan','Priya Sharma','Sam Lee','José García','తెలుగు ప్రేక్షకుడు','UI Demo Team'][j],comment_text=['Love this! 🙌','When is the next session?','Could you add subtitles?','Great explanation.\nSharing with my team.','ధన్యవాదాలు! Thank you.','Thanks for watching!'][j],published_at=dt(-i-0.5),retrieved_at=dt(),like_count=0 if j==0 else j*5,reply_count=1 if j==0 else 0,resolved_at=dt() if j==4 else None,resolved_by='ui-demo-operator' if j==4 else None)
        if j<3:
            ref('comment','social_comments',content_id=v('c'+str(i)),external_comment_id=comment)
            state=['SENT','FAILED','UNKNOWN'][j]
            insert('social_comment_replies',id=f'ui-demo-reply-{i}-{j}',comment_id=v('comment'),request_key=f'ui-demo-reply-{i}-{j}',reply_text='Thank you! Our team will follow up with details.',actor='ui-demo-operator',status=state,external_reply_id=f'demo-remote-{i}' if j==0 else None,error_message=None if j==0 else 'Demo: permission expired' if j==1 else 'Demo: delivery uncertain; reconcile before retry',attempts=1,next_attempt_at=dt(3650),created_at=dt(-1),updated_at=dt())
    insert('social_comment_tasks',content_id=v('c'+str(i)),parent_external_id='',due_at=dt(3650),last_success_at=dt(-1),status=['SUCCESS','PENDING','FAILED'][i%3],error_message='Demo: rate limit' if i%3==2 else None,pages_fetched=i%5)
    op={'youtube':'public','instagram':'field:like_count','facebook':'field:shares','linkedin':'shares'}[p]
    result={'items':[{'id':c,'statistics':{'viewCount':str(i*1200),'likeCount':str(i*31),'commentCount':'6'}}]} if p=='youtube' else {'data':[{'name':'likes','values':[{'value':i*31}]}]}
    insert('social_statistics_tasks',account_id=v('a'+str(a)),content_id=v('c'+str(i)),operation=op,enabled=0,due_at=dt(3650),status=['SUCCESS','PENDING','FAILED'][i%3],last_success_at=dt(-1),result=result,error_message='Demo: metric permission missing' if i%3==2 else None)
    ref('task','social_statistics_tasks',account_id=v('a'+str(a)),content_id=v('c'+str(i)),operation=op)
    for h in range(5):
        insert('social_statistics_history',task_id=v('task'),snapshot_bucket=910000+h,observed_at=dt(-h),window_from=(now-timedelta(days=h+30)).date().isoformat(),window_to=(now-timedelta(days=h)).date().isoformat(),result=result)
    if i<24:
        vid=f'VID-UI-DEMO-{i%6+1}'
        insert('application_videos',id=vid,title=f'Demo campaign {i%6+1}',description='Sample campaign for cross-platform upload comparison.',created_at=dt(-30),updated_at=dt())
        if typ in ['VIDEO','SHORT_VIDEO','REEL','LIVE']:
            insert('application_video_uploads',content_id=v('c'+str(i)),video_id=vid,assigned_at=dt())
            insert('application_video_assignment_audit',id=910000+i,content_id=v('c'+str(i)),old_video_id=None,new_video_id=vid,changed_at=dt())
for i in range(30):
    cid=f'ui-demo-draft-{i}'; p=platforms[i%3]
    insert('application_content',id=cid,request_key=cid,title=f'Demo publishing campaign {i+1}',caption='Sample publishing content #UIDemo',content_type='VIDEO' if i%2 else 'IMAGE',media_type='video/mp4' if i%2 else 'image/jpeg',media_size=5242880 if i%2 else 153600,youtube_privacy=['public','unlisted','private'][i%3],created_at=dt(-i))
    state=['PUBLISHED','FAILED','UNKNOWN','ACTION_REQUIRED','QUEUED','PROCESSING'][i%6]
    insert('social_publications',id=f'ui-demo-publication-{i}',content_id=cid,platform=p,external_account_id=f'ui-demo-account-{i%3+1}',status=state,resume_status='QUEUED',external_content_id=f'demo-published-{i}' if state=='PUBLISHED' else None,permalink=f'https://example.com/ui-demo/published/{i}' if state=='PUBLISHED' else None,error_message='Demo: reconnect account' if state=='ACTION_REQUIRED' else 'Demo: upload failed' if state=='FAILED' else None,attempts=0 if state=='QUEUED' else 1,next_attempt_at=dt(3650),deadline_at=dt(3651),created_at=dt(-i),updated_at=dt())
print('COMMIT;')
