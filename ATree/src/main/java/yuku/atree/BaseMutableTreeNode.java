package yuku.atree;

import java.util.*;

public abstract class BaseMutableTreeNode implements MutableTreeNode {
	protected MutableTreeNode parent;
	protected List<TreeNode> children;
	protected transient Object userObject;
	private boolean expanded;
	
	public BaseMutableTreeNode() {
		this(null);
	}

	public BaseMutableTreeNode(final Object userObject) {
		setUserObject(userObject);
	}

	@Override public void insert(final MutableTreeNode child, final int childIndex) {
		if (child == null || isNodeAncestor(child)) {
			throw new IllegalArgumentException("invalid child to insert");
		}

		if (child.getParent() instanceof MutableTreeNode) {
			child.<MutableTreeNode> getParent().remove(child);
		}
		child.setParent(this);
		getChildren().add(childIndex, child);
	}

	@Override public void remove(final int childIndex) {
		MutableTreeNode child = (MutableTreeNode) getChildren().remove(childIndex);
		child.setParent(null);
	}

	@Override public void setParent(final MutableTreeNode parent) {
		this.parent = parent;
	}

	@SuppressWarnings("unchecked") @Override public <T extends TreeNode> T getParent() {
		return (T) parent;
	}

	@SuppressWarnings("unchecked") @Override public <T extends TreeNode> T getChildAt(final int index) {
		return (T) getChildren().get(index);
	}

	@Override public int getChildCount() {
		return children != null ? children.size() : 0;
	}

	@Override public int getIndex(final TreeNode child) {
		return children != null ? children.indexOf(child) : -1;
	}

	public List<TreeNode> children() {
		return children;
	}

	@Override public void setUserObject(final Object userObject) {
		this.userObject = userObject;
	}

	@SuppressWarnings("unchecked") @Override public <T> T getUserObject() {
		return (T) userObject;
	}

	@Override public void removeFromParent() {
		if (parent != null) {
			parent.remove(this);
		}
	}

	@Override public void remove(final MutableTreeNode child) {
		int index = -1;
		if (child == null || children == null || (index = children.indexOf(child)) == -1) {
			throw new IllegalArgumentException("child null or not found");
		}
		remove(index);
	}

	public void removeAllChildren() {
		if (children == null) {
			return;
		}
		for (Iterator<TreeNode> it = children.iterator(); it.hasNext();) {
			MutableTreeNode child = (MutableTreeNode) it.next();
			child.setParent(null);
			it.remove();
		}
	}

	public void add(final MutableTreeNode child) {
		insert(child, getChildCount() - (isNodeChild(child) ? 1 : 0));
	}

	public boolean isNodeAncestor(final TreeNode anotherNode) {
		if (anotherNode == null) {
			return false;
		}
		TreeNode currentParent = this;
		while (currentParent != null) {
			if (currentParent == anotherNode) {
				return true;
			}
			currentParent = currentParent.getParent();
		}

		return false;
	}

	public boolean isNodeDescendant(final BaseMutableTreeNode anotherNode) {
		return anotherNode != null ? anotherNode.isNodeAncestor(this) : false;
	}

	public TreeNode getSharedAncestor(final BaseMutableTreeNode anotherNode) {
		TreeNode currentParent = anotherNode;
		while (currentParent != null) {
			if (isNodeAncestor(currentParent)) {
				return currentParent;
			}

			currentParent = currentParent.getParent();
		}

		return null;
	}

	public boolean isNodeRelated(final BaseMutableTreeNode node) {
		return getSharedAncestor(node) != null;
	}

	@Override public int getDepth() {
		if (children == null || children.size() == 0) {
			return 0;
		}
		int childrenDepth = 0;
		for (TreeNode child : children) {
			int childDepth = child.getDepth();
			if (childDepth > childrenDepth) {
				childrenDepth = childDepth;
			}
		}
		return childrenDepth + 1;
	}

	@Override public int getLevel() {
		int result = 0;
		TreeNode currentParent = getParent();
		while (currentParent != null) {
			currentParent = currentParent.getParent();
			result++;
		}

		return result;
	}

	public TreeNode[] getPath() {
		return getPathToRoot(this, 0);
	}

	public Object[] getUserObjectPath() {
		TreeNode[] path = getPath();
		Object[] result = new Object[path.length];
		for (int i = 0; i < path.length; i++) {
			result[i] = ((BaseMutableTreeNode) path[i]).getUserObject();
		}

		return result;
	}

	public TreeNode getRoot() {
		TreeNode currentNode = this;
		while (currentNode.getParent() != null) {
			currentNode = currentNode.getParent();
		}

		return currentNode;
	}

	public boolean isRoot() {
		return getParent() == null;
	}

	public boolean isNodeChild(final TreeNode child) {
		return child != null && children != null ? children.contains(child) : false;
	}

	@Override public boolean isLeaf() {
		return children == null || children.isEmpty();
	}

	@Override public String toString() {
		return getUserObject() != null ? getUserObject().toString() : null;
	}

	protected TreeNode[] getPathToRoot(final TreeNode node, final int depth) {
		if (node == null) {
			return new TreeNode[depth];
		}
		TreeNode[] result = getPathToRoot(node.getParent(), depth + 1);
		result[result.length - 1 - depth] = node;

		return result;
	}

	private List<TreeNode> getChildren() {
		if (children == null) {
			children = new ArrayList<>();
		}

		return children;
	}

	// including me
	@Override public int getRowCount() {
		if (!expanded) {
			return 1;
		}
		
		int res = 1;
		for (TreeNode node: getChildren()) {
			res += node.getRowCount();
		}
		return res;
	}

	@Override public boolean getExpanded() {
		return expanded;
	}

	@Override public void setExpanded(boolean expanded) {
		this.expanded = expanded;
	}
}
