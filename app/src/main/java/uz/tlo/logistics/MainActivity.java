package uz.tlo.logistics;
import android.app.Activity;import android.os.Bundle;import android.webkit.WebView;import android.webkit.WebViewClient;
public class MainActivity extends Activity {WebView w; public void onCreate(Bundle b){super.onCreate(b);w=new WebView(this);setContentView(w);w.setWebViewClient(new WebViewClient());w.getSettings().setJavaScriptEnabled(true);w.getSettings().setDomStorageEnabled(true);w.loadUrl("file:///android_asset/index.html");}}
