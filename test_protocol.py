import io
import pathlib
import socket
import struct
import subprocess
import sys
import tempfile
import threading
import unittest
from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from PIL import Image

ROOT=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'windows'))
from protocol import authenticate, receive_frame, exact, MAX_PACKET

class ProtocolTests(unittest.TestCase):
    def packet(self, rotation=0, counter=0, image=None):
        buffer=io.BytesIO()
        (image or Image.new('RGB',(640,480),'red')).save(buffer,format='JPEG')
        cipher=AESGCM(bytes(range(32)))
        payload=cipher.encrypt(counter.to_bytes(12,'big'),struct.pack('!H',rotation)+buffer.getvalue(),b'LCW1')
        return cipher,struct.pack('!I',len(payload))+payload

    def receive(self, packet, cipher, counter=0):
        a,b=socket.socketpair()
        try:
            a.settimeout(2); b.settimeout(2)
            a.sendall(packet); a.shutdown(socket.SHUT_WR)
            return receive_frame(b,cipher,counter)
        finally:
            a.close(); b.close()

    def test_normal_frame(self):
        cipher,packet=self.packet()
        result=self.receive(packet,cipher)
        self.assertEqual(result.size,(640,480))
        self.assertGreater(result.getpixel((320,240))[0],240)

    def test_reject_tamper(self):
        cipher,packet=self.packet()
        with self.assertRaises(InvalidTag): self.receive(packet[:-1]+bytes([packet[-1]^1]),cipher)

    def test_reject_replay(self):
        cipher,packet=self.packet()
        with self.assertRaises(InvalidTag): self.receive(packet,cipher,1)

    def test_reject_oversized_packet(self):
        cipher,_=self.packet()
        with self.assertRaises(ValueError): self.receive(struct.pack('!I',MAX_PACKET+1),cipher)

    def test_reject_bad_rotation(self):
        cipher,packet=self.packet(rotation=45)
        with self.assertRaises(ValueError): self.receive(packet,cipher)

    def test_reject_truncation(self):
        cipher,packet=self.packet()
        with self.assertRaises(ConnectionError): self.receive(packet[:-20],cipher)

    def test_reject_wrong_pairing_code(self):
        a,b=socket.socketpair(); errors=[]
        def server():
            try: authenticate(b,'00'*16)
            except Exception as e: errors.append(e)
            finally: b.close()
        t=threading.Thread(target=server); t.start()
        try:
            a.settimeout(2); exact(a,36); a.sendall(bytes(64))
            t.join(2)
            self.assertFalse(t.is_alive())
            self.assertIsInstance(errors[0],ValueError)
        finally: a.close()

    def test_java_android_protocol_to_python(self):
        token='0123456789abcdef'*2
        with tempfile.TemporaryDirectory() as temp:
            image=pathlib.Path(temp)/'frame.jpg'
            Image.new('RGB',(640,480),'red').save(image)
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',temp,str(ROOT/'android/app/src/main/java/cl/luzcam/SecureChannel.java'),str(ROOT/'tests/ProtocolClient.java')],check=True)
            server=socket.socket(); server.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
            server.bind(('127.0.0.1',47990)); server.listen(1); server.settimeout(10)
            process=subprocess.Popen(['java','-cp',temp,'ProtocolClient',token,str(image)],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
            try:
                client,_=server.accept()
                with client:
                    client.settimeout(5)
                    cipher=authenticate(client,token)
                    first=receive_frame(client,cipher,0)
                    second=receive_frame(client,cipher,1)
                    self.assertEqual(first.getpixel((0,0)),(12,17,27))
                    self.assertGreater(first.getpixel((320,240))[0],240)
                    self.assertGreater(second.getpixel((0,0))[0],240)
                _,err=process.communicate(timeout=5)
                self.assertEqual(process.returncode,0,err.decode())
            finally:
                server.close()
                if process.poll() is None: process.kill(); process.communicate()

if __name__=='__main__': unittest.main(verbosity=2)
