package yuku.alkitab.base.model;

import java.util.Date;
import java.util.Map;

public class SyncLog {
	public Date createTime;
	public int kind_code; // not of type "Kind" because we may have unknown/deprecated kind encountered in the db
	public String syncSetName;
	public Map<String, Object> params;
}
