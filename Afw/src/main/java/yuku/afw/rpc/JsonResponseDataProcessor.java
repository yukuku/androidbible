package yuku.afw.rpc;

import org.json.JSONObject;

public interface JsonResponseDataProcessor {
	void processJsonResponse(JSONObject json);
}
