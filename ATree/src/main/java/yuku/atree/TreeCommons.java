package yuku.atree;


/**
 * Storage of the utility methods for tree-related calculations.
 * 
 */
public class TreeCommons {
	/**
	 * Returns tree path from the specified ancestor to a node.
	 * 
	 * @param node
	 *            TreeNode which is the path end
	 * @param ancestor
	 *            TreeNode which is the path top
	 * 
	 * @return path from an ancestor to a node
	 */
	public static TreeNode[] getPathToAncestor(final TreeNode node, final TreeNode ancestor) {
		return getPathToAncestor(node, ancestor, 0);
	}

	/**
	 * Returns tree path from the specified ancestor to a node limited by the depth.
	 * 
	 * @param node
	 *            TreeNode which is the path end
	 * @param ancestor
	 *            TreeNode which is the path top
	 * @param depth
	 *            int value representing the maximum path length
	 * 
	 * @return path from an ancestor to a node
	 */
	public static TreeNode[] getPathToAncestor(final TreeNode node, final TreeNode ancestor, final int depth) {
		if (node == null) {
			return new TreeNode[depth];
		}

		if (node == ancestor) {
			TreeNode[] result = new TreeNode[depth + 1];
			result[0] = ancestor;
			return result;
		}

		TreeNode[] result = getPathToAncestor(node.getParent(), ancestor, depth + 1);
		result[result.length - depth - 1] = node;
		return result;
	}
}