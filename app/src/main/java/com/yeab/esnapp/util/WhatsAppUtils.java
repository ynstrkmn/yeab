package com.yeab.esnapp.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class WhatsAppUtils {

    public static void sendMessage(Context context, String phone10Digits, String message) {
        String phoneFull = "90" + phone10Digits;
        String url = "https://wa.me/" + phoneFull + "?text=" + Uri.encode(message);

        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        context.startActivity(i);
    }
}
