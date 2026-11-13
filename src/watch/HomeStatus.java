package local.ninebot.account;

/** Same-response location is authoritative; engine is usable only from the current round. */
public final class HomeStatus {
    public enum State {
        DRIVING("行驶中"), POWERED("已上电"), PARKED("已驻车"), OFF("已关机"), UNKNOWN("状态待确认");
        public final String label; State(String text){label=text;}
    }
    private HomeStatus(){}
    public static Integer binary(Object value) {
        Double n=StatusFormat.finiteNumber(value);
        return n!=null&&(n==0||n==1)?n.intValue():null;
    }
    public static State classify(Double speed,Integer power,boolean engineOk,Integer acc,Integer lock) {
        if(speed==null||Double.isNaN(speed)||Double.isInfinite(speed)||speed<0||power==null)return State.UNKNOWN;
        if(speed>0)return power==1?State.DRIVING:State.UNKNOWN;
        if(power==1)return State.POWERED;
        if(power!=0)return State.UNKNOWN;
        return engineOk&&Integer.valueOf(0).equals(acc)&&Integer.valueOf(1).equals(lock)?State.PARKED:State.OFF;
    }
}
