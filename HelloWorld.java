package com.example.helloworld;


import com.sun.org.apache.regexp.internal.RE;
import com.sun.tools.internal.ws.wsdl.document.http.HTTPOperation;
import com.sun.tools.internal.ws.wsdl.parser.InternalizationLogic;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;


/**
 * Created by bilalSaad on 29/04/2016.
 */
public class HelloWorld {
    public static void main(String[] args) {
        int port = 9000;
        ServerSocket server = null;
        try {
            server = new ServerSocket(port);
        } catch (IOException e) {
            System.out.println("Could not listen on port " + port);
            System.exit(-1);
        }

        Socket client = null;


        while(true) {
            try {
                client = server.accept();
                handle_request(client);

            } catch (IOException e) {
                System.out.println("Failed to accept a connection");
                e.printStackTrace();
            }
        }
    }

    private static void handle_request(Socket client) throws IOException {
        InputStream input = client.getInputStream();
        OutputStream output = client.getOutputStream();
        Pair<HttpRequest, Integer> request = HttpRequest.parseRequest(input);
        HttpResponse resp;
        if (!request.ok()) {
            resp = new HttpResponse(request.snd(), "");
        } else {
            Pair<String, FileStatus> file_or_status = get_file(request.fst().url());
            resp = file_or_status.ok() ? new HttpResponse(request.snd(), file_or_status.fst()) :
                                         new HttpResponse(file_stat_to_http(file_or_status.snd()), "");
        }

        output.write(resp.getBytes());
        client.close();
    }

    private static Integer file_stat_to_http(FileStatus snd) {
        Integer ret = HTTP_CODES.NOT_IMPLEMENTED;
        switch(snd) {
            case NOT_FOUND:
                ret = HTTP_CODES.NOT_FOUND;
                break;
            case PERMISSION_DENIED:
                ret = HTTP_CODES.FORBIDDEN;
                break;
            case OK:
                ret = HTTP_CODES.OK;
                break;
        }

        return ret;
    }

    private static Pair<String, FileStatus> get_file(String path) throws IOException {
        File f = new File(path);
        if(!f.exists() || f.isDirectory() || f.isHidden())
            return new Pair<>(FileStatus.NOT_FOUND);
        List<String> lines = Files.readAllLines(Paths.get(path));
        StringBuilder b = new StringBuilder();

        for(String s : lines)
            b.append(s + " \n");
        return new Pair<>(b.toString(),FileStatus.OK, x-> FileStatus.OK.equals(x));
    }

    private enum FileStatus {
        NOT_FOUND,
        PERMISSION_DENIED,
        OK
    }
}

class HttpRequest {
    private static final String ENCODING = "ISO-8859-1";
    private String furl;
    private Map<String, String> fheaders;
    private Map<String, String> fparams;

    public static Pair<HttpRequest, Integer> parseRequest(InputStream in)
            throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        HttpRequest req = new HttpRequest();
        String request_line = reader.readLine();
        if (!validate_request_line(request_line)) {
            new Pair<HttpRequest, Integer>(HTTP_CODES.BAD_REQUEST);
        }

        String request_contents[] = request_line.split("\\s");
        if(request_contents[0].equals("GET")) {
            Pair<Map<String, String>, String> url = parse_url(request_contents[1]);
            req.set_url(url.snd());
            req.set_params(url.fst());
            req.set_headers(parse_headers(reader));
            return new Pair<HttpRequest, Integer>(req, HTTP_CODES.OK, x->true);
        }
        return new Pair<HttpRequest, Integer>(HTTP_CODES.NOT_IMPLEMENTED);
    }

    private static Pair<Map<String, String>, String> parse_url
            (String cmd) throws IOException {
        int qmark = cmd.indexOf('?');

        HashMap<String, String> params =  new HashMap();
        String url = decode((qmark > 0) ? cmd.substring(0, qmark) : cmd);
        if (qmark < 0) {
            return new Pair<>(params, url, x->false);
        }
        String[] prms = cmd.substring(qmark+1).split("&");

        for(String s : prms) {
            String[] tmp = s.split("=");
            String a = decode(tmp[0]);
            String b = decode(tmp.length > 1 ? tmp[1] : "");
            params.put(a, b);
        }
        return new Pair<>(params, url, x->true);
    }
    private static String decode(String x) throws IOException {
        return URLDecoder.decode(x, ENCODING);
    }
    private static boolean validate_request_line(String request_line) {
        String[] cmd = request_line.split("\\s");
        return cmd.length == 3 && cmd[2].trim().equalsIgnoreCase("http/1.1");
    }

    private static HashMap<String, String> parse_headers(BufferedReader reader)
            throws IOException {
        String line;
        HashMap headers = new HashMap<String, String>();
        while(!(line = reader.readLine()).equals("")) {
            int split = line.indexOf(':');
            if (split < 0) {
                throw new IOException("Ill formated header");
            }
            headers.put(line.substring(0, split),
                    line.substring(split + 1).trim());

        }

        return headers;
    }

    private void set_url(String url) {
        furl = url;
    }

    private HttpRequest(){
        furl = "";
    }
    @ Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("URL: " + furl + "\n \n HEADERS: \n \n");
        for (Map.Entry<String, String> e : fheaders.entrySet()) {
            b.append(e.getKey() + "=" + e.getValue() + "\n");
        }
        b.append("PARAMS \n");
        for (Map.Entry<String, String> e : fparams.entrySet()) {
            b.append(e.getKey() + "=" + e.getValue() + "\n");
        }
        return b.toString();

    }

    private void set_headers(HashMap<String,String> _headers) {
        fheaders = _headers;
    }

    private void set_params(Map<String, String> _params) {
        this.fparams = _params;
    }

    public String url() {
        return furl;
    }
}

// A class for an HTTP response.
class HttpResponse {
    private Map<String, String> fheaders;
    private StatusLine fstatus_line;
    private String fbody;

    public HttpResponse(Integer snd, String s) {
        this(new HashMap<>(), snd, s);
    }

    private class StatusLine {
        public final static String VERSION = "HTTP/1.1";
        public int fstatus_code;
        public String fphrase;

        StatusLine(int status_code) {
            fstatus_code = status_code;
            fphrase = GetPhrase(status_code);
        }

        private String GetPhrase(int status_code) {
            return "Phrase for status_code " + status_code;
        }
    }

    HttpResponse(Map<String, String> headers, int status_code,  String body) {
        fheaders = headers;
        fstatus_line = new StatusLine(status_code);
        fbody = body;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        final String clrf = "\r\n";

        b.append(StatusLine.VERSION + " " + fstatus_line.fstatus_code + " " + fstatus_line.fphrase + " " + clrf);
        for (Map.Entry<String, String> e : fheaders.entrySet()) {
            b.append(e.getKey() + ": " + e.getValue() + clrf);
        }

        b.append(clrf);

        b.append(fbody);


        return b.toString();
    }

    public byte[] getBytes() {
        return toString().getBytes();
    }
}

class HTTP_CODES {
    public final static int OK = 200;
    public final static int BAD_REQUEST = 400;
    public static final int NOT_IMPLEMENTED = 501;
    public static final int NOT_FOUND = 404;
    public static final int FORBIDDEN = 403;
}
class Pair<T, V>{
    private T t;
    private V v;
    private Function<V,Boolean> func;
    boolean ok() {
        return func.apply(v);
    }
    T fst() {
        return t;
    }
    V snd() {
        return v;
    }

    Pair(T new_t, V new_v, Function<V, Boolean> foo ) {
        t = new_t;
        v = new_v;
        func = foo;
    }
    Pair(V new_v) {
        t = null;
        v = new_v;
        func = x->false;
    }


}