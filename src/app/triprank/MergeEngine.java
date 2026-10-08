package app.triprank;
import org.json.*;import java.util.*;import java.security.*;import java.nio.charset.StandardCharsets;
/** Experimental append-only union. Never overwrites local records or propagates deletion. */
final class MergeEngine {
 static final String APP="trip-rank";
 static final String[] FIELDS={"name","description","tier","photos","workspace"};
 static class Plan {final JSONArray additions=new JSONArray();int conflicts=0;}
 static String origin(JSONObject r){return r.optString("mergeOrigin",r.optString("id"));}
 static String hash(String text)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:digest)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
 static String content(JSONObject r)throws Exception{JSONArray values=new JSONArray().put(r.get("name")).put(r.get("description")).put(r.get("photos"));List<String> ids=new ArrayList<>();JSONArray sheets=Workspaces.ids(r);for(int i=0;i<sheets.length();i++)ids.add(sheets.getString(i));Collections.sort(ids);for(String id:ids)values.put(new JSONArray().put(id).put(Workspaces.tier(r,id)));return values.toString();}
 static Plan plan(JSONArray local,JSONArray incoming)throws Exception{
  Set<String> ids=new HashSet<>(),versions=new HashSet<>(),groups=new HashSet<>();
  for(int j=0;j<local.length();j++){JSONObject r=local.getJSONObject(j);String base=origin(r);ids.add(r.getString("id"));groups.add(base);versions.add(base+":"+hash(content(r)));}
  Plan result=new Plan();
  for(int j=0;j<incoming.length();j++){JSONObject raw=incoming.getJSONObject(j),r=LocalServer.validate(raw);String base=origin(r),fingerprint=hash(content(r)),key=base+":"+fingerprint;if(versions.contains(key))continue;
   boolean conflict=groups.contains(base);String id=r.getString("id");if(ids.contains(id))id="merge_"+hash(key);if(ids.contains(id))throw new IllegalArgumentException("合并编号冲突，已停止，未覆盖任何数据");
   r.put("id",id).put("mergeOrigin",base).put("mergeConflict",conflict||r.optBoolean("mergeConflict",false));
   if(raw.has("author"))r.put("author",raw.optString("author"));if(raw.has("updatedBy"))r.put("updatedBy",raw.optString("updatedBy"));
   result.additions.put(r);if(r.optBoolean("mergeConflict"))result.conflicts++;ids.add(id);groups.add(base);versions.add(key);
  }return result;
 }
}
