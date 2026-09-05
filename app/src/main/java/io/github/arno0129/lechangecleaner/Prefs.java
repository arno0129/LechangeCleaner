/*
 * Copyright 2026 arno0129
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.arno0129.lechangecleaner;

final class Prefs {
    static final String GROUP = "lechange_cleaner_settings";
    static final String HIDE_SPLASH = "hide_splash";
    static final String HIDE_TAB_PRODUCT = "hide_tab_product";
    static final String HIDE_TAB_AI = "hide_tab_ai";
    static final String HIDE_TAB_COMMUNITY = "hide_tab_community";
    static final String HIDE_HOME_BANNER = "hide_home_banner";
    static final String HIDE_HOME_CLOUD_ACTION = "hide_home_cloud_action";
    static final String HIDE_MINE_CHECKIN = "hide_mine_checkin";
    static final String HIDE_MINE_AD = "hide_mine_ad";
    static final String HIDE_MINE_EXCHANGE = "hide_mine_exchange";
    static final String HIDE_MINE_RECOMMEND = "hide_mine_recommend";
    static final String HIDE_MONITOR_SERVICE_BAR = "hide_monitor_service_bar";
    static final String HIDE_MONITOR_SERVICE_TAB = "hide_monitor_service_tab";
    static final String HIDE_MONITOR_PROMO = "hide_monitor_promo";

    static final String[] ALL = {
            HIDE_SPLASH, HIDE_TAB_PRODUCT, HIDE_TAB_AI, HIDE_TAB_COMMUNITY,
            HIDE_HOME_BANNER, HIDE_HOME_CLOUD_ACTION, HIDE_MINE_CHECKIN, HIDE_MINE_AD,
            HIDE_MINE_EXCHANGE, HIDE_MINE_RECOMMEND,
            HIDE_MONITOR_SERVICE_BAR, HIDE_MONITOR_SERVICE_TAB, HIDE_MONITOR_PROMO
    };

    private Prefs() {}
}
