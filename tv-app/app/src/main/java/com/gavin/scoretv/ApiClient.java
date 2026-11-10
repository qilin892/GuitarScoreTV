package com.gavin.scoretv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Small HTTP client kept dependency-free so the TV app stays light. */
final class ApiClient {
    private ApiClient() {}

    static String getText(String address) throws IOException {
        byte[] data = getBytes(address);
        return new String(data, "UTF-8");
    }

    static Bitmap getBitmap(String address, int maxSide) throws IOException {
        byte[] data = getBytes(address);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        int sample = 1;
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        while (longest / sample > maxSide * 2) sample *= 2;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (bitmap == null) throw new IOException("图片解码失败");
        return bitmap;
    }

    private static byte[] getBytes(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/json,image/*");
        connection.setRequestProperty("Connection", "close");
        try {
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) throw new IOException("HTTP " + code);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = stream.read(buffer)) >= 0) output.write(buffer, 0, read);
            stream.close();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            return output.toByteArray();
        } finally {
            connection.disconnect();
        }
    }
}
