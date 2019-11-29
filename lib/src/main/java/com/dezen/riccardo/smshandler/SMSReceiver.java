package com.dezen.riccardo.smshandler;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import com.dezen.riccardo.smshandler.database.SMSDatabaseManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Class meant to intercept SMSMessages coming from the Android system.
 * The filtering work on messages is voluntarily repeated between this Receiver and
 * NotificationCatcherService in order to avoid attaching the Receiver to the Service and keep it
 * lighter.
 * The class checks whether pertinent messages have been received. Then proceeds to check whether a
 * suitable listener is available for immediate response. If not then proceeds to either fire a
 * broadcast meant to wake some other process or writes the messages to a database for later use.
 * @author Riccardo De Zen
 */
public class SMSReceiver extends BroadcastReceiver {

    private static final Class<Activity> ACTIVITY_SUPERCLASS = Activity.class;
    private boolean shouldWake = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction() != null && intent.getAction().equals(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)){
            SmsMessage[] messages = filter(Telephony.Sms.Intents.getMessagesFromIntent(intent));
            if(messages.length < 1) return;
            if(SMSHandler.shouldHandleIncomingSms()){
                /*
                 * SMSHandler.shouldHandleIncomingSms() returns true if a suitable listener for
                 * immediate response is available. A broadcast event is therefore fired to
                 * notify said listener through the receiver it is attached to.
                 */
                Intent localIntent = new Intent();
                localIntent.replaceExtras(intent);
                localIntent.setAction(SMSHandler.RECEIVED_BROADCAST);
                localIntent.setPackage(context.getApplicationContext().getPackageName());
                context.sendBroadcast(localIntent);
            }
            else if(shouldWake){
                startAppropriateAction(context, intent);
                shouldWake = false;
            }
            else{
                SMSDatabaseManager.getInstance(context).addSMS(messages);
            }
        }
    }

    /**
     * Method to filter messages containing SMSHandler.APP_KEY.
     * Messages are meant to be the ones coming directly from the received Intent.
     * @param messages array of SmsMessage.
     * @return list of messages containing SMSHandler.APP_KEY
     */
    private SmsMessage[] filter(SmsMessage[] messages){
        List<SmsMessage> list = new ArrayList<>();
        if(messages != null)
            for(SmsMessage sms : messages){
                if(sms.getMessageBody().contains(SMSHandler.APP_KEY)) list.add(sms);
                if(sms.getMessageBody().contains(SMSHandler.WAKE_KEY)) shouldWake = true;
            }
        return list.toArray(new SmsMessage[0]);
    }

    /**
     * Method to start some action considered responsible of managing urgent messages.
     * Right now only activities can be saved and started. Although starting an Activity from a
     * BroadcastReceiver is bad practice according to the Android docs, it is the only way to
     * forcibly start an app.
     * @param context the context on which this method is running
     * @param extraIntent an Intent containing the extras for the action to be started
     */
    private void startAppropriateAction(Context context, Intent extraIntent){
        Class classToStart = getWakeAction(context);
        if(classToStart == null) return;
        if(ACTIVITY_SUPERCLASS.isAssignableFrom(classToStart))
            startActivity(classToStart, context, extraIntent);
    }

    /**
     * Method to start an activity from its canonical class name.
     * @param context the context starting the Activity.
     * @param extraIntent Intent containing any extras to be passed along.
     */
    private void startActivity(Class activityToStart, Context context, Intent extraIntent){
        try{
            Intent wakeIntent = new Intent(context, activityToStart);
            wakeIntent.replaceExtras(extraIntent);
            wakeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(wakeIntent);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Reads from preferences the name of the activity that should be started and returns its class;
     * @return The class of the Activity that should be woken up, null if none is present or the
     * saved value is invalid.
     */
    private Class getWakeAction(Context context){
        final String DEFAULT = "";
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                SMSHandler.PREFERENCES_FILE_NAME,
                Context.MODE_PRIVATE
        );
        String wakeClass = sharedPreferences.getString(
                SMSHandler.PREFERENCE_WAKE_ACTION_KEY,
                DEFAULT
        );
        try{
            return Class.forName(wakeClass);
        }
        catch(ClassNotFoundException e){
            e.printStackTrace();
            return null;
        }
    }
}