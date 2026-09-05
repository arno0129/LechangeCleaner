package io.github.arno0129.lechangecleaner;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedModule;

public class LechangeHookModule extends XposedModule {
    private static final String TAG = "LechangeHookShell";
    private static final String TARGET_PACKAGE = "com.mm.android.lc";
    private static final long[] SCAN_DELAYS = {0, 32, 150, 400, 900, 1800};

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private boolean targetLoaded;
    private boolean mainProcess;
    private boolean attachHookInstalled;
    private boolean adHooksInstalled;
    private boolean uiHooksInstalled;
    private boolean promoHooksInstalled;
    private final WeakHashMap<View, Integer> originalHeights = new WeakHashMap<>();
    private final WeakHashMap<View, Integer> mineExchangeOffsets = new WeakHashMap<>();
    private final WeakHashMap<View, Runnable> pendingRootScans = new WeakHashMap<>();
    private final WeakHashMap<View, Long> lastRootScanAt = new WeakHashMap<>();

    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        mainProcess = TARGET_PACKAGE.equals(param.getProcessName());
        try { prefs = getRemotePreferences(Prefs.GROUP); } catch (Throwable ignored) {}
        diagnostic(Log.INFO, TAG, "module loaded in process=" + param.getProcessName());
    }

    @Override public void onPackageLoaded(PackageLoadedParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) return;
        targetLoaded = true;
        installApplicationAttachHook();
    }

    @Override public void onPackageReady(PackageReadyParam param) {
        if (!targetLoaded || !TARGET_PACKAGE.equals(param.getPackageName())) return;
        ClassLoader loader = param.getClassLoader();
        if (enabled(Prefs.HIDE_SPLASH)) installAdHooks(loader);
        if (mainProcess) {
            installUiHooks(loader);
            if (enabled(Prefs.HIDE_MONITOR_PROMO)) installPromoHooks(loader);
        }
    }

    private boolean enabled(String key) {
        try { return prefs == null || prefs.getBoolean(key, true); }
        catch (Throwable ignored) { return true; }
    }

    /** No file, preference buffer or LSPosed framework log is produced. */
    private void diagnostic(int priority, String tag, String message) {
    }

    private void diagnostic(int priority, String tag, String message, Throwable error) {
    }

    private void installApplicationAttachHook() {
        if (attachHookInstalled) return;
        try {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            attach.setAccessible(true);
            hook(attach).intercept(chain -> {
                Context context = (Context) chain.getArg(0);
                if (context != null && TARGET_PACKAGE.equals(context.getPackageName())
                        && enabled(Prefs.HIDE_SPLASH)) installAdHooks(context.getClassLoader());
                return chain.proceed();
            });
            attachHookInstalled = true;
        } catch (Throwable t) { diagnostic(Log.ERROR, TAG, "attach hook failed", t); }
    }

    private synchronized void installAdHooks(ClassLoader loader) {
        if (adHooksInstalled || loader == null) return;
        try {
            Class<?> sdk = Class.forName("com.lc.ad.LCAdSdk", false, loader);
            hookFalse(sdk.getDeclaredMethod("adEnable"));
            hookFalse(sdk.getDeclaredMethod("adEnable", String.class));
            hookFalse(sdk.getDeclaredMethod("adEnable", String.class, boolean.class));
            Class<?> listener = Class.forName("com.lc.ad.listener.OnHotSplashListener", false, loader);
            Method method = sdk.getDeclaredMethod("setHotSplashListener", listener);
            method.setAccessible(true);
            hook(method).intercept(chain -> null);
            adHooksInstalled = true;
            diagnostic(Log.INFO, TAG, "splash hooks installed");
        } catch (Throwable t) { diagnostic(Log.ERROR, TAG, "splash hooks failed", t); }
    }

    private void hookFalse(Method method) {
        method.setAccessible(true);
        hook(method).intercept(chain -> false);
    }

    private synchronized void installPromoHooks(ClassLoader loader) {
        if (promoHooksInstalled) return;
        try {
            Class<?> provider = Class.forName("com.mm.android.lc.provider.LCAppProvider", false, loader);
            for (Method method : provider.getDeclaredMethods()) {
                if (!"showDynamicDialog".equals(method.getName())) continue;
                method.setAccessible(true);
                hook(method).intercept(chain -> {
                    Object activity = chain.getArg(0);
                    boolean monitorActivity = activity != null
                            && activity.getClass().getName().contains("LCMediaProfessionalActivity");
                    if (enabled(Prefs.HIDE_MONITOR_PROMO) && monitorActivity) {
                        diagnostic(Log.INFO, TAG, "blocked monitor promotion dialog");
                        return null;
                    }
                    return chain.proceed();
                });
            }
            promoHooksInstalled = true;
        } catch (Throwable t) { diagnostic(Log.ERROR, TAG, "promotion hooks failed", t); }
    }

    private synchronized void installUiHooks(ClassLoader loader) {
        if (uiHooksInstalled) return;
        try {
            Method resumed = Activity.class.getDeclaredMethod("onPostResume");
            resumed.setAccessible(true);
            hook(resumed).intercept(chain -> {
                Object result = chain.proceed();
                Activity activity = (Activity) chain.getThisObject();
                if (TARGET_PACKAGE.equals(activity.getPackageName())) scheduleScans(activity.getWindow().getDecorView());
                return result;
            });

            Method addView = ViewGroup.class.getDeclaredMethod(
                    "addView", View.class, int.class, ViewGroup.LayoutParams.class);
            addView.setAccessible(true);
            hook(addView).intercept(chain -> {
                Object result = chain.proceed();
                View child = (View) chain.getArg(0);
                Context context = child == null ? null : child.getContext();
                if (context != null && TARGET_PACKAGE.equals(context.getPackageName())) {
                    scheduleScanSoon(child.getRootView());
                }
                return result;
            });

            try {
                Class<?> fragment = Class.forName(
                        "com.mm.lc.media.professional.fragment.LCMediaLivePreviewFragment", false, loader);
                Method bind = fragment.getDeclaredMethod("bindView", View.class);
                bind.setAccessible(true);
                hook(bind).intercept(chain -> {
                    Object result = chain.proceed();
                    View root = (View) chain.getArg(0);
                    scheduleScans(root);
                    return result;
                });
            } catch (Throwable t) { diagnostic(Log.WARN, TAG, "monitor bind hook unavailable", t); }

            uiHooksInstalled = true;
            diagnostic(Log.INFO, TAG, "layout hooks installed");
        } catch (Throwable t) { diagnostic(Log.ERROR, TAG, "layout hooks failed", t); }
    }

    private void scheduleScans(View root) {
        if (root == null) return;
        for (long delay : SCAN_DELAYS) mainHandler.postDelayed(() -> applyRules(root), delay);
    }

    private void scheduleScanSoon(View root) {
        if (root == null || !root.isAttachedToWindow() || pendingRootScans.containsKey(root)) return;
        Long previous = lastRootScanAt.get(root);
        long now = android.os.SystemClock.uptimeMillis();
        if (previous != null && now - previous < 120L) return;
        Runnable task = () -> {
            pendingRootScans.remove(root);
            lastRootScanAt.put(root, android.os.SystemClock.uptimeMillis());
            applyRules(root);
        };
        pendingRootScans.put(root, task);
        mainHandler.postDelayed(task, 32);
    }

    private void applyRules(View root) {
        if (root == null) return;
        try {
            applyMainTabs(root);
            applyHomeCloudActions(root);
            if (enabled(Prefs.HIDE_HOME_BANNER)) {
                View footer = findByEntry(root, "lv_homeList_listFooterLarge_itemRoot");
                if (footer != null) {
                    View banner = findByEntry(footer, "advertise_content");
                    collapse(banner);
                    View filler = findByEntry(footer, "view_positiondown");
                    View endTip = findByEntry(footer, "end_tip");
                    collapse(filler);
                    if (endTip != null && endTip.getHeight() > 0) {
                        int[] footerLocation = new int[2];
                        int[] tipLocation = new int[2];
                        footer.getLocationOnScreen(footerLocation);
                        endTip.getLocationOnScreen(tipLocation);
                        int padding = Math.round(12f * footer.getResources().getDisplayMetrics().density);
                        resizeHeight(footer, Math.max(endTip.getHeight(),
                                tipLocation[1] + endTip.getHeight() - footerLocation[1] + padding));
                    }
                }
            }
            if (enabled(Prefs.HIDE_MINE_CHECKIN)) collapse(findByEntry(root, "cl_cheng_dou_view"));
            if (enabled(Prefs.HIDE_MINE_AD)) collapse(findByEntry(root, "cl_advertise_view"));
            applyMineReact(root);
            if (enabled(Prefs.HIDE_MONITOR_SERVICE_BAR)) collapse(findByEntry(root, "service_layout"));
            applyMonitorTabs(root);
        } catch (Throwable t) { diagnostic(Log.WARN, TAG, "layout scan failed", t); }
    }

    private void applyMainTabs(View root) {
        ViewGroup parent = asGroup(findByEntry(root, "main_tab_normal_layout"));
        if (parent == null) return;
        boolean changed = false;
        changed |= hideTab(parent, "tab_shop_layout", Prefs.HIDE_TAB_PRODUCT);
        changed |= hideTab(parent, "tab_ai_layout", Prefs.HIDE_TAB_AI);
        changed |= hideTab(parent, "tab_discovery_layout", Prefs.HIDE_TAB_COMMUNITY);
        if (changed) equalizeVisibleChildren(parent);
    }

    private void applyHomeCloudActions(View root) {
        if (!enabled(Prefs.HIDE_HOME_CLOUD_ACTION)) return;
        for (View action : findAllByEntry(root, "action_item_3")) {
            ViewGroup parent = asGroup(action.getParent());
            if (parent == null || !"single_ipc_action_bar".equals(entryName(parent))) continue;
            hideNavItem(action);
            equalizeVisibleChildren(parent);
        }
    }

    private boolean hideTab(ViewGroup parent, String entry, String key) {
        View view = findByEntry(parent, entry);
        if (view == null || !enabled(key)) return false;
        if (view.isSelected()) {
            View home = findByEntry(parent, "tab_home_layout");
            if (home != null) home.performClick();
        }
        hideNavItem(view);
        return true;
    }

    private void applyMonitorTabs(View root) {
        if (!enabled(Prefs.HIDE_MONITOR_SERVICE_TAB)) return;
        View service = findByEntry(root, "play_navgate_bottom_service");
        if (service == null) return;
        ViewGroup parent = asGroup(service.getParent());
        if (service.isSelected() && parent != null && parent.getChildCount() > 0) parent.getChildAt(0).performClick();
        hideNavItem(service);
        if (parent != null) equalizeVisibleChildren(parent);
    }

    private void applyMineReact(View root) {
        if (!enabled(Prefs.HIDE_MINE_EXCHANGE) && !enabled(Prefs.HIDE_MINE_RECOMMEND)) return;
        View react = findByEntry(root, "react_view");
        if (react == null || !hasDescriptionPrefix(react, "MyAddSeriviceRecommend_")) return;
        boolean exchange = enabled(Prefs.HIDE_MINE_EXCHANGE);
        boolean recommend = enabled(Prefs.HIDE_MINE_RECOMMEND);
        if (exchange && recommend) {
            collapse(react);
            return;
        }
        List<View> descendants = flatten(react);
        View exchangeView = null;
        List<View> recommendations = new ArrayList<>();
        for (View view : descendants) {
            CharSequence d = view.getContentDescription();
            String desc = d == null ? "" : d.toString();
            if ("MyAddSeriviceRecommend_bgImage".equals(desc)) exchangeView = view;
            else if (desc.startsWith("MyAddSeriviceRecommend_") && desc.endsWith("Strategy")) recommendations.add(view);
        }
        if (exchange && !recommend && exchangeView != null && !recommendations.isEmpty()) {
            Integer offset = mineExchangeOffsets.get(react);
            if (offset == null) {
                int[] rootLocation = new int[2];
                react.getLocationOnScreen(rootLocation);
                int firstTop = Integer.MAX_VALUE;
                for (View item : recommendations) {
                    int[] itemLocation = new int[2];
                    item.getLocationOnScreen(itemLocation);
                    firstTop = Math.min(firstTop, itemLocation[1]);
                }
                offset = Math.max(0, firstTop - rootLocation[1]);
                mineExchangeOffsets.put(react, offset);
                originalHeights.put(react, react.getHeight());
            }
            for (View item : recommendations) item.setTranslationY(-offset);
            Integer original = originalHeights.get(react);
            if (original != null) resizeHeight(react, Math.max(1, original - offset));
        }
        if (exchange) collapse(exchangeView);
        if (recommend) {
            for (View view : recommendations) collapse(view);
            if (exchangeView != null && exchangeView.getHeight() > 0) resizeHeight(react, exchangeView.getHeight());
        }
    }

    private void equalizeVisibleChildren(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            ViewGroup.LayoutParams raw = child.getLayoutParams();
            if (raw instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
                lp.width = 0; lp.weight = 1f;
                if (lp.height == 0) lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                child.setLayoutParams(lp);
            }
        }
        parent.requestLayout();
    }

    private void hideNavItem(View view) {
        if (view == null) return;
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
            lp.width = 0; lp.weight = 0f;
            view.setLayoutParams(lp);
        }
        view.setVisibility(View.GONE);
    }

    private void collapse(View view) {
        if (view == null) return;
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null) {
            lp.height = 0;
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                mlp.setMargins(0, 0, 0, 0);
            }
            view.setLayoutParams(lp);
        }
        view.setMinimumHeight(0);
        view.setPadding(0, 0, 0, 0);
        view.setVisibility(View.GONE);
        ViewParentCompat.request(view);
    }

    private void resizeHeight(View view, int height) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null && height > 0) { lp.height = height; view.setLayoutParams(lp); view.requestLayout(); }
    }

    private View findByEntry(View root, String wanted) {
        for (View view : flatten(root)) {
            int id = view.getId();
            if (id == View.NO_ID) continue;
            try { if (wanted.equals(view.getResources().getResourceEntryName(id))) return view; }
            catch (Throwable ignored) {}
        }
        return null;
    }

    private List<View> findAllByEntry(View root, String wanted) {
        ArrayList<View> matches = new ArrayList<>();
        for (View view : flatten(root)) if (wanted.equals(entryName(view))) matches.add(view);
        return matches;
    }

    private String entryName(View view) {
        if (view == null || view.getId() == View.NO_ID) return "";
        try { return view.getResources().getResourceEntryName(view.getId()); }
        catch (Throwable ignored) { return ""; }
    }

    private boolean hasDescriptionPrefix(View root, String prefix) {
        for (View view : flatten(root)) {
            CharSequence d = view.getContentDescription();
            if (d != null && d.toString().startsWith(prefix)) return true;
        }
        return false;
    }

    private List<View> flatten(View root) {
        ArrayList<View> result = new ArrayList<>();
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            View view = queue.removeFirst(); result.add(view);
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
            }
        }
        return result;
    }

    private ViewGroup asGroup(Object view) { return view instanceof ViewGroup ? (ViewGroup) view : null; }

    private static final class ViewParentCompat {
        static void request(View view) {
            if (view.getParent() instanceof View) ((View) view.getParent()).requestLayout();
        }
    }
}
