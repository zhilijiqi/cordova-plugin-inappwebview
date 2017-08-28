package org.apache.cordova.inappwebview;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.webkit.WebView;

import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * This class echoes a string called from JavaScript.
 */
public class InAppWebView extends CordovaPlugin {

    private static final String LOG_TAG = "InAppWebView";
    private static String dataDirPrefixPath = "file:///android_asset/www/";
    private boolean isFixDir = false;
    private int checkCounter = 0,checkMaxCounter=2;
    private String launchPage = "index.html";

    private String errorFile = null;
    private static String failingUrl;

    private String jsFile = null;
    private String jsLoader = null;

    private String urlFlag = null;
    private String urlFlagHost = null;
    private List<String> localFiles = null;


    @Override
    protected void pluginInitialize() {
        super.initialize(cordova, webView);

        String errorPage = preferences.getString("UrlError", "errorPage.html");
        if(errorPage != null){
            errorFile = errorPage;
        }
        jsFile = preferences.getString("LoadJsFile", "app.js");
        if(jsFile != null){
            StringBuffer buf = new StringBuffer();
            //jsWrapper = "(function(d) { var c = d.createElement('script'); c.src = %s; d.body.appendChild(c); })(document)";
            buf.append("var _app = _app || [];");
            buf.append("window._app = _app;");
            buf.append("(function(d){");
            buf.append("var app = document.createElement('script');");
            buf.append("app.type='text/javascript';");
            buf.append("app.async = true;");
            buf.append("app.src = '"+jsFile+"';");
            buf.append("var js = document.getElementsByTagName('script');");
            buf.append("if(js==undefined ){d.body.appendChild(app);return;}");
            buf.append("var s = js[0];");
            buf.append("s.parentNode.insertBefore(app, s);");
            buf.append("})(document);");
            jsLoader = buf.toString();
        }
        urlFlag = preferences.getString("UrlFlag", "app=1");
        urlFlagHost = preferences.getString("UrlFlagHost", null);
        try {
            String [] files= cordova.getActivity().getAssets().list("www");
            System.out.print(files);
        } catch (IOException e) {
            Log.e(LOG_TAG,e.getMessage());
        }
    }
    @Override
    public Uri remapUri(Uri uri) {

        //System.out.println(uri);

        if(uri.toString().indexOf("app.js")>-1){
            System.out.println(uri);
            uri = Uri.fromFile(new File( dataDirPrefixPath + "app.js"));
            return uri;
        }
        return null;
    }

    @Override
    public Object onMessage(String id, Object data) {
        if ("onReceivedError".equals(id)) {
            JSONObject d = (JSONObject)data;

            try {
                int code = d.getInt("errorCode");
                if(code ==-2 || code == -6){
                    this.onReceivedError(d.getInt("errorCode"), d.getString("description"), d.getString("url"));
                    return data;
                }
            } catch (JSONException e) {
                LOG.e(LOG_TAG,e.getMessage());
            }
        }else if("onPageFinished".equals(id)){
            if(jsLoader != null && jsLoader.length() > 0) {
                injectDeferredObject(jsLoader, null);
            }
        }else if("onPageStarted".equals(id)){
            checkPrefixPath(data.toString());
            return checkUrlFlag(data.toString());
        }
        return null;
    }

    private void checkPrefixPath(String url){
        if(isFixDir){
           return;
        }
        if(checkCounter < checkMaxCounter){
            dataDirPrefixPath = url.substring(0,url.indexOf(launchPage));
            checkCounter++;
        }else{
            isFixDir=true;
            updatePath();
        }
    }

    private void updatePath(){
        errorFile = dataDirPrefixPath + errorFile;
    }

    private Boolean checkUrlFlag(String url) {
        if(urlFlag==null || !url.startsWith("http") || url.indexOf(urlFlag) >- 1){
            return null;
        }else{
            if(urlFlagHost!=null && !url.startsWith(urlFlagHost)){
                return null;
            }
            if(url.indexOf("?")>-1){
                url = url + "&" + urlFlag;
            }else{
                url = url + "?" +urlFlag;
            }
            final String newUrl = url + "t=" + System.currentTimeMillis();
            cordova.getActivity().runOnUiThread(new Runnable() {
                @SuppressLint("NewApi")
                @Override
                public void run() {
                    webView.loadUrl(newUrl);
                }
            });
        }
        return false;
    }

    @Override
    public Boolean shouldAllowNavigation(String url) {
        if(urlFlag==null || !url.startsWith("http") || url.indexOf(urlFlag) >- 1){
            return true;
        }else{
            if(url.indexOf("?")>-1){
                url = url + "&" + urlFlag;
            }else{
                url = url + "?" +urlFlag;
            }
            final String newUrl = url;
            cordova.getActivity().runOnUiThread(new Runnable() {
                @SuppressLint("NewApi")
                @Override
                public void run() {
                    webView.loadUrl(newUrl);
                }
            });
        }
        return false;
    }
    private void injectDeferredObject(String source, String jsWrapper) {
        if (webView!=null) {
            String scriptToInject;
            if (jsWrapper != null) {
                org.json.JSONArray jsonEsc = new org.json.JSONArray();
                jsonEsc.put(source);
                String jsonRepr = jsonEsc.toString();
                String jsonSourceString = jsonRepr.substring(1, jsonRepr.length()-1);
                scriptToInject = String.format(jsWrapper, jsonSourceString);
            } else {
                scriptToInject = source;
            }
            final String finalScriptToInject = scriptToInject;
            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @SuppressLint("NewApi")
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                        // This action will have the side-effect of blurring the currently focused element
                        webView.loadUrl("javascript:" + finalScriptToInject);
                    } else {
                        webView.getEngine().evaluateJavascript(finalScriptToInject, null);
                    }
                }
            });
        } else {
            LOG.d(LOG_TAG, "Can't inject code into the system browser");
        }
    }

    public void onReceivedError(final int errorCode, final String description, final String failingUrl) {
        if ((errorFile != null) && (!failingUrl.equals(errorFile)) && (webView != null)) {
            this.failingUrl = failingUrl;
            // Load URL on UI thread
            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    webView.loadUrlIntoView(errorFile,true);
                }
            });
        }
    }

    @Override
    public boolean execute(final String action, final CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
        LOG.v(LOG_TAG, "Executing action: " + action);
        if ("errorUrl".equals(action)) {
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, failingUrl);
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);
            return true;
        }

        if ("load".equals(action)) {
            String l = args.getString(0);
            boolean isInnerRes = args.getBoolean(1);
            if(isInnerRes){
                l = dataDirPrefixPath + l;
            }
            final String loadUrl = l;
            if(loadUrl!=null && loadUrl.length()>0){
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        webView.clearHistory();
                        webView.loadUrlIntoView(loadUrl,true);
                    }
                });
            }
            return true;
        }
        return false;
    }
}
