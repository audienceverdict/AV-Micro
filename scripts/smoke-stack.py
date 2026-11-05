#!/usr/bin/env python3
"""Run all five packaged projects with temporary H2 databases; never sends OTPs.
Requires Java 21+, openssl, and H2 already downloaded by mvn verify.
"""
import base64, json, os, secrets, socket, subprocess, tempfile, time, urllib.request, urllib.error
from pathlib import Path
root=Path(__file__).resolve().parents[1]
java=os.environ.get('JAVA_CMD','/opt/homebrew/opt/openjdk@21/bin/java')
if not Path(java).exists(): java='java'
h2=sorted(p for p in (Path.home()/'.m2/repository/com/h2database/h2').glob('*/h2-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))
managed_h2=Path.home()/'.m2/repository/com/h2database/h2/2.3.232/h2-2.3.232.jar'
if managed_h2.exists(): h2=[managed_h2]
if not h2: raise SystemExit('Run mvn verify to download H2 first')
def port():
 with socket.socket() as sock: sock.bind(('127.0.0.1',0));return sock.getsockname()[1]
ports={name:port() for name in ['config-server','discovery-server','identity-service','av-service','api-gateway']}
logs=root/'target/smoke'/str(os.getpid());logs.mkdir(parents=True,exist_ok=True)
processes=[];handles=[]
def request(service,path,method='GET',body=None,headers=None):
 req=urllib.request.Request(f'http://127.0.0.1:{ports[service]}'+path,data=None if body is None else json.dumps(body).encode(),method=method,headers=headers or {})
 if body is not None:req.add_header('Content-Type','application/json')
 try:
  with urllib.request.urlopen(req,timeout=4) as response:return response.status,response.read(),response.headers
 except urllib.error.HTTPError as error:return error.code,error.read(),error.headers

def wait_ready(service):
 deadline=time.monotonic()+150
 while time.monotonic()<deadline:
  if any(p.poll() is not None for p in processes):raise RuntimeError('A service exited; inspect target/smoke logs')
  try:
   if request(service,'/actuator/health')[0]==200:return
  except (OSError,TimeoutError):pass
  time.sleep(.5)
 raise RuntimeError(service+' did not become healthy; inspect target/smoke logs')
def start(service,env,extra=None):
 jar=root/service/'target'/f'{service}-1.0.0.jar'
 if not jar.exists():raise RuntimeError('Run mvn package first: missing '+str(jar))
 handle=(logs/f'{service}.log').open('w');handles.append(handle)
 args=[java,'-Xmx256m','-Dloader.path='+str(h2[-1]),'-cp',str(jar),'org.springframework.boot.loader.launch.PropertiesLauncher']+(extra or [])
 service_env=os.environ.copy();service_env.update(env);service_env['DEBUG']='false';service_env['PORT']=str(ports[service])
 if service in ['identity-service','av-service']:service_env.update(DB_URL=f'jdbc:h2:mem:{service};MODE=MySQL;DB_CLOSE_DELAY=-1',DB_USER='sa',DB_PASSWORD='',MAIL_HOST='localhost',MAIL_FROM='smoke@example.test')
 processes.append(subprocess.Popen(args,cwd=root,env=service_env,stdout=handle,stderr=subprocess.STDOUT))
try:
 with tempfile.TemporaryDirectory(prefix='av-smoke-') as temp:
  private=Path(temp)/'identity-private.pem';public=Path(temp)/'identity-public.pem'
  subprocess.run(['openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:2048','-out',str(private)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
  subprocess.run(['openssl','pkey','-in',str(private),'-pubout','-out',str(public)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
  config_password=secrets.token_hex(24);eureka_password=secrets.token_hex(24);internal=secrets.token_hex(32)
  env={'CONFIG_PASSWORD':config_password,'EUREKA_PASSWORD':eureka_password,'INTERNAL_SERVICE_TOKEN':internal,
       'CONFIG_SERVER_URL':f'http://127.0.0.1:{ports["config-server"]}',
       'CONFIG_REPO_LOCATION':'file:'+str(root/'config-repo'),
       'EUREKA_URL':f'http://discovery:{eureka_password}@127.0.0.1:{ports["discovery-server"]}/eureka/',
       'JWT_PRIVATE_KEY':'file:'+str(private),'JWT_PUBLIC_KEY':'file:'+str(public),
       'JWT_JWK_SET_URI':f'http://127.0.0.1:{ports["identity-service"]}/.well-known/jwks.json',
       'APP_ADMIN_EMAIL':'smoke-admin@example.test','APP_ADMIN_MOBILE':'+919000000071',
       'MANAGEMENT_HEALTH_MAIL_ENABLED':'false','WHATSAPP_PHONE_NUMBER_ID':'smoke-only','WHATSAPP_ACCESS_TOKEN':'smoke-only-no-delivery','WHATSAPP_TEMPLATE_LANGUAGE':'en_US',
       'SPRING_PROFILES_ACTIVE':'dev','CONFIG_BACKEND':'native'}
  start('config-server',env,['--spring.profiles.active=native']);start('discovery-server',env)
  wait_ready('config-server');wait_ready('discovery-server');print('PASS Config Server and Eureka health',flush=True)
  start('identity-service',env,['--spring.datasource.driver-class-name=org.h2.Driver']);start('av-service',env,['--spring.datasource.driver-class-name=org.h2.Driver'])
  wait_ready('identity-service');wait_ready('av-service');print('PASS Identity and AV health with separate H2 databases',flush=True)
  start('api-gateway',env);wait_ready('api-gateway')
  deadline=time.monotonic()+150
  while time.monotonic()<deadline:
   if request('api-gateway','/api/v1/auth/otp/config')[0]==200 and request('api-gateway','/api/v1/movies')[0]==200:break
   time.sleep(1)
  else:raise RuntimeError('Gateway discovery routes not ready')
  print('PASS Gateway routes through Eureka to both applications',flush=True)
  operations=0
  for path in ['/identity/v3/api-docs','/av/v3/api-docs']:
   status,document,_=request('api-gateway',path);assert status==200
   operations+=sum(method in {'get','post','put','patch','delete','head','options','trace'} for item in json.loads(document)['paths'].values() for method in item)
  assert operations==96, f'Expected all 96 original API operations, got {operations}'
  print('PASS all 96 original API operations present in combined OpenAPI documents',flush=True)
  status,body,_=request('identity-service','/internal/users',headers={'X-Service-Token':internal});assert status==200
  user=next(row for row in json.loads(body) if row['email']=='smoke-admin@example.test')
  def b64(value):return base64.urlsafe_b64encode(value).rstrip(b'=')
  def token(purpose=None):
   claims={'sub':user['id'],'iss':'audience-verdict','aud':['av-api'],'iat':int(time.time()),'exp':int(time.time())+300}
   if purpose:claims['purpose']=purpose
   payload=b64(json.dumps({'alg':'RS256','kid':'av-identity-1'}).encode())+b'.'+b64(json.dumps(claims).encode())
   signed=subprocess.run(['openssl','dgst','-sha256','-sign',str(private)],input=payload,capture_output=True,check=True).stdout
   return (payload+b'.'+b64(signed)).decode()
  bearer={'Authorization':'Bearer '+token()}
  assert request('api-gateway','/api/v1/auth/me',headers=bearer)[0]==200
  deadline=time.monotonic()+90
  while time.monotonic()<deadline:
   status=request('api-gateway','/api/v1/bookings/me',headers=bearer)[0]
   if status==200:break
   assert status==503, f'Unexpected authenticated business status {status}'
   time.sleep(1)
  else:raise RuntimeError('AV did not discover Identity in time')
  assert request('api-gateway','/api/v1/bookings/admin',headers=bearer)[0]==200
  print('PASS RSA JWT accepted by Identity and AV; current ADMIN role loaded remotely',flush=True)
  assert request('api-gateway','/api/v1/auth/otp/config',headers={'Origin':'http://localhost:5173'})[2].get('Access-Control-Allow-Origin')=='http://localhost:5173'
  assert request('api-gateway','/internal/users',headers={'X-Service-Token':internal})[0]==404
  assert request('identity-service','/internal/users')[0]==401
  assert request('av-service','/internal/shows/missing/exists')[0]==401
  assert request('api-gateway','/api/v1/bookings/me',headers={'Authorization':'Bearer '+token('otp-registration')})[0]==401
  print('PASS CORS, internal API isolation, and registration-proof rejection',flush=True)
  assert request('api-gateway',f'/api/v1/admin/users/{user["id"]}/additional-roles','PUT',{'roles':['VERIFIER'],'verifierShowIds':['missing-show']},bearer)[0]==404
  print('PASS Identity validates show assignments through AV private API and Eureka',flush=True)
  assert request('api-gateway',f'/api/v1/admin/users/{user["id"]}/role' ,'PATCH',{'role':'USER'},bearer)[0]==200
  assert request('api-gateway','/api/v1/bookings/admin',headers=bearer)[0]==403
  print('PASS role revocation takes effect on the existing JWT across services',flush=True)
  # Restore role directly only through Identity's authenticated API using a fresh temporary admin seed is unnecessary.
  # Disable the account with its own Identity repository is covered by the HTTP regression suite.
  print('STACK SMOKE PASSED (no WhatsApp or SMTP messages sent)',flush=True)
finally:
 for process in reversed(processes):
  if process.poll() is None:process.terminate()
  try:process.wait(timeout=12)
  except subprocess.TimeoutExpired:process.kill();process.wait()
 for handle in handles:handle.close()
