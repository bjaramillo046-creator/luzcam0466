"""LuzCam v1: authenticated local streaming. No discovery or cloud service."""
import hashlib
import hmac
import io
import secrets
import struct
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from PIL import Image, ImageOps

MAGIC = b'LCW1'
MAX_PACKET = 2_000_000

def exact(sock, size):
    result = bytearray()
    while len(result) < size:
        chunk = sock.recv(size - len(result))
        if not chunk:
            raise ConnectionError('El teléfono se desconectó')
        result.extend(chunk)
    return bytes(result)

def mac(key, data):
    return hmac.new(key, data, hashlib.sha256).digest()

def authenticate(sock, token):
    server_nonce = secrets.token_bytes(32)
    sock.sendall(MAGIC + server_nonce)
    client_nonce = exact(sock, 32)
    key = mac(bytes.fromhex(token), b'session' + server_nonce + client_nonce)
    proof = exact(sock, 32)
    if not hmac.compare_digest(proof, mac(key, b'client')):
        raise ValueError('Código de conexión incorrecto')
    sock.sendall(mac(key, b'server'))
    return AESGCM(key)

def receive_frame(sock, cipher, counter):
    size = struct.unpack('!I', exact(sock, 4))[0]
    if not 19 <= size <= MAX_PACKET:
        raise ValueError('Tamaño de imagen inválido')
    payload = cipher.decrypt(counter.to_bytes(12, 'big'), exact(sock, size), MAGIC)
    rotation = struct.unpack('!H', payload[:2])[0]
    if rotation not in (0, 90, 180, 270):
        raise ValueError('Rotación inválida')
    with Image.open(io.BytesIO(payload[2:])) as source:
        if source.format != 'JPEG' or source.width * source.height > 2_100_000:
            raise ValueError('Formato o resolución inválida')
        result = source.convert('RGB')
    if rotation:
        result = result.rotate(-rotation, expand=True)
    return ImageOps.pad(result, (640, 480), color=(12, 17, 27))
