package com.wisdomgarden.trpc.openwith;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * This is the entry point of the openwith plugin
 *
 * @author Jean-Christophe Hoelt
 */
public class OpenWithPlugin extends CordovaPlugin {

    /**
     * How the plugin name shows in logs
     */
    private final String PLUGIN_NAME = "OpenWithPlugin";
    private final String SAVED_KEY = "sharedData";

    /**
     * Maximal verbosity, log everything
     */
    private final int DEBUG = 0;
    /**
     * Default verbosity, log interesting stuff only
     */
    private final int INFO = 10;
    /**
     * Low verbosity, log only warnings and errors
     */
    private final int WARN = 20;
    /**
     * Minimal verbosity, log only errors
     */
    private final int ERROR = 30;

    /**
     * Current verbosity level, changed with setVerbosity
     */
    private int verbosity = INFO;

    public static final int DEFAULT_ATTACHMENTS_WITH_MAX_COUNT = 5;

    /**
     * Log to the console if verbosity level is greater or equal to level
     */
    private void log(final int level, final String message) {
        switch (level) {
            case DEBUG:
                Log.d(PLUGIN_NAME, message);
                break;
            case INFO:
                Log.i(PLUGIN_NAME, message);
                break;
            case WARN:
                Log.w(PLUGIN_NAME, message);
                break;
            case ERROR:
                Log.e(PLUGIN_NAME, message);
                break;
        }
    }

    /**
     * Intents added before the handler has been registered
     */
    private ArrayList pendingIntents = new ArrayList(); //NOPMD

    private SharedPreferences prefs;

    private int maxAttachmentCount = DEFAULT_ATTACHMENTS_WITH_MAX_COUNT;

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        log(DEBUG, "initialize()");
        try {
            Context context = this.cordova.getContext();
            ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            this.maxAttachmentCount = applicationInfo.metaData.getInt("OPEN_WITH_ATTACHMENTS_WITH_MAX_COUNT", DEFAULT_ATTACHMENTS_WITH_MAX_COUNT);
        } catch (Exception e) {
            this.maxAttachmentCount = DEFAULT_ATTACHMENTS_WITH_MAX_COUNT;
        }
        Serializer.setMaxAttachmentCount(this.maxAttachmentCount);

        this.prefs = this.cordova.getContext().getSharedPreferences("OpenWithSharedData", Activity.MODE_PRIVATE);
        super.initialize(cordova, webView);
    }

    /**
     * Called when the WebView does a top-level navigation or refreshes.
     * <p>
     * Plugins should stop any long-running processes and clean up internal state.
     * <p>
     * Does nothing by default.
     */
    @Override
    public void onReset() {
        verbosity = INFO;
        pendingIntents.clear();
    }

    /**
     * Generic plugin command executor
     *
     * @param action
     * @param data
     * @param callbackContext
     * @return
     */
    @Override
    public boolean execute(final String action, final JSONArray data, final CallbackContext callbackContext) {
        log(DEBUG, "execute() called with action:" + action + " and options: " + data);
        if ("setVerbosity".equals(action)) {
            return setVerbosity(data, callbackContext);
        } else if ("init".equals(action)) {
            return init(data, callbackContext);
        } else if ("fetchSharedData".equals(action)) {
            return fetchSharedData(data, callbackContext);
        } else if ("exit".equals(action)) {
            return exit(data, callbackContext);
        }
        log(DEBUG, "execute() did not recognize this action: " + action);
        return false;
    }

    public boolean setVerbosity(final JSONArray data, final CallbackContext context) {
        log(DEBUG, "setVerbosity() " + data);
        if (data.length() != 1) {
            log(WARN, "setVerbosity() -> invalidAction");
            return false;
        }
        try {
            verbosity = data.getInt(0);
            log(DEBUG, "setVerbosity() -> ok");
            return PluginResultSender.ok(context);
        } catch (JSONException ex) {
            log(WARN, "setVerbosity() -> invalidAction");
            return false;
        }
    }

    // Initialize the plugin
    public boolean init(final JSONArray data, final CallbackContext context) {
        log(DEBUG, "init() " + data);
        if (data.length() != 0) {
            log(WARN, "init() -> invalidAction");
            return false;
        }
        verbosity = INFO;
        onNewIntent(cordova.getActivity().getIntent());
        log(DEBUG, "init() -> ok");
        return PluginResultSender.ok(context);
    }

    // Exit after processing
    public boolean exit(final JSONArray data, final CallbackContext context) {
        log(DEBUG, "exit() " + data);
        if (data.length() != 0) {
            log(WARN, "exit() -> invalidAction");
            return false;
        }
        cordova.getActivity().moveTaskToBack(true);
        log(DEBUG, "exit() -> ok");
        return PluginResultSender.ok(context);
    }

    public boolean fetchSharedData(final JSONArray data, final CallbackContext context) {
        log(DEBUG, "fetchSharedData() " + data);
        if (data.length() != 0) {
            log(WARN, "fetchSharedData() -> invalidAction");
            return false;
        }

        JSONObject sharedData = getSharedData();
        this.removeSharedData();
        if (sharedData != null) {

            final PluginResult result = new PluginResult(PluginResult.Status.OK, sharedData);

            context.sendPluginResult(result);
            return true;
        } else {
            return PluginResultSender.ok(context);
        }

    }


    /**
     * This is called when a new intent is sent while the app is already opened.
     * <p>
     * We also call it manually with the cordova application intent when the plugin
     * is initialized (so all intents will be managed by this method).
     */
    @Override
    public void onNewIntent(final Intent intent) {
        log(DEBUG, "onNewIntent() " + intent.getAction());
        final JSONObject json = toJSONObject(intent);
        if (json != null) {
            pendingIntents.add(json);
        }
        processPendingIntents();
    }

    /**
     * When the handler is defined, call it with all attached files.
     */
    private void processPendingIntents() {
        log(DEBUG, "processPendingIntents()");
        JSONObject jsonObject = getSharedData();
        for (int i = 0; i < pendingIntents.size(); i++) {
            jsonObject = mergeIntends((JSONObject) pendingIntents.get(i), jsonObject);
        }
        pendingIntents.clear();

        if (jsonObject != null) {
            saveSharedData(jsonObject);
        }
    }

    /**
     * Calls the javascript intent handlers.
     */
    private JSONObject mergeIntends(final JSONObject intent, JSONObject jsonObject) {
        if (jsonObject == null) {
            try {
                jsonObject = new JSONObject();
                jsonObject.put("action", intent.getString("action"));
                jsonObject.put("exit", intent.getBoolean("exit"));
                jsonObject.put("items", intent.getJSONArray("items"));
                jsonObject.put("receivedCounts", intent.getInt("receivedCounts"));
                jsonObject.put("maxAttachmentCount", this.maxAttachmentCount);
            } catch (Exception e) {
                jsonObject = null;
            }
        } else {
            try {

                JSONArray finalItems = jsonObject.getJSONArray("items");
                JSONArray items = intent.getJSONArray("items");

                if (finalItems != items) {
                    int len = items.length();
                    for (int i = 0; i < len; i++) {
                        finalItems.put(items.getJSONObject(i));
                    }

                    jsonObject.put("action", intent.getString("action"));
                    jsonObject.put("exit", intent.getBoolean("exit"));
                    jsonObject.put("receivedCounts", finalItems.length());
                    jsonObject.put("maxAttachmentCount", this.maxAttachmentCount);
                }
            } catch (Exception e) {
                //
            }
        }

        return jsonObject;
    }

    /**
     * Converts an intent to JSON
     */
    private JSONObject toJSONObject(final Intent intent) {
        try {
            final Context context = this.cordova
                    .getActivity().getApplicationContext();
            final File tmpDir = this.cordova.getContext().getCacheDir();

            return Serializer.toJSONObject(context, intent, tmpDir);
        } catch (JSONException e) {
            log(ERROR, "Error converting intent to JSON: " + e.getMessage());
            log(ERROR, Arrays.toString(e.getStackTrace()));
            return null;
        }
    }

    private void saveSharedData(JSONObject sharedData) {
        try {
            SharedPreferences.Editor editor = this.prefs.edit();

            editor.putString(SAVED_KEY, sharedData.toString());

            editor.commit();

        } catch (Exception e) {
            //
        }
    }

    private boolean removeSharedData() {
        try {
            SharedPreferences.Editor editor = this.prefs.edit();

            editor.remove(SAVED_KEY);

            editor.commit();
            return true;

        } catch (Exception e) {
            return false;
        }

    }

    private JSONObject getSharedData() {
        String savedData = this.prefs.getString(SAVED_KEY, null);
        if (savedData == null) {
            return null;
        }

        JSONObject data = null;
        try {
            data = new JSONObject(savedData);
        } catch (Exception e) {
            data = null;
        }

        return data;
    }

}
