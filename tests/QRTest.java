package app.triprank;
import com.google.zxing.*;import com.google.zxing.common.*;import java.util.*;import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import org.json.*;
public class QRTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void rejected(String value)throws Exception{boolean rejected=false;try{Invite.parse(value);}catch(Exception e){rejected=true;}check(rejected,"must reject unsafe invite");}
 static String roundtrip(String payload)throws Exception{Map<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);hints.put(EncodeHintType.CHARACTER_SET,"UTF-8");BitMatrix m=new MultiFormatWriter().encode(payload,BarcodeFormat.QR_CODE,720,720,hints);int[] rgb=new int[720*720];for(int y=0;y<720;y++)for(int x=0;x<720;x++)rgb[y*720+x]=m.get(x,y)?0xff000000:0xffffffff;return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(720,720,rgb)))).getText();}
 public static void main(String[] args)throws Exception{
 Invite i=new Invite();i.url="http://192.168.43.1:8766";i.code="AB12CD34";i.ssid="小马的旅行热点";i.password="hello;:123456";i.security="WPA2";
 Invite j=Invite.parse(roundtrip(i.encode()));check(j.ssid.equals(i.ssid)&&j.password.equals(i.password)&&j.page().equals(i.page()),"combined QR roundtrip");check(Invite.parse(roundtrip(i.page())).ssid.isEmpty(),"browser QR roundtrip");
 for(String u:Arrays.asList("http://8.8.8.8:8766/#join=AB12CD34","http://127.0.0.1:8766/#join=AB12CD34","http://192.168.1.1:80/#join=AB12CD34","http://192.168.1.1:8766/evil#join=AB12CD34","http://x@192.168.1.1:8766/#join=AB12CD34","javascript:alert(1)","triprank://join#broken"))rejected(u);
 TestHost.Memory memory=new TestHost.Memory();LocalServer server=new LocalServer("<html>join</html>".getBytes(),memory,0);server.start();LocalProxy proxy=new LocalProxy(url->(HttpURLConnection)url.openConnection(),"http://127.0.0.1:"+server.port());proxy.start();String root="http://127.0.0.1:"+proxy.port();
 try{
  HttpURLConnection html=(HttpURLConnection)new URL(root+"/").openConnection();check(html.getResponseCode()==200,"proxy serves page");html.disconnect();
  HttpURLConnection login=(HttpURLConnection)new URL(root+"/api/login").openConnection();login.setRequestMethod("POST");login.setDoOutput(true);login.setRequestProperty("Content-Type","application/json");byte[] bytes=new JSONObject().put("code",server.code).put("name","扫码旅伴").toString().getBytes(StandardCharsets.UTF_8);login.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=login.getOutputStream()){out.write(bytes);}check(login.getResponseCode()==200,"QR credential login through proxy");JSONObject response=new JSONObject(new String(LocalServer.readAll(login.getInputStream()),StandardCharsets.UTF_8));login.disconnect();
  HttpURLConnection state=(HttpURLConnection)new URL(root+"/api/state?since=-1").openConnection();state.setRequestProperty("Authorization","Bearer "+response.getString("token"));check(state.getResponseCode()==200,"proxy forwards auth");check(new JSONObject(new String(LocalServer.readAll(state.getInputStream()),StandardCharsets.UTF_8)).getJSONArray("records").length()==0,"proxy receives state");state.disconnect();
  HttpURLConnection denied=(HttpURLConnection)new URL(root+"/arbitrary").openConnection();check(denied.getResponseCode()==404,"proxy path restriction");denied.disconnect();
  // Browser Origin is explicitly checked at relay boundary.
  try(Socket sock=new Socket("127.0.0.1",proxy.port())){String req="POST /api/login HTTP/1.1\r\nHost: 127.0.0.1:"+proxy.port()+"\r\nOrigin: http://evil.example\r\nContent-Type: application/json\r\nContent-Length: 2\r\n\r\n{}";sock.getOutputStream().write(req.getBytes(StandardCharsets.US_ASCII));String status=new BufferedReader(new InputStreamReader(sock.getInputStream())).readLine();check(status.contains("403"),"reject cross-site requests");}
  System.out.println("PASS: combined QR generation and decoding; Chinese SSID and special password preservation; page QR; invalid/public/foreign URL rejection; loopback relay page/login/auth; route allowlist; cross-origin protection.");
 }finally{proxy.close();server.close();}
 }
}
