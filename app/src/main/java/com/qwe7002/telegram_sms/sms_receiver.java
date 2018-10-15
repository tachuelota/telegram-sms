package com.qwe7002.telegram_sms;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.content.Context.MODE_PRIVATE;

public class sms_receiver extends BroadcastReceiver {
    static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public static boolean is_numeric(String str) {
        for (int i = str.length(); --i >= 0; ) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);

        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = "https://api.telegram.org/bot" + bot_token + "/sendMessage";
        assert bot_token != null;
        assert chat_id != null;
        if (bot_token.isEmpty() || chat_id.isEmpty()) {
            Log.i("tg-sms", "onReceive: token not found");
            return;
        }
        if ("android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            Bundle bundle = intent.getExtras();

            if (bundle != null) {
                String DualSim = "";
                SubscriptionManager manager = SubscriptionManager.from(context);
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    if (manager.getActiveSubscriptionInfoCount() == 2) {
                        int slot = bundle.getInt("slot", -1);
                        if (slot != -1) {
                            DualSim = "\n" + context.getString(R.string.SIM_card_slot) + (slot + 1);
                        }
                    }
                }

                final int sub = bundle.getInt("subscription", -1);
                Object[] pdus = (Object[]) bundle.get("pdus");
                assert pdus != null;
                final SmsMessage[] messages = new SmsMessage[pdus.length];
                for (int i = 0; i < pdus.length; i++) {
                    messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                }
                if (messages.length > 0) {
                    StringBuilder msgBody = new StringBuilder();
                    for (SmsMessage item : messages) {
                        msgBody.append(item.getMessageBody());
                    }
                    String msgAddress = messages[0].getOriginatingAddress();

                    final request_json request_body = new request_json();
                    request_body.chat_id = chat_id;
                    request_body.text = context.getString(R.string.receive_sms_head) + DualSim + "\n" + context.getString(R.string.from) + msgAddress + "\n" + context.getString(R.string.content) + msgBody;
                    assert msgAddress != null;
                    if (msgAddress.equals(sharedPreferences.getString("trusted_phone_number", null))) {
                        String[] msg_send_list = msgBody.toString().split("\n");
                        if (is_numeric(msg_send_list[0])) {
                            String msg_send_to = msg_send_list[0];
                            StringBuilder msg_send_content = new StringBuilder();
                            for (int i = 1; i < msg_send_list.length; i++) {
                                if (msg_send_list.length != 2 && i != 1) {
                                    msg_send_content.append("\n");
                                }
                                msg_send_content.append(msg_send_list[i]);
                            }
                            public_func.send_sms(msg_send_to, msg_send_content.toString(), sub);
                            request_body.text = context.getString(R.string.send_sms_head) + DualSim + "\n" + context.getString(R.string.to) + msg_send_to + "\n" + context.getString(R.string.content) + msg_send_content.toString();
                        }
                    }
                    Gson gson = new Gson();
                    String request_body_raw = gson.toJson(request_body);
                    RequestBody body = RequestBody.create(JSON, request_body_raw);
                    OkHttpClient okHttpClient = new OkHttpClient();
                    Request request = new Request.Builder().url(request_uri).method("POST", body).build();
                    Call call = okHttpClient.newCall(request);
                    call.enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            Looper.prepare();
                            Toast.makeText(context, "Send Error:" + e.getMessage(), Toast.LENGTH_SHORT).show();
                            if (sharedPreferences.getBoolean("fallback_sms", false)) {
                                String msg_send_to = sharedPreferences.getString("trusted_phone_number", null);
                                String msg_send_content = request_body.text;
                                if (msg_send_to != null) {
                                    public_func.send_sms(msg_send_to, msg_send_content, sub);
                                }
                            }
                            Looper.loop();
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            if (response.code() != 200) {
                                Looper.prepare();
                                assert response.body() != null;
                                Toast.makeText(context, "Send Error:" + response.body().string(), Toast.LENGTH_SHORT).show();
                                Looper.loop();
                            }
                        }
                    });
                }
            }
        }
    }
}
