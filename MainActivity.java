package cl.luzcam;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.Camera;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity implements SurfaceHolder.Callback {
    private EditText host, token;
    private TextView status;
    private Button start, stop;
    private Spinner selector;
    private SurfaceView surface;
    private boolean surfaceReady;
    private Camera camera;
    private int width, height, rotation;
    private long lastCapture;
    private final ArrayList<Integer> cameraIds = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Session session;

    private static class Frame {
        byte[] bytes; int width, height, rotation;
        Frame(byte[] b, int w, int h, int r) { bytes=b; width=w; height=h; rotation=r; }
    }
    private static class Session {
        volatile boolean running=true;
        volatile SecureChannel channel;
        volatile long sendingSince;
        Thread worker;
        ArrayBlockingQueue<Frame> frames = new ArrayBlockingQueue<>(1);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18,12,18,12);
        root.setBackgroundColor(Color.rgb(12,17,27));
        TextView title = new TextView(this); title.setText("LuzCam · Cámara Wi-Fi"); title.setTextSize(22); root.addView(title);
        LinearLayout inputs = new LinearLayout(this);
        host = new EditText(this); host.setHint("IPv4 del PC"); host.setSingleLine();
        host.setInputType(InputType.TYPE_CLASS_PHONE);
        host.setText(getPreferences(0).getString("host", ""));
        token = new EditText(this); token.setHint("Código de LuzCam Windows"); token.setSingleLine();
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        inputs.addView(host,new LinearLayout.LayoutParams(0,-2,1));
        inputs.addView(token,new LinearLayout.LayoutParams(0,-2,2)); root.addView(inputs);
        LinearLayout actions = new LinearLayout(this);
        selector = new Spinner(this);
        ArrayList<String> names = new ArrayList<>();
        for (int i=0;i<Camera.getNumberOfCameras();i++) {
            Camera.CameraInfo info=new Camera.CameraInfo(); Camera.getCameraInfo(i,info);
            cameraIds.add(i); names.add(info.facing==Camera.CameraInfo.CAMERA_FACING_FRONT ? "Frontal " + i : "Trasera " + i);
        }
        selector.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
        actions.addView(selector,new LinearLayout.LayoutParams(0,-2,1));
        start = new Button(this); start.setText("Conectar"); actions.addView(start);
        stop = new Button(this); stop.setText("Detener"); stop.setEnabled(false); actions.addView(stop); root.addView(actions);
        status = new TextView(this); status.setText("Abre LuzCam en Windows. Mantén esta app visible durante el uso."); root.addView(status);
        surface = new SurfaceView(this); surface.getHolder().addCallback(this);
        root.addView(surface,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v,insets)-> {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left+18,bars.top+12,bars.right+18,bars.bottom+12); return insets;
            });
        }
        start.setOnClickListener(v->begin());
        stop.setOnClickListener(v->end("Transmisión detenida"));
    }

    private void begin() {
        if (session!=null) return;
        if (checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA},7); return;
        }
        String ip=host.getText().toString().trim();
        String secret=token.getText().toString().replace("-","").replace(" ","").trim();
        if (!validIp(ip)) { status.setText("Escribe una dirección IPv4, por ejemplo 192.168.1.20"); return; }
        if (!secret.matches("[a-fA-F0-9]{32}")) { status.setText("Copia los 32 caracteres del código de Windows; los guiones son opcionales."); return; }
        if (!surfaceReady || cameraIds.isEmpty()) { status.setText("Cámara no disponible todavía"); return; }
        Session s=new Session(); session=s;
        try { openCamera(s); }
        catch (Exception e) { end("No se pudo abrir la cámara: " + e.getMessage()); return; }
        getPreferences(0).edit().putString("host",ip).apply();
        host.setEnabled(false); token.setEnabled(false); selector.setEnabled(false); start.setEnabled(false); stop.setEnabled(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        s.worker=new Thread(()->transmit(s,ip,secret),"LuzCam-red"); s.worker.start();
        handler.post(new Runnable() {
            public void run() {
                if (session!=s || !s.running) return;
                if (s.sendingSince!=0 && SystemClock.elapsedRealtime()-s.sendingSince>6000) {
                    SecureChannel ch=s.channel; if(ch!=null) ch.close();
                }
                handler.postDelayed(this,1000);
            }
        });
    }

    private boolean validIp(String ip) {
        String[] parts=ip.split("\\.",-1); if(parts.length!=4) return false;
        try { for(String p:parts) if(!p.matches("[0-9]{1,3}") || Integer.parseInt(p)>255) return false; }
        catch(NumberFormatException e) { return false; }
        int first=Integer.parseInt(parts[0]);
        return first>0 && first<224 && first!=127;
    }

    @android.annotation.SuppressLint("MissingPermission") // begin() checks runtime permission first.
    private void openCamera(Session s) throws Exception {
        int id=cameraIds.get(selector.getSelectedItemPosition());
        Camera.CameraInfo info=new Camera.CameraInfo(); Camera.getCameraInfo(id,info);
        camera=Camera.open(id);
        Camera.Parameters params=camera.getParameters();
        Camera.Size chosen=null; int best=Integer.MAX_VALUE;
        for(Camera.Size size:params.getSupportedPreviewSizes()) {
            if(size.width*size.height>2100000) continue;
            int distance=Math.abs(size.width-640)+Math.abs(size.height-480);
            if(distance<best) { best=distance; chosen=size; }
        }
        if(chosen==null) throw new Exception("No hay resolución de vista previa compatible");
        width=chosen.width; height=chosen.height;
        params.setPreviewSize(width,height); params.setPreviewFormat(ImageFormat.NV21);
        List<String> focuses=params.getSupportedFocusModes();
        if(focuses!=null && focuses.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        camera.setParameters(params);
        int degrees=getWindowManager().getDefaultDisplay().getRotation()*90;
        if(info.facing==Camera.CameraInfo.CAMERA_FACING_FRONT) {
            rotation=(info.orientation+degrees)%360;
            camera.setDisplayOrientation((360-rotation)%360);
        } else {
            rotation=(info.orientation-degrees+360)%360;
            camera.setDisplayOrientation(rotation);
        }
        camera.setPreviewDisplay(surface.getHolder());
        lastCapture=0;
        camera.setPreviewCallbackWithBuffer((bytes, cam)-> {
            if(session==s && s.running && bytes!=null) {
                long now=SystemClock.elapsedRealtime();
                if(now-lastCapture>=66) {
                    lastCapture=now;
                    Frame frame=new Frame(bytes.clone(),width,height,rotation);
                    if(!s.frames.offer(frame)) { s.frames.poll(); s.frames.offer(frame); }
                }
                cam.addCallbackBuffer(bytes);
            }
        });
        int length=width*height*3/2;
        for(int i=0;i<3;i++) camera.addCallbackBuffer(new byte[length]);
        camera.startPreview();
    }

    private void update(Session s, String message) {
        runOnUiThread(()-> { if(session==s && s.running) status.setText(message); });
    }

    private void transmit(Session s, String ip, String secret) {
        while(s.running) {
            SecureChannel channel=new SecureChannel(); s.channel=channel;
            try {
                if(!s.running) break;
                update(s,"Conectando con " + ip + "…"); channel.connect(ip,secret);
                update(s,"Transmitiendo · conexión cifrada · pulsa Detener para apagar la cámara");
                while(s.running) {
                    Frame f=s.frames.take();
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                    YuvImage yuv=new YuvImage(f.bytes,ImageFormat.NV21,f.width,f.height,null);
                    if(!yuv.compressToJpeg(new Rect(0,0,f.width,f.height),65,bytes)) throw new Exception("No se pudo codificar la imagen");
                    s.sendingSince=SystemClock.elapsedRealtime();
                    channel.send(bytes.toByteArray(),f.rotation);
                    s.sendingSince=0;
                }
            } catch(Exception e) {
                if(s.running) update(s,"Sin conexión: " + e.getClass().getSimpleName() + ". Revisa IP, código y firewall. Reintento en 2 s.");
            } finally { channel.close(); s.channel=null; s.sendingSince=0; }
            if(s.running) try { Thread.sleep(2000); } catch(InterruptedException ignored) {}
        }
    }

    private void end(String message) {
        Session old=session; session=null;
        if(old!=null) {
            old.running=false;
            SecureChannel ch=old.channel; if(ch!=null) ch.close();
            if(old.worker!=null) old.worker.interrupt();
            old.frames.clear();
        }
        if(camera!=null) {
            try { camera.setPreviewCallbackWithBuffer(null); camera.stopPreview(); } catch(RuntimeException ignored) {}
            camera.release(); camera=null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(start!=null) {
            start.setEnabled(true); stop.setEnabled(false); host.setEnabled(true); token.setEnabled(true); selector.setEnabled(true); status.setText(message);
        }
    }
    @Override protected void onPause() { end("En pausa. Pulsa Conectar para volver a transmitir."); super.onPause(); }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==7 && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) begin();
        else status.setText("Necesitas permitir acceso a la cámara para transmitir.");
    }
    @Override public void surfaceCreated(SurfaceHolder holder) { surfaceReady=true; }
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int w,int h) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) { surfaceReady=false; end("Vista previa cerrada"); }
}
