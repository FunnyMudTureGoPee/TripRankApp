package app.triprank;
/** Connection routing is independent of Activity lifecycle and ephemeral proxy ports. */
final class JoinRoute {
 enum Target { READY, WAITING, DIRECT, RECONNECT, MISSING }
 static Target decide(boolean active,boolean hasPage,boolean hasInvite,boolean needsWifi){
  if(active)return hasPage?Target.READY:Target.WAITING;
  if(!hasInvite)return Target.MISSING;
  return needsWifi?Target.RECONNECT:Target.DIRECT;
 }
}
