package com.jcatsolutions.officechat;

/** Where the app's website and API live. Change here if you move to your own domain (e.g. chat.jcatsolutions.com). */
final class Config {
    /** Must match "server.url" in capacitor.config.json. */
    static final String WEB_URL = "https://office-chat-frontend.vercel.app";
    /** The Railway backend (used to decline a call straight from the notification). */
    static final String API_URL = "https://office-chat-backend-production.up.railway.app";

    private Config() {}
}
