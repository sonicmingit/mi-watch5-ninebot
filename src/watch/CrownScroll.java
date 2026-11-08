package local.ninebot.account;

import android.app.Activity;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;

/** A transparent window callback decorator; never synthesizes clicks. */
public final class CrownScroll implements InvocationHandler {
    private final Activity activity;
    private final Window.Callback original;
    private Window.Callback proxy;
    private CrownScroll(Activity a,Window.Callback c){activity=a;original=c;}
    public static void install(Activity a) {
        Window.Callback c=a.getWindow().getCallback();
        if(c==null || own(c)!=null)return;
        CrownScroll h=new CrownScroll(a,c);
        h.proxy=(Window.Callback)Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),new Class<?>[]{Window.Callback.class},h);
        a.getWindow().setCallback(h.proxy);
    }
    private static CrownScroll own(Window.Callback c) {
        if(c!=null && Proxy.isProxyClass(c.getClass())) {
            InvocationHandler h=Proxy.getInvocationHandler(c);if(h instanceof CrownScroll)return (CrownScroll)h;
        }
        return null;
    }
    public static void uninstall(Activity a) {
        CrownScroll h=own(a.getWindow().getCallback());if(h!=null)a.getWindow().setCallback(h.original);
    }
    public Object invoke(Object p,Method m,Object[] args)throws Throwable {
        if(m.getName().equals("dispatchGenericMotionEvent") && args!=null && args[0] instanceof MotionEvent) {
            if(scroll((MotionEvent)args[0]))return true;
        }
        try{return m.invoke(original,args);}catch(InvocationTargetException e){throw e.getCause();}
    }
    static boolean isScroller(View v) {
        String n=v.getClass().getName();
        return v instanceof ScrollView || v instanceof AbsListView || n.endsWith("NestedScrollView") || n.endsWith("RecyclerView");
    }
    private boolean visible(View v){return v.isShown() && v.getAlpha()>0 && v.getGlobalVisibleRect(new Rect());}
    private View find(View v,int direction) {
        if(!visible(v))return null;
        if(v instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)v;
            for(int i=g.getChildCount()-1;i>=0;i--){View r=find(g.getChildAt(i),direction);if(r!=null)return r;}
        }
        return isScroller(v)&&v.canScrollVertically(direction)?v:null;
    }
    private boolean scroll(MotionEvent e) {
        if(e.getActionMasked()!=MotionEvent.ACTION_SCROLL || !e.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER))return false;
        float axis=e.getAxisValue(MotionEvent.AXIS_SCROLL);
        if(Float.isNaN(axis)||Float.isInfinite(axis)||axis==0)return false;
        float factor=ViewConfiguration.get(activity).getScaledVerticalScrollFactor();
        int delta=Math.round(-axis*factor);if(delta==0)delta=axis>0?-1:1;
        // Limit a malformed or accelerated event to one viewport.
        int limit=Math.max(1,activity.getWindow().getDecorView().getHeight());
        delta=Math.max(-limit,Math.min(limit,delta));int direction=delta>0?1:-1;
        View target=null,focus=activity.getCurrentFocus();
        for(View v=focus;v!=null;) {
            if(visible(v)&&isScroller(v)&&v.canScrollVertically(direction)){target=v;break;}
            android.view.ViewParent parent=v.getParent();v=parent instanceof View?(View)parent:null;
        }
        if(target==null)target=find(activity.getWindow().getDecorView(),direction);
        if(target==null)return false;
        if(target instanceof AbsListView)((AbsListView)target).scrollListBy(delta);
        else target.scrollBy(0,delta);
        return true;
    }
}
