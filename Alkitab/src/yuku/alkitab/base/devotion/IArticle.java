package yuku.alkitab.base.devotion;


public interface IArticle {
	CharSequence getTitle();
	String getHeaderHtml();
	String getBodyHtml();
	String getDate();
	boolean getReadyToUse();
	
	CharSequence getCopyrightHtml();
	
	//# dipake buat orang luar 
	String getName();
	String getDevotionTitle();
	String getUrl();
	String getRawEncoding();
	void fillIn(String raw);
}
