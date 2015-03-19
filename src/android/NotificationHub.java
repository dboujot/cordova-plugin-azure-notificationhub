package msopentech.azure;

import java.util.Set;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.microsoft.windowsazure.messaging.NativeRegistration;



/**
 * Apache Cordova plugin for Windows Azure Notification Hub
 */
public class NotificationHub extends CordovaPlugin {

    /**
     * The callback context from which we were invoked.
     */
    protected static CallbackContext _callbackContext = null;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        _callbackContext = callbackContext;
        try {
            
            if (action.equals("registerApplication")) {   
                    String hubName = args.getString(0);
                    String connectionString = args.getString(1);
					          //2 for NotificatonHub_onNotificationReceivedGlobal
                    //les tags sont mis dans un e chaine de caractères avec un ; comme séparateur
					          String tags = args.getString(3);
                    String senderId = args.getString(4);
                    registerApplication(hubName, connectionString, senderId,tags);
                    return true;
            }
            
            if (action.equals("unregisterApplication")) {
                String hubName = args.getString(0);
                String connectionString = args.getString(1);
                unregisterApplication(hubName, connectionString);
                return true;
            } 
            
            return false; // invalid action            
        } catch (Exception e) {
            _callbackContext.error(e.getMessage());
        }
        return true;
    }

    /**
     * Asynchronously registers the device for native notifications.
     */
    @SuppressWarnings("unchecked")
    private void registerApplication(final String hubName, final String connectionString, final String senderId, final String tagsString) {

        try {
            final GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(cordova.getActivity());
            final com.microsoft.windowsazure.messaging.NotificationHub hub = 
                    new com.microsoft.windowsazure.messaging.NotificationHub(hubName, connectionString, cordova.getActivity());

            new AsyncTask() {
                @Override
                protected Object doInBackground(Object... params) {
                   try {
                      String gcmId = gcm.register(senderId);
                      System.out.println("registerApplication => tags = " + tagsString);
					            String[] tags = (tagsString!=null && !tagsString.equals(""))?tagsString.split(";"):new String[0];                      
                      NativeRegistration registrationInfo = hub.register(gcmId,tags);
					//NativeRegistration registrationInfo = hub.register(gcmId);
                      
                      JSONObject registrationResult = new JSONObject();
                      registrationResult.put("registrationId", registrationInfo.getRegistrationId());
                      registrationResult.put("channelUri", registrationInfo.getGCMRegistrationId());
                      registrationResult.put("notificationHubPath", registrationInfo.getNotificationHubPath());
                      registrationResult.put("event", "registerApplication");
                      
                      PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, registrationResult);
                      // keepKallback is used to continue using the same callback to notify about push notifications received
                      pluginResult.setKeepCallback(true); 
                      
                      NotificationHub.getCallbackContext().sendPluginResult(pluginResult);
                      
                   } catch (Exception e) {
                       NotificationHub.getCallbackContext().error(e.getMessage());
                   }
                   return null;
               }
             }.execute(null, null, null);
        } catch (Exception e) {
            NotificationHub.getCallbackContext().error(e.getMessage());
        }
    }

    /**
     * Unregisters the device for native notifications.
     */
    private void unregisterApplication(final String hubName, final String connectionString) {
        try {
            final com.microsoft.windowsazure.messaging.NotificationHub hub = 
                    new com.microsoft.windowsazure.messaging.NotificationHub(hubName, connectionString, cordova.getActivity());
            hub.unregister();
            NotificationHub.getCallbackContext().success();            
        } catch (Exception e) {
            NotificationHub.getCallbackContext().error(e.getMessage());
        }
    }
    
    /**
     * Handles push notifications received.
     */
    public static class PushNotificationReceiver extends android.content.BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            
            if (NotificationHub.getCallbackContext() == null){
                return;
            }                                    
            JSONObject json = new JSONObject();
            try {
                
                Set<String> keys = intent.getExtras().keySet();
                for (String key : keys) {
                    json.put(key, intent.getExtras().get(key));
                }
                PluginResult result = new PluginResult(PluginResult.Status.OK, json);
                result.setKeepCallback(true);
                NotificationHub.getCallbackContext().sendPluginResult(result);
            } catch (JSONException e) {
                e.printStackTrace();
            }
		//Toast.makeText(context, "Intent Detected.", Toast.LENGTH_LONG).show();
		
		// Extract the payload from the message
		Bundle extras = intent.getExtras();
		if (extras != null)
		{
		// if we are in the foreground, just surface the payload, else post it to the statusbar
            	//if (PushPlugin.isInForeground()) {
		//		extras.putBoolean("foreground", true);
                //PushPlugin.sendExtras(extras);
		//	}
		//	else {
		extras.putBoolean("foreground", false);

                // Send a notification if there is a message
                if (extras.getString("message") != null && extras.getString("message").length() != 0) {
                    createNotification(context, extras);
                }
            }
        }
	

	public void createNotification(Context context, Bundle extras)
	{
		NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		String appName = getAppName(context);

		int defaults = Notification.DEFAULT_ALL;
		
		//Intent resultIntent = new Intent(context, ResultActivity.class);
        Intent resultIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
		// Because clicking the notification opens a new ("special") activity, there's
		// no need to create an artificial back stack.
		PendingIntent resultPendingIntent =
		    PendingIntent.getActivity(
		    context,
		    0,
		    resultIntent,
		    PendingIntent.FLAG_UPDATE_CURRENT
		);
		NotificationCompat.Builder mBuilder =
			new NotificationCompat.Builder(context)
				.setDefaults(defaults)
				.setSmallIcon(context.getApplicationInfo().icon)
				.setWhen(System.currentTimeMillis())
				.setContentTitle(extras.getString("title"))
				.setContentIntent(resultPendingIntent)
				.setTicker(extras.getString("title"))
				.setAutoCancel(true);

		String message = extras.getString("message");
		if (message != null) {
			mBuilder.setContentText(message);
		}

		String msgcnt = extras.getString("msgcnt");
		if (msgcnt != null) {
			mBuilder.setNumber(Integer.parseInt(msgcnt));
		}
		
		mNotificationManager.notify((String) appName, 0, mBuilder.build());
	}
	
	private static String getAppName(Context context)
	{
		CharSequence appName = 
				context
					.getPackageManager()
					.getApplicationLabel(context.getApplicationInfo());
		
		return (String)appName;
	}
   }
    /**
     * Returns plugin callback.
     */
    protected static CallbackContext getCallbackContext() {
        return _callbackContext;
    }
}