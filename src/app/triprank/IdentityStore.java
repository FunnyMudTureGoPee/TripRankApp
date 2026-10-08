package app.triprank;
import android.content.*;import org.json.*;import java.util.UUID;
final class IdentityStore {
 static synchronized String get(Context c){return get(c,"");}
 static synchronized String get(Context c,String suggestion){SharedPreferences p=c.getSharedPreferences("member-identity",0);String name=p.getString("name","");if(name.isEmpty()&&suggestion!=null&&!suggestion.trim().isEmpty()&&suggestion.trim().length()<=20)name=suggestion.trim();if(name.isEmpty())name="旅伴"+UUID.randomUUID().toString().substring(0,4);return select(c,name);}
 static synchronized String select(Context c,String input){try{String name=input==null?"":input.trim();if(name.isEmpty()||name.length()>20)throw new IllegalArgumentException();SharedPreferences p=c.getSharedPreferences("member-identity",0);JSONObject profiles=new JSONObject(p.getString("profiles","{}"));String id=profiles.optString(name,"");if(id.isEmpty()){id=UUID.randomUUID().toString();profiles.put(name,id);}p.edit().putString("name",name).putString("profiles",profiles.toString()).commit();return new JSONObject().put("id",id).put("name",name).toString();}catch(Exception e){return "{}";}}
}
