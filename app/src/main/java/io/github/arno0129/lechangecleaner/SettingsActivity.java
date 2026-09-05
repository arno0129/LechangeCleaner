package io.github.arno0129.lechangecleaner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class SettingsActivity extends Activity implements XposedServiceHelper.OnServiceListener {
    private static final String TARGET = "com.mm.android.lc";
    private static final int ACCENT = 0xFFFF6B35;
    private static final int BACKGROUND = 0xFFF5F7FA;
    private static final int TEXT = 0xFF172026;
    private static final int SUBTEXT = 0xFF74818B;
    private static final int DIVIDER = 0xFFE8ECEF;

    private final Map<String, Switch> switches = new LinkedHashMap<>();
    private SharedPreferences local;
    private XposedService service;
    private TextView pageTitle;
    private FrameLayout titleStage;
    private TextView serviceValue;
    private TextView enabledCountValue;
    private TextView overviewTab;
    private TextView featuresTab;
    private ScrollView overviewPage;
    private ScrollView featuresPage;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        configureSystemBars();
        local = getSharedPreferences(Prefs.GROUP, MODE_PRIVATE);
        initializeDefaults();
        setContentView(buildShell());
        ((LechangeApp) getApplication()).addServiceListener(this);
        showDisclaimer();
    }

    @Override protected void onDestroy() {
        ((LechangeApp) getApplication()).removeServiceListener(this);
        super.onDestroy();
    }

    private View buildShell() {
        int status = systemBarHeight("status_bar_height");
        int navigation = systemBarHeight("navigation_bar_height");
        int headerHeight = status + dp(48);
        int bottomHeight = navigation + dp(70);

        FrameLayout shell = new FrameLayout(this);
        shell.setBackgroundColor(BACKGROUND);
        FrameLayout content = new FrameLayout(this);
        shell.addView(content, frame(-1, -1, Gravity.NO_GRAVITY));

        overviewPage = makePage(headerHeight, bottomHeight);
        buildOverview((LinearLayout) overviewPage.getChildAt(0));
        content.addView(overviewPage, frame(-1, -1, Gravity.NO_GRAVITY));

        featuresPage = makePage(headerHeight, bottomHeight);
        buildFeatures((LinearLayout) featuresPage.getChildAt(0));
        featuresPage.setVisibility(View.GONE);
        content.addView(featuresPage, frame(-1, -1, Gravity.NO_GRAVITY));

        FrameLayout header = new FrameLayout(this);
        header.setPadding(dp(24), status, dp(24), dp(8));
        header.setBackgroundColor(0xF7F5F7FA);
        titleStage = new FrameLayout(this);
        header.addView(titleStage, frame(-1, -1, Gravity.NO_GRAVITY));
        pageTitle = text(27, TEXT, true);
        pageTitle.setText("概览");
        pageTitle.setGravity(Gravity.BOTTOM);
        titleStage.addView(pageTitle, frame(-2, -2, Gravity.START | Gravity.BOTTOM));
        shell.addView(header, frame(-1, headerHeight, Gravity.TOP));

        addBottomNavigation(shell, bottomHeight, navigation);
        selectPage(false);
        return shell;
    }

    private ScrollView makePage(int top, int bottom) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, top, 0, bottom);
        LinearLayout root = vertical();
        root.setPadding(dp(20), dp(18), dp(20), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> updateHeader(y));
        return scroll;
    }

    private void buildOverview(LinearLayout root) {
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(statusRow, linear(-1, dp(116), 0, 0, 0, dp(16)));

        LinearLayout framework = statusCard("LSPosed 状态", "等待连接");
        serviceValue = (TextView) framework.getChildAt(1);
        statusRow.addView(framework, new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout target = statusCard("目标版本", targetVersion());
        LinearLayout.LayoutParams targetLp = new LinearLayout.LayoutParams(0, -1, 1f);
        targetLp.leftMargin = dp(12);
        statusRow.addView(target, targetLp);

        section(root, "状态摘要", "当前生效配置");
        LinearLayout summary = group(root);
        enabledCountValue = summaryRow(summary, "已启用功能", enabledCount() + " 项", true);
        summaryRow(summary, "应用方式", "重启乐橙后生效", true);
        summaryRow(summary, "模块版本", "v" + ownVersion(), false);

        TextView hint = text(13, SUBTEXT, false);
        hint.setText("所有隐藏规则都会同步回收父容器高度，避免保留空白占位。");
        hint.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams hintLp = linear(-1, -2);
        hintLp.setMargins(dp(4), 0, dp(4), dp(18));
        root.addView(hint, hintLp);

        Button open = new Button(this);
        open.setText("打开乐橙");
        open.setTextSize(16);
        open.setTextColor(Color.WHITE);
        open.setAllCaps(false);
        open.setBackground(roundRect(ACCENT, 18));
        open.setOnClickListener(v -> openTarget());
        root.addView(open, linear(-1, dp(52)));
    }

    private void buildFeatures(LinearLayout root) {
        addSection(root, "启动与弹窗", "启动阶段与监控页推广", new String[][]{
                {Prefs.HIDE_SPLASH, "去掉开屏广告", "冷启动直接进入首页"},
                {Prefs.HIDE_MONITOR_PROMO, "去掉监控优惠弹窗", "阻止监控页套餐促销弹窗"}
        });
        addSection(root, "底部导航", "剩余入口自动等分宽度", new String[][]{
                {Prefs.HIDE_TAB_PRODUCT, "去掉产品", "移除导航与占位"},
                {Prefs.HIDE_TAB_AI, "去掉 AI", "移除导航与占位"},
                {Prefs.HIDE_TAB_COMMUNITY, "去掉社区", "移除导航与占位"}
        });
        addSection(root, "首页", "设备卡片与底部内容", new String[][]{
                {Prefs.HIDE_HOME_BANNER, "去掉底部广告横幅", "收起广告和页脚填充区"},
                {Prefs.HIDE_HOME_CLOUD_ACTION, "去掉设备卡云服务入口", "其余两个操作按钮自动二等分"}
        });
        addSection(root, "我的", "账户页推广与服务入口", new String[][]{
                {Prefs.HIDE_MINE_CHECKIN, "去掉签到图标", "移除账户区右上角签到"},
                {Prefs.HIDE_MINE_AD, "去掉中间广告", "回收原生广告卡片高度"},
                {Prefs.HIDE_MINE_EXCHANGE, "去掉兑换横幅", "保留推荐时自动上移"},
                {Prefs.HIDE_MINE_RECOMMEND, "去掉服务推荐", "必要时整个容器一并移除"}
        });
        addSection(root, "监控画面", "操作区与底部分页", new String[][]{
                {Prefs.HIDE_MONITOR_SERVICE_BAR, "去掉中间横向服务栏", "移除服务内容及其高度"},
                {Prefs.HIDE_MONITOR_SERVICE_TAB, "去掉底部服务推荐", "实时、消息、录像重新三等分"}
        });
    }

    private void addBottomNavigation(FrameLayout shell, int height, int navInset) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.TOP);
        bar.setPadding(dp(18), dp(7), dp(18), navInset);
        bar.setBackgroundColor(0xFAFFFFFF);
        overviewTab = navItem("概览", R.drawable.ic_tab_overview);
        featuresTab = navItem("功能", R.drawable.ic_tab_settings);
        overviewTab.setOnClickListener(v -> selectPage(false));
        featuresTab.setOnClickListener(v -> selectPage(true));
        bar.addView(overviewTab, new LinearLayout.LayoutParams(0, dp(58), 1f));
        bar.addView(featuresTab, new LinearLayout.LayoutParams(0, dp(58), 1f));
        shell.addView(bar, frame(-1, height, Gravity.BOTTOM));
    }

    private void selectPage(boolean features) {
        overviewPage.setVisibility(features ? View.GONE : View.VISIBLE);
        featuresPage.setVisibility(features ? View.VISIBLE : View.GONE);
        pageTitle.setText(features ? "功能" : "概览");
        styleNav(overviewTab, !features);
        styleNav(featuresTab, features);
        ScrollView selected = features ? featuresPage : overviewPage;
        pageTitle.post(() -> updateHeader(selected.getScrollY()));
    }

    private void updateHeader(int scrollY) {
        if (pageTitle == null || titleStage == null) return;
        float progress = Math.min(1f, Math.max(0f, scrollY / (float) dp(56)));
        float eased = progress * progress * (3f - 2f * progress);
        pageTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 27f - 8f * eased);
        int weight = Math.round(700f - 200f * eased);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            pageTitle.setTypeface(Typeface.create(Typeface.SANS_SERIF, weight, false));
        }
        float width = pageTitle.getPaint().measureText(pageTitle.getText().toString());
        float centered = Math.max(0f, (titleStage.getWidth() - width) / 2f);
        pageTitle.setTranslationX(centered * eased);
    }

    private TextView navItem(String label, int iconRes) {
        TextView tab = text(12, SUBTEXT, false);
        tab.setText(label);
        tab.setGravity(Gravity.CENTER);
        tab.setCompoundDrawablePadding(dp(3));
        tab.setTag(iconRes);
        return tab;
    }

    private void styleNav(TextView tab, boolean active) {
        int color = active ? ACCENT : 0xFF9AA3AA;
        tab.setTextColor(color);
        tab.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        Drawable icon = getDrawable((Integer) tab.getTag());
        if (icon != null) {
            icon = icon.mutate(); icon.setTint(color);
            tab.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null);
        }
    }

    private LinearLayout statusCard(String label, String value) {
        LinearLayout card = vertical();
        card.setPadding(dp(16), dp(16), dp(16), dp(14));
        card.setBackground(roundRect(0xFFFFF0EA, 24));
        TextView l = text(13, SUBTEXT, false); l.setText(label);
        TextView v = text(24, ACCENT, true); v.setText(value); v.setSingleLine(true); v.setPadding(0, dp(9), 0, 0);
        card.addView(l); card.addView(v);
        return card;
    }

    private void addSection(LinearLayout root, String title, String description, String[][] rows) {
        section(root, title, description);
        LinearLayout card = group(root);
        for (int i = 0; i < rows.length; i++) {
            card.addView(switchRow(rows[i][0], rows[i][1], rows[i][2]));
            if (i < rows.length - 1) card.addView(divider(), linear(-1, 1, dp(16), 0, dp(16), 0));
        }
    }

    private void section(LinearLayout root, String title, String description) {
        TextView h = text(16, ACCENT, true); h.setText(title);
        LinearLayout.LayoutParams hp = linear(-1, -2); hp.topMargin = dp(4);
        root.addView(h, hp);
        TextView sub = text(12, SUBTEXT, false); sub.setText(description); sub.setPadding(0, dp(5), 0, dp(10));
        root.addView(sub);
    }

    private LinearLayout group(LinearLayout root) {
        LinearLayout card = vertical();
        card.setPadding(0, dp(3), 0, dp(3));
        card.setBackground(roundRect(Color.WHITE, 22));
        LinearLayout.LayoutParams lp = linear(-1, -2); lp.bottomMargin = dp(18);
        root.addView(card, lp);
        return card;
    }

    private View switchRow(String key, String label, String description) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(12), dp(13));
        LinearLayout copy = vertical();
        TextView main = text(16, TEXT, false); main.setText(label);
        TextView sub = text(12, SUBTEXT, false); sub.setText(description); sub.setPadding(0, dp(5), dp(10), 0);
        copy.addView(main); copy.addView(sub);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(local.getBoolean(key, true));
        toggle.setThumbTintList(switchThumbTint());
        toggle.setTrackTintList(switchTrackTint());
        toggle.setOnCheckedChangeListener((button, checked) -> save(key, checked));
        switches.put(key, toggle);
        row.addView(toggle, linear(-2, -2));
        return row;
    }

    private TextView summaryRow(LinearLayout root, String label, String value, boolean hasDivider) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(15), dp(16), dp(15));
        TextView l = text(15, TEXT, false); l.setText(label);
        TextView v = text(13, SUBTEXT, true); v.setText(value); v.setGravity(Gravity.END);
        row.addView(l, new LinearLayout.LayoutParams(0, -2, 1f)); row.addView(v);
        root.addView(row);
        if (hasDivider) root.addView(divider(), linear(-1, 1, dp(16), 0, dp(16), 0));
        return v;
    }

    private void initializeDefaults() {
        SharedPreferences.Editor editor = local.edit();
        boolean changed = false;
        for (String key : Prefs.ALL) if (!local.contains(key)) { editor.putBoolean(key, true); changed = true; }
        if (changed) editor.apply();
    }

    private void save(String key, boolean value) {
        local.edit().putBoolean(key, value).apply();
        if (service != null) service.getRemotePreferences(Prefs.GROUP).edit().putBoolean(key, value).apply();
        if (enabledCountValue != null) enabledCountValue.setText(enabledCount() + " 项");
    }

    @Override public void onServiceBind(XposedService service) {
        this.service = service;
        serviceValue.setText("已连接");
        serviceValue.setTextColor(0xFF22A06B);
        SharedPreferences.Editor remote = service.getRemotePreferences(Prefs.GROUP).edit();
        for (String key : Prefs.ALL) remote.putBoolean(key, local.getBoolean(key, true));
        remote.apply();
    }

    @Override public void onServiceDied(XposedService service) {
        this.service = null;
        serviceValue.setText("未连接");
        serviceValue.setTextColor(ACCENT);
    }

    private int enabledCount() {
        int count = 0;
        for (String key : Prefs.ALL) if (local.getBoolean(key, true)) count++;
        return count;
    }

    private String targetVersion() {
        try { return getPackageManager().getPackageInfo(TARGET, 0).versionName; }
        catch (Throwable ignored) { return "未安装"; }
    }

    private String ownVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Throwable ignored) { return "?"; }
    }

    private void openTarget() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(TARGET);
        if (intent == null) { Toast.makeText(this, "未找到乐橙", Toast.LENGTH_SHORT).show(); return; }
        startActivity(intent);
    }

    private void showDisclaimer() {
        new AlertDialog.Builder(this)
                .setTitle("内部测试声明")
                .setMessage("本程序仅供内部测试与技术研究，禁止传播、分发或用于非法及商业用途。测试完成后，请在 24 小时内删除程序及相关安装包。继续即表示你已知悉并同意。")
                .setCancelable(false)
                .setNegativeButton("退出", (dialog, which) -> finish())
                .setPositiveButton("我已知悉", null)
                .show();
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private ColorStateList switchThumbTint() {
        return new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{ACCENT, 0xFFF7F8F9});
    }
    private ColorStateList switchTrackTint() {
        return new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{0x66FF6B35, 0xFFD8DDE1});
    }
    private View divider() { View v = new View(this); v.setBackgroundColor(DIVIDER); return v; }
    private LinearLayout vertical() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(float sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setTextSize(sp); t.setTextColor(color); t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.SANS_SERIF, bold ? Typeface.BOLD : Typeface.NORMAL); return t;
    }
    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radiusDp)); return g;
    }
    private FrameLayout.LayoutParams frame(int w, int h, int gravity) { return new FrameLayout.LayoutParams(w, h, gravity); }
    private LinearLayout.LayoutParams linear(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    private LinearLayout.LayoutParams linear(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = linear(w, h); lp.setMargins(l, t, r, b); return lp;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int systemBarHeight(String name) {
        int id = getResources().getIdentifier(name, "dimen", "android");
        return id == 0 ? 0 : getResources().getDimensionPixelSize(id);
    }
}
