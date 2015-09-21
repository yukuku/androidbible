package yuku.atree;

import android.view.*;
import android.widget.*;

public class TreeAdapter extends BaseAdapter {
	public static final String TAG = TreeAdapter.class.getSimpleName();

	private TreeNode root;
	private boolean rootVisible = true;
	private TreeListener listener;

	@Override public int getCount() {
		if (root == null) {
			return 0;
		} else {
			return root.getRowCount() - (rootVisible? 0: 1);
		}
	}

	@Override public TreeNode getItem(int position) {
		if (rootVisible && position == 0) return root;
		
		return searchItem(root, rootVisible? 0: -1, position);
	}

	private static TreeNode searchItem(TreeNode cur, int base, int target) {
		if (base == target) return cur;
		
		int pos = base + 1; // first child is always one row after
		
		for (int i = 0, len = cur.getChildCount(); i < len; i++) {
			TreeNode child = cur.getChildAt(i);
			int max = pos + child.getRowCount();
			
			// range covered is pos..<max 
			if (target >= pos && target < max) {
				return searchItem(child, pos, target);
			}
			
			pos = max;
		}
		
		throw new RuntimeException("invalid target: " + target);
	}

	@Override public long getItemId(int position) {
		return position;
	}

	@Override public View getView(int position, View convertView, ViewGroup parent) {
		TreeNode node = getItem(position);
		
		return node.getView(position, convertView, parent, node.getLevel(), TreeNodeIconType.up, null);
	}

	public void setRoot(TreeNode root) {
		this.root = root;
		notifyDataSetChanged();
		
		if (root != null) {
			dispatchNodeStructureChanged(root);
		} else {
			notifyRootChangedToNull(this);
		}
	}

	public boolean getRootVisible() {
		return rootVisible;
	}

	public void setRootVisible(boolean visible) {
		this.rootVisible = visible;
		notifyDataSetChanged();
	}

	public void setTreeListener(final TreeListener l) {
		listener = l;
	}

	@SuppressWarnings("unchecked") public <T extends TreeNode> T getRoot() {
		return (T) root;
	}

	public int getIndexOfChild(TreeNode parent, TreeNode child) {
		if (parent == null || child == null) {
			return -1;
		}

		int numChildren = parent.getChildCount();
		for (int i = 0; i < numChildren; i++) {
			if (child.equals(parent.getChildAt(i))) {
				return i;
			}
		}

		return -1;
	}

	public void reload() {
		reload(root);
	}

	public void reload(final TreeNode node) {
		dispatchNodeStructureChanged(node);
	}

	public void insertNodeInto(final MutableTreeNode newChild, final MutableTreeNode parent, final int index) {
		parent.insert(newChild, index);
		dispatchNodesWereInserted(parent, new int[] { index });
	}

	public void removeNodeFromParent(final MutableTreeNode node) {
		MutableTreeNode parent = node.getParent();
		int index = parent.getIndex(node);
		parent.remove(node);
		dispatchNodesWereRemoved(parent, new int[] { index }, new TreeNode[] { node });
	}

	public void dispatchNodeChanged(final TreeNode node) {
		if (node == root) {
			dispatchNodesChanged(node, null);
			return;
		}
		if (node == null) {
			return;
		}
		final TreeNode parent = node.getParent();
		if (parent == null) {
			return;
		}
		dispatchNodesChanged(parent, new int[] { getIndexOfChild(parent, node) });
	}

	public void dispatchNodesChanged(final TreeNode node, final int[] childIndices) {
		if (node == null || node != root || childIndices == null || childIndices.length == 0) {
			return;
		}

		notifyTreeNodesChanged(getPathToRoot(node), childIndices, getNodeChildren(node, childIndices));
	}

	public void dispatchNodesWereInserted(final TreeNode node, final int[] childIndices) {
		if (node == null || childIndices == null || childIndices.length == 0) {
			return;
		}

		notifyTreeNodesInserted(this, getPathToRoot(node), childIndices, getNodeChildren(node, childIndices));
	}

	public void dispatchNodesWereRemoved(final TreeNode node, final int[] childIndices, final TreeNode[] removedChildren) {
		if (node == null || childIndices == null || childIndices.length == 0) {
			return;
		}
		notifyTreeNodesRemoved(this, getPathToRoot(node), childIndices, removedChildren);
	}

	public void dispatchNodeStructureChanged(final TreeNode node) {
		if (node == null) {
			return;
		}
		notifyTreeStructureChanged(this, getPathToRoot(node), null, null);
	}

	public TreeNode[] getPathToRoot(final TreeNode aNode) {
		if (aNode == null) {
			return new TreeNode[0];
		}

		return getPathToRoot(aNode, 0);
	}

	protected TreeNode[] getPathToRoot(final TreeNode aNode, final int depth) {
		return TreeCommons.getPathToAncestor(aNode, root, depth);
	}

	protected void notifyTreeNodesChanged(final TreeNode[] path, final int[] childIndices, final TreeNode[] children) {
		if (listener == null) return;
		
		TreeEvent event = new TreeEvent(path, childIndices, children);
		listener.onTreeNodesChanged(event);
	}

	protected void notifyTreeNodesInserted(final Object source, final TreeNode[] path, final int[] childIndices, final TreeNode[] children) {
		if (listener == null) return;

		TreeEvent event = new TreeEvent(path, childIndices, children);
		listener.onTreeNodesInserted(event);
	}

	protected void notifyTreeNodesRemoved(final Object source, final TreeNode[] path, final int[] childIndices, final TreeNode[] children) {
		if (listener == null) return;

		TreeEvent event = new TreeEvent(path, childIndices, children);
		listener.onTreeNodesRemoved(event);
	}

	protected void notifyTreeStructureChanged(final Object source, final TreeNode[] path, final int[] childIndices, final TreeNode[] children) {
		if (listener == null) return;

		TreeEvent event = new TreeEvent(path, childIndices, children);
		listener.onTreeStructureChanged(event);
	}

	private void notifyRootChangedToNull(final Object source) {
		TreeEvent event = new TreeEvent((TreePath) null);
		listener.onTreeStructureChanged(event);
	}

	private TreeNode[] getNodeChildren(final TreeNode node, final int[] childIndices) {
		if (childIndices == null) {
			return null;
		}

		TreeNode[] result = new TreeNode[childIndices.length];
		for (int i = 0; i < result.length; i++) {
			result[i] = node.getChildAt(childIndices[i]);
		}
		return result;
	}
}
