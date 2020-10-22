package com.example.carcontrollermqtt.service;

import android.content.Context;
import android.util.Log;

import com.example.carcontrollermqtt.data.local.AppDatabase;
import com.example.carcontrollermqtt.data.local.dao.WqttMessageDao;
import com.example.carcontrollermqtt.data.models.Device;
import com.example.carcontrollermqtt.data.models.WqttClient;
import com.example.carcontrollermqtt.data.models.WqttMessage;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Calendar;

import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class WqttMessageManager {
    private static final String TAG = "WqttMessageManager";
    private static final CompletableObserver writeToDbObserver = new CompletableObserver() {
        @Override
        public void onSubscribe(@NonNull Disposable d) {

        }

        @Override
        public void onComplete() {
        }

        @Override
        public void onError(@NonNull Throwable e) {
            Log.e(TAG, "onError: failed to write the new message", e);
        }
    };
    private static WqttMessageManager instance;
    private final Calendar calendar;
    private final WqttMessageDao messageDao;

    private final WqttClientManager clientManager;

    private WqttMessageManager(Context context) {
        messageDao = AppDatabase.getInstance(context).messageDao();
        calendar = Calendar.getInstance();
        clientManager = WqttClientManager.getInstance(context);
    }

    public static WqttMessageManager getInstance(Context context) {
        if (instance == null) {
            instance = new WqttMessageManager(context);
        }

        return instance;
    }

    public void receiveMessage(Device device, String topic, MqttMessage message) {
        writeToDb(
                messageDao.insert(WqttMessage.newInstance(device.getId(), message.getId(), calendar.getTime(), true, topic, message.toString()))
        );
    }

    public void sendMessage(Device device, String topic, String payload) {
        WqttClient client = clientManager.getWqttClient(device.getUsername());
        if (client != null) {
            try {
                MqttMessage mqttMessage = new MqttMessage(payload.getBytes());
                WqttMessage wqttMessage = WqttMessage.newInstance(device.getId(), mqttMessage.getId(), calendar.getTime(), false, topic, payload);

                client.getClient().publish(topic, new MqttMessage(payload.getBytes()), null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Log.d(TAG, "onSuccess: updating msg status");
                        writeToDb(
                                messageDao.update(wqttMessage.cloneWithStatus(WqttMessage.MessageStatus.DELIVERED))
                        );
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Log.e(TAG, "onFailure: failed to send", exception);
                        writeToDb(
                                messageDao.update(wqttMessage.cloneWithStatus(WqttMessage.MessageStatus.FAILED))
                        );
                    }
                });

                writeToDb(messageDao.insert(wqttMessage));
            } catch (MqttException e) {
                Log.e(TAG, "sendMessage: failed to send - " + e.getMessage(), e);
            }
        }
    }

    private void writeToDb(Completable task) {
        task.subscribeOn(Schedulers.io()).subscribe(writeToDbObserver);
    }
}