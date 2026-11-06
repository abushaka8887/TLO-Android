package uz.tlo.logistics;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import org.json.JSONArray;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public class MainActivity extends Activity {
 WebView w;
 static final int PICK_XLSX=120;
 ValueCallback<Uri[]> fileCallback;
 public void onCreate(Bundle b){
  super.onCreate(b);w=new WebView(this);setContentView(w);
  w.setWebViewClient(new WebViewClient());
  w.setWebChromeClient(new WebChromeClient(){
   @Override public boolean onShowFileChooser(WebView webView,ValueCallback<Uri[]> callback,FileChooserParams params){
    if(fileCallback!=null)fileCallback.onReceiveValue(null);
    fileCallback=callback;
    try{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/octet-stream"});startActivityForResult(intent,PICK_XLSX);return true;}
    catch(Exception ex){fileCallback=null;callback.onReceiveValue(null);Toast.makeText(MainActivity.this,"Fayl tanlash ochilmadi",Toast.LENGTH_LONG).show();return true;}
   }
  });
  w.getSettings().setJavaScriptEnabled(true);
  w.getSettings().setDomStorageEnabled(true);
  w.addJavascriptInterface(new Bridge(),"TLOAndroid");
  w.loadUrl("file:///android_asset/index.html");
 }
 public class Bridge {
  @JavascriptInterface public void chooseExcel(){
   runOnUiThread(()->{
    try {Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/octet-stream"});
    startActivityForResult(i,PICK_XLSX);
    }catch(Exception ex){Toast.makeText(MainActivity.this,"Excel tanlash oynasi ochilmadi: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
   });
  }
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request!=PICK_XLSX)return;
  if(fileCallback!=null){fileCallback.onReceiveValue(result==RESULT_OK&&data!=null&&data.getData()!=null?new Uri[]{data.getData()}:null);fileCallback=null;}
  if(result!=RESULT_OK||data==null)return;
  try{
   JSONArray rows=readExcel(data.getData());
   String json=rows.toString();
   w.evaluateJavascript("importExcelRows("+json+")",null);
  }catch(Exception ex){
   Toast.makeText(this,"Excel o'qilmadi: "+ex.getMessage(),Toast.LENGTH_LONG).show();
  }
 }
 static Document parse(byte[] b)throws Exception{
  DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
  f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
  f.setFeature("http://xml.org/sax/features/external-general-entities",false);
  f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
  f.setExpandEntityReferences(false);
  return f.newDocumentBuilder().parse(new ByteArrayInputStream(b));
 }
 static byte[] entry(ZipFile z,String name)throws Exception{
  ZipEntry e=z.getEntry(name);if(e==null)return null;
  if(e.getSize()>12000000)throw new IOException("Fayl juda katta");
  try(InputStream in=z.getInputStream(e);ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>12000000)throw new IOException("Fayl juda katta");}return out.toByteArray();
  }
 }
 static String text(Element e,String tag){
  NodeList n=e.getElementsByTagName(tag);return n.getLength()==0?"":n.item(0).getTextContent();
 }
 JSONArray readExcel(Uri uri)throws Exception{
  File temp=File.createTempFile("tlo_excel",".xlsx",getCacheDir());
  try{
   try(InputStream in=getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(temp)){
    byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(temp.length()>12000000)throw new IOException("Excel 12 MB dan katta");}
   }
   try(ZipFile z=new ZipFile(temp)){
    ArrayList<String> shared=new ArrayList<>();
    byte[] sb=entry(z,"xl/sharedStrings.xml");
    if(sb!=null){NodeList si=parse(sb).getElementsByTagName("si");for(int k=0;k<si.getLength();k++){NodeList ts=((Element)si.item(k)).getElementsByTagName("t");StringBuilder v=new StringBuilder();for(int j=0;j<ts.getLength();j++)v.append(ts.item(j).getTextContent());shared.add(v.toString());}}
    byte[] sheet=entry(z,"xl/worksheets/sheet1.xml");
    if(sheet==null)throw new IOException("BUYURTMALAR varag'i topilmadi");
    NodeList rs=parse(sheet).getElementsByTagName("row");JSONArray rows=new JSONArray();
    for(int k=0;k<rs.getLength();k++){
     Element row=(Element)rs.item(k);NodeList cells=row.getElementsByTagName("c");
     String[] values=new String[14];Arrays.fill(values,"");
     for(int j=0;j<cells.getLength();j++){
      Element c=(Element)cells.item(j);String ref=c.getAttribute("r");int col=0;
      for(int q=0;q<ref.length()&&Character.isLetter(ref.charAt(q));q++)col=col*26+(Character.toUpperCase(ref.charAt(q))-'A'+1);
      col--;if(col<0||col>=14)continue;
      String type=c.getAttribute("t");String v=text(c,"v");
      if("s".equals(type)){int ix=Integer.parseInt(v);v=ix>=0&&ix<shared.size()?shared.get(ix):"";}
      else if("inlineStr".equals(type))v=text(c,"t");
      values[col]=v;
     }
     JSONArray a=new JSONArray();for(String v:values)a.put(v);rows.put(a);
    }
    return rows;
   }
  }finally{temp.delete();}
 }
 @Override public void onBackPressed(){if(w.canGoBack())w.goBack();else super.onBackPressed();}
}