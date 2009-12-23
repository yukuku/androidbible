package yuku.alkitab;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;

public class Alkitab extends Activity {
	final int menuEdisi = 1;
	final int menuIsi = 2;
	final int menuMenuju = 3;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.utama);
        
        S.siapinEdisi(getResources());
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	menu.add(Menu.NONE, menuEdisi, Menu.NONE, R.string.menuEdisi_text);
    	menu.add(Menu.NONE, menuIsi, Menu.NONE, "Isi gebug");
    	menu.add(Menu.NONE, menuMenuju, Menu.NONE, "Menuju gebug");
    	
    	return true;
    }
    
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
    	if (item.getItemId() == menuEdisi) {
    		startActivity(new Intent(this, EdisiActivity.class));
    		return true;
    	} else if (item.getItemId() == menuIsi) {
    		startActivity(new Intent(this, IsiActivity.class));
    		return true;
    	} else if (item.getItemId() == menuMenuju) {
    		startActivity(new Intent(this, MenujuActivity.class));
    		return true;
    	}
    	
		return super.onMenuItemSelected(featureId, item);
    }
}
