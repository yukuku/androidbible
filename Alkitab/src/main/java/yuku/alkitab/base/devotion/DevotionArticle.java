package yuku.alkitab.base.devotion;

import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.base.widget.CallbackSpan;


public interface DevotionArticle {
	CharSequence getContent(CallbackSpan.OnClickListener listener);
	String getDate();
	boolean getReadyToUse();
	
	//# used by external
	DevotionActivity.DevotionKind getKind();
	String getRawEncoding();
	
	/**
	 * From raw, implementations must fill in other data like header, title, and body.
	 * Also must set their own "readyToUse" property.
	 */
	void fillIn(String raw);
	
	/**
	 * Must return a 3-element array: header, title, body.
	 * This is so because it's needed for compatibility with database table.
	 * Header and title can be null. Body should not be null.
	 */
	String[] getHeaderTitleBody();
}
