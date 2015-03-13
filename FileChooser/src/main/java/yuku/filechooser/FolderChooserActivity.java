package yuku.filechooser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import yuku.atree.TreeAdapter;
import yuku.atree.TreeNodeIconType;
import yuku.atree.nodes.BaseFileTreeNode;

import java.io.File;
import java.util.Arrays;

public class FolderChooserActivity extends Activity {
	static final String EXTRA_config = "config"; //$NON-NLS-1$
	static final String EXTRA_result = "result"; //$NON-NLS-1$

	public static Intent createIntent(Context context, FolderChooserConfig config) {
		Intent res = new Intent(context, FolderChooserActivity.class);
		res.putExtra(EXTRA_config, config);
		return res;
	}

	public static FolderChooserResult obtainResult(Intent data) {
		if (data == null) return null;
		return data.getParcelableExtra(EXTRA_result);
	}

	ListView tree;
	Button bOk;
	TextView lPath;
	
	FolderChooserConfig config;
	TreeAdapter adapter;
	File selectedDir;
	
	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		config = getIntent().getParcelableExtra(EXTRA_config);

		Utils.configureTitles(this, config.title, config.subtitle);

		setContentView(R.layout.filechooser_activity_folderchooser);

		tree = (ListView) findViewById(R.id.filechooser_tree);
		bOk = (Button) findViewById(R.id.filechooser_bOk);
		lPath = (TextView) findViewById(R.id.filechooser_lPath);
		
		adapter = new TreeAdapter();
		tree.setAdapter(adapter);
		tree.setOnItemClickListener(tree_itemClick);

		final int perm_writeExt = checkCallingOrSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
		if (perm_writeExt == PackageManager.PERMISSION_GRANTED) {
			tree.setOnItemLongClickListener(tree_itemLongClick);
		}
		
		bOk.setOnClickListener(bOk_click);

		setSelectedDir(null);

		if (config.roots == null || config.roots.size() == 0) {
			config.roots = Arrays.asList(Environment.getExternalStorageDirectory().getAbsolutePath());
		}

		final BaseFileTreeNode.VirtualChild[] children = new BaseFileTreeNode.VirtualChild[config.roots.size()];
		for (int i = 0; i < children.length; i++) {
			final BaseFileTreeNode.VirtualChild child = children[i];
			child.file = new File(config.roots.get(i));
		}

		FileTreeNode root = new FileTreeNode(children);
		adapter.setRootVisible(false);
		root.setExpanded(true);

		if (children.length == 1 && config.expandSingularRoot) {
			FileTreeNode childNode = root.getChildAt(0);
			childNode.setExpanded(true);
		}
		
		if (children.length > 1 && config.expandMultipleRoots) {
			for (int i = 0, len = root.getChildCount(); i < len; i++) {
				FileTreeNode childNode = root.getChildAt(i);
				childNode.setExpanded(true);
			}
		}
		
		adapter.setRoot(root);
	}

	private OnItemClickListener tree_itemClick = new OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			FileTreeNode node = (FileTreeNode) adapter.getItem(position);
			node.setExpanded(!node.getExpanded());
			adapter.notifyDataSetChanged();

			setSelectedDir(node.getFile());
		}
	};
	
	private OnItemLongClickListener tree_itemLongClick = new OnItemLongClickListener() {
		@Override public boolean onItemLongClick(AdapterView<?> parent, View v, final int position, long id) {
			final FileTreeNode node = (FileTreeNode) adapter.getItem(position);
			final File file = node.getFile();
			if (!file.isDirectory()) return false;
			
			new AlertDialog.Builder(FolderChooserActivity.this)
			.setItems(new String[] {"New folder"}, new DialogInterface.OnClickListener() {
				@Override public void onClick(DialogInterface _unused_, int which) {
					final Button[] bOk = {null};
					
					final EditText tFolderName = new EditText(FolderChooserActivity.this);
					MarginLayoutParams lp = new MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
					lp.leftMargin = lp.topMargin = lp.rightMargin = lp.bottomMargin = (int) (6 * getResources().getDisplayMetrics().density);
					tFolderName.setLayoutParams(lp);
					tFolderName.setHint("Folder name");
					tFolderName.addTextChangedListener(new TextWatcher() {
						@Override public void afterTextChanged(Editable s) {
							bOk[0].setEnabled(s.toString().trim().length() > 0);
						}

						@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
						@Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
					});
					
					final AlertDialog dialog = new AlertDialog.Builder(FolderChooserActivity.this)
					.setTitle("New folder")
					.setView(tFolderName)
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override public void onClick(DialogInterface dialog, int which) {
							String folderName = tFolderName.getText().toString().trim();
							File newDir = new File(file, folderName);
							boolean ok = newDir.mkdirs();
							if (ok) {
								if (node.getExpanded()) {
									node.setExpanded(false);
								}
								node.setExpanded(true);
							}
						}})
					.show();
					
					bOk[0] = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
					bOk[0].setEnabled(false);
				}
			})
			.show();
			return true;
		}
	};
		
	OnClickListener bOk_click = new OnClickListener() {
		@Override public void onClick(View v) {
			if (selectedDir == null) return;
			
			FolderChooserResult result = new FolderChooserResult();
			result.selectedFolder = selectedDir.getAbsolutePath(); 
			
			Intent data = new Intent();
			data.putExtra(EXTRA_result, result);
			
			setResult(RESULT_OK, data);
			finish();
		}
	};

	class FileTreeNode extends BaseFileTreeNode {

		private float density;

		public FileTreeNode(File file) {
			super(file);
			init();
		}

		public FileTreeNode(VirtualChild[] virtualChildren) {
			super(virtualChildren);
			init();
		}

		void init() {
			density = getResources().getDisplayMetrics().density;
		}

		@Override public View getView(int position, View convertView, ViewGroup parent, int level, TreeNodeIconType iconType, int[] lines) {
			TextView res = (TextView) (convertView != null? convertView: getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false));

			res.setPadding((int) (density * (20 * (level - 1) + 6)), 0, 0, 0);
			res.setText(file.getName());
			res.setCompoundDrawablesWithIntrinsicBounds(file.isDirectory()? R.drawable.filechooser_folder: R.drawable.filechooser_file, 0, 0, 0);
			
			return res;
		}

		@Override protected BaseFileTreeNode generateForFile(File file) {
			return new FileTreeNode(file);
		}
		
		@Override protected boolean showDirectoriesOnly() {
			return true;
		}
		
		@Override protected boolean showHidden() {
			return config.showHidden;
		}
	}

	protected void setSelectedDir(File dir) {
		this.selectedDir = dir;
		bOk.setEnabled(dir != null);
		
		if (dir != null) {
			// display complete path
			SpannableStringBuilder sb = new SpannableStringBuilder();
			String parent = dir.getParent();
			sb.append(parent == null? "": parent + "/");
			int sb_len = sb.length();
			sb.append(dir.getName());
			sb.setSpan(new StyleSpan(Typeface.BOLD), sb_len, sb.length(), 0);
			lPath.setText(sb, BufferType.SPANNABLE);
			
			// disable button if not writable
			if (config.mustBeWritable && !dir.canWrite()) {
				bOk.setEnabled(false);
			}
		} else {
			lPath.setText("");
		}
	}
}
