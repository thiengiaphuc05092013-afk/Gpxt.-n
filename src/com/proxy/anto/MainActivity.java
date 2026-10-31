package com.proxy.anto;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private TextView statusText;
    private EditText keyInput;
    private Button startBtn, stopBtn;
    
    // BẠN CÓ THỂ THAY ĐỔI KEY Ở ĐÂY
    private static final String SECRET_KEY = "GPxt.ân"; 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // --- TỰ ĐỘNG TẠO GIAO DIỆN BẰNG CODE (Không cần file XML) ---
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        TextView title = new TextView(this);
        title.setText("GpxT Proxy App");
        title.setTextSize(24);
        title.setPadding(0, 0, 0, 30);
        layout.addView(title);

        keyInput = new EditText(this);
        keyInput.setHint("Nhập Key (Mặc định: 123456)");
        layout.addView(keyInput);

        startBtn = new Button(this);
        startBtn.setText("BẬT PROXY & NÚT NỔI");
        layout.addView(startBtn);

        stopBtn = new Button(this);
        stopBtn.setText("TẮT PROXY");
        stopBtn.setEnabled(false);
        layout.addView(stopBtn);

        statusText = new TextView(this);
        statusText.setText("Trạng thái: Chưa bật");
        statusText.setPadding(0, 30, 0, 0);
        layout.addView(statusText);

        setContentView(layout);

        // --- BẮT SỰ KIỆN NÚT BẤM ---
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (keyInput.getText().toString().trim().equals(SECRET_KEY)) {
                    checkOverlayPermissionAndStart();
                } else {
                    Toast.makeText(MainActivity.this, "Sai Key bản quyền!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent svc = new Intent(MainActivity.this, ProxyService.class);
                svc.setAction("STOP");
                startService(svc);
                updateUI(false);
            }
        });
    }

    // --- KIỂM TRA QUYỀN NÚT NỔI ---
    private void checkOverlayPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Vui lòng cấp quyền Nút Nổi (Hiển thị trên ứng dụng khác)!", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 1234);
                return;
            }
        }
        startProxyService();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1234) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    startProxyService();
                } else {
                    Toast.makeText(this, "Chưa có quyền Nút Nổi!", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void startProxyService() {
        Intent svc = new Intent(this, ProxyService.class);
        svc.setAction("START");
        startService(svc);
        updateUI(true);
        Toast.makeText(this, "Đã bật Nút Nổi và Proxy!", Toast.LENGTH_SHORT).show();
    }

    private void updateUI(boolean isRunning) {
        startBtn.setEnabled(!isRunning);
        stopBtn.setEnabled(isRunning);
        keyInput.setEnabled(!isRunning);
        statusText.setText(isRunning ? "Trạng thái: ĐANG CHẠY" : "Trạng thái: ĐÃ TẮT");
    }

    // ==========================================
    // LỚP SERVICE (CHẠY NGẦM + QUẢN LÝ NÚT NỔI)
    // ==========================================
    public static class ProxyService extends Service {
        private HttpProxyServer server;
        private WifiManager.WifiLock wifiLock;
        private WindowManager windowManager;
        private View floatingView;

        @Override
        public IBinder onBind(Intent intent) { return null; }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (intent != null && "STOP".equals(intent.getAction())) {
                stopAll();
                stopSelf();
                return START_NOT_STICKY;
            }

            if (intent != null && "START".equals(intent.getAction())) {
                startAll();
            }
            return START_STICKY;
        }

        private void startAll() {
            if (server != null) return;
            
            startForegroundNotification();
            
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ProxyWifiLock");
            wifiLock.acquire();

            server = new HttpProxyServer(8888);
            server.start();

            createFloatingButton();
        }

        private void stopAll() {
            if (server != null) {
                server.shutdown();
                server = null;
            }
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
            }
            if (floatingView != null && windowManager != null) {
                windowManager.removeView(floatingView);
                floatingView = null;
            }
            stopForeground(true);
        }

        private void startForegroundNotification() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager nm = getSystemService(NotificationManager.class);
                NotificationChannel ch = new NotificationChannel("proxy", "Proxy Service", NotificationManager.IMPORTANCE_LOW);
                nm.createNotificationChannel(ch);
                Notification.Builder builder = new Notification.Builder(this, "proxy")
                        .setContentTitle("GpxT Proxy đang chạy")
                        .setSmallIcon(android.R.drawable.stat_sys_download);
                startForeground(1, builder.build());
            }
        }

        // --- TẠO NÚT NỔI ---
        private void createFloatingButton() {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            final Button btn = new Button(this);
            btn.setText("ON");
            btn.setBackgroundColor(Color.GREEN);

            final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                            WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 100;
            params.y = 100;

            // Xử lý kéo thả nút nổi
            btn.setOnTouchListener(new View.OnTouchListener() {
                private int initialX, initialY;
                private float initialTouchX, initialTouchY;
                private boolean isClick = false;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            isClick = true;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);
                            windowManager.updateViewLayout(floatingView, params);
                            if (Math.abs(event.getRawX() - initialTouchX) > 10 || Math.abs(event.getRawY() - initialTouchY) > 10) {
                                isClick = false;
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (isClick) {
                                Toast.makeText(ProxyService.this, "Proxy đang chạy cổng 8888", Toast.LENGTH_SHORT).show();
                            }
                            return true;
                    }
                    return false;
                }
            });

            floatingView = btn;
            windowManager.addView(floatingView, params);
        }
    }

    // ==========================================
    // LÕI XỬ LÝ MẠNG (HTTP/HTTPS PROXY SERVER)
    // ==========================================
    public static class HttpProxyServer extends Thread {
        private int port;
        private ServerSocket serverSocket;
        private volatile boolean running = true;
        private final ExecutorService executor = Executors.newCachedThreadPool();

        public HttpProxyServer(int port) { this.port = port; }

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(port);
                while (running) {
                    Socket client = serverSocket.accept();
                    executor.execute(new ClientHandler(client));
                }
            } catch (IOException ignored) {}
        }

        public void shutdown() {
            running = false;
            try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
            executor.shutdownNow();
        }
    }

    public static class ClientHandler implements Runnable {
        private Socket client;
        public ClientHandler(Socket client) { this.client = client; }

        @Override
        public void run() {
            try {
                InputStream cis = client.getInputStream();
                OutputStream cos = client.getOutputStream();
                String firstLine = readLine(cis);
                if (firstLine == null || firstLine.isEmpty()) return;

                String[] parts = firstLine.split(" ");
                if (parts.length < 3) return;

                String method = parts[0];
                String target = parts[1];

                if ("CONNECT".equalsIgnoreCase(method)) {
                    // Xử lý HTTPS
                    int colon = target.lastIndexOf(':');
                    String host = colon == -1 ? target : target.substring(0, colon);
                    int remotePort = colon == -1 ? 443 : Integer.parseInt(target.substring(colon + 1));

                    Socket remote = new Socket(host, remotePort);
                    cos.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
                    cos.flush();
                    while (true) {
                        String line = readLine(cis);
                        if (line == null || line.isEmpty()) break;
                    }
                    relay(client, remote);
                    remote.close();
                } else {
                    // Xử lý HTTP
                    String url = target.replace("http://", "");
                    int slash = url.indexOf('/');
                    String host = slash == -1 ? url : url.substring(0, slash);
                    String path = slash == -1 ? "/" : url.substring(slash);
                    
                    Socket remote = new Socket(host, 80);
                    OutputStream ros = remote.getOutputStream();
                    ros.write((method + " " + path + " HTTP/1.1\r\n").getBytes());

                    String line;
                    while ((line = readLine(cis)) != null && !line.isEmpty()) {
                        if (!line.toLowerCase().startsWith("proxy-")) {
                            ros.write((line + "\r\n").getBytes());
                        }
                    }
                    ros.write("\r\n".getBytes());
                    ros.flush();
                    relay(client, remote);
                    remote.close();
                }
            } catch (Exception e) {
            } finally {
                try { client.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static void relay(Socket a, Socket b) throws IOException {
        final InputStream ais = a.getInputStream();
        final OutputStream aos = a.getOutputStream();
        final InputStream bis = b.getInputStream();
        final OutputStream bos = b.getOutputStream();

        Thread up = new Thread(new Runnable() {
            public void run() { pipe(ais, bos); }
        });
        Thread down = new Thread(new Runnable() {
            public void run() { pipe(bis, aos); }
        });
        up.setDaemon(true);
        down.setDaemon(true);
        up.start();
        down.start();
        try { down.join(); } catch (Exception ignored) {}
    }

    private static void pipe(InputStream in, OutputStream out) {
        byte[] buf = new byte[8192];
        int len;
        try {
            while ((len = in.read(buf)) != -1) { out.write(buf, 0, len); out.flush(); }
        } catch (IOException ignored) {}
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') continue;
            if (b == '\n') break;
            sb.append((char) b);
        }
        return sb.length() == 0 ? null : sb.toString();
    }
          }
