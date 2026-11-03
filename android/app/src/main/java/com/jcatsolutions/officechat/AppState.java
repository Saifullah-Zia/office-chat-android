package com.jcatsolutions.officechat;

/** What the app is doing right now (reset when Android closes the app, which is what we want). */
final class AppState {
    /** The app is open on screen: the website rings and notifies by itself. */
    static volatile boolean foreground = false;
    /** A call is in progress in the app. */
    static volatile boolean inCall = false;

    private AppState() {}
}
