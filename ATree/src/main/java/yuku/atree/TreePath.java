package yuku.atree;

import java.io.*;

public class TreePath implements Serializable {
    private TreeNode[] elements;
    private TreePath parent;
    private final int pathCount;

    public TreePath(final TreeNode[] path) {
        pathCount = path.length;
        elements = new TreeNode[pathCount];
        System.arraycopy(path, 0, elements, 0, pathCount);
        parent = null;
    }

    public TreePath(final TreeNode singlePath) {
        elements = new TreeNode[] {singlePath};
        pathCount = 1;
        parent = null;
    }

    protected TreePath() {
        elements = new TreeNode[] {null};
        pathCount = 1;
        parent = null;
    }

    protected TreePath(final TreeNode[] path, final int length) {
        pathCount = length;
        elements = new TreeNode[pathCount];
        System.arraycopy(path, 0, elements, 0, pathCount);
        parent = null;
    }

    protected TreePath(final TreePath parentPath, final TreeNode lastElement) {
        elements = new TreeNode[] {lastElement};
        parent = parentPath;
        pathCount = (parent != null) ? parent.getPathCount() + 1 : 1;
    }

    @Override public boolean equals(final Object o) {
        if (!(o instanceof TreePath)) {
            return false;
        }

        TreePath path = (TreePath)o;
        final int numPathComponents = getPathCount();
        if (path.getPathCount() != numPathComponents) {
            return false;
        }

        for (int i = 0; i < numPathComponents; i++) {
            if (!path.getPathComponent(i).equals(getPathComponent(i))) {
                return false;
            }
        }

        return true;
    }

    public TreeNode getLastPathComponent() {
        return elements[elements.length - 1];
    }

    public TreePath getParentPath() {
        if (parent != null) {
            return parent;
        }

        int numParentPaths = getPathCount() - 1;
        if (numParentPaths <= 0) {
            return null;
        }

        return new TreePath(getPath(), numParentPaths);
    }

    public TreeNode[] getPath() {
        if (parent == null) {
            return elements;
        }

        TreeNode[] parentPath = parent.getPath();
        TreeNode[] result = new TreeNode[parentPath.length + 1];
        System.arraycopy(parentPath, 0, result, 0, parentPath.length);
        result[result.length - 1] = getLastPathComponent();

        elements = result.clone();
        parent = null;
        return result;
    }

    public TreeNode getPathComponent(final int element) {
        final int pathCount = getPathCount();
        if (element < 0 || element >= pathCount) {
            throw new IllegalArgumentException("element index out of bounds"); //$NON-NLS-1$
        }
        if (parent == null) {
            return elements[element];
        }

        return (element < pathCount - 1) ? parent.getPathComponent(element) :
                                           getLastPathComponent();
    }

    public int getPathCount() {
        return pathCount;
    }

    public boolean isDescendant(final TreePath child) {
        if (child == null) {
            return false;
        }

        final int numPathComponents = getPathCount();
        if (child.getPathCount() < numPathComponents) {
            return false;
        }

        for (int i = 0; i < numPathComponents; i++) {
            if (!child.getPathComponent(i).equals(getPathComponent(i))) {
                return false;
            }
        }

        return true;
    }

    public TreePath pathByAddingChild(final TreeNode child) {
        return new TreePath(this, child);
    }

    @Override public int hashCode() {
        return getLastPathComponent().hashCode();
    }

    @Override public String toString() {
        String result = null;
        final int numPathComponents = getPathCount();
        for (int i = 0; i < numPathComponents; i++) {
            if (result != null) {
                result += ", ";
            } else {
                result = "";
            }
            result += getPathComponent(i);
        }
        return "[" + result + "]";
    }
}
