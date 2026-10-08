package app.triprank;
import org.json.*;import java.util.*;
/** Three-way reconciliation against the last shared snapshot. */
final class SyncEngine {
 static String canonical(Object value)throws Exception{if(value==null||value==JSONObject.NULL)return "null";if(value instanceof JSONObject){JSONObject o=(JSONObject)value;List<String> keys=new ArrayList<>();Iterator<String> it=o.keys();while(it.hasNext())keys.add(it.next());Collections.sort(keys);StringBuilder s=new StringBuilder("{");for(String k:keys)s.append(JSONObject.quote(k)).append(':').append(canonical(o.get(k))).append(',');return s.append('}').toString();}if(value instanceof JSONArray){StringBuilder s=new StringBuilder("[");JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)s.append(canonical(a.get(i))).append(',');return s.append(']').toString();}return value instanceof String?JSONObject.quote((String)value):String.valueOf(value);}
 static JSONObject meaningful(JSONObject r)throws Exception{JSONObject v=LocalServer.validate(r);v.remove("updatedBy");v.remove("updatedById");v.remove("revision");v.remove("updatedAt");if(v.has("workspaceIds")){JSONArray a=v.getJSONArray("workspaceIds");List<String> ids=new ArrayList<>();for(int i=0;i<a.length();i++)ids.add(a.getString(i));Collections.sort(ids);v.put("workspaceIds",new JSONArray(ids));}if(v.has("placements")){v.remove("workspace");v.remove("tier");v.remove("rank");}return v;}
 static String key(JSONObject r)throws Exception{return r==null?"null":canonical(meaningful(r));}
 static String version(JSONObject r)throws Exception{JSONObject c=meaningful(r);c.remove("id");c.remove("mergeConflict");c.remove("mergeOrigin");return MergeEngine.hash(canonical(c));}
 static Map<String,JSONObject> map(JSONArray a)throws Exception{Map<String,JSONObject> m=new TreeMap<>();for(int i=0;i<a.length();i++){JSONObject r=a.getJSONObject(i);String id=r.getString("id");if(m.put(id,r)!=null)throw new IllegalArgumentException("重复记录编号");}return m;}
 static class Result {JSONArray records=new JSONArray(),pages=new JSONArray();int conflicts;}
 static JSONObject copy(JSONObject r)throws Exception{return new JSONObject(r.toString());}
 static Result merge(JSONArray base,JSONArray local,JSONArray remote,JSONArray localPages,JSONArray remotePages,JSONArray basePages)throws Exception{
  Result result=new Result();Map<String,JSONObject> b=map(base),a=map(local),c=map(remote),out=new TreeMap<>();Set<String> ids=new TreeSet<>();ids.addAll(b.keySet());ids.addAll(a.keySet());ids.addAll(c.keySet());List<JSONObject> conflicts=new ArrayList<>();
  for(String id:ids){JSONObject old=b.get(id),left=a.get(id),right=c.get(id),chosen=null;String x=key(left),y=key(right),z=key(old);
   if(x.equals(y))chosen=left;else if(x.equals(z))chosen=right;else if(y.equals(z))chosen=left;else if(left==null||right==null){chosen=copy(left==null?right:left);chosen.put("mergeConflict",true).put("mergeOrigin",MergeEngine.origin(chosen));result.conflicts++;}
   else if(version(left).equals(version(right))){chosen=copy(left);chosen.put("mergeConflict",left.optBoolean("mergeConflict")||right.optBoolean("mergeConflict"));}
   else{boolean first=version(left).compareTo(version(right))<=0;chosen=first?left:right;JSONObject other=copy(first?right:left);String origin=MergeEngine.origin(other);other.put("id","sync_"+MergeEngine.hash(origin+":"+version(other))).put("mergeOrigin",origin).put("mergeConflict",true);conflicts.add(other);result.conflicts++;}
   if(chosen!=null)out.put(id,copy(chosen));
  }
  Set<String> seen=new HashSet<>();for(JSONObject r:out.values())seen.add(MergeEngine.origin(r)+":"+version(r));for(JSONObject r:conflicts){String v=MergeEngine.origin(r)+":"+version(r);if(seen.add(v)){if(out.containsKey(r.getString("id")))throw new IllegalArgumentException("冲突编号重复");out.put(r.getString("id"),r);}}
  for(JSONObject r:out.values())result.records.put(r);
  Map<String,JSONObject> pages=map(localPages),otherPages=map(remotePages),oldPages=map(basePages);for(JSONObject r:otherPages.values()){String id=r.getString("id");JSONObject p=pages.get(id),old=oldPages.get(id);if(p==null||canonical(p).equals(canonical(old)))pages.put(id,r);else if(!canonical(r).equals(canonical(old))&&canonical(r).compareTo(canonical(p))<0)pages.put(id,r);}for(JSONObject p:pages.values())result.pages.put(p);return result;
 }
 static boolean same(JSONArray a,JSONArray b)throws Exception{Map<String,JSONObject> x=map(a),y=map(b);if(!x.keySet().equals(y.keySet()))return false;for(String id:x.keySet())if(!key(x.get(id)).equals(key(y.get(id))))return false;return true;}
}
