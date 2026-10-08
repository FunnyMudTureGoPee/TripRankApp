package app.triprank;
import org.json.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** QR invitation parser. Only the app's fixed port on a private IPv4 address is allowed. */
public final class Invite {
 public String ssid="",password="",security="WPA2",url,code;
 public String encode()throws Exception{validate();return "triprank://join#"+Base64.getUrlEncoder().withoutPadding().encodeToString(json().toString().getBytes(StandardCharsets.UTF_8));}
 public JSONObject json()throws Exception{return new JSONObject().put("v",1).put("ssid",ssid).put("password",password).put("security",security).put("url",url).put("code",code);}
 public String page(){return url+"/#join="+code;}
 public void validate()throws Exception{
  URI u=new URI(url);String h=u.getHost();if(!"http".equals(u.getScheme())||u.getPort()!=8766||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||!(u.getPath().isEmpty()||u.getPath().equals("/"))||h==null||!h.matches("[0-9.]+"))throw new IllegalArgumentException("二维码中的页面地址不正确");
  String[] p=h.split("\\.");if(p.length!=4)throw new IllegalArgumentException("仅支持局域网 IPv4 地址");int[] n=new int[4];for(int i=0;i<4;i++){if(p[i].length()>1&&p[i].startsWith("0"))throw new IllegalArgumentException("地址格式错误");n[i]=Integer.parseInt(p[i]);if(n[i]<0||n[i]>255)throw new IllegalArgumentException("地址格式错误");}if(!(n[0]==10||(n[0]==172&&n[1]>=16&&n[1]<=31)||(n[0]==192&&n[1]==168)))throw new IllegalArgumentException("仅支持私人局域网地址");
  if(code==null||!code.matches("[A-F0-9]{8}"))throw new IllegalArgumentException("入场凭证无效，请让主机重新生成二维码");
  if(ssid==null||ssid.getBytes(StandardCharsets.UTF_8).length>32||ssid.indexOf('\0')>=0)throw new IllegalArgumentException("热点名称过长或无效");
  if(password==null||!Arrays.asList("WPA2","WPA3","OPEN").contains(security))throw new IllegalArgumentException("热点加密方式无效");
  if(!ssid.isEmpty()&&!security.equals("OPEN")&&(password.length()<8||password.length()>63||!password.matches("[\\x20-\\x7E]+")))throw new IllegalArgumentException("热点密码需为 8–63 个英文字符或数字 / 符号");
  if(url.endsWith("/"))url=url.substring(0,url.length()-1);
 }
 public static Invite parse(String text)throws Exception{
  if(text==null||text.length()>4096)throw new IllegalArgumentException("二维码内容无效");Invite i=new Invite();
  if(text.startsWith("triprank://join#")){JSONObject d=new JSONObject(new String(Base64.getUrlDecoder().decode(text.substring("triprank://join#".length())),StandardCharsets.UTF_8));if(d.getInt("v")!=1)throw new IllegalArgumentException("不支持的二维码版本");i.ssid=d.getString("ssid");i.password=d.getString("password");i.security=d.getString("security");i.url=d.getString("url");i.code=d.getString("code");}
  else{URI u=new URI(text);if(u.getFragment()==null||!u.getFragment().matches("join=[A-F0-9]{8}"))throw new IllegalArgumentException("请扫描旅途排排榜的加入二维码");i.url=new URI(u.getScheme(),u.getRawAuthority(),u.getRawPath(),u.getRawQuery(),null).toString();i.code=u.getFragment().substring(5);}
  i.validate();return i;
 }
}
