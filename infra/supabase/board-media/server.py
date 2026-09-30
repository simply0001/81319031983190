import base64
import binascii
import hashlib
import io
import json
import os
import threading
import warnings
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from PIL import Image, ImageOps, UnidentifiedImageError

MAX_INPUT = 10 * 1024 * 1024
MAX_REQUEST = 14 * 1024 * 1024
Image.MAX_IMAGE_PIXELS = 20_000_000
warnings.simplefilter('error', Image.DecompressionBombWarning)
PROCESSORS = threading.BoundedSemaphore(2)


def process_image(raw):
    if not raw or len(raw) > MAX_INPUT:
        raise ValueError('Choose an image smaller than 10 MB.')
    try:
        with Image.open(io.BytesIO(raw), formats=['JPEG', 'PNG', 'WEBP', 'GIF']) as source:
            source.seek(0)
            source.load()
            image = ImageOps.exif_transpose(source).convert('RGBA')
            clean = Image.new('RGBA', image.size)
            clean.paste(image)
        for edge in (2048, 1600, 1280, 1024, 800):
            clean.thumbnail((edge, edge), Image.Resampling.LANCZOS)
            result = io.BytesIO()
            clean.save(result, format='WEBP', quality=85, method=4)
            if len(result.getvalue()) <= 2 * 1024 * 1024:
                return result.getvalue(), clean.width, clean.height
    except (OSError, UnidentifiedImageError, Image.DecompressionBombError, Image.DecompressionBombWarning) as error:
        raise ValueError('This image could not be opened. Choose a PNG, JPEG, WebP, or GIF.') from error
    raise ValueError('This image is too large after processing.')


def rpc(name, payload, authorization):
    request = Request(os.environ['POSTGREST_URL'].rstrip('/') + '/rpc/' + name,
                      data=json.dumps(payload).encode(), method='POST',
                      headers={'Authorization': authorization, 'Content-Type': 'application/json'})
    with urlopen(request, timeout=25) as response:
        return json.load(response)


class Handler(BaseHTTPRequestHandler):
    server_version = 'PocketPass'
    def log_message(self, *_args):
        pass

    def reply(self, status, data, retry_after=None):
        body = json.dumps(data).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        if status == 429:
            self.send_header('Retry-After', str(retry_after) if str(retry_after).isdigit() else '60')
        origin = self.headers.get('Origin', '')
        if self.path == '/v1/boards.artwork_upload':
            self.send_header('Access-Control-Allow-Origin', '*')
            self.send_header('Access-Control-Expose-Headers', 'Retry-After')
        elif origin in ('https://admin.pocketpass.xyz', 'https://dashboard.pocketpass.xyz'):
            self.send_header('Access-Control-Allow-Origin', origin)
            self.send_header('Vary', 'Origin')
        self.end_headers()
        self.wfile.write(body)

    def fail(self, status, message, hint):
        return self.reply(status, {'code': f'PT{status}', 'message': message, 'hint': hint})

    def api_failure(self, status, detail, retry_after=None):
        if isinstance(detail, dict) and str(detail.get('code', '')).startswith('PT') and detail.get('hint'):
            return self.reply(status, {'code': detail['code'], 'message': detail.get('message') or '', 'hint': detail['hint']}, retry_after)
        if status == 401:
            return self.fail(401, 'A connected app access token is required', 'API_TOKEN_REQUIRED')
        if status >= 500:
            return self.fail(503, 'Image upload could not be confirmed. Please retry.', 'MEDIA_UNCONFIRMED')
        message = detail.get('message') if isinstance(detail, dict) else None
        hint = detail.get('hint') if isinstance(detail, dict) else None
        fallback = {400: 'INVALID_FIELD', 403: 'BOARD_ACCESS_DENIED', 409: 'DUPLICATE_OPERATION_ID', 429: 'API_RATE_LIMITED'}
        return self.reply(status, {'code': f'PT{status}', 'message': message or 'Image upload failed. Please retry.',
                                   'hint': hint or fallback.get(status, 'MEDIA_UNCONFIRMED')}, retry_after)

    def do_OPTIONS(self):
        self.send_response(204)
        origin = self.headers.get('Origin', '')
        if self.path == '/v1/boards.artwork_upload':
            self.send_header('Access-Control-Allow-Origin', '*')
        elif origin in ('https://admin.pocketpass.xyz', 'https://dashboard.pocketpass.xyz'):
            self.send_header('Access-Control-Allow-Origin', origin)
            self.send_header('Vary', 'Origin')
        self.send_header('Access-Control-Allow-Methods', 'POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Authorization, Content-Type')
        self.end_headers()

    def do_POST(self):
        if self.path not in ('/boards/media', '/v1/boards.artwork_upload'):
            return self.fail(404, 'Unknown endpoint', 'UNKNOWN_ENDPOINT')
        public_api = self.path == '/v1/boards.artwork_upload'
        authorization = self.headers.get('Authorization', '')
        if not authorization.startswith('Bearer ') or len(authorization) > 8192:
            if public_api:
                return self.fail(401, 'A connected app access token is required', 'API_TOKEN_REQUIRED')
            return self.fail(401, 'Sign in to change board artwork.', 'TOKEN_REQUIRED')
        if not PROCESSORS.acquire(blocking=False):
            return self.fail(503, 'Image processing is busy. Please retry.', 'MEDIA_BUSY')
        try:
            self.connection.settimeout(30)
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length <= MAX_REQUEST:
                return self.fail(413, 'Choose an image smaller than 10 MB.', 'MEDIA_TOO_LARGE')
            body = json.loads(self.rfile.read(length))
            if not isinstance(body, dict) or set(body) - {'board_id', 'kind', 'operation_id', 'image'}:
                return self.reply(400, {'code': 'PT400', 'hint': 'UNKNOWN_FIELD', 'message': 'Invalid artwork upload fields.'})
            raw = base64.b64decode(body['image'], validate=True)
            if len(raw) > MAX_INPUT:
                return self.fail(413, 'Choose an image smaller than 10 MB.', 'MEDIA_TOO_LARGE')
            if public_api:
                prepared = rpc('api_v1_boards_prepare_branding', {
                    'board_id': body['board_id'], 'kind': body['kind'],
                    'operation_id': body['operation_id'], 'source_hash': hashlib.sha256(raw).hexdigest(),
                }, authorization)
                if 'code' in prepared:
                    return self.reply(int(prepared['code'][2:]), prepared)
                ticket = prepared['ticket']
            else:
                ticket = rpc('prepare_board_branding', {
                    'p_board_id': body['board_id'], 'p_kind': body['kind'],
                    'p_operation_id': body['operation_id'], 'p_source_hash': hashlib.sha256(raw).hexdigest(),
                }, authorization)
            encoded, width, height = process_image(raw)
            result = rpc('commit_board_branding', {'p_ticket': ticket, 'p_data': base64.b64encode(encoded).decode(),
                         'p_width': width, 'p_height': height}, 'Bearer ' + os.environ['SERVICE_ROLE_KEY'])
            self.reply(200, result)
        except HTTPError as error:
            try:
                detail = json.loads(error.read(4096))
            except (ValueError, AttributeError):
                detail = {'message': 'Image upload failed. Please retry.'}
            finally:
                error.close()
            retry_after = error.headers.get('Retry-After') if error.headers else None
            if public_api:
                self.api_failure(error.code, detail, retry_after)
            else:
                self.reply(error.code, detail if isinstance(detail, dict) else {'message': 'Image upload failed.'}, retry_after)
        except KeyError:
            if public_api:
                self.fail(400, 'board_id, kind, operation_id and image are required.', 'MISSING_FIELD')
            else:
                self.fail(400, 'Invalid image upload.', 'INVALID_FIELD')
        except json.JSONDecodeError:
            self.fail(400, 'Request body must be a JSON object', 'INVALID_FIELD')
        except (ValueError, TypeError, binascii.Error) as error:
            self.fail(400, str(error) if isinstance(error, ValueError) else 'Invalid image upload.', 'INVALID_FIELD')
        except Exception:
            self.fail(503, 'Image upload could not be confirmed. Please retry.', 'MEDIA_UNCONFIRMED')
        finally:
            PROCESSORS.release()


if __name__ == '__main__':
    ThreadingHTTPServer(('0.0.0.0', 8080), Handler).serve_forever()
