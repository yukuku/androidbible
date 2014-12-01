package yuku.atree.nodes;

import yuku.atree.BaseMutableTreeNode;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

public abstract class BaseFileTreeNode extends BaseMutableTreeNode implements Comparable<BaseFileTreeNode> {
	public static final String TAG = BaseFileTreeNode.class.getSimpleName();
	protected final File file;
	protected final VirtualChild[] virtualChildren;

	public static class VirtualChild {
		public File file;
	}

	public BaseFileTreeNode(File file) {
		this.file = file;
		this.virtualChildren = null;
	}

	public BaseFileTreeNode(VirtualChild[] virtualChildren) {
		this.file = null;
		this.virtualChildren = virtualChildren;
	}

	@Override public void setExpanded(boolean expanded) {
		super.setExpanded(expanded);

		if (expanded) {
			File[] files;
			boolean virtual = false;
			if (file != null && file.isDirectory()) {
				files = file.listFiles(fileFilter);
				if (files != null) Arrays.sort(files, fileComparator);
			} else if (virtualChildren != null) {
				files = new File[virtualChildren.length];
				for (int i = 0; i < virtualChildren.length; i++) {
					files[i] = virtualChildren[i].file;
				}
				virtual = true;
			} else {
				files = null;
			}
			
			if (files == null) {
				this.removeAllChildren();
			} else {
				HashMap<String, BaseFileTreeNode> existing = new HashMap<String, BaseFileTreeNode>();
				for (int i = 0; i < this.getChildCount(); i++) {
					BaseFileTreeNode child = this.getChildAt(i);
					existing.put(child.file.getName(), child);
				}
				
				this.removeAllChildren();

				for (int i = 0; i < files.length; i++) {
					final File file = files[i];
					final BaseFileTreeNode existingNode = existing.get(file.getName());
					if (existingNode != null) {
						this.add(existingNode);
					} else {
						this.add(virtual? generateForVirtualChild(virtualChildren[i]): generateForFile(file));
					}
				}
			}
		}
	}
	
	private FileFilter fileFilter = new FileFilter() {
		@Override public boolean accept(File pathname) {
			if (showDirectoriesOnly()) {
				if (!pathname.isDirectory()) {
					return false;
				}
			}
			
			if (!showHidden()) {
				if (pathname.isHidden()) {
					return false;
				}
			}
			
			return true;
		}
	};
	
	static Comparator<File> fileComparator = new Comparator<File>() {
		@Override public int compare(File a, File b) {
			// virtual first
			if (a == null) {
				return -1;
			} else if (b == null) {
				return +1;
			}
			
			if (a.isDirectory() && !b.isDirectory()) {
				return -1;
			} else if (!a.isDirectory() && b.isDirectory()) {
				return +1;
			}
			
			// both files or both dirs
			
			String aname = a.getName();
			String bname = b.getName();
			
			// dot-files are later
			if (aname.startsWith(".") && !bname.startsWith(".")) { //$NON-NLS-1$ //$NON-NLS-2$
				return +1;
			} else if (!aname.startsWith(".") && bname.startsWith(".")) { //$NON-NLS-1$ //$NON-NLS-2$
				return -1;
			}
			
			return aname.compareToIgnoreCase(bname);
		}
	};

	@Override public int compareTo(BaseFileTreeNode another) {
		File a = this.file;
		File b = another.file;
		return fileComparator.compare(a, b);
	}

	protected abstract BaseFileTreeNode generateForFile(File file);

	protected BaseFileTreeNode generateForVirtualChild(VirtualChild virtualChild) {
		return generateForFile(virtualChild.file); // default implementation falls back to normal file
	}
	
	protected boolean showDirectoriesOnly() {
		return false;
	}
	
	protected boolean showHidden() {
		return true;
	}

	public File getFile() {
		return file;
	}
	
	public VirtualChild[] getVirtualChildren() {
		return virtualChildren;
	}
}
