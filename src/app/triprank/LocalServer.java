package app.triprank;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

public class LocalServer {
    String code=random(4).toUpperCase(Locale.ROOT);
    private final byte[] html;
    private final Store store;
    private java.io.File mergeBackups;private String nodeId="test-node";private boolean requireForeground=false;private android.content.SharedPreferences syncPrefs;
    private final ServerSocket socket;
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(12));
    private final Map<String,Session> sessions=new HashMap<>();
    private final Map<String,List<Long>> attempts=new HashMap<>();
    private volatile boolean live;
    private final Object lock=new Object();
    private static final int MAX_BODY=32*1024*1024;
    static class Session {String name,userId;long expiry;Session(String n,String id){name=n;userId=id;expiry=System.currentTimeMillis()+86400000L;}}
    static class Problem extends Exception {int status;Problem(int s,String m){super(m);status=s;}}
    public LocalServer(Context context)throws Exception{this(readAll(context.getAssets().open("index.html")),new SqlStore(context),8766);mergeBackups=new java.io.File(context.getFilesDir(),"merge-backups");android.content.SharedPreferences sync=context.getSharedPreferences("sync-node",0);syncPrefs=sync;nodeId=sync.getString("id","");if(nodeId.isEmpty()){nodeId=java.util.UUID.randomUUID().toString();sync.edit().putString("id",nodeId).apply();}code=sync.getString("code",code);sync.edit().putString("code",code).apply();requireForeground=true;}
    LocalServer(byte[] page,Store data,int port)throws Exception{html=page;store=data;socket=new ServerSocket();socket.setReuseAddress(true);socket.bind(new InetSocketAddress("0.0.0.0",port));}
    int port(){return socket.getLocalPort();}
    static byte[] readAll(InputStream in)throws IOException{try(InputStream source=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=source.read(buf))!=-1)b.write(buf,0,n);return b.toByteArray();}}
    static String random(int size){byte[] a=new byte[size];new SecureRandom().nextBytes(a);StringBuilder s=new StringBuilder();for(byte x:a)s.append(String.format(Locale.ROOT,"%02x",x&255));return s.toString();}
    public void start(){live=true;new Thread(()->{while(live){try{Socket client=socket.accept();client.setSoTimeout(20000);try{workers.execute(()->handle(client));}catch(RejectedExecutionException e){client.close();}}catch(IOException e){if(live)LedgerService.error="连接服务异常，请重新启动";}}},"ledger-listener").start();}
    public void close(){live=false;try{socket.close();}catch(IOException ignored){}workers.shutdown();synchronized(sessions){sessions.clear();}}
    static String line(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();for(int i=0;i<8192;i++){int x=in.read();if(x<0)throw new EOFException();if(x=='\n')return new String(b.toByteArray(),StandardCharsets.US_ASCII).replace("\r","");b.write(x);}throw new IOException("header too long");}
    private void handle(Socket client){try(Socket c=client){InputStream in=new BufferedInputStream(c.getInputStream());OutputStream out=c.getOutputStream();try{
        String[] request=line(in).split(" ");if(request.length!=3)throw new Problem(400,"请求格式错误");String method=request[0],path=request[1];Map<String,String> headers=new HashMap<>();int count=0;while(true){String h=line(in);if(h.isEmpty())break;if((count+=h.length())>16384)throw new Problem(400,"请求头过大");int i=h.indexOf(':');if(i<0)throw new Problem(400,"请求头格式错误");String key=h.substring(0,i).toLowerCase(Locale.ROOT);if(headers.containsKey(key))throw new Problem(400,"重复请求头");headers.put(key,h.substring(i+1).trim());}
        if("GET".equals(method)&&"/".equals(path)){send(out,200,"text/html; charset=utf-8",html);return;}
        String name=null,userId="";
        if(!"/api/login".equals(path)){String token=headers.getOrDefault("authorization","");if(token.startsWith("Bearer "))token=token.substring(7);synchronized(sessions){Session session=sessions.get(token);if(session!=null&&session.expiry>System.currentTimeMillis()){name=session.name;userId=session.userId;}}if(name==null)throw new Problem(401,"请重新输入访问码连接榜单");}
        if("GET".equals(method)&&path.startsWith("/api/state")){JSONObject data;synchronized(lock){long revision=store.revision();data=new JSONObject().put("revision",revision).put("app",MergeEngine.APP).put("syncProtocol",1).put("nodeId",nodeId).put("foreground",!requireForeground||(SyncApp.visible()&&(syncPrefs==null||syncPrefs.getBoolean("accept",true)))).put("mergeProtocol",3);if(path.equals("/api/state?since="+revision))data.put("unchanged",true);else data.put("records",store.all()).put("pages",store.pages());}json(out,200,data);return;}
        if(!"POST".equals(method))throw new Problem(404,"页面不存在");
        String origin=headers.get("origin");if(origin!=null&&!origin.equals("http://"+headers.get("host")))throw new Problem(403,"不允许跨站访问");
        if(!headers.getOrDefault("content-type","").split(";")[0].equals("application/json")||headers.containsKey("transfer-encoding"))throw new Problem(415,"仅接受 JSON 请求");
        int length;try{length=Integer.parseInt(headers.getOrDefault("content-length","0"));}catch(NumberFormatException e){throw new Problem(400,"请求长度错误");}if(length<=0||length>MAX_BODY)throw new Problem(413,"单次请求上限 32MB，请减少图片或拆分备份");
        byte[] body=new byte[length];int offset=0,n;while(offset<length&&(n=in.read(body,offset,length-offset))>0)offset+=n;if(offset!=length)throw new Problem(400,"请求不完整");JSONObject data=new JSONObject(new String(body,StandardCharsets.UTF_8));
        if(path.equals("/api/login")){String ip=c.getInetAddress().getHostAddress();long now=System.currentTimeMillis();synchronized(sessions){List<Long> recent=attempts.get(ip);if(recent==null)recent=new ArrayList<>();Iterator<Long> iter=recent.iterator();while(iter.hasNext())if(iter.next()<now-60000)iter.remove();if(recent.size()>=10)throw new Problem(429,"尝试过多，请一分钟后重试");if(!MessageDigest.isEqual(code.getBytes(StandardCharsets.UTF_8),data.optString("code","").getBytes(StandardCharsets.UTF_8))){recent.add(now);attempts.put(ip,recent);throw new Problem(403,"访问码不正确");}String member=data.optString("name","").trim();if(member.isEmpty()||member.length()>20)throw new Problem(400,"请输入 1–20 字昵称");Iterator<Session> it=sessions.values().iterator();while(it.hasNext())if(it.next().expiry<now)it.remove();String identity=data.optString("userId","");if(!identity.isEmpty()&&!identity.matches("[A-Za-z0-9_-]{16,80}"))throw new Problem(400,"用户身份格式错误");String token=random(32);sessions.put(token,new Session(member,identity));json(out,200,new JSONObject().put("token",token).put("name",member));return;}}
        if(path.equals("/api/sync")){
          if(requireForeground&&(!SyncApp.visible()||(syncPrefs!=null&&!syncPrefs.getBoolean("accept",true))))throw new Problem(423,"对方 App 不在前台或已暂停同步");
          if(!MergeEngine.APP.equals(data.optString("app"))||data.optInt("syncProtocol")!=1)throw new Problem(400,"双方都需要升级到自动同步版");
          JSONArray incoming=data.optJSONArray("records");if(incoming==null||incoming.length()>10000)throw new Problem(400,"同步记录数量错误");JSONArray clean=new JSONArray();Set<String> unique=new HashSet<>();for(int i=0;i<incoming.length();i++){JSONObject raw=incoming.getJSONObject(i),r=validate(raw);if(!unique.add(r.getString("id")))throw new Problem(400,"同步编号重复");if(raw.has("author"))r.put("author",raw.optString("author"));clean.put(r);}
          JSONArray syncPages=Workspaces.validate(data.optJSONArray("pages"));Workspaces.checkRecords(syncPages,clean);
          JSONObject reply;synchronized(lock){store.begin();try{long current=store.revision();if(!(data.opt("expectedRevision") instanceof Number)||data.getLong("expectedRevision")!=current)throw new Problem(409,"同步期间有新改动，将自动重试");JSONArray old=store.all();boolean changed=!SyncEngine.same(old,clean) ||!SyncEngine.canonical(store.pages()).equals(SyncEngine.canonical(syncPages));
            if(changed){if(mergeBackups!=null){File dir=new File(mergeBackups,"automatic");if(!dir.exists()&&!dir.mkdirs())throw new IOException("备份失败");File file=File.createTempFile("before-sync-",".json",dir);try(FileOutputStream f=new FileOutputStream(file)){f.write(new JSONObject().put("app",MergeEngine.APP).put("version",2).put("records",old) .put("pages",store.pages()) .toString().getBytes(StandardCharsets.UTF_8));}File[] prior=dir.listFiles();if(prior!=null&&prior.length>5){Arrays.sort(prior,(a,b)->Long.compare(b.lastModified(),a.lastModified()));for(int i=5;i<prior.length;i++)prior[i].delete();}}
              Map<String,JSONObject> existing=SyncEngine.map(old);for(JSONObject r:existing.values())if(!unique.contains(r.getString("id")))store.delete(r.getString("id"));long next=current+1;for(int i=0;i<clean.length();i++){JSONObject r=clean.getJSONObject(i),prev=existing.get(r.getString("id"));if(prev!=null&&SyncEngine.key(prev).equals(SyncEngine.key(r)))continue;r.put("revision",next).put("updatedBy",incoming.getJSONObject(i).optString("updatedBy",name)).put("updatedAt",System.currentTimeMillis());store.put(r);}store.setPages(syncPages); store.setRevision(next);current=next;
            }store.success();if(changed&&requireForeground)AutoSync.status="已接收同行者改动 · 本机已更新";reply=new JSONObject().put("ok",true).put("revision",current);
          }finally{store.end();}}json(out,200,reply);return;
        }
        if(path.equals("/api/merge")){
          if(!MergeEngine.APP.equals(data.optString("app"))||data.optInt("mergeProtocol")!=3)throw new Problem(400,"请双方升级到支持实验性合并的同一应用");
          JSONArray incoming=data.optJSONArray("records");if(incoming==null||incoming.length()>10000)throw new Problem(400,"合并数据格式错误");
          JSONObject reply; synchronized(lock){store.begin();try{
            long current=store.revision();if(!(data.opt("expectedRevision") instanceof Number)||data.getLong("expectedRevision")!=current)throw new Problem(409,"数据在预览后有更新，请重新预览合并");
            JSONArray existing=store.all(),oldPages=store.pages(),incomingPages=Workspaces.validate(data.optJSONArray("pages")),combinedPages=Workspaces.union(oldPages,incomingPages);Workspaces.checkRecords(incomingPages,incoming);MergeEngine.Plan plan=MergeEngine.plan(existing,incoming);
            int addedPages=combinedPages.length()-oldPages.length();if(plan.additions.length()>0||addedPages>0){if(mergeBackups!=null){if(!mergeBackups.exists()&&!mergeBackups.mkdirs())throw new IOException("无法创建备份目录");File backup=File.createTempFile("before-merge-",".json",mergeBackups);try(FileOutputStream output=new FileOutputStream(backup)){output.write(new JSONObject().put("app",MergeEngine.APP).put("version",2).put("records",existing).put("pages",oldPages).toString().getBytes(StandardCharsets.UTF_8));}}
              long next=current+1;
              for(int j=0;j<plan.additions.length();j++){JSONObject record=plan.additions.getJSONObject(j);JSONArray sheets=Workspaces.ids(record);for(int k=0;k<sheets.length();k++){String page=sheets.getString(k),tier=Workspaces.tier(record,page);Workspaces.set(record,page,tier,Workspaces.nextRank(store,page,tier));}
                record.put("revision",next).put("author",record.optString("author",name)).put("updatedBy",name).put("updatedAt",System.currentTimeMillis());store.put(record);}
              store.setPages(combinedPages);store.setRevision(next);current=next;
            }
            reply=new JSONObject().put("ok",true).put("addedPages",addedPages).put("added",plan.additions.length()).put("conflicts",plan.conflicts).put("revision",current);store.success();
          }finally{store.end();}}json(out,200,reply);return;
        }
        if(!path.equals("/api/mutate"))throw new Problem(404,"接口不存在");
        JSONArray ops=data.optJSONArray("ops");if(ops==null||ops.length()<1||ops.length()>10000)throw new Problem(400,"操作数量错误");
        long revision;
        synchronized(lock){store.begin();try{revision=store.revision()+1;
          for(int i=0;i<ops.length();i++){
            JSONObject op=ops.getJSONObject(i);String action=op.optString("action");
            if(action.equals("pagesImport")){JSONArray combined=Workspaces.union(store.pages(),Workspaces.validate(op.getJSONArray("pages")));store.setPages(combined);continue;}
            if(action.equals("pagePut")||action.equals("pagesCombine")){
              if(ops.length()!=1||!(op.opt("expectedBoard") instanceof Number)||op.getLong("expectedBoard")!=store.revision())throw new Problem(409,"工作表已变化，请刷新后重试");
              JSONArray pages=store.pages();
              if(action.equals("pagePut")){JSONObject page=Workspaces.validate(new JSONArray().put(op.getJSONObject("page"))).getJSONObject(0);JSONArray next=new JSONArray();boolean found=false;for(int j=0;j<pages.length();j++){JSONObject item=pages.getJSONObject(j);if(item.getString("id").equals(page.getString("id"))){next.put(page);found=true;}else next.put(item);}if(!found)next.put(page);store.setPages(Workspaces.validate(next));}
              else{String target=op.getString("target");JSONArray sources=op.getJSONArray("sources");if(!Workspaces.has(pages,target)||sources.length()<1)throw new Problem(400,"请选择来源工作表");Set<String> selected=new HashSet<>();for(int j=0;j<sources.length();j++){String id=sources.getString(j);if(id.equals(target)||!Workspaces.has(pages,id))throw new Problem(400,"来源工作表无效");selected.add(id);}JSONArray all=store.all();for(int j=0;j<all.length();j++){JSONObject r=all.getJSONObject(j);boolean match=false;for(String id:selected)if(Workspaces.includes(r,id))match=true;if(match&&!Workspaces.includes(r,target)){JSONArray ids=new JSONArray(Workspaces.ids(r).toString()).put(target);Workspaces.set(r,target,"pending",Workspaces.nextRank(store,target,"pending"));Workspaces.select(r,ids);r.put("revision",revision).put("updatedBy",name).put("updatedAt",System.currentTimeMillis());store.put(r);}}}

              continue;
            }
            if(action.equals("sheetSelect")){
              if(ops.length()!=1||!(op.opt("expectedBoard") instanceof Number)||op.getLong("expectedBoard")!=store.revision())throw new Problem(409,"项目或工作表已变化，请重新打开选择窗口");String page=op.getString("workspace");if(!Workspaces.has(store.pages(),page))throw new Problem(409,"工作表不存在");JSONArray selected=op.getJSONArray("ids");Set<String> wanted=new HashSet<>();for(int j=0;j<selected.length();j++){String id=selected.getString(j);if(store.get(id)==null)throw new Problem(409,"选择的项目已删除");wanted.add(id);}JSONArray all=store.all();for(int j=0;j<all.length();j++){JSONObject r=all.getJSONObject(j);boolean had=Workspaces.includes(r,page),add=wanted.contains(r.getString("id"));if(had==add)continue;JSONArray ids=new JSONArray();JSONArray prior=Workspaces.ids(r);for(int k=0;k<prior.length();k++)if(!prior.getString(k).equals(page))ids.put(prior.get(k));if(add){ids.put(page);Workspaces.set(r,page,"pending",Workspaces.nextRank(store,page,"pending"));}Workspaces.select(r,ids);r.put("revision",revision).put("updatedBy",name).put("updatedAt",System.currentTimeMillis());store.put(r);}continue;
            }
            if(action.equals("move")){
              if(ops.length()!=1||!(op.opt("expectedBoard") instanceof Number)||op.getLong("expectedBoard")!=store.revision())throw new Problem(409,"其他人刚更新了榜单，已保留对方修改。请同步后再拖一次。");
              String id=op.getString("id"),tier=op.getString("tier");if(!TIERS.contains(tier))throw new Problem(400,"档位错误");JSONObject moving=store.get(id);if(moving==null)throw new Problem(409,"该景点已被删除");
              String page=op.optString("workspace",Workspaces.of(moving));if(!Workspaces.includes(moving,page))throw new Problem(409,"该项目不在当前工作表");String before=op.optString("beforeId","");if(before.equals(id))throw new Problem(400,"不能放在自身前方");
              List<JSONObject> target=new ArrayList<>();JSONArray all=store.all();for(int j=0;j<all.length();j++){JSONObject item=all.getJSONObject(j);if(Workspaces.tier(item,page).equals(tier)&&Workspaces.includes(item,page)&&!item.getString("id").equals(id))target.add(item);}
              Collections.sort(target,(x,y)->Long.compare(Workspaces.rank(x,page),Workspaces.rank(y,page)));
              int at=target.size();if(!before.isEmpty()){at=-1;for(int j=0;j<target.size();j++)if(target.get(j).getString("id").equals(before))at=j;if(at<0)throw new Problem(409,"目标位置已变化，请刷新后重试");}
              moving.put("updatedBy",name).put("updatedAt",System.currentTimeMillis());target.add(at,moving);
              for(int j=0;j<target.size();j++){JSONObject item=target.get(j);if(Workspaces.rank(item,page)!=j||item.getString("id").equals(id)){Workspaces.set(item,page,tier,j);item.put("revision",revision);store.put(item);}}
              continue;
            }
            JSONObject r=null;String id;if(action.equals("put")||action.equals("import")){r=validate(op.getJSONObject("record"));id=r.getString("id");}else if(action.equals("delete")){id=op.getString("id");}else throw new Problem(400,"未知操作");
            JSONObject old=store.get(id);if(r!=null&&old!=null){if(!op.getJSONObject("record").has("workspace"))r.put("workspace",Workspaces.of(old));if(old.has("mergeOrigin"))r.put("mergeOrigin",old.getString("mergeOrigin"));if(!op.getJSONObject("record").has("mergeConflict")&&old.has("mergeConflict"))r.put("mergeConflict",old.getBoolean("mergeConflict"));}if(action.equals("import")&&old!=null)continue;
            if(!action.equals("import")){Object expected=op.opt("expected");boolean match=old==null?(expected==null||expected==JSONObject.NULL):(expected instanceof Number&&((Number)expected).longValue()==old.getLong("revision"));if(!match)throw new Problem(409,"该景点已被他人修改或排序。请先复制未保存内容，关闭后重新打开最新详情再修改。");}
            if(action.equals("delete"))store.delete(id);else{
              if(old!=null&&!op.getJSONObject("record").has("workspaceIds")){r.put("workspaceIds",Workspaces.ids(old));}
              Workspaces.checkRecords(store.pages(),new JSONArray().put(r));JSONArray selected=Workspaces.ids(r);String editedPage=op.optString("workspace",op.getJSONObject("record").optString("workspace",Workspaces.of(r)));String selectedTier=r.getString("tier");JSONObject positions=new JSONObject();for(int j=0;j<selected.length();j++){String page=selected.getString(j);String tier=action.equals("import")?Workspaces.tier(r,page):page.equals(editedPage)?selectedTier:old!=null&&Workspaces.includes(old,page)?Workspaces.tier(old,page):"pending";long rank=old!=null&&Workspaces.includes(old,page)&&Workspaces.tier(old,page).equals(tier)?Workspaces.rank(old,page):Workspaces.nextRank(store,page,tier);positions.put(page,new JSONObject().put("tier",tier).put("rank",rank));}r.put("placements",positions);Workspaces.select(r,selected);
              r.put("authorId",old!=null?old.optString("authorId",""):action.equals("import")?r.optString("authorId",""):userId);r.put("revision",revision).put("author",old==null?(action.equals("import")?r.optString("author",name):name):old.optString("author",name)).put("updatedBy",name).put("updatedAt",System.currentTimeMillis());store.put(r);
            }
          }store.setRevision(revision);store.success();}finally{store.end();}}
        json(out,200,new JSONObject().put("ok",true).put("revision",revision));
    }catch(Problem e){json(out,e.status,new JSONObject().put("error",e.getMessage()));}catch(JSONException|IllegalArgumentException e){json(out,400,new JSONObject().put("error","景点格式错误，请检查输入或备份"));}catch(Exception e){json(out,503,new JSONObject().put("error","操作失败，请确认主机存储空间及连接，然后重试"));}}catch(Exception ignored){}}
    static void json(OutputStream out,int status,JSONObject obj)throws IOException{send(out,status,"application/json; charset=utf-8",obj.toString().getBytes(StandardCharsets.UTF_8));}
    static void send(OutputStream out,int status,String type,byte[] bytes)throws IOException{String head="HTTP/1.1 "+status+" Response\r\nContent-Type: "+type+"\r\nContent-Length: "+bytes.length+"\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\nConnection: close\r\n\r\n";out.write(head.getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();}
    static final List<String> TIERS=Arrays.asList("hang","top","good","npc","low","pending");
    static JSONObject validate(JSONObject r)throws Exception{
        String[] keys={"id","name","description","tier"};int[] limits={100,80,3000,20};JSONObject clean=new JSONObject();for(int i=0;i<keys.length;i++){Object v=r.opt(keys[i]);if(!(v instanceof String)||((String)v).length()>limits[i])throw new Problem(400,"字段格式错误："+keys[i]);clean.put(keys[i],v);}if(r.getString("id").isEmpty()||r.getString("name").trim().isEmpty())throw new Problem(400,"请填写景点名称");
        if(!TIERS.contains(r.getString("tier")))throw new Problem(400,"档位错误");
        Object created=r.opt("created");if(!(created instanceof Number)||!Double.isFinite(((Number)created).doubleValue())||((Number)created).doubleValue()<0||((Number)created).doubleValue()>=1e15)throw new Problem(400,"创建时间错误");
        JSONArray photos=r.optJSONArray("photos");if(photos==null||photos.length()>6)throw new Problem(400,"每个景点最多 6 张图片");for(int i=0;i<photos.length();i++){Object p=photos.get(i);if(!(p instanceof String))throw new Problem(400,"图片格式错误");String value=(String)p;if(value.length()>12000000||!value.startsWith("data:image/"))throw new Problem(400,"图片格式错误");int comma=value.indexOf(',');if(comma<0||!Arrays.asList("data:image/jpeg;base64","data:image/png;base64","data:image/webp;base64").contains(value.substring(0,comma)))throw new Problem(400,"图片格式错误");byte[] raw;try{raw=Base64.getDecoder().decode(value.substring(comma+1));}catch(Exception e){throw new Problem(400,"图片内容错误");}boolean jpeg=raw.length>=3&&(raw[0]&255)==255&&(raw[1]&255)==216&&(raw[2]&255)==255;boolean png=raw.length>=8&&Arrays.equals(Arrays.copyOf(raw,8),new byte[]{(byte)137,80,78,71,13,10,26,10});boolean webp=raw.length>=12&&new String(raw,0,4,StandardCharsets.US_ASCII).equals("RIFF")&&new String(raw,8,4,StandardCharsets.US_ASCII).equals("WEBP");if(!jpeg&&!png&&!webp)throw new Problem(400,"图片内容错误");}
        for(String key:new String[]{"authorId","updatedById"}){if(r.has(key)){Object v=r.opt(key);if(!(v instanceof String)||(!((String)v).isEmpty()&&!((String)v).matches("[A-Za-z0-9_-]{16,80}")))throw new Problem(400,"用户身份格式错误");clean.put(key,v);}}
        if(r.has("author")){Object v=r.opt("author");if(!(v instanceof String)||((String)v).length()>100)throw new Problem(400,"记录人格式错误");clean.put("author",v);}
        if(r.has("mergeOrigin")){Object origin=r.opt("mergeOrigin");if(!(origin instanceof String)||((String)origin).isEmpty()||((String)origin).length()>100)throw new Problem(400,"合并来源编号错误");clean.put("mergeOrigin",origin);}
        if(r.has("mergeConflict")){if(!(r.opt("mergeConflict") instanceof Boolean))throw new Problem(400,"冲突标记格式错误");clean.put("mergeConflict",r.getBoolean("mergeConflict"));}
        Object workspace=r.opt("workspace");if(workspace!=null&&(!(workspace instanceof String)||!((String)workspace).matches("[A-Za-z0-9_-]{1,100}")))throw new Problem(400,"工作表编号错误");clean.put("workspace",workspace==null?"default":workspace);
        Workspaces.validateRecord(r,clean);return clean.put("created",created).put("photos",photos);
    }
    interface Store {JSONArray pages()throws Exception;void setPages(JSONArray pages)throws Exception;long revision()throws Exception;JSONArray all()throws Exception;JSONObject get(String id)throws Exception;void put(JSONObject record)throws Exception;void delete(String id)throws Exception;void begin();void success();void end();void setRevision(long v);}
    static class SqlStore extends SQLiteOpenHelper implements Store {
        SqlStore(Context c){super(c,"triprank.sqlite3",null,2);setWriteAheadLoggingEnabled(true);getWritableDatabase();}
        public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE records(id TEXT PRIMARY KEY,body TEXT NOT NULL)");d.execSQL("CREATE TABLE meta(id INTEGER PRIMARY KEY,revision INTEGER NOT NULL)");d.execSQL("INSERT INTO meta VALUES(1,0)");createPages(d);}
        private static void createPages(SQLiteDatabase d){d.execSQL("CREATE TABLE IF NOT EXISTS workspaces(id TEXT PRIMARY KEY,name TEXT NOT NULL)");d.execSQL("INSERT OR IGNORE INTO workspaces VALUES('default','默认工作表')");}
        public void onUpgrade(SQLiteDatabase d,int a,int b){if(a<2)createPages(d);}
        public JSONArray pages()throws Exception{JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name FROM workspaces ORDER BY rowid",null)){while(c.moveToNext())a.put(new JSONObject().put("id",c.getString(0)).put("name",c.getString(1)));}return a;}
        public void setPages(JSONArray pages)throws Exception{getWritableDatabase().delete("workspaces",null,null);for(int i=0;i<pages.length();i++){JSONObject p=pages.getJSONObject(i);ContentValues v=new ContentValues();v.put("id",p.getString("id"));v.put("name",p.getString("name"));if(getWritableDatabase().insertOrThrow("workspaces",null,v)<0)throw new android.database.SQLException("保存工作表失败");}}
        public long revision(){try(Cursor c=getReadableDatabase().rawQuery("SELECT revision FROM meta WHERE id=1",null)){c.moveToFirst();return c.getLong(0);}}
        public JSONArray all()throws Exception{JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM records",null)){while(c.moveToNext())a.put(new JSONObject(c.getString(0)));}return a;}
        public JSONObject get(String id)throws Exception{try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM records WHERE id=?",new String[]{id})){return c.moveToFirst()?new JSONObject(c.getString(0)):null;}}
        public void put(JSONObject r)throws Exception{ContentValues v=new ContentValues();v.put("id",r.getString("id"));v.put("body",r.toString());if(getWritableDatabase().insertWithOnConflict("records",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new android.database.SQLException("保存景点失败");}
        public void delete(String id){getWritableDatabase().delete("records","id=?",new String[]{id});}
        public void begin(){getWritableDatabase().beginTransaction();}public void success(){getWritableDatabase().setTransactionSuccessful();}public void end(){getWritableDatabase().endTransaction();}public void setRevision(long v){getWritableDatabase().execSQL("UPDATE meta SET revision=? WHERE id=1",new Object[]{v});}
    }
}
