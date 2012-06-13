package yuku.alkitab.base.rpc;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import yuku.alkitab.base.ac.FontManagerActivity;

public class SimpleHttpConnection {
	final URL url;
	HttpURLConnection conn = null;
	InputStream in = null;
	IOException ex = null;
	
	public SimpleHttpConnection(String url) {
		try {
			this.url = new URL(url);
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}
	
	public InputStream load() {
		try {
			conn = (HttpURLConnection) url.openConnection();
			in = new BufferedInputStream(conn.getInputStream());
			return in;
		} catch (IOException e) {
			this.ex = e;
			return null;
		}
	}
	
	public boolean isSameHost() {
		return url.getHost().equals(conn.getURL().getHost());
	}
	
	public long getContentLength() {
		return conn.getContentLength();
	}
	
	public Exception getException() {
		return ex;
	}
	
	public void close() {
		try {
			if (in != null) in.close();
			if (conn != null) conn.disconnect();
		} catch (IOException e) {
			Log.d(FontManagerActivity.TAG, "close", e);
		}
	}
}