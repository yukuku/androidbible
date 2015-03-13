package yuku.atree;

public class TreeEvent {
	protected TreePath path;
	protected int[] childIndices;
	protected TreeNode[] children;

	public TreeEvent(final TreeNode[] path) {
		this(path, new int[0], null);
	}

	public TreeEvent(final TreePath path) {
		this(path, new int[0], null);
	}

	public TreeEvent(final TreeNode[] path, final int[] childIndices, final TreeNode[] children) {

		this(new TreePath(path), childIndices, children);
	}

	public TreeEvent(final TreePath path, final int[] childIndices, final TreeNode[] children) {
		this.path = path;
		this.childIndices = childIndices;
		this.children = children;
	}

	public TreePath getTreePath() {
		return path;
	}

	public TreeNode[] getPath() {
		return path != null ? path.getPath() : null;
	}

	public TreeNode[] getChildren() {
		return children != null ? children.clone() : null;
	}

	public int[] getChildIndices() {
		return childIndices != null ? (int[]) childIndices.clone() : null;
	}
}
