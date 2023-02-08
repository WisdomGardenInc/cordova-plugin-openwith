package com.wisdomgarden.trpc.openwith;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


class SharedData {
    JSONArray items;
    int receivedCounts;

    public SharedData(int receivedCounts, JSONArray items) {
        this.receivedCounts = receivedCounts;
        this.items = items;
    }
}

/**
 * Handle serialization of Android objects ready to be sent to javascript.
 */
class Serializer {
    private static int MAX_ATTACHMENT_COUNT = OpenWithPlugin.DEFAULT_ATTACHMENTS_WITH_MAX_COUNT;

    public static void setMaxAttachmentCount(int maxAttachmentCount) {
        MAX_ATTACHMENT_COUNT = maxAttachmentCount;
    }

    /**
     * Convert an intent to JSON.
     * <p>
     * This actually only exports stuff necessary to see file content
     * (streams or clip data) sent with the intent.
     * If none are specified, null is return.
     */
    public static JSONObject toJSONObject(
            final Context context,
            final Intent intent,
            final File tmpDir)
            throws JSONException {
        SharedData sharedData = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                sharedData = itemsFromClipData(context, intent.getClipData(), tmpDir);
            }
            if (sharedData == null || sharedData.items == null || sharedData.items.length() == 0) {
                sharedData = itemsFromExtras(context, intent.getExtras(), tmpDir);
            }
            if (sharedData == null || sharedData.items == null || sharedData.items.length() == 0) {
                sharedData = itemsFromData(context, intent.getData(), tmpDir);
            }
        } catch (Exception e) {
            sharedData = null;
        }

        if (sharedData == null) {
            return null;
        }

        final JSONObject action = new JSONObject();
        action.put("action", translateAction(intent.getAction()));
        action.put("exit", readExitOnSent(intent.getExtras()));
        action.put("items", sharedData.items);
        action.put("receivedCounts", sharedData.receivedCounts);
        action.put("maxAttachmentCount", MAX_ATTACHMENT_COUNT);

        return action;
    }

    public static String translateAction(final String action) {
        if ("android.intent.action.SEND".equals(action) ||
                "android.intent.action.SEND_MULTIPLE".equals(action)) {
            return "SEND";
        } else if ("android.intent.action.VIEW".equals(action)) {
            return "VIEW";
        }
        return action;
    }

    /**
     * Read the value of "exit_on_sent" in the intent's extra.
     * <p>
     * Defaults to false.
     */
    public static boolean readExitOnSent(final Bundle extras) {
        if (extras == null) {
            return false;
        }
        return extras.getBoolean("exit_on_sent", false);
    }

    /**
     * Extract the list of items from clip data (if available).
     * <p>
     * Defaults to null.
     */
    public static SharedData itemsFromClipData(
            final Context context,
            final ClipData clipData,
            final File tmpDir) throws JSONException {
        if (clipData == null) {
            return null;
        }

        final int clipItemCount = clipData.getItemCount();
        JSONObject[] items = new JSONObject[clipItemCount];
        for (int i = 0; i < clipItemCount; i++) {
            Uri uri = clipData.getItemAt(i).getUri();

            if (uri != null) {
                items[i] = toJSONObject(context, uri, tmpDir);
            } else {
                // process share plain text not file
                String text = clipData.getItemAt(i).getText().toString();
                final JSONObject json = new JSONObject();
                json.put("type", "text/plain");
                json.put("uri", "");
                json.put("path", "");
                json.put("text", text);
                json.put("name", "text");

                items[i] = json;
            }

        }

        List<JSONObject> filteredList = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                filteredList.add(items[i]);
                if (filteredList.size() >= MAX_ATTACHMENT_COUNT) {
                    break;
                }
            }
        }
        return new SharedData(clipItemCount, new JSONArray(filteredList));
    }


    /**
     * Extract the list of items from the intent's extra stream.
     * <p>
     * See Intent.EXTRA_STREAM for details.
     */
    public static SharedData itemsFromExtras(
            final Context context,
            final Bundle extras,
            final File tmpDir) throws JSONException {

        if (extras == null) {
            return null;
        }


        ArrayList<Uri> uris;
        Object obj = extras.get(Intent.EXTRA_STREAM);
        if (obj instanceof ArrayList) {
            uris = (ArrayList<Uri>) obj;
        } else {
            uris = new ArrayList<>();
            uris.add((Uri) extras.get(Intent.EXTRA_STREAM));
        }

        List<JSONObject> filteredList = new ArrayList<>();

        for (int i = 0; i < uris.size(); i++) {
            Uri uri = uris.get(i);
            JSONObject item = toJSONObject(context, uri, tmpDir);
            if (item != null) {
                filteredList.add(item);
                if (filteredList.size() >= MAX_ATTACHMENT_COUNT) {
                    break;
                }
            }
        }
        return new SharedData(uris.size(), new JSONArray(filteredList));
    }

    /**
     * Extract the list of items from the intent's getData
     * <p>
     * See Intent.ACTION_VIEW for details.
     */
    public static SharedData itemsFromData(
            final Context context,
            final Uri uri,
            final File tmpDir) throws JSONException {
        if (uri == null) {
            return null;
        }

        final JSONObject item = toJSONObject(context, uri, tmpDir);
        if (item == null) {
            return null;
        }
        final JSONObject[] items = new JSONObject[1];
        items[0] = item;
        return new SharedData(items.length, new JSONArray(items));
    }

    /**
     * Convert an Uri to JSON object.
     * <p>
     * Object will include:
     * "type" of data;
     * "uri" itself;
     * "path" to the file, if applicable.
     */
    private static JSONObject toJSONObject(
            final Context context,
            final Uri uri,
            final File tmpDir)
            throws JSONException {
        if (uri == null) {
            return null;
        }
        final JSONObject json = new JSONObject();
        final String type = context.getContentResolver().getType(uri);
        PathData pathData = null;
        try {
            pathData = PathUtil.getPath(context, uri, tmpDir);
        } catch (Exception e) {
            //
        }

        if (pathData == null) {
            return null;
        }

        json.put("type", type);
        json.put("uri", uri);
        json.put("path", pathData.filePath);
        json.put("isTemp", pathData.isTemp);
        json.put("name", pathData.fileName);

        return json;
    }
}
