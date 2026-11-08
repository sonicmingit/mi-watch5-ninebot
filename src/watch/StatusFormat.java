package local.ninebot.account;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Conservative presentation of typed beans whose primitive presence is unavailable. */
public final class StatusFormat {
    private StatusFormat() {}
    public static String speed(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) return "未知（无有效速度）";
        if (value == 0) return "未知（0或字段缺失）";
        return String.format(Locale.CHINA,"%.1f（接口值）",value);
    }
    public static String rawSpeed(boolean present,Object value) {
        if(!present)return "未知（字段缺失）";
        if(!(value instanceof Number))return "未知（非数值或空值）";
        double n=((Number)value).doubleValue();
        if(Double.isNaN(n)||Double.isInfinite(n)||n<0)return "未知（无有效速度）";
        return n==0?"0（接口值）":String.format(Locale.CHINA,"%.1f（接口值）",n);
    }
    public static String rawPower(boolean present,Object value) {
        if(!present)return "未知（字段缺失）";
        if(!(value instanceof Number))return "未知（非数值或空值）";
        double n=((Number)value).doubleValue();
        if(Double.isNaN(n)||Double.isInfinite(n)||n!=Math.rint(n)||Math.abs(n)>999999)return "未知枚举";
        return String.valueOf((int)n)+"（接口状态码）";
    }
    public static String rawReport(boolean present,Object value,long now) {
        if(!present||!(value instanceof Number))return "未知";
        double n=((Number)value).doubleValue();
        if(Double.isNaN(n)||Double.isInfinite(n)||n!=Math.rint(n)||n<0||n>9999999999999L)return "未知";
        return report(String.valueOf(((Number)value).longValue()),now);
    }
    public static String code(String value) {
        if(value==null || value.trim().isEmpty())return "未知";
        // No server text is rendered: only short numeric enum values are accepted.
        if(!value.matches("-?[0-9]{1,6}"))return "未知枚举";
        return value+"（含义待验证）";
    }
    public static String acc(String value) {
        // ACC=0 was checked against this vehicle switched off on 2026-11-08.
        return "0".equals(value)?"已关机（ACC=0）":code(value);
    }
    public static String lock(String value) {
        // lock_status=1 was checked against this vehicle locked on 2026-11-08.
        return "1".equals(value)?"已锁定（锁码=1）":code(value);
    }
    public static long reportMillis(String value) {
        if(value==null || value.trim().isEmpty())return 0;
        try {
            long n=Long.parseLong(value.trim());
            if(n>1000000000L && n<10000000000L)return n*1000L;
            if(n>1000000000000L && n<10000000000000L)return n;
        }catch(Exception ignored){}
        for(String pattern:new String[]{"yyyy-MM-dd HH:mm:ss","yyyy-MM-dd'T'HH:mm:ssXXX","yyyy-MM-dd'T'HH:mm:ss.SSSXXX"}) {
            try {
                SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.CHINA);f.setLenient(false);
                java.text.ParsePosition p=new java.text.ParsePosition(0);Date d=f.parse(value,p);
                if(d!=null && p.getIndex()==value.length())return d.getTime();
            }catch(Exception ignored){}
        }
        return 0;
    }
    public static String report(String raw,long now) {
        long t=reportMillis(raw);
        if(t==0)return "未知";
        String text=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date(t));
        if(t>now+60000)return text+"（时间异常）";
        return text+(now-t>120000?"（旧上报）":"");
    }
    public static String clock(long t) {
        return t==0?"尚未查询":new SimpleDateFormat("HH:mm:ss",Locale.CHINA).format(new Date(t));
    }
}
