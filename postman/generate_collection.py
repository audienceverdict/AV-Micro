#!/usr/bin/env python3
"""Generate Postman artifacts from this repository's Spring controllers. No dependencies."""
import copy, json, re
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'postman'
V = lambda k: '{{' + k + '}}'
samples = {
'OtpRequest': {'destination': V('email')},
'OtpVerifyRequest': {'destination': V('email'), 'otp': V('loginOtp')},
'RegistrationEmailRequest': {'registrationToken': V('registrationToken'), 'email': V('email')},
'RegistrationEmailVerifyRequest': {'registrationToken': V('registrationToken'), 'email': V('email'), 'otp': V('registrationEmailOtp')},
'OtpRegistrationRequest': {'registrationToken': V('registrationToken'), 'name': 'Postman Test User', 'email': V('email'), 'mobile': V('mobile'), 'emailVerificationToken': V('emailVerificationToken')},
'EmailOtpRequest': {'email': V('email')},
'EmailOtpVerifyRequest': {'email': V('email'), 'otp': V('loginOtp')},
'EmailOtpRegistrationRequest': {'email': V('email'), 'name': 'Postman Test User', 'mobile': V('mobile')},
'UpdateProfileRequest': {'name': 'Postman Updated User', 'email': V('email'), 'alternativeMobile': '+919876543211'},
'RoleUpdateRequest': {'role': 'USER'},
'AccountStatusRequest': {'enabled': True},
'AdditionalRolesRequest': {'roles': ['VERIFIER'], 'verifierShowIds': [V('showId')]},
'MovieRequest': {'title': 'Postman Sample Movie', 'description': 'A sample movie for API testing.', 'genre': ['Drama'], 'language': 'Telugu', 'duration': 120, 'releaseDate': '2026-10-01', 'status': 'ACTIVE', 'posterUrl': 'https://example.com/poster.jpg', 'currencyCode': 'INR', 'budget': 1000000},
'PersonRequest': {'fullName': 'Sample Actor', 'biography': 'Sample biography.', 'dateOfBirth': '1990-01-15', 'nationality': 'Indian', 'profileImageUrl': 'https://example.com/actor.jpg', 'skills': ['Acting']},
'PersonSocialLinkRequest': {'platform': 'INSTAGRAM', 'profileUrl': 'https://www.instagram.com/sample_actor', 'username': 'sample_actor', 'isVerifiedOfficial': False},
'MovieCreditRequest': {'personId': V('personId'), 'creditType': 'CAST', 'roleTitle': 'Lead Actor', 'characterName': 'Arjun', 'billingOrder': 1, 'isMainCast': True},
'MovieMediaRequest': {'mediaType': 'POSTER', 'title': 'Official poster', 'mediaUrl': 'https://example.com/poster.jpg', 'isOfficial': True, 'isPrimary': True, 'displayOrder': 0},
'MediaOrderRequest': {'mediaIds': [V('mediaId')]},
'Theatre': {'name': 'Postman Cinema', 'address': '123 Test Road', 'city': 'Hyderabad', 'state': 'Telangana', 'contact': '+919876543210', 'status': 'ACTIVE', 'media': []},
'Screen': {'name': 'Screen 1', 'number': 1, 'rows': 1, 'seatsPerRow': 2, 'status': 'ACTIVE', 'seats': [{'label': 'A1', 'rowNumber': 1, 'columnNumber': 1, 'category': 'REGULAR'}, {'label': 'A2', 'rowNumber': 1, 'columnNumber': 2, 'category': 'REGULAR'}]},
'List<LayoutSeat>': [{'label': 'A1', 'rowNumber': 1, 'columnNumber': 1, 'category': 'REGULAR', 'disabled': False}, {'label': 'A2', 'rowNumber': 1, 'columnNumber': 2, 'category': 'REGULAR', 'disabled': False}],
'Show': {'movieId': V('movieId'), 'theatreId': V('theatreId'), 'screenId': V('screenId'), 'date': V('showDate'), 'startTime': '18:00:00', 'endTime': '20:00:00', 'ticketType': 'PAID', 'ticketPrice': 200, 'maxTicketsPerMobile': 6, 'holdMinutes': 5, 'status': 'OPEN'},
'SeatHoldRequest': {'showId': V('showId'), 'seatIds': [V('seatId')]},
'CreateBookingRequest': {'showId': V('showId'), 'seatIds': [V('seatId')], 'termsAccepted': True, 'alternativeMobile': '+919876543211'},
'CancelBookingRequest': {'seatIds': [V('seatId')]},
'AdminCancellationRequest': {'reason': 'Customer requested cancellation', 'seatIds': [V('seatId')]},
'VerifyTicketRequest': {'qrPayload': V('qrPayload'), 'showId': V('showId')},
'VerifyPaymentRequest': {'provider': 'MANUAL_TEST', 'providerReference': 'POSTMAN-{{$guid}}'},
'Review': {'movieId': V('movieId'), 'title': 'Enjoyable movie', 'text': 'Good story and performances.', 'rating': 4},
'WaitingListRequest': {'showId': V('fullShowId'), 'requestedSeatsCount': 1},
'CampaignRequest': {'target': 'SHOW_BOOKERS', 'subject': 'Test show update', 'message': 'This is a test notification.', 'showId': V('showId')},
}
variables = dict(baseUrl='http://localhost:8080', identityBaseUrl='http://localhost:8081', avBaseUrl='http://localhost:8082', email='postman.user@example.com', mobile='+919876543210', adminEmail='admin@example.com', verifierEmail='verifier@example.com', adminOtp='', verifierOtp='', loginOtp='', registrationMobileOtp='', registrationEmailOtp='', registrationToken='', emailVerificationToken='', userToken='', adminToken='', verifierToken='', otherUserToken='', serviceToken='', showDate='2030-12-20', qrPayload='', fullShowId='', offeredWaitingListId='', foreignBookingId='', foreignReviewId='', foreignWaitingListId='', missingId='00000000-0000-0000-0000-000000000000')
for k in ['userId','targetUserId','movieId','personId','linkId','mediaId','creditId','theatreId','screenId','versionId','showId','bookingId','reviewId','waitingListId','seatId']: variables[k]=''
folders = {}
coverage=[]
def folder(name): return folders.setdefault(name, {'name': name, 'item': []})['item']
def test(code): return {'listen': 'test', 'script': {'type':'text/javascript','exec':code.splitlines()}}
def req(name, method, url, body=None, role=None, status=200, description='', capture=None, extra='', group=''):
    headers=[]
    if body is not None: headers=[{'key':'Content-Type','value':'application/json'}]
    r={'method':method, 'header':headers, 'url':url, 'description':description, 'auth': {'type':'noauth'} if not role else {'type':'bearer','bearer':[{'key':'token','value':V(role),'type':'string'}]}}
    if body is not None: r['body']={'mode':'raw','raw':json.dumps(body,indent=2) if not isinstance(body,str) else body,'options':{'raw':{'language':'json'}}}
    code=f'pm.test("HTTP {status}", () => pm.response.to.have.status({status}));'
    if status>=400: code+='\npm.test("Error is not a server failure", () => pm.expect(pm.response.code).to.be.below(500));'
    if capture:
        code+='\nif (pm.response.code >= 200 && pm.response.code < 300) {\n const data = pm.response.json();\n'
        for key, expr in capture.items(): code+=f' const value_{key} = {expr}; if (value_{key} !== undefined && value_{key} !== null) pm.environment.set("{key}", value_{key});\n'
        code+='}'
    if extra: code+='\n'+extra
    item={'name':name,'request':r,'event':[test(code)]}
    folder(group).append(item)
    return item

def role_for(path, method):
    if path.startswith('/internal'): return None
    if '/auth/otp' in path or '/auth/email-otp' in path or path=='/.well-known/jwks.json': return None
    if '/verifier/' in path: return 'verifierToken'
    if '/ticket-scan' in path: return 'adminToken'
    if '/admin' in path: return 'adminToken'
    if any(path.startswith('/api/v1/'+s) for s in ['movies','people','theatres','shows']):
        return 'adminToken' if method!='GET' or 'layout-compatibility' in path else None
    if method=='GET' and (path.startswith('/api/v1/reviews') or path.startswith('/api/v1/payments')): return None
    return 'userToken'

def pathvar(path, key):
    if key!='id': return key
    for prefix,val in [('admin/users','targetUserId'),('movies','movieId'),('theatres','theatreId'),('shows','showId'),('bookings','bookingId'),('reviews','reviewId'),('waiting-list','waitingListId'),('internal/users','targetUserId')]:
        if prefix in path: return val
    raise ValueError(path)

for service in ['identity-service','av-service']:
 for file in sorted((ROOT/service/'src/main/java/com/slokam/av/controller').glob('*.java')):
    source=file.read_text()
    base=re.search(r'@RequestMapping\("([^"]+)"\)',source)
    base=base.group(1) if base else ''
    # The signature pattern consumes annotation arguments before locating the method body.
    mappings=list(re.finditer(r'@(Get|Post|Put|Patch|Delete)Mapping\b(?:\(([^\n]*?)\))?',source))
    for i,m in enumerate(mappings):
        suffix=re.search(r'"([^"]+)"',m.group(2) or '')
        path=base+(suffix.group(1) if suffix else '')
        method=m.group(1).upper()
        tail=source[m.end():mappings[i+1].start() if i+1<len(mappings) else len(source)]
        sig=tail.split('{',1)[0]
        internal=path.startswith('/internal')
        body_match=re.search(r'@RequestBody(?:\([^)]*\))?\s+([\w<>]+)',sig)
        typ=body_match.group(1) if body_match else None
        body=copy.deepcopy(samples[typ]) if typ else None
        if '/otp/registration/' in path and typ in ['OtpRequest','OtpVerifyRequest']:
            body['destination']=V('mobile')
            if typ=='OtpVerifyRequest': body['otp']=V('registrationMobileOtp')
        role=role_for(path,method)
        urlpath=re.sub(r'\{(\w+)\}',lambda x:V(pathvar(path,x.group(1))),path)
        host=('identityBaseUrl' if service=='identity-service' else 'avBaseUrl') if internal else 'baseUrl'
        url=V(host)+urlpath
        if path.endswith('/{id}/claim'): url=url.replace(V('waitingListId'),V('offeredWaitingListId'))
        params=[]
        for q in re.finditer(r'@RequestParam(?:\(([^)]*)\))?\s+([\w<>]+)\s+(\w+)',sig):
            opts,qt,key=q.groups(); opts=opts or ''
            default=re.search(r'defaultValue\s*=\s*"([^"]*)"',opts)
            optional='required = false' in opts or 'required=false' in opts
            if optional: continue
            val=default.group(1) if default else {'status':'APPROVED','attended':'true','showId':V('showId')}.get(key,V(key))
            params.append(key+'='+val)
        if params: url+='?'+'&'.join(params)
        status=201 if 'HttpStatus.CREATED' in sig else 204 if 'HttpStatus.NO_CONTENT' in sig else 200
        legacy='/email-otp/' in path
        if legacy: status=409
        capture={}
        if method=='POST' and status<300:
            cap={'MovieRequest':'movieId','PersonRequest':'personId','MovieMediaRequest':'mediaId','MovieCreditRequest':'creditId','PersonSocialLinkRequest':'linkId','Theatre':'theatreId','Screen':'screenId','Show':'showId','CreateBookingRequest':'bookingId','Review':'reviewId','WaitingListRequest':'waitingListId'}.get(typ)
            if cap: capture[cap]='data.id'
            if path.endswith('/layout-versions'): capture['versionId']='data.id'
            if path.endswith('/otp/registration/verify'): capture['registrationToken']='data.registrationToken'
            if path.endswith('/otp/registration/email/verify'): capture['emailVerificationToken']='data.registrationToken'
            if path.endswith('/otp/register') or path.endswith('/otp/verify'): capture.update(userToken='data.accessToken',userId='data.user && data.user.id')
        if path.endswith('/availability'):
            capture['seatId']='data.layout && data.layout.seats && data.layout.seats.find(s => !s.disabled && ![...(data.bookedSeatIds || []), ...(data.reservedSeatIds || []), ...(data.heldSeatIds || [])].includes(s.id))?.id'
        label=file.stem.replace('Controller','')
        group=('90 Private internal APIs' if internal else '80 Legacy disabled APIs' if legacy else '10 '+label)
        desc=f'Source: {service}/{file.name}. Set referenced environment variables first. '
        if internal: desc+='Private service URL only; configure X-Service-Token. Compose does not expose these ports. '
        if typ=='WaitingListRequest': desc+='fullShowId must reference an open show with insufficient available seats. '
        if 'claim' in path: desc+='Requires an active unexpired offer; choose offeredWaitingListId manually. '
        if method=='DELETE': desc+='Deletes/cancels the referenced test resource. Run cleanup last. '
        if '/campaign' in path or '/reminder' in path: desc+='Sends real notifications to the selected audience. Use isolated test data. '
        if '/ticket-scan' in path: desc+='Set qrPayload by decoding the owner ticket PNG; requires confirmed, paid/verified booking. '
        if legacy: desc+='Expected 409 OTP_CHANNEL_DISABLED in the current implementation. '
        item=req(f'{method} {path}',method,url,body,role,status,desc,capture,group=group)
        if internal: item['request']['header'].append({'key':'X-Service-Token','value':V('serviceToken')})
        coverage.append({'service':service,'controller':file.name,'method':method,'path':path,'positive':not legacy,'sampleType':typ})
        # Every protected endpoint gets authentication and privilege checks.
        if role:
            req(f'401 missing bearer | {method} {path}',method,url,body,None,401,desc,group='40 Authentication negatives')
            req(f'401 invalid bearer | {method} {path}',method,url,body,'invalidToken',401,desc,group='40 Authentication negatives')
            if role in ['adminToken','verifierToken']:
                req(f'403 USER role | {method} {path}',method,url,body,'userToken',403,'Requires an enabled USER without additional staff roles. '+desc,group='41 Authorization negatives')
        if internal:
            bad=req(f'401 missing service token | {method} {path}',method,url,body,None,401,group='91 Private API negatives')
        # JSON decoding failure is independent of field constraints.
        if body is not None:
            bad=req(f'400 malformed JSON | {method} {path}',method,url,'{"invalid":',role,400,desc,group='42 Input negatives')
            if internal: bad['request']['header'].append({'key':'X-Service-Token','value':V('serviceToken')})
        # Only validated DTO constraints, avoiding claims of validation on entity-bound requests.
        if typ and '@Valid' in sig:
            dto=ROOT/service/'src/main/java/com/slokam/av/dto'/f'{typ}.java'
            if dto.exists():
                text=dto.read_text()
                required=[]
                for field in body if isinstance(body,dict) else []:
                    match=re.search(r'((?:@[\w.]+(?:\([^\n]*?\))?\s*)+)(?:public\s+)?(?:[\w<>?,]+\s+)'+re.escape(field)+r'\b',text)
                    if match and re.search(r'@(NotBlank|NotNull|NotEmpty)\b',match.group(1)): required.append(field)
                for field in required:
                    payload=copy.deepcopy(body); payload.pop(field)
                    req(f'400 missing {field} | {method} {path}',method,url,payload,role,400,desc,group='42 Input negatives')
        # Guaranteed single-resource read misses; exclude list/exists APIs.
        if method=='GET' and re.search(r'/\{\w+\}$',path) and not internal:
            badurl=re.sub(r'\{\{\w+\}\}(?=\?|$)',V('missingId'),url)
            req(f'404 unknown ID | {path}',method,badurl,body,role,404,desc,group='43 Missing resources')

# Explicit guided authentication sequence, separate from the full endpoint reference.
auth=folder('01 Registration and login - run manually in order')
for path in ['/config','/registration/request','/registration/verify','/registration/email/request','/registration/email/verify','/register','/request','/verify']:
    ref=next(x for x in folders['10 Otp']['item'] if x['request']['url']==V('baseUrl')+'/api/v1/auth/otp'+path)
    auth.append(copy.deepcopy(ref))
for who in ['admin','verifier']:
    req(f'{who} - request email OTP','POST',V('baseUrl')+'/api/v1/auth/otp/request',{'destination':V(who+'Email')},status=200,group='02 Staff login')
    req(f'{who} - verify email OTP','POST',V('baseUrl')+'/api/v1/auth/otp/verify',{'destination':V(who+'Email'),'otp':V(who+'Otp')},capture={who+'Token':'data.accessToken'},group='02 Staff login')
variables['invalidToken']='this-is-not-a-valid-jwt'
# Focused business negatives with explicit prerequisite data.
def case(name,method,path,body,status=400,role='userToken',extra='',desc=''):
    return req(name,method,V('baseUrl')+path,body,role,status,desc,extra=extra,group='44 Business and boundary negatives')
case('Login rejects malformed email','POST','/api/v1/auth/otp/request',{'destination':'not-an-email'},role=None)
case('Login requires an existing account','POST','/api/v1/auth/otp/request',{'destination':'unregistered-{{$guid}}@example.com'},role=None)
case('Registration rejects malformed mobile','POST','/api/v1/auth/otp/registration/request',{'destination':'abc'},role=None)
for p in ['/verify','/registration/verify']:
    for otp in ['12345','abcdef','1234567']:
        case(f'OTP format {otp} {p}','POST','/api/v1/auth/otp'+p,{'destination':V('email') if p=='/verify' else V('mobile'),'otp':otp},role=None)
case('Reject forged mobile proof','POST','/api/v1/auth/otp/registration/email/request',{'registrationToken':'forged-token','email':V('email')},role=None)
for field,value in [('duration',0),('duration',-1),('posterUrl','javascript:alert(1)'),('budget',-1),('currencyCode','inr'),('title','x'*201)]:
    body=copy.deepcopy(samples['MovieRequest']); body[field]=value
    case(f'Movie invalid {field} = {str(value)[:30]}','POST','/api/v1/movies',body,role='adminToken')
for rating in [0,6,-1]:
    body=copy.deepcopy(samples['Review']);body['rating']=rating
    case(f'Review rating {rating}','POST','/api/v1/reviews',body)
for field,val in [('seatIds',[]),('termsAccepted',False),('seatIds',[V('seatId'),V('seatId')])]:
    body=copy.deepcopy(samples['CreateBookingRequest']);body[field]=val
    case(f'Booking rejects {field}: {val}','POST','/api/v1/bookings',body,desc='Requires an open bookable show, valid seat and enabled account.')
for n in [0,-1]: case(f'Waiting list requestedSeatsCount {n}','POST','/api/v1/waiting-list',{'showId':V('fullShowId'),'requestedSeatsCount':n})
for q in ['page=-1&size=20','page=0&size=0','page=0&size=101']:
    case('Scan history invalid '+q,'GET','/api/v1/admin/ticket-scans?'+q,None,role='adminToken')
for key in ['foreignBookingId','foreignReviewId','foreignWaitingListId']:
    path={'foreignBookingId':'/api/v1/bookings/','foreignReviewId':'/api/v1/reviews/','foreignWaitingListId':'/api/v1/waiting-list/'}[key]
    method={'foreignBookingId':'GET','foreignReviewId':'PUT','foreignWaitingListId':'DELETE'}[key]
    case('Reject access to another owner '+key,method,path+V(key),samples['Review'] if method=='PUT' else None,403,desc='Set this foreign ID to a resource owned by a different enabled account.')
case('Ticket scan invalid QR returns business rejection','POST','/api/v1/bookings/admin/ticket-scan',{'qrPayload':'invalid-qr','showId':V('showId')},200,'adminToken',extra='pm.test("Rejected QR", () => { const d=pm.response.json(); pm.expect(d.valid).to.eql(false); pm.expect(d.result).to.eql("INVALID_QR"); });')
# A ready-to-run setup folder with deterministic dependency order; select after login.
setup=folder('03 Create test fixtures - run after staff login')
sequence=[('POST','/api/v1/movies'),('POST','/api/v1/people'),('POST','/api/v1/theatres'),('POST','/api/v1/theatres/{id}/screens'),('POST','/api/v1/theatres/screens/{screenId}/layout-versions'),('PUT','/api/v1/theatres/screens/{screenId}/layout-versions/{versionId}/seats'),('POST','/api/v1/theatres/screens/{screenId}/layout-versions/{versionId}/publish'),('POST','/api/v1/shows'),('GET','/api/v1/shows/{id}/availability')]
for method,path in sequence:
    ref=next(x for f in list(folders.values()) for x in f['item'] if x['name']==f'{method} {path}')
    setup.append(copy.deepcopy(ref))
# State-dependent scenarios are runnable requests with explicit fixture requirements.
for key in ['closedShowId','notOpenShowId','bookingClosedShowId','heldSeatId','bookedSeatId','expiredWaitingListId','expiredToken','disabledUserToken','unassignedShowId','expiredRegistrationToken']:
    variables[key]=''
def state(name,path,body,status,description,role='userToken',method='POST',code=None):
    item=case(name,method,path,body,status,role,desc=description)
    if code:
        item['event'][0]['script']['exec'].append('pm.test("Business error code", () => pm.expect(pm.response.json().error).to.eql('+json.dumps(code)+'));')
    return item
for key,code in [('closedShowId','SHOW_CLOSED'),('notOpenShowId','BOOKING_NOT_OPEN'),('bookingClosedShowId','BOOKING_CLOSED')]:
    body=copy.deepcopy(samples['CreateBookingRequest']);body['showId']=V(key)
    state(code,'/api/v1/bookings',body,400,'Set '+key+' to an existing show in the named state; seatId must belong to that show.',code=code)
for key,code in [('heldSeatId','SEAT_UNAVAILABLE'),('bookedSeatId','SEAT_ALREADY_BOOKED')]:
    body=copy.deepcopy(samples['CreateBookingRequest']);body['seatIds']=[V(key)]
    state(code,'/api/v1/bookings',body,409,'Use the current open show. '+key+' must belong to it and be held by another account / already booked respectively.',code=code)
body=copy.deepcopy(samples['CreateBookingRequest']);body['seatIds']=[V('missingId')]
state('Unknown seat rejected','/api/v1/bookings',body,400,'Current show must be open and bookable.',code='INVALID_SEAT')
body=copy.deepcopy(samples['Show']);body['endTime']='17:00:00'
state('Show end before start','/api/v1/shows',body,400,'Movie/theatre/screen must exist.',role='adminToken',code='INVALID_TIME')
state('Overlapping show','/api/v1/shows',samples['Show'],409,'Run after fixture setup; same screen/date/time as existing show.',role='adminToken',code='SHOW_OVERLAP')
state('Waiting list when seats remain','/api/v1/waiting-list',{'showId':V('showId'),'requestedSeatsCount':1},409,'Run before booking the fixture seats.',code='SEATS_AVAILABLE')
state('Duplicate waiting-list membership','/api/v1/waiting-list',samples['WaitingListRequest'],409,'Join fullShowId successfully once before running this request.',code='ALREADY_WAITING')
state('Expired waiting-list offer','/api/v1/waiting-list/'+V('expiredWaitingListId')+'/claim',None,409,'Existing owned entry with expired offer or status other than OFFERED.',code='OFFER_EXPIRED')
for key in ['expiredToken','disabledUserToken']:
    state('Reject '+key,'/api/v1/auth/me',None,401,'Supply an expired access token / a valid token belonging to an account disabled by an administrator.',role=key,method='GET')
state('Registration proof is not an access token','/api/v1/bookings/me',None,401,'Requires the captured valid mobile registration proof.',role='registrationToken',method='GET')
state('Expired registration proof','/api/v1/auth/otp/registration/email/request',{'registrationToken':V('expiredRegistrationToken'),'email':V('email')},400,'Use a genuinely expired mobile proof.',role=None,code='INVALID_REGISTRATION_TOKEN')
state('Verifier cannot scan unassigned show','/api/v1/bookings/admin/ticket-scan',{'qrPayload':V('qrPayload'),'showId':V('unassignedShowId')},403,'Verifier must have VERIFIER role but no assignment to this show.',role='verifierToken')
for result,description in [('WRONG_SHOW','Use a valid ticket for a different show; showId must be an authorized scan location.'),('ALREADY_USED','Scan a successfully admitted ticket again.'),('CANCELLED','Use a cancelled booking ticket.'),('BOOKING_NOT_CONFIRMED','Use a booking pending administrator confirmation.'),('PAYMENT_NOT_VERIFIED','Use a confirmed paid booking with payment not verified.')]:
    req('QR rejection '+result,'POST',V('baseUrl')+'/api/v1/bookings/admin/ticket-scan',samples['VerifyTicketRequest'],'adminToken',200,description,extra='pm.test("QR business rejection", () => { const d=pm.response.json(); pm.expect(d.valid).to.eql(false); pm.expect(d.result).to.eql('+json.dumps(result)+'); });',group='45 Ticket state scenarios')
for role in ['THEATER_MANAGER','MOVIE_MAKER']:
    key='managerToken' if role=='THEATER_MANAGER' else 'makerToken';variables[key]=''
    req(role+' has no catalog write access','POST',V('baseUrl')+'/api/v1/movies',samples['MovieRequest'],key,403,'Provide token for a USER with only this additional staff role.',group='41 Authorization negatives')
for dest,path,code,status,description in [(V('email'),'/request','OTP_COOLDOWN',429,'Repeat a successful login OTP request before resendAfterSeconds has elapsed.'),(V('mobile'),'/registration/request','CONTACT_ALREADY_IN_USE',409,'Use a mobile number of an already registered account.')]:
    state(code,'/api/v1/auth/otp'+path,{'destination':dest},status,description,role=None,code=code)
for name,description,status,code in [('Wrong login OTP','Request a challenge, then set wrongOtp to a different six-digit code.',400,'INVALID_OTP'),('Expired login OTP','Request a challenge and wait until expiresInSeconds has elapsed; use the received code.',400,'INVALID_OTP'),('Replayed login OTP','Successfully verify the same challenge first, then resubmit its code.',400,'INVALID_OTP'),('OTP attempts exhausted','Exhaust the configured verification attempt allowance for a challenge first.',429,'OTP_ATTEMPT_LIMIT')]:
    variables.setdefault('wrongOtp','000000')
    state(name,'/api/v1/auth/otp/verify',{'destination':V('email'),'otp':V('wrongOtp') if name=='Wrong login OTP' else V('loginOtp')},status,description,role=None,code=code)
# Guided booking workflow excludes cancellation and scanning; run those separately.
flow=folder('04 Booking and payment - run after fixtures')
for method,path in [('POST','/api/v1/bookings/seat-holds'),('POST','/api/v1/bookings'),('GET','/api/v1/payments/booking/{bookingId}'),('POST','/api/v1/payments/booking/{bookingId}/verify'),('GET','/api/v1/bookings/{id}/ticket-qr')]:
    ref=next(x for f in list(folders.values()) for x in f['item'] if x['name']==f'{method} {path}')
    flow.append(copy.deepcopy(ref))
# Track service error branches as an audit reference, not as a claim of exhaustive runtime testing.
errors=[]
for service in ['identity-service','av-service']:
 for file in sorted((ROOT/service/'src/main/java').rglob('*.java')):
    source=file.read_text()
    for m in re.finditer(r'new ApiException\(\s*(\d+),\s*"([A-Z_]+)"',source):
        errors.append({'source':str(file.relative_to(ROOT)), 'line':source[:m.start()].count('\n')+1,'status':int(m.group(1)),'error':m.group(2)})
(OUT/'service-error-inventory.json').write_text(json.dumps(errors,indent=2)+'\n')

collection={'info':{'name':'AV Microservices - positive and negative API coverage','schema':'https://schema.getpostman.com/json/collection/v2.1.0/collection.json','description':'Source-derived endpoint reference, guided registration/login and fixtures, validation/authentication/authorization/business negatives. Read postman/README.md before running. Select folders individually: full collection execution is not a valid single workflow. OTPs require manual delivery; stateful scenarios need fixtures. Never use production data.'},'item':[folders[k] for k in sorted(folders)],'variable':[{'key':k,'value':v,'type':'string'} for k,v in variables.items()]}
env={'name':'AV Local - sample data','_postman_variable_scope':'environment','values':[{'key':k,'value':v,'enabled':True,'type':'secret' if ('Token' in k or 'Otp' in k) else 'default'} for k,v in variables.items()]}
for name,data in [('AV-Microservices.postman_collection.json',collection),('AV-Local.postman_environment.json',env),('endpoint-coverage.json',coverage)]:
    (OUT/name).write_text(json.dumps(data,indent=2)+'\n')
count=sum(len(f['item']) for f in folders.values())
print(f'Generated {count} requests covering {len(coverage)} controller operations in {len(folders)} folders.')
