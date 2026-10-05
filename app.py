import queue
import secrets
import socket
import threading
import time
import tkinter as tk
from tkinter import ttk, messagebox
import numpy as np
from PIL import Image, ImageDraw, ImageTk
from protocol import authenticate, receive_frame

PORT = 47990

class Receiver:
    def __init__(self, report):
        self.report = report
        self.stop = threading.Event()
        self.lock = threading.Lock()
        self.latest = None
        self.last_at = 0
        self.listener = None
        self.client = None
        self.count = 0
        self.token = secrets.token_hex(16)

    def start(self):
        self.listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.listener.bind(('0.0.0.0', PORT))
        self.listener.listen(2)
        self.listener.settimeout(0.5)
        threading.Thread(target=self.listen, daemon=True).start()
        threading.Thread(target=self.output, daemon=True).start()

    def close(self):
        self.stop.set()
        for sock in (self.client, self.listener):
            if sock:
                try:
                    sock.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                sock.close()

    def listen(self):
        while not self.stop.is_set():
            try:
                client, address = self.listener.accept()
            except socket.timeout:
                continue
            except OSError:
                break
            self.client = client
            try:
                with client:
                    client.settimeout(5)
                    cipher = authenticate(client, self.token)
                    self.report('network', 'Teléfono conectado: ' + address[0])
                    counter = 0
                    while not self.stop.is_set():
                        frame = receive_frame(client, cipher, counter)
                        counter += 1
                        with self.lock:
                            self.latest = frame
                            self.last_at = time.monotonic()
                            self.count += 1
            except Exception as exc:
                if not self.stop.is_set():
                    self.report('network', 'Esperando reconexión · ' + (str(exc) or type(exc).__name__))
            finally:
                self.client = None
                with self.lock:
                    self.latest = None
                self.stop.wait(0.3)

    def frame(self):
        with self.lock:
            if self.latest is not None and time.monotonic() - self.last_at < 2:
                return self.latest.copy()
        blank = Image.new('RGB', (640, 480), (12, 17, 27))
        ImageDraw.Draw(blank).text((220, 225), 'LuzCam - Sin video', fill='white')
        return blank

    def output(self):
        # Retry allows installing/fixing the virtual camera without restarting.
        while not self.stop.is_set():
            try:
                import pyvirtualcam
                with pyvirtualcam.Camera(width=640, height=480, fps=15, backend='obs') as cam:
                    self.report('camera', 'Cámara virtual activa: ' + cam.device)
                    while not self.stop.is_set():
                        cam.send(np.asarray(self.frame(), dtype=np.uint8))
                        cam.sleep_until_next_frame()
            except Exception as exc:
                self.report('camera', 'Cámara virtual no disponible. Instala OBS y cierra su cámara virtual.\n' + str(exc))
                self.stop.wait(5)

def addresses():
    try:
        return ', '.join(sorted({x[4][0] for x in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET)
                                if not x[4][0].startswith('127.')})) or 'Consulta IPv4 con ipconfig'
    except OSError:
        return 'Consulta IPv4 con ipconfig'

def main():
    root = tk.Tk()
    root.title('LuzCam · Cámara Wi-Fi')
    root.geometry('760x800')
    root.configure(bg='#0c111b')
    style = ttk.Style(root)
    style.theme_use('clam')
    style.configure('TFrame', background='#0c111b')
    style.configure('TLabel', background='#0c111b', foreground='#edf2fa', font=('Segoe UI', 11))
    style.configure('Title.TLabel', font=('Segoe UI', 24, 'bold'))
    body = ttk.Frame(root, padding=22)
    body.pack(fill='both', expand=True)
    ttk.Label(body, text='LuzCam', style='Title.TLabel').pack(anchor='w')
    ttk.Label(body, text='Tu teléfono como cámara · Wi-Fi local · 640 × 480 / 15 FPS').pack(anchor='w', pady=(0, 15))
    events = queue.Queue()
    receiver = Receiver(lambda kind, msg: events.put((kind, msg)))
    ttk.Label(body, text='1. En Android escribe la IPv4 de este PC:').pack(anchor='w')
    ip = tk.StringVar(value=addresses())
    ttk.Entry(body, textvariable=ip, state='readonly', font=('Consolas', 13)).pack(fill='x', pady=6)
    ttk.Label(body, text='2. Copia este código en Android (cambia al abrir LuzCam):').pack(anchor='w')
    code = tk.StringVar(value='-'.join(receiver.token[i:i+4] for i in range(0, 32, 4)))
    ttk.Entry(body, textvariable=code, state='readonly', font=('Consolas', 13)).pack(fill='x', pady=6)
    ttk.Label(body, text='3. En Discord selecciona «OBS Virtual Camera».').pack(anchor='w', pady=6)
    preview = ttk.Label(body)
    preview.pack(pady=8)
    network = tk.StringVar(value='Esperando teléfono · TCP ' + str(PORT))
    camera = tk.StringVar(value='Comprobando cámara virtual…')
    stats = tk.StringVar(value='')
    for value in (network, camera, stats):
        ttk.Label(body, textvariable=value, wraplength=700).pack(anchor='w', pady=3)
    def close():
        receiver.close()
        root.destroy()
    ttk.Button(body, text='Detener y cerrar', command=close).pack(anchor='e', pady=8)
    root.protocol('WM_DELETE_WINDOW', close)
    try:
        receiver.start()
    except OSError as exc:
        receiver.close()
        messagebox.showerror('No se pudo iniciar', 'Puerto ocupado o bloqueado. Cierra otras instancias.\n' + str(exc))
        root.destroy()
        return
    def refresh():
        while True:
            try:
                kind, msg = events.get_nowait()
                (network if kind == 'network' else camera).set(msg)
            except queue.Empty:
                break
        photo = ImageTk.PhotoImage(receiver.frame().resize((576, 432)))
        preview.configure(image=photo)
        preview.image = photo
        stats.set('Imágenes recibidas: ' + str(receiver.count))
        root.after(100, refresh)
    refresh()
    root.mainloop()

if __name__ == '__main__':
    main()
