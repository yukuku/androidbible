package yuku.androidsdk.searchbar;

import android.content.Context;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.TextView.OnEditorActionListener;

public class SearchBar extends LinearLayout {
	public static final String TAG = SearchBar.class.getSimpleName();
	
	public interface OnSearchListener {
		void onSearch(SearchBar searchBar, Editable text);
	}
	
	TextView lBadge;
	EditText tSearch;
	Button bSearch;
	Button bExtra1;
	LinearLayout root;
	OnSearchListener onSearchListener;
	
	public SearchBar(Context context) {
		super(context);
		init();
	}

	public SearchBar(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {
		LayoutInflater.from(getContext()).inflate(R.layout.search_bar, this);
		
        lBadge = (TextView) findViewById(R.id.search_badge);
        tSearch = (EditText) findViewById(R.id.search_src_text);
        bSearch = (Button) findViewById(R.id.search_go_btn);
        bExtra1 = (Button) findViewById(R.id.search_extra1_btn);
        root = (LinearLayout) findViewById(R.id.search_bar);
        
        if (isInEditMode()) return;
        tSearch.setOnEditorActionListener(new OnEditorActionListener() {
			@Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_SEARCH) {
					if (onSearchListener != null) {
						onSearchListener.onSearch(SearchBar.this, tSearch.getText());
					}
					return true;
				}
				return false;
			}
		});
        bSearch.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) {
				if (onSearchListener != null) {
					onSearchListener.onSearch(SearchBar.this, tSearch.getText());
				}
			}
		});
        
        lBadge.setVisibility(View.GONE);
	}

	public Editable getText() {
		return tSearch.getText();
	}

	public void setText(CharSequence text, BufferType type) {
		tSearch.setText(text, type);
	}

	public final void setText(CharSequence text) {
		tSearch.setText(text);
	}
	
	public void setOnSearchListener(OnSearchListener l) {
		this.onSearchListener = l;
	}
	
	public EditText getSearchField() {
		return tSearch;
	}
	
	public Button getSearchButton() {
		return bSearch;
	}
	
	public Button getSearchExtra1() {
		return bExtra1;
	}
	
	public void setBottomView(View v) {
		// note that the root has 1 child already. So we need to add/replace the second child and so on.
		if (root.getChildCount() > 1) {
			root.removeViews(1, root.getChildCount() - 1);
		}
		v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		root.addView(v);
	}
	
	@SuppressWarnings("unchecked") public <T extends View> T getBottomView() {
		if (root.getChildCount() <= 1) {
			return null;
		}
		return (T) root.getChildAt(1);
	}
}
