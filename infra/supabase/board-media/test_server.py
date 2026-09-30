import io
import unittest
import base64
import json
import threading
from unittest.mock import patch
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from http.server import ThreadingHTTPServer
import server
from PIL import Image
from server import process_image, MAX_INPUT


class ImageTests(unittest.TestCase):
    def test_metadata_size_and_animation_are_removed(self):
        image = Image.new('RGB', (2400, 1600), 'blue')
        source = io.BytesIO()
        exif = Image.Exif(); exif[270] = 'private description'
        image.save(source, format='JPEG', exif=exif)
        data, width, height = process_image(source.getvalue())
        self.assertEqual((width, height), (2048, 1365))
        with Image.open(io.BytesIO(data)) as result:
            self.assertEqual(result.format, 'WEBP')
            self.assertFalse(result.getexif())
            self.assertEqual(getattr(result, 'n_frames', 1), 1)
        self.assertLess(len(data), 2 * 1024 * 1024)

    def test_gif_becomes_a_static_raster(self):
        source = io.BytesIO()
        Image.new('RGB', (24, 24), 'red').save(source, format='GIF', save_all=True,
            append_images=[Image.new('RGB', (24, 24), 'green')], duration=100, loop=0)
        data, _, _ = process_image(source.getvalue())
        with Image.open(io.BytesIO(data)) as result:
            self.assertEqual(getattr(result, 'n_frames', 1), 1)

    def test_untrusted_formats_and_oversized_uploads_are_rejected(self):
        for source in (b'<svg onload="alert(1)"></svg>', b'not an image', b'x' * (MAX_INPUT + 1)):
            with self.assertRaises(ValueError): process_image(source)

    def test_transparency_is_preserved(self):
        source = io.BytesIO()
        Image.new('RGBA', (32, 32), (200, 10, 20, 0)).save(source, format='PNG')
        data, _, _ = process_image(source.getvalue())
        with Image.open(io.BytesIO(data)) as result:
            self.assertEqual(result.getpixel((0, 0))[3], 0)


class HttpTests(unittest.TestCase):
    def setUp(self):
        self.http = ThreadingHTTPServer(('127.0.0.1', 0), server.Handler)
        self.thread = threading.Thread(target=self.http.serve_forever, daemon=True); self.thread.start()
    def tearDown(self):
        self.http.shutdown(); self.http.server_close(); self.thread.join()
    def post(self, body, token=None, path='/boards/media'):
        headers = {'Content-Type':'application/json'}
        if token: headers['Authorization'] = 'Bearer ' + token
        request = Request(f'http://127.0.0.1:{self.http.server_port}{path}', data=json.dumps(body).encode(), headers=headers)
        try:
            with urlopen(request) as response: return response.status, json.load(response)
        except HTTPError as error:
            with error: return error.code, json.load(error)
    def test_no_auth_never_processes_pixels_or_calls_rpc(self):
        with patch.object(server, 'rpc') as rpc, patch.object(server, 'process_image') as pixels:
            self.assertEqual(self.post({'image':'bad'})[0], 401)
            rpc.assert_not_called(); pixels.assert_not_called()
    def test_permission_rejection_happens_before_image_decoding(self):
        error = HTTPError('test',403,'Forbidden',{},io.BytesIO(b'{"message":"Board owner required"}'))
        with patch.object(server,'rpc',side_effect=error), patch.object(server,'process_image') as pixels:
            status, body = self.post({'board_id':'board','kind':'icon','operation_id':'operation','image':base64.b64encode(b'not pixels').decode()},'test-user')
            self.assertEqual(status,403); self.assertEqual(body['message'],'Board owner required'); pixels.assert_not_called()
    def test_authenticated_import_commits_only_sanitized_webp(self):
        source=io.BytesIO();Image.new('RGB',(40,30),'blue').save(source,format='PNG')
        calls=[]
        def rpc(name, body, authorization):
            calls.append((name,body,authorization))
            return 'ticket' if name=='prepare_board_branding' else {'asset_id':'asset'}
        with patch.object(server,'rpc',side_effect=rpc), patch.dict(server.os.environ,{'SERVICE_ROLE_KEY':'test-service'}):
            status, body=self.post({'board_id':'board','kind':'icon','operation_id':'operation','image':base64.b64encode(source.getvalue()).decode()},'test-user')
        self.assertEqual((status,body),(200,{'asset_id':'asset'}))
        self.assertEqual(calls[0][2],'Bearer test-user');self.assertEqual(calls[1][2],'Bearer test-service')
        self.assertEqual(calls[1][1]['p_ticket'],'ticket')
        with Image.open(io.BytesIO(base64.b64decode(calls[1][1]['p_data']))) as image:
            self.assertEqual(image.format,'WEBP');self.assertEqual(image.size,(40,30))

    def test_api_artwork_uses_scoped_prepare_and_service_only_commit(self):
        source=io.BytesIO();Image.new('RGB',(20,10),'green').save(source,format='PNG')
        calls=[]
        def rpc(name, body, authorization):
            calls.append((name,body,authorization))
            return {'ticket':'api-ticket'} if name=='api_v1_boards_prepare_branding' else {'ok':True,'asset_id':'asset'}
        with patch.object(server,'rpc',side_effect=rpc), patch.dict(server.os.environ,{'SERVICE_ROLE_KEY':'test-service'}):
            status,result=self.post({'board_id':'board','kind':'cover','operation_id':'operation','image':base64.b64encode(source.getvalue()).decode()},'oauth-token','/v1/boards.artwork_upload')
        self.assertEqual(status,200);self.assertTrue(result['ok'])
        self.assertEqual(calls[0][0],'api_v1_boards_prepare_branding')
        self.assertEqual(calls[0][2],'Bearer oauth-token')
        self.assertEqual(calls[0][1],{'board_id':'board','kind':'cover','operation_id':'operation','source_hash':server.hashlib.sha256(source.getvalue()).hexdigest()})
        self.assertEqual(calls[1][0],'commit_board_branding');self.assertEqual(calls[1][2],'Bearer test-service')
        self.assertEqual(calls[1][1]['p_ticket'],'api-ticket')
        with Image.open(io.BytesIO(base64.b64decode(calls[1][1]['p_data']))) as image:
            self.assertEqual(image.format,'WEBP');self.assertEqual(image.size,(20,10))

    def test_api_denied_scope_never_decodes_or_commits(self):
        failure={'code':'PT403','hint':'SCOPE_REQUIRED','message':'boards:manage is required'}
        with patch.object(server,'rpc',return_value=failure) as rpc, patch.object(server,'process_image') as pixels:
            status,result=self.post({'board_id':'board','kind':'icon','operation_id':'operation','image':base64.b64encode(b'not pixels').decode()},'oauth','/v1/boards.artwork_upload')
            self.assertEqual((status,result),(403,failure));self.assertEqual(rpc.call_count,1);pixels.assert_not_called()

    def test_api_unknown_fields_and_invalid_base64_are_rejected(self):
        valid={'board_id':'board','kind':'icon','operation_id':'operation','image':base64.b64encode(b'pixels').decode()}
        with patch.object(server,'rpc') as rpc, patch.object(server,'process_image') as pixels:
            for body in ({**valid,'staff_review':True},{**valid,'image':'%%%bad'},[],{**valid,'image':None}):
                self.assertEqual(self.post(body,'oauth','/v1/boards.artwork_upload')[0],400)
            rpc.assert_not_called();pixels.assert_not_called()

    def test_api_preflight_supports_connected_apps_without_cookies(self):
        request=Request(f'http://127.0.0.1:{self.http.server_port}/v1/boards.artwork_upload',method='OPTIONS',headers={'Origin':'https://connected.example'})
        with urlopen(request) as response:
            self.assertEqual(response.status,204)
            self.assertEqual(response.headers['Access-Control-Allow-Origin'],'*')
            self.assertIsNone(response.headers.get('Access-Control-Allow-Credentials'))
            self.assertIn('Authorization',response.headers['Access-Control-Allow-Headers'])

    def test_api_rate_limit_preserves_retry_after_and_cors(self):
        failure={'code':'PT429','message':'Rate limited','hint':'API_RATE_LIMITED'}
        error=HTTPError('test',429,'Rate limited',{'Retry-After':'1'},io.BytesIO(json.dumps(failure).encode()))
        body={'board_id':'board','kind':'icon','operation_id':'operation','image':base64.b64encode(b'pixels').decode()}
        request=Request(f'http://127.0.0.1:{self.http.server_port}/v1/boards.artwork_upload',data=json.dumps(body).encode(),headers={'Authorization':'Bearer oauth','Content-Type':'application/json'})
        with patch.object(server,'rpc',side_effect=error), patch.object(server,'process_image') as pixels:
            with self.assertRaises(HTTPError) as caught: urlopen(request)
            self.assertEqual(caught.exception.code,429)
            self.assertEqual(caught.exception.headers['Retry-After'],'1')
            self.assertEqual(caught.exception.headers['Access-Control-Allow-Origin'],'*')
            caught.exception.close()
            pixels.assert_not_called()

    def test_api_missing_token_and_fields_use_documented_codes(self):
        valid={'board_id':'board','kind':'icon','operation_id':'operation','image':base64.b64encode(b'pixels').decode()}
        with patch.object(server,'rpc') as rpc, patch.object(server,'process_image') as pixels:
            status,result=self.post(valid,None,'/v1/boards.artwork_upload')
            self.assertEqual((status,result['hint']),(401,'API_TOKEN_REQUIRED'))
            self.assertEqual(self.post(valid)[1]['hint'],'TOKEN_REQUIRED')
            status,result=self.post({k:v for k,v in valid.items() if k!='image'},'oauth','/v1/boards.artwork_upload')
            self.assertEqual((status,result['hint']),(400,'MISSING_FIELD'))
            rpc.assert_not_called();pixels.assert_not_called()

    def test_api_commit_errors_use_the_envelope(self):
        source=io.BytesIO();Image.new('RGB',(8,8),'red').save(source,format='PNG')
        body={'board_id':'board','kind':'icon','operation_id':'operation','image':base64.b64encode(source.getvalue()).decode()}
        for status,raw,expected in (
            (403,b'{"code":"42501","details":null,"hint":null,"message":"Upload unavailable"}',(403,{'code':'PT403','message':'Upload unavailable','hint':'BOARD_ACCESS_DENIED'})),
            (400,b'{"code":"22023","details":null,"hint":null,"message":"Upload expired. Choose the image again."}',(400,{'code':'PT400','message':'Upload expired. Choose the image again.','hint':'INVALID_FIELD'})),
            (500,b'{"code":"XX000","message":"boom"}',(503,{'code':'PT503','message':'Image upload could not be confirmed. Please retry.','hint':'MEDIA_UNCONFIRMED'})),
        ):
            def rpc(name, payload, authorization):
                if name=='api_v1_boards_prepare_branding': return {'ticket':'ticket'}
                raise HTTPError('test',status,'Error',{},io.BytesIO(raw))
            with patch.object(server,'rpc',side_effect=rpc), patch.dict(server.os.environ,{'SERVICE_ROLE_KEY':'test-service'}):
                self.assertEqual(self.post(body,'oauth','/v1/boards.artwork_upload'),expected)


if __name__ == '__main__': unittest.main()
