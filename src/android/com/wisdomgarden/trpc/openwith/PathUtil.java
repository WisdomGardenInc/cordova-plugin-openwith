package com.wisdomgarden.trpc.openwith;


import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

class PathData {
    public String filePath;
    public Boolean isTemp;
    public String fileName;

    public PathData(String filePath, String fileName, Boolean isTemp) {
        this.filePath = filePath;
        this.isTemp = isTemp;
        this.fileName = fileName;
    }

    public PathData(String filePath) {
        this.filePath = filePath;
        this.isTemp = false;
        int lastSlashIndex = filePath.lastIndexOf("/");
        this.fileName = filePath.substring(lastSlashIndex + 1);
    }
}


// https://stackoverflow.com/questions/13209494/how-to-get-the-full-file-path-from-uri
class PathUtil {
    public static PathData getPath(final Context context, Uri uri, final File tmpDir) throws Exception {
        final boolean needToCheckUri = Build.VERSION.SDK_INT >= 19;
        String selection = null;
        String[] selectionArgs = null;
        // Uri is different in versions after KITKAT (Android 4.4), we need to
        // deal with different Uris.
        if (needToCheckUri && DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                return new PathData(Environment.getExternalStorageDirectory() + "/" + split[1]);
            } else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                uri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
            } else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                if ("image".equals(type)) {
                    uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                selection = "_id=?";
                selectionArgs = new String[]{split[1]};
            }
        }
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            // https://stackoverflow.com/questions/20067508/get-real-path-from-uri-android-kitkat-new-storage-access-framework/20402190?noredirect=1#comment30507493_20402190
            String[] projection = {MediaStore.Images.Media.DATA, MediaStore.Images.Media.DISPLAY_NAME};
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
                int dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                int nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                cursor.moveToFirst();
                if (dataIndex >= 0) {
                    return new PathData(cursor.getString(dataIndex));
                } else if (nameIndex >= 0) {
                    final String displayName = cursor.getString(nameIndex);

                    return new PathData(getFilePathFromContent(context, uri, displayName, tmpDir), displayName, true);
                }

            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return new PathData(uri.getPath());
        }
        return null;
    }

    private static String getFilePathFromContent(final Context context, Uri uri, final String fileName, final File tmpDir) throws Exception {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        File outputFile = new File(tmpDir, fileName);
        outputFile.deleteOnExit();
        outputFile.createNewFile();

        FileOutputStream outputStream = new FileOutputStream(outputFile, false);

        int read;
        byte[] bytes = new byte[1024 * 100];
        while ((read = inputStream.read(bytes)) != -1) {
            outputStream.write(bytes, 0, read);
        }

        inputStream.close();
        outputStream.close();

        return outputFile.getAbsolutePath();
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
}
