package com.example.androidhttpserver;


import android.content.Context;
import android.content.res.AssetManager;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import com.example.androidhttpserver.servlet.base.IAndroidServletRequest;
import com.example.androidhttpserver.servlet.http.Cookie;
import com.example.androidhttpserver.servlet.impl.AndroidServletRequestImpl;
import com.example.androidhttpserver.servlet.impl.AndroidServletResponseImpl;
import com.example.androidhttpserver.webinfo.WebMapping;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class AndroidHttpServer extends NanoHTTPD{
    private static final String TAG = "AndroidHttpServer";

    private Context ctx;
    private AssetManager assetManager;

    public AndroidHttpServer(int port, Context ctx) {
        super(port);
        this.ctx = ctx;
        init();

        /*
        * android Library和主Module的assets下同路径同文件名的文件不会合并;
        * 只会保留主module的文件打包进apk里;
        * 但是library/main/assets/file1和module/main/assets/file2都会打包进apk里
        *
        * 查看apk的assets文件夹; 可以将apk后缀改为zip, 再解压即可
        */
        loadWebSet("webserver/web.xml");
        loadWebSet("webserver/baseweb.xml");
    }

    private void loadWebSet(String webxml){
        XmlPullParser xmlPullParser = Xml.newPullParser();
        InputStream is = null;
        try {
            is = assetManager.open(webxml);
            xmlPullParser.setInput(is,"UTF-8");
            int event = xmlPullParser.getEventType();
            WebMapping mapping = null;
            while (event != XmlPullParser.END_DOCUMENT){
                switch (event){
                    case XmlPullParser.START_DOCUMENT:
                        // xml文档的开始标签
                        break;
                    case XmlPullParser.START_TAG:
                        String name = xmlPullParser.getName();
                        switch (name){
                            case "android-servlet":
                                mapping = new WebMapping();
                                break;
                            case "url":
                                if(mapping != null) {
                                    String url_pattern = xmlPullParser.nextText();
                                    mapping.setUrl_pattern(url_pattern);
                                }
                                break;
                            case "html-file":
                                if(mapping != null) {
                                    String html_file = xmlPullParser.nextText();
                                    mapping.setHtmlPath(html_file);
                                }
                                break;
                            case "servlet-class":
                                if(mapping != null) {
                                    String servletName = xmlPullParser.nextText();
                                    mapping.loadServletClazz(servletName);
                                }
                                break;
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        String endName = xmlPullParser.getName();
                        if("android-servlet".equals(endName)){
                            if(mapping == null){
                                throw new IllegalStateException(
                                        "find end 'android-servlet' tag without start tag");
                            }else{
                                if(mapping.isValid())
                                    WebMappingSet.put(mapping.getUrl_pattern(),mapping);
                                else
                                    throw new IllegalArgumentException(
                                            "a super servlet tag must contains an 'url' " +
                                                    "tag and a 'html-file' or 'servlet-class' tag");
                            }
                        }
                        break;
                }
                event = xmlPullParser.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {

                }
            }
        }
    }


    private void init(){
        assetManager = ctx.getAssets();
    }

    @Override
    public Response serve(String uri,
                          Method method,
                          Map<String, String> headers,
                          Map<String, String> parms,
                          Map<String, String> files) {

        WebMapping mapping = WebMappingSet.findMapping(uri);
        if(mapping == null){
            return handleAsExtraRes(uri);
        }
        if(!TextUtils.isEmpty(mapping.getHtmlPath())){
            // html
            String htmlPath = mapping.getHtmlPath();
            return handleAsHtml(htmlPath);
        }else{
            // servlet
            Class<? extends AndroidHttpServlet> servletClass = mapping.getServletClass();
            try {
                //
                // ------ cookie;
                //



                AndroidHttpServlet androidHttpServlet  = WebMappingSet.getServlet(servletClass);
                if(androidHttpServlet == null) {
                    androidHttpServlet = createServlet(servletClass);
                }

                IAndroidServletRequest request = createRequest(uri, method, headers, parms, files);

                AndroidServletResponseImpl response = new AndroidServletResponseImpl();

                androidHttpServlet.doRequest(request,response);

                return createResponseByAndroidServletResp(response);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return super.serve(uri, method, headers, parms, files);
    }

    private Response handleAsExtraRes(String uri) {
        try {
            Log.e(TAG, "handleAsExtraRes: "+uri);
            if(uri.startsWith("/"))
                uri = uri.substring(1);
            InputStream in = assetManager.open(uri, AssetManager.ACCESS_BUFFER);

            byte[] buffer = new byte[1024 * 1024];

            int temp = 0;
            int len = 0;
            while((temp=in.read())!=-1){
                buffer[len]=(byte)temp;
                len++;
            }
            in.close();
            return new NanoHTTPD.Response(new String(buffer,0,len));
        } catch (IOException ignored) {
        }
        return handleAsHtml(WebMappingSet.findMapping(WebMappingSet._404).getHtmlPath());
    }

    @NonNull
    private Response createResponseByAndroidServletResp(AndroidServletResponseImpl response) {

        // 传入参数;
        // 请求状态:
        // content type
        // 文本
        Response response1 = new Response(response.getStatus(), response.getContentType(), response.toResponseString());

        // 反馈的头信息
        Map<String, String> respHeader = response.getHeaders();
        for (Map.Entry<String, String> entry : respHeader.entrySet()) {
            response1.addHeader(entry.getKey(),entry.getValue());
        }

        List<Cookie> cookies = response.getCookies();
        response1.addCookie(cookies.toArray(new Cookie[0]));
        return response1;
    }

    private AndroidHttpServlet createServlet(Class<? extends AndroidHttpServlet> servletClass)
            throws IllegalAccessException,
            InstantiationException {
        AndroidHttpServlet androidHttpServlet = servletClass.newInstance();
        androidHttpServlet.injectContext(ctx);
        return androidHttpServlet;
    }

    private Response handleAsHtml(String htmlPath){
        try {

            InputStream in = assetManager.open(htmlPath, AssetManager.ACCESS_BUFFER);

            byte[] buffer = new byte[1024];

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int len;
            while ((len = in.read(buffer))!=-1){
                baos.write(buffer,0,len);
                baos.flush();
            }
            in.close();
            return new NanoHTTPD.Response(baos.toString());
        } catch (IOException ignored) {
        }
        return handleAsHtml(WebMappingSet.findMapping(WebMappingSet._404).getHtmlPath());
    }

    private IAndroidServletRequest createRequest(String uri,
                                                 Method method,
                                                 Map<String, String> headers,
                                                 Map<String, String> parms,
                                                 Map<String, String> files){
        AndroidServletRequestImpl request = new AndroidServletRequestImpl();

        if(headers != null){
            for (Map.Entry<String, String> entry : headers.entrySet()) {

                // 服务器向浏览器写回的时候是Set-Cookie, 但是浏览器请求的时候携带的是cookie
                // cookie -- testKey=testValue; lastTime=1519610074200
                if("cookie".equals(entry.getKey())){
                    String cookiesStr = entry.getValue();
                    if(!TextUtils.isEmpty(cookiesStr)){
                        String[] cookies = cookiesStr.split("; ");
                        for (String cookie:cookies){
                            try {
                                String[] split = cookie.split("=");
                                request.injectCookie(new Cookie(split[0],split[1]));
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                        }
                    }
                }else {
                    request.injectHeader(entry.getKey(), entry.getValue());
                }
            }
        }

        if(parms != null){
            for (Map.Entry<String, String> entry : parms.entrySet()) {
                request.injectParamter(entry.getKey(),entry.getValue());
            }
        }

        request.injectReqUri(uri);
        request.injectReqMethod(method.name());

        // 注入cookie


        return request;
    }
}
